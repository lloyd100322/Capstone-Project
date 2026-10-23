package com.mobile.enerjeep.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.EmergencyContact
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.PasswordHasher
import com.mobile.enerjeep.data.PhilippineIdentifiers
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.ui.components.ToastFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

enum class ProfileSubView {
    MAIN,
    EDIT_PROFILE,
    SECURITY
}

data class DriverProfileUiState(
    val currentView: ProfileSubView =
        ProfileSubView.MAIN,

    val firstName: String = "",
    val lastName: String = "",
    val phone: String = "",
    val licenseNumber: String = "",
    val plateNumber: String = "",
    val emergencyContactName: String = "",
    val emergencyContactNumber: String = "",
    val oldPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = ""
)

@HiltViewModel
class DriverProfileViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            DriverProfileUiState()
        )

    val uiState: StateFlow<DriverProfileUiState> =
        _uiState.asStateFlow()

    private var listener:
            ListenerRegistration? = null

    companion object {
        private val PH_PHONE_REGEX =
            Regex("^9\\d{9}$")
    }

    init {
        loadProfile()
    }

    private fun loadProfile() {

        val phone =
            userSession.userProfile
                ?.phoneNumber
                ?: return

        listener =
            firestore
                .collection(
                    FirestoreCollections.USERS
                )
                .document(phone)
                .addSnapshotListener {
                        document,
                        _ ->

                    if (
                        document == null ||
                        !document.exists()
                    ) {
                        return@addSnapshotListener
                    }

                    val storedPhone =
                        document
                            .getString(
                                "phoneNumber"
                            )
                            .orEmpty()

                    val phoneDigits =
                        storedPhone
                            .removePrefix("+63")
                            .filter {
                                it.isDigit()
                            }
                            .take(10)

                    _uiState.value =
                        _uiState.value.copy(
                            firstName =
                                document
                                    .getString(
                                        "firstName"
                                    )
                                    .orEmpty(),

                            lastName =
                                document
                                    .getString(
                                        "lastName"
                                    )
                                    .orEmpty(),

                            phone =
                                phoneDigits,

                            licenseNumber =
                                document
                                    .getString(
                                        "licenseNumber"
                                    )
                                    .orEmpty(),

                            plateNumber =
                                document
                                    .getString(
                                        "plateNumber"
                                    )
                                    .orEmpty()
                                    .uppercase(),

                            emergencyContactName =
                                readEmergencyContact(
                                    document,
                                    "emergencyContactName"
                                )
                                    .ifBlank {
                                        // Older accounts only have the
                                        // one combined value.
                                        EmergencyContact
                                            .splitLegacy(
                                                document.getString(
                                                    "emergencyContact"
                                                )
                                            )
                                            .name
                                    },

                            emergencyContactNumber =
                                readEmergencyContact(
                                    document,
                                    "emergencyContactNumber"
                                )
                                    .ifBlank {
                                        EmergencyContact
                                            .splitLegacy(
                                                document.getString(
                                                    "emergencyContact"
                                                )
                                            )
                                            .number
                                    }
                        )
                }
    }

    fun setView(
        view: ProfileSubView
    ) {
        _uiState.value =
            _uiState.value.copy(
                currentView = view
            )
    }

    fun updateFirstName(
        value: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                firstName = value
            )
    }

    fun updateLastName(
        value: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                lastName = value
            )
    }

    fun updatePhone(
        value: String
    ) {
        val cleanPhone =
            value
                .filter {
                    it.isDigit()
                }
                .take(10)

        _uiState.value =
            _uiState.value.copy(
                phone = cleanPhone
            )
    }

    fun updateLicenseNumber(
        value: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                licenseNumber = value
            )
    }

    fun updatePlateNumber(
        value: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                plateNumber =
                    value.uppercase()
            )
    }

    fun updateEmergencyContactName(
        value: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                emergencyContactName = value
            )
    }

    fun updateEmergencyContactNumber(
        value: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                emergencyContactNumber = value
            )
    }

    fun updateOldPassword(
        value: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                oldPassword = value
            )
    }

    fun updateNewPassword(
        value: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                newPassword = value
            )
    }

    fun updateConfirmPassword(
        value: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                confirmPassword = value
            )
    }

    fun saveProfile() {

        val oldProfile =
            userSession.userProfile
                ?: return

        val currentPhone =
            oldProfile.phoneNumber

        val state =
            _uiState.value

        val cleanPhoneDigits =
            state.phone
                .filter {
                    it.isDigit()
                }
                .take(10)

        if (
            !PH_PHONE_REGEX.matches(
                cleanPhoneDigits
            )
        ) {
            ToastFeedback.show(
                "Ang numero ay dapat 10 digit at nasa format na 9XXXXXXXXX"
            )
            return
        }

        val newPhone =
            "+63$cleanPhoneDigits"

        /*
         * Changing the phone number is NOT allowed here. The phone
         * number is the id of the account document AND the Firebase Auth
         * identity, so changing it on one side only would sign the user
         * out and orphan their session. It has to go through a phone
         * verification flow first (same rule as the admin Profile tab).
         */
        if (newPhone != currentPhone) {
            ToastFeedback.show(
                "Hindi muna maaaring palitan ang phone number dito. " +
                        "Kailangan itong dumaan sa phone verification."
            )
            return
        }

        val cleanPlate =
            state.plateNumber
                .trim()
                .uppercase()

        // The plate number, the license number and the emergency contact
        // are checked before anything is saved.
        PhilippineIdentifiers.plateNumberError(cleanPlate)
            ?.let { error ->
                ToastFeedback.show(error)
                return
            }

        PhilippineIdentifiers.licenseNumberError(state.licenseNumber.trim())
            ?.let { error ->
                ToastFeedback.show(error)
                return
            }

        val cleanEmergencyName =
            state.emergencyContactName.trim()

        val cleanEmergencyNumber =
            state.emergencyContactNumber.trim()

        if (!EmergencyContact.isValidName(cleanEmergencyName)) {
            ToastFeedback.show(
                "Ang pangalan ng emergency contact ay kailangan at " +
                        "dapat hanggang 50 karakter."
            )
            return
        }

        if (!EmergencyContact.isValidNumber(cleanEmergencyNumber)) {
            ToastFeedback.show(
                "Ang numero ng emergency contact ay dapat valid na " +
                        "telepono (09XXXXXXXXX o +639XXXXXXXXX)."
            )
            return
        }

        viewModelScope.launch {

            try {

                val users =
                    firestore.collection(
                        FirestoreCollections.USERS
                    )

                val oldRef =
                    users.document(
                        currentPhone
                    )

                val cleanFirstName =
                    state.firstName.trim()

                val cleanLastName =
                    state.lastName.trim()

                val cleanLicense =
                    state.licenseNumber.trim()

                val cleanEmergency =
                    EmergencyContact.combine(
                        cleanEmergencyName,
                        cleanEmergencyNumber
                    )

                /*
                 * A plate number must stay unique, the same rule the
                 * Create Account screen uses. Otherwise reports would
                 * attach earnings and incidents to the wrong driver.
                 *
                 * Drivers may not list every account (only admins can),
                 * so the double-check is best effort here: when the
                 * database refuses the list, the save continues. The
                 * Create Account screen (admin) is the strict gate.
                 */
                if (cleanPlate != oldProfile.plateNumber) {
                    val alreadyUsed = try {
                        users
                            .whereEqualTo("role", "driver")
                            .get()
                            .await()
                            .documents
                            .any {
                                it.id != currentPhone &&
                                        PhilippineIdentifiers.platesMatch(
                                            it.getString("plateNumber"),
                                            cleanPlate
                                        )
                            }
                    } catch (_: Exception) {
                        false
                    }

                    if (alreadyUsed) {
                        ToastFeedback.show(
                            "Ginagamit na ng ibang driver ang plate number na ito"
                        )
                        return@launch
                    }
                }

                oldRef
                    .update(
                        mapOf<String, Any>(
                            "firstName" to
                                    cleanFirstName,

                            "lastName" to
                                    cleanLastName,

                            "licenseNumber" to
                                    cleanLicense,

                            "plateNumber" to
                                    cleanPlate,

                            "emergencyContact" to
                                    cleanEmergency,

                            "emergencyContactName" to
                                    cleanEmergencyName,

                            "emergencyContactNumber" to
                                    cleanEmergencyNumber,

                            "updatedAt" to
                                    FieldValue
                                        .serverTimestamp()
                        )
                    )
                    .await()

                /*
                 * Keep active queue records synchronized
                 * with the driver's updated name and plate.
                 */
                val queueSnapshot =
                    firestore
                        .collection(
                            FirestoreCollections.QUEUE
                        )
                        .whereEqualTo(
                            "driverId",
                            oldProfile.userId
                        )
                        .get()
                        .await()

                val batch =
                    firestore.batch()

                queueSnapshot.documents.forEach {
                        queueDocument ->

                    val status =
                        queueDocument
                            .getString("status")
                            ?.lowercase()

                    val isActive = status !in setOf(
                        "dispatched",
                        "cancelled",
                        "completed",
                        "resolved"
                    )

                    if (isActive) {
                        batch.update(
                            queueDocument.reference,
                            mapOf(
                                "driverName" to
                                        listOf(
                                            cleanFirstName,
                                            cleanLastName
                                        )
                                            .filter {
                                                it.isNotBlank()
                                            }
                                            .joinToString(" ")
                                            .ifBlank {
                                                "Driver"
                                            },

                                "plateNumber" to
                                        cleanPlate,

                                "updatedAt" to
                                        FieldValue
                                            .serverTimestamp()
                            )
                        )
                    }
                }

                batch.commit().await()

                userSession.setUser(
                    oldProfile.copy(
                        firstName =
                            cleanFirstName,

                        lastName =
                            cleanLastName,

                        licenseNumber =
                            cleanLicense,

                        plateNumber =
                            cleanPlate,

                        emergencyContact =
                            cleanEmergency,

                        emergencyContactName =
                            cleanEmergencyName,

                        emergencyContactNumber =
                            cleanEmergencyNumber
                    )
                )

                _uiState.value =
                    _uiState.value.copy(
                        currentView =
                            ProfileSubView.MAIN
                    )

                ToastFeedback.show(
                    "Na-update ang profile."
                )

            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message
                        ?: "Hindi ma-update ang profile. Subukan muli."
                )
            }
        }
    }

    fun savePassword() {

        val phone =
            userSession.userProfile
                ?.phoneNumber
                ?: return

        val state =
            _uiState.value

        if (state.oldPassword.isBlank()) {
            ToastFeedback.show(
                "Ilagay ang kasalukuyang password."
            )
            return
        }

        if (state.newPassword.length < 6) {
            ToastFeedback.show(
                "Dapat hindi bababa sa 6 na karakter ang bagong password."
            )
            return
        }

        if (state.newPassword != state.confirmPassword) {
            ToastFeedback.show(
                "Hindi magkapareho ang bagong password at kumpirmasyon."
            )
            return
        }

        viewModelScope.launch {

            try {

                val userReference =
                    firestore
                        .collection(
                            FirestoreCollections.USERS
                        )
                        .document(phone)

                val secretsReference =
                    firestore
                        .collection(
                            FirestoreCollections.USER_SECRETS
                        )
                        .document(phone)

                val document =
                    userReference.get().await()

                val secrets =
                    secretsReference.get().await()

                val salt =
                    secrets.getString("passwordSalt")

                val savedHash =
                    secrets.getString("passwordHash")

                val passwordMatches = if (
                    !salt.isNullOrBlank() &&
                    !savedHash.isNullOrBlank()
                ) {
                    PasswordHasher.verify(
                        state.oldPassword,
                        salt,
                        savedHash
                    )
                } else {
                    // Legacy account that still has the plaintext field.
                    document.getString("password") == state.oldPassword
                }

                if (!passwordMatches) {
                    ToastFeedback.show(
                        "Mali ang kasalukuyang password."
                    )
                    return@launch
                }

                // Save only a salted hash; never a readable password.
                val salted =
                    PasswordHasher.hash(state.newPassword)

                val batch = firestore.batch()

                batch.set(
                    secretsReference,
                    mapOf(
                        "passwordHash" to salted.hash,
                        "passwordSalt" to salted.salt,
                        "updatedAt" to
                                FieldValue.serverTimestamp()
                    )
                )

                // Remove the old plaintext field if it still exists.
                batch.update(
                    userReference,
                    mapOf(
                        "password" to FieldValue.delete(),
                        "updatedAt" to
                                FieldValue.serverTimestamp()
                    )
                )

                batch.commit().await()

                _uiState.value =
                    _uiState.value.copy(
                        currentView =
                            ProfileSubView.MAIN,
                        oldPassword = "",
                        newPassword = "",
                        confirmPassword = ""
                    )

                ToastFeedback.show(
                    "Matagumpay na napalitan ang password."
                )

            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message
                        ?: "Hindi ma-update ang password. Subukan muli."
                )
            }
        }
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}

/**
 * Reads one of the two emergency contact fields. Older accounts do not
 * have these fields yet, so the value is simply empty and the caller
 * falls back to the old combined value.
 */
private fun readEmergencyContact(
    document: com.google.firebase.firestore.DocumentSnapshot,
    fieldName: String
): String {
    return document.getString(fieldName)
        ?.trim()
        .orEmpty()
}

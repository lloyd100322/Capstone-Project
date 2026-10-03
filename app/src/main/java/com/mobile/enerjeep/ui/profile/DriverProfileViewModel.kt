package com.mobile.enerjeep.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.UserSession
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
    val emergencyContact: String = "",
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

                            emergencyContact =
                                document
                                    .getString(
                                        "emergencyContact"
                                    )
                                    .orEmpty()
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

    fun updateEmergencyContact(
        value: String
    ) {
        _uiState.value =
            _uiState.value.copy(
                emergencyContact = value
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
            return
        }

        val newPhone =
            "+63$cleanPhoneDigits"

        val cleanPlate =
            state.plateNumber
                .trim()
                .uppercase()

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

                val newRef =
                    users.document(
                        newPhone
                    )

                val cleanFirstName =
                    state.firstName.trim()

                val cleanLastName =
                    state.lastName.trim()

                val cleanLicense =
                    state.licenseNumber.trim()

                val cleanEmergency =
                    state.emergencyContact.trim()

                if (newPhone != currentPhone) {

                    if (
                        newRef
                            .get()
                            .await()
                            .exists()
                    ) {
                        return@launch
                    }

                    val newData =
                        mutableMapOf<String, Any>(
                            "userId" to
                                    oldProfile.userId,

                            "firstName" to
                                    cleanFirstName,

                            "lastName" to
                                    cleanLastName,

                            "phoneNumber" to
                                    newPhone,

                            "password" to
                                    oldProfile.password,

                            "role" to
                                    oldProfile.role
                                        .name
                                        .lowercase(),

                            "status" to
                                    "active",

                            "licenseNumber" to
                                    cleanLicense,

                            "plateNumber" to
                                    cleanPlate,

                            "emergencyContact" to
                                    cleanEmergency,

                            "updatedAt" to
                                    FieldValue
                                        .serverTimestamp()
                        )

                    val batch =
                        firestore.batch()

                    batch.set(
                        newRef,
                        newData
                    )

                    batch.delete(
                        oldRef
                    )

                    batch.commit().await()

                    listener?.remove()

                    listener =
                        newRef
                            .addSnapshotListener {
                                    document,
                                    _ ->

                                if (
                                    document != null &&
                                    document.exists()
                                ) {

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
                                                document
                                                    .getString(
                                                        "phoneNumber"
                                                    )
                                                    .orEmpty()
                                                    .removePrefix(
                                                        "+63"
                                                    ),

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

                                            emergencyContact =
                                                document
                                                    .getString(
                                                        "emergencyContact"
                                                    )
                                                    .orEmpty()
                                        )
                                }
                            }

                } else {

                    oldRef
                        .update(
                            mapOf<String, Any>(
                                "firstName" to
                                        cleanFirstName,

                                "lastName" to
                                        cleanLastName,

                                "phoneNumber" to
                                        newPhone,

                                "licenseNumber" to
                                        cleanLicense,

                                "plateNumber" to
                                        cleanPlate,

                                "emergencyContact" to
                                        cleanEmergency,

                                "updatedAt" to
                                        FieldValue
                                            .serverTimestamp()
                            )
                        )
                        .await()
                }

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

                    val isActive =
                        status !in setOf(
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

                        phoneNumber =
                            newPhone,

                        licenseNumber =
                            cleanLicense,

                        plateNumber =
                            cleanPlate,

                        emergencyContact =
                            cleanEmergency
                    )
                )

                _uiState.value =
                    _uiState.value.copy(
                        currentView =
                            ProfileSubView.MAIN
                    )

            } catch (_: Exception) {
                // Keep existing silent-error behavior.
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

        if (
            state.newPassword.isBlank() ||
            state.newPassword !=
            state.confirmPassword
        ) {
            return
        }

        viewModelScope.launch {

            try {

                val document =
                    firestore
                        .collection(
                            FirestoreCollections.USERS
                        )
                        .document(phone)
                        .get()
                        .await()

                if (
                    document.getString(
                        "password"
                    ) != state.oldPassword
                ) {
                    return@launch
                }

                firestore
                    .collection(
                        FirestoreCollections.USERS
                    )
                    .document(phone)
                    .update(
                        "password",
                        state.newPassword
                    )
                    .await()

                val old =
                    userSession.userProfile
                        ?: return@launch

                userSession.setUser(
                    old.copy(
                        password =
                            state.newPassword
                    )
                )

                _uiState.value =
                    _uiState.value.copy(
                        currentView =
                            ProfileSubView.MAIN,
                        oldPassword = "",
                        newPassword = "",
                        confirmPassword = ""
                    )

            } catch (_: Exception) {
                // Keep existing silent-error behavior.
            }
        }
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
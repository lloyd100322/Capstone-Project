package com.mobile.enerjeep.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.mobile.enerjeep.data.EmergencyContact
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.PhilippineIdentifiers
import com.mobile.enerjeep.data.SecureLoginCache
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
    private val userSession: UserSession,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            DriverProfileUiState()
        )

    val uiState: StateFlow<DriverProfileUiState> =
        _uiState.asStateFlow()

    private val _isSavingProfile = MutableStateFlow(false)
    val isSavingProfile = _isSavingProfile.asStateFlow()

    private val _isSavingPassword = MutableStateFlow(false)
    val isSavingPassword = _isSavingPassword.asStateFlow()

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut = _isSigningOut.asStateFlow()

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

        val oldProfile = userSession.userProfile ?: return
        val currentPhone = oldProfile.phoneNumber
        val state = _uiState.value

        val cleanPhoneDigits = state.phone
            .filter { it.isDigit() }
            .take(10)

        if (!PH_PHONE_REGEX.matches(cleanPhoneDigits)) {
            ToastFeedback.show(
                "Ang numero ay dapat 10 digit at nasa format na 9XXXXXXXXX"
            )
            return
        }

        val newPhone = "+63$cleanPhoneDigits"
        if (newPhone != currentPhone) {
            ToastFeedback.show(
                "Hindi muna maaaring palitan ang phone number dito. " +
                    "Kailangan itong dumaan sa phone verification."
            )
            return
        }

        val cleanFirstName = state.firstName.trim()
        val cleanLastName = state.lastName.trim()
        if (cleanFirstName.isBlank() || cleanLastName.isBlank() ||
            cleanFirstName.length > 50 || cleanLastName.length > 50
        ) {
            ToastFeedback.show("Ilagay ang valid na first name at last name.")
            return
        }

        val cleanPlate = state.plateNumber.trim().uppercase()
        val cleanLicense = state.licenseNumber.trim()

        PhilippineIdentifiers.plateNumberError(cleanPlate)?.let { error ->
            ToastFeedback.show(error)
            return
        }
        PhilippineIdentifiers.licenseNumberError(cleanLicense)?.let { error ->
            ToastFeedback.show(error)
            return
        }

        val cleanEmergencyName = state.emergencyContactName.trim()
        val cleanEmergencyNumber = state.emergencyContactNumber.trim()

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

        val combinedEmergency = EmergencyContact.combine(
            cleanEmergencyName,
            cleanEmergencyNumber
        )

        if (_isSavingProfile.value) return
        _isSavingProfile.value = true

        viewModelScope.launch {
            try {
                FirebaseFunctions.getInstance("us-central1")
                    .getHttpsCallable("updateDriverProfile")
                    .call(
                        mapOf(
                            "firstName" to cleanFirstName,
                            "lastName" to cleanLastName,
                            "licenseNumber" to cleanLicense,
                            "plateNumber" to cleanPlate,
                            "emergencyContactName" to cleanEmergencyName,
                            "emergencyContactNumber" to cleanEmergencyNumber,
                            "emergencyContact" to combinedEmergency
                        )
                    )
                    .await()

                userSession.setUser(
                    oldProfile.copy(
                        firstName = cleanFirstName,
                        lastName = cleanLastName,
                        licenseNumber = cleanLicense,
                        plateNumber = cleanPlate,
                        emergencyContact = combinedEmergency,
                        emergencyContactName = cleanEmergencyName,
                        emergencyContactNumber = cleanEmergencyNumber
                    )
                )

                _uiState.value = _uiState.value.copy(
                    currentView = ProfileSubView.MAIN
                )

                ToastFeedback.show("Na-update ang profile.")
            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message ?: "Hindi ma-update ang profile. Subukan muli."
                )
            } finally {
                _isSavingProfile.value = false
            }
        }
    }

    fun savePassword() {
        val phone = userSession.userProfile?.phoneNumber?.trim().orEmpty()
        if (phone.isBlank()) return

        val state = _uiState.value

        if (state.oldPassword.isBlank()) {
            ToastFeedback.show("Ilagay ang kasalukuyang password.")
            return
        }

        if (state.newPassword.length < 6 || state.newPassword.length > 128) {
            ToastFeedback.show("Ang bagong password ay dapat 6 hanggang 128 karakter.")
            return
        }

        if (state.newPassword != state.confirmPassword) {
            ToastFeedback.show("Hindi magkapareho ang bagong password at kumpirmasyon.")
            return
        }

        if (state.newPassword == state.oldPassword) {
            ToastFeedback.show("Pumili ng ibang password kaysa sa kasalukuyan.")
            return
        }

        if (_isSavingPassword.value) return
        _isSavingPassword.value = true

        viewModelScope.launch {
            try {
                val result = FirebaseFunctions.getInstance("us-central1")
                    .getHttpsCallable("changeOwnPassword")
                    .call(
                        mapOf(
                            "oldPassword" to state.oldPassword,
                            "newPassword" to state.newPassword
                        )
                    )
                    .await()

                val response = result.data as? Map<*, *>
                if (response?.get("success") != true) {
                    throw IllegalStateException("Hindi nakumpirma ng server ang password update.")
                }

                // Clear the old password verifier stored on this device.
                SecureLoginCache.clear(phone)

                _uiState.value = _uiState.value.copy(
                    currentView = ProfileSubView.MAIN,
                    oldPassword = "",
                    newPassword = "",
                    confirmPassword = ""
                )

                ToastFeedback.show("Matagumpay na napalitan ang password.")
            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message ?: "Hindi ma-update ang password. Subukan muli."
                )
            } finally {
                _isSavingPassword.value = false
            }
        }
    }

    /**
     * Fully signs out a driver. The root ContentViewModel observes the
     * cleared UserSession and replaces the authenticated NavController,
     * so Android Back cannot return to the previous driver dashboard.
     */
    fun signOut() {
        if (_isSigningOut.value) return
        val phone = userSession.userProfile?.phoneNumber.orEmpty()
        _isSigningOut.value = true

        viewModelScope.launch {
            try {
                auth.signOut()
            } finally {
                // Remove the optional pre-OTP verifier for this account too.
                SecureLoginCache.clear(phone)
                userSession.clear()
                _isSigningOut.value = false
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

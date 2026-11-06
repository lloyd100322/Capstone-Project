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
import com.mobile.enerjeep.data.SecureLoginCache
import com.mobile.enerjeep.ui.components.ToastFeedback
import com.mobile.enerjeep.data.UserRole
import com.mobile.enerjeep.data.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class ProfileData(
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val role: String,
    val emergencyContactName: String = "",
    val emergencyContactNumber: String = ""
) {
    val initials: String
        get() =
            "${firstName.firstOrNull()?.uppercase() ?: ""}" +
                    "${lastName.firstOrNull()?.uppercase() ?: ""}"

    val fullName: String
        get() =
            listOf(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
}

sealed interface ProfileUiState {

    data object Loading : ProfileUiState

    data class Success(
        val profile: ProfileData,
        val updateMessage: String? = null
    ) : ProfileUiState

    data class Error(
        val message: String
    ) : ProfileUiState
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ProfileUiState>(
            ProfileUiState.Loading
        )

    val uiState = _uiState.asStateFlow()

    private val _isUpdatingProfile = MutableStateFlow(false)
    val isUpdatingProfile = _isUpdatingProfile.asStateFlow()

    private val _isUpdatingPassword = MutableStateFlow(false)
    val isUpdatingPassword = _isUpdatingPassword.asStateFlow()

    private val _isSigningOut = MutableStateFlow(false)
    val isSigningOut = _isSigningOut.asStateFlow()

    private var listener: ListenerRegistration? = null

    init {
        loadProfile()
    }

    private fun loadProfile() {

        val phone =
            userSession.userProfile
                ?.phoneNumber
                ?.trim()

        if (phone.isNullOrBlank()) {

            _uiState.value =
                ProfileUiState.Error(
                    "Walang naka-login na user"
                )

            return
        }

        listener =
            firestore
                .collection(
                    FirestoreCollections.USERS
                )
                .document(phone)
                .addSnapshotListener {
                        document,
                        error ->

                    if (error != null) {
                        _uiState.value =
                            ProfileUiState.Error(
                                error.message
                                    ?: "Hindi mabasa ang profile"
                            )

                        return@addSnapshotListener
                    }

                    if (
                        document == null ||
                        !document.exists()
                    ) {
                        _uiState.value =
                            ProfileUiState.Error(
                                "Hindi makita ang user profile"
                            )

                        return@addSnapshotListener
                    }

                    pushFromDocument(
                        document,
                        null
                    )
                }
    }

    fun updateProfile(
        firstName: String,
        lastName: String,
        phoneNumber: String,
        emergencyContactName: String? = null,
        emergencyContactNumber: String? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {

        val oldProfile =
            userSession.userProfile
                ?: return

        /*
         * null = hindi binago ang emergency contact. Kapag binigyan ng
         * value (kahit blank), ang mga ito ang ipapasok sa database.
         * Kapag parehong blank, tatanggalin ang contact (walang contact).
         */
        val cleanEmergencyName =
            emergencyContactName?.trim().orEmpty()

        val cleanEmergencyNumber =
            emergencyContactNumber?.trim().orEmpty()

        val changingEmergency =
            emergencyContactName != null ||
                    emergencyContactNumber != null

        if (
            changingEmergency &&
            (cleanEmergencyName.isNotEmpty() ||
                    cleanEmergencyNumber.isNotEmpty())
        ) {
            if (!EmergencyContact.isValidName(cleanEmergencyName)) {
                _uiState.value = ProfileUiState.Error(
                    "Ang pangalan ng emergency contact ay kailangan at " +
                            "dapat hanggang 50 karakter."
                )
                return
            }

            if (!EmergencyContact.isValidNumber(cleanEmergencyNumber)) {
                _uiState.value = ProfileUiState.Error(
                    "Ang numero ng emergency contact ay dapat valid na " +
                            "telepono (09XXXXXXXXX o +639XXXXXXXXX)."
                )
                return
            }
        }

        val currentPhone =
            oldProfile.phoneNumber.trim()

        val requestedPhone =
            phoneNumber.trim()

        /*
         * Firebase Phone Auth controls the actual authenticated
         * phone number. Changing only the Firestore document would
         * make Firebase Auth and Firestore inconsistent.
         */
        if (
            requestedPhone.isNotBlank() &&
            requestedPhone != currentPhone
        ) {

            _uiState.value =
                ProfileUiState.Error(
                    "Hindi muna maaaring palitan ang phone number dito. Kailangan itong dumaan sa phone verification."
                )

            return
        }

        val first =
            firstName
                .trim()
                .ifBlank {
                    oldProfile.firstName
                }

        val last =
            lastName
                .trim()
                .ifBlank {
                    oldProfile.lastName
                }

        if (_isUpdatingProfile.value) return
        _isUpdatingProfile.value = true

        viewModelScope.launch {
            var succeeded = false
            try {

                val updates = mutableMapOf<String, Any>(
                    "firstName" to first,
                    "lastName" to last,
                    "updatedAt" to FieldValue.serverTimestamp()
                )

                if (changingEmergency) {
                    updates["emergencyContactName"] =
                        cleanEmergencyName

                    updates["emergencyContactNumber"] =
                        cleanEmergencyNumber

                    // The old combined field is kept in sync.
                    updates["emergencyContact"] =
                        EmergencyContact.combine(
                            cleanEmergencyName,
                            cleanEmergencyNumber
                        )
                }

                firestore
                    .collection(
                        FirestoreCollections.USERS
                    )
                    .document(currentPhone)
                    .update(updates)
                    .await()

                userSession.setUser(
                    oldProfile.copy(
                        firstName = first,
                        lastName = last,
                        emergencyContactName =
                            if (changingEmergency) {
                                cleanEmergencyName
                            } else {
                                oldProfile.emergencyContactName
                            },
                        emergencyContactNumber =
                            if (changingEmergency) {
                                cleanEmergencyNumber
                            } else {
                                oldProfile.emergencyContactNumber
                            }
                    )
                )

                pushFromValues(
                    firstName = first,
                    lastName = last,
                    phone = currentPhone,
                    role = oldProfile.role,
                    message =
                        "Profile updated successfully!",
                    emergencyContactName =
                        if (changingEmergency) {
                            cleanEmergencyName
                        } else {
                            oldProfile.emergencyContactName
                                .orEmpty()
                        },
                    emergencyContactNumber =
                        if (changingEmergency) {
                            cleanEmergencyNumber
                        } else {
                            oldProfile.emergencyContactNumber
                                .orEmpty()
                        },
                    adminStation = oldProfile.adminStation
                )

                succeeded = true
            } catch (e: Exception) {
                _uiState.value = ProfileUiState.Error(
                    e.message ?: "Hindi ma-update ang profile"
                )
            } finally {
                _isUpdatingProfile.value = false
                onComplete(succeeded)
            }
        }
    }

    fun updatePassword(
        oldPass: String,
        newPass: String,
        confirmPass: String,
        onSuccess: () -> Unit = {}
    ) {
        if (oldPass.isBlank()) {
            ToastFeedback.show("Ilagay ang kasalukuyang password.")
            return
        }

        if (newPass.length < 6 || newPass.length > 128) {
            ToastFeedback.show("Ang bagong password ay dapat 6 hanggang 128 karakter.")
            return
        }

        if (newPass != confirmPass) {
            ToastFeedback.show("Hindi magkapareho ang bagong password at kumpirmasyon.")
            return
        }

        if (newPass == oldPass) {
            ToastFeedback.show("Pumili ng ibang password kaysa sa kasalukuyan.")
            return
        }

        val phone = userSession.userProfile
            ?.phoneNumber
            ?.trim()
            ?: return

        if (_isUpdatingPassword.value) return
        _isUpdatingPassword.value = true

        viewModelScope.launch {
            try {
                val result = FirebaseFunctions.getInstance("us-central1")
                    .getHttpsCallable("changeOwnPassword")
                    .call(
                        mapOf(
                            "oldPassword" to oldPass,
                            "newPassword" to newPass
                        )
                    )
                    .await()

                val response = result.data as? Map<*, *>
                if (response?.get("success") != true) {
                    throw IllegalStateException("Hindi nakumpirma ng server ang password update.")
                }

                // The cached verifier contains the former password.
                SecureLoginCache.clear(phone)

                val oldProfile = userSession.userProfile
                    ?: return@launch

                pushFromValues(
                    oldProfile.firstName,
                    oldProfile.lastName,
                    oldProfile.phoneNumber,
                    oldProfile.role,
                    "Password updated successfully!",
                    oldProfile.emergencyContactName.orEmpty(),
                    oldProfile.emergencyContactNumber.orEmpty(),
                    oldProfile.adminStation
                )

                ToastFeedback.show("Matagumpay na napalitan ang password.")
                onSuccess()
            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message ?: "Hindi ma-update ang password. Subukan muli."
                )
            } finally {
                _isUpdatingPassword.value = false
            }
        }
    }

    fun signOut() {
        if (_isSigningOut.value) return
        val phone = userSession.userProfile?.phoneNumber.orEmpty()
        _isSigningOut.value = true

        viewModelScope.launch {
            try {
                // Firebase signOut is synchronous and clears its persisted session.
                auth.signOut()
            } finally {
                // Remove the optional pre-OTP verifier for this account too.
                SecureLoginCache.clear(phone)

                // The root ContentViewModel observes this and replaces the
                // entire navigation controller with a fresh Login back stack.
                userSession.clear()
                _isSigningOut.value = false
            }
        }
    }

    fun clearMessage() {

        val current =
            userSession.userProfile
                ?: return

        pushFromValues(
            current.firstName,
            current.lastName,
            current.phoneNumber,
            current.role,
            null,
            current.emergencyContactName.orEmpty(),
            current.emergencyContactNumber.orEmpty(),
            current.adminStation
        )
    }

    private fun pushFromDocument(
        document:
        com.google.firebase.firestore.DocumentSnapshot,
        message: String?
    ) {

        val roleString =
            document
                .getString("role")
                ?: UserRole.Driver.name

        val role =
            UserRole.entries.find {
                it.name.equals(
                    roleString,
                    ignoreCase = true
                )
            } ?: UserRole.Driver

        val legacyEmergency = EmergencyContact.splitLegacy(
            document.getString("emergencyContact")
        )

        pushFromValues(
            document
                .getString("firstName")
                .orEmpty(),

            document
                .getString("lastName")
                .orEmpty(),

            document
                .getString("phoneNumber")
                ?: document.id,

            role,
            message,

            document
                .getString("emergencyContactName")
                ?.takeIf { it.isNotBlank() }
                ?: legacyEmergency.name,

            document
                .getString("emergencyContactNumber")
                ?.takeIf { it.isNotBlank() }
                ?: legacyEmergency.number,
            document.getString("adminStation")
        )
    }

    private fun pushFromValues(
        firstName: String,
        lastName: String,
        phone: String,
        role: UserRole,
        message: String?,
        emergencyContactName: String = "",
        emergencyContactNumber: String = "",
        adminStation: String? = null
    ) {

        _uiState.value =
            ProfileUiState.Success(
                profile = ProfileData(
                    firstName = firstName,
                    lastName = lastName,
                    phoneNumber = phone,
                    role = role.displayName(adminStation),
                    emergencyContactName = emergencyContactName,
                    emergencyContactNumber = emergencyContactNumber
                ),
                updateMessage = message
            )
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
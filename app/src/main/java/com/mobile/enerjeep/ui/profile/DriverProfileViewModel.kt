package com.mobile.enerjeep.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    MAIN, EDIT_PROFILE, SECURITY
}

data class DriverProfileUiState(
    val currentView: ProfileSubView = ProfileSubView.MAIN,
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

    private val _uiState = MutableStateFlow(DriverProfileUiState())
    val uiState: StateFlow<DriverProfileUiState> = _uiState.asStateFlow()
    private var listener: ListenerRegistration? = null

    init {
        loadProfile()
    }

    private fun loadProfile() {
        val phone = userSession.userProfile?.phoneNumber ?: return
        listener = firestore.collection(FirestoreCollections.USERS)
            .document(phone)
            .addSnapshotListener { document, _ ->
                if (document == null || !document.exists()) return@addSnapshotListener
                _uiState.value = _uiState.value.copy(
                    firstName = document.getString("firstName").orEmpty(),
                    lastName = document.getString("lastName").orEmpty(),
                    phone = document.getString("phoneNumber") ?: phone,
                    licenseNumber = document.getString("licenseNumber").orEmpty(),
                    plateNumber = document.getString("plateNumber").orEmpty(),
                    emergencyContact = document.getString("emergencyContact").orEmpty()
                )
            }
    }

    fun setView(view: ProfileSubView) {
        _uiState.value = _uiState.value.copy(currentView = view)
    }

    fun updateFirstName(value: String) { _uiState.value = _uiState.value.copy(firstName = value) }
    fun updateLastName(value: String) { _uiState.value = _uiState.value.copy(lastName = value) }
    fun updatePhone(value: String) { _uiState.value = _uiState.value.copy(phone = value) }
    fun updateLicenseNumber(value: String) { _uiState.value = _uiState.value.copy(licenseNumber = value) }
    fun updatePlateNumber(value: String) { _uiState.value = _uiState.value.copy(plateNumber = value) }
    fun updateEmergencyContact(value: String) { _uiState.value = _uiState.value.copy(emergencyContact = value) }
    fun updateOldPassword(value: String) { _uiState.value = _uiState.value.copy(oldPassword = value) }
    fun updateNewPassword(value: String) { _uiState.value = _uiState.value.copy(newPassword = value) }
    fun updateConfirmPassword(value: String) { _uiState.value = _uiState.value.copy(confirmPassword = value) }

    fun saveProfile() {
        val oldProfile = userSession.userProfile ?: return
        val currentPhone = oldProfile.phoneNumber
        val state = _uiState.value
        val newPhone = state.phone.trim().ifBlank { currentPhone }

        viewModelScope.launch {
            try {
                val users = firestore.collection(FirestoreCollections.USERS)
                val oldRef = users.document(currentPhone)
                val newRef = users.document(newPhone)

                if (newPhone != currentPhone) {
                    if (newRef.get().await().exists()) return@launch
                    val newData = mutableMapOf<String, Any>(
                        "userId" to oldProfile.userId,
                        "firstName" to state.firstName.trim(),
                        "lastName" to state.lastName.trim(),
                        "phoneNumber" to newPhone,
                        "password" to oldProfile.password,
                        "role" to oldProfile.role.name.lowercase(),
                        "status" to "active",
                        "licenseNumber" to state.licenseNumber.trim(),
                        "plateNumber" to state.plateNumber.trim(),
                        "emergencyContact" to state.emergencyContact.trim(),
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                    )
                    val batch = firestore.batch()
                    batch.set(newRef, newData)
                    batch.delete(oldRef)
                    batch.commit().await()
                    listener?.remove()
                    listener = newRef.addSnapshotListener { document, _ ->
                        if (document != null && document.exists()) {
                            _uiState.value = _uiState.value.copy(
                                firstName = document.getString("firstName").orEmpty(),
                                lastName = document.getString("lastName").orEmpty(),
                                phone = document.getString("phoneNumber") ?: newPhone,
                                licenseNumber = document.getString("licenseNumber").orEmpty(),
                                plateNumber = document.getString("plateNumber").orEmpty(),
                                emergencyContact = document.getString("emergencyContact").orEmpty()
                            )
                        }
                    }
                } else {
                    oldRef.update(
                        mapOf<String, Any>(
                            "firstName" to state.firstName.trim(),
                            "lastName" to state.lastName.trim(),
                            "phoneNumber" to newPhone,
                            "licenseNumber" to state.licenseNumber.trim(),
                            "plateNumber" to state.plateNumber.trim(),
                            "emergencyContact" to state.emergencyContact.trim(),
                            "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                userSession.setUser(oldProfile.copy(
                    firstName = state.firstName.trim(),
                    lastName = state.lastName.trim(),
                    phoneNumber = newPhone,
                    licenseNumber = state.licenseNumber.trim(),
                    plateNumber = state.plateNumber.trim(),
                    emergencyContact = state.emergencyContact.trim()
                ))
                _uiState.value = _uiState.value.copy(currentView = ProfileSubView.MAIN)
            } catch (_: Exception) {
            }
        }
    }
    fun savePassword() {
        val phone = userSession.userProfile?.phoneNumber ?: return
        val state = _uiState.value
        if (state.newPassword.isBlank() || state.newPassword != state.confirmPassword) return

        viewModelScope.launch {
            try {
                val document = firestore.collection(FirestoreCollections.USERS).document(phone).get().await()
                if (document.getString("password") != state.oldPassword) return@launch

                firestore.collection(FirestoreCollections.USERS).document(phone)
                    .update("password", state.newPassword)
                    .await()

                val old = userSession.userProfile ?: return@launch
                userSession.setUser(old.copy(password = state.newPassword))
                _uiState.value = _uiState.value.copy(
                    currentView = ProfileSubView.MAIN,
                    oldPassword = "",
                    newPassword = "",
                    confirmPassword = ""
                )
            } catch (_: Exception) {
            }
        }
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}

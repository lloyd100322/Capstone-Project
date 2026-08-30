package com.mobile.enerjeep.ui.profile

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// --- 1. DATA MODEL ---
data class ProfileData(
    val firstName: String,
    val lastName: String,
    val phoneNumber: String,
    val role: String
) {
    val initials: String
        get() = "${firstName.firstOrNull()?.uppercase() ?: ""}${lastName.firstOrNull()?.uppercase() ?: ""}"

    val fullName: String
        get() = "$firstName $lastName"
}

// --- 2. UI STATE ---
sealed interface ProfileUiState {
    data object Loading : ProfileUiState
    data class Success(
        val profile: ProfileData,
        val updateMessage: String? = null // Used for successful save snackbars if needed
    ) : ProfileUiState
    data class Error(val message: String) : ProfileUiState
}

// --- 3. VIEWMODEL ---
@HiltViewModel
class ProfileViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ProfileUiState>(ProfileUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // Mock initial user data (ready to be replaced by your UserSession singleton)
    private var currentUser = ProfileData(
        firstName = "Admin",
        lastName = "User",
        phoneNumber = "9171234567",
        role = "Admin"
    )

    init {
        pushStateToUI()
    }

    private fun pushStateToUI(message: String? = null) {
        _uiState.value = ProfileUiState.Success(
            profile = currentUser,
            updateMessage = message
        )
    }

    // --- BUSINESS ACTIONS ---

    fun updateProfile(firstName: String, lastName: String, phoneNumber: String) {
        // In a real app, you'd send this to Firestore first
        currentUser = currentUser.copy(
            firstName = firstName.ifBlank { currentUser.firstName },
            lastName = lastName.ifBlank { currentUser.lastName },
            phoneNumber = phoneNumber.ifBlank { currentUser.phoneNumber }
        )
        pushStateToUI(message = "Profile updated successfully!")
    }

    fun updatePassword(oldPass: String, newPass: String, confirmPass: String): Boolean {
        if (newPass != confirmPass) {
            _uiState.value = ProfileUiState.Error("Passwords do not match.")
            return false
        }
        // Mock password update logic
        pushStateToUI(message = "Password updated successfully!")
        return true
    }

    fun clearMessage() {
        if (_uiState.value is ProfileUiState.Success) {
            pushStateToUI(null)
        }
    }
}
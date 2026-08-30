package com.mobile.enerjeep.ui.profile

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class ProfileSubView {
    MAIN, EDIT_PROFILE, SECURITY
}

data class DriverProfileUiState(
    val currentView: ProfileSubView = ProfileSubView.MAIN,
    val firstName: String = "Juan",
    val lastName: String = "Dela Cruz",
    val phone: String = "9175551234",
    val licenseNumber: String = "",
    val plateNumber: String = "",
    val emergencyContact: String = "",
    val oldPassword: String = "",
    val newPassword: String = "",
    val confirmPassword: String = ""
)

@HiltViewModel
class DriverProfileViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(DriverProfileUiState())
    val uiState: StateFlow<DriverProfileUiState> = _uiState.asStateFlow()

    fun setView(view: ProfileSubView) {
        _uiState.value = _uiState.value.copy(currentView = view)
    }

    fun updateFirstName(value: String) {
        _uiState.value = _uiState.value.copy(firstName = value)
    }

    fun updateLastName(value: String) {
        _uiState.value = _uiState.value.copy(lastName = value)
    }

    fun updatePhone(value: String) {
        _uiState.value = _uiState.value.copy(phone = value)
    }

    fun updateLicenseNumber(value: String) {
        _uiState.value = _uiState.value.copy(licenseNumber = value)
    }

    fun updatePlateNumber(value: String) {
        _uiState.value = _uiState.value.copy(plateNumber = value)
    }

    fun updateEmergencyContact(value: String) {
        _uiState.value = _uiState.value.copy(emergencyContact = value)
    }

    fun updateOldPassword(value: String) {
        _uiState.value = _uiState.value.copy(oldPassword = value)
    }

    fun updateNewPassword(value: String) {
        _uiState.value = _uiState.value.copy(newPassword = value)
    }

    fun updateConfirmPassword(value: String) {
        _uiState.value = _uiState.value.copy(confirmPassword = value)
    }

    fun saveProfile() {
        // TODO: Save changes to backend/Firebase
        setView(ProfileSubView.MAIN)
    }

    fun savePassword() {
        // TODO: Save password changes to backend/Firebase
        setView(ProfileSubView.MAIN)
    }
}
package com.mobile.enerjeep.ui.login

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

enum class ResetStep {
    PHONE_INPUT,
    OTP_INPUT,
    NEW_PASSWORD,
    SUCCESS
}

data class ResetPasswordUiState(
    val step: ResetStep =
        ResetStep.PHONE_INPUT,

    val phoneNumber: String = "",
    val otp: String = "",
    val newPassword: String = "",
    val confirmPassword: String = ""
)

@HiltViewModel
class ResetPasswordViewModel @Inject constructor() :
    ViewModel() {

    private val _uiState =
        MutableStateFlow(
            ResetPasswordUiState()
        )

    val uiState: StateFlow<ResetPasswordUiState> =
        _uiState.asStateFlow()

    fun updatePhoneNumber(
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
                phoneNumber = cleanPhone
            )
    }

    fun updateOtp(
        value: String
    ) {
        if (
            value.length <= 6 &&
            value.all {
                it.isDigit()
            }
        ) {
            _uiState.value =
                _uiState.value.copy(
                    otp = value
                )
        }
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

    fun navigateToOtp() {

        val phone =
            _uiState.value.phoneNumber

        if (
            phone.length != 10 ||
            !phone.startsWith("9")
        ) {
            return
        }

        /*
         * TODO: Trigger Firebase OTP send here.
         */
        _uiState.value =
            _uiState.value.copy(
                step = ResetStep.OTP_INPUT
            )
    }

    fun navigateToNewPassword() {

        /*
         * TODO: Verify Firebase OTP here.
         */
        _uiState.value =
            _uiState.value.copy(
                step = ResetStep.NEW_PASSWORD
            )
    }

    fun completeReset() {

        /*
         * TODO: Update password in Firestore/Auth here.
         */
        _uiState.value =
            _uiState.value.copy(
                step = ResetStep.SUCCESS
            )
    }

    fun navigateBack() {

        val currentStep =
            _uiState.value.step

        val previousStep =
            when (currentStep) {

                ResetStep.OTP_INPUT ->
                    ResetStep.PHONE_INPUT

                ResetStep.NEW_PASSWORD ->
                    ResetStep.OTP_INPUT

                else ->
                    currentStep
            }

        _uiState.value =
            _uiState.value.copy(
                step = previousStep
            )
    }
}
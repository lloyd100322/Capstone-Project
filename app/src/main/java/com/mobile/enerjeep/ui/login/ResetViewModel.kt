package com.mobile.enerjeep.ui.login

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.ui.components.ToastFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
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
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            ResetPasswordUiState()
        )

    val uiState: StateFlow<ResetPasswordUiState> =
        _uiState.asStateFlow()

    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    // Set only after the OTP was verified for this phone number.
    private var verifiedUserRef: DocumentReference? = null

    private fun fullPhone() = "+63" + _uiState.value.phoneNumber

    fun updatePhoneNumber(value: String) {
        _uiState.value = _uiState.value.copy(
            phoneNumber = value.filter { it.isDigit() }.take(10),
            errorMessage = null
        )
    }

    fun updateOtp(value: String) {
        if (value.length <= 6 && value.all { it.isDigit() }) {
            _uiState.value = _uiState.value.copy(
                otp = value,
                errorMessage = null
            )
        }
    }

    fun updateNewPassword(value: String) {
        _uiState.value = _uiState.value.copy(
            newPassword = value,
            errorMessage = null
        )
    }

    fun updateConfirmPassword(value: String) {
        _uiState.value = _uiState.value.copy(
            confirmPassword = value,
            errorMessage = null
        )
    }

    private fun fail(message: String) {
        _uiState.value = _uiState.value.copy(
            isLoading = false,
            errorMessage = message
        )
    }

    /** Step 1: make sure the account exists, then send the OTP. */
    fun sendOtp(activity: Activity, isResend: Boolean = false) {
        val phone = _uiState.value.phoneNumber

        if (phone.length != 10 || !phone.startsWith("9")) {
            fail("Ang numero ay dapat 10 digit at nasa format na 9XXXXXXXXX")
            return
        }

        if (_uiState.value.isLoading) return

        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                /*
                 * The account document id is the phone number, so the
                 * account can be opened directly by its id. This keeps
                 * the reset working even when the Firestore rules only
                 * allow one account to be read before signing in.
                 */
                val userDoc = firestore
                    .collection(FirestoreCollections.USERS)
                    .document(fullPhone())
                    .get()
                    .await()

                if (!userDoc.exists()) {
                    fail("Walang account na naka-rehistro sa numerong ito.")
                    return@launch
                }

                if (userDoc.getBoolean("isActive") == false) {
                    fail("Hindi aktibo ang account na ito.")
                    return@launch
                }

                val callbacks =
                    object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                        override fun onVerificationCompleted(
                            credential: PhoneAuthCredential
                        ) {
                            // Instant verification / SMS auto-retrieval.
                            confirmCredential(credential, userDoc.reference)
                        }

                        override fun onVerificationFailed(e: FirebaseException) {
                            fail(
                                e.localizedMessage
                                    ?: "Hindi maipadala ang OTP. Subukan muli."
                            )
                        }

                        override fun onCodeSent(
                            id: String,
                            token: PhoneAuthProvider.ForceResendingToken
                        ) {
                            verificationId = id
                            resendToken = token
                            verifiedUserRef = userDoc.reference

                            _uiState.value = _uiState.value.copy(
                                step = ResetStep.OTP_INPUT,
                                otp = "",
                                isLoading = false,
                                errorMessage = null
                            )

                            ToastFeedback.show(
                                if (isResend) "Naipadala muli ang OTP."
                                else "Naipadala ang OTP."
                            )
                        }
                    }

                val builder = PhoneAuthOptions
                    .newBuilder(auth)
                    .setPhoneNumber(fullPhone())
                    .setTimeout(60L, TimeUnit.SECONDS)
                    .setActivity(activity)
                    .setCallbacks(callbacks)

                if (isResend) {
                    resendToken?.let { builder.setForceResendingToken(it) }
                }

                PhoneAuthProvider.verifyPhoneNumber(builder.build())

            } catch (e: Exception) {
                fail(e.message ?: "Hindi ma-check ang account. Subukan muli.")
            }
        }
    }

    fun resendOtp(activity: Activity) = sendOtp(activity, isResend = true)

    /** Step 2: check the 6-digit code. */
    fun verifyOtp() {
        val code = _uiState.value.otp

        if (code.length != 6) {
            fail("Dapat ay 6 na digit ang code.")
            return
        }

        val id = verificationId

        if (id.isNullOrBlank()) {
            fail("Wala nang aktibong OTP session. Ipadala muli ang OTP.")
            return
        }

        val ref = verifiedUserRef ?: run {
            fail("Bumalik at ilagay muli ang numero.")
            return
        }

        confirmCredential(PhoneAuthProvider.getCredential(id, code), ref)
    }

    private fun confirmCredential(
        credential: PhoneAuthCredential,
        userRef: DocumentReference
    ) {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            errorMessage = null
        )

        viewModelScope.launch {
            try {
                val result = auth.signInWithCredential(credential).await()

                // The verified phone must be the account we are resetting.
                val verifiedPhone = result.user?.phoneNumber
                if (verifiedPhone != null && verifiedPhone != fullPhone()) {
                    endVerifiedSession()
                    fail("Hindi tugma ang na-verify na numero.")
                    return@launch
                }

                // Firestore only accepts the password write from a signed-in
                // user (this is exactly what the Profile tab does). Stay signed
                // in with the OTP-verified phone until the password is saved,
                // then endVerifiedSession() signs out again.
                verifiedUserRef = userRef

                _uiState.value = _uiState.value.copy(
                    step = ResetStep.NEW_PASSWORD,
                    isLoading = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                endVerifiedSession()
                fail("Maling o expired na OTP. Subukan muli.")
            }
        }
    }

    /** Step 3: save the new password. */
    fun completeReset() {
        val state = _uiState.value

        if (state.isLoading) return

        if (state.newPassword.length < 6) {
            fail("Dapat hindi bababa sa 6 na karakter ang password.")
            return
        }

        if (state.newPassword != state.confirmPassword) {
            fail("Hindi magkapareho ang mga password.")
            return
        }

        val ref = verifiedUserRef ?: run {
            fail("Hindi pa na-verify ang numero. Magsimula muli.")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                ref.update(
                    mapOf(
                        "password" to state.newPassword,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                // Password saved: end the temporary OTP session so the app
                // never keeps this phone logged in without the password.
                endVerifiedSession()

                _uiState.value = _uiState.value.copy(
                    step = ResetStep.SUCCESS,
                    isLoading = false,
                    newPassword = "",
                    confirmPassword = "",
                    otp = ""
                )
            } catch (e: Exception) {
                val denied = e is FirebaseFirestoreException &&
                        e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED

                fail(
                    if (denied) {
                        "Hindi pinayagan ng Firestore rules ang pagpalit ng " +
                                "password. Ipaalam sa developer."
                    } else {
                        e.message ?: "Hindi ma-save ang bagong password."
                    }
                )
            }
        }
    }

    /** Signs out the temporary OTP session and forgets the verification. */
    private fun endVerifiedSession() {
        verifiedUserRef = null
        verificationId = null
        auth.signOut()
    }

    override fun onCleared() {
        // Leaving the screen must never leave a verified session behind.
        if (_uiState.value.step != ResetStep.PHONE_INPUT) {
            auth.signOut()
        }
        super.onCleared()
    }

    fun navigateBack() {
        val previousStep = when (_uiState.value.step) {
            ResetStep.OTP_INPUT -> ResetStep.PHONE_INPUT
            // Verified already; going back to OTP would be meaningless.
            ResetStep.NEW_PASSWORD -> ResetStep.PHONE_INPUT
            else -> _uiState.value.step
        }

        if (previousStep == ResetStep.PHONE_INPUT) {
            endVerifiedSession()
        }

        _uiState.value = _uiState.value.copy(
            step = previousStep,
            errorMessage = null,
            isLoading = false
        )
    }
}

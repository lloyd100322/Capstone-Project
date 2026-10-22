
package com.mobile.enerjeep.ui.login

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.enerjeep.data.UserProfile
import com.mobile.enerjeep.data.UserRole
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.ui.components.ToastFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<LoginUiState>(LoginUiState.Idle)

    val uiState = _uiState.asStateFlow()

    private var verificationId: String? = null
    private var resendingToken:
            PhoneAuthProvider.ForceResendingToken? = null

    private var pendingProfile: UserProfile? = null
    private var currentPhoneNumber: String? = null
    private var resendAllowedAtMillis: Long = 0L

    companion object {
        private val PH_PHONE_REGEX = Regex("^\\+639\\d{9}$")
        private const val RESEND_COOLDOWN_MILLIS = 30_000L
    }

    fun sendVerificationCode(
        phoneNumber: String,
        password: String,
        activity: Activity
    ) {
        val cleanPhoneNumber = phoneNumber.trim()

        if (!PH_PHONE_REGEX.matches(cleanPhoneNumber)) {
            _uiState.value = LoginUiState.Error(
                "Ang numero ay dapat 10 digit at nasa format na 9XXXXXXXXX"
            )
            return
        }

        if (password.isBlank()) {
            _uiState.value = LoginUiState.Error(
                "Pakilagay ang iyong password"
            )
            return
        }

        if (_uiState.value is LoginUiState.Loading) return

        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            try {
                /*
                 * The account document id is the phone number, so the
                 * account can be opened directly by its id. This keeps
                 * the login working even when the Firestore rules only
                 * allow one account to be read before signing in.
                 */
                val userDocument = firestore
                    .collection("users")
                    .document(cleanPhoneNumber)
                    .get()
                    .await()

                if (!userDocument.exists()) {
                    _uiState.value = LoginUiState.Error(
                        "Maling numero o password"
                    )
                    return@launch
                }

                val data = userDocument.data

                if (data?.get("password") as? String != password) {
                    _uiState.value = LoginUiState.Error(
                        "Maling numero o password"
                    )
                    return@launch
                }

                val roleString =
                    data?.get("role") as? String
                        ?: UserRole.Driver.name

                val isDriver = roleString.equals(
                    "driver",
                    ignoreCase = true
                )

                val accountStatus =
                    data?.get("status") as? String

                if (
                    !isDriver &&
                    accountStatus.equals(
                        "inactive",
                        ignoreCase = true
                    )
                ) {
                    _uiState.value = LoginUiState.Error(
                        "Hindi aktibo ang account na ito."
                    )
                    return@launch
                }

                val matchedRole = UserRole.entries.find {
                    it.name.equals(
                        roleString,
                        ignoreCase = true
                    )
                } ?: UserRole.Driver

                pendingProfile = UserProfile(
                    userId = data?.get("userId") as? String ?: "",
                    firstName = data?.get("firstName") as? String ?: "",
                    lastName = data?.get("lastName") as? String ?: "",
                    phoneNumber = data?.get("phoneNumber") as? String ?: "",
                    password = data?.get("password") as? String ?: "",
                    role = matchedRole,
                    licenseNumber = data?.get("licenseNumber") as? String,
                    plateNumber = data?.get("plateNumber") as? String,
                    emergencyContact =
                        data?.get("emergencyContact") as? String,
                    emergencyContactName =
                        data?.get("emergencyContactName") as? String,
                    emergencyContactNumber =
                        data?.get("emergencyContactNumber") as? String
                )

                currentPhoneNumber = cleanPhoneNumber

                triggerSmsVerification(
                    phoneNumber = cleanPhoneNumber,
                    activity = activity,
                    isResend = false
                )
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(
                    "Hindi ma-verify ang account: " +
                            (e.message ?: "Pakisubukan muli.")
                )
            }
        }
    }

    fun resendVerificationCode(activity: Activity) {
        if (
            _uiState.value is LoginUiState.ResendingOtp ||
            _uiState.value is LoginUiState.VerifyingOtp
        ) {
            return
        }

        val phoneNumber = currentPhoneNumber

        if (phoneNumber.isNullOrBlank()) {
            _uiState.value = LoginUiState.Error(
                "Nawala ang session. Mag-login muli."
            )
            return
        }

        val millisRemaining =
            resendAllowedAtMillis - System.currentTimeMillis()

        if (millisRemaining > 0L) {
            val secondsRemaining =
                (millisRemaining + 999L) / 1000L

            ToastFeedback.show(
                "Maghintay ng $secondsRemaining segundo bago muling magpadala ng OTP."
            )
            return
        }

        triggerSmsVerification(
            phoneNumber = phoneNumber,
            activity = activity,
            forceResendingToken = resendingToken,
            isResend = true
        )
    }

    private fun triggerSmsVerification(
        phoneNumber: String,
        activity: Activity,
        forceResendingToken:
        PhoneAuthProvider.ForceResendingToken? = null,
        isResend: Boolean
    ) {
        _uiState.value = if (isResend) {
            LoginUiState.ResendingOtp
        } else {
            LoginUiState.Loading
        }

        val callbacks =
            object :
                PhoneAuthProvider.OnVerificationStateChangedCallbacks() {

                override fun onVerificationCompleted(
                    credential: PhoneAuthCredential
                ) {
                    signInWithPhoneAuthCredential(credential)
                }

                override fun onVerificationFailed(
                    e: FirebaseException
                ) {
                    Log.e(
                        "PhoneAuth",
                        "Phone verification failed",
                        e
                    )

                    val message = e.message
                        ?: "Hindi maipadala ang OTP."

                    ToastFeedback.show(message)

                    _uiState.value =
                        if (isResend && verificationId != null) {
                            LoginUiState.OtpError(message)
                        } else {
                            LoginUiState.Error(message)
                        }
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    this@LoginViewModel.verificationId =
                        verificationId

                    this@LoginViewModel.resendingToken = token

                    resendAllowedAtMillis =
                        System.currentTimeMillis() +
                                RESEND_COOLDOWN_MILLIS

                    _uiState.value = LoginUiState.CodeSent

                    ToastFeedback.show(
                        if (isResend) {
                            "Naipadala muli ang OTP."
                        } else {
                            "Naipadala ang OTP."
                        }
                    )
                }
            }

        val builder = PhoneAuthOptions
            .newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)

        if (forceResendingToken != null) {
            builder.setForceResendingToken(forceResendingToken)
        }

        PhoneAuthProvider.verifyPhoneNumber(
            builder.build()
        )
    }

    fun verifyOtp(otp: String) {
        if (
            _uiState.value is LoginUiState.VerifyingOtp ||
            _uiState.value is LoginUiState.ResendingOtp
        ) {
            return
        }

        val cleanOtp = otp.filter { it.isDigit() }

        if (cleanOtp.length != 6) {
            _uiState.value = LoginUiState.OtpError(
                "Dapat ay 6 na digit ang code."
            )
            return
        }

        val id = verificationId

        if (id.isNullOrBlank()) {
            _uiState.value = LoginUiState.OtpError(
                "Wala nang aktibong OTP session. Ipadala muli ang OTP."
            )
            return
        }

        val credential = PhoneAuthProvider.getCredential(
            id,
            cleanOtp
        )

        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(
        credential: PhoneAuthCredential
    ) {
        viewModelScope.launch {
            // Keep the OTP screen visible while verification runs.
            _uiState.value = LoginUiState.VerifyingOtp

            try {
                auth.signInWithCredential(credential).await()

                pendingProfile?.let {
                    userSession.setUser(it)
                }

                _uiState.value = LoginUiState.Success
            } catch (e: Exception) {
                val message = e.message
                    ?: "Hindi ma-verify ang code. Pakisubukan muli."

                ToastFeedback.show(message)

                // Return to the OTP form instead of the login form.
                _uiState.value = LoginUiState.OtpError(message)
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
        verificationId = null
        resendingToken = null
        pendingProfile = null
        currentPhoneNumber = null
        resendAllowedAtMillis = 0L
    }
}

sealed interface LoginUiState {
    data object Idle : LoginUiState
    data object Loading : LoginUiState
    data object CodeSent : LoginUiState
    data object ResendingOtp : LoginUiState
    data object VerifyingOtp : LoginUiState
    data object Success : LoginUiState

    data class Error(
        val message: String
    ) : LoginUiState

    data class OtpError(
        val message: String
    ) : LoginUiState
}

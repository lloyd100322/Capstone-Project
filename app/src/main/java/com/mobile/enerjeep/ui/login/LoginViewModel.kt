
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
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.enerjeep.data.EmergencyContact
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.PasswordHasher
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

    // Typed on the login form and checked AFTER the OTP is verified.
    private var pendingPassword: String? = null
    private var currentPhoneNumber: String? = null
    private var resendAllowedAtMillis: Long = 0L

    companion object {
        private val PH_PHONE_REGEX = Regex("^\\+639\\d{9}$")
        private const val RESEND_COOLDOWN_MILLIS = 30_000L
    }

    /**
     * Step 1: send the OTP.
     *
     * Nothing is read from Firestore before the OTP is verified. The
     * password is only checked after Firebase Auth has proven that the
     * caller really owns the phone number (and the app is then allowed
     * to read the account). A stolen password alone can no longer be
     * used to probe the database.
     */
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

        pendingPassword = password
        currentPhoneNumber = cleanPhoneNumber

        viewModelScope.launch {
            /*
             * Password check MUNA bago magpadala ng OTP: hindi dapat
             * gumastos ng SMS kapag mali ang password. get lang ang
             * userSecrets (walang listahan - kailangang alam ang
             * numero). null = walang maayos na hash (legacy o offline),
             * kaya tuloy sa OTP; ang post-OTP verify pa rin ang
             * authoritative at buo.
             */
            _uiState.value = LoginUiState.Loading

            val preCheck = preCheckPassword(cleanPhoneNumber, password)

            if (preCheck == false) {
                _uiState.value = LoginUiState.Error(
                    "Maling numero o password. Subukan muli."
                )
                return@launch
            }

            triggerSmsVerification(
                phoneNumber = cleanPhoneNumber,
                activity = activity,
                isResend = false
            )
        }
    }

    /**
     * Pre-login password check laban sa userSecrets (PBKDF2). Read-
     * only: walang anumang ina-upgrade o inililipat bago mag-OTP.
     * true = tama; false = mali; null = walang matse-check (legacy
     * o error) - magpapatuloy sa OTP path.
     */
    private suspend fun preCheckPassword(
        phoneNumber: String,
        typedPassword: String
    ): Boolean? {
        return try {
            val secrets = firestore
                .collection(FirestoreCollections.USER_SECRETS)
                .document(phoneNumber)
                .get()
                .await()

            val salt = secrets.getString("passwordSalt")
            val hash = secrets.getString("passwordHash")

            if (salt.isNullOrBlank() || hash.isNullOrBlank()) return null

            val storedIterations = secrets.getLong("iterations")?.toInt()
                ?: PasswordHasher.LEGACY_ITERATIONS

            if (PasswordHasher.verify(
                    typedPassword,
                    salt,
                    hash,
                    storedIterations
                )
            ) {
                return true
            }

            /*
             * Stale-hash fallback: kung may legacy plaintext password
             * pa sa profile at tugma ito, huwag i-deny dito - hayaan ang
             * OTP path na ayusin ang hash (self-heal sa post-OTP verify).
             * Ang "false" ay para lang sa TALAGANG mali ang password.
             */
            val legacy = firestore
                .collection(FirestoreCollections.USERS)
                .document(phoneNumber)
                .get()
                .await()
                .getString("password")

            if (!legacy.isNullOrBlank()) {
                return if (legacy == typedPassword) null else false
            }

            false
        } catch (_: Exception) {
            null
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

    /**
     * Step 2: verify the OTP with Firebase Auth, then (as an
     * authenticated user) verify the password and load the profile.
     */
    private fun signInWithPhoneAuthCredential(
        credential: PhoneAuthCredential
    ) {
        viewModelScope.launch {
            // Keep the OTP screen visible while verification runs.
            _uiState.value = LoginUiState.VerifyingOtp

            try {
                auth.signInWithCredential(credential).await()
            } catch (e: Exception) {
                Log.e("PhoneAuth", "OTP sign-in failed", e)
                ToastFeedback.show(
                    "Maling o expired na OTP. Pakisubukan muli."
                )
                _uiState.value = LoginUiState.OtpError(
                    "Maling o expired na OTP. Pakisubukan muli."
                )
                return@launch
            }

            // --- From here on the caller really owns the phone number. ---

            try {
                val phone = auth.currentUser?.phoneNumber

                if (phone.isNullOrBlank()) {
                    auth.signOut()
                    _uiState.value = LoginUiState.Error(
                        "Hindi ma-verify ang account. Pakisubukan muli."
                    )
                    return@launch
                }

                val userDocument = firestore
                    .collection(FirestoreCollections.USERS)
                    .document(phone)
                    .get()
                    .await()

                val data = userDocument.data

                if (!userDocument.exists() || data == null) {
                    auth.signOut()
                    _uiState.value = LoginUiState.Error(
                        "Maling numero o password. Subukan muli."
                    )
                    return@launch
                }

                val typedPassword = pendingPassword.orEmpty()

                if (!verifyPassword(phone, typedPassword, userDocument.reference, data)) {
                    auth.signOut()
                    _uiState.value = LoginUiState.Error(
                        "Maling numero o password. Subukan muli."
                    )
                    return@launch
                }

                val roleString =
                    data["role"] as? String
                        ?: UserRole.Driver.name

                val isDriver = roleString.equals(
                    "driver",
                    ignoreCase = true
                )

                val accountStatus =
                    data["status"] as? String

                if (
                    !isDriver &&
                    accountStatus.equals(
                        "inactive",
                        ignoreCase = true
                    )
                ) {
                    auth.signOut()
                    _uiState.value = LoginUiState.Error(
                        "Hindi aktibo ang account na ito. Kontakin ang admin."
                    )
                    return@launch
                }

                val matchedRole = UserRole.entries.find {
                    it.name.equals(
                        roleString,
                        ignoreCase = true
                    )
                } ?: UserRole.Driver

                /*
                 * The emergency contact is read from the two dedicated
                 * fields. Older accounts only have the combined
                 * "emergencyContact" value, so that one is split as a
                 * fallback and the dialog never shows up empty.
                 */
                val legacyEmergency = EmergencyContact.splitLegacy(
                    data["emergencyContact"] as? String
                )

                userSession.setUser(UserProfile(
                    userId = data["userId"] as? String ?: "",
                    firstName = data["firstName"] as? String ?: "",
                    lastName = data["lastName"] as? String ?: "",
                    phoneNumber = data["phoneNumber"] as? String ?: "",
                    role = matchedRole,
                    licenseNumber = data["licenseNumber"] as? String,
                    plateNumber = data["plateNumber"] as? String,
                    emergencyContact =
                        data["emergencyContact"] as? String,
                    emergencyContactName =
                        (data["emergencyContactName"] as? String)
                            ?.takeIf { it.isNotBlank() }
                            ?: legacyEmergency.name.takeIf { it.isNotBlank() },
                    emergencyContactNumber =
                        (data["emergencyContactNumber"] as? String)
                            ?.takeIf { it.isNotBlank() }
                            ?: legacyEmergency.number.takeIf { it.isNotBlank() },
                    adminStation = data["adminStation"] as? String
                ))

                _uiState.value = LoginUiState.Success
            } catch (e: Exception) {
                Log.e("PhoneAuth", "Post-login account load failed", e)
                auth.signOut()
                _uiState.value = LoginUiState.Error(
                    "Hindi ma-load ang account. Pakisubukan muli."
                )
            }
        }
    }

    /**
     * Checks the typed password against the salted hash in
     * /userSecrets. Older accounts still have a plaintext `password`
     * field on the profile document; those are verified once and
     * immediately migrated to a hash (the plaintext field is deleted).
     * Hashes written at the older, weaker work factor are silently
     * upgraded to the current one after a successful login.
     */
    private suspend fun verifyPassword(
        phone: String,
        typedPassword: String,
        userReference: DocumentReference,
        data: Map<String, Any>
    ): Boolean {
        val secrets = firestore
            .collection(FirestoreCollections.USER_SECRETS)
            .document(phone)
            .get()
            .await()

        val salt = secrets.getString("passwordSalt")
        val hash = secrets.getString("passwordHash")

        // Documents carry the work factor they were written with;
        // documents from before the field existed used the legacy one.
        val storedIterations = secrets.getLong("iterations")?.toInt()
            ?: PasswordHasher.LEGACY_ITERATIONS

        if (!salt.isNullOrBlank() && !hash.isNullOrBlank()) {
            val matched = PasswordHasher.verify(
                typedPassword, salt, hash, storedIterations
            )

            if (matched) {
                if (storedIterations < PasswordHasher.ITERATIONS) {
                    try {
                        persistPasswordHash(phone, typedPassword, userReference)
                    } catch (e: Exception) {
                        // Login still succeeds; the next login tries again.
                        Log.w("Login", "Password upgrade failed", e)
                    }
                }
                return true
            }

            /*
             * Self-heal: kung SIRA o LUMA ang hash sa userSecrets pero
             * tugma pa rin ang legacy plaintext password sa profile,
             * palitan ang hash (migrate) at hayaang pumasok ang user -
             * hindi dapat ma-lockout ang account dahil sa sirang hash.
             * Kung hindi rin tugma ang legacy, talagang mali ang password.
             */
            val legacyMatch = data["password"] as? String
            if (!legacyMatch.isNullOrBlank() && legacyMatch == typedPassword) {
                try {
                    persistPasswordHash(phone, typedPassword, userReference)
                } catch (e: Exception) {
                    // Login still succeeds; the next login tries again.
                    Log.w("Login", "Password self-heal failed", e)
                }
                return true
            }
            return false
        }

        // Legacy plaintext account: verify, then migrate to a hash.
        val legacy = data["password"] as? String ?: return false
        val matched = legacy == typedPassword

        if (matched) {
            try {
                persistPasswordHash(phone, typedPassword, userReference)
            } catch (e: Exception) {
                // Login still succeeds; the next login tries again.
                Log.w("Login", "Password migration failed", e)
            }
        }

        return matched
    }

    /**
     * Writes the salted hash to /userSecrets (at the current work
     * factor) and removes any leftover plaintext password from the
     * profile document.
     */
    private suspend fun persistPasswordHash(
        phone: String,
        plainPassword: String,
        userReference: DocumentReference
    ) {
        val salted = PasswordHasher.hash(plainPassword)

        val batch = firestore.batch()

        batch.set(
            firestore
                .collection(FirestoreCollections.USER_SECRETS)
                .document(phone),
            mapOf(
                "passwordHash" to salted.hash,
                "passwordSalt" to salted.salt,
                "iterations" to salted.iterations,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        // Remove the plaintext password from the profile document.
        batch.update(
            userReference,
            mapOf("password" to FieldValue.delete())
        )

        batch.commit().await()
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
        verificationId = null
        resendingToken = null
        pendingPassword = null
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

package com.mobile.enerjeep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.enerjeep.data.EmergencyContact
import com.mobile.enerjeep.data.UserProfile
import com.mobile.enerjeep.data.UserRole
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.navigation.Screen
import com.mobile.enerjeep.ui.components.ToastFeedback
import com.mobile.enerjeep.ui.queue.QueueRotation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject



@HiltViewModel
class ContentViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _authState = MutableStateFlow<AppAuthState>(AppAuthState.Loading)
    val authState = _authState.asStateFlow()

    init {
        checkSession()
    }

    /** Re-runs the session check (used by the retry button). */
    fun retry() {
        checkSession()
    }

    private var rotationWatchStarted = false

    /**
     * Catch-up ng awtomatikong reset agad sa pag-login, tapos isang
     * watch habang naka-bukas ang app. Idempotent (isang beses lang
     * bawat session).
     */
    private fun startQueueRotation() {
        if (rotationWatchStarted) return
        rotationWatchStarted = true

        viewModelScope.launch {
            while (isActive) {
                val hour = try {
                    val result = QueueRotation.rotateOnce(firestore)
                    if (result.rotated) {
                        ToastFeedback.show(
                            "Na-reset na ang pila: ang dating #1 ay nasa huli na."
                        )
                    }
                    result.cutoffHour
                } catch (_: Exception) {
                    // Best effort - susubukan sa susunod na tikso.
                    QueueRotation.CUTOFF_HOUR
                }
                delay(QueueRotation.millisUntilNextCutoff(hour))
            }
        }
    }

    private fun checkSession() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            _authState.value = AppAuthState.Unauthenticated
            return
        }

        _authState.value = AppAuthState.Loading

        viewModelScope.launch {
            try {
                // Phone number in Firebase Auth is usually +63...
                val phoneNumber = currentUser.phoneNumber ?: run {
                    auth.signOut()
                    _authState.value = AppAuthState.Unauthenticated
                    return@launch
                }

                val doc = firestore.collection("users")
                    .document(phoneNumber.lowercase())
                    .get()
                    .await()

                if (doc.exists()) {
                    val data = doc.data
                    val roleString = data?.get("role") as? String ?: UserRole.Driver.name
                    val matchedRole = UserRole.entries.find { 
                        it.name.lowercase() == roleString.lowercase() 
                    } ?: UserRole.Driver

                    // Same fallback as the login flow: old accounts only
                    // carry the combined "emergencyContact" value.
                    val legacyEmergency = EmergencyContact.splitLegacy(
                        data?.get("emergencyContact") as? String
                    )

                    val profile = UserProfile(
                        userId = data?.get("userId") as? String ?: "",
                        firstName = data?.get("firstName") as? String ?: "",
                        lastName = data?.get("lastName") as? String ?: "",
                        phoneNumber = data?.get("phoneNumber") as? String ?: "",
                        role = matchedRole,
                        licenseNumber = data?.get("licenseNumber") as? String,
                        plateNumber = data?.get("plateNumber") as? String,
                        emergencyContact = data?.get("emergencyContact") as? String,
                        emergencyContactName =
                            (data?.get("emergencyContactName") as? String)
                                ?.takeIf { it.isNotBlank() }
                                ?: legacyEmergency.name.takeIf { it.isNotBlank() },
                        emergencyContactNumber =
                            (data?.get("emergencyContactNumber") as? String)
                                ?.takeIf { it.isNotBlank() }
                                ?: legacyEmergency.number.takeIf { it.isNotBlank() }
                    )
                    
                    userSession.setUser(profile)

                    /*
                     * AWTOMATIKONG RESET NG PILA - tumatakbo AGAD sa
                     * pag-login ng admin/superadmin (kahit hindi sa Pila
                     * screen at kahit ADMIN lang ang naka-login). May
                     * watch din habang naka-bukas ang app kaya nagre-reset
                     * ito kahit nasa Home lang ang user.
                     */
                    if (matchedRole != UserRole.Driver) {
                        startQueueRotation()
                    }

                    _authState.value = AppAuthState.Authenticated(Screen.Home)
                } else {
                    // Profile missing in Firestore, force re-login
                    auth.signOut()
                    _authState.value = AppAuthState.Unauthenticated
                }
            } catch (e: Exception) {
                /*
                 * A network problem is NOT a logout. The Firebase session
                 * is still valid, so show an error with a retry button
                 * instead of throwing the user back to the login form
                 * (which used to leave a half-signed-in state behind).
                 */
                _authState.value = AppAuthState.Error(
                    "Hindi ma-load ang account. Pakisubukan muli."
                )
            }
        }
    }
}

sealed interface AppAuthState {
    data object Loading : AppAuthState
    data object Unauthenticated : AppAuthState
    data class Authenticated(val startDestination: Screen) : AppAuthState
    data class Error(val message: String) : AppAuthState
}

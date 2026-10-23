package com.mobile.enerjeep.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.ui.components.ToastFeedback
import com.mobile.enerjeep.data.UserRole
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.navigation.AppNavigator
import com.mobile.enerjeep.navigation.Screen
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
    val role: String
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
    private val appNavigator: AppNavigator
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<ProfileUiState>(
            ProfileUiState.Loading
        )

    val uiState = _uiState.asStateFlow()

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
        phoneNumber: String
    ) {

        val oldProfile =
            userSession.userProfile
                ?: return

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

        viewModelScope.launch {

            try {

                firestore
                    .collection(
                        FirestoreCollections.USERS
                    )
                    .document(currentPhone)
                    .update(
                        mapOf(
                            "firstName" to first,
                            "lastName" to last,
                            "updatedAt" to
                                    FieldValue.serverTimestamp()
                        )
                    )
                    .await()

                userSession.setUser(
                    oldProfile.copy(
                        firstName = first,
                        lastName = last
                    )
                )

                pushFromValues(
                    firstName = first,
                    lastName = last,
                    phone = currentPhone,
                    role = oldProfile.role,
                    message =
                        "Profile updated successfully!"
                )

            } catch (e: Exception) {

                _uiState.value =
                    ProfileUiState.Error(
                        e.message
                            ?: "Hindi ma-update ang profile"
                    )
            }
        }
    }

    fun updatePassword(
        oldPass: String,
        newPass: String,
        confirmPass: String,
        onSuccess: () -> Unit = {}
    ) {

        if (newPass.length < 6) {

            ToastFeedback.show(
                "Dapat hindi bababa sa 6 na karakter ang bagong password."
            )

            return
        }

        if (newPass != confirmPass) {

            ToastFeedback.show(
                "Hindi magkapareho ang bagong password at kumpirmasyon."
            )

            return
        }

        val phone =
            userSession.userProfile
                ?.phoneNumber
                ?.trim()
                ?: return

        viewModelScope.launch {

            try {

                val document =
                    firestore
                        .collection(
                            FirestoreCollections.USERS
                        )
                        .document(phone)
                        .get()
                        .await()

                val savedPassword =
                    document
                        .getString("password")

                if (savedPassword != oldPass) {

                    ToastFeedback.show(
                        "Mali ang kasalukuyang password."
                    )

                    return@launch
                }

                firestore
                    .collection(
                        FirestoreCollections.USERS
                    )
                    .document(phone)
                    .update(
                        mapOf(
                            "password" to newPass,
                            "updatedAt" to
                                    FieldValue.serverTimestamp()
                        )
                    )
                    .await()

                val oldProfile =
                    userSession.userProfile
                        ?: return@launch

                userSession.setUser(
                    oldProfile.copy(
                        password = newPass
                    )
                )

                pushFromValues(
                    oldProfile.firstName,
                    oldProfile.lastName,
                    oldProfile.phoneNumber,
                    oldProfile.role,
                    "Password updated successfully!"
                )

                ToastFeedback.show(
                    "Matagumpay na napalitan ang password."
                )

                // Only leave the Security screen once the save really finished.
                onSuccess()

            } catch (e: Exception) {

                ToastFeedback.show(
                    e.message
                        ?: "Hindi ma-update ang password"
                )
            }
        }
    }

    fun signOut() {

        viewModelScope.launch {

            try {
                auth.signOut()
            } finally {

                userSession.clear()

                appNavigator.navigateTo(
                    Screen.Login
                ) {
                    popUpTo(Screen.Home) {
                        inclusive = true
                    }
                }
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
            null
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
            message
        )
    }

    private fun pushFromValues(
        firstName: String,
        lastName: String,
        phone: String,
        role: UserRole,
        message: String?
    ) {

        _uiState.value =
            ProfileUiState.Success(
                profile = ProfileData(
                    firstName = firstName,
                    lastName = lastName,
                    phoneNumber = phone,
                    role = role.name
                ),
                updateMessage = message
            )
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}
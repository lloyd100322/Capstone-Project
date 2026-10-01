package com.mobile.enerjeep.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
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
    val role: String
) {

    val initials: String
        get() =
            "${firstName.firstOrNull()?.uppercase() ?: ""}" +
                    "${lastName.firstOrNull()?.uppercase() ?: ""}"

    val fullName: String
        get() = "$firstName $lastName"
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
    private val userSession: UserSession
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
            userSession.userProfile?.phoneNumber

        if (phone.isNullOrBlank()) {
            _uiState.value =
                ProfileUiState.Error(
                    "Walang naka-login na user"
                )
            return
        }

        listener =
            firestore
                .collection(FirestoreCollections.USERS)
                .document(phone)
                .addSnapshotListener { document, error ->

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
            oldProfile.phoneNumber

        val newPhone =
            phoneNumber
                .ifBlank { currentPhone }
                .trim()

        val first =
            firstName
                .ifBlank { oldProfile.firstName }
                .trim()

        val last =
            lastName
                .ifBlank { oldProfile.lastName }
                .trim()

        viewModelScope.launch {

            try {

                val users =
                    firestore.collection(
                        FirestoreCollections.USERS
                    )

                val oldRef =
                    users.document(currentPhone)

                val newRef =
                    users.document(newPhone)

                if (newPhone != currentPhone) {

                    if (
                        newRef
                            .get()
                            .await()
                            .exists()
                    ) {

                        _uiState.value =
                            ProfileUiState.Error(
                                "May account nang gumagamit ng bagong numero"
                            )

                        return@launch
                    }

                    val newData =
                        mutableMapOf<String, Any>(
                            "userId" to oldProfile.userId,
                            "firstName" to first,
                            "lastName" to last,
                            "phoneNumber" to newPhone,
                            "password" to oldProfile.password,
                            "role" to oldProfile.role.name.lowercase(),
                            "status" to "active",
                            "updatedAt" to FieldValue.serverTimestamp()
                        )

                    oldProfile.licenseNumber?.let {
                        newData["licenseNumber"] = it
                    }

                    oldProfile.plateNumber?.let {
                        newData["plateNumber"] = it
                    }

                    oldProfile.emergencyContact?.let {
                        newData["emergencyContact"] = it
                    }

                    val batch =
                        firestore.batch()

                    batch.set(
                        newRef,
                        newData
                    )

                    batch.delete(oldRef)

                    batch.commit().await()

                    listener?.remove()

                    listener =
                        newRef.addSnapshotListener {
                                document,
                                error ->

                            if (
                                error == null &&
                                document != null &&
                                document.exists()
                            ) {

                                pushFromDocument(
                                    document,
                                    null
                                )
                            }
                        }

                } else {

                    oldRef.update(
                        mapOf(
                            "firstName" to first,
                            "lastName" to last,
                            "phoneNumber" to newPhone,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()
                }

                userSession.setUser(
                    oldProfile.copy(
                        firstName = first,
                        lastName = last,
                        phoneNumber = newPhone
                    )
                )

                pushFromValues(
                    first,
                    last,
                    newPhone,
                    oldProfile.role,
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
        confirmPass: String
    ): Boolean {

        if (newPass != confirmPass) {

            _uiState.value =
                ProfileUiState.Error(
                    "Passwords do not match."
                )

            return false
        }

        if (newPass.isBlank()) {

            _uiState.value =
                ProfileUiState.Error(
                    "New password cannot be empty."
                )

            return false
        }

        val phone =
            userSession.userProfile
                ?.phoneNumber
                ?: return false

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
                    document.getString("password")

                if (savedPassword != oldPass) {

                    _uiState.value =
                        ProfileUiState.Error(
                            "Old password is incorrect."
                        )

                    return@launch
                }

                firestore
                    .collection(
                        FirestoreCollections.USERS
                    )
                    .document(phone)
                    .update(
                        "password",
                        newPass
                    )
                    .await()

                val old =
                    userSession.userProfile
                        ?: return@launch

                userSession.setUser(
                    old.copy(
                        password = newPass
                    )
                )

                pushFromValues(
                    old.firstName,
                    old.lastName,
                    old.phoneNumber,
                    old.role,
                    "Password updated successfully!"
                )

            } catch (e: Exception) {

                _uiState.value =
                    ProfileUiState.Error(
                        e.message
                            ?: "Hindi ma-update ang password"
                    )
            }
        }

        return true
    }

    fun clearMessage() {

        if (
            _uiState.value
                    is ProfileUiState.Success
        ) {

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
    }

    private fun pushFromDocument(
        document: com.google.firebase.firestore.DocumentSnapshot,
        message: String?
    ) {

        val roleString =
            document
                .getString("role")
                ?: UserRole.Admin.name

        val role =
            UserRole.entries.find {
                it.name.equals(
                    roleString,
                    ignoreCase = true
                )
            } ?: UserRole.Admin

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
                profile =
                    ProfileData(
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
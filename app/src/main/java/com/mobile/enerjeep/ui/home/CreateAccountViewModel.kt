package com.mobile.enerjeep.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.FirestoreIds
import com.mobile.enerjeep.data.UserRole
import com.mobile.enerjeep.navigation.AppNavigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CreateAccountViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val appNavigator: AppNavigator
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<CreateAccountUiState>(
            CreateAccountUiState.Idle
        )

    val uiState = _uiState.asStateFlow()

    companion object {
        private val PH_PHONE_REGEX =
            Regex("^\\+639\\d{9}$")
    }

    fun createAccount(
        firstName: String,
        lastName: String,
        phoneNumber: String,
        password: String,
        role: UserRole,
        licenseNumber: String = "",
        plateNumber: String = "",
        emergencyContact: String = ""
    ) {
        val cleanFirstName = firstName.trim()
        val cleanLastName = lastName.trim()
        val cleanPhoneNumber = phoneNumber.trim()
        val cleanPassword = password
        val cleanLicenseNumber = licenseNumber.trim()
        val cleanPlateNumber = plateNumber
            .trim()
            .uppercase()
        val cleanEmergencyContact = emergencyContact.trim()

        if (
            cleanFirstName.isBlank() ||
            cleanLastName.isBlank() ||
            cleanPhoneNumber.isBlank() ||
            cleanPassword.isBlank()
        ) {
            _uiState.value =
                CreateAccountUiState.Error(
                    "Pakilagay ang lahat ng kailangang impormasyon"
                )
            return
        }

        if (!PH_PHONE_REGEX.matches(cleanPhoneNumber)) {
            _uiState.value =
                CreateAccountUiState.Error(
                    "Ang numero ay dapat 10 digit at nasa format na 9XXXXXXXXX"
                )
            return
        }

        val generatedUserId = UUID.randomUUID().toString()

        val userMap =
            mutableMapOf<String, Any>(
                "userId" to generatedUserId,
                "firstName" to cleanFirstName,
                "lastName" to cleanLastName,
                "phoneNumber" to cleanPhoneNumber,
                "password" to cleanPassword,
                "role" to role.name.lowercase(),
                "createdAt" to System.currentTimeMillis()
            )

        if (role == UserRole.Driver) {
            userMap["licenseNumber"] = cleanLicenseNumber
            userMap["plateNumber"] = cleanPlateNumber
            userMap["emergencyContact"] = cleanEmergencyContact
        }

        viewModelScope.launch {
            _uiState.value =
                CreateAccountUiState.Loading

            try {
                val usersCollection =
                    firestore.collection(
                        FirestoreCollections.USERS
                    )

                val userReference =
                    usersCollection.document(
                        cleanPhoneNumber
                    )

                val batch =
                    firestore.batch()

                batch.set(
                    userReference,
                    userMap
                )

                /*
                 * Automatically place every newly-created Driver
                 * into the dispatch queue.
                 */
                if (role == UserRole.Driver) {

                    val queueSnapshot =
                        firestore
                            .collection(
                                FirestoreCollections.QUEUE
                            )
                            .get()
                            .await()

                    val activeQueue =
                        queueSnapshot.documents.filter {
                            isActiveQueueStatus(
                                it.getString("status")
                            )
                        }

                    val nextPosition =
                        activeQueue
                            .mapNotNull {
                                it.getLong(
                                    "position"
                                )?.toInt()
                            }
                            .maxOrNull()
                            ?.plus(1)
                            ?: (activeQueue.size + 1)

                    val queueId =
                        FirestoreIds.queue(
                            generatedUserId
                        )

                    val driverName =
                        listOf(
                            cleanFirstName,
                            cleanLastName
                        )
                            .filter {
                                it.isNotBlank()
                            }
                            .joinToString(" ")
                            .ifBlank {
                                "Driver"
                            }

                    val queueData =
                        mapOf<String, Any>(
                            "id" to queueId,
                            "driverId" to generatedUserId,
                            "plateNumber" to cleanPlateNumber,
                            "driverName" to driverName,
                            "route" to "",
                            "position" to nextPosition,
                            "status" to "Nakapila",
                            "time" to SimpleDateFormat(
                                "hh:mm a",
                                Locale.getDefault()
                            ).format(Date()),
                            "createdAt" to FieldValue.serverTimestamp()
                        )

                    val queueReference =
                        firestore
                            .collection(
                                FirestoreCollections.QUEUE
                            )
                            .document(queueId)

                    batch.set(
                        queueReference,
                        queueData
                    )
                }

                batch.commit().await()

                _uiState.value =
                    CreateAccountUiState.Success

            } catch (e: Exception) {

                _uiState.value =
                    CreateAccountUiState.Error(
                        e.message
                            ?: "Hindi nagawa ang account"
                    )
            }
        }
    }

    private fun isActiveQueueStatus(
        status: String?
    ): Boolean {
        return when (status?.lowercase()) {
            "dispatched",
            "cancelled",
            "completed",
            "resolved" -> false

            else -> true
        }
    }

    fun goBack() {
        appNavigator.navigateBack()
    }

    fun resetState() {
        _uiState.value =
            CreateAccountUiState.Idle
    }
}

sealed interface CreateAccountUiState {

    data object Idle :
        CreateAccountUiState

    data object Loading :
        CreateAccountUiState

    data object Success :
        CreateAccountUiState

    data class Error(
        val message: String
    ) : CreateAccountUiState
}
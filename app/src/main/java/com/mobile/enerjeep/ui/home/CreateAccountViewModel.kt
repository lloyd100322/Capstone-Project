package com.mobile.enerjeep.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.mobile.enerjeep.data.EmergencyContact
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.FirestoreIds
import com.mobile.enerjeep.data.PhilippineIdentifiers
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

        private const val DEFAULT_ROUTE =
            "Monumento – Marilao"
    }

    fun createAccount(
        firstName: String,
        lastName: String,
        phoneNumber: String,
        password: String,
        role: UserRole,
        licenseNumber: String = "",
        plateNumber: String = "",
        emergencyContactName: String = "",
        emergencyContactNumber: String = ""
    ) {
        val cleanFirstName = firstName.trim()
        val cleanLastName = lastName.trim()
        val cleanPhoneNumber = phoneNumber.trim()
        val cleanPassword = password
        val cleanLicenseNumber = licenseNumber.trim()

        val cleanPlateNumber = plateNumber
            .trim()
            .uppercase(Locale.ROOT)

        val cleanEmergencyName = emergencyContactName.trim()
        val cleanEmergencyNumber = emergencyContactNumber.trim()

        if (
            cleanFirstName.isBlank() ||
            cleanLastName.isBlank() ||
            cleanPhoneNumber.isBlank() ||
            cleanPassword.isBlank()
        ) {
            _uiState.value = CreateAccountUiState.Error(
                "Pakilagay ang lahat ng kailangang impormasyon"
            )
            return
        }

        if (!PH_PHONE_REGEX.matches(cleanPhoneNumber)) {
            _uiState.value = CreateAccountUiState.Error(
                "Ang numero ay dapat 10 digit at nasa format na 9XXXXXXXXX"
            )
            return
        }

        if (role == UserRole.Driver) {
            // The plate number and the license number are checked before
            // anything is saved, so a wrong value never reaches Firestore.
            PhilippineIdentifiers.plateNumberError(cleanPlateNumber)
                ?.let { error ->
                    _uiState.value = CreateAccountUiState.Error(error)
                    return
                }

            PhilippineIdentifiers.licenseNumberError(cleanLicenseNumber)
                ?.let { error ->
                    _uiState.value = CreateAccountUiState.Error(error)
                    return
                }
        }

        if (
            role == UserRole.Driver &&
            !EmergencyContact.isValidName(cleanEmergencyName)
        ) {
            _uiState.value = CreateAccountUiState.Error(
                "Ang pangalan ng emergency contact ay kailangan at " +
                        "dapat hanggang 50 karakter."
            )
            return
        }

        if (
            role == UserRole.Driver &&
            !EmergencyContact.isValidNumber(cleanEmergencyNumber)
        ) {
            _uiState.value = CreateAccountUiState.Error(
                "Ang numero ng emergency contact ay dapat valid na " +
                        "telepono (09XXXXXXXXX o +639XXXXXXXXX)."
            )
            return
        }

        viewModelScope.launch {
            _uiState.value = CreateAccountUiState.Loading

            try {
                val users = firestore.collection(
                    FirestoreCollections.USERS
                )

                // Current schema uses the phone number as the document ID.
                val userReference = users.document(
                    cleanPhoneNumber
                )

                if (userReference.get().await().exists()) {
                    _uiState.value = CreateAccountUiState.Error(
                        "May existing account na para sa numerong ito"
                    )
                    return@launch
                }

                if (role == UserRole.Driver) {
                    val duplicatePlate = users
                        .whereEqualTo("role", "driver")
                        .get()
                        .await()

                    // Compare the plates the same way everywhere in the
                    // app, so "abc 1234" and "ABC-1234" are understood as
                    // the same plate. Older records are not rewritten.
                    val alreadyUsed = duplicatePlate.documents.any {
                        PhilippineIdentifiers.platesMatch(
                            it.getString("plateNumber"),
                            cleanPlateNumber
                        )
                    }

                    if (alreadyUsed) {
                        _uiState.value = CreateAccountUiState.Error(
                            "Ginagamit na ng ibang driver ang plate number na ito"
                        )
                        return@launch
                    }
                }

                val generatedUserId =
                    UUID.randomUUID().toString()

                val userMap = mutableMapOf<String, Any>(
                    "userId" to generatedUserId,
                    "firstName" to cleanFirstName,
                    "lastName" to cleanLastName,
                    "phoneNumber" to cleanPhoneNumber,
                    "password" to cleanPassword,
                    "role" to role.name.lowercase(Locale.ROOT),
                    "status" to "active",
                    "createdAt" to FieldValue.serverTimestamp()
                )

                if (role == UserRole.Driver) {
                    userMap["licenseNumber"] =
                        cleanLicenseNumber

                    userMap["plateNumber"] =
                        cleanPlateNumber

                    // The two emergency contact values are saved in their
                    // own fields. The old combined field is kept in sync
                    // so older screens and exports still show something.
                    userMap["emergencyContactName"] =
                        cleanEmergencyName

                    userMap["emergencyContactNumber"] =
                        cleanEmergencyNumber

                    userMap["emergencyContact"] =
                        EmergencyContact.combine(
                            cleanEmergencyName,
                            cleanEmergencyNumber
                        )

                    // New drivers are active unless an admin deactivates them.
                    userMap["isVehicleActive"] = true
                }

                val batch = firestore.batch()

                batch.set(userReference, userMap)

                if (role == UserRole.Driver) {
                    val queueSnapshot = firestore
                        .collection(FirestoreCollections.QUEUE)
                        .get()
                        .await()

                    val activeQueue = queueSnapshot.documents.filter {
                        isActiveQueueStatus(
                            it.getString("status")
                        )
                    }

                    val nextPosition = activeQueue
                        .mapNotNull {
                            it.getLong("position")?.toInt()
                        }
                        .maxOrNull()
                        ?.plus(1)
                        ?: 1

                    val queueId = FirestoreIds.queue(
                        generatedUserId
                    )

                    val driverName = listOf(
                        cleanFirstName,
                        cleanLastName
                    )
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank { "Driver" }

                    val queueData = mapOf<String, Any>(
                        "id" to queueId,
                        "driverId" to generatedUserId,
                        "plateNumber" to cleanPlateNumber,
                        "driverName" to driverName,
                        "route" to DEFAULT_ROUTE,
                        "position" to nextPosition,
                        "status" to "Nakapila",
                        "time" to SimpleDateFormat(
                            "hh:mm a",
                            Locale.getDefault()
                        ).format(Date()),
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    val queueReference = firestore
                        .collection(FirestoreCollections.QUEUE)
                        .document(queueId)

                    batch.set(queueReference, queueData)
                }

                // Create the user and initial queue entry together.
                batch.commit().await()

                _uiState.value = CreateAccountUiState.Success

            } catch (e: Exception) {
                _uiState.value = CreateAccountUiState.Error(
                    e.message ?: "Hindi nagawa ang account"
                )
            }
        }
    }

    private fun isActiveQueueStatus(
        status: String?
    ): Boolean {
        return when (status?.lowercase(Locale.ROOT)) {
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
        _uiState.value = CreateAccountUiState.Idle
    }
}

sealed interface CreateAccountUiState {

    data object Idle : CreateAccountUiState

    data object Loading : CreateAccountUiState

    data object Success : CreateAccountUiState

    data class Error(
        val message: String
    ) : CreateAccountUiState
}

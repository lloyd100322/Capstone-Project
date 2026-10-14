package com.mobile.enerjeep.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.FirestoreIds
import com.mobile.enerjeep.data.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class AccountItem(
    val id: String,
    val userId: String,
    val name: String,
    val role: String,
    val phone: String,
    val dateCreated: String,
    val isActive: Boolean,
    val plateNumber: String = ""
)

data class AccountManagementUiState(
    val searchQuery: String = "",
    val allAccounts: List<AccountItem> = emptyList(),
    val filteredAccounts: List<AccountItem> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val busyAccountIds: Set<String> = emptySet()
)

@HiltViewModel
class AccountManagementViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(AccountManagementUiState())

    val uiState: StateFlow<AccountManagementUiState> =
        _uiState.asStateFlow()

    private var usersListener: ListenerRegistration? = null

    companion object {
        private const val DEFAULT_ROUTE = "Monumento – Marilao"
    }

    init {
        listenForAccounts()
    }

    private fun listenForAccounts() {
        usersListener = firestore
            .collection(FirestoreCollections.USERS)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message
                            ?: "Hindi mabasa ang mga account."
                    )
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                val accounts = snapshot.documents.mapNotNull { document ->

                    val roleLower = document
                        .getString("role")
                        .orEmpty()
                        .lowercase(Locale.ROOT)

                    if (
                        roleLower != "admin" &&
                        roleLower != "driver"
                    ) {
                        return@mapNotNull null
                    }

                    val firstName = document
                        .getString("firstName")
                        .orEmpty()
                        .trim()

                    val lastName = document
                        .getString("lastName")
                        .orEmpty()
                        .trim()

                    val name = listOf(firstName, lastName)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank {
                            document.getString("phoneNumber").orEmpty()
                        }

                    val isActive = if (roleLower == "driver") {
                        document.getBoolean("isVehicleActive") ?: true
                    } else {
                        !document.getString("status")
                            .orEmpty()
                            .equals("inactive", ignoreCase = true)
                    }

                    AccountItem(
                        id = document.id,

                        userId = document
                            .getString("userId")
                            .orEmpty()
                            .ifBlank { document.id },

                        name = name,

                        role = if (roleLower == "admin") {
                            "Admin"
                        } else {
                            "Driver"
                        },

                        phone = document
                            .getString("phoneNumber")
                            .orEmpty()
                            .ifBlank { document.id },

                        // Safely supports different Firestore date formats.
                        dateCreated = readDate(document, "createdAt")
                            ?.let {
                                SimpleDateFormat(
                                    "MMM dd, yyyy",
                                    Locale.getDefault()
                                ).format(it)
                            }
                            ?: "-",

                        isActive = isActive,

                        plateNumber = document
                            .getString("plateNumber")
                            .orEmpty()
                    )
                }.sortedBy {
                    it.name.lowercase(Locale.ROOT)
                }

                val current = _uiState.value

                _uiState.value = current.copy(
                    allAccounts = accounts,
                    filteredAccounts = filterAccounts(
                        accounts,
                        current.searchQuery
                    ),
                    isLoading = false,
                    errorMessage = null
                )
            }
    }

    /**
     * Safely reads Firestore date fields without assuming that they
     * are always stored as Firebase Timestamp values.
     */
    private fun readDate(
        document: DocumentSnapshot,
        fieldName: String
    ): Date? {
        val value = document.get(fieldName) ?: return null

        return when (value) {
            is Timestamp -> value.toDate()

            is Date -> value

            is String -> parseDateString(value)

            is Number -> {
                val number = value.toLong()

                // Accept epoch seconds or epoch milliseconds.
                val milliseconds = if (
                    number > -100_000_000_000L &&
                    number < 100_000_000_000L
                ) {
                    number * 1000L
                } else {
                    number
                }

                Date(milliseconds)
            }

            else -> null
        }
    }

    private fun parseDateString(value: String): Date? {
        val input = value.trim()

        if (input.isBlank()) {
            return null
        }

        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd",
            "MMM dd, yyyy",
            "MMMM dd, yyyy",
            "MM/dd/yyyy",
            "dd/MM/yyyy"
        )

        for (pattern in patterns) {
            try {
                val formatter = SimpleDateFormat(
                    pattern,
                    Locale.US
                )

                formatter.isLenient = false

                val position = ParsePosition(0)
                val parsedDate = formatter.parse(input, position)

                if (
                    parsedDate != null &&
                    position.index == input.length
                ) {
                    return parsedDate
                }
            } catch (_: IllegalArgumentException) {
                // Try the next supported date format.
            }
        }

        // Also support a numeric timestamp represented as a string.
        val numericValue = input.toLongOrNull()

        if (numericValue != null) {
            val milliseconds = if (
                numericValue > -100_000_000_000L &&
                numericValue < 100_000_000_000L
            ) {
                numericValue * 1000L
            } else {
                numericValue
            }

            return Date(milliseconds)
        }

        return null
    }

    private fun queueDateSeconds(
        document: DocumentSnapshot,
        fieldName: String
    ): Long? {
        return readDate(document, fieldName)
            ?.time
            ?.div(1000L)
    }

    fun updateSearchQuery(query: String) {
        val current = _uiState.value

        _uiState.value = current.copy(
            searchQuery = query,
            filteredAccounts = filterAccounts(
                current.allAccounts,
                query
            )
        )
    }

    private fun filterAccounts(
        accounts: List<AccountItem>,
        query: String
    ): List<AccountItem> {
        if (query.isBlank()) {
            return accounts
        }

        return accounts.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.phone.contains(query, ignoreCase = true) ||
                    it.plateNumber.contains(query, ignoreCase = true)
        }
    }

    fun toggleAccountStatus(accountId: String) {
        val account = _uiState.value.allAccounts
            .firstOrNull { it.id == accountId }
            ?: return

        if (
            account.role == "Admin" &&
            isCurrentAccount(account)
        ) {
            setError(
                "Hindi maaaring i-deactivate ang kasalukuyang admin account."
            )
            return
        }

        if (accountId in _uiState.value.busyAccountIds) {
            return
        }

        val newIsActive = !account.isActive

        viewModelScope.launch {
            setBusy(accountId, true)
            clearError()

            try {
                if (account.role == "Driver") {
                    setDriverVehicleActive(
                        account,
                        newIsActive
                    )
                } else {
                    firestore
                        .collection(FirestoreCollections.USERS)
                        .document(account.id)
                        .update(
                            mapOf(
                                "status" to if (newIsActive) {
                                    "active"
                                } else {
                                    "inactive"
                                },
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .await()
                }
            } catch (e: Exception) {
                setError(
                    e.message ?: "Hindi na-update ang status."
                )
            } finally {
                setBusy(accountId, false)
            }
        }
    }

    private suspend fun setDriverVehicleActive(
        account: AccountItem,
        active: Boolean
    ) {
        val userReference = firestore
            .collection(FirestoreCollections.USERS)
            .document(account.id)

        val userSnapshot = userReference.get().await()

        if (!userSnapshot.exists()) {
            throw IllegalStateException(
                "Hindi na makita ang driver account."
            )
        }

        val driverId = userSnapshot
            .getString("userId")
            .orEmpty()
            .ifBlank { account.userId }

        if (driverId.isBlank()) {
            throw IllegalStateException(
                "Walang User ID ang driver account."
            )
        }

        val queueCollection =
            firestore.collection(FirestoreCollections.QUEUE)

        val queueSnapshot = queueCollection.get().await()
        val queueDocuments = queueSnapshot.documents

        val thisDriversOpenEntries = queueDocuments.filter { document ->
            document.getString("driverId") == driverId &&
                    isOpenQueueStatus(document.getString("status"))
        }

        val otherActiveEntries = queueDocuments
            .filter { document ->
                document.getString("driverId") != driverId &&
                        isOpenQueueStatus(
                            document.getString("status")
                        ) &&
                        document.getBoolean("isActive") != false
            }
            .sortedWith(
                compareBy<DocumentSnapshot> {
                    it.getLong("position") ?: Long.MAX_VALUE
                }.thenBy {
                    queueDateSeconds(it, "createdAt") ?: Long.MAX_VALUE
                }
            )

        val batch = firestore.batch()

        // Vehicle availability is separate from login/account status.
        batch.update(
            userReference,
            mapOf(
                "isVehicleActive" to active,
                "updatedAt" to FieldValue.serverTimestamp()
            )
        )

        if (!active) {
            // Remove only open queue entries; keep dispatched history.
            thisDriversOpenEntries.forEach { document ->
                batch.delete(document.reference)
            }

            otherActiveEntries.forEachIndexed { index, document ->
                batch.update(
                    document.reference,
                    mapOf(
                        "position" to index + 1,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        } else {
            val queueEntryToKeep = thisDriversOpenEntries
                .maxWithOrNull(
                    compareBy<DocumentSnapshot> {
                        queueDateSeconds(it, "createdAt")
                            ?: Long.MIN_VALUE
                    }.thenBy {
                        it.getLong("position") ?: Long.MIN_VALUE
                    }
                )

            if (queueEntryToKeep != null) {
                // Clean up any duplicate open entries for this driver.
                thisDriversOpenEntries
                    .filter { it.id != queueEntryToKeep.id }
                    .forEach { document ->
                        batch.delete(document.reference)
                    }

                batch.update(
                    queueEntryToKeep.reference,
                    mapOf(
                        "position" to otherActiveEntries.size + 1,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            } else {
                val plateNumber = userSnapshot
                    .getString("plateNumber")
                    .orEmpty()
                    .trim()
                    .uppercase(Locale.ROOT)

                if (plateNumber.isBlank()) {
                    throw IllegalStateException(
                        "Walang plate number ang driver kaya hindi ito maibalik sa pila."
                    )
                }

                val route = queueDocuments
                    .filter {
                        it.getString("driverId") == driverId
                    }
                    .sortedByDescending {
                        queueDateSeconds(it, "createdAt") ?: 0L
                    }
                    .firstOrNull()
                    ?.getString("route")
                    .orEmpty()
                    .ifBlank { DEFAULT_ROUTE }

                val firstName = userSnapshot
                    .getString("firstName")
                    .orEmpty()

                val lastName = userSnapshot
                    .getString("lastName")
                    .orEmpty()

                val driverName = listOf(firstName, lastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { account.name }

                val queueId = FirestoreIds.queue(driverId)

                val queueReference = queueCollection
                    .document(queueId)

                val queueData = mapOf<String, Any>(
                    "id" to queueId,
                    "driverId" to driverId,
                    "plateNumber" to plateNumber,
                    "driverName" to driverName,
                    "route" to route,
                    "position" to otherActiveEntries.size + 1,
                    "status" to "Nakapila",
                    "time" to SimpleDateFormat(
                        "hh:mm a",
                        Locale.getDefault()
                    ).format(Date()),
                    "createdAt" to FieldValue.serverTimestamp()
                )

                batch.set(queueReference, queueData)
            }

            otherActiveEntries.forEachIndexed { index, document ->
                batch.update(
                    document.reference,
                    mapOf(
                        "position" to index + 1,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
        }

        batch.commit().await()
    }

    fun deleteAccount(accountId: String) {
        val account = _uiState.value.allAccounts
            .firstOrNull { it.id == accountId }
            ?: return

        if (isCurrentAccount(account)) {
            setError(
                "Hindi maaaring burahin ang account na kasalukuyang naka-login."
            )
            return
        }

        if (accountId in _uiState.value.busyAccountIds) {
            return
        }

        viewModelScope.launch {
            setBusy(accountId, true)
            clearError()

            try {
                val userReference = firestore
                    .collection(FirestoreCollections.USERS)
                    .document(account.id)

                val batch = firestore.batch()

                if (account.role == "Driver") {
                    val queueDocuments = firestore
                        .collection(FirestoreCollections.QUEUE)
                        .get()
                        .await()
                        .documents

                    val openEntriesToDelete = queueDocuments.filter {
                        it.getString("driverId") == account.userId &&
                                isOpenQueueStatus(
                                    it.getString("status")
                                )
                    }

                    openEntriesToDelete.forEach { document ->
                        batch.delete(document.reference)
                    }

                    val deletedIds = openEntriesToDelete
                        .map { it.id }
                        .toSet()

                    val remainingActive = queueDocuments
                        .filter {
                            it.id !in deletedIds &&
                                    isOpenQueueStatus(
                                        it.getString("status")
                                    ) &&
                                    it.getBoolean("isActive") != false
                        }
                        .sortedWith(
                            compareBy<DocumentSnapshot> {
                                it.getLong("position") ?: Long.MAX_VALUE
                            }.thenBy {
                                queueDateSeconds(
                                    it,
                                    "createdAt"
                                ) ?: Long.MAX_VALUE
                            }
                        )

                    remainingActive.forEachIndexed { index, document ->
                        batch.update(
                            document.reference,
                            mapOf(
                                "position" to index + 1,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }
                }

                // Preserve previous dispatch and ledger records.
                batch.delete(userReference)
                batch.commit().await()

            } catch (e: Exception) {
                setError(
                    e.message ?: "Hindi mabura ang account."
                )
            } finally {
                setBusy(accountId, false)
            }
        }
    }

    private fun isCurrentAccount(account: AccountItem): Boolean {
        val profile = userSession.userProfile ?: return false

        return (
                profile.userId.isNotBlank() &&
                        profile.userId == account.userId
                ) || (
                profile.phoneNumber.isNotBlank() &&
                        profile.phoneNumber == account.phone
                )
    }

    private fun isOpenQueueStatus(status: String?): Boolean {
        return when (status?.lowercase(Locale.ROOT)) {
            "dispatched",
            "cancelled",
            "completed",
            "resolved" -> false

            else -> true
        }
    }

    private fun setBusy(
        accountId: String,
        busy: Boolean
    ) {
        val current = _uiState.value
        val updated = current.busyAccountIds.toMutableSet()

        if (busy) {
            updated.add(accountId)
        } else {
            updated.remove(accountId)
        }

        _uiState.value = current.copy(
            busyAccountIds = updated
        )
    }

    private fun setError(message: String) {
        _uiState.value = _uiState.value.copy(
            errorMessage = message
        )
    }

    private fun clearError() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null
        )
    }

    override fun onCleared() {
        usersListener?.remove()
        super.onCleared()
    }
}
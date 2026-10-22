package com.mobile.enerjeep.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.PhilippineIdentifiers
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class LedgerItem(
    val id: String,
    val plateNumber: String,
    val driverName: String,
    val date: String,
    val amount: Double,
    val feeType: String,
    val status: String,
    val createdAtMillis: Long = 0L
)

data class LedgerDriverOption(
    val userId: String,
    val plateNumber: String,
    val driverName: String
)

sealed interface LedgerUiState {
    data object Loading : LedgerUiState
    data class Success(
        val todayCollections: List<LedgerItem>,
        val historyCollections: List<LedgerItem>,
        // Every collection of the ledger. The "Kasaysayan" tab shows this
        // list, so a collection that was made today is written in the
        // history as well.
        val allCollections: List<LedgerItem>,
        val totalToday: Double
    ) : LedgerUiState
    data class Error(val message: String) : LedgerUiState
}

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow<LedgerUiState>(LedgerUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private val _driverOptions = MutableStateFlow<List<LedgerDriverOption>>(emptyList())
    val driverOptions = _driverOptions.asStateFlow()

    private var ledgerListener: ListenerRegistration? = null
    private var driverListener: ListenerRegistration? = null

    init {
        loadLedger()
        loadDrivers()
    }

    private fun loadLedger() {
        ledgerListener = firestore.collection(FirestoreCollections.LEDGER)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = LedgerUiState.Error(error.message ?: "Hindi mabasa ang ledger")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val items = snapshot.documents.map { document ->
                    val timestampMillis = getTimestampMillis(document)
                    val textDate = document.getString("date")
                        ?: timestampMillis.takeIf { it > 0L }?.let { formatDate(Date(it)) }
                        ?: ""
                    LedgerItem(
                        id = document.id,
                        plateNumber = document.getString("plateNumber").orEmpty(),
                        driverName = document.getString("driverName").orEmpty(),
                        date = textDate,
                        amount = document.getDouble("amount") ?: document.getDouble("netAmount") ?: 0.0,
                        feeType = document.getString("feeType") ?: "Collection",
                        status = document.getString("status") ?: "Bayad",
                        createdAtMillis = timestampMillis
                    )
                }.sortedByDescending { it.createdAtMillis.takeIf { time -> time > 0L } ?: parseDateMillis(it.date) }

                val today = LocalDate.now()
                val todaysItems = items.filter { item ->
                    val millis = item.createdAtMillis.takeIf { it > 0L } ?: parseDateMillis(item.date)
                    millis > 0L && Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() == today
                }
                val todayIds = todaysItems.map { it.id }.toSet()
                val history = items.filter { it.id !in todayIds }
                _uiState.value = LedgerUiState.Success(
                    todayCollections = todaysItems,
                    historyCollections = history,
                    allCollections = items,
                    totalToday = todaysItems.sumOf { it.amount }
                )
            }
    }

    /** A legacy field may be a Firestore Timestamp, epoch milliseconds, epoch seconds, or a date string. */
    private fun getTimestampMillis(document: DocumentSnapshot): Long {
        val raw = document.get("createdAt")
        return when (raw) {
            is Timestamp -> raw.toDate().time
            is Date -> raw.time
            is Number -> {
                val value = raw.toLong()
                if (value in 1 until 100_000_000_000L) value * 1000L else value
            }
            is String -> parseDateMillis(raw)
            else -> 0L
        }.takeIf { it > 0L } ?: parseDateMillis(document.getString("date"))
    }

    private fun parseDateMillis(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        val patterns = listOf("MMM dd, yyyy", "MMM d, yyyy", "yyyy-MM-dd", "MM/dd/yyyy", "M/d/yyyy")
        for (locale in listOf(Locale.getDefault(), Locale.ENGLISH).distinct()) {
            for (pattern in patterns) {
                try {
                    val formatter = SimpleDateFormat(pattern, locale).apply { isLenient = false }
                    return formatter.parse(value.trim())?.time ?: continue
                } catch (_: Exception) { }
            }
        }
        return 0L
    }

    private fun formatDate(date: Date): String = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(date)

    private fun loadDrivers() {
        driverListener = firestore.collection(FirestoreCollections.USERS)
            .whereEqualTo("role", "driver")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    _driverOptions.value = emptyList()
                    return@addSnapshotListener
                }
                _driverOptions.value = snapshot.documents.mapNotNull(::mapDriverOption)
                    .sortedBy { it.plateNumber.lowercase(Locale.ROOT) }
            }
    }

    private fun mapDriverOption(document: DocumentSnapshot): LedgerDriverOption? {
        val userId = document.getString("userId").orEmpty().trim()
        val plateNumber = document.getString("plateNumber").orEmpty().trim()
        if (userId.isBlank() || plateNumber.isBlank()) return null
        val firstName = document.getString("firstName").orEmpty().trim()
        val lastName = document.getString("lastName").orEmpty().trim()
        val name = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Driver" }
        return LedgerDriverOption(userId, plateNumber, name)
    }

    /** Adds one real ledger record. The driverId is users.userId, not the Firestore document ID. */
    fun addCollection(plateNumber: String, driverName: String, amount: Double, feeType: String) {
        viewModelScope.launch {
            try {
                val cleanPlateNumber = plateNumber.trim()
                if (cleanPlateNumber.isBlank()) {
                    _uiState.value = LedgerUiState.Error("Pumili muna ng plaka")
                    return@launch
                }
                if (amount <= 0.0) {
                    _uiState.value = LedgerUiState.Error("Dapat mas mataas sa ₱0 ang halaga")
                    return@launch
                }

                /*
                 * The plate is compared the same way everywhere in the app,
                 * so "abc 1234" and "ABC-1234" still find the same driver.
                 * The stored value is never rewritten.
                 */
                val driverSnapshot = firestore.collection(FirestoreCollections.USERS)
                    .whereEqualTo("role", "driver")
                    .get().await()
                val driverDocument = driverSnapshot.documents.firstOrNull {
                    PhilippineIdentifiers.platesMatch(
                        it.getString("plateNumber"),
                        cleanPlateNumber
                    )
                }
                if (driverDocument == null) {
                    _uiState.value = LedgerUiState.Error("Hindi makita ang driver para sa plaka $cleanPlateNumber")
                    return@launch
                }
                val driverId = driverDocument.getString("userId").orEmpty().trim()
                if (driverId.isBlank()) {
                    _uiState.value = LedgerUiState.Error("Walang User ID ang driver na may plaka $cleanPlateNumber")
                    return@launch
                }

                val firstName = driverDocument.getString("firstName").orEmpty().trim()
                val lastName = driverDocument.getString("lastName").orEmpty().trim()
                val resolvedName = listOf(firstName, lastName).filter { it.isNotBlank() }
                    .joinToString(" ").ifBlank { driverName.trim() }.ifBlank { "Driver" }
                val documentId = UUID.randomUUID().toString()
                val nowText = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
                val data = hashMapOf<String, Any>(
                    "id" to documentId,
                    "driverId" to driverId,
                    "plateNumber" to cleanPlateNumber,
                    "driverName" to resolvedName,
                    "date" to nowText,
                    "amount" to amount,
                    "grossAmount" to amount,
                    "otherFees" to 0.0,
                    "netAmount" to amount,
                    "feeType" to feeType.trim(),
                    "status" to "Bayad",
                    "source" to "ledger",
                    "createdAt" to FieldValue.serverTimestamp()
                )
                firestore.collection(FirestoreCollections.LEDGER).document(documentId).set(data).await()
            } catch (e: Exception) {
                _uiState.value = LedgerUiState.Error(e.message ?: "Hindi na-save ang collection")
            }
        }
    }

    override fun onCleared() {
        ledgerListener?.remove()
        driverListener?.remove()
        super.onCleared()
    }
}

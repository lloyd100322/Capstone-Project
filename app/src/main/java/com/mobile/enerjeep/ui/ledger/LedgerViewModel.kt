package com.mobile.enerjeep.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
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

data class LedgerItem(
    val id: String,
    val plateNumber: String,
    val driverName: String,
    val date: String,
    val amount: Double,
    val feeType: String,
    val status: String
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
        val totalToday: Double
    ) : LedgerUiState

    data class Error(
        val message: String
    ) : LedgerUiState
}

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<LedgerUiState>(LedgerUiState.Loading)

    val uiState = _uiState.asStateFlow()

    private val _driverOptions =
        MutableStateFlow<List<LedgerDriverOption>>(emptyList())

    val driverOptions = _driverOptions.asStateFlow()

    private var ledgerListener: ListenerRegistration? = null
    private var driverListener: ListenerRegistration? = null

    init {
        loadLedger()
        loadDrivers()
    }

    /**
     * Loads all ledger records in real time.
     *
     * The records are sorted using Firestore's createdAt timestamp.
     * Document IDs must NOT be used for chronological sorting because
     * UUID/random IDs do not represent creation order.
     */
    private fun loadLedger() {

        ledgerListener =
            firestore
                .collection(FirestoreCollections.LEDGER)
                .addSnapshotListener { snapshot, error ->

                    if (error != null) {
                        _uiState.value =
                            LedgerUiState.Error(
                                error.message
                                    ?: "Hindi mabasa ang ledger"
                            )
                        return@addSnapshotListener
                    }

                    if (snapshot == null) {
                        return@addSnapshotListener
                    }

                    val todayKey =
                        SimpleDateFormat(
                            "yyyyMMdd",
                            Locale.getDefault()
                        ).format(Date())

                    val documents =
                        snapshot.documents.sortedByDescending { document ->
                            document
                                .getTimestamp("createdAt")
                                ?.seconds
                                ?: 0L
                        }

                    val items =
                        documents.map { document ->

                            LedgerItem(
                                id = document.id,

                                plateNumber =
                                    document
                                        .getString("plateNumber")
                                        .orEmpty(),

                                driverName =
                                    document
                                        .getString("driverName")
                                        .orEmpty(),

                                date =
                                    document
                                        .getString("date")
                                        ?: formatTimestamp(
                                            document.getTimestamp("createdAt")
                                        ),

                                amount =
                                    document.getDouble("amount")
                                        ?: document.getDouble("netAmount")
                                        ?: 0.0,

                                feeType =
                                    document
                                        .getString("feeType")
                                        ?: "Collection",

                                status =
                                    document
                                        .getString("status")
                                        ?: "Bayad"
                            )
                        }

                    val today =
                        documents
                            .filter { document ->
                                val timestamp =
                                    document.getTimestamp("createdAt")

                                timestamp != null &&
                                        SimpleDateFormat(
                                            "yyyyMMdd",
                                            Locale.getDefault()
                                        ).format(
                                            timestamp.toDate()
                                        ) == todayKey
                            }
                            .map { document ->
                                items.first { it.id == document.id }
                            }

                    val todayIds =
                        today
                            .map { it.id }
                            .toSet()

                    val history =
                        items.filter { item ->
                            item.id !in todayIds
                        }

                    _uiState.value =
                        LedgerUiState.Success(
                            todayCollections = today,
                            historyCollections = history,
                            totalToday = today.sumOf { it.amount }
                        )
                }
    }

    /**
     * Loads only real driver accounts from Firestore.
     *
     * A driver must have:
     * - role = driver
     * - a non-empty plateNumber
     * - a valid users.userId
     *
     * These options are used by the Ledger form instead of hard-coded
     * mock jeep/driver records.
     */
    private fun loadDrivers() {

        driverListener =
            firestore
                .collection(FirestoreCollections.USERS)
                .whereEqualTo("role", "driver")
                .addSnapshotListener { snapshot, error ->

                    if (error != null || snapshot == null) {
                        _driverOptions.value = emptyList()
                        return@addSnapshotListener
                    }

                    val drivers =
                        snapshot.documents
                            .mapNotNull { document ->
                                mapDriverOption(document)
                            }
                            .sortedBy {
                                it.plateNumber.lowercase()
                            }

                    _driverOptions.value = drivers
                }
    }

    /**
     * Converts a Firestore users document into a driver option.
     */
    private fun mapDriverOption(
        document: DocumentSnapshot
    ): LedgerDriverOption? {

        val userId =
            document
                .getString("userId")
                .orEmpty()
                .trim()

        val plateNumber =
            document
                .getString("plateNumber")
                .orEmpty()
                .trim()

        if (userId.isBlank() || plateNumber.isBlank()) {
            return null
        }

        val firstName =
            document
                .getString("firstName")
                .orEmpty()
                .trim()

        val lastName =
            document
                .getString("lastName")
                .orEmpty()
                .trim()

        val driverName =
            listOf(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank {
                    "Driver"
                }

        return LedgerDriverOption(
            userId = userId,
            plateNumber = plateNumber,
            driverName = driverName
        )
    }

    /**
     * Adds a collection to the ledger.
     *
     * The plate number is used to locate the real driver account.
     * The value stored as ledger.driverId is ALWAYS users.userId.
     */
    fun addCollection(
        plateNumber: String,
        driverName: String,
        amount: Double,
        feeType: String
    ) {

        viewModelScope.launch {

            try {

                val cleanPlateNumber =
                    plateNumber
                        .trim()

                if (cleanPlateNumber.isBlank()) {
                    _uiState.value =
                        LedgerUiState.Error(
                            "Pumili muna ng plaka"
                        )
                    return@launch
                }

                if (amount <= 0.0) {
                    _uiState.value =
                        LedgerUiState.Error(
                            "Dapat mas mataas sa ₱0 ang halaga"
                        )
                    return@launch
                }

                /*
                 * Find the real driver using the plate number.
                 */
                val driverSnapshot =
                    firestore
                        .collection(
                            FirestoreCollections.USERS
                        )
                        .whereEqualTo(
                            "role",
                            "driver"
                        )
                        .whereEqualTo(
                            "plateNumber",
                            cleanPlateNumber
                        )
                        .limit(1)
                        .get()
                        .await()

                val driverDocument =
                    driverSnapshot
                        .documents
                        .firstOrNull()

                if (driverDocument == null) {
                    _uiState.value =
                        LedgerUiState.Error(
                            "Hindi makita ang driver para sa plaka $cleanPlateNumber"
                        )
                    return@launch
                }

                val driverId =
                    driverDocument
                        .getString("userId")
                        .orEmpty()
                        .trim()

                if (driverId.isBlank()) {
                    _uiState.value =
                        LedgerUiState.Error(
                            "Walang User ID ang driver na may plaka $cleanPlateNumber"
                        )
                    return@launch
                }

                val firstName =
                    driverDocument
                        .getString("firstName")
                        .orEmpty()
                        .trim()

                val lastName =
                    driverDocument
                        .getString("lastName")
                        .orEmpty()
                        .trim()

                val resolvedDriverName =
                    listOf(firstName, lastName)
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank {
                            driverName.trim()
                        }
                        .ifBlank {
                            "Driver"
                        }

                /*
                 * Document ID can be generated independently.
                 * driverId remains users.userId.
                 */
                val documentId =
                    UUID.randomUUID()
                        .toString()

                val nowText =
                    SimpleDateFormat(
                        "MMM dd, yyyy",
                        Locale.getDefault()
                    ).format(Date())

                val data =
                    hashMapOf<String, Any>(

                        "id" to documentId,

                        "driverId" to driverId,

                        "plateNumber" to
                                cleanPlateNumber,

                        "driverName" to
                                resolvedDriverName,

                        "date" to
                                nowText,

                        "amount" to
                                amount,

                        "grossAmount" to
                                amount,

                        "otherFees" to
                                0.0,

                        "netAmount" to
                                amount,

                        "feeType" to
                                feeType.trim(),

                        "status" to
                                "Bayad",

                        "source" to
                                "ledger",

                        "createdAt" to
                                FieldValue.serverTimestamp()
                    )

                firestore
                    .collection(
                        FirestoreCollections.LEDGER
                    )
                    .document(documentId)
                    .set(data)
                    .await()

            } catch (e: Exception) {

                _uiState.value =
                    LedgerUiState.Error(
                        e.message
                            ?: "Hindi na-save ang collection"
                    )
            }
        }
    }

    private fun formatTimestamp(
        timestamp: com.google.firebase.Timestamp?
    ): String {

        return timestamp
            ?.toDate()
            ?.let {
                SimpleDateFormat(
                    "MMM dd, yyyy",
                    Locale.getDefault()
                ).format(it)
            }
            .orEmpty()
    }

    override fun onCleared() {

        ledgerListener?.remove()
        driverListener?.remove()

        super.onCleared()
    }
}
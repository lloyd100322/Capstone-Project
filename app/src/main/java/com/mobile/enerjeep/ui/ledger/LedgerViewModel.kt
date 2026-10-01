package com.mobile.enerjeep.ui.ledger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.UserSession
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

sealed interface LedgerUiState {
    data object Loading : LedgerUiState

    data class Success(
        val todayCollections: List<LedgerItem>,
        val historyCollections: List<LedgerItem>,
        val totalToday: Double
    ) : LedgerUiState

    data class Error(val message: String) : LedgerUiState
}

@HiltViewModel
class LedgerViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<LedgerUiState>(LedgerUiState.Loading)

    val uiState = _uiState.asStateFlow()

    private var listener: ListenerRegistration? = null

    init {
        loadLedger()
    }

    private fun loadLedger() {
        listener = firestore
            .collection(FirestoreCollections.LEDGER)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    _uiState.value =
                        LedgerUiState.Error(
                            error.message ?: "Hindi mabasa ang ledger"
                        )
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                val todayKey =
                    SimpleDateFormat(
                        "yyyyMMdd",
                        Locale.getDefault()
                    ).format(Date())

                val items = snapshot.documents.map { document ->

                    LedgerItem(
                        id = document.id,

                        plateNumber =
                            document.getString("plateNumber")
                                .orEmpty(),

                        driverName =
                            document.getString("driverName")
                                .orEmpty(),

                        date =
                            document.getString("date")
                                ?: formatTimestamp(
                                    document.getTimestamp("createdAt")
                                ),

                        amount =
                            document.getDouble("amount")
                                ?: document.getDouble("netAmount")
                                ?: 0.0,

                        feeType =
                            document.getString("feeType")
                                ?: "Collection",

                        status =
                            document.getString("status")
                                ?: "Bayad"
                    )
                }.sortedByDescending { it.id }

                val today = items.filter { item ->

                    val document =
                        snapshot.documents.firstOrNull {
                            it.id == item.id
                        }

                    val timestamp =
                        document?.getTimestamp("createdAt")

                    timestamp != null &&
                            SimpleDateFormat(
                                "yyyyMMdd",
                                Locale.getDefault()
                            ).format(timestamp.toDate()) == todayKey
                }

                val history =
                    items.filterNot { item ->
                        today.any { it.id == item.id }
                    }

                _uiState.value =
                    LedgerUiState.Success(
                        todayCollections = today,
                        historyCollections = history,
                        totalToday = today.sumOf { it.amount }
                    )
            }
    }

    fun addCollection(
        plateNumber: String,
        driverName: String,
        amount: Double,
        feeType: String
    ) {

        viewModelScope.launch {

            try {

                val cleanPlateNumber =
                    plateNumber.trim()

                /*
                 * Find the driver using plateNumber.
                 * The value saved into ledger.driverId
                 * MUST be users.userId.
                 */
                val driverSnapshot =
                    firestore
                        .collection(FirestoreCollections.USERS)
                        .whereEqualTo(
                            "plateNumber",
                            cleanPlateNumber
                        )
                        .limit(1)
                        .get()
                        .await()

                val driverDocument =
                    driverSnapshot.documents.firstOrNull()

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

                if (driverId.isBlank()) {
                    _uiState.value =
                        LedgerUiState.Error(
                            "Walang User ID ang driver na may plaka $cleanPlateNumber"
                        )
                    return@launch
                }

                val resolvedDriverName =
                    listOf(
                        driverDocument
                            .getString("firstName")
                            .orEmpty(),

                        driverDocument
                            .getString("lastName")
                            .orEmpty()
                    )
                        .filter { it.isNotBlank() }
                        .joinToString(" ")
                        .ifBlank {
                            driverName.trim()
                        }

                /*
                 * The document ID can be generated freely.
                 * This is DIFFERENT from driverId.
                 */
                val documentId =
                    UUID.randomUUID().toString()

                val nowText =
                    SimpleDateFormat(
                        "MMM dd, yyyy",
                        Locale.getDefault()
                    ).format(Date())

                val data = hashMapOf<String, Any>(
                    "id" to documentId,
                    "driverId" to driverId,
                    "plateNumber" to cleanPlateNumber,
                    "driverName" to resolvedDriverName,
                    "date" to nowText,
                    "amount" to amount,
                    "grossAmount" to amount,
                    "otherFees" to 0.0,
                    "netAmount" to amount,
                    "feeType" to feeType,
                    "status" to "Bayad",
                    "source" to "ledger",
                    "createdAt" to FieldValue.serverTimestamp()
                )

                firestore
                    .collection(FirestoreCollections.LEDGER)
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
        listener?.remove()
        super.onCleared()
    }
}
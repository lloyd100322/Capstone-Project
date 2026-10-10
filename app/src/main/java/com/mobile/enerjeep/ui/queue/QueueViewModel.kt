package com.mobile.enerjeep.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.FirestoreIds
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject


data class QueueVehicle(
    val id: String,
    val driverId: String = "",
    val position: Int,
    val plateNumber: String,
    val driverName: String,
    val route: String,
    val time: String,
    val status: String,
    val showPositionEdit: Boolean = false
)

data class HistoryVehicle(
    val id: String,
    val plateNumber: String,
    val driverName: String,
    val time: String,
    val status: String
)

sealed interface QueueUiState {
    data object Loading : QueueUiState

    data class Success(
        val activeQueue: List<QueueVehicle>,
        val historyQueue: List<HistoryVehicle>,
        val totalInQueue: Int,
        val totalDispatchedToday: Int
    ) : QueueUiState

    data class Error(
        val message: String
    ) : QueueUiState
}

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<QueueUiState>(QueueUiState.Loading)

    val uiState = _uiState.asStateFlow()

    private var queueListener: ListenerRegistration? = null

    companion object {
        private const val DEFAULT_ROUTE = "Monumento – Marilao"
    }

    init {
        startQueueListener()
    }

    private fun startQueueListener() {
        queueListener =
            firestore
                .collection(FirestoreCollections.QUEUE)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        _uiState.value = QueueUiState.Error(
                            error.message ?: "Hindi mabasa ang pila"
                        )
                        return@addSnapshotListener
                    }

                    if (snapshot == null) return@addSnapshotListener

                    val active = snapshot.documents
                        .filter { isActiveStatus(it.getString("status")) }
                        .sortedWith(
                            compareBy<DocumentSnapshot> {
                                it.getLong("position") ?: Long.MAX_VALUE
                            }.thenBy {
                                it.getTimestamp("createdAt")?.seconds ?: Long.MAX_VALUE
                            }
                        )
                        .mapIndexed { index, document ->
                            QueueVehicle(
                                id = document.id,
                                driverId = document.getString("driverId").orEmpty(),
                                position = document.getLong("position")
                                    ?.toInt()
                                    ?.takeIf { it > 0 }
                                    ?: index + 1,
                                plateNumber = document.getString("plateNumber").orEmpty(),
                                driverName = document.getString("driverName").orEmpty(),
                                route = document.getString("route").orEmpty(),
                                time = formatTimestamp(document.getTimestamp("createdAt"))
                                    .ifBlank { document.getString("time").orEmpty() },
                                status = document.getString("status") ?: "Nakapila"
                            )
                        }

                    val history = snapshot.documents
                        .filter { !isActiveStatus(it.getString("status")) }
                        .sortedByDescending {
                            it.getTimestamp("dispatchedAt")?.seconds
                                ?: it.getTimestamp("createdAt")?.seconds
                                ?: 0L
                        }
                        .map { document ->
                            val timestamp = document.getTimestamp("dispatchedAt")
                                ?: document.getTimestamp("updatedAt")
                                ?: document.getTimestamp("createdAt")

                            HistoryVehicle(
                                id = document.id,
                                plateNumber = document.getString("plateNumber").orEmpty(),
                                driverName = document.getString("driverName").orEmpty(),
                                time = formatTimestamp(timestamp)
                                    .ifBlank { document.getString("time").orEmpty() },
                                status = when (document.getString("status")?.lowercase()) {
                                    "cancelled" -> "Nakansela"
                                    else -> "Na-Dispatch"
                                }
                            )
                        }

                    val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                    val dispatchedToday = snapshot.documents.count { document ->
                        if (document.getString("status")?.lowercase() != "dispatched") {
                            return@count false
                        }

                        val timestamp = document.getTimestamp("dispatchedAt")
                        timestamp != null && SimpleDateFormat(
                            "yyyyMMdd",
                            Locale.getDefault()
                        ).format(timestamp.toDate()) == today
                    }

                    _uiState.value = QueueUiState.Success(
                        activeQueue = active,
                        historyQueue = history,
                        totalInQueue = active.size,
                        totalDispatchedToday = dispatchedToday
                    )
                }
    }

    /** Adds a real driver to the queue if they do not already have an active entry. */
    fun addVehicleToQueue(
        driverId: String,
        route: String
    ) {
        viewModelScope.launch {
            try {
                val driverSnapshot = firestore
                    .collection(FirestoreCollections.USERS)
                    .whereEqualTo("userId", driverId)
                    .whereEqualTo("role", "driver")
                    .limit(1)
                    .get()
                    .await()

                val driver = driverSnapshot.documents.firstOrNull()
                    ?: throw IllegalStateException("Hindi makita ang driver account.")

                val existingQueue = firestore
                    .collection(FirestoreCollections.QUEUE)
                    .whereEqualTo("driverId", driverId)
                    .get()
                    .await()

                if (existingQueue.documents.any { isActiveStatus(it.getString("status")) }) {
                    throw IllegalStateException("Nasa pila na ang driver na ito.")
                }

                val plate = driver.getString("plateNumber").orEmpty().trim().uppercase()
                if (plate.isBlank()) {
                    throw IllegalStateException("Walang plaka ang driver.")
                }

                val driverName = listOf(
                    driver.getString("firstName").orEmpty(),
                    driver.getString("lastName").orEmpty()
                ).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Driver" }

                val queueSnapshot = firestore
                    .collection(FirestoreCollections.QUEUE)
                    .get()
                    .await()

                val activeQueue = queueSnapshot.documents
                    .filter { isActiveStatus(it.getString("status")) }

                val nextPosition = activeQueue
                    .mapNotNull { it.getLong("position")?.toInt() }
                    .maxOrNull()
                    ?.plus(1)
                    ?: 1

                val id = FirestoreIds.queue(driverId)
                val queueData = mapOf<String, Any>(
                    "id" to id,
                    "driverId" to driverId,
                    "plateNumber" to plate,
                    "driverName" to driverName,
                    "route" to route.trim().ifBlank { DEFAULT_ROUTE },
                    "position" to nextPosition,
                    "status" to "Nakapila",
                    "time" to SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                    "createdAt" to FieldValue.serverTimestamp()
                )

                firestore
                    .collection(FirestoreCollections.QUEUE)
                    .document(id)
                    .set(queueData)
                    .await()
            } catch (e: Exception) {
                _uiState.value = QueueUiState.Error(
                    e.message ?: "Hindi maidagdag sa pila"
                )
            }
        }
    }

    /**
     * Dispatches the selected queue turn, records the transaction, and places
     * the same driver at the end of the active queue for their next turn.
     * The previous queue document remains in history as "dispatched".
     */
    fun dispatchVehicle(
        vehicleId: String,
        income: Double,
        otherFees: Double
    ) {
        if (income < 0.0) {
            _uiState.value = QueueUiState.Error("Hindi maaaring negatibo ang kita")
            return
        }

        if (otherFees < 0.0) {
            _uiState.value = QueueUiState.Error("Hindi maaaring negatibo ang ibang bayad")
            return
        }

        if (otherFees > income) {
            _uiState.value = QueueUiState.Error(
                "Hindi maaaring mas mataas ang ibang bayad kaysa sa kita"
            )
            return
        }

        viewModelScope.launch {
            try {
                val queueSnapshot = firestore
                    .collection(FirestoreCollections.QUEUE)
                    .get()
                    .await()

                val currentDocument = queueSnapshot.documents
                    .firstOrNull { it.id == vehicleId }
                    ?: throw IllegalStateException("Hindi makita ang sasakyan sa pila.")

                if (!isActiveStatus(currentDocument.getString("status"))) {
                    throw IllegalStateException("Ang sasakyang ito ay wala na sa aktibong pila.")
                }

                val driverId = currentDocument.getString("driverId").orEmpty().trim()
                val driverName = currentDocument.getString("driverName").orEmpty()
                val plateNumber = currentDocument.getString("plateNumber").orEmpty()
                    .trim()
                    .uppercase()
                val route = currentDocument.getString("route")
                    ?.trim()
                    .orEmpty()
                    .ifBlank { DEFAULT_ROUTE }

                if (driverId.isBlank()) {
                    throw IllegalStateException("Walang User ID ang driver na ito.")
                }

                // Confirm the queue entry references a driver in users.
                val driverSnapshot = firestore
                    .collection(FirestoreCollections.USERS)
                    .whereEqualTo("userId", driverId)
                    .whereEqualTo("role", "driver")
                    .limit(1)
                    .get()
                    .await()

                if (driverSnapshot.documents.isEmpty()) {
                    throw IllegalStateException(
                        "Hindi makita ang driver account sa users collection."
                    )
                }

                val netAmount = income - otherFees
                val batch = firestore.batch()
                val queueReference = currentDocument.reference

                // Keep this turn as dispatched so it remains available in history.
                batch.update(
                    queueReference,
                    mapOf(
                        "status" to "dispatched",
                        "income" to income,
                        "otherFees" to otherFees,
                        "netAmount" to netAmount,
                        "dispatchedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "position" to 0
                    )
                )

                // Keep the currently waiting drivers in their existing order.
                val remainingActive = queueSnapshot.documents
                    .filter {
                        it.id != vehicleId && isActiveStatus(it.getString("status"))
                    }
                    .sortedWith(
                        compareBy<DocumentSnapshot> {
                            it.getLong("position") ?: Long.MAX_VALUE
                        }.thenBy {
                            it.getTimestamp("createdAt")?.seconds ?: Long.MAX_VALUE
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

                // Add the just-dispatched driver behind everyone already waiting.
                // This is a new queue turn; the dispatched turn above is kept as history.
                val nextQueueId = FirestoreIds.queue(driverId)
                val nextQueuePosition = remainingActive.size + 1
                val newQueueData = mapOf<String, Any>(
                    "id" to nextQueueId,
                    "driverId" to driverId,
                    "plateNumber" to plateNumber,
                    "driverName" to driverName,
                    "route" to route,
                    "position" to nextQueuePosition,
                    "status" to "Nakapila",
                    "time" to SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date()),
                    "previousQueueId" to vehicleId,
                    "createdAt" to FieldValue.serverTimestamp()
                )

                batch.set(
                    firestore
                        .collection(FirestoreCollections.QUEUE)
                        .document(nextQueueId),
                    newQueueData
                )

                // Keep one dispatch document linked to the completed queue turn.
                val dispatchReference = firestore
                    .collection(FirestoreCollections.DISPATCHES)
                    .document(vehicleId)

                batch.set(
                    dispatchReference,
                    mapOf<String, Any>(
                        "id" to vehicleId,
                        "queueId" to vehicleId,
                        "driverId" to driverId,
                        "driverName" to driverName,
                        "plateNumber" to plateNumber,
                        "route" to route,
                        "status" to "dispatched",
                        "income" to income,
                        "otherFees" to otherFees,
                        "netAmount" to netAmount,
                        "dispatchedAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                // Save the financial transaction for the driver's Kita screen.
                val ledgerId = "${vehicleId}_dispatch_${System.currentTimeMillis()}"
                val ledgerReference = firestore
                    .collection(FirestoreCollections.LEDGER)
                    .document(ledgerId)

                batch.set(
                    ledgerReference,
                    mapOf<String, Any>(
                        "id" to ledgerId,
                        "driverId" to driverId,
                        "driverName" to driverName,
                        "plateNumber" to plateNumber,
                        "queueId" to vehicleId,
                        "date" to SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date()),
                        "amount" to netAmount,
                        "grossAmount" to income,
                        "otherFees" to otherFees,
                        "netAmount" to netAmount,
                        "feeType" to "Dispatch",
                        "status" to "Bayad",
                        "source" to "queue_dispatch",
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                batch.commit().await()
            } catch (e: Exception) {
                _uiState.value = QueueUiState.Error(
                    e.message ?: "Hindi ma-dispatch ang sasakyan"
                )
            }
        }
    }

    fun updateVehiclePosition(
        vehicleId: String,
        newPosition: Int
    ) {
        val currentState = _uiState.value as? QueueUiState.Success ?: return
        val active = currentState.activeQueue.toMutableList()
        val currentIndex = active.indexOfFirst { it.id == vehicleId }

        if (currentIndex == -1 || active.isEmpty()) return

        val safePosition = newPosition.coerceIn(1, active.size)
        val vehicle = active.removeAt(currentIndex)
        active.add(safePosition - 1, vehicle)

        viewModelScope.launch {
            try {
                val batch = firestore.batch()
                active.forEachIndexed { index, item ->
                    batch.update(
                        firestore
                            .collection(FirestoreCollections.QUEUE)
                            .document(item.id),
                        mapOf(
                            "position" to index + 1,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            } catch (e: Exception) {
                _uiState.value = QueueUiState.Error(
                    e.message ?: "Hindi ma-update ang posisyon"
                )
            }
        }
    }

    private fun isActiveStatus(status: String?): Boolean {
        return when (status?.lowercase()) {
            "dispatched", "cancelled", "completed", "resolved" -> false
            else -> true
        }
    }

    private fun formatTimestamp(
        timestamp: com.google.firebase.Timestamp?
    ): String {
        return timestamp
            ?.toDate()
            ?.let {
                SimpleDateFormat(
                    "MMM dd, yyyy • hh:mm a",
                    Locale.getDefault()
                ).format(it)
            }
            .orEmpty()
    }

    override fun onCleared() {
        queueListener?.remove()
        super.onCleared()
    }
}

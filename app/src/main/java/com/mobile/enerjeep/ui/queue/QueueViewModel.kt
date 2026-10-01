package com.mobile.enerjeep.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    data class Error(val message: String) : QueueUiState
}

@HiltViewModel
class QueueViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _uiState = MutableStateFlow<QueueUiState>(QueueUiState.Loading)
    val uiState = _uiState.asStateFlow()

    private var queueListener: ListenerRegistration? = null

    init {
        startQueueListener()
    }

    private fun startQueueListener() {
        queueListener = firestore.collection(FirestoreCollections.QUEUE)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = QueueUiState.Error(error.message ?: "Hindi mabasa ang pila")
                    return@addSnapshotListener
                }
                if (snapshot == null) return@addSnapshotListener

                val active = snapshot.documents
                    .filter { isActiveStatus(it.getString("status")) }
                    .sortedBy { it.getLong("position") ?: Long.MAX_VALUE }
                    .mapIndexed { index, document ->
                        QueueVehicle(
                            id = document.id,
                            driverId = document.getString("driverId").orEmpty(),
                            position = (document.getLong("position")?.toInt() ?: (index + 1)),
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
                    .sortedByDescending { it.getTimestamp("dispatchedAt")?.seconds ?: it.getTimestamp("createdAt")?.seconds ?: 0L }
                    .map { document ->
                        val timestamp = document.getTimestamp("dispatchedAt")
                            ?: document.getTimestamp("updatedAt")
                            ?: document.getTimestamp("createdAt")
                        HistoryVehicle(
                            id = document.id,
                            plateNumber = document.getString("plateNumber").orEmpty(),
                            driverName = document.getString("driverName").orEmpty(),
                            time = formatTimestamp(timestamp).ifBlank { document.getString("time").orEmpty() },
                            status = when (document.getString("status")?.lowercase()) {
                                "cancelled" -> "Nakansela"
                                else -> "Na-Dispatch"
                            }
                        )
                    }

                val today = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
                val dispatchedToday = snapshot.documents.count { document ->
                    if (document.getString("status")?.lowercase() != "dispatched") return@count false
                    val timestamp = document.getTimestamp("dispatchedAt") ?: return@count false
                    SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(timestamp.toDate()) == today
                }

                _uiState.value = QueueUiState.Success(
                    activeQueue = active,
                    historyQueue = history,
                    totalInQueue = active.size,
                    totalDispatchedToday = dispatchedToday
                )
            }
    }

    fun dispatchVehicle(vehicleId: String, income: Double, otherFees: Double) {
        val currentState = _uiState.value as? QueueUiState.Success ?: return
        val vehicle = currentState.activeQueue.firstOrNull { it.id == vehicleId } ?: return

        viewModelScope.launch {
            try {
                val batch = firestore.batch()
                val queueRef = firestore.collection(FirestoreCollections.QUEUE).document(vehicleId)

                batch.update(
                    queueRef,
                    mapOf(
                        "status" to "dispatched",
                        "income" to income,
                        "otherFees" to otherFees,
                        "netAmount" to (income - otherFees),
                        "dispatchedAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "position" to 0
                    )
                )

                val ledgerId = "${vehicleId}_dispatch_${System.currentTimeMillis()}"
                val ledgerRef = firestore.collection(FirestoreCollections.LEDGER).document(ledgerId)
                batch.set(ledgerRef, mapOf(
                    "id" to ledgerId,
                    "driverId" to vehicle.driverId,
                    "driverName" to vehicle.driverName,
                    "plateNumber" to vehicle.plateNumber,
                    "date" to SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date()),
                    "amount" to (income - otherFees),
                    "grossAmount" to income,
                    "otherFees" to otherFees,
                    "feeType" to "Dispatch",
                    "status" to "Bayad",
                    "source" to "queue_dispatch",
                    "createdAt" to FieldValue.serverTimestamp()
                ))

                batch.commit().await()
            } catch (e: Exception) {
                _uiState.value = QueueUiState.Error(e.message ?: "Hindi ma-dispatch ang sasakyan")
            }
        }
    }

    fun updateVehiclePosition(vehicleId: String, newPosition: Int) {
        val currentState = _uiState.value as? QueueUiState.Success ?: return
        val active = currentState.activeQueue.toMutableList()
        val currentIndex = active.indexOfFirst { it.id == vehicleId }
        if (currentIndex == -1) return

        val vehicle = active.removeAt(currentIndex)
        active.add((newPosition - 1).coerceIn(0, active.size), vehicle)

        viewModelScope.launch {
            try {
                val batch = firestore.batch()
                active.forEachIndexed { index, item ->
                    batch.update(
                        firestore.collection(FirestoreCollections.QUEUE).document(item.id),
                        mapOf(
                            "position" to index + 1,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }
                batch.commit().await()
            } catch (e: Exception) {
                _uiState.value = QueueUiState.Error(e.message ?: "Hindi ma-update ang posisyon")
            }
        }
    }

    private fun isActiveStatus(status: String?): Boolean {
        return when (status?.lowercase()) {
            "dispatched", "cancelled", "completed", "resolved" -> false
            else -> true
        }
    }

    private fun formatTimestamp(timestamp: com.google.firebase.Timestamp?): String {
        return timestamp?.toDate()?.let {
            SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(it)
        }.orEmpty()
    }

    override fun onCleared() {
        queueListener?.remove()
        super.onCleared()
    }
}

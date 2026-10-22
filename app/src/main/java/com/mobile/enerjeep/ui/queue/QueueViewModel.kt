package com.mobile.enerjeep.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.FirestoreIds
import com.mobile.enerjeep.data.safeTimestamp
import com.mobile.enerjeep.ui.components.ToastFeedback
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
        MutableStateFlow<QueueUiState>(
            QueueUiState.Loading
        )

    val uiState = _uiState.asStateFlow()

    private var queueListener: ListenerRegistration? = null

    companion object {
        private const val DEFAULT_ROUTE = "Monumento – Marilao"
    }

    init {
        startQueueListener()
    }

    private fun startQueueListener() {
        queueListener = firestore
            .collection(FirestoreCollections.QUEUE)
            .addSnapshotListener { snapshot, error ->

                if (error != null) {
                    _uiState.value = QueueUiState.Error(
                        error.message ?: "Hindi mabasa ang pila"
                    )
                    return@addSnapshotListener
                }

                if (snapshot == null) {
                    return@addSnapshotListener
                }

                val active = snapshot.documents
                    .filter {
                        isActiveStatus(it.getString("status"))
                    }
                    .sortedWith(
                        compareBy<DocumentSnapshot> {
                            it.getLong("position") ?: Long.MAX_VALUE
                        }.thenBy {
                            it.safeTimestamp("createdAt")?.seconds
                                ?: Long.MAX_VALUE
                        }
                    )
                    .mapIndexed { index, document ->
                        QueueVehicle(
                            id = document.id,

                            driverId = document
                                .getString("driverId")
                                .orEmpty(),

                            position = document
                                .getLong("position")
                                ?.toInt()
                                ?.takeIf { it > 0 }
                                ?: index + 1,

                            plateNumber = document
                                .getString("plateNumber")
                                .orEmpty(),

                            driverName = document
                                .getString("driverName")
                                .orEmpty(),

                            route = document
                                .getString("route")
                                .orEmpty(),

                            time = formatTimestamp(
                                document.safeTimestamp("createdAt")
                            ).ifBlank {
                                document.getString("time").orEmpty()
                            },

                            status = document
                                .getString("status")
                                ?: "Nakapila"
                        )
                    }

                val history = snapshot.documents
                    .filter {
                        !isActiveStatus(it.getString("status"))
                    }
                    .sortedByDescending {
                        it.safeTimestamp("dispatchedAt")?.seconds
                            ?: it.safeTimestamp("updatedAt")?.seconds
                            ?: it.safeTimestamp("createdAt")?.seconds
                            ?: 0L
                    }
                    .map { document ->
                        val timestamp =
                            document.safeTimestamp("dispatchedAt")
                                ?: document.safeTimestamp("updatedAt")
                                ?: document.safeTimestamp("createdAt")

                        HistoryVehicle(
                            id = document.id,

                            plateNumber = document
                                .getString("plateNumber")
                                .orEmpty(),

                            driverName = document
                                .getString("driverName")
                                .orEmpty(),

                            time = formatTimestamp(timestamp).ifBlank {
                                document.getString("time").orEmpty()
                            },

                            status = when (
                                document.getString("status")
                                    ?.lowercase(Locale.ROOT)
                            ) {
                                "cancelled" -> "Nakansela"
                                else -> "Na-Dispatch"
                            }
                        )
                    }

                val today = SimpleDateFormat(
                    "yyyyMMdd",
                    Locale.getDefault()
                ).format(Date())

                val dispatchedToday = snapshot.documents.count { document ->
                    if (
                        document.getString("status")
                            ?.lowercase(Locale.ROOT) != "dispatched"
                    ) {
                        return@count false
                    }

                    val timestamp =
                        document.safeTimestamp("dispatchedAt")

                    timestamp != null &&
                            SimpleDateFormat(
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

    /**
     * Adds an existing driver to the queue.
     * Drivers marked as inactive cannot be added.
     */
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
                    ?: throw IllegalStateException(
                        "Hindi makita ang driver account."
                    )

                if (driver.getBoolean("isVehicleActive") == false) {
                    throw IllegalStateException(
                        "Hindi aktibo ang jeepney na ito."
                    )
                }

                if (
                    driver.getString("status")
                        .orEmpty()
                        .equals("inactive", ignoreCase = true)
                ) {
                    throw IllegalStateException(
                        "Hindi aktibo ang account na ito."
                    )
                }

                val existingQueue = firestore
                    .collection(FirestoreCollections.QUEUE)
                    .whereEqualTo("driverId", driverId)
                    .get()
                    .await()

                val alreadyQueued = existingQueue.documents.any {
                    isActiveStatus(it.getString("status"))
                }

                if (alreadyQueued) {
                    throw IllegalStateException(
                        "Nasa pila na ang driver na ito."
                    )
                }

                val plate = driver
                    .getString("plateNumber")
                    .orEmpty()
                    .trim()
                    .uppercase(Locale.ROOT)

                if (plate.isBlank()) {
                    throw IllegalStateException(
                        "Walang plaka ang driver."
                    )
                }

                val firstName = driver
                    .getString("firstName")
                    .orEmpty()

                val lastName = driver
                    .getString("lastName")
                    .orEmpty()

                val driverName = listOf(firstName, lastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { "Driver" }

                val queueSnapshot = firestore
                    .collection(FirestoreCollections.QUEUE)
                    .get()
                    .await()

                val activeQueue = queueSnapshot.documents.filter {
                    isActiveStatus(it.getString("status"))
                }

                val nextPosition = activeQueue
                    .mapNotNull {
                        it.getLong("position")?.toInt()
                    }
                    .maxOrNull()
                    ?.plus(1)
                    ?: 1

                val id = FirestoreIds.queue(driverId)

                val queueData = mapOf<String, Any>(
                    "id" to id,
                    "driverId" to driverId,
                    "plateNumber" to plate,
                    "driverName" to driverName,
                    "route" to route.trim().ifBlank {
                        DEFAULT_ROUTE
                    },
                    "position" to nextPosition,
                    "status" to "Nakapila",
                    "time" to SimpleDateFormat(
                        "hh:mm a",
                        Locale.getDefault()
                    ).format(Date()),
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
     * Dispatches the selected driver and creates exactly one ledger
     * transaction. Active drivers return to the end of the queue.
     */
    // Queue entries whose dispatch is currently being written.
    private val dispatchingIds = mutableSetOf<String>()

    fun dispatchVehicle(
        vehicleId: String,
        income: Double,
        otherFees: Double
    ) {
        if (income < 0.0) {
            ToastFeedback.show(
                "Hindi maaaring negatibo ang kita"
            )
            return
        }

        if (otherFees < 0.0) {
            ToastFeedback.show(
                "Hindi maaaring negatibo ang ibang bayad"
            )
            return
        }

        if (otherFees > income) {
            ToastFeedback.show(
                "Hindi maaaring mas mataas ang ibang bayad kaysa sa kita"
            )
            return
        }

        viewModelScope.launch {
            // Ignore a second tap/retry while this dispatch is still running.
            if (!dispatchingIds.add(vehicleId)) return@launch

            try {
                val queueSnapshot = firestore
                    .collection(FirestoreCollections.QUEUE)
                    .get()
                    .await()

                val currentDocument = queueSnapshot.documents
                    .firstOrNull { it.id == vehicleId }
                    ?: throw IllegalStateException(
                        "Hindi makita ang sasakyan sa pila."
                    )

                if (
                    !isActiveStatus(
                        currentDocument.getString("status")
                    )
                ) {
                    throw IllegalStateException(
                        "Ang sasakyang ito ay wala na sa aktibong pila."
                    )
                }

                val driverId = currentDocument
                    .getString("driverId")
                    .orEmpty()
                    .trim()

                val driverName = currentDocument
                    .getString("driverName")
                    .orEmpty()

                val plateNumber = currentDocument
                    .getString("plateNumber")
                    .orEmpty()
                    .trim()
                    .uppercase(Locale.ROOT)

                if (driverId.isBlank()) {
                    throw IllegalStateException(
                        "Walang User ID ang driver na ito."
                    )
                }

                /*
                 * Check the latest driver settings before deciding whether
                 * this vehicle should be requeued after dispatch.
                 */
                val driverSnapshot = firestore
                    .collection(FirestoreCollections.USERS)
                    .whereEqualTo("userId", driverId)
                    .whereEqualTo("role", "driver")
                    .limit(1)
                    .get()
                    .await()

                val driverAccount =
                    driverSnapshot.documents.firstOrNull()

                if (driverAccount == null) {
                    throw IllegalStateException(
                        "Hindi makita ang driver account."
                    )
                }

                if (driverAccount.getBoolean("isVehicleActive") == false) {
                    throw IllegalStateException(
                        "Hindi aktibo ang jeepney na ito."
                    )
                }

                if (
                    driverAccount.getString("status")
                        .orEmpty()
                        .equals("inactive", ignoreCase = true)
                ) {
                    throw IllegalStateException(
                        "Hindi aktibo ang account na ito."
                    )
                }

                val netAmount = income - otherFees

                val remainingActive = queueSnapshot.documents
                    .filter {
                        it.id != vehicleId &&
                                isActiveStatus(it.getString("status"))
                    }
                    .sortedWith(
                        compareBy<DocumentSnapshot> {
                            it.getLong("position") ?: Long.MAX_VALUE
                        }.thenBy {
                            it.safeTimestamp("createdAt")?.seconds
                                ?: Long.MAX_VALUE
                        }
                    )

                // Avoid creating another active entry if one already exists.
                val alreadyQueuedAfterDispatch =
                    remainingActive.any {
                        it.getString("driverId") == driverId
                    }

                val shouldRequeue =
                    driverAccount.getBoolean("isVehicleActive") != false &&
                            !alreadyQueuedAfterDispatch

                val queueReference = currentDocument.reference

                /*
                 * A Firestore transaction is used so the dispatch is only
                 * written when this queue entry was not changed by another
                 * admin in the meantime. If it changed, the transaction
                 * fails instead of corrupting the queue.
                 */
                firestore.runTransaction { transaction ->
                    val freshEntry = transaction.get(queueReference)

                    if (!freshEntry.exists() ||
                        !isActiveStatus(freshEntry.getString("status"))
                    ) {
                        throw IllegalStateException(
                            "Ang sasakyang ito ay wala na sa aktibong pila."
                        )
                    }

                    // Mark the dispatched queue entry as historical.
                    transaction.update(
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

                // Renumber the existing active queue after dispatch.
                remainingActive.forEachIndexed { index, document ->
                    transaction.update(
                        document.reference,
                        mapOf(
                            "position" to index + 1,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                /*
                 * Return the driver to the bottom of the queue only if the
                 * jeepney remains active. The dispatched entry above remains
                 * in history and is not reused.
                 */
                if (shouldRequeue) {
                    // Deterministic id: a retried dispatch rewrites the same
                    // re-queue entry instead of creating another one.
                    val requeueId = "queue_${driverId}_after_${vehicleId}"

                    val requeueReference = firestore
                        .collection(FirestoreCollections.QUEUE)
                        .document(requeueId)

                    val route = currentDocument
                        .getString("route")
                        .orEmpty()
                        .ifBlank { DEFAULT_ROUTE }

                    val requeueData = mapOf<String, Any>(
                        "id" to requeueId,
                        "driverId" to driverId,
                        "driverName" to driverName,
                        "plateNumber" to plateNumber,
                        "route" to route,
                        "position" to remainingActive.size + 1,
                        "status" to "Nakapila",
                        "time" to SimpleDateFormat(
                            "hh:mm a",
                            Locale.getDefault()
                        ).format(Date()),
                        "createdAt" to FieldValue.serverTimestamp()
                    )

                    transaction.set(requeueReference, requeueData)
                }

                /*
                 * Create exactly one ledger record for this dispatch.
                 * Driver earnings continue to use the net amount.
                 */
                // One ledger (earning) document per dispatched queue entry.
                // The id is derived from the queue entry, not the clock, so
                // a retry or double tap rewrites the same document.
                val ledgerId = "${vehicleId}_dispatch"

                val ledgerReference = firestore
                    .collection(FirestoreCollections.LEDGER)
                    .document(ledgerId)

                transaction.set(
                    ledgerReference,
                    mapOf<String, Any>(
                        "id" to ledgerId,
                        "driverId" to driverId,
                        "driverName" to driverName,
                        "plateNumber" to plateNumber,
                        "queueId" to vehicleId,
                        "date" to SimpleDateFormat(
                            "MMM dd, yyyy",
                            Locale.getDefault()
                        ).format(Date()),
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

                    true
                }.await()

                val dispatchedLabel = listOf(plateNumber, driverName)
                    .filter { it.isNotBlank() }
                    .joinToString(" — ")

                ToastFeedback.show(
                    "Na-dispatch na ang driver: $dispatchedLabel"
                )

            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message ?: "Hindi ma-dispatch ang sasakyan"
                )
            } finally {
                dispatchingIds.remove(vehicleId)
            }
        }
    }

    /**
     * Cancels an active queue entry. The entry moves to the Kasaysayan tab
     * as "Nakansela" and the remaining active entries are renumbered.
     */
    fun cancelQueueEntry(vehicleId: String) {
        viewModelScope.launch {
            try {
                val queueSnapshot = firestore
                    .collection(FirestoreCollections.QUEUE)
                    .get()
                    .await()

                val target = queueSnapshot.documents
                    .firstOrNull { it.id == vehicleId }
                    ?: throw IllegalStateException(
                        "Hindi makita ang sasakyan sa pila."
                    )

                if (!isActiveStatus(target.getString("status"))) {
                    throw IllegalStateException(
                        "Ang sasakyang ito ay wala na sa aktibong pila."
                    )
                }

                val remaining = queueSnapshot.documents
                    .filter {
                        it.id != vehicleId &&
                                isActiveStatus(it.getString("status"))
                    }
                    .sortedWith(
                        compareBy<DocumentSnapshot> {
                            it.getLong("position") ?: Long.MAX_VALUE
                        }.thenBy {
                            it.safeTimestamp("createdAt")?.seconds
                                ?: Long.MAX_VALUE
                        }
                    )

                /*
                 * Transaction: only cancel if the entry is still active
                 * and was not changed by someone else in the meantime.
                 */
                firestore.runTransaction { transaction ->
                    val freshEntry = transaction.get(target.reference)

                    if (!freshEntry.exists() ||
                        !isActiveStatus(freshEntry.getString("status"))
                    ) {
                        throw IllegalStateException(
                            "Ang sasakyang ito ay wala na sa aktibong pila."
                        )
                    }

                    transaction.update(
                        target.reference,
                        mapOf(
                            "status" to "cancelled",
                            "position" to 0,
                            "cancelledAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )

                    remaining.forEachIndexed { index, document ->
                        transaction.update(
                            document.reference,
                            mapOf(
                                "position" to index + 1,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )
                        )
                    }

                    true
                }.await()

                ToastFeedback.show(
                    "Nakansela ang " +
                            target.getString("plateNumber").orEmpty() +
                            " sa pila."
                )
            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message ?: "Hindi makansela ang sasakyan."
                )
            }
        }
    }

    fun updateVehiclePosition(
        vehicleId: String,
        newPosition: Int
    ) {
        val currentState =
            _uiState.value as? QueueUiState.Success
                ?: return

        val active = currentState.activeQueue.toMutableList()

        val currentIndex = active.indexOfFirst {
            it.id == vehicleId
        }

        if (currentIndex == -1 || active.isEmpty()) {
            return
        }

        val safePosition = newPosition.coerceIn(
            1,
            active.size
        )

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
                ToastFeedback.show(
                    e.message ?: "Hindi ma-update ang posisyon"
                )
            }
        }
    }

    private fun isActiveStatus(
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
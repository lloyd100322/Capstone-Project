package com.mobile.enerjeep.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.ActivityLogger
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.FirestoreIds
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.data.safeTimestamp
import com.mobile.enerjeep.ui.components.ToastFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Calendar
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
    val isCancelled: Boolean = false
)

data class HistoryVehicle(
    val id: String,
    val plateNumber: String,
    val driverName: String,
    val route: String = "",
    val time: String,
    val status: String,
    val createdAtMillis: Long = 0L
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
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<QueueUiState>(
            QueueUiState.Loading
        )

    val uiState = _uiState.asStateFlow()

    private var queueListener: ListenerRegistration? = null

    /*
     * -----------------------------
     * AWTOMATIKONG RESET (cut-off)
     * -----------------------------
     * Kahit nakabukas ang app, nagre-reset ang pila pagdating ng
     * cut-off hour (default 9:00 PM, settable ng superadmin). Ang unang
     * tseke ay agad (catch-up kapag lumampas na ang cut-off habang
     * sarado ang app), tapos ang susunod ay eksaktong sa susunod na
     * cut-off.
     */
    private var rotationWatch: Job? = null

    companion object {
        /*
         * Ang bagong sasakyan sa pila ay pumapasok sa MARILAO –
         * MONUMENTO. Ang Monumento – Marilao ay simula lang na WALA
         * PANG LAMAN - lalamanin lang nito ang mga driver pagkatapos
         * silang i-dispatch mula sa Marilao – Monumento (kabila).
         */
        private const val DEFAULT_ROUTE = "Marilao – Monumento"

        const val ROUTE_MA = "Monumento – Marilao"
        const val ROUTE_AM = "Marilao – Monumento"

        /*
         * Ang pila ay awtomatikong nagre-reset tuwing cut-off hour
         * (default 9:00 PM / 21:00, settable ng superadmin). Hindi na ito
         * manual - awtomatiko pa rin.
         */
        const val CUTOFF_HOUR = 21

        fun formatCutoffHour(hour: Int): String {
            val h12 = ((hour + 11) % 12) + 1
            val amPm = if (hour < 12) "AM" else "PM"
            return "$h12:00 $amPm"
        }

        /** 0 = Monumento -> Marilao, 1 = Marilao -> Monumento. */
        fun routeKey(route: String?): Int =
            if (route.orEmpty().trim()
                    .startsWith("Marilao", ignoreCase = true)
            ) 1 else 0

        fun sameRoute(a: String?, b: String?): Boolean =
            routeKey(a) == routeKey(b)

        /**
         * The route going BACK. A dispatched driver enters the pila of
         * the opposite route at position #1.
         */
        fun oppositeRoute(route: String?): String =
            if (routeKey(route) == 1) ROUTE_MA else ROUTE_AM

        /**
         * The dispatch deduction for a jeep comes from its number of
         * seats (seater). Fixed tiers are stored in
         * settings/deductionTiers ("16" -> 32, "22" -> 44). A jeep
         * without a stored tier falls back to 2 pesos per seat.
         */
        fun tierFor(seater: Int, tiers: Map<String, Any?>?): Double {
            if (seater <= 0) return 0.0
            val stored = tiers
                ?.get(seater.toString())
                ?.let { it as? Number }
                ?.toDouble()
            return stored ?: (seater * 2.0)
        }
    }

    /*
     * Ang oras ng awtomatikong reset: binabasa mula sa settings
     * ("cutoffHour", default 21 = 9 PM). Naka-cache dito para sa timer
     * at ipinapakita sa UI.
     */
    @Volatile
    private var cutoffHour: Int = CUTOFF_HOUR

    private val _cutoffLabel =
        MutableStateFlow(formatCutoffHour(CUTOFF_HOUR))

    val cutoffLabel: StateFlow<String> = _cutoffLabel.asStateFlow()

    init {
        startQueueListener()
        healActiveRoster()
        normalizeQueueRoutes()
        maybeRotateQueue()
        startRotationWatch()
    }

    /**
     * ONE-TIME normalization ng mga ruta: ang buong pila ay inilalagay
     * sa Marilao – Monumento (ang panimulang ruta). Ang Monumento –
     * Marilao ay mananatiling WALA SA LAMAN hanggang sa may unang
     * i-dispatch mula sa Marilao – Monumento. Isang beses lang ito
     * (may flag sa settings) para hindi na galawin ang mga legit na
     * entry na gawa ng dispatch.
     */
    private fun normalizeQueueRoutes() {
        if (!userSession.isAdmin()) return

        viewModelScope.launch {
            try {
                val metaReference = rotationMetaReference()
                val meta = metaReference.get().await()
                if (meta.getBoolean("routesNormalized") == true) {
                    return@launch
                }

                val queueSnapshot = firestore
                    .collection(FirestoreCollections.QUEUE)
                    .get()
                    .await()

                val open = queueSnapshot.documents.filter {
                    isActiveStatus(it.getString("status"))
                }

                val batch = firestore.batch()

                val allSorted = open.sortedWith(
                    compareBy<DocumentSnapshot> {
                        if (it.getBoolean("isCancelled") == true) 1 else 0
                    }.thenBy {
                        it.getLong("position") ?: Long.MAX_VALUE
                    }
                )

                allSorted.forEachIndexed { index, document ->
                    batch.update(
                        document.reference,
                        mapOf(
                            "route" to ROUTE_AM,
                            "position" to index + 1,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                batch.update(
                    metaReference,
                    mapOf(
                        "routesNormalized" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
                batch.commit().await()
            } catch (_: Exception) {
                // Best effort - hindi nasisira ang pila kung hindi ito tumakbo.
            }
        }
    }

    private fun startRotationWatch() {
        if (!userSession.isAdmin()) return
        rotationWatch?.cancel()
        rotationWatch = viewModelScope.launch {
            while (isActive) {
                try {
                    val rotated = rotateQueueIfNeeded(force = false)
                    if (rotated) {
                        onQueueRotated()
                    }
                } catch (_: Exception) {
                    // Best effort - susubukan ulit sa susunod na tikso.
                }
                delay(millisUntilNextCutoff())
            }
        }
    }

    /** Millis hanggang sa susunod na cut-off hour (hindi bababa sa 1 minuto). */
    private fun millisUntilNextCutoff(): Long {
        val now = Calendar.getInstance()
        val next = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, cutoffHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return (next.timeInMillis - now.timeInMillis)
            .coerceAtLeast(60_000L)
    }

    private fun onQueueRotated() {
        ActivityLogger.log(
            firestore,
            userSession,
            "Reset ng Pila",
            "Automatic (${_cutoffLabel.value})"
        )
        ToastFeedback.show(
            "Na-reset na ang pila: ang dating #1 ay nasa huli na."
        )
    }

    /**
     * Every ACTIVE driver belongs in the queue. Drivers left out by older
     * app versions (dispatched before the return-to-the-end rule existed)
     * or by account repairs are added back at the END of the pila — the
     * same rule used after a dispatch. Runs once when the queue opens and
     * only for admins (drivers may not write to the queue).
     */
    private fun healActiveRoster() {
        if (!userSession.isAdmin()) return

        viewModelScope.launch {
            try {
                val usersSnapshot = firestore
                    .collection(FirestoreCollections.USERS)
                    .whereEqualTo("role", "driver")
                    .get()
                    .await()

                val queueSnapshot = firestore
                    .collection(FirestoreCollections.QUEUE)
                    .get()
                    .await()

                val openEntries = queueSnapshot.documents.filter {
                    isActiveStatus(it.getString("status"))
                }

                val openDriverIds = openEntries
                    .mapNotNull { it.getString("driverId") }
                    .toSet()

                val stranded = usersSnapshot.documents.filter { user ->
                    val userId = user
                        .getString("userId")
                        .orEmpty()
                        .ifBlank { user.id }

                    user.getBoolean("isVehicleActive") != false &&
                            !user.getString("status").orEmpty()
                                .equals("inactive", ignoreCase = true) &&
                            userId !in openDriverIds
                }

                if (stranded.isEmpty()) {
                    return@launch
                }

                var nextPosition = openEntries
                    .filter {
                        sameRoute(it.getString("route"), DEFAULT_ROUTE)
                    }
                    .mapNotNull { it.getLong("position")?.toInt() }
                    .maxOrNull()
                    ?: openEntries
                        .mapNotNull { it.getLong("position")?.toInt() }
                        .maxOrNull()
                    ?: 0

                stranded.forEach { user ->
                    val userId = user
                        .getString("userId")
                        .orEmpty()
                        .ifBlank { user.id }

                    val driverName = listOf(
                        user.getString("firstName"),
                        user.getString("lastName")
                    ).filter { !it.isNullOrBlank() }
                        .joinToString(" ")
                        .ifBlank { "Driver" }

                    val queueId = FirestoreIds.queue(userId)

                    firestore
                        .collection(FirestoreCollections.QUEUE)
                        .document(queueId)
                        .set(
                            mapOf<String, Any>(
                                "id" to queueId,
                                "driverId" to userId,
                                "plateNumber" to user
                                    .getString("plateNumber")
                                    .orEmpty(),
                                "driverName" to driverName,
                                "route" to DEFAULT_ROUTE,
                                "position" to ++nextPosition,
                                "status" to "Nakapila",
                                "time" to SimpleDateFormat(
                                    "hh:mm a",
                                    Locale.getDefault()
                                ).format(Date()),
                                "createdAt" to FieldValue.serverTimestamp()
                            )
                        )
                        .await()
                }

                ToastFeedback.show(
                    "Bumalik sa dulo ng pila ang " +
                            stranded.size +
                            " aktibong driver na wala sa pila."
                )
            } catch (_: Exception) {
                // Best effort: the queue keeps working without the repair.
            }
        }
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
                            if (it.getBoolean("isCancelled") == true) 1 else 0
                        }.thenBy {
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
                                ?: "Nakapila",

                            isCancelled = document
                                .getBoolean("isCancelled") == true
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

                            route = document
                                .getString("route")
                                .orEmpty()
                                .ifBlank { DEFAULT_ROUTE },

                            time = formatTimestamp(timestamp).ifBlank {
                                document.getString("time").orEmpty()
                            },

                            createdAtMillis = timestamp
                                ?.toDate()
                                ?.time
                                ?: 0L,

                            status = when (
                                document.getString("status")
                                    ?.lowercase(Locale.ROOT)
                            ) {
                                "cancelled" -> "Nakansela"
                                "completed" -> "Tapos Na"
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
     * Dispatches the selected driver and creates exactly one ledger
     * transaction. Active drivers return to the end of the queue.
     */
    // Queue entries whose dispatch is currently being written.
    private val dispatchingIds = mutableSetOf<String>()

    /**
     * The automatic "bawas" shown in the dispatch modal: a fixed amount
     * per seater from the settings. The admin may still change it at
     * dispatch time.
     */
    suspend fun suggestedDeduction(driverId: String): Double {
        val driverSnapshot = firestore
            .collection(FirestoreCollections.USERS)
            .whereEqualTo("userId", driverId)
            .whereEqualTo("role", "driver")
            .limit(1)
            .get()
            .await()

        val seater = driverSnapshot.documents
            .firstOrNull()
            ?.getLong("seater")
            ?.toInt()
            ?: 0

        val tiersSnapshot = firestore
            .collection(FirestoreCollections.SETTINGS)
            .document("deductionTiers")
            .get()
            .await()

        return tierFor(seater, tiersSnapshot.data)
    }

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

                /*
                 * Record how the automatic deduction was computed: the
                 * number of seats of the jeep and its fixed tier amount.
                 */
                val seater = driverAccount
                    .getLong("seater")
                    ?.toInt()
                    ?: 0

                val tierDeduction = tierFor(
                    seater,
                    firestore
                        .collection(FirestoreCollections.SETTINGS)
                        .document("deductionTiers")
                        .get()
                        .await()
                        .data
                )

                val remainingActive = queueSnapshot.documents
                    .filter {
                        it.id != vehicleId &&
                                isActiveStatus(it.getString("status"))
                    }
                    .sortedWith(
                        compareBy<DocumentSnapshot> {
                            if (it.getBoolean("isCancelled") == true) 1 else 0
                        }.thenBy {
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

                /*
                 * Positions are per route. The dispatched entry left its
                 * route, so the drivers behind it in the SAME route move
                 * up one spot.
                 */
                val currentRoute = currentDocument.getString("route")

                val sameRouteRemaining = remainingActive.filter {
                    sameRoute(it.getString("route"), currentRoute)
                }

                val oppositeRouteRemaining = remainingActive.filter {
                    !sameRoute(it.getString("route"), currentRoute)
                }

                sameRouteRemaining.forEachIndexed { index, document ->
                    transaction.update(
                        document.reference,
                        mapOf(
                            "position" to index + 1,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                /*
                 * The dispatched driver travels to the other terminal and
                 * enters the OPPOSITE route at position #1. The drivers
                 * already in that pila move one spot behind him.
                 */
                oppositeRouteRemaining.forEachIndexed { index, document ->
                    transaction.update(
                        document.reference,
                        mapOf(
                            "position" to index + 2,
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

                    /*
                     * He enters the OPPOSITE route (the one going back)
                     * at the FRONT: "when the first driver of one route
                     * is dispatched he becomes #1 of the other route".
                     */
                    val route = oppositeRoute(
                        currentDocument.getString("route")
                    )

                    val requeueData = mapOf<String, Any>(
                        "id" to requeueId,
                        "driverId" to driverId,
                        "driverName" to driverName,
                        "plateNumber" to plateNumber,
                        "route" to route,
                        "position" to 1,
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
                        "seater" to seater,
                        "seaterDeduction" to tierDeduction,
                        "deductionCustomized" to
                                (otherFees != tierDeduction),
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

                ActivityLogger.log(
                    firestore,
                    userSession,
                    "Dispatch",
                    "$plateNumber — $driverName • " +
                            oppositeRoute(currentDocument.getString("route"))
                )

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
     * Cancels an active queue entry. The entry stays in the queue as
     * "Nakansela" at the LAST position ("pinakadulo") and OUT of the
     * line: no dispatch button. "Ibalik sa Pila" returns it to the END
     * of the active pila, exactly like any other waiting vehicle. It
     * does not go to the Kasaysayan tab.
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

                val targetRoute = target.getString("route")

                val remaining = queueSnapshot.documents
                    .filter {
                        it.id != vehicleId &&
                                isActiveStatus(it.getString("status")) &&
                                sameRoute(
                                    it.getString("route"),
                                    targetRoute
                                )
                    }
                    .sortedWith(
                        compareBy<DocumentSnapshot> {
                            if (it.getBoolean("isCancelled") == true) 1 else 0
                        }.thenBy {
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

                    // The cancelled entry stays in the queue at the bottom.
                    transaction.update(
                        target.reference,
                        mapOf(
                            "isCancelled" to true,
                            "position" to remaining.size + 1,
                            "cancelledAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )

                    // The remaining entries keep (or move up) their
                    // positions above the cancelled entry.
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

                ActivityLogger.log(
                    firestore,
                    userSession,
                    "Kanselahin sa Pila",
                    target.getString("plateNumber").orEmpty()
                )

                ToastFeedback.show(
                    "Nakansela ang " +
                            target.getString("plateNumber").orEmpty() +
                            ". Inilipat ito sa pinakadulo ng pila."
                )
            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message ?: "Hindi makansela ang sasakyan."
                )
            }
        }
    }

    /**
     * Returns a cancelled entry to the normal queue. It is placed at the
     * END of the non-cancelled entries (just above the other cancelled
     * ones) and the whole queue is renumbered.
     */
    fun restoreQueueEntry(vehicleId: String) {
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

                val targetRoute = target.getString("route")

                val remaining = queueSnapshot.documents
                    .filter {
                        it.id != vehicleId &&
                                isActiveStatus(it.getString("status")) &&
                                sameRoute(
                                    it.getString("route"),
                                    targetRoute
                                )
                    }
                    .sortedWith(
                        compareBy<DocumentSnapshot> {
                            if (it.getBoolean("isCancelled") == true) 1 else 0
                        }.thenBy {
                            it.getLong("position") ?: Long.MAX_VALUE
                        }.thenBy {
                            it.safeTimestamp("createdAt")?.seconds
                                ?: Long.MAX_VALUE
                        }
                    )

                /*
                 * The restored entry joins the END of the normal
                 * (non-cancelled) group, before any cancelled entries.
                 */
                val ordered = remaining.filter {
                    it.getBoolean("isCancelled") != true
                } + listOf(target) + remaining.filter {
                    it.getBoolean("isCancelled") == true
                }

                firestore.runTransaction { transaction ->
                    val freshEntry = transaction.get(target.reference)

                    if (!freshEntry.exists() ||
                        !isActiveStatus(freshEntry.getString("status"))
                    ) {
                        throw IllegalStateException(
                            "Ang sasakyang ito ay wala na sa aktibong pila."
                        )
                    }

                    ordered.forEachIndexed { index, document ->
                        val update = mutableMapOf<String, Any>(
                            "position" to index + 1,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )

                        if (document.id == vehicleId) {
                            update["isCancelled"] = false
                        }

                        transaction.update(document.reference, update)
                    }

                    true
                }.await()

                ActivityLogger.log(
                    firestore,
                    userSession,
                    "Ibalik sa Pila",
                    target.getString("plateNumber").orEmpty()
                )

                ToastFeedback.show(
                    "Ibinalik sa pila ang " +
                            target.getString("plateNumber").orEmpty() + "."
                )
            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message ?: "Hindi maibalik ang sasakyan sa pila."
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

        val targetVehicle = currentState.activeQueue.firstOrNull {
            it.id == vehicleId
        } ?: return

        /*
         * Positions are per route: only the vehicles of the SAME route
         * are renumbered when one of them changes its spot.
         */
        val active = currentState.activeQueue.filter {
            sameRoute(it.route, targetVehicle.route)
        }.toMutableList()

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

    /*
     * -----------------------------
     * DAILY RESET ("reset ng pila")
     * -----------------------------
     * AWTOMATIKO ang reset tuwing cut-off hour (default 9:00 PM / 21:00,
     * settable ng superadmin sa Settings) - hindi ito manual (tinanggal
     * ang "I-reset Ngayon"). Ang rotation: ang #1 ngayon ay huli na
     * bukas at ang kasunod ang mauuna. Ang unang tseke ay tuwing
     * binubuksan ang queue tab (catch-up), tapos may timer na nagre-reset
     * mismo sa oras ng cut-off.
     */

    private fun rotationMetaReference() =
        firestore
            .collection(FirestoreCollections.SETTINGS)
            .document("queueRotation")

    /**
     * The queue "day" flips at the cut-off hour (default 21:00) - after
     * the cut-off the queue already belongs to the next operating day.
     */
    fun queueDayId(
        now: Date = Date(),
        cutoffHour: Int = CUTOFF_HOUR
    ): String {
        val calendar = Calendar.getInstance()
        calendar.time = now
        if (calendar.get(Calendar.HOUR_OF_DAY) >= cutoffHour) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return SimpleDateFormat(
            "yyyyMMdd",
            Locale.US
        ).format(calendar.time)
    }

    /** Automatic check - runs whenever the queue opens (catch-up). */
    private fun maybeRotateQueue() {
        if (!userSession.isAdmin()) return
        viewModelScope.launch {
            try {
                val rotated = rotateQueueIfNeeded(force = false)
                if (rotated) {
                    onQueueRotated()
                }
            } catch (_: Exception) {
                // Best effort - susubukan ulit sa susunod na tikso.
            }
        }
    }

    private suspend fun rotateQueueIfNeeded(force: Boolean): Boolean {
        val queueSnapshot = firestore
            .collection(FirestoreCollections.QUEUE)
            .get()
            .await()

        return firestore.runTransaction { transaction ->
            val metaReference = rotationMetaReference()
            val meta = transaction.get(metaReference)
            val lastReset = meta.getString("lastQueueDay")

            // Ang cut-off hour ay settable ng superadmin (default 21 = 9 PM).
            val hour = (meta.getLong("cutoffHour")?.toInt() ?: CUTOFF_HOUR)
                .coerceIn(0, 23)
            cutoffHour = hour
            _cutoffLabel.value = formatCutoffHour(hour)

            val today = queueDayId(Date(), hour)

            if (!force && lastReset == today) {
                return@runTransaction false
            }

            listOf(ROUTE_MA, ROUTE_AM).forEach { route ->
                val active = queueSnapshot.documents
                    .filter {
                        isActiveStatus(it.getString("status")) &&
                                it.getBoolean("isCancelled") != true &&
                                sameRoute(it.getString("route"), route)
                    }
                    .sortedBy {
                        it.getLong("position") ?: Long.MAX_VALUE
                    }

                if (active.size > 1) {
                    /*
                     * Rotation: today's first goes to the last spot,
                     * everyone else moves up one position.
                     */
                    val rotated = active.drop(1) + active.first()
                    rotated.forEachIndexed { index, document ->
                        transaction.update(
                            document.reference,
                            mapOf(
                                "position" to index + 1,
                                "updatedAt" to
                                        FieldValue.serverTimestamp()
                            )
                        )
                    }
                }
            }

            transaction.set(
                metaReference,
                mapOf(
                    "lastQueueDay" to today,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            true
        }.await()
    }

    /**
     * "Tapos Na" (driver side): ends today's travel and leaves the pila
     * completely - the entry moves to the Kasaysayan tab.
     */
    fun endTravel(vehicleId: String) {
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
                        "Wala na sa pila ang sasakyang ito."
                    )
                }

                val route = target.getString("route")

                val remaining = queueSnapshot.documents
                    .filter {
                        it.id != vehicleId &&
                                isActiveStatus(it.getString("status")) &&
                                sameRoute(it.getString("route"), route)
                    }
                    .sortedWith(
                        compareBy<DocumentSnapshot> {
                            if (it.getBoolean("isCancelled") == true) 1 else 0
                        }.thenBy {
                            it.getLong("position") ?: Long.MAX_VALUE
                        }.thenBy {
                            it.safeTimestamp("createdAt")?.seconds
                                ?: Long.MAX_VALUE
                        }
                    )

                firestore.runTransaction { transaction ->
                    val freshEntry = transaction.get(target.reference)

                    if (!freshEntry.exists() ||
                        !isActiveStatus(freshEntry.getString("status"))
                    ) {
                        throw IllegalStateException(
                            "Wala na sa pila ang sasakyang ito."
                        )
                    }

                    transaction.update(
                        target.reference,
                        mapOf(
                            "status" to "completed",
                            "endTravelAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp(),
                            "position" to 0
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

                ActivityLogger.log(
                    firestore,
                    userSession,
                    "Tapos Na",
                    target.getString("plateNumber").orEmpty()
                )

                ToastFeedback.show(
                    "Tapos na ang biyahe. Wala na sa pila ang " +
                            target.getString("plateNumber").orEmpty() + "."
                )
            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message ?: "Hindi matapos ang biyahe."
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
        rotationWatch?.cancel()
        queueListener?.remove()
        super.onCleared()
    }
}
package com.mobile.enerjeep.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.mobile.enerjeep.EnerJeepApp
import com.mobile.enerjeep.data.ActivityLogger
import com.mobile.enerjeep.data.DispatchNotifier
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.FirestoreIds
import com.mobile.enerjeep.data.UserRole
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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

    // Shared optimistic lock for queue operations that start from a snapshot.
    private fun queueControlReference() =
        firestore.collection(FirestoreCollections.SETTINGS).document("queueControl")

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
    private var rotationSettingsListener: ListenerRegistration? = null

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
         * Active = nasa pang-araw-araw na pila ("Nakapila", kasama ang
         * naka-flag na "cancelled" - hiwalay ang flag sa status). Ang
         * dispatched/cancelled/completed/resolved ay history na.
         */
        fun isActiveStatus(
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
        observeRotationSettings()
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
        // Only superadmin can run one-time maintenance across both terminals.
        if (!userSession.isSuperadmin()) return

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

                // Preserve every valid terminal assignment. Only legacy entries
                // without a route are placed at the end of the Marilao queue.
                val active = queueSnapshot.documents.filter {
                    isActiveStatus(it.getString("status"))
                }
                val missingRoute = active
                    .filter { it.getString("route").isNullOrBlank() }
                    .sortedWith(
                        compareBy<DocumentSnapshot> {
                            if (it.getBoolean("isCancelled") == true) 1 else 0
                        }.thenBy {
                            it.getLong("position") ?: Long.MAX_VALUE
                        }.thenBy {
                            it.safeTimestamp("createdAt")?.seconds ?: Long.MAX_VALUE
                        }
                    )
                val nextMarilaoPosition = active
                    .filter { sameRoute(it.getString("route"), ROUTE_AM) }
                    .mapNotNull { it.getLong("position")?.toInt() }
                    .maxOrNull() ?: 0

                val batch = firestore.batch()
                missingRoute.forEachIndexed { index, document ->
                    batch.update(
                        document.reference,
                        mapOf(
                            "route" to ROUTE_AM,
                            "position" to nextMarilaoPosition + index + 1,
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    )
                }

                batch.set(
                    metaReference,
                    mapOf(
                        "routesNormalized" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                batch.set(
                    queueControlReference(),
                    mapOf(
                        "revision" to FieldValue.increment(1L),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                )
                batch.commit().await()
            } catch (_: Exception) {
                // Best effort - valid route assignments remain untouched.
            }
        }
    }

    private fun observeRotationSettings() {
        rotationSettingsListener?.remove()
        rotationSettingsListener = rotationMetaReference()
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                val latestHour = (snapshot.getLong("cutoffHour")?.toInt()
                    ?: CUTOFF_HOUR).coerceIn(0, 23)
                val changed = latestHour != cutoffHour
                cutoffHour = latestHour
                _cutoffLabel.value = formatCutoffHour(latestHour)

                // Restart the timer immediately when Settings changes the reset hour.
                if (changed && userSession.isSuperadmin()) {
                    startRotationWatch()
                }
            }
    }

    private fun startRotationWatch() {
        // The daily reset affects both terminals and follows the latest saved cutoff.
        if (!userSession.isSuperadmin()) return
        rotationWatch?.cancel()
        rotationWatch = viewModelScope.launch {
            while (isActive) {
                try {
                    val result = QueueRotation.rotateOnce(firestore)
                    cutoffHour = result.cutoffHour
                    _cutoffLabel.value = formatCutoffHour(result.cutoffHour)
                    if (result.rotated) {
                        onQueueRotated()
                    }
                } catch (_: Exception) {
                    // Best effort - susubukan ulit sa susunod na tikso.
                }
                delay(QueueRotation.millisUntilNextCutoff(cutoffHour))
            }
        }
    }

    private fun onQueueRotated() {
        ActivityLogger.log(
            firestore,
            userSession,
            "Reset ng Pila",
            "Automatic (${_cutoffLabel.value})"
        )
        ToastFeedback.show(
            "Naibalik ang pila sa pagkakasunod ng petsa ng paggawa ng account."
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
        val canManageAllStations = userSession.isSuperadmin()
        val assignedStation = userSession.userProfile?.adminStation
            ?.trim()
            ?.lowercase(Locale.ROOT)
        if (!canManageAllStations &&
            assignedStation !in listOf(
                UserRole.STATION_MARILAO,
                UserRole.STATION_MONUMENTO
            )
        ) return

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

                val completedTodayDriverIds = queueSnapshot.documents
                    .filter { document ->
                        document.getString("status")
                            .equals("completed", ignoreCase = true) &&
                            sameDayMillis(
                                document.safeTimestamp("endTravelAt")?.toDate()?.time ?: 0L,
                                System.currentTimeMillis()
                            )
                    }
                    .mapNotNull { it.getString("driverId") }
                    .toSet()

                // Remember each driver's most recent known route from their history.
                val historicalRouteByDriver = queueSnapshot.documents
                    .filter {
                        !it.getString("driverId").isNullOrBlank() &&
                            !it.getString("route").isNullOrBlank()
                    }
                    .groupBy { it.getString("driverId").orEmpty() }
                    .mapValues { (_, entries) ->
                        entries.maxByOrNull {
                            it.safeTimestamp("updatedAt")?.seconds
                                ?: it.safeTimestamp("createdAt")?.seconds
                                ?: 0L
                        }?.getString("route")
                    }

                val stranded = usersSnapshot.documents.mapNotNull { user ->
                    val userId = user.getString("userId")
                        .orEmpty()
                        .ifBlank { user.id }

                    val lastKnownRoute = historicalRouteByDriver[userId]
                        ?.takeIf { it.isNotBlank() }
                    val recoveryRoute = lastKnownRoute
                        ?: if (canManageAllStations) DEFAULT_ROUTE else null

                    val routeBelongsToAdmin = canManageAllStations ||
                        (recoveryRoute != null && when (assignedStation) {
                            UserRole.STATION_MARILAO ->
                                recoveryRoute.startsWith("Marilao", ignoreCase = true)
                            UserRole.STATION_MONUMENTO ->
                                recoveryRoute.startsWith("Monumento", ignoreCase = true)
                            else -> false
                        })

                    val needsRecovery =
                        user.getBoolean("isVehicleActive") != false &&
                            !user.getString("status").orEmpty()
                                .equals("inactive", ignoreCase = true) &&
                            userId !in openDriverIds &&
                            userId !in completedTodayDriverIds &&
                            recoveryRoute != null &&
                            routeBelongsToAdmin

                    if (needsRecovery) user to recoveryRoute!! else null
                }

                if (stranded.isEmpty()) {
                    return@launch
                }

                val nextPositionByStation = mutableMapOf<String, Int>()
                openEntries.forEach { entry ->
                    val stationKey = if (sameRoute(entry.getString("route"), ROUTE_AM)) {
                        UserRole.STATION_MARILAO
                    } else {
                        UserRole.STATION_MONUMENTO
                    }
                    val position = entry.getLong("position")?.toInt() ?: 0
                    nextPositionByStation[stationKey] = maxOf(
                        nextPositionByStation[stationKey] ?: 0,
                        position
                    )
                }

                stranded.forEach { (user, recoveryRoute) ->
                    val userId = user.getString("userId")
                        .orEmpty()
                        .ifBlank { user.id }

                    val routeStation = if (
                        recoveryRoute.startsWith("Marilao", ignoreCase = true)
                    ) UserRole.STATION_MARILAO else UserRole.STATION_MONUMENTO
                    val nextPosition = (nextPositionByStation[routeStation] ?: 0) + 1
                    nextPositionByStation[routeStation] = nextPosition

                    val driverName = listOf(
                        user.getString("firstName"),
                        user.getString("lastName")
                    ).filter { !it.isNullOrBlank() }
                        .joinToString(" ")
                        .ifBlank { "Driver" }

                    // Stable per operating day: retries update one recovery entry.
                    val queueId = "queue_" + userId +
                        "_recovery_" + QueueRotation.queueDayId()

                    val recoveryBatch = firestore.batch()
                    recoveryBatch.set(
                        firestore.collection(FirestoreCollections.QUEUE)
                            .document(queueId),
                        mapOf<String, Any>(
                            "id" to queueId,
                            "driverId" to userId,
                            "plateNumber" to user.getString("plateNumber").orEmpty(),
                            "driverName" to driverName,
                            "route" to recoveryRoute,
                            "position" to nextPosition,
                            "status" to "Nakapila",
                            "time" to SimpleDateFormat(
                                "hh:mm a",
                                Locale.getDefault()
                            ).format(Date()),
                            "accountCreatedAt" to (
                                user.getTimestamp("createdAt")
                                    ?: FieldValue.serverTimestamp()
                            ),
                            "createdAt" to FieldValue.serverTimestamp()
                        )
                    )
                    recoveryBatch.set(
                        queueControlReference(),
                        mapOf(
                            "revision" to FieldValue.increment(1L),
                            "updatedAt" to FieldValue.serverTimestamp()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    recoveryBatch.commit().await()
                }

                ToastFeedback.show(
                    "Naibalik sa kani-kanilang terminal ang " +
                        stranded.size +
                        " aktibong driver na wala sa pila."
                )
            } catch (_: Exception) {
                // Best effort: recovery never changes a different terminal's queue.
            }
        }
    }

    fun retryLoading() {
        _uiState.value = QueueUiState.Loading
        startQueueListener()
    }

    private fun startQueueListener() {
        queueListener?.remove()
        queueListener = null
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

                val positionByRoute = mutableMapOf<Int, Int>()
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
                    .map { document ->
                        val route = routeKey(document.getString("route"))
                        val displayPosition = (positionByRoute[route] ?: 0) + 1
                        positionByRoute[route] = displayPosition
                        QueueVehicle(
                            id = document.id,

                            driverId = document
                                .getString("driverId")
                                .orEmpty(),

                            position = displayPosition,

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
    // Serializes queue mutations from this app instance while allowing each button to show its own loading state.
    private val queueMutationMutex = Mutex()

    // Queue entries whose dispatch is currently being written.
    private val dispatchingIds = mutableSetOf<String>()

    private val _busyActions = MutableStateFlow<Set<String>>(emptySet())
    val busyActions = _busyActions.asStateFlow()

    private val queueActionStateLock = Any()

    /**
     * Only one queue-changing action is accepted at a time on this device.
     * The active action key remains in busyActions so its own control can
     * display a spinner while every other queue control is disabled.
     */
    private fun beginQueueAction(key: String): Boolean =
        synchronized(queueActionStateLock) {
            if (_busyActions.value.isNotEmpty()) {
                ToastFeedback.show(
                    "May kasalukuyang aksyon sa pila. Hintaying matapos ito bago gumawa ng panibago."
                )
                false
            } else {
                _busyActions.value = setOf(key)
                true
            }
        }

    private fun finishQueueAction(key: String) {
        synchronized(queueActionStateLock) {
            if (key in _busyActions.value) {
                _busyActions.value = _busyActions.value - key
            }
        }
    }

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

    fun canManageRoute(route: String?): Boolean {
        if (userSession.isSuperadmin()) return true
        val profile = userSession.userProfile ?: return false
        if (profile.role != UserRole.Admin) return false
        val routeStation = when {
            route.orEmpty().trim().startsWith("Marilao", ignoreCase = true) ->
                UserRole.STATION_MARILAO
            route.orEmpty().trim().startsWith("Monumento", ignoreCase = true) ->
                UserRole.STATION_MONUMENTO
            else -> null
        }
        return routeStation != null &&
            profile.adminStation?.trim()?.equals(routeStation, ignoreCase = true) == true
    }

    private fun canManageQueueEntryOrNotify(vehicleId: String): Boolean {
        val state = _uiState.value as? QueueUiState.Success
        val vehicle = state?.activeQueue?.firstOrNull { it.id == vehicleId }
        if (vehicle == null || !canManageRoute(vehicle.route)) {
            ToastFeedback.show("Wala kang pahintulot na baguhin ang entry na ito.")
            return false
        }
        return true
    }

    /**
     * Dispatch now runs through a trusted callable because dispatching also
     * updates the returning terminal's positions and writes the ledger entry.
     */
    fun dispatchVehicle(
        vehicleId: String,
        income: Double,
        otherFees: Double,
        onComplete: (Boolean) -> Unit = {}
    ) {
        if (income < 0.0) {
            ToastFeedback.show("Hindi maaaring negatibo ang kita")
            return
        }
        if (otherFees < 0.0) {
            ToastFeedback.show("Hindi maaaring negatibo ang ibang bayad")
            return
        }
        if (otherFees > income) {
            ToastFeedback.show("Hindi maaaring mas mataas ang ibang bayad kaysa sa kita")
            return
        }
        if (!canManageQueueEntryOrNotify(vehicleId)) return
        if (!dispatchingIds.add(vehicleId)) return

        val vehicle = (_uiState.value as? QueueUiState.Success)
            ?.activeQueue
            ?.firstOrNull { it.id == vehicleId }
            ?: run {
                dispatchingIds.remove(vehicleId)
                ToastFeedback.show("Hindi makita ang sasakyan sa pila.")
                return
            }

        val busyKey = "dispatch:$vehicleId"
        if (!beginQueueAction(busyKey)) {
            dispatchingIds.remove(vehicleId)
            return
        }

        viewModelScope.launch {
            var completed = false
            try {
                // Wait for earlier queue actions from this app instance. The
                // callable re-reads the queue inside its own server transaction.
                queueMutationMutex.withLock {
                    FirebaseFunctions.getInstance("us-central1")
                        .getHttpsCallable("dispatchQueueVehicle")
                        .call(
                            mapOf(
                                "queueEntryId" to vehicleId,
                                "income" to income,
                                "otherFees" to otherFees
                            )
                        )
                        .await()
                }
                completed = true

                val dispatchedLabel = listOf(vehicle.plateNumber, vehicle.driverName)
                    .filter { it.isNotBlank() }
                    .joinToString(" — ")

                // Dispatch is logged by the trusted Cloud Function in the
                // same transaction as the queue and ledger writes.
                ToastFeedback.show("Na-dispatch na ang driver: $dispatchedLabel")
                DispatchNotifier.post(
                    EnerJeepApp.instance,
                    "May na-dispatch",
                    listOf(
                        vehicle.plateNumber,
                        vehicle.route,
                        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date())
                    ).joinToString(" • ")
                )
            } catch (e: Exception) {
                ToastFeedback.show(e.message ?: "Hindi ma-dispatch ang sasakyan")
            } finally {
                dispatchingIds.remove(vehicleId)
                finishQueueAction(busyKey)
                onComplete(completed)
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
        if (!canManageQueueEntryOrNotify(vehicleId)) return
        val busyKey = "cancel:$vehicleId"
        if (!beginQueueAction(busyKey)) return
        var targetPlateNumber = ""
        viewModelScope.launch {
            try {
                queueMutationMutex.withLock {
                    val queueControlRef = queueControlReference()
                    val observedQueueRevision = queueControlRef.get().await()
                        .getLong("revision") ?: 0L
                    val queueSnapshot = firestore
                        .collection(FirestoreCollections.QUEUE)
                        .get()
                        .await()

                    val target = queueSnapshot.documents
                        .firstOrNull { it.id == vehicleId }
                        ?: throw IllegalStateException(
                            "Hindi makita ang sasakyan sa pila."
                        )
                    targetPlateNumber = target.getString("plateNumber").orEmpty()

                    if (!isActiveStatus(target.getString("status")) ||
                        target.getBoolean("isCancelled") == true
                    ) {
                        throw IllegalStateException(
                            "Ang entry na ito ay wala na sa aktibong pila o nakansela na."
                        )
                    }

                    val queueReferencesById =
                        queueSnapshot.documents.associate { it.id to it.reference }

                    firestore.runTransaction { transaction ->
                        val queueControl = transaction.get(queueControlRef)
                        val currentRevision = queueControl.getLong("revision") ?: 0L
                        if (currentRevision != observedQueueRevision) {
                            throw IllegalStateException(
                                "Nagbago ang pila mula sa ibang device. I-refresh at subukan muli."
                            )
                        }
                        val freshEntry = transaction.get(target.reference)

                        if (!freshEntry.exists() ||
                            !isActiveStatus(freshEntry.getString("status")) ||
                            freshEntry.getBoolean("isCancelled") == true
                        ) {
                            throw IllegalStateException(
                                "Ang entry na ito ay wala na sa aktibong pila o nakansela na."
                            )
                        }

                        val remaining = queueReferencesById
                            .filterKeys { it != vehicleId }
                            .values
                            .map { transaction.get(it) }
                            .filter {
                                it.exists() &&
                                    isActiveStatus(it.getString("status")) &&
                                    sameRoute(
                                        it.getString("route"),
                                        freshEntry.getString("route")
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

                        transaction.update(
                            target.reference,
                            mapOf(
                                "isCancelled" to true,
                                "position" to remaining.size + 1,
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

                        transaction.set(
                            queueControlRef,
                            mapOf(
                                "revision" to currentRevision + 1L,
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        true
                    }.await()
                }

                ActivityLogger.log(
                    firestore,
                    userSession,
                    "Kanselahin sa Pila",
                    targetPlateNumber
                )

                ToastFeedback.show(
                    "Nakansela ang " + targetPlateNumber +
                        " at nailipat sa pinakadulo ng pila."
                )
            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message ?: "Hindi makansela ang sasakyan."
                )
            } finally {
                finishQueueAction(busyKey)
            }
        }
    }

    /**
     * Returns a cancelled entry to the normal queue. It is placed at the
     * END of the non-cancelled entries (just above the other cancelled
     * ones) and the whole queue is renumbered.
     */
    fun restoreQueueEntry(vehicleId: String) {
        if (!canManageQueueEntryOrNotify(vehicleId)) return
        val busyKey = "restore:$vehicleId"
        if (!beginQueueAction(busyKey)) return
        var targetPlateNumber = ""
        viewModelScope.launch {
            try {
                queueMutationMutex.withLock {
                    val queueControlRef = queueControlReference()
                    val observedQueueRevision = queueControlRef.get().await()
                        .getLong("revision") ?: 0L
                    val queueSnapshot = firestore
                        .collection(FirestoreCollections.QUEUE)
                        .get()
                        .await()

                    val target = queueSnapshot.documents
                        .firstOrNull { it.id == vehicleId }
                        ?: throw IllegalStateException(
                            "Hindi makita ang sasakyan sa pila."
                        )
                    targetPlateNumber = target.getString("plateNumber").orEmpty()

                    if (!isActiveStatus(target.getString("status")) ||
                        target.getBoolean("isCancelled") != true
                    ) {
                        throw IllegalStateException(
                            "Hindi nakansela o wala na sa aktibong pila ang entry."
                        )
                    }

                    val queueReferencesById =
                        queueSnapshot.documents.associate { it.id to it.reference }

                    firestore.runTransaction { transaction ->
                        val queueControl = transaction.get(queueControlRef)
                        val currentRevision = queueControl.getLong("revision") ?: 0L
                        if (currentRevision != observedQueueRevision) {
                            throw IllegalStateException(
                                "Nagbago ang pila mula sa ibang device. I-refresh at subukan muli."
                            )
                        }
                        val freshEntry = transaction.get(target.reference)

                        if (!freshEntry.exists() ||
                            !isActiveStatus(freshEntry.getString("status")) ||
                            freshEntry.getBoolean("isCancelled") != true
                        ) {
                            throw IllegalStateException(
                                "Hindi na nakansela o wala na sa aktibong pila ang entry."
                            )
                        }

                        val remaining = queueReferencesById
                            .filterKeys { it != vehicleId }
                            .values
                            .map { transaction.get(it) }
                            .filter {
                                it.exists() &&
                                    isActiveStatus(it.getString("status")) &&
                                    sameRoute(
                                        it.getString("route"),
                                        freshEntry.getString("route")
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

                        val ordered = remaining.filter {
                            it.getBoolean("isCancelled") != true
                        } + listOf(freshEntry) + remaining.filter {
                            it.getBoolean("isCancelled") == true
                        }

                        ordered.forEachIndexed { index, document ->
                            val update = mutableMapOf<String, Any>(
                                "position" to index + 1,
                                "updatedAt" to FieldValue.serverTimestamp()
                            )

                            if (document.id == vehicleId) {
                                update["isCancelled"] = false
                                update["cancelledAt"] = FieldValue.delete()
                            }

                            transaction.update(document.reference, update)
                        }

                        transaction.set(
                            queueControlRef,
                            mapOf(
                                "revision" to currentRevision + 1L,
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        true
                    }.await()
                }

                ActivityLogger.log(
                    firestore,
                    userSession,
                    "Ibalik sa Pila",
                    targetPlateNumber
                )

                ToastFeedback.show("Ibinalik sa pila ang " + targetPlateNumber + ".")
            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message ?: "Hindi maibalik ang sasakyan sa pila."
                )
            } finally {
                finishQueueAction(busyKey)
            }
        }
    }

    fun updateVehiclePosition(
        vehicleId: String,
        positionDelta: Int
    ) {
        if (positionDelta == 0) return
        if (!canManageQueueEntryOrNotify(vehicleId)) return
        val busyKey = if (positionDelta < 0) {
            "position-up:$vehicleId"
        } else {
            "position-down:$vehicleId"
        }
        if (!beginQueueAction(busyKey)) return

        viewModelScope.launch {
            try {
                queueMutationMutex.withLock {
                    val queueControlRef = queueControlReference()
                    val observedRevision = queueControlRef.get().await()
                        .getLong("revision") ?: 0L
                    val queueSnapshot = firestore
                        .collection(FirestoreCollections.QUEUE)
                        .get()
                        .await()
                    val references = queueSnapshot.documents.associate {
                        it.id to it.reference
                    }
                    val targetReference = references[vehicleId]
                        ?: throw IllegalStateException("Hindi makita ang sasakyan sa pila.")

                    firestore.runTransaction { transaction ->
                        val control = transaction.get(queueControlRef)
                        val currentRevision = control.getLong("revision") ?: 0L
                        if (currentRevision != observedRevision) {
                            throw IllegalStateException(
                                "Nagbago ang pila mula sa ibang device. I-refresh at subukan muli."
                            )
                        }

                        val freshTarget = transaction.get(targetReference)
                        if (!freshTarget.exists() ||
                            !isActiveStatus(freshTarget.getString("status")) ||
                            freshTarget.getBoolean("isCancelled") == true
                        ) {
                            throw IllegalStateException(
                                "Hindi na maaaring baguhin ang posisyon ng entry na ito."
                            )
                        }
                        val route = freshTarget.getString("route").orEmpty()

                        val freshDocs = references.values
                            .map { transaction.get(it) }
                            .filter {
                                it.exists() &&
                                    isActiveStatus(it.getString("status")) &&
                                    sameRoute(it.getString("route"), route)
                            }
                            .sortedWith(
                                compareBy<DocumentSnapshot> {
                                    if (it.getBoolean("isCancelled") == true) 1 else 0
                                }.thenBy {
                                    it.getLong("position") ?: Long.MAX_VALUE
                                }.thenBy {
                                    it.safeTimestamp("createdAt")?.seconds ?: Long.MAX_VALUE
                                }
                            )

                        val movingQueue = freshDocs.filter {
                            it.getBoolean("isCancelled") != true
                        }.toMutableList()
                        val cancelledQueue = freshDocs.filter {
                            it.getBoolean("isCancelled") == true
                        }

                        val fromIndex = movingQueue.indexOfFirst {
                            it.id == vehicleId
                        }
                        if (fromIndex < 0) {
                            throw IllegalStateException("Wala na sa aktibong pila ang entry.")
                        }

                        val destinationIndex = (fromIndex + positionDelta)
                            .coerceIn(0, movingQueue.lastIndex)
                        val movingEntry = movingQueue.removeAt(fromIndex)
                        movingQueue.add(destinationIndex, movingEntry)

                        // Keep all cancelled entries at the bottom regardless
                        // of how several up/down actions are tapped in succession.
                        (movingQueue + cancelledQueue).forEachIndexed { index, document ->
                            transaction.update(
                                document.reference,
                                mapOf(
                                    "position" to index + 1,
                                    "updatedAt" to FieldValue.serverTimestamp()
                                )
                            )
                        }

                        transaction.set(
                            queueControlRef,
                            mapOf(
                                "revision" to currentRevision + 1L,
                                "updatedAt" to FieldValue.serverTimestamp()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        true
                    }.await()
                }
            } catch (e: Exception) {
                ToastFeedback.show(e.message ?: "Hindi ma-update ang posisyon")
            } finally {
                finishQueueAction(busyKey)
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
     * bukas at ang kasunod ang mauuna.
     *
     * Ang aktwal na engine ay nasa QueueRotation (shared) - tumatakbo
     * ito HINDI lang sa Pila tab kundi pati agad sa pag-login ng admin
     * o superadmin (tingnan ang ContentViewModel). Kaya kahit hindi
     * superadmin ang naka-login at kahit anong screen ang bukas, nagre-
     * reset ang pila sa tamang oras.
     */

    private fun rotationMetaReference() =
        QueueRotation.metaReference(firestore)

    /** Automatic check - runs whenever the queue opens (catch-up). */
    private fun maybeRotateQueue() {
        if (!userSession.isSuperadmin()) return
        viewModelScope.launch {
            try {
                val result = QueueRotation.rotateOnce(firestore)
                cutoffHour = result.cutoffHour
                _cutoffLabel.value = formatCutoffHour(result.cutoffHour)
                if (result.rotated) {
                    onQueueRotated()
                }
            } catch (_: Exception) {
                // Best effort - susubukan ulit sa susunod na tikso.
            }
        }
    }

    /**
     * "Tapos Na" (driver side): ends today's travel and leaves the pila
     * completely - the entry moves to the Kasaysayan tab.
     */
    private val endingTravelIds = mutableSetOf<String>()

    /**
     * Driver completion is handled by the trusted callable so the client
     * cannot directly modify the shared queue or move other drivers.
     */
    fun endTravel(vehicleId: String) {
        if (!endingTravelIds.add(vehicleId)) return

        viewModelScope.launch {
            try {
                FirebaseFunctions.getInstance()
                    .getHttpsCallable("finishDriverTravel")
                    .call(mapOf("queueEntryId" to vehicleId))
                    .await()

                ActivityLogger.log(
                    firestore,
                    userSession,
                    "Tapos Na Bumyahe",
                    vehicleId
                )
                ToastFeedback.show("Tapos na ang biyahe.")
            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message ?: "Hindi matapos ang biyahe."
                )
            } finally {
                endingTravelIds.remove(vehicleId)
            }
        }
    }

    /** True kapag pareho ang petsa ng dalawang timestamp (millis). */
    private fun sameDayMillis(a: Long, b: Long): Boolean {
        val manila = TimeZone.getTimeZone("Asia/Manila")
        val fmt = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = manila
        }
        return fmt.format(Date(a)) == fmt.format(Date(b))
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
        rotationSettingsListener?.remove()
        rotationSettingsListener = null
        queueListener?.remove()
        super.onCleared()
    }
}
package com.mobile.enerjeep.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.ActivityLogger
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.safeTimestamp
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.ui.components.ToastFeedback
import com.mobile.enerjeep.ui.queue.QueueViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject


/**
 * Fase 7: in-app dispatch alert (sound + banner + bell). Walang push
 * notification o Cloud Functions - habang bukas ang app, doon lang
 * maririnig/makikita ang alert.
 */
data class DriverDispatchAlert(
    val id: String = "",
    val plateNumber: String = "",
    val route: String = "",
    val time: String = ""
)

data class DriverDashboardUiState(
    val firstName: String = "",
    val plateNumber: String = "",
    val route: String = "",
    val queuePosition: Int = 0,
    val totalInQueue: Int = 0,
    val queueStatus: String = "",
    val activeIncidentTitle: String? = null,
    val activeIncidentDate: String? = null,
    // The plate of the jeepney the incident belongs to. It can be the
    // plate of another driver, because the dashboard shows the incidents
    // of the whole cooperative.
    val activeIncidentPlate: String? = null,
    // How many incidents are still open.
    val openIncidentCount: Int = 0,
    // Fase 4B: posisyon BUKAS (rotation: #1 -> hulihan, iba -> bawas 1).
    val posisyonBukas: Int = 0,
    // Sino ang #1 at sino ang kasunod sa pila ng ruta ng driver.
    val queueLeadName: String = "",
    val queueLeadPlate: String = "",
    val queueNextName: String = "",
    val queueNextPlate: String = "",
    // The doc id of the driver's own active queue entry ("Tapos Na").
    val activeQueueDocId: String = "",
    val isCancelledInQueue: Boolean = false
)

@HiltViewModel
class DriverViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverDashboardUiState())
    val uiState: StateFlow<DriverDashboardUiState> = _uiState.asStateFlow()

    // Fase 7: dispatch alert state
    private val _dispatchAlert =
        MutableStateFlow<DriverDispatchAlert?>(null)
    val dispatchAlert: StateFlow<DriverDispatchAlert?> =
        _dispatchAlert.asStateFlow()

    private val _recentAlerts =
        MutableStateFlow<List<DriverDispatchAlert>>(emptyList())
    val recentAlerts: StateFlow<List<DriverDispatchAlert>> =
        _recentAlerts.asStateFlow()

    private val _hasUnreadAlerts = MutableStateFlow(false)
    val hasUnreadAlerts: StateFlow<Boolean> =
        _hasUnreadAlerts.asStateFlow()

    // Ids ng mga dispatch na nakita na (para sa "bago lang" detection).
    private var seenDispatchedIds: MutableSet<String>? = null

    private var profileListener: ListenerRegistration? = null
    private var queueListener: ListenerRegistration? = null
    private var incidentListener: ListenerRegistration? = null

    init {
        attachListeners()
    }

    private fun attachListeners() {
        val profile = userSession.userProfile ?: return

        profileListener = firestore.collection(FirestoreCollections.USERS)
            .document(profile.phoneNumber)
            .addSnapshotListener { document, _ ->
                if (document != null && document.exists()) {
                    refreshBaseProfile(document)
                }
            }

        queueListener = firestore.collection(FirestoreCollections.QUEUE)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                updateQueueState(snapshot.documents)
                checkDispatchAlerts(snapshot.documents)
            }

        incidentListener = firestore.collection(FirestoreCollections.INCIDENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                updateIncidentState(snapshot.documents)
            }
    }

    private fun refreshBaseProfile(document: DocumentSnapshot) {
        val current = _uiState.value
        _uiState.value = current.copy(
            firstName = document.getString("firstName") ?: userSession.userProfile?.firstName.orEmpty(),
            plateNumber = document.getString("plateNumber") ?: userSession.userProfile?.plateNumber.orEmpty()
        )
    }

    private fun matchesDriver(document: DocumentSnapshot): Boolean {
        val profile = userSession.userProfile ?: return false
        val driverId = document.getString("driverId")
        val plateNumber = document.getString("plateNumber")
        return driverId == profile.userId ||
                (!profile.plateNumber.isNullOrBlank() && plateNumber == profile.plateNumber)
    }

    private fun updateQueueState(documents: List<DocumentSnapshot>) {
        val driverDocs = documents.filter(::matchesDriver)
        val active = driverDocs.filter { isActiveQueueStatus(it.getString("status")) }
            .sortedBy { it.getLong("position") ?: Int.MAX_VALUE.toLong() }

        // Ang sariling entry: unahin ang hindi nakansela.
        val own = active.firstOrNull { it.getBoolean("isCancelled") != true }
            ?: active.firstOrNull()
        val ownRoute = own?.getString("route") ?: _uiState.value.route

        /*
         * Ang pila ay PER ROUTE (magkaiba ang bilang at posisyon bawat
         * ruta), kaya ang "ng X sa pila" at ang "#1 & kasunod" ay
         * kinukuha sa ruta ng driver lang.
         */
        val routeFront = documents.filter {
            isActiveQueueStatus(it.getString("status")) &&
                    it.getBoolean("isCancelled") != true &&
                    QueueViewModel.sameRoute(it.getString("route"), ownRoute)
        }.sortedBy { it.getLong("position") ?: Int.MAX_VALUE.toLong() }

        val lead = routeFront.getOrNull(0)
        val next = routeFront.getOrNull(1)

        val position = own?.getLong("position")?.toInt() ?: 0
        val total = routeFront.size

        /*
         * Posisyon BUKAS: sa rotation ng cut-off, ang #1 ngayon ay
         * mapupunta sa hulihan at ang lahat ay uusad ng isa. Kaya kung
         * #1 ka ngayon -> hulihan ka bukas; kung hindi -> bawas 1.
         */
        val bukas = when {
            own == null || own.getBoolean("isCancelled") == true -> 0
            total <= 1 -> 1
            position == 1 -> total
            position > 1 -> position - 1
            else -> 0
        }

        _uiState.value = _uiState.value.copy(
            queuePosition = position,
            totalInQueue = total,
            queueStatus = own?.getString("status").orEmpty(),
            route = ownRoute,
            posisyonBukas = bukas,
            queueLeadName = lead?.getString("driverName").orEmpty(),
            queueLeadPlate = lead?.getString("plateNumber").orEmpty(),
            queueNextName = next?.getString("driverName").orEmpty(),
            queueNextPlate = next?.getString("plateNumber").orEmpty(),
            activeQueueDocId = own?.id.orEmpty(),
            isCancelledInQueue = own?.getBoolean("isCancelled") == true
        )
    }

    /*
     * Fase 7: kapag ang sasakyan ng driver ay na-DISPATCH (status
     * "dispatched"), mag-alert: sound (sa UI host), banner, at bell.
     * Ang unang snapshot ay "seed" lang - walang alert sa pagbukas
     * ng app para sa mga lumang dispatch.
     */
    private fun checkDispatchAlerts(
        documents: List<DocumentSnapshot>
    ) {
        val dispatched = documents.filter {
            it.getString("status") == "dispatched" &&
                    matchesDriver(it)
        }
        val ids = dispatched.map { it.id }.toSet()

        if (seenDispatchedIds == null) {
            seenDispatchedIds = ids.toMutableSet()
            return
        }

        val fresh = dispatched.filter { it.id !in seenDispatchedIds.orEmpty() }
        seenDispatchedIds?.addAll(ids)

        if (fresh.isEmpty()) return

        fresh.forEach { document ->
            val alert = DriverDispatchAlert(
                id = document.id,
                plateNumber =
                    document.getString("plateNumber").orEmpty(),
                route = document.getString("route").orEmpty(),
                time = java.text.SimpleDateFormat(
                    "hh:mm a",
                    java.util.Locale.getDefault()
                ).format(java.util.Date())
            )

            _recentAlerts.value =
                (listOf(alert) + _recentAlerts.value).take(20)
            _dispatchAlert.value = alert
            _hasUnreadAlerts.value = true
        }
    }

    fun dismissDispatchAlert() {
        _dispatchAlert.value = null
    }

    fun markAlertsRead() {
        _hasUnreadAlerts.value = false
    }

    /**
     * "Tapos Na": ang driver ay aalis sa pila (status "completed" ->
     * Kasaysayan) at ang nasa likod niya ay uusad.
     */
    fun endTravel() {
        val docId = _uiState.value.activeQueueDocId
        if (docId.isBlank()) {
            ToastFeedback.show("Wala ka sa pila ngayon.")
            return
        }

        viewModelScope.launch {
            try {
                val target = firestore
                    .collection(FirestoreCollections.QUEUE)
                    .document(docId)
                    .get()
                    .await()

                if (!target.exists() ||
                    !isActiveQueueStatus(target.getString("status"))
                ) {
                    throw IllegalStateException(
                        "Wala na sa pila ang sasakyang ito."
                    )
                }

                val route = target.getString("route")
                val queueSnapshot = firestore
                    .collection(FirestoreCollections.QUEUE)
                    .get()
                    .await()

                val remaining = queueSnapshot.documents
                    .filter {
                        it.id != docId &&
                                isActiveQueueStatus(it.getString("status")) &&
                                QueueViewModel.sameRoute(
                                    it.getString("route"), route
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

                firestore.runTransaction { transaction ->
                    val fresh = transaction.get(target.reference)
                    if (!fresh.exists() ||
                        !isActiveQueueStatus(fresh.getString("status"))
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
                    "Tapos na ang biyahe. Wala ka na sa pila."
                )
            } catch (e: Exception) {
                ToastFeedback.show(
                    e.message ?: "Hindi matapos ang biyahe."
                )
            }
        }
    }

    private fun updateIncidentState(documents: List<DocumentSnapshot>) {
        /*
         * The dashboard of the driver shows the open incidents of the whole
         * cooperative, the same way the "Insidente" tab does. It used to
         * show only the incidents that carried the driverId or the plate of
         * the signed-in driver, so the card stayed empty for most drivers.
         * Only the fields the app already stores are shown, so no personal
         * data of the other drivers is exposed.
         */
        val openIncidents = documents
            .filter { !(it.getBoolean("isResolved") ?: false) && (it.getString("status") ?: "open").lowercase() != "resolved" }

        val incident = openIncidents
            .sortedByDescending { it.safeTimestamp("createdAt")?.seconds ?: 0L }
            .firstOrNull()

        _uiState.value = _uiState.value.copy(
            activeIncidentTitle = incident?.getString("title"),
            activeIncidentDate = incident?.getString("date"),
            activeIncidentPlate = incident?.getString("plateNumber"),
            openIncidentCount = openIncidents.size
        )
    }

    private fun isActiveQueueStatus(status: String?): Boolean {
        return when (status?.lowercase()) {
            "dispatched", "cancelled", "completed", "resolved" -> false
            else -> true
        }
    }

    fun clearWarning() {
        _uiState.value = _uiState.value.copy(
            activeIncidentTitle = null,
            activeIncidentDate = null,
            activeIncidentPlate = null
        )
    }

    override fun onCleared() {
        profileListener?.remove()
        queueListener?.remove()
        incidentListener?.remove()
        super.onCleared()
    }
}

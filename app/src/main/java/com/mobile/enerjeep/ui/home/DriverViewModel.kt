package com.mobile.enerjeep.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.safeTimestamp
import com.mobile.enerjeep.data.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject


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
    val openIncidentCount: Int = 0
)

@HiltViewModel
class DriverViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverDashboardUiState())
    val uiState: StateFlow<DriverDashboardUiState> = _uiState.asStateFlow()

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

        val own = active.firstOrNull()
        _uiState.value = _uiState.value.copy(
            queuePosition = own?.getLong("position")?.toInt() ?: 0,
            totalInQueue = documents.count { isActiveQueueStatus(it.getString("status")) },
            queueStatus = own?.getString("status").orEmpty(),
            route = own?.getString("route") ?: _uiState.value.route
        )
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

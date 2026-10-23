package com.mobile.enerjeep.ui.incident

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.UserSession
import com.mobile.enerjeep.data.safeTimestamp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DriverIncident(
    val id: String,
    val title: String,
    val plateNumber: String,
    val location: String,
    val description: String,
    val date: String,
    val isResolved: Boolean,
    val multiya: Double = 0.0,
    val isSentToDriver: Boolean = true,
    // Newest activity (resolved time, else created time) used for ordering.
    val sortMillis: Long = 0L,
    // Creation time of the record, used by the "Ngayon" date filter.
    val createdAtMillis: Long = 0L
)

enum class IncidentTabType {
    DI_NAAYOS, NAAYOS
}

data class DriverIncidentUiState(
    val openCount: Int = 0,
    val resolvedCount: Int = 0,
    val searchQuery: String = "",
    val selectedTab: IncidentTabType = IncidentTabType.DI_NAAYOS,
    val allIncidents: List<DriverIncident> = emptyList(),
    val filteredIncidents: List<DriverIncident> = emptyList(),
    val selectedIncident: DriverIncident? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class DriverIncidentViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val userSession: UserSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverIncidentUiState())
    val uiState: StateFlow<DriverIncidentUiState> = _uiState.asStateFlow()
    private var listener: ListenerRegistration? = null

    init {
        startListener()
    }

    /**
     * Drivers need to see the incidents of every driver so they can tell
     * which areas keep having accidents. The listener therefore reads the
     * whole collection instead of only the records of the signed-in
     * driver. Only the fields the app already stores are shown, so no
     * personal data of the other drivers is exposed.
     */
    private fun startListener() {
        listener?.remove()

        listener = firestore.collection(FirestoreCollections.INCIDENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error.message
                            ?: "Hindi ma-load ang mga insidente."
                    )
                    return@addSnapshotListener
                }

                if (snapshot == null) return@addSnapshotListener

                /*
                 * Reports that were saved WITHOUT sending are admin-only
                 * drafts - a driver must only see incidents that were
                 * explicitly sent to them.
                 */
                val incidents = snapshot.documents
                    .filter { it.getBoolean("sentToDriver") != false }
                    .map { mapIncident(it) }
                    .sortedByDescending { it.sortMillis }

                val open = incidents.count { !it.isResolved }
                val resolved = incidents.count { it.isResolved }

                _uiState.value = _uiState.value.copy(
                    openCount = open,
                    resolvedCount = resolved,
                    allIncidents = incidents,
                    isLoading = false,
                    errorMessage = null
                )
                applyFilters()
            }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applyFilters()
    }

    fun onTabSelected(tab: IncidentTabType) {
        _uiState.value = _uiState.value.copy(selectedTab = tab)
        applyFilters()
    }

    fun selectIncident(incident: DriverIncident) {
        _uiState.value = _uiState.value.copy(selectedIncident = incident)
    }

    fun clearSelectedIncident() {
        _uiState.value = _uiState.value.copy(selectedIncident = null)
    }

    private fun applyFilters() {
        val currentState = _uiState.value

        /*
         * "Di-Naayos" shows only the incidents that are still open.
         * "Naayos" shows only the resolved incidents.
         */
        val tabIncidents = currentState.allIncidents.filter { incident ->
            if (currentState.selectedTab == IncidentTabType.NAAYOS) {
                incident.isResolved
            } else {
                !incident.isResolved
            }
        }

        val filtered = tabIncidents.filter { incident ->
            incident.title.contains(currentState.searchQuery, ignoreCase = true) ||
                    incident.plateNumber.contains(currentState.searchQuery, ignoreCase = true) ||
                    incident.location.contains(currentState.searchQuery, ignoreCase = true) ||
                    incident.description.contains(currentState.searchQuery, ignoreCase = true)
        }

        _uiState.value = currentState.copy(filteredIncidents = filtered)
    }

    private fun mapIncident(document: DocumentSnapshot): DriverIncident {
        val profile = userSession.userProfile

        return DriverIncident(
            id = document.id,
            title = document.getString("title") ?: "Insidente",
            plateNumber = document.getString("plateNumber")
                ?: profile?.plateNumber.orEmpty(),
            location = document.getString("location").orEmpty(),
            description = document.getString("description").orEmpty(),
            date = document.getString("date").orEmpty(),
            isResolved = document.getBoolean("isResolved") == true || document.getString("status")?.lowercase() == "resolved",
            multiya = (document.get("penalty") as? Number)?.toDouble() ?: 0.0,
            isSentToDriver = document.getBoolean("sentToDriver") ?: true,
            sortMillis = (
                    document.safeTimestamp("resolvedAt")
                        ?: document.safeTimestamp("createdAt")
                    )?.toDate()?.time ?: 0L,
            createdAtMillis = document
                .safeTimestamp("createdAt")
                ?.toDate()
                ?.time
                ?: 0L
        )
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}

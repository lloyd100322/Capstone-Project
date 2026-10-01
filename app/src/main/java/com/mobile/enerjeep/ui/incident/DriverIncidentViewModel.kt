package com.mobile.enerjeep.ui.incident

import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.mobile.enerjeep.data.FirestoreCollections
import com.mobile.enerjeep.data.UserSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DriverIncident(
    val id: String,
    val title: String,
    val plateNumber: String,
    val description: String,
    val date: String,
    val isResolved: Boolean,
    val multiya: Double = 0.0,
    val isSentToDriver: Boolean = true
)

enum class IncidentTabType {
    KASALUKUYAN, KASAYSAYAN
}

data class DriverIncidentUiState(
    val openCount: Int = 0,
    val resolvedCount: Int = 0,
    val searchQuery: String = "",
    val selectedTab: IncidentTabType = IncidentTabType.KASALUKUYAN,
    val allIncidents: List<DriverIncident> = emptyList(),
    val filteredIncidents: List<DriverIncident> = emptyList(),
    val selectedIncident: DriverIncident? = null
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
        listener = firestore.collection(FirestoreCollections.INCIDENTS)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val profile = userSession.userProfile ?: return@addSnapshotListener
                val incidents = snapshot.documents
                    .filter { document ->
                        document.getString("driverId") == profile.userId ||
                                document.getString("plateNumber") == profile.plateNumber
                    }
                    .map { mapIncident(it) }
                    .sortedByDescending { it.date }

                val open = incidents.count { !it.isResolved }
                val resolved = incidents.count { it.isResolved }

                _uiState.value = _uiState.value.copy(
                    openCount = open,
                    resolvedCount = resolved,
                    allIncidents = incidents
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
        val isResolvedTab = currentState.selectedTab == IncidentTabType.KASAYSAYAN

        val filtered = currentState.allIncidents.filter { incident ->
            incident.isResolved == isResolvedTab &&
                    (incident.title.contains(currentState.searchQuery, ignoreCase = true) ||
                            incident.plateNumber.contains(currentState.searchQuery, ignoreCase = true) ||
                            incident.description.contains(currentState.searchQuery, ignoreCase = true))
        }

        _uiState.value = currentState.copy(filteredIncidents = filtered)
    }

    private fun mapIncident(document: DocumentSnapshot): DriverIncident {
        return DriverIncident(
            id = document.id,
            title = document.getString("title") ?: "Insidente",
            plateNumber = document.getString("plateNumber") ?: userSession.userProfile?.plateNumber.orEmpty(),
            description = document.getString("description").orEmpty(),
            date = document.getString("date").orEmpty(),
            isResolved = document.getBoolean("isResolved") == true || document.getString("status")?.lowercase() == "resolved",
            multiya = document.getDouble("penalty") ?: 0.0,
            isSentToDriver = document.getBoolean("sentToDriver") ?: true
        )
    }

    override fun onCleared() {
        listener?.remove()
        super.onCleared()
    }
}

package com.mobile.enerjeep.ui.incident

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// Updated Data Model for the Incident
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

// Updated UI State
data class DriverIncidentUiState(
    val openCount: Int = 0,
    val resolvedCount: Int = 0,
    val searchQuery: String = "",
    val selectedTab: IncidentTabType = IncidentTabType.KASALUKUYAN,
    val allIncidents: List<DriverIncident> = emptyList(),
    val filteredIncidents: List<DriverIncident> = emptyList(),
    val selectedIncident: DriverIncident? = null // Holds the incident to display in the details view
)

@HiltViewModel
class DriverIncidentViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(DriverIncidentUiState())
    val uiState: StateFlow<DriverIncidentUiState> = _uiState.asStateFlow()

    init {
        loadIncidents()
    }

    private fun loadIncidents() {
        val mockIncidents = listOf(
            DriverIncident("1", "Sira ang makina", "ABC-123", "Umuusok ang makina", "Aug 30, 2026", false, 0.0, true),
            DriverIncident("2", "Flat Tire", "ABC-123", "Banda sa Monumento", "Aug 29, 2026", false, 0.0, true),
            DriverIncident("3", "Nasiraan ng suspension", "ABC-123", "Lubak", "Aug 22, 2026", true, 500.0, true),
            DriverIncident("4", "fasasd", "6543as", "aacfadasd", "Aug 4, 2026", false, 0.0, true),
            DriverIncident("5", "Ayaw mag-start", "ABC-123", "Battery issue", "Aug 10, 2026", true, 0.0, true),
            DriverIncident("6", "Pundidong ilaw", "ABC-123", "Headlight", "Aug 4, 2026", true, 0.0, true)
        )

        val openCount = mockIncidents.count { !it.isResolved }
        val resolvedCount = mockIncidents.count { it.isResolved }

        _uiState.value = _uiState.value.copy(
            openCount = openCount,
            resolvedCount = resolvedCount,
            allIncidents = mockIncidents
        )
        applyFilters()
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
}
package com.mobile.enerjeep.ui.incident

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// --- 1. DATA MODELS ---
data class IncidentItem(
    val id: String,
    val title: String,
    val plateNumber: String,
    val location: String,
    val date: String,
    val penalty: String,
    val description: String,
    val sentToDriver: String = "Oo",
    val status: String = "Bukas"
)

// --- 2. UI STATE ---
sealed interface IncidentUiState {
    data object Loading : IncidentUiState
    data class Success(
        val activeIncidents: List<IncidentItem>,
        val historyIncidents: List<IncidentItem>,
        val openCount: Int,
        val resolvedCount: Int
    ) : IncidentUiState
    data class Error(val message: String) : IncidentUiState
}

// --- 3. VIEWMODEL ---
@HiltViewModel
class IncidentViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<IncidentUiState>(IncidentUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // In-memory databases (starting with a few items so the screen isn't empty)
    private var activeList = mutableListOf(
        IncidentItem("1", "Nasiraan ng suspension", "6543as — juan", "Lias", "Aug 24, 2026", "₱0", "Tumunog ang ilalim ng jeep."),
        IncidentItem("2", "Sira ang makina", "XYZ-987 — Maria", "Malhacan", "Aug 25, 2026", "₱500", "Ayaw umandar sa gitna ng byahe.")
    )

    private var historyList = mutableListOf(
        IncidentItem("3", "Flat Tire", "ABC-123 — Pedro", "Monumento", "Aug 20, 2026", "₱0", "Na-flat ang gulong sa likod.", status = "Naayos")
    )

    init {
        pushStateToUI()
    }

    private fun pushStateToUI() {
        _uiState.value = IncidentUiState.Success(
            activeIncidents = activeList.toList(),
            historyIncidents = historyList.toList(),
            openCount = activeList.size,
            resolvedCount = historyList.size
        )
    }

    // --- BUSINESS ACTIONS ---

    fun reportIncident(plateNumber: String, type: String, location: String, penalty: String, description: String) {
        val formattedPenalty = if (penalty.isBlank()) "₱0" else "₱$penalty"

        val newItem = IncidentItem(
            id = System.currentTimeMillis().toString(),
            title = type.ifBlank { "Di-tiyak na Insidente" },
            plateNumber = plateNumber,
            location = location.ifBlank { "Di-tiyak na lokasyon" },
            date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date()),
            penalty = formattedPenalty,
            description = description,
            status = "Bukas"
        )

        // Add new incident to the top of the active list
        activeList.add(0, newItem)
        pushStateToUI()
    }

    fun resolveIncident(incidentId: String) {
        // Find the incident in the active list
        val incident = activeList.find { it.id == incidentId } ?: return

        // Remove it from active
        activeList.remove(incident)

        // Mark as resolved and add to history
        val resolvedIncident = incident.copy(status = "Naayos")
        historyList.add(0, resolvedIncident)

        pushStateToUI()
    }
}
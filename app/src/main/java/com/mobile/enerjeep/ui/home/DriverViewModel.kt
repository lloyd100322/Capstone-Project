package com.mobile.enerjeep.ui.home

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// 1. Define the UI State for the Driver Dashboard
data class DriverDashboardUiState(
    val firstName: String = "",
    val plateNumber: String = "",
    val route: String = "",
    val queuePosition: Int = 0,
    val totalInQueue: Int = 0,
    val queueStatus: String = "",
    val activeIncidentTitle: String? = null,
    val activeIncidentDate: String? = null
)

// 2. Create the ViewModel
@HiltViewModel
class DriverViewModel @Inject constructor(
    // TODO: Inject your repositories here later (e.g., AuthRepository, QueueRepository)
) : ViewModel() {

    private val _uiState = MutableStateFlow(DriverDashboardUiState())
    val uiState: StateFlow<DriverDashboardUiState> = _uiState.asStateFlow()

    init {
        loadDriverData()
    }

    private fun loadDriverData() {
        // Mock data matching your design.
        // Once connected to Firebase, you will update this state with real database queries.
        _uiState.value = DriverDashboardUiState(
            firstName = "Juan",
            plateNumber = "ABC-123",
            route = "Monumento – Marilao",
            queuePosition = 1,
            totalInQueue = 1,
            queueStatus = "Naghintay ng Dispatch",
            activeIncidentTitle = "Sira ang makina",
            activeIncidentDate = "Aug 30, 2026"
        )
    }

    // Example action: If the driver resolves the warning, you can clear it
    fun clearWarning() {
        _uiState.value = _uiState.value.copy(
            activeIncidentTitle = null,
            activeIncidentDate = null
        )
    }
}
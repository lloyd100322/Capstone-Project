package com.mobile.enerjeep.ui.schedule

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// 1. Define the UI State for the Schedule Tab
data class ScheduleUiState(
    val queuePosition: Int = 0,
    val queueStatus: String = "",
    val plateNumber: String = "",
    val route: String = "",
    val totalInQueue: Int = 0
)

// 2. Create the ViewModel
@HiltViewModel
class ScheduleViewModel @Inject constructor(
    // Inject repositories here later when integrating Firebase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState.asStateFlow()

    init {
        loadScheduleData()
    }

    private fun loadScheduleData() {
        // Mock data perfectly matching your Frame 7 mockup
        _uiState.value = ScheduleUiState(
            queuePosition = 1,
            queueStatus = "Naghintay ng Dispatch",
            plateNumber = "ABC-123",
            route = "Monumento – Marilao",
            totalInQueue = 1
        )
    }
}
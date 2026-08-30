package com.mobile.enerjeep.ui.queue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

// --- 1. DATA MODELS ---
data class QueueVehicle(
    val id: String,
    val position: Int,
    val plateNumber: String,
    val driverName: String,
    val route: String,
    val time: String,
    val status: String,
    val showPositionEdit: Boolean = false
)

data class HistoryVehicle(
    val id: String,
    val plateNumber: String,
    val driverName: String,
    val time: String,
    val status: String
)

// --- 2. UI STATE (Sealed Interface) ---
sealed interface QueueUiState {
    data object Loading : QueueUiState
    data class Success(
        val activeQueue: List<QueueVehicle>,
        val historyQueue: List<HistoryVehicle>,
        val totalInQueue: Int,
        val totalDispatchedToday: Int
    ) : QueueUiState
    data class Error(val message: String) : QueueUiState
}

// --- 3. VIEWMODEL ---
@HiltViewModel
class QueueViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<QueueUiState>(QueueUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // 1. Keep the data stored in memory locally so we can manipulate it
    private var activeQueueList = mutableListOf(
        QueueVehicle("1", 1, "123das", "asdasd asdasd", "Monumento – Marilao", "2026-08-19", "Handa", false),
        QueueVehicle("2", 2, "657dgs", "Juan Dela Cruz", "Monumento – Marilao", "2026-08-19", "Nakapila", false),
        QueueVehicle("3", 3, "XYZ-987", "Pedro Penduko", "Monumento – Marilao", "2026-08-19", "Nakapila", false)
    )

    private var historyQueueList = mutableListOf(
        HistoryVehicle("4", "GHI 012", "Jose Reyes", "09:05 AM • Jul 17, 2026", "Nakansela"),
        HistoryVehicle("5", "DEF 789", "Maria Garcia", "08:47 AM • Jul 17, 2026", "Na-Dispatch")
    )

    init {
        pushStateToUI()
    }

    // Helper function to bundle the current memory lists and send them to the Screen
    private fun pushStateToUI() {
        _uiState.value = QueueUiState.Success(
            activeQueue = activeQueueList.toList(),
            historyQueue = historyQueueList.toList(),
            totalInQueue = activeQueueList.size,
            totalDispatchedToday = historyQueueList.count { it.status == "Na-Dispatch" }
        )
    }

    // --- BUSINESS ACTIONS ---

    fun dispatchVehicle(vehicleId: String, income: Double, otherFees: Double) {
        // Find the vehicle in the active queue
        val vehicle = activeQueueList.find { it.id == vehicleId } ?: return

        // Remove it from the active queue
        activeQueueList.remove(vehicle)

        // Add it to the top of the history queue
        historyQueueList.add(
            0, // Insert at top
            HistoryVehicle(
                id = vehicle.id,
                plateNumber = vehicle.plateNumber,
                driverName = vehicle.driverName,
                time = "Ngayon", // In a real app, format the current timestamp
                status = "Na-Dispatch"
            )
        )

        // Re-index the remaining active queue so positions are correct (1, 2, 3...)
        activeQueueList = activeQueueList.mapIndexed { index, v ->
            v.copy(position = index + 1)
        }.toMutableList()

        pushStateToUI()
    }

    fun updateVehiclePosition(vehicleId: String, newPosition: Int) {
        // 1. Find the current index of the vehicle
        val currentIndex = activeQueueList.indexOfFirst { it.id == vehicleId }
        if (currentIndex == -1) return

        // 2. Remove the vehicle temporarily
        val vehicleToMove = activeQueueList.removeAt(currentIndex)

        // 3. Calculate the new index (0-based).
        // We use coerceIn to ensure the user can't pick a position outside the list size.
        val newIndex = (newPosition - 1).coerceIn(0, activeQueueList.size)

        // 4. Insert the vehicle at its new location
        activeQueueList.add(newIndex, vehicleToMove)

        // 5. Re-assign the position numbers sequentially (1, 2, 3, etc.)
        // This ensures that when someone moves up, the person they replaced slides down naturally.
        activeQueueList = activeQueueList.mapIndexed { index, vehicle ->
            vehicle.copy(position = index + 1)
        }.toMutableList()

        // 6. Push the updated list back to the UI!
        pushStateToUI()
    }
}
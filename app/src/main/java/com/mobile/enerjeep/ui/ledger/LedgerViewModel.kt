package com.mobile.enerjeep.ui.ledger

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

// --- 1. DATA MODELS ---
data class LedgerItem(
    val id: String,
    val plateNumber: String,
    val driverName: String,
    val date: String,
    val amount: Double,
    val feeType: String,
    val status: String
)

// --- 2. UI STATE ---
sealed interface LedgerUiState {
    data object Loading : LedgerUiState
    data class Success(
        val todayCollections: List<LedgerItem>,
        val historyCollections: List<LedgerItem>,
        val totalToday: Double
    ) : LedgerUiState
    data class Error(val message: String) : LedgerUiState
}

// --- 3. VIEWMODEL ---
@HiltViewModel
class LedgerViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<LedgerUiState>(LedgerUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // In-memory mock databases
    private val todayList = mutableListOf<LedgerItem>()
    private val historyList = mutableListOf(
        LedgerItem("1", "6543as", "juan", "Aug 23, 2026", 50.0, "Terminal Fee", "Bayad"),
        LedgerItem("2", "ABC-123", "Pedro", "Aug 4, 2026", 50.0, "Terminal Fee", "Bayad")
    )

    init {
        pushStateToUI()
    }

    private fun pushStateToUI() {
        val total = todayList.sumOf { it.amount }
        _uiState.value = LedgerUiState.Success(
            todayCollections = todayList.toList(),
            historyCollections = historyList.toList(),
            totalToday = total
        )
    }

    // --- BUSINESS ACTIONS ---

    fun addCollection(plateNumber: String, driverName: String, amount: Double, feeType: String) {
        val newItem = LedgerItem(
            id = System.currentTimeMillis().toString(),
            plateNumber = plateNumber,
            driverName = driverName,
            date = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date()),
            amount = amount,
            feeType = feeType,
            status = "Bayad"
        )

        // Add to the top of today's list
        todayList.add(0, newItem)
        pushStateToUI()
    }
}
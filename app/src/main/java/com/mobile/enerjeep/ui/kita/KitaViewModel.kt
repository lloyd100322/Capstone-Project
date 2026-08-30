package com.mobile.enerjeep.ui.kita

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class KitaRecord(
    val id: String,
    val title: String,
    val date: String,
    val amount: Double,
    val type: String, // "Kita", "Bayad", "Multiya"
    val isHistory: Boolean // false = Kasalukuyan, true = Kasaysayan
)

enum class KitaTabType {
    KASALUKUYAN, KASAYSAYAN
}

data class KitaUiState(
    val netEarnings: Double = 0.0,
    val totalKita: Double = 0.0,
    val totalBayad: Double = 0.0,
    val totalMultiya: Double = 0.0,
    val selectedTab: KitaTabType = KitaTabType.KASALUKUYAN,
    val searchQuery: String = "",
    val records: List<KitaRecord> = emptyList(),
    val filteredRecords: List<KitaRecord> = emptyList()
)

@HiltViewModel
class KitaViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(KitaUiState())
    val uiState: StateFlow<KitaUiState> = _uiState.asStateFlow()

    init {
        loadKitaData()
    }

    private fun loadKitaData() {
        // Dynamic dataset that feeds calculations (ready for Firebase integration)
        val mockRecords = listOf(
            KitaRecord("1", "Pasahe Dagdag", "Aug 30, 2026", 0.0, "Kita", false),
            KitaRecord("2", "Terminal Fee", "Aug 29, 2026", 0.0, "Bayad", false),
            KitaRecord("3", "Kasaysayan Record", "Jul 15, 2026", 1200.0, "Kita", true)
        )

        updateMetricsAndFilter(mockRecords, _uiState.value.selectedTab, _uiState.value.searchQuery)
    }

    fun onTabSelected(tab: KitaTabType) {
        updateMetricsAndFilter(_uiState.value.records, tab, _uiState.value.searchQuery)
    }

    fun onSearchQueryChanged(query: String) {
        updateMetricsAndFilter(_uiState.value.records, _uiState.value.selectedTab, query)
    }

    private fun updateMetricsAndFilter(allRecords: List<KitaRecord>, tab: KitaTabType, query: String) {
        val isHistory = tab == KitaTabType.KASAYSAYAN

        // Filter items for the active tab view
        val currentTabRecords = allRecords.filter { it.isHistory == isHistory }

        // Dynamically compute summaries
        val kitaSum = currentTabRecords.filter { it.type == "Kita" }.sumOf { it.amount }
        val bayadSum = currentTabRecords.filter { it.type == "Bayad" }.sumOf { it.amount }
        val multiyaSum = currentTabRecords.filter { it.type == "Multiya" }.sumOf { it.amount }
        val net = kitaSum - (bayadSum + multiyaSum)

        val filtered = currentTabRecords.filter { record ->
            record.title.contains(query, ignoreCase = true) ||
                    record.date.contains(query, ignoreCase = true)
        }

        _uiState.value = _uiState.value.copy(
            netEarnings = net,
            totalKita = kitaSum,
            totalBayad = bayadSum,
            totalMultiya = multiyaSum,
            selectedTab = tab,
            searchQuery = query,
            records = allRecords,
            filteredRecords = filtered
        )
    }
}
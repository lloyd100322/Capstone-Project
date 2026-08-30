package com.mobile.enerjeep.ui.report

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

// --- 1. DATA MODELS ---
data class MonthlyIncome(
    val period: String,
    val trips: Int,
    val amount: Double,
    val growthPercentage: Double? = null
)

data class YearlyIncome(
    val year: String,
    val activeMonths: Int,
    val amount: Double
)

data class IncomeHistory(
    val plateNumber: String,
    val driverName: String,
    val netAmount: Double,
    val grossAmount: Double,
    val date: String
)

data class ReportIncidentItem(
    val title: String,
    val plateNumber: String,
    val location: String,
    val date: String,
    val penalty: Double
)

data class ReportMultiyaItem(
    val plateNumber: String,
    val driverName: String,
    val date: String,
    val amount: Double
)

data class DonasyonItem(
    val donor: String,
    val amount: Double,
    val date: String,
    val source: String,
    val note: String
)

// --- 2. UI STATE ---
sealed interface ReportUiState {
    data object Loading : ReportUiState
    data class Success(
        // Ulat ng Kita
        val monthlyData: List<MonthlyIncome>,
        val yearlyData: List<YearlyIncome>,
        val historyData: List<IncomeHistory>,

        // Ulat ng Insidente
        val currentIncidents: List<ReportIncidentItem>,
        val historyIncidents: List<ReportIncidentItem>,
        val totalIncidentPenalty: Double,

        // Ulat ng Multiya
        val currentMultiya: List<ReportMultiyaItem>,
        val historyMultiya: List<ReportMultiyaItem>,
        val multiyaNgayon: Double,
        val kabuuangMultiya: Double,

        // Donasyon ng Koop
        val currentDonasyon: List<DonasyonItem>,
        val historyDonasyon: List<DonasyonItem>,
        val donasyonNgayon: Double,
        val kabuuangDonasyon: Double
    ) : ReportUiState
    data class Error(val message: String) : ReportUiState
}

// --- 3. VIEWMODEL ---
@HiltViewModel
class ReportViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow<ReportUiState>(ReportUiState.Loading)
    val uiState = _uiState.asStateFlow()

    // --- IN-MEMORY MOCK DATA (Retained for testing) ---
    private val monthlyIncomeList = mutableListOf(
        MonthlyIncome("July 2026", 122, 63700.0, 29.1),
        MonthlyIncome("June 2026", 93, 49350.0)
    )
    private val yearlyIncomeList = mutableListOf(
        YearlyIncome("2026", 7, 359800.0)
    )
    private val incomeHistoryList = mutableListOf(
        IncomeHistory("123456", "jose", 550.0, 600.0, "Aug 4, 2026")
    )

    private val currentIncidentList = mutableListOf(
        ReportIncidentItem("Brake Issue", "ABC 123", "Perez", "Jul 17, 2026", 0.0)
    )
    private val historyIncidentList = mutableListOf(
        ReportIncidentItem("Engine Overheating", "XYZ 456", "Savemore Meyc", "Jul 18, 2026", 120.0),
        ReportIncidentItem("Flat Tire", "ABC 123", "McArthur Highway, Marilao", "Apr 15, 2026", 0.0)
    )

    private val currentMultiyaList = mutableListOf<ReportMultiyaItem>()
    private val historyMultiyaList = mutableListOf(
        ReportMultiyaItem("DEF 789", "Maria Garcia", "Jul 16, 2026", 200.0)
    )

    private var currentDonasyonList = mutableListOf(
        DonasyonItem("Glenn", 8000.0, "Jul 18, 2026", "byahe", "pasasalamat")
    )
    private val historyDonasyonList = mutableListOf(
        DonasyonItem("Brgy. Marilao", 5000.0, "Jul 17, 2026", "LGU", "Para sa terminal"),
        DonasyonItem("Juan Hardware", 3000.0, "Jul 10, 2026", "Negosyo", "Buwanang donasyon"),
        DonasyonItem("Anonymous", 1500.0, "Jun 20, 2026", "Pribado", "")
    )

    init {
        pushStateToUI()
    }

    private fun pushStateToUI() {
        // Calculate Totals Business Logic
        val totalIncidentPenalty = historyIncidentList.sumOf { it.penalty } + currentIncidentList.sumOf { it.penalty }

        val multiyaNgayon = currentMultiyaList.sumOf { it.amount }
        val kabuuangMultiya = multiyaNgayon + historyMultiyaList.sumOf { it.amount }

        val donasyonNgayon = currentDonasyonList.sumOf { it.amount }
        val kabuuangDonasyon = donasyonNgayon + historyDonasyonList.sumOf { it.amount }

        _uiState.value = ReportUiState.Success(
            monthlyData = monthlyIncomeList.toList(),
            yearlyData = yearlyIncomeList.toList(),
            historyData = incomeHistoryList.toList(),

            currentIncidents = currentIncidentList.toList(),
            historyIncidents = historyIncidentList.toList(),
            totalIncidentPenalty = totalIncidentPenalty,

            currentMultiya = currentMultiyaList.toList(),
            historyMultiya = historyMultiyaList.toList(),
            multiyaNgayon = multiyaNgayon,
            kabuuangMultiya = kabuuangMultiya,

            currentDonasyon = currentDonasyonList.toList(),
            historyDonasyon = historyDonasyonList.toList(),
            donasyonNgayon = donasyonNgayon,
            kabuuangDonasyon = kabuuangDonasyon
        )
    }

    // --- BUSINESS ACTIONS ---

    fun addDonasyon(donasyon: DonasyonItem) {
        currentDonasyonList.add(0, donasyon)
        pushStateToUI() // Pushing recalculates all the totals automatically!
    }
}
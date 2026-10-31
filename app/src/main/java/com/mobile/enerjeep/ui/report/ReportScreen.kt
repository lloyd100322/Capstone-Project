package com.mobile.enerjeep.ui.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.R
import com.mobile.enerjeep.ui.components.HistoryDateFilterBar
import com.mobile.enerjeep.ui.components.LabeledBarChart
import com.mobile.enerjeep.ui.components.HistoryFilterState
import com.mobile.enerjeep.ui.components.PesoIcon
import com.mobile.enerjeep.ui.components.SearchField
import com.mobile.enerjeep.ui.components.filterHistoryRecords
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.EnerjeepBlueLight
import com.mobile.enerjeep.ui.theme.ErrorRed
import com.mobile.enerjeep.ui.theme.ErrorRedLight
import com.mobile.enerjeep.ui.theme.FieldBorderColor
import com.mobile.enerjeep.ui.theme.HeartPink
import com.mobile.enerjeep.ui.theme.HeartPinkLight
import com.mobile.enerjeep.ui.theme.LightGrayBackground
import com.mobile.enerjeep.ui.theme.SuccessGreen
import com.mobile.enerjeep.ui.theme.TextSecondary
import com.mobile.enerjeep.ui.theme.WarningOrange
import com.mobile.enerjeep.ui.theme.WarningOrangeLight


fun formatCurrency(amount: Double): String = "₱${NumberFormat.getNumberInstance(Locale.US).format(amount)}"

@Composable
fun ReportScreen(
    viewModel: ReportViewModel = hiltViewModel(),
    /*
     * ISANG buong screen na ang Mga Ulat: naka-stack ang lahat ng
     * statistic na chart (Kita, Insidente, Multa, Donasyon) - walang
     * choices/buttons na pipindutin pa. "donasyon" = dumidiretso sa
     * Donasyon na seksyon (galing sa Donasyon stat ng dashboard).
     */
    initialSection: String = "top"
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        is ReportUiState.Loading -> Box(Modifier.fillMaxSize().background(LightGrayBackground), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EnerjeepBlue) }
        is ReportUiState.Error -> Box(Modifier.fillMaxSize().background(LightGrayBackground), contentAlignment = Alignment.Center) { Text(state.message, color = ErrorRed) }
        is ReportUiState.Success -> {
            val listState = rememberLazyListState()
            val sectionScope = rememberCoroutineScope()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().background(LightGrayBackground),
                contentPadding = PaddingValues(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 48.dp)
            ) {
                item {
                    HeaderRow()
                    Spacer(Modifier.height(28.dp))
                    Text("Mga Ulat", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
                    Spacer(Modifier.height(4.dp))
                    Text("Buong estadistika ng kooperatiba sa isang screen.", fontSize = 14.sp, color = TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    /*
                     * Mabilis na lipat sa bawat seksyon - hindi na kailangang
                     * i-scroll ang buong page para makarating sa Donasyon.
                     */
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionChip("Kita") {
                            sectionScope.launch { listState.scrollToItem(1) }
                        }
                        SectionChip("Insidente") {
                            sectionScope.launch { listState.scrollToItem(2) }
                        }
                        SectionChip("Multa") {
                            sectionScope.launch { listState.scrollToItem(3) }
                        }
                        SectionChip("Donasyon") {
                            sectionScope.launch { listState.scrollToItem(4) }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
                item {
                    ReportSectionHeader(Icons.Default.AccountBalanceWallet, EnerjeepBlue, EnerjeepBlueLight, "Ulat ng Kita")
                    Spacer(Modifier.height(16.dp))
                    UlatNgKitaContent(state.monthlyData, state.yearlyData, state.historyData)
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                    Spacer(Modifier.height(20.dp))
                }
                item {
                    ReportSectionHeader(Icons.Default.Warning, WarningOrange, WarningOrangeLight, "Ulat ng Insidente")
                    Spacer(Modifier.height(16.dp))
                    UlatNgInsidenteContent(state.currentIncidents, state.allIncidents, state.totalIncidentPenalty)
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                    Spacer(Modifier.height(20.dp))
                }
                item {
                    ReportSectionHeader(Icons.Default.PrivacyTip, ErrorRed, ErrorRedLight, "Ulat ng Multa")
                    Spacer(Modifier.height(16.dp))
                    UlatNgMultiyaContent(state.currentMultiya, state.allMultiya, state.multiyaNgayon, state.kabuuangMultiya)
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
                    Spacer(Modifier.height(20.dp))
                }
                item {
                    ReportSectionHeader(Icons.Default.FavoriteBorder, HeartPink, HeartPinkLight, "Donasyon ng Koop")
                    Spacer(Modifier.height(16.dp))
                    DonasyonContent(
                        state.currentDonasyon,
                        state.allDonasyon,
                        state.donasyonNgayon,
                        state.kabuuangDonasyon,
                        isSuperadmin = viewModel.isSuperadmin(),
                        onAddDonasyon = { viewModel.addDonasyon(it) }
                    )
                }
            }
            LaunchedEffect(initialSection) {
                if (initialSection == "donasyon") {
                    listState.scrollToItem(4)
                }
            }
        }
    }
}

@Composable
private fun SectionChip(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        border = BorderStroke(1.dp, FieldBorderColor)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = EnerjeepBlue
        )
    }
}

@Composable
private fun ReportSectionHeader(
    icon: ImageVector,
    iconColor: Color,
    iconBg: Color,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(44.dp), shape = RoundedCornerShape(14.dp), color = iconBg) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(22.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

@Composable
private fun HeaderRow() {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.app_icon), contentDescription = "Enerjeep Logo", modifier = Modifier.size(40.dp))
        Spacer(Modifier.width(12.dp)); Text("Enerjeep", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UlatNgKitaContent(monthlyData: List<MonthlyIncome>, yearlyData: List<YearlyIncome>, historyData: List<IncomeHistory>) {
    /*
     * Fase 5: ang dating Buwanang/Taunang LISTA ay naging LABELED
     * CHARTS (view-only), at ang tab ay pinalitan ng isang
     * "Kasaysayan" na may SEARCH (gaya ng ibang report) at date
     * filter - walang dropdown ng plaka.
     * Ayon sa hiling: KASAYSAYAN MUNA (nasa taas) at ang charts ay
     * nasa IBABA.
     */
    var searchQuery by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }
    Column(Modifier.fillMaxWidth()) {
        Text("Kasaysayan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        SearchField(searchQuery, { searchQuery = it }, "Hanapin ayon sa plaka o driver")
        Spacer(Modifier.height(8.dp))
        HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth())
        val filteredPlates = historyData.filter {
            searchQuery.isBlank() ||
                    it.plateNumber.contains(searchQuery, ignoreCase = true) ||
                    it.driverName.contains(searchQuery, ignoreCase = true)
        }
        val history = filterHistoryRecords(filteredPlates, { it.date }, historyFilter)
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) EmptyHistory() else history.forEach { IncomeHistoryCard(it); Spacer(Modifier.height(12.dp)) }

        /*
         * Ang mga chart ay nasa IBABA ng Kasaysayan.
         */
        Spacer(Modifier.height(12.dp))
        LabeledBarChart(
            title = "Buwanang Kita",
            bars = monthlyData.map { it.period to it.amount }
        )
        Spacer(Modifier.height(24.dp))
        LabeledBarChart(
            title = "Taunang Kita",
            bars = yearlyData.map { it.year to it.amount },
            barColor = SuccessGreen
        )
    }
}

@Composable
fun UlatNgInsidenteContent(currentIncidents: List<ReportIncidentItem>, allIncidents: List<ReportIncidentItem>, totalPenalty: Double) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }
    Column(Modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(24.dp)) { Text("Kabuuang Multa mula sa Insidente", color = Color.White); Spacer(Modifier.height(8.dp)); Text(formatCurrency(totalPenalty).replace(".00", ""), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        }
        Spacer(Modifier.height(24.dp))
        /*
         * Modern chart UI - narito dapat ang charts/statistics (hindi sa
         * driver Kita). Buong kasaysayan ang gamit ng chart.
         */
        val incidentBars = remember(allIncidents) {
            allIncidents
                .groupBy { it.plateNumber.ifBlank { "Walang plaka" } }
                .map { (plate, items) -> plate to items.size.toDouble() }
                .sortedByDescending { it.second }
                .take(7)
        }
        LabeledBarChart(
            title = "Bilang ng Insidente kada Plaka",
            bars = incidentBars,
            barColor = WarningOrange,
            isMoney = false // Bilang ito, hindi pera - walang peso sign.
        )
        Spacer(Modifier.height(24.dp)); SearchField(searchQuery, { searchQuery = it }, "Hanapin ayon sa uri, plaka, o lokasyon")
        Spacer(Modifier.height(20.dp)); ReportTabs(selectedTab) { selectedTab = it }
        if (selectedTab == 1) HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        // "Kasaysayan" is the complete list, so an incident that was made
        // today is in the history as well.
        val source = if (selectedTab == 0) currentIncidents else allIncidents
        val searched = source.filter { it.plateNumber.contains(searchQuery, true) || it.title.contains(searchQuery, true) || it.location.contains(searchQuery, true) }
        val list = if (selectedTab == 1) filterHistoryRecords(searched, { it.date }, historyFilter) else searched
        if (list.isEmpty()) { if (selectedTab == 1) EmptyHistory() else Text("Walang naitalang insidente.", color = TextSecondary) }
        else list.forEach { ReportIncidentCard(it); Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun UlatNgMultiyaContent(currentMultiya: List<ReportMultiyaItem>, allMultiya: List<ReportMultiyaItem>, multiyaNgayon: Double, kabuuangMultiya: Double) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }
    Column(Modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(24.dp)) { Text("Multa Ngayon", color = Color.White.copy(alpha = .9f)); Spacer(Modifier.height(8.dp)); Text(formatCurrency(multiyaNgayon).replace(".00", ""), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White); Spacer(Modifier.height(8.dp)); Text("Kabuuang: ${formatCurrency(kabuuangMultiya).replace(".00", "")}", fontSize = 12.sp, color = Color.White.copy(alpha = .8f)) }
        }
        Spacer(Modifier.height(24.dp))
        val multiyaBars = remember(allMultiya) {
            allMultiya
                .groupBy { it.plateNumber.ifBlank { "Walang plaka" } }
                .map { (plate, items) -> plate to items.sumOf { it.amount } }
                .sortedByDescending { it.second }
                .take(7)
        }
        LabeledBarChart(
            title = "Multa kada Plaka",
            bars = multiyaBars,
            barColor = ErrorRed
        )
        Spacer(Modifier.height(24.dp)); SearchField(searchQuery, { searchQuery = it }, "Hanapin ayon sa plaka"); Spacer(Modifier.height(20.dp)); ReportTabs(selectedTab) { selectedTab = it }
        if (selectedTab == 1) HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        // "Kasaysayan" is the complete list, so a fine that was made today
        // is in the history as well.
        val source = if (selectedTab == 0) currentMultiya else allMultiya
        val searched = source.filter { it.plateNumber.contains(searchQuery, true) }
        val list = if (selectedTab == 1) filterHistoryRecords(
            searched,
            { it.date },
            historyFilter,
            { it.createdAtMillis.takeIf { millis -> millis > 0L } }
        ) else searched
        if (list.isEmpty()) { if (selectedTab == 1) EmptyHistory() else Text("Walang multa ngayon.", color = TextSecondary) }
        else list.forEach { ReportMultiyaCard(it); Spacer(Modifier.height(12.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonasyonContent(currentDonasyon: List<DonasyonItem>, allDonasyon: List<DonasyonItem>, donasyonNgayon: Double, kabuuangDonasyon: Double, isSuperadmin: Boolean, onAddDonasyon: (DonasyonItem) -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddModal by remember { mutableStateOf(false) }
    var showGamitModal by remember { mutableStateOf(false) }
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nakalap = remember(allDonasyon) { allDonasyon.filter { it.type != "gamit" }.sumOf { it.amount } }
    val nagamit = remember(allDonasyon) { allDonasyon.filter { it.type == "gamit" }.sumOf { it.amount } }
    Column(Modifier.fillMaxWidth()) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(24.dp)) {
                Text("Natitirang Donasyon", color = Color.White.copy(alpha = .9f))
                Spacer(Modifier.height(8.dp))
                Text(formatCurrency(kabuuangDonasyon).replace(".00", ""), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(8.dp))
                Text("Nakalap: ${formatCurrency(nakalap).replace(".00", "")} • Nagamit: ${formatCurrency(nagamit).replace(".00", "")}", fontSize = 12.sp, color = Color.White.copy(alpha = .8f))
                Spacer(Modifier.height(4.dp))
                Text("Ngayon: ${formatCurrency(donasyonNgayon).replace(".00", "")}", fontSize = 12.sp, color = Color.White.copy(alpha = .8f))
            }
        }
        Spacer(Modifier.height(16.dp))
        /*
         * Ang pondo ng donasyon ay SUPERADMIN lang ang humahawak
         * (magdagdag ng donasyon o maglabas ng gamit).
         */
        if (isSuperadmin) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                OutlinedButton(onClick = { showGamitModal = true }) { Text("Gamit", maxLines = 1) }
                Button(onClick = { showAddModal = true }, colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)) { Text("Idagdag", maxLines = 1) }
            }
        }
        Spacer(Modifier.height(16.dp))
        val sourceBars = remember(allDonasyon) {
            allDonasyon
                .filter { it.type != "gamit" }
                .groupBy { it.source.ifBlank { "Iba" } }
                .map { (source, items) -> source to items.sumOf { it.amount } }
                .sortedByDescending { it.second }
                .take(7)
        }
        LabeledBarChart(
            title = "Donasyon kada Source",
            bars = sourceBars,
            barColor = HeartPink
        )
        Spacer(Modifier.height(16.dp)); SearchField(searchQuery, { searchQuery = it }, "Hanapin ayon sa donor o source"); Spacer(Modifier.height(20.dp)); ReportTabs(selectedTab) { selectedTab = it }
        if (selectedTab == 1) HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        // "Kasaysayan" is the complete list, so a donation that was made
        // today is in the history as well.
        val source = if (selectedTab == 0) currentDonasyon else allDonasyon
        val searched = source.filter { it.donor.contains(searchQuery, true) || it.source.contains(searchQuery, true) }
        val list = if (selectedTab == 1) filterHistoryRecords(searched, { it.date }, historyFilter) else searched
        if (list.isEmpty()) { if (selectedTab == 1) EmptyHistory() else Text("Walang donasyon.", color = TextSecondary) }
        else list.forEach { DonasyonCardItem(it); Spacer(Modifier.height(12.dp)) }
    }
    if (showAddModal) ModalBottomSheet(onDismissRequest = { showAddModal = false }, sheetState = sheetState, containerColor = Color.White) {
        AddDonasyonFormContent(isGamit = false, onCancel = { showAddModal = false }, onSubmit = { onAddDonasyon(it); showAddModal = false; selectedTab = 0 })
    }
    if (showGamitModal) ModalBottomSheet(onDismissRequest = { showGamitModal = false }, sheetState = sheetState, containerColor = Color.White) {
        AddDonasyonFormContent(isGamit = true, onCancel = { showGamitModal = false }, onSubmit = { onAddDonasyon(it); showGamitModal = false; selectedTab = 0 })
    }
}

@Composable
private fun ReportTabs(selected: Int, onSelected: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp)) {
        TabButton(Modifier.weight(1f), "Ngayon", selected == 0) { onSelected(0) }
        TabButton(Modifier.weight(1f), "Kasaysayan", selected == 1) { onSelected(1) }
    }
}

@Composable
private fun EmptyHistory() { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("Walang record sa napiling filter.", color = TextSecondary) } }

@Composable
fun MonthlyIncomeRow(data: MonthlyIncome) {
    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Text(data.period, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.height(2.dp)); Text("${data.trips} trips", fontSize = 12.sp, color = TextSecondary) }
        Column(horizontalAlignment = Alignment.End) { Text(formatCurrency(data.amount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 16.sp); data.growthPercentage?.let { Text("+$it%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen) } }
    }
}

@Composable
fun YearlyIncomeRow(data: YearlyIncome) {
    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Text(data.year, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.height(2.dp)); Text("${data.activeMonths} buwan aktibo", fontSize = 12.sp, color = TextSecondary) }
        Text(formatCurrency(data.amount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun IncomeHistoryCard(history: IncomeHistory) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(history.plateNumber, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(Modifier.height(2.dp)); Text(history.driverName, fontSize = 14.sp, color = TextSecondary); Spacer(Modifier.height(4.dp)); Text("Malinis na Kita: ${formatCurrency(history.netAmount).replace(".00", "")}", fontSize = 14.sp, color = TextSecondary) }
            Column(horizontalAlignment = Alignment.End) { Text(formatCurrency(history.grossAmount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(4.dp)); Text(history.date, fontSize = 12.sp, color = TextSecondary) }
        }
    }
}

@Composable
fun ReportIncidentCard(incident: ReportIncidentItem) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { Text(incident.title, fontWeight = FontWeight.Bold, fontSize = 15.sp); Spacer(Modifier.height(4.dp)); Text("Plaka: ${incident.plateNumber}", fontSize = 12.sp, color = TextSecondary); Text("Lokasyon: ${incident.location}", fontSize = 12.sp, color = TextSecondary); Spacer(Modifier.height(4.dp)); Text(incident.date, fontSize = 12.sp, color = TextSecondary) }
            Text(formatCurrency(incident.penalty).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun ReportMultiyaCard(multiya: ReportMultiyaItem) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { Text(multiya.plateNumber, fontWeight = FontWeight.Bold, fontSize = 15.sp); Spacer(Modifier.height(2.dp)); Text(multiya.driverName, fontSize = 13.sp, color = TextSecondary); Spacer(Modifier.height(4.dp)); Text(multiya.date, fontSize = 12.sp, color = TextSecondary) }
            Text(formatCurrency(multiya.amount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
fun DonasyonCardItem(donasyon: DonasyonItem) {
    val isGamit = donasyon.type == "gamit"
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { Text(if (isGamit) "Gamit • " + donasyon.donor else donasyon.donor, fontWeight = FontWeight.Bold, fontSize = 15.sp); Spacer(Modifier.height(2.dp)); Text(donasyon.source, fontSize = 13.sp, color = TextSecondary); Spacer(Modifier.height(4.dp)); Text(donasyon.date, fontSize = 12.sp, color = TextSecondary); if (donasyon.note.isNotBlank()) Text(donasyon.note, fontSize = 12.sp, color = TextSecondary) }
            Text(
                (if (isGamit) "−" else "+") + formatCurrency(donasyon.amount).replace(".00", ""),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = if (isGamit) ErrorRed else SuccessGreen
            )
        }
    }
}

@Composable
fun AddDonasyonFormContent(isGamit: Boolean, onCancel: () -> Unit, onSubmit: (DonasyonItem) -> Unit) {
    var donor by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var source by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    val today = SimpleDateFormat("MMM dd, yyyy", Locale.US).format(Date()); var date by remember { mutableStateOf(today) }
    Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text(if (isGamit) "Gamit ng Donasyon" else "Idagdag na Donasyon", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
        Text(
            if (isGamit)
                "Ibinabawas ito sa natitirang pondo ng donasyon. Hiwalay pa rin ito sa kita."
            else
                "Ang donasyon ay boluntaryo at hiwalay sa kita - hindi ito binabawas sa kahit kanino.",
            fontSize = 12.sp, color = TextSecondary
        ); Spacer(Modifier.height(12.dp))
        Text(if (isGamit) "Saan / Para kanino ginamit" else "Donor", fontWeight = FontWeight.Medium); OutlinedTextField(donor, { donor = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(if (isGamit) "Hal: Tulong sa miyembro" else "Pangalan ng donor") })
        Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Text("Halaga", fontWeight = FontWeight.Medium); OutlinedTextField(amount, { if (it.isEmpty() || it.all(Char::isDigit)) amount = it }, modifier = Modifier.fillMaxWidth(), leadingIcon = { PesoIcon(modifier = Modifier.padding(start = 12.dp), size = 18.dp, color = TextSecondary) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("0") }) }
            Column(Modifier.weight(1f)) { Text("Petsa", fontWeight = FontWeight.Medium); OutlinedTextField(date, { date = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("MMM dd, yyyy") }) }
        }
        Spacer(Modifier.height(12.dp)); Text("Source", fontWeight = FontWeight.Medium); OutlinedTextField(source, { source = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text(if (isGamit) "Hal: tulong, opisina" else "Hal: byahe, LGU, etc.") })
        Spacer(Modifier.height(12.dp)); Text("Note", fontWeight = FontWeight.Medium); OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Karagdagang detalye") })
        Spacer(Modifier.height(24.dp)); Row(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Kanselahin") }; Spacer(Modifier.width(12.dp))
            Button(onClick = { val parsed = amount.toDoubleOrNull() ?: 0.0; if (donor.isNotBlank() && parsed > 0.0) onSubmit(DonasyonItem(donor, parsed, date, source, note, if (isGamit) "gamit" else "donate")) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)) { Text("Ipasa") }
        }
    }
}

@Composable
fun TabButton(modifier: Modifier = Modifier, text: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(modifier = modifier.fillMaxHeight().padding(horizontal = 4.dp), onClick = onClick, colors = CardDefaults.cardColors(containerColor = if (isSelected) Color.White else Color.Transparent), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(if (isSelected) 2.dp else 0.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (isSelected) Color.Black else TextSecondary) }
    }
}

@Preview(showBackground = true)
@Composable
fun ReportScreenPreview() { ReportScreen() }

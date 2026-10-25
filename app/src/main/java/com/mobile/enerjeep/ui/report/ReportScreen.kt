package com.mobile.enerjeep.ui.report

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.mobile.enerjeep.ui.components.filterHistoryRecords
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val LightGrayBackground = Color(0xFFF8F9FA)
val EnerjeepBlue = Color(0xFF194287)
val EnerjeepBlueLight = Color(0xFFE0E7FF)
val WarningOrange = Color(0xFFB45309)
val WarningOrangeLight = Color(0xFFFEF3C7)
val ErrorRed = Color(0xFFB91C1C)
val ErrorRedLight = Color(0xFFFEE2E2)
val HeartPink = Color(0xFFBE185D)
val HeartPinkLight = Color(0xFFFCE7F3)
val SuccessGreenText = Color(0xFF16A34A)
val FieldBorderColor = Color(0xFFE5E7EB)

fun formatCurrency(amount: Double): String = "₱${NumberFormat.getNumberInstance(Locale.US).format(amount)}"

@Composable
fun ReportScreen(viewModel: ReportViewModel = hiltViewModel()) {
    var currentScreen by remember { mutableStateOf("MainGrid") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    when (val state = uiState) {
        is ReportUiState.Loading -> Box(Modifier.fillMaxSize().background(LightGrayBackground), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EnerjeepBlue) }
        is ReportUiState.Error -> Box(Modifier.fillMaxSize().background(LightGrayBackground), contentAlignment = Alignment.Center) { Text(state.message, color = ErrorRed) }
        is ReportUiState.Success -> when (currentScreen) {
            "MainGrid" -> ReportsGridScreen(
                onUlatNgKitaClick = { currentScreen = "UlatNgKita" },
                onUlatNgInsidenteClick = { currentScreen = "UlatNgInsidente" },
                onUlatNgMultiyaClick = { currentScreen = "UlatNgMultiya" },
                onDonasyonClick = { currentScreen = "DonasyonNgKoop" }
            )
            "UlatNgKita" -> UlatNgKitaScreen(state.monthlyData, state.yearlyData, state.historyData) { currentScreen = "MainGrid" }
            "UlatNgInsidente" -> UlatNgInsidenteScreen(state.currentIncidents, state.allIncidents, state.totalIncidentPenalty) { currentScreen = "MainGrid" }
            "UlatNgMultiya" -> UlatNgMultiyaScreen(state.currentMultiya, state.allMultiya, state.multiyaNgayon, state.kabuuangMultiya) { currentScreen = "MainGrid" }
            "DonasyonNgKoop" -> DonasyonNgKoopScreen(state.currentDonasyon, state.allDonasyon, state.donasyonNgayon, state.kabuuangDonasyon, { viewModel.addDonasyon(it) }) { currentScreen = "MainGrid" }
        }
    }
}

@Composable
fun ReportsGridScreen(onUlatNgKitaClick: () -> Unit, onUlatNgInsidenteClick: () -> Unit, onUlatNgMultiyaClick: () -> Unit, onDonasyonClick: () -> Unit) {
    Scaffold(containerColor = LightGrayBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            HeaderRow()
            Spacer(Modifier.height(40.dp)); Text("Mga Ulat", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            Spacer(Modifier.height(4.dp)); Text("Pumili ng ulat na titingnan.", fontSize = 16.sp, color = Color.Gray); Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ReportCard(Modifier.weight(1f), "Ulat ng Kita", "Buwanang at taunang kita", Icons.Default.AccountBalanceWallet, EnerjeepBlue, EnerjeepBlueLight, onUlatNgKitaClick)
                ReportCard(Modifier.weight(1f), "Ulat ng Insidente", "Mga naitalang insidente", Icons.Default.Warning, WarningOrange, WarningOrangeLight, onUlatNgInsidenteClick)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ReportCard(Modifier.weight(1f), "Ulat ng Multa", "Mga naitalang multa", Icons.Default.PrivacyTip, ErrorRed, ErrorRedLight, onUlatNgMultiyaClick)
                ReportCard(Modifier.weight(1f), "Donasyon ng Koop", "Mga donasyon ng kooperatiba", Icons.Default.FavoriteBorder, HeartPink, HeartPinkLight, onDonasyonClick)
            }
        }
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
fun UlatNgKitaScreen(monthlyData: List<MonthlyIncome>, yearlyData: List<YearlyIncome>, historyData: List<IncomeHistory>, onBack: () -> Unit) {
    /*
     * Fase 5: ang dating Buwanang/Taunang LISTA ay naging LABELED
     * CHARTS (view-only), at ang tab/search ay pinalitan ng isang
     * "Kasaysayan" na may PLATE filter at date filter.
     */
    var plateFilter by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }
    val plateOptions = remember(historyData) {
        historyData.map { it.plateNumber }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    ReportPage("Ulat ng Kita", onBack) {
        LabeledBarChart(
            title = "Buwanang Kita",
            bars = monthlyData.map { it.period to it.amount }
        )
        Spacer(Modifier.height(24.dp))
        LabeledBarChart(
            title = "Taunang Kita",
            bars = yearlyData.map { it.year to it.amount },
            barColor = Color(0xFF16A34A)
        )
        Spacer(Modifier.height(28.dp))
        Text("Kasaysayan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(12.dp))
        if (plateOptions.size > 1) {
            var expandedPlate by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedPlate,
                onExpandedChange = { expandedPlate = !expandedPlate }
            ) {
                OutlinedTextField(
                    value = plateFilter.ifBlank { "Lahat ng plaka" },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = expandedPlate
                        )
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = expandedPlate,
                    onDismissRequest = { expandedPlate = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Lahat ng plaka") },
                        onClick = {
                            plateFilter = ""
                            expandedPlate = false
                        }
                    )
                    plateOptions.forEach { plate ->
                        DropdownMenuItem(
                            text = { Text(plate) },
                            onClick = {
                                plateFilter = plate
                                expandedPlate = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth())
        val filteredPlates = historyData.filter {
            plateFilter.isBlank() || it.plateNumber == plateFilter
        }
        val history = filterHistoryRecords(filteredPlates, { it.date }, historyFilter)
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) EmptyHistory() else history.forEach { IncomeHistoryCard(it); Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun UlatNgInsidenteScreen(currentIncidents: List<ReportIncidentItem>, allIncidents: List<ReportIncidentItem>, totalPenalty: Double, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }
    ReportPage("Ulat ng Insidente", onBack) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(24.dp)) { Text("Kabuuang Multa mula sa Insidente", color = Color.White); Spacer(Modifier.height(8.dp)); Text(formatCurrency(totalPenalty).replace(".00", ""), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White) }
        }
        Spacer(Modifier.height(24.dp)); SearchField(searchQuery, { searchQuery = it }, "Hanapin ayon sa uri, plaka, o lokasyon")
        Spacer(Modifier.height(20.dp)); ReportTabs(selectedTab) { selectedTab = it }
        if (selectedTab == 1) HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        // "Kasaysayan" is the complete list, so an incident that was made
        // today is in the history as well.
        val source = if (selectedTab == 0) currentIncidents else allIncidents
        val searched = source.filter { it.plateNumber.contains(searchQuery, true) || it.title.contains(searchQuery, true) || it.location.contains(searchQuery, true) }
        val list = if (selectedTab == 1) filterHistoryRecords(searched, { it.date }, historyFilter) else searched
        if (list.isEmpty()) { if (selectedTab == 1) EmptyHistory() else Text("Walang naitalang insidente.", color = Color.Gray) }
        else list.forEach { ReportIncidentCard(it); Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
fun UlatNgMultiyaScreen(currentMultiya: List<ReportMultiyaItem>, allMultiya: List<ReportMultiyaItem>, multiyaNgayon: Double, kabuuangMultiya: Double, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }
    ReportPage("Ulat ng Multa", onBack) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(24.dp)) { Text("Multa Ngayon", color = Color.White.copy(alpha = .9f)); Spacer(Modifier.height(8.dp)); Text(formatCurrency(multiyaNgayon).replace(".00", ""), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White); Spacer(Modifier.height(8.dp)); Text("Kabuuang: ${formatCurrency(kabuuangMultiya).replace(".00", "")}", fontSize = 12.sp, color = Color.White.copy(alpha = .8f)) }
        }
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
        if (list.isEmpty()) { if (selectedTab == 1) EmptyHistory() else Text("Walang multa ngayon.", color = Color.Gray) }
        else list.forEach { ReportMultiyaCard(it); Spacer(Modifier.height(12.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonasyonNgKoopScreen(currentDonasyon: List<DonasyonItem>, allDonasyon: List<DonasyonItem>, donasyonNgayon: Double, kabuuangDonasyon: Double, onAddDonasyon: (DonasyonItem) -> Unit, onBack: () -> Unit) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddModal by remember { mutableStateOf(false) }
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ReportPage("Donasyon ng Koop", onBack) {
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.padding(24.dp)) { Text("Kabuuang Donasyon", color = Color.White.copy(alpha = .9f)); Spacer(Modifier.height(8.dp)); Text(formatCurrency(kabuuangDonasyon).replace(".00", ""), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White); Spacer(Modifier.height(8.dp)); Text("Ngayon: ${formatCurrency(donasyonNgayon).replace(".00", "")}", fontSize = 12.sp, color = Color.White.copy(alpha = .8f)) }
        }
        Spacer(Modifier.height(16.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { Button(onClick = { showAddModal = true }, colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)) { Text("+ Idagdag") } }
        Spacer(Modifier.height(16.dp)); SearchField(searchQuery, { searchQuery = it }, "Hanapin ayon sa donor o source"); Spacer(Modifier.height(20.dp)); ReportTabs(selectedTab) { selectedTab = it }
        if (selectedTab == 1) HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
        Spacer(Modifier.height(16.dp))
        // "Kasaysayan" is the complete list, so a donation that was made
        // today is in the history as well.
        val source = if (selectedTab == 0) currentDonasyon else allDonasyon
        val searched = source.filter { it.donor.contains(searchQuery, true) || it.source.contains(searchQuery, true) }
        val list = if (selectedTab == 1) filterHistoryRecords(searched, { it.date }, historyFilter) else searched
        if (list.isEmpty()) { if (selectedTab == 1) EmptyHistory() else Text("Walang donasyon.", color = Color.Gray) }
        else list.forEach { DonasyonCardItem(it); Spacer(Modifier.height(12.dp)) }
    }
    if (showAddModal) ModalBottomSheet(onDismissRequest = { showAddModal = false }, sheetState = sheetState, containerColor = Color.White) {
        AddDonasyonFormContent(onCancel = { showAddModal = false }, onSubmit = { onAddDonasyon(it); showAddModal = false; selectedTab = 0 })
    }
}

@Composable
private fun ReportPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(containerColor = LightGrayBackground) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp).background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black) }
                Spacer(Modifier.width(16.dp)); Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
            Spacer(Modifier.height(28.dp)); content.invoke(this)
        }
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
private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(value, onValueChange, modifier = Modifier.fillMaxWidth(), placeholder = { Text(placeholder, color = Color.LightGray) }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) }, shape = RoundedCornerShape(12.dp), singleLine = true, colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White))
}

@Composable
private fun EmptyHistory() { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("Walang record sa napiling filter.", color = Color.Gray) } }

@Composable
fun ReportCard(modifier: Modifier = Modifier, title: String, description: String, icon: ImageVector, iconColor: Color, iconBgColor: Color, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(170.dp), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.Start) {
            Surface(Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = iconBgColor) { Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = iconColor, modifier = Modifier.size(20.dp)) } }
            Spacer(Modifier.height(16.dp)); Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black); Spacer(Modifier.height(4.dp)); Text(description, fontSize = 13.sp, color = Color.Gray)
        }
    }
}

@Composable
fun MonthlyIncomeRow(data: MonthlyIncome) {
    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Text(data.period, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.height(2.dp)); Text("${data.trips} trips", fontSize = 12.sp, color = Color.Gray) }
        Column(horizontalAlignment = Alignment.End) { Text(formatCurrency(data.amount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 16.sp); data.growthPercentage?.let { Text("+$it%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreenText) } }
    }
}

@Composable
fun YearlyIncomeRow(data: YearlyIncome) {
    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column { Text(data.year, fontWeight = FontWeight.Bold, fontSize = 14.sp); Spacer(Modifier.height(2.dp)); Text("${data.activeMonths} buwan aktibo", fontSize = 12.sp, color = Color.Gray) }
        Text(formatCurrency(data.amount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun IncomeHistoryCard(history: IncomeHistory) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(history.plateNumber, fontWeight = FontWeight.Bold, fontSize = 16.sp); Spacer(Modifier.height(2.dp)); Text(history.driverName, fontSize = 14.sp, color = Color.Gray); Spacer(Modifier.height(4.dp)); Text("Neto: ${formatCurrency(history.netAmount).replace(".00", "")}", fontSize = 14.sp, color = Color.Gray) }
            Column(horizontalAlignment = Alignment.End) { Text(formatCurrency(history.grossAmount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 18.sp); Spacer(Modifier.height(4.dp)); Text(history.date, fontSize = 12.sp, color = Color.Gray) }
        }
    }
}

@Composable
fun ReportIncidentCard(incident: ReportIncidentItem) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { Text(incident.title, fontWeight = FontWeight.Bold, fontSize = 15.sp); Spacer(Modifier.height(4.dp)); Text("Plaka: ${incident.plateNumber}", fontSize = 12.sp, color = Color.Gray); Text("Lokasyon: ${incident.location}", fontSize = 12.sp, color = Color.Gray); Spacer(Modifier.height(4.dp)); Text(incident.date, fontSize = 12.sp, color = Color.Gray) }
            Text(formatCurrency(incident.penalty).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
fun ReportMultiyaCard(multiya: ReportMultiyaItem) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { Text(multiya.plateNumber, fontWeight = FontWeight.Bold, fontSize = 15.sp); Spacer(Modifier.height(2.dp)); Text(multiya.driverName, fontSize = 13.sp, color = Color.Gray); Spacer(Modifier.height(4.dp)); Text(multiya.date, fontSize = 12.sp, color = Color.Gray) }
            Text(formatCurrency(multiya.amount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
fun DonasyonCardItem(donasyon: DonasyonItem) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) { Text(donasyon.donor, fontWeight = FontWeight.Bold, fontSize = 15.sp); Spacer(Modifier.height(2.dp)); Text(donasyon.source, fontSize = 13.sp, color = Color.Gray); Spacer(Modifier.height(4.dp)); Text(donasyon.date, fontSize = 12.sp, color = Color.Gray); if (donasyon.note.isNotBlank()) Text(donasyon.note, fontSize = 12.sp, color = Color.Gray) }
            Text(formatCurrency(donasyon.amount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
fun AddDonasyonFormContent(onCancel: () -> Unit, onSubmit: (DonasyonItem) -> Unit) {
    var donor by remember { mutableStateOf("") }; var amount by remember { mutableStateOf("") }; var source by remember { mutableStateOf("") }; var note by remember { mutableStateOf("") }
    val today = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date()); var date by remember { mutableStateOf(today) }
    Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Text("Idagdag na Donasyon", fontSize = 20.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
        Text("Ang donasyon ay itinatala bilang BAWAS sa netong kita.", fontSize = 12.sp, color = Color.Gray); Spacer(Modifier.height(12.dp))
        Text("Donor", fontWeight = FontWeight.Medium); OutlinedTextField(donor, { donor = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Pangalan ng donor") })
        Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { Text("Halaga (Bawas sa kita)", fontWeight = FontWeight.Medium); OutlinedTextField(amount, { if (it.isEmpty() || it.all(Char::isDigit)) amount = it }, modifier = Modifier.fillMaxWidth(), leadingIcon = { PesoIcon(modifier = Modifier.padding(start = 12.dp), size = 18.dp, color = Color.Gray) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("0") }) }
            Column(Modifier.weight(1f)) { Text("Petsa", fontWeight = FontWeight.Medium); OutlinedTextField(date, { date = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("MMM dd, yyyy") }) }
        }
        Spacer(Modifier.height(12.dp)); Text("Source", fontWeight = FontWeight.Medium); OutlinedTextField(source, { source = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Hal: byahe, LGU, etc.") })
        Spacer(Modifier.height(12.dp)); Text("Note", fontWeight = FontWeight.Medium); OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Karagdagang detalye") })
        Spacer(Modifier.height(24.dp)); Row(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Kanselahin") }; Spacer(Modifier.width(12.dp))
            Button(onClick = { val parsed = amount.toDoubleOrNull() ?: 0.0; if (donor.isNotBlank() && parsed > 0.0) onSubmit(DonasyonItem(donor, parsed, date, source, note)) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue)) { Text("Ipasa") }
        }
    }
}

@Composable
fun TabButton(modifier: Modifier = Modifier, text: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(modifier = modifier.fillMaxHeight().padding(horizontal = 4.dp), onClick = onClick, colors = CardDefaults.cardColors(containerColor = if (isSelected) Color.White else Color.Transparent), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(if (isSelected) 2.dp else 0.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (isSelected) Color.Black else Color.Gray) }
    }
}

@Preview(showBackground = true)
@Composable
fun ReportScreenPreview() { ReportScreen() }

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
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.mobile.enerjeep.R

// --- 1. LOCAL THEME COLORS ---
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

// Helper for formatting currency
fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getNumberInstance(Locale.US)
    return "₱${format.format(amount)}"
}

// --- 2. MAIN SCREEN CONTROLLER ---
@Composable
fun ReportScreen(
    viewModel: ReportViewModel = hiltViewModel()
) {
    var currentScreen by remember { mutableStateOf("MainGrid") }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    when (val state = uiState) {
        is ReportUiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize().background(LightGrayBackground), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = EnerjeepBlue)
            }
        }
        is ReportUiState.Error -> {
            Box(modifier = Modifier.fillMaxSize().background(LightGrayBackground), contentAlignment = Alignment.Center) {
                Text(text = state.message, color = ErrorRed)
            }
        }
        is ReportUiState.Success -> {
            // Nested Navigation based on local state
            when (currentScreen) {
                "MainGrid" -> {
                    ReportsGridScreen(
                        onUlatNgKitaClick = { currentScreen = "UlatNgKita" },
                        onUlatNgInsidenteClick = { currentScreen = "UlatNgInsidente" },
                        onUlatNgMultiyaClick = { currentScreen = "UlatNgMultiya" },
                        onDonasyonClick = { currentScreen = "DonasyonNgKoop" }
                    )
                }
                "UlatNgKita" -> {
                    UlatNgKitaScreen(
                        monthlyData = state.monthlyData,
                        yearlyData = state.yearlyData,
                        historyData = state.historyData,
                        onBack = { currentScreen = "MainGrid" }
                    )
                }
                "UlatNgInsidente" -> {
                    UlatNgInsidenteScreen(
                        currentIncidents = state.currentIncidents,
                        historyIncidents = state.historyIncidents,
                        totalPenalty = state.totalIncidentPenalty,
                        onBack = { currentScreen = "MainGrid" }
                    )
                }
                "UlatNgMultiya" -> {
                    UlatNgMultiyaScreen(
                        currentMultiya = state.currentMultiya,
                        historyMultiya = state.historyMultiya,
                        multiyaNgayon = state.multiyaNgayon,
                        kabuuangMultiya = state.kabuuangMultiya,
                        onBack = { currentScreen = "MainGrid" }
                    )
                }
                "DonasyonNgKoop" -> {
                    DonasyonNgKoopScreen(
                        currentDonasyon = state.currentDonasyon,
                        historyDonasyon = state.historyDonasyon,
                        donasyonNgayon = state.donasyonNgayon,
                        kabuuangDonasyon = state.kabuuangDonasyon,
                        onAddDonasyon = { newDonasyon ->
                            viewModel.addDonasyon(newDonasyon)
                        },
                        onBack = { currentScreen = "MainGrid" }
                    )
                }
            }
        }
    }
}

// --- 3. MAIN REPORTS GRID SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsGridScreen(
    onUlatNgKitaClick: () -> Unit,
    onUlatNgInsidenteClick: () -> Unit,
    onUlatNgMultiyaClick: () -> Unit,
    onDonasyonClick: () -> Unit
) {
    Scaffold(containerColor = LightGrayBackground) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Image(painter = painterResource(id = R.drawable.app_icon), contentDescription = "Enerjeep Logo", modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Enerjeep", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(40.dp))

            Text(text = "Mga Ulat", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Color.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = "Pumili ng ulat na titingnan.", fontSize = 16.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(32.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ReportCard(modifier = Modifier.weight(1f), title = "Ulat ng Kita", description = "Buwanang at taunang kita", icon = Icons.Default.AccountBalanceWallet, iconColor = EnerjeepBlue, iconBgColor = EnerjeepBlueLight, onClick = onUlatNgKitaClick)
                ReportCard(modifier = Modifier.weight(1f), title = "Ulat ng Insidente", description = "Mga naitalang insidente", icon = Icons.Default.Warning, iconColor = WarningOrange, iconBgColor = WarningOrangeLight, onClick = onUlatNgInsidenteClick)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ReportCard(modifier = Modifier.weight(1f), title = "Ulat ng Multiya", description = "Mga naitalang multiya", icon = Icons.Default.PrivacyTip, iconColor = ErrorRed, iconBgColor = ErrorRedLight, onClick = onUlatNgMultiyaClick)
                ReportCard(modifier = Modifier.weight(1f), title = "Donasyon ng Koop", description = "Mga donasyon ng kooperatiba", icon = Icons.Default.FavoriteBorder, iconColor = HeartPink, iconBgColor = HeartPinkLight, onClick = onDonasyonClick)
            }
        }
    }
}

// --- 4. ULAT NG KITA SCREEN ---
@Composable
fun UlatNgKitaScreen(
    monthlyData: List<MonthlyIncome>,
    yearlyData: List<YearlyIncome>,
    historyData: List<IncomeHistory>,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(containerColor = LightGrayBackground) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp).background(Color(0xFFFFF7ED), RoundedCornerShape(12.dp))) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = WarningOrange)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "Ulat ng Kita", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                TabButton(modifier = Modifier.weight(1f), text = "Kasalukuyan", isSelected = selectedTab == 0, onClick = { selectedTab = 0 })
                TabButton(modifier = Modifier.weight(1f), text = "Kasaysayan", isSelected = selectedTab == 1, onClick = { selectedTab = 1 })
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (selectedTab == 0) {
                Text(text = "Buwanang Kita", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.height(12.dp))

                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (monthlyData.isEmpty()) {
                            Text(text = "Walang datos.", modifier = Modifier.padding(24.dp), color = Color.Gray, fontSize = 14.sp)
                        } else {
                            monthlyData.forEachIndexed { index, data ->
                                MonthlyIncomeRow(data)
                                if (index < monthlyData.size - 1) HorizontalDivider(color = FieldBorderColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 24.dp))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Taunang Kita", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                Spacer(modifier = Modifier.height(12.dp))

                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (yearlyData.isEmpty()) {
                            Text(text = "Walang datos.", modifier = Modifier.padding(24.dp), color = Color.Gray, fontSize = 14.sp)
                        } else {
                            yearlyData.forEachIndexed { index, data ->
                                YearlyIncomeRow(data)
                                if (index < yearlyData.size - 1) HorizontalDivider(color = FieldBorderColor, thickness = 1.dp, modifier = Modifier.padding(horizontal = 24.dp))
                            }
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Hanapin ayon sa plaka", color = Color.LightGray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    shape = RoundedCornerShape(12.dp), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
                )

                Spacer(modifier = Modifier.height(16.dp))

                val filteredHistory = historyData.filter { it.plateNumber.contains(searchQuery, true) }

                if (filteredHistory.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(text = "Walang kasaysayan.", color = Color.Gray)
                    }
                } else {
                    filteredHistory.forEach { history ->
                        IncomeHistoryCard(history)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

// --- 5. ULAT NG INSIDENTE SCREEN ---
@Composable
fun UlatNgInsidenteScreen(
    currentIncidents: List<ReportIncidentItem>,
    historyIncidents: List<ReportIncidentItem>,
    totalPenalty: Double,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(containerColor = LightGrayBackground) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp).background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "Ulat ng Insidente", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(text = "Kabuuang Multiya mula sa Insidente", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = formatCurrency(totalPenalty).replace(".00", ""), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Hanapin ayon sa uri, plaka, o lokasyon", color = Color.LightGray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                shape = RoundedCornerShape(12.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                TabButton(modifier = Modifier.weight(1f), text = "Kasalukuyan", isSelected = selectedTab == 0, onClick = { selectedTab = 0 })
                TabButton(modifier = Modifier.weight(1f), text = "Kasaysayan", isSelected = selectedTab == 1, onClick = { selectedTab = 1 })
            }

            Spacer(modifier = Modifier.height(24.dp))

            val listToDisplay = if (selectedTab == 0) currentIncidents else historyIncidents
            val filteredList = listToDisplay.filter {
                it.plateNumber.contains(searchQuery, true) || it.title.contains(searchQuery, true) || it.location.contains(searchQuery, true)
            }

            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Walang naitalang insidente.", color = Color.Gray)
                }
            } else {
                filteredList.forEach { incident ->
                    ReportIncidentCard(incident)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

// --- 6. ULAT NG MULTIYA SCREEN ---
@Composable
fun UlatNgMultiyaScreen(
    currentMultiya: List<ReportMultiyaItem>,
    historyMultiya: List<ReportMultiyaItem>,
    multiyaNgayon: Double,
    kabuuangMultiya: Double,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(containerColor = LightGrayBackground) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(24.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(40.dp).background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = "Ulat ng Multiya", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(text = "Multiya Ngayon", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = formatCurrency(multiyaNgayon).replace(".00", ""), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Kabuuang: ${formatCurrency(kabuuangMultiya).replace(".00", "")}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Hanapin ayon sa plaka", color = Color.LightGray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                shape = RoundedCornerShape(12.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                TabButton(modifier = Modifier.weight(1f), text = "Kasalukuyan", isSelected = selectedTab == 0, onClick = { selectedTab = 0 })
                TabButton(modifier = Modifier.weight(1f), text = "Kasaysayan", isSelected = selectedTab == 1, onClick = { selectedTab = 1 })
            }

            Spacer(modifier = Modifier.height(24.dp))

            val listToDisplay = if (selectedTab == 0) currentMultiya else historyMultiya
            val filteredList = listToDisplay.filter { it.plateNumber.contains(searchQuery, true) }

            if (filteredList.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(text = "Walang multiya ngayon.", color = Color.Gray)
                }
            } else {
                filteredList.forEach { multiya ->
                    ReportMultiyaCard(multiya)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

// --- 7. DONASYON NG KOOP SCREEN ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DonasyonNgKoopScreen(
    currentDonasyon: List<DonasyonItem>,
    historyDonasyon: List<DonasyonItem>,
    donasyonNgayon: Double,
    kabuuangDonasyon: Double,
    onAddDonasyon: (DonasyonItem) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showAddModal by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(containerColor = LightGrayBackground) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(24.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp).background(Color(0xFFF1F5F9), RoundedCornerShape(12.dp))) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "Donasyon ng Koop", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }

                Spacer(modifier = Modifier.height(32.dp))

                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(text = "Kabuuang Donasyon", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = formatCurrency(kabuuangDonasyon).replace(".00", ""), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Ngayon: ${formatCurrency(donasyonNgayon).replace(".00", "")}", fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = { showAddModal = true }, colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(22.dp), modifier = Modifier.height(36.dp)) {
                        Text("+ Idagdag", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Hanapin ayon sa donor o source", color = Color.LightGray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                    shape = RoundedCornerShape(12.dp), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    TabButton(modifier = Modifier.weight(1f), text = "Kasalukuyan", isSelected = selectedTab == 0, onClick = { selectedTab = 0 })
                    TabButton(modifier = Modifier.weight(1f), text = "Kasaysayan", isSelected = selectedTab == 1, onClick = { selectedTab = 1 })
                }

                Spacer(modifier = Modifier.height(24.dp))

                val listToDisplay = if (selectedTab == 0) currentDonasyon else historyDonasyon
                val filteredList = listToDisplay.filter {
                    it.donor.contains(searchQuery, true) || it.source.contains(searchQuery, true)
                }

                if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(text = "Walang donasyon.", color = Color.Gray)
                    }
                } else {
                    filteredList.forEach { donasyon ->
                        DonasyonCardItem(donasyon)
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        if (showAddModal) {
            ModalBottomSheet(
                onDismissRequest = { showAddModal = false },
                sheetState = sheetState,
                containerColor = Color.White,
                dragHandle = { BottomSheetDefaults.DragHandle() }
            ) {
                AddDonasyonFormContent(
                    onCancel = { showAddModal = false },
                    onSubmit = { newDonasyon ->
                        onAddDonasyon(newDonasyon)
                        showAddModal = false
                        selectedTab = 0
                    }
                )
            }
        }
    }
}

// --- 8. REUSABLE COMPONENTS & ROWS ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportCard(modifier: Modifier = Modifier, title: String, description: String, icon: ImageVector, iconColor: Color, iconBgColor: Color, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(170.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Top, horizontalAlignment = Alignment.Start) {
            Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = iconBgColor) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = description, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
        }
    }
}

@Composable
fun MonthlyIncomeRow(data: MonthlyIncome) {
    Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(text = data.period, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "${data.trips} trips", fontSize = 12.sp, color = Color.Gray)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = formatCurrency(data.amount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            if (data.growthPercentage != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = SuccessGreenText, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "+${data.growthPercentage}%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreenText)
                }
            }
        }
    }
}

@Composable
fun YearlyIncomeRow(data: YearlyIncome) {
    Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text(text = data.year, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = "${data.activeMonths} buwan aktibo", fontSize = 12.sp, color = Color.Gray)
        }
        Text(text = formatCurrency(data.amount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
    }
}

@Composable
fun IncomeHistoryCard(history: IncomeHistory) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(text = history.plateNumber, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = history.driverName, fontSize = 14.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "Neto: ${formatCurrency(history.netAmount).replace(".00", "")}", fontSize = 14.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = formatCurrency(history.grossAmount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = history.date, fontSize = 12.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
fun ReportIncidentCard(incident: ReportIncidentItem) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(10.dp), color = WarningOrangeLight) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(20.dp)) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = incident.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Numbers, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = incident.plateNumber, fontSize = 12.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = incident.location, fontSize = 12.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = incident.date, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            Text(text = formatCurrency(incident.penalty).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Black)
        }
    }
}

@Composable
fun ReportMultiyaCard(multiya: ReportMultiyaItem) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(10.dp), color = WarningOrangeLight) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(20.dp)) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = multiya.plateNumber, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = multiya.driverName, fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = multiya.date, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            Text(text = formatCurrency(multiya.amount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
        }
    }
}

@Composable
fun DonasyonCardItem(donasyon: DonasyonItem) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(modifier = Modifier.size(36.dp), shape = RoundedCornerShape(10.dp), color = HeartPinkLight) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = HeartPink, modifier = Modifier.size(20.dp)) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = donasyon.donor, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = donasyon.source, fontSize = 13.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AccessTime, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = donasyon.date, fontSize = 12.sp, color = Color.Gray)
                    }
                    if (donasyon.note.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = donasyon.note, fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
            Text(text = formatCurrency(donasyon.amount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
        }
    }
}

@Composable
fun AddDonasyonFormContent(onCancel: () -> Unit, onSubmit: (DonasyonItem) -> Unit) {
    var donor by remember { mutableStateOf("") }
    var halaga by remember { mutableStateOf("") }
    var source by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val currentDate = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date())
    var petsa by remember { mutableStateOf(currentDate) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp, top = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Idagdag na Donasyon", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "Donor", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = donor, onValueChange = { donor = it }, placeholder = { Text("Pangalan ng donor", color = Color.LightGray) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue, unfocusedContainerColor = Color.White))
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Halaga", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = halaga, onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) halaga = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), placeholder = { Text("0", color = Color.LightGray) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue, unfocusedContainerColor = Color.White))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Petsa", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = petsa, onValueChange = { petsa = it }, trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(18.dp)) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue, unfocusedContainerColor = Color.White))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Source", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = source, onValueChange = { source = it }, placeholder = { Text("Hal: byahe, LGU, etc.", color = Color.LightGray) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue, unfocusedContainerColor = Color.White))
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Note", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(value = note, onValueChange = { note = it }, placeholder = { Text("Karagdagang detalye", color = Color.LightGray) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue, unfocusedContainerColor = Color.White))
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(28.dp), border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor)) { Text("Kanselahin", color = Color.Black) }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = {
                    val amountParsed = halaga.toDoubleOrNull() ?: 0.0
                    if (donor.isNotBlank() && amountParsed > 0) {
                        onSubmit(DonasyonItem(donor = donor, amount = amountParsed, date = petsa, source = source, note = note))
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(28.dp)
            ) { Text("Ipasa") }
        }
    }
}

@Composable
fun TabButton(modifier: Modifier = Modifier, text: String, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) Color.White else Color.Transparent
    val textColor = if (isSelected) Color.Black else Color.Gray
    val elevation = if (isSelected) 2.dp else 0.dp
    Card(modifier = modifier.fillMaxHeight().padding(horizontal = 4.dp), onClick = onClick, colors = CardDefaults.cardColors(containerColor = backgroundColor), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = elevation)) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textColor) }
    }
}

// --- PREVIEWS ---

@Preview(showBackground = true)
@Composable
fun ReportScreenPreview() {
    ReportScreen()
}
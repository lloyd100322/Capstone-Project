package com.mobile.enerjeep.ui.ledger

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.R
import com.mobile.enerjeep.ui.components.HistoryDateFilterBar
import com.mobile.enerjeep.ui.components.HistoryFilterState
import com.mobile.enerjeep.ui.components.PesoIcon
import com.mobile.enerjeep.ui.components.filterHistoryRecords
import java.text.NumberFormat
import java.util.Locale

val LightGrayBackground = Color(0xFFF8F9FA)
val EnerjeepBlue = Color(0xFF194287)
val SuccessGreenText = Color(0xFF16A34A)
val FieldBorderColor = Color(0xFFE5E7EB)

fun formatCurrency(amount: Double): String = "₱${NumberFormat.getNumberInstance(Locale.US).format(amount)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: LedgerViewModel = hiltViewModel(),
    shouldOpenForm: Boolean = false,
    onFormHandled: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val driverOptions by viewModel.driverOptions.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    var showAddModal by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(shouldOpenForm) {
        if (shouldOpenForm) { showAddModal = true; onFormHandled() }
    }

    Scaffold(containerColor = LightGrayBackground) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.app_icon), contentDescription = "Enerjeep Logo", modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Text("Enerjeep", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Talaan", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Button(onClick = { showAddModal = true }, colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(22.dp)) { Text("+ Idagdag") }
            }
            Spacer(Modifier.height(24.dp))
            when (val state = uiState) {
                LedgerUiState.Loading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = EnerjeepBlue) }
                is LedgerUiState.Error -> Text(state.message, color = Color.Red, modifier = Modifier.padding(16.dp))
                is LedgerUiState.Success -> {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(24.dp)) {
                            Text("Koleksyon Ngayon", color = Color.White)
                            Spacer(Modifier.height(8.dp))
                            Text(formatCurrency(state.totalToday).replace(".00", ""), color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth().height(48.dp)) {
                        LedgerTabButton(Modifier.weight(1f), "Ngayon", selectedTab == 0) { selectedTab = 0 }
                        LedgerTabButton(Modifier.weight(1f), "Kasaysayan", selectedTab == 1) { selectedTab = 1 }
                    }
                    Spacer(Modifier.height(24.dp))
                    if (selectedTab == 0) {
                        if (state.todayCollections.isEmpty()) Text("Wala pang koleksyon ngayon.", color = Color.Gray, modifier = Modifier.fillMaxWidth().padding(48.dp))
                        else state.todayCollections.forEach { LedgerItemCard(it); Spacer(Modifier.height(12.dp)) }
                    } else {
                        OutlinedTextField(
                            value = searchQuery, onValueChange = { searchQuery = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Hanapin ayon sa plaka o driver") }, leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") }, singleLine = true
                        )
                        HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
                        /*
                         * "Kasaysayan" is the complete list, so a collection
                         * that was made today is in the history as well.
                         */
                        val searched = state.allCollections.filter {
                            it.plateNumber.contains(searchQuery, true) || it.driverName.contains(searchQuery, true)
                        }
                        val filtered = filterHistoryRecords(searched, { it.date }, historyFilter, { it.createdAtMillis.takeIf { millis -> millis > 0L } })
                        Spacer(Modifier.height(12.dp))
                        if (filtered.isEmpty()) Text("Walang record sa napiling filter.", color = Color.Gray, modifier = Modifier.fillMaxWidth().padding(48.dp))
                        else filtered.forEach { LedgerItemCard(it); Spacer(Modifier.height(12.dp)) }
                    }
                }
            }
        }
    }

    if (showAddModal) {
        ModalBottomSheet(onDismissRequest = { showAddModal = false }, sheetState = sheetState, containerColor = Color.White) {
            TerminalFeeFormContent(
                drivers = driverOptions,
                onCancel = { showAddModal = false },
                onSubmit = { driver, amount, feeType ->
                    viewModel.addCollection(driver.plateNumber, driver.driverName, amount, feeType)
                    showAddModal = false
                    selectedTab = 0
                }
            )
        }
    }
}

@Composable
fun LedgerItemCard(item: LedgerItem) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = EnerjeepBlue)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(item.plateNumber, fontWeight = FontWeight.Bold)
                    Text(item.driverName, color = Color.Gray, fontSize = 12.sp)
                    Text(item.date, color = Color.Gray, fontSize = 12.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatCurrency(item.amount).replace(".00", ""), fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Surface(color = EnerjeepBlue, shape = RoundedCornerShape(12.dp)) {
                    Text(item.feeType, color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(item.status, color = SuccessGreenText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalFeeFormContent(
    drivers: List<LedgerDriverOption>,
    onCancel: () -> Unit,
    onSubmit: (LedgerDriverOption, Double, String) -> Unit
) {
    var amount by remember { mutableStateOf("50") }
    var expandedDriver by remember { mutableStateOf(false) }
    var selectedDriver by remember(drivers) { mutableStateOf(drivers.firstOrNull()) }
    var expandedFeeType by remember { mutableStateOf(false) }
    val feeTypes = listOf("Terminal Fee", "Multa")
    var selectedFeeType by remember { mutableStateOf(feeTypes.first()) }

    Column(Modifier.fillMaxWidth().padding(24.dp).padding(bottom = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
            Text("Itala ang Koleksyon", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Text("Plaka", fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expandedDriver, onExpandedChange = { if (drivers.isNotEmpty()) expandedDriver = !expandedDriver }) {
            OutlinedTextField(
                value = selectedDriver?.let { "${it.plateNumber} — ${it.driverName}" } ?: "Walang rehistradong jeep",
                onValueChange = {}, readOnly = true, enabled = drivers.isNotEmpty(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDriver) },
                modifier = Modifier.fillMaxWidth().menuAnchor(), singleLine = true
            )
            ExposedDropdownMenu(expanded = expandedDriver, onDismissRequest = { expandedDriver = false }) {
                drivers.forEach { driver -> DropdownMenuItem(text = { Text("${driver.plateNumber} — ${driver.driverName}") }, onClick = { selectedDriver = driver; expandedDriver = false }) }
            }
        }
        if (drivers.isEmpty()) Text("Walang driver account na may nakatalang plaka. Gumawa muna ng driver account.", color = Color.Red, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Text("Uri ng Bayad", fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expandedFeeType, onExpandedChange = { expandedFeeType = !expandedFeeType }) {
            OutlinedTextField(value = selectedFeeType, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedFeeType) }, modifier = Modifier.fillMaxWidth().menuAnchor())
            ExposedDropdownMenu(expanded = expandedFeeType, onDismissRequest = { expandedFeeType = false }) {
                feeTypes.forEach { type -> DropdownMenuItem(text = { Text(type) }, onClick = { selectedFeeType = type; expandedFeeType = false }) }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Halaga", fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { if (it.isEmpty() || it.all(Char::isDigit)) amount = it },
            leadingIcon = { PesoIcon(modifier = Modifier.padding(start = 12.dp).size(18.dp)) },
            trailingIcon = {
                Column(Modifier.padding(end = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase", modifier = Modifier.size(20.dp).clickable { amount = ((amount.toIntOrNull() ?: 0) + 10).toString() })
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease", modifier = Modifier.size(20.dp).clickable { val current = amount.toIntOrNull() ?: 0; if (current >= 10) amount = (current - 10).toString() })
                }
            }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(28.dp)) { Text("Kanselahin", color = Color.Black) }
            Spacer(Modifier.width(16.dp))
            val finalAmount = amount.toDoubleOrNull() ?: 0.0
            Button(onClick = { selectedDriver?.let { if (finalAmount > 0.0) onSubmit(it, finalAmount, selectedFeeType) } }, enabled = selectedDriver != null && finalAmount > 0.0, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(28.dp)) { Text("Ipasa") }
        }
    }
}

@Composable
fun LedgerTabButton(modifier: Modifier, text: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(modifier = modifier.fillMaxHeight().padding(horizontal = 4.dp), onClick = onClick, colors = CardDefaults.cardColors(containerColor = if (isSelected) Color.White else Color.Transparent), shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 2.dp else 0.dp)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text, color = if (isSelected) Color.Black else Color.Gray, fontWeight = FontWeight.Medium) }
    }
}

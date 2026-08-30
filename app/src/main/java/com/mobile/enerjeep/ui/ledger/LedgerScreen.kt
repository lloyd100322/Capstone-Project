package com.mobile.enerjeep.ui.ledger

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.R
import java.text.NumberFormat
import java.util.Locale

// --- 1. LOCAL THEME COLORS ---
val LightGrayBackground = Color(0xFFF8F9FA)
val EnerjeepBlue = Color(0xFF194287)
val SuccessGreenText = Color(0xFF16A34A)
val FieldBorderColor = Color(0xFFE5E7EB)

// Helper for formatting currency
fun formatCurrency(amount: Double): String {
    val format = NumberFormat.getNumberInstance(Locale.US)
    return "₱${format.format(amount)}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: LedgerViewModel = hiltViewModel(),
    shouldOpenForm: Boolean = false,
    onFormHandled: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Ngayon, 1: Kasaysayan
    var showAddModal by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(shouldOpenForm) {
        if (shouldOpenForm) {
            showAddModal = true
            onFormHandled()
        }
    }

    Scaffold(
        containerColor = LightGrayBackground
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app_icon),
                        contentDescription = "Enerjeep Logo",
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Enerjeep", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Title & Action Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Talaan", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Button(
                        onClick = { showAddModal = true },
                        colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("+ Idagdag", fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Handle UI States
                when (val state = uiState) {
                    is LedgerUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EnerjeepBlue)
                        }
                    }
                    is LedgerUiState.Error -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(text = state.message, color = Color.Red)
                        }
                    }
                    is LedgerUiState.Success -> {
                        // Blue Summary Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = EnerjeepBlue),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Text(text = "Koleksyon Ngayon", fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = formatCurrency(state.totalToday).replace(".00", ""),
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Tabs
                        Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                            TabButton(modifier = Modifier.weight(1f), text = "Ngayon", isSelected = selectedTab == 0, onClick = { selectedTab = 0 })
                            TabButton(modifier = Modifier.weight(1f), text = "Kasaysayan", isSelected = selectedTab == 1, onClick = { selectedTab = 1 })
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Content View Based on Tab
                        if (selectedTab == 0) {
                            // Tab: Ngayon
                            if (state.todayCollections.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Wala pang koleksyon ngayon.", color = Color.Gray, fontSize = 14.sp)
                                }
                            } else {
                                state.todayCollections.forEach { item ->
                                    LedgerItemCard(item)
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        } else {
                            // Tab: Kasaysayan
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Hanapin ayon sa plaka", color = Color.LightGray) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = FieldBorderColor,
                                    focusedBorderColor = EnerjeepBlue,
                                    unfocusedContainerColor = Color.White,
                                    focusedContainerColor = Color.White
                                )
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            val filteredHistory = state.historyCollections.filter {
                                it.plateNumber.contains(searchQuery, ignoreCase = true)
                            }

                            if (filteredHistory.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Walang nahanap na record.", color = Color.Gray, fontSize = 14.sp)
                                }
                            } else {
                                filteredHistory.forEach { item ->
                                    LedgerItemCard(item)
                                    Spacer(modifier = Modifier.height(12.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet Form
    if (showAddModal) {
        ModalBottomSheet(
            onDismissRequest = { showAddModal = false },
            sheetState = sheetState,
            containerColor = Color.White,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            TerminalFeeFormContent(
                onCancel = { showAddModal = false },
                onSubmit = { selectedPlaka, amount, feeType ->
                    // Parse the "6543as — juan" string to get Plate and Name separately
                    val parts = selectedPlaka.split(" — ")
                    val plate = parts.getOrNull(0) ?: selectedPlaka
                    val driver = parts.getOrNull(1) ?: "Unknown"

                    viewModel.addCollection(plate, driver, amount, feeType)
                    showAddModal = false
                    selectedTab = 0 // Automatically switch to "Ngayon" tab to see the new entry
                }
            )
        }
    }
}

// --- REUSABLE COMPONENTS ---

@Composable
fun LedgerItemCard(item: LedgerItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DirectionsBus, contentDescription = null, tint = EnerjeepBlue, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = item.plateNumber, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = item.driverName, fontSize = 12.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(text = item.date, fontSize = 12.sp, color = Color.Gray)
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(text = formatCurrency(item.amount).replace(".00", ""), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                Spacer(modifier = Modifier.height(4.dp))
                Surface(color = EnerjeepBlue, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        text = item.feeType,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = item.status, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreenText)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalFeeFormContent(
    onCancel: () -> Unit,
    onSubmit: (String, Double, String) -> Unit // Now accepts Plaka String too
) {
    var amount by remember { mutableStateOf("50") } // Default to 50 for convenience

    // Dropdown states for Plaka
    var expandedPlaka by remember { mutableStateOf(false) }
    val plakaOptions = listOf("6543as — juan", "ABC-123 — Pedro", "XYZ-987 — Maria", "LMN-456 — Jose")
    var selectedPlaka by remember { mutableStateOf(plakaOptions[0]) }

    // Dropdown states for Uri ng Bayad
    var expandedUriNgBayad by remember { mutableStateOf(false) }
    val uriNgBayadOptions = listOf("Terminal Fee", "Multa")
    var selectedUriNgBayad by remember { mutableStateOf(uriNgBayadOptions[0]) }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp, top = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Itala ang Koleksyon", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Fully functional Plaka Dropdown
        Text(text = "Plaka", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = expandedPlaka,
            onExpandedChange = { expandedPlaka = !expandedPlaka }
        ) {
            OutlinedTextField(
                value = selectedPlaka,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPlaka) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = FieldBorderColor,
                    focusedBorderColor = EnerjeepBlue,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                )
            )
            ExposedDropdownMenu(
                expanded = expandedPlaka,
                onDismissRequest = { expandedPlaka = false },
                modifier = Modifier.background(Color.White)
            ) {
                plakaOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = option, color = Color.Black) },
                        onClick = {
                            selectedPlaka = option
                            expandedPlaka = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Uri ng Bayad Dropdown
        Text(text = "Uri ng Bayad", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(
            expanded = expandedUriNgBayad,
            onExpandedChange = { expandedUriNgBayad = !expandedUriNgBayad }
        ) {
            OutlinedTextField(
                value = selectedUriNgBayad,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedUriNgBayad) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = FieldBorderColor,
                    focusedBorderColor = EnerjeepBlue,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                )
            )
            ExposedDropdownMenu(
                expanded = expandedUriNgBayad,
                onDismissRequest = { expandedUriNgBayad = false },
                modifier = Modifier.background(Color.White)
            ) {
                uriNgBayadOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = option, color = Color.Black) },
                        onClick = {
                            selectedUriNgBayad = option
                            expandedUriNgBayad = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(text = "Halaga", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) amount = it },
            leadingIcon = { Text("₱", modifier = Modifier.padding(start = 12.dp), color = Color.Gray, fontSize = 16.sp) },
            trailingIcon = {
                Column(modifier = Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Up",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp).clickable {
                            val current = amount.toIntOrNull() ?: 0
                            amount = (current + 10).toString()
                        }
                    )
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Down",
                        tint = Color.Gray,
                        modifier = Modifier.size(20.dp).clickable {
                            val current = amount.toIntOrNull() ?: 0
                            if (current >= 10) amount = (current - 10).toString()
                        }
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor, focusedBorderColor = EnerjeepBlue, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(28.dp), border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor)) { Text("Kanselahin", color = Color.Black) }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = {
                    val finalAmount = amount.toDoubleOrNull() ?: 0.0
                    if (finalAmount > 0) {
                        // Pass the selectedPlaka string along with amount and fee type
                        onSubmit(selectedPlaka, finalAmount, selectedUriNgBayad)
                    }
                },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue),
                shape = RoundedCornerShape(28.dp)
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

@Preview(showBackground = true)
@Composable
fun LedgerScreenPreview() {
    LedgerScreen()
}
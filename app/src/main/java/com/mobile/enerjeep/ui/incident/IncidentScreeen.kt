package com.mobile.enerjeep.ui.incident

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.R
import com.mobile.enerjeep.ui.theme.*

/**
 * Created by Bry1337 on 8/19/26.
 * Copyright (c) 2026. All rights reserved.
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentScreen(
    onReportClick: () -> Unit,
    viewModel: IncidentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: Kasalukuyan, 1: Kasaysayan

    var selectedIncident by remember { mutableStateOf<IncidentItem?>(null) }
    var showSuccessSnackbar by remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(text = "Enerjeep", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Page Title & Action
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Insidente", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Button(
                        onClick = onReportClick,
                        colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue),
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("+ I-report", fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                when (val state = uiState) {
                    is IncidentUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = EnerjeepBlue)
                        }
                    }
                    is IncidentUiState.Error -> {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(text = state.message, color = Color.Red)
                        }
                    }
                    is IncidentUiState.Success -> {
                        // Summary Stats
                        Row(modifier = Modifier.fillMaxWidth()) {
                            IncidentStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Bukas",
                                value = state.openCount.toString(),
                                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange) }
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            IncidentStatCard(
                                modifier = Modifier.weight(1f),
                                title = "Naayos",
                                value = state.resolvedCount.toString(),
                                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen) }
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Search Bar
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Hanapin ayon sa uri, plaka, o lokasyon", color = Color.LightGray) },
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

                        Spacer(modifier = Modifier.height(24.dp))

                        // Tabs
                        Row(modifier = Modifier.fillMaxWidth().height(48.dp).background(Color.Transparent)) {
                            TabButton(modifier = Modifier.weight(1f), text = "Kasalukuyan", isSelected = selectedTab == 0, onClick = { selectedTab = 0 })
                            TabButton(modifier = Modifier.weight(1f), text = "Kasaysayan", isSelected = selectedTab == 1, onClick = { selectedTab = 1 })
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Incident Items List
                        if (selectedTab == 0) {
                            val filteredActive = state.activeIncidents.filter {
                                it.plateNumber.contains(searchQuery, ignoreCase = true) ||
                                        it.title.contains(searchQuery, ignoreCase = true) ||
                                        it.location.contains(searchQuery, ignoreCase = true)
                            }

                            if (filteredActive.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Walang bukas na insidente.", color = Color.Gray, fontSize = 14.sp)
                                }
                            } else {
                                filteredActive.forEach { incident ->
                                    IncidentItemCard(
                                        incident = incident,
                                        onViewClick = { selectedIncident = incident },
                                        onMarkResolvedClick = {
                                            viewModel.resolveIncident(incident.id)
                                            showSuccessSnackbar = true
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        } else {
                            val filteredHistory = state.historyIncidents.filter {
                                it.plateNumber.contains(searchQuery, ignoreCase = true) ||
                                        it.title.contains(searchQuery, ignoreCase = true) ||
                                        it.location.contains(searchQuery, ignoreCase = true)
                            }

                            if (filteredHistory.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                                    Text(text = "Walang kasaysayan ng insidente.", color = Color.Gray, fontSize = 14.sp)
                                }
                            } else {
                                filteredHistory.forEach { incident ->
                                    HistoryIncidentItemCard(incident = incident)
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Floating Snackbar Notification
            if (showSuccessSnackbar) {
                CustomSnackbar(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp).padding(bottom = 80.dp),
                    title = "Naayos",
                    message = "Na-mark bilang naayos ang insidente.",
                    onDismiss = { showSuccessSnackbar = false }
                )
            }
        }
    }

    // Modal Bottom Sheet
    if (selectedIncident != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedIncident = null },
            sheetState = sheetState,
            containerColor = Color.White,
            dragHandle = null
        ) {
            IncidentDetailsContent(
                incident = selectedIncident!!,
                onCancel = { selectedIncident = null },
                onResolve = {
                    viewModel.resolveIncident(selectedIncident!!.id)
                    selectedIncident = null
                    showSuccessSnackbar = true
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentReportFormScreen(
    onBack: () -> Unit,
    viewModel: IncidentViewModel = hiltViewModel()
) {
    var isSubmitted by remember { mutableStateOf(false) }

    // Plaka Dropdown State
    var expandedPlaka by remember { mutableStateOf(false) }
    val plakaOptions = listOf("6543as — juan", "ABC-123 — Pedro", "XYZ-987 — Maria", "LMN-456 — Jose")
    var selectedPlaka by remember { mutableStateOf(plakaOptions[0]) }

    var incidentType by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var penalty by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    Scaffold(
        containerColor = LightGrayBackground,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.Black) }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "I-report ang Insidente", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        },
        bottomBar = {
            Column(modifier = Modifier.background(LightGrayBackground).padding(24.dp)) {
                if (isSubmitted) {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(SuccessGreenLight, RoundedCornerShape(12.dp)).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(text = "Nai-save na ang insidente. Ipadala sa mga driver.", color = SuccessGreen, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(28.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor)
                    ) { Text("Kanselahin", color = Color.Black) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = {
                            if (!isSubmitted) {
                                viewModel.reportIncident(selectedPlaka, incidentType, location, penalty, description)
                                isSubmitted = true
                            } else {
                                onBack() // Navigate back after submission logic
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue),
                        shape = RoundedCornerShape(28.dp)
                    ) {
                        if (isSubmitted) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Ipadala")
                        } else {
                            Text("Ipasa")
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
        ) {

            // Fully Functional Dropdown for Plaka
            Text(text = "Plaka", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = expandedPlaka && !isSubmitted, // Prevent edits if already submitted
                onExpandedChange = { if (!isSubmitted) expandedPlaka = !expandedPlaka }
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

            IncidentFormTextField(
                label = "Uri ng Insidente",
                value = incidentType,
                onValueChange = { if (!isSubmitted) incidentType = it },
                placeholder = "Ilagay ang uri ng insidente"
            )

            Spacer(modifier = Modifier.height(16.dp))

            IncidentFormTextField(
                label = "Lokasyon",
                value = location,
                onValueChange = { if (!isSubmitted) location = it },
                placeholder = "Ilagay ang lokasyon"
            )

            TextButton(onClick = { }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Gamitin ang Kasalukuyang Lokasyon", color = Color.Gray, fontSize = 12.sp)
            }

            Spacer(modifier = Modifier.height(8.dp))

            IncidentFormTextField(
                label = "Multa (Penalty)",
                value = penalty,
                onValueChange = { if (!isSubmitted && (it.isEmpty() || it.all { char -> char.isDigit() })) penalty = it },
                placeholder = "0",
                leadingIcon = { Text("₱", modifier = Modifier.padding(start = 12.dp), color = Color.Gray) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Paglalarawan", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { if (!isSubmitted) description = it },
                placeholder = { Text("Ilarawan ang nangyari...", color = Color.LightGray) },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = FieldBorderColor,
                    focusedBorderColor = EnerjeepBlue,
                    unfocusedContainerColor = Color.White,
                    focusedContainerColor = Color.White
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Larawan", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Color.Gray)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mag-upload ng Larawan", color = Color.Gray)
            }

            Spacer(modifier = Modifier.height(80.dp))
        }
    }
}

// --- Reusable Components ---

@Composable
fun IncidentStatCard(modifier: Modifier = Modifier, title: String, value: String, icon: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            icon()
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(text = title, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun IncidentItemCard(
    incident: IncidentItem,
    onViewClick: () -> Unit,
    onMarkResolvedClick: () -> Unit
) {
    val statusColor = WarningOrange
    val statusBg = WarningOrangeLight

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.Top) {
                    Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = statusBg) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Warning, contentDescription = null, tint = statusColor) }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = incident.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                        Text(text = incident.plateNumber, fontSize = 14.sp, color = Color.Gray)
                        Text(text = incident.location, fontSize = 14.sp, color = Color.Gray)
                        Text(text = incident.date, fontSize = 12.sp, color = Color.Gray)
                    }
                }
                StatusBadge(text = incident.status, backgroundColor = statusBg, textColor = statusColor)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = onMarkResolvedClick,
                    modifier = Modifier.height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue),
                    shape = RoundedCornerShape(22.dp)
                ) { Text("Markahin Bilang Tapos", fontSize = 14.sp) }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onViewClick,
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tignan", color = Color.Black, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun HistoryIncidentItemCard(incident: IncidentItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.Top) {
                    Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = WarningOrangeLight) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange) }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = incident.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                        Text(text = incident.plateNumber, fontSize = 14.sp, color = Color.Gray)
                        Text(text = incident.date, fontSize = 12.sp, color = Color.Gray)
                    }
                }
                StatusBadge(text = incident.status, backgroundColor = SuccessGreenLight, textColor = SuccessGreen)
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                OutlinedButton(
                    onClick = { },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(22.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor)
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Tingnan", color = Color.Black, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun IncidentDetailsContent(
    incident: IncidentItem,
    onCancel: () -> Unit,
    onResolve: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Detalye ng Insidente", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = incident.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            StatusBadge(text = incident.status, backgroundColor = WarningOrangeLight, textColor = WarningOrange)
        }

        Spacer(modifier = Modifier.height(24.dp))

        IncidentInfoRow(label = "Plaka ng Sasakyan", value = incident.plateNumber, icon = { Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp)) })
        IncidentInfoRow(label = "Lokasyon", value = incident.location, icon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp)) })
        IncidentInfoRow(label = "Petsa at Oras", value = incident.date, icon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp)) })
        IncidentInfoRow(label = "Multiya", value = incident.penalty, icon = { Icon(Icons.Default.Payments, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp)) })
        IncidentInfoRow(label = "Paglalarawan", value = incident.description, icon = { Icon(Icons.Default.Description, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp)) })
        IncidentInfoRow(label = "Naipadala sa Driver", value = incident.sentToDriver, icon = { Icon(Icons.Default.Send, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp)) })

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(56.dp),
                shape = RoundedCornerShape(28.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor)
            ) { Text("Isara", color = Color.Black) }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = onResolve,
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue),
                shape = RoundedCornerShape(28.dp)
            ) { Text("Markahin Bilang Naayos") }
        }
    }
}

@Composable
fun IncidentInfoRow(label: String, value: String, icon: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(text = value, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

@Composable
fun CustomSnackbar(modifier: Modifier = Modifier, title: String, message: String, onDismiss: () -> Unit) {
    Card(modifier = modifier.width(280.dp), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = message, fontSize = 12.sp, color = Color.Gray)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp).padding(start = 8.dp)) { Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
fun IncidentFormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.Black)
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = Color.LightGray) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedBorderColor = FieldBorderColor,
            focusedBorderColor = EnerjeepBlue,
            unfocusedContainerColor = Color.White,
            focusedContainerColor = Color.White
        )
    )
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

@Composable
fun StatusBadge(text: String, backgroundColor: Color, textColor: Color) {
    Surface(color = backgroundColor, shape = RoundedCornerShape(12.dp)) {
        Text(text = text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Preview(showBackground = true)
@Composable
fun IncidentScreenPreview() {
    EnerjeepTheme { IncidentScreen(onReportClick = {}) }
}

@Preview(showBackground = true)
@Composable
fun IncidentReportFormPreview() {
    EnerjeepTheme { IncidentReportFormScreen(onBack = {}) }
}
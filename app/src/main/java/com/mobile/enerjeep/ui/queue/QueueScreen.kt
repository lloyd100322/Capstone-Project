package com.mobile.enerjeep.ui.queue

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.mobile.enerjeep.ui.components.HistoryFilterState
import com.mobile.enerjeep.ui.components.PesoIcon
import com.mobile.enerjeep.ui.components.filterHistoryRecords
import com.mobile.enerjeep.ui.theme.*



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel = hiltViewModel()
) {
    // Collect Business State from ViewModel safely
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Ephemeral UI State (stays in the composable)
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: Ngayon (aktibong pila), 1: Kasaysayan
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }

    // Instead of just a boolean, hold the actual vehicle being dispatched so the modal knows what to show
    var vehicleToDispatch by remember { mutableStateOf<QueueVehicle?>(null) }

    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        containerColor = LightGrayBackground
    ) { innerPadding ->
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
                    contentDescription = null,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(text = "Enerjeep", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(text = "Pila ng Dispatch", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(text = "Rotational na dispatch • Monumento – Marilao", fontSize = 14.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(24.dp))

            // Search Bar
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

            Spacer(modifier = Modifier.height(24.dp))

            // Handle UI States
            when (val state = uiState) {
                is QueueUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = EnerjeepBlue)
                    }
                }
                is QueueUiState.Error -> {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(text = state.message, color = ErrorRed)
                    }
                }
                is QueueUiState.Success -> {
                    // Summary Stats injected dynamically
                    Row(modifier = Modifier.fillMaxWidth()) {
                        QueueStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Sa Pila",
                            value = state.totalInQueue.toString(),
                            icon = { Image(painter = painterResource(id = R.drawable.app_icon), contentDescription = null, modifier = Modifier.size(20.dp)) }
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        QueueStatCard(
                            modifier = Modifier.weight(1f),
                            title = "Na-dispatch Ngayon",
                            value = state.totalDispatchedToday.toString(),
                            icon = { NavigationIcon(modifier = Modifier.size(20.dp), color = SuccessGreen) }
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Tabs
                    Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        TabButton(modifier = Modifier.weight(1f), text = "Ngayon" +
                                "", isSelected = selectedTab == 0, onClick = { selectedTab = 0 })
                        TabButton(modifier = Modifier.weight(1f), text = "Kasaysayan", isSelected = selectedTab == 1, onClick = { selectedTab = 1 })
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Dynamic Lists (filtered by the search bar: plaka o driver)
                    val searchMatches = { plate: String, driver: String ->
                        searchQuery.isBlank() ||
                                plate.contains(searchQuery, ignoreCase = true) ||
                                driver.contains(searchQuery, ignoreCase = true)
                    }
                    if (searchQuery.isNotBlank()) {
                        Text(
                            "I-clear ang search para magpalit ng posisyon ng mga sasakyan.",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (selectedTab == 0) {
                        val searchedQueue = state.activeQueue.filter { vehicle ->
                            searchMatches(vehicle.plateNumber, vehicle.driverName)
                        }
                        if (searchedQueue.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Walang sasakyan sa pila.", color = Color.Gray)
                            }
                        } else {
                            searchedQueue.forEachIndexed { index, vehicle ->
                                QueueItem(
                                    vehicle = vehicle,
                                    displayPosition = index + 1,
                                    positionEditingEnabled = searchQuery.isBlank(),
                                    onDispatchClick = { vehicleToDispatch = vehicle },
                                    onPositionUpdate = { newPosition -> viewModel.updateVehiclePosition(vehicle.id, newPosition) },
                                    onCancelClick = { viewModel.cancelQueueEntry(vehicle.id) },
                                    onRestoreClick = { viewModel.restoreQueueEntry(vehicle.id) }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                            }
                        }
                    } else {
                        HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(12.dp))

                        val filteredHistory = filterHistoryRecords(
                            state.historyQueue.filter { history ->
                                searchMatches(history.plateNumber, history.driverName)
                            },
                            { it.time },
                            historyFilter,
                            { it.createdAtMillis.takeIf { millis -> millis > 0 } }
                        )

                        if (filteredHistory.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Walang kasaysayan.", color = Color.Gray)
                            }
                        } else {
                            filteredHistory.forEach { history ->
                                HistoryItem(history)
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    // Bottom Modal for Dispatch
    if (vehicleToDispatch != null) {
        ModalBottomSheet(
            onDismissRequest = { vehicleToDispatch = null },
            sheetState = sheetState,
            containerColor = Color.White,
            dragHandle = null
        ) {
            DispatchCollectionContent(
                plateNumber = vehicleToDispatch!!.plateNumber,
                route = vehicleToDispatch!!.route,
                driverName = vehicleToDispatch!!.driverName,
                onCancel = { vehicleToDispatch = null },
                onDispatch = { income, otherFees ->
                    viewModel.dispatchVehicle(vehicleToDispatch!!.id, income, otherFees)
                    vehicleToDispatch = null
                }
            )
        }
    }
}

// --- REUSABLE COMPONENTS ---

@Composable
fun QueueStatCard(modifier: Modifier = Modifier, title: String, value: String, icon: @Composable () -> Unit) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            icon()
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
            Text(text = title, fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun TabButton(modifier: Modifier = Modifier, text: String, isSelected: Boolean, onClick: () -> Unit) {
    val backgroundColor = if (isSelected) Color.White else Color.Transparent
    val textColor = if (isSelected) Color.Black else Color.Gray
    val elevation = if (isSelected) 2.dp else 0.dp

    Card(
        modifier = modifier.fillMaxHeight().padding(horizontal = 4.dp),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(text = text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = textColor)
        }
    }
}

@Composable
fun QueueItem(
    vehicle: QueueVehicle,
    displayPosition: Int,
    positionEditingEnabled: Boolean = true,
    onDispatchClick: () -> Unit,
    onPositionUpdate: (Int) -> Unit,
    onCancelClick: () -> Unit,
    onRestoreClick: () -> Unit = {}
) {
    var isEditingPosition by remember { mutableStateOf(false) }
    var newPositionInput by remember { mutableStateOf("0") }
    var showCancelConfirm by remember { mutableStateOf(false) }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            title = { Text("Kanselahin ang pila?") },
            text = { Text("Mananatili sa pila ang ${vehicle.plateNumber} bilang \"Nakansela\" at ilalagay sa pinakadulo. Maaari pa itong i-dispatch mamaya.") },
            confirmButton = {
                TextButton(onClick = {
                    showCancelConfirm = false
                    onCancelClick()
                }) { Text("Oo, kanselahin", color = ErrorRed) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelConfirm = false }) { Text("Hindi") }
            }
        )
    }

    // Resolve UI colors based on ViewModel status string
    val (statusBg, statusColor) = when {
        vehicle.isCancelled -> Pair(ErrorRedLight, ErrorRed)
        vehicle.status.lowercase() == "nakapila" -> Pair(WarningOrangeLight, WarningOrange)
        else -> Pair(SuccessGreenLight, SuccessGreen)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(modifier = Modifier.size(40.dp), shape = RoundedCornerShape(12.dp), color = EnerjeepBlue) {
                    Box(contentAlignment = Alignment.Center) { Text(text = displayPosition.toString(), color = Color.White, fontWeight = FontWeight.Bold) }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WorldIcon(modifier = Modifier.size(16.dp), color = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = vehicle.plateNumber, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        StatusBadge(text = if (vehicle.isCancelled) "Nakansela" else vehicle.status, backgroundColor = statusBg, textColor = statusColor)
                    }
                    Text(text = vehicle.driverName, fontSize = 14.sp, color = Color.Gray)
                    Text(text = vehicle.route, fontSize = 12.sp, color = Color.Gray)
                    Text(text = "Posisyon #${displayPosition} • Sumali: ${vehicle.time}", fontSize = 12.sp, color = Color.Gray)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (isEditingPosition) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    /*
                     * The two arrow buttons used to be 24.dp each and were
                     * stacked inside a 48.dp tall field, so they took the
                     * whole height of the field and the number the driver
                     * typed was cut off. The field is now taller and the
                     * arrows are smaller, so the number is always fully
                     * visible while typing and after saving.
                     */
                    OutlinedTextField(
                        value = newPositionInput,
                        onValueChange = {
                            if (it.isEmpty() || it.all { char -> char.isDigit() }) {
                                newPositionInput = it
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = FieldBorderColor,
                            focusedBorderColor = EnerjeepBlue
                        ),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            Column(
                                modifier = Modifier.padding(end = 4.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDropUp,
                                    contentDescription = "Up",
                                    tint = Color.Gray,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            val current = newPositionInput.toIntOrNull() ?: 0
                                            newPositionInput = (current + 1).toString()
                                        }
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = "Down",
                                    tint = Color.Gray,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            val current = newPositionInput.toIntOrNull() ?: 0
                                            if (current > 0) newPositionInput = (current - 1).toString()
                                        }
                                )
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            newPositionInput.toIntOrNull()?.let {
                                onPositionUpdate(it)
                                isEditingPosition = false
                                newPositionInput = "0"
                            }
                        },
                        modifier = Modifier.height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Set", fontSize = 14.sp) }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            isEditingPosition = false
                            newPositionInput = "0"
                        },
                        modifier = Modifier.height(56.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Bawi", fontSize = 14.sp, color = Color.Black) }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = onDispatchClick,
                        modifier = Modifier.weight(1f).height(44.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        NavigationIcon(modifier = Modifier.size(16.dp), color = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("I-dispatch", fontSize = 14.sp)
                    }
                    if (positionEditingEnabled) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // UP ARROW: Decreases position number (moves closer to 1)
                        ActionButton(
                            icon = { ArrowUpIcon(modifier = Modifier.size(16.dp)) },
                            onClick = {
                                if (displayPosition > 1) {
                                    onPositionUpdate(displayPosition - 1)
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // DOWN ARROW: Increases position number (moves further back)
                        ActionButton(
                            icon = { ArrowDownIcon(modifier = Modifier.size(16.dp)) },
                            onClick = {
                                onPositionUpdate(displayPosition + 1)
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (positionEditingEnabled) {
                        ActionButton(
                            modifier = Modifier.weight(1f),
                            text = "Posisyon",
                            icon = { Text("#", fontWeight = FontWeight.Bold) },
                            onClick = { isEditingPosition = true }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    if (vehicle.isCancelled) {
                        ActionButton(
                            modifier = Modifier.weight(1f),
                            text = "Ibalik sa Pila",
                            icon = { NavigationIcon(modifier = Modifier.size(16.dp), color = SuccessGreen) },
                            textColor = SuccessGreen,
                            onClick = onRestoreClick
                        )
                    } else {
                        ActionButton(
                            modifier = Modifier.weight(1f),
                            text = "Kanselahin",
                            icon = { CancelIcon(modifier = Modifier.size(16.dp), color = ErrorRed) },
                            textColor = ErrorRed,
                            onClick = { showCancelConfirm = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(history: HistoryVehicle) {
    val (statusBg, statusColor) = when (history.status.lowercase()) {
        "nakansela" -> Pair(ErrorRedLight, ErrorRed)
        else -> Pair(EnerjeepBlue, Color.White)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WorldIcon(modifier = Modifier.size(14.dp), color = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = history.plateNumber, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(text = history.driverName, fontSize = 12.sp, color = Color.Gray)
                Text(text = "Sumali: ${history.time}", fontSize = 11.sp, color = Color.Gray)
            }
            StatusBadge(text = history.status, backgroundColor = statusBg, textColor = statusColor)
        }
    }
}

@Composable
fun StatusBadge(text: String, backgroundColor: Color, textColor: Color) {
    Surface(color = backgroundColor, shape = RoundedCornerShape(12.dp)) {
        Text(text = text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

@Composable
fun ActionButton(modifier: Modifier = Modifier, text: String? = null, icon: @Composable () -> Unit, textColor: Color = Color.Black, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        icon()
        if (text != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = text, fontSize = 14.sp, color = textColor)
        }
    }
}

@Composable
fun DispatchCollectionContent(
    plateNumber: String,
    route: String,
    driverName: String,
    onCancel: () -> Unit,
    onDispatch: (Double, Double) -> Unit
) {
    var income by remember { mutableStateOf("") }
    var otherFees by remember { mutableStateOf("") }

    val netValue = remember(income, otherFees) {
        val inc = income.toDoubleOrNull() ?: 0.0
        val fees = otherFees.toDoubleOrNull() ?: 0.0
        inc - fees
    }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Koleksyon sa Dispatch", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Close") }
        }

        Spacer(modifier = Modifier.height(24.dp))

        InfoRow(label = "Plaka", value = plateNumber, icon = { TruckIcon(modifier = Modifier.size(16.dp), color = Color.Gray) })
        InfoRow(label = "Ruta", value = route, icon = { RouteIcon(modifier = Modifier.size(16.dp), color = Color.Gray) })
        InfoRow(label = "Driver", value = driverName, icon = null)

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Kita (Income)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = income,
            onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) income = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { PesoIcon(modifier = Modifier.padding(start = 12.dp), size = 18.dp, color = Color.Gray) },
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Iba pang Bayad (Other Fees)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = otherFees,
            onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) otherFees = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { PesoIcon(modifier = Modifier.padding(start = 12.dp), size = 18.dp, color = Color.Gray) },
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Neto", color = Color.Gray)
            Text(text = "₱${netValue.toInt()}", fontWeight = FontWeight.Bold, color = EnerjeepBlue)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(28.dp), border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor)) { Text("Kanselahin", color = Color.Black) }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = { onDispatch(income.toDoubleOrNull() ?: 0.0, otherFees.toDoubleOrNull() ?: 0.0) },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue),
                shape = RoundedCornerShape(28.dp)
            ) { Text("I-dispatch") }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, icon: (@Composable () -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { icon(); Spacer(modifier = Modifier.width(8.dp)) } else { Spacer(modifier = Modifier.width(24.dp)) }
        Text(text = label, color = Color.Gray, modifier = Modifier.weight(1f))
        Text(text = value, fontWeight = FontWeight.Bold)
    }
}

// Icon Definitions
@Composable
fun WorldIcon(modifier: Modifier = Modifier, color: Color = Color.Gray) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width; val height = size.height
        drawCircle(color = color, radius = width * 0.45f, center = Offset(width * 0.5f, height * 0.5f), style = Stroke(width = width * 0.05f))
        drawLine(color = color, start = Offset(width * 0.05f, height * 0.5f), end = Offset(width * 0.95f, height * 0.5f), strokeWidth = width * 0.05f)
        drawOval(color = color, topLeft = Offset(width * 0.3f, height * 0.05f), size = Size(width * 0.4f, height * 0.9f), style = Stroke(width = width * 0.05f))
    }
}
@Composable
fun NavigationIcon(modifier: Modifier = Modifier, color: Color = Color.Gray) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(size.width * 0.5f, 0f); lineTo(size.width, size.height); lineTo(size.width * 0.5f, size.height * 0.8f); lineTo(0f, size.height); close() }
        drawPath(path = path, color = color)
    }
}
@Composable
fun ArrowUpIcon(modifier: Modifier = Modifier, color: Color = Color.Gray) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width; val height = size.height
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(width * 0.5f, height * 0.2f); lineTo(width * 0.8f, height * 0.5f); lineTo(width * 0.2f, height * 0.5f); close() }
        drawPath(path = path, color = color)
        drawLine(color = color, start = Offset(width * 0.5f, height * 0.5f), end = Offset(width * 0.5f, height * 0.8f), strokeWidth = width * 0.1f)
    }
}
@Composable
fun ArrowDownIcon(modifier: Modifier = Modifier, color: Color = Color.Gray) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width; val height = size.height
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(width * 0.5f, height * 0.8f); lineTo(width * 0.8f, height * 0.5f); lineTo(width * 0.2f, height * 0.5f); close() }
        drawPath(path = path, color = color)
        drawLine(color = color, start = Offset(width * 0.5f, height * 0.5f), end = Offset(width * 0.5f, height * 0.2f), strokeWidth = width * 0.1f)
    }
}
@Composable
fun CancelIcon(modifier: Modifier = Modifier, color: Color = Color.Gray) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        drawCircle(color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        drawLine(color = color, start = Offset(size.width * 0.25f, size.height * 0.25f), end = Offset(size.width * 0.75f, size.height * 0.75f), strokeWidth = 2.dp.toPx())
    }
}
@Composable
fun RouteIcon(modifier: Modifier = Modifier, color: Color = Color.Gray) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width; val height = size.height
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(width * 0.2f, height * 0.2f); cubicTo(width * 0.8f, height * 0.2f, width * 0.2f, height * 0.8f, width * 0.8f, height * 0.8f) }
        drawPath(path = path, color = color, style = Stroke(width = width * 0.1f))
        drawCircle(color = color, radius = width * 0.1f, center = Offset(width * 0.2f, height * 0.2f))
        drawCircle(color = color, radius = width * 0.1f, center = Offset(width * 0.8f, height * 0.8f))
    }
}
@Composable
fun TruckIcon(modifier: Modifier = Modifier, color: Color = Color.Gray) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        drawRoundRect(color = color, topLeft = Offset(0f, size.height * 0.2f), size = Size(size.width * 0.6f, size.height * 0.6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
        drawRoundRect(color = color, topLeft = Offset(size.width * 0.65f, size.height * 0.4f), size = Size(size.width * 0.35f, size.height * 0.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
        drawCircle(color = Color.DarkGray, radius = size.width * 0.1f, center = Offset(size.width * 0.2f, size.height * 0.9f))
        drawCircle(color = Color.DarkGray, radius = size.width * 0.1f, center = Offset(size.width * 0.8f, size.height * 0.9f))
    }
}
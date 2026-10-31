package com.mobile.enerjeep.ui.queue

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import com.mobile.enerjeep.ui.theme.TextSecondary
import kotlin.math.roundToInt



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel = hiltViewModel()
) {
    // Collect Business State from ViewModel safely
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cutoffHourLabel by viewModel.cutoffLabel.collectAsStateWithLifecycle()

    // Ephemeral UI State (stays in the composable)
    var searchQuery by remember { mutableStateOf("") }

    // 0: Marilao -> Monumento, 1: Monumento -> Marilao, 2: Kasaysayan
    var selectedTab by remember { mutableStateOf(0) }
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }

    // "Zoom out": smaller boxes so a long pila scrolls faster.
    // Pinch gesture ang nagpapalit nito (walang button).
    var compactView by remember { mutableStateOf(false) }

    /*
     * Drag-to-reorder (zoom-out mode lang): long-press + hila ng kahon
     * para lumipat ng posisyon sa pila.
     */
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    val itemHeights = remember { mutableStateMapOf<String, Int>() }

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
            Text(text = "Rotational na dispatch • 2 Ruta • Auto-reset $cutoffHourLabel", fontSize = 14.sp, color = TextSecondary)

            Spacer(modifier = Modifier.height(24.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Hanapin ayon sa plaka", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary) },
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

            /*
             * Awtomatikong reset tuwing cut-off hour (editable ng
             * superadmin sa Settings). Tanggal na ang "I-reset Ngayon"
             * na button - awtomatiko pa rin ang reset.
             */
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Reset ng Pila • $cutoffHourLabel",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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

                    /*
                     * Tabs: 2 routes + Kasaysayan. Marilao–Monumento
                     * muna (tab 0), kasunod ang Monumento–Marilao
                     * (tab 1), tapos Kasaysayan. Magkakapareho ang
                     * laki at espasyo ng bawat tab para pantay.
                     */
                    Row(
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TabButton(
                            modifier = Modifier.weight(1f),
                            text = "Marilao",
                            isSelected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            fontSize = 13.sp
                        )
                        TabButton(
                            modifier = Modifier.weight(1f),
                            text = "Monumento",
                            isSelected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            fontSize = 13.sp
                        )
                        TabButton(
                            modifier = Modifier.weight(1f),
                            text = "Kasaysayan",
                            isSelected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            fontSize = 10.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    /*
                     * Zoom: PINCH gestures na lang ang gamit (walang
                     * button). Pinch IN = maliit na box (zoom out),
                     * pinch OUT = normal na laki.
                     */
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Pinch para mag-zoom • Pindutin nang matagal ang kahon para ilipat ang posisyon.",
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
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
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    if (selectedTab == 0 || selectedTab == 1) {
                        /*
                         * Each route tab shows only the pila of that
                         * route. Tab 0 = Marilao -> Monumento, tab 1 =
                         * Monumento -> Marilao.
                         */
                        val onThisRoute = { vehicle: QueueVehicle ->
                            val marilaoFirst = vehicle.route.trim()
                                .startsWith("Marilao", ignoreCase = true)
                            if (selectedTab == 0) marilaoFirst else !marilaoFirst
                        }
                        val searchedQueue = state.activeQueue.filter { vehicle ->
                            onThisRoute(vehicle) &&
                                    searchMatches(vehicle.plateNumber, vehicle.driverName)
                        }
                        if (searchedQueue.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                Text("Walang sasakyan sa pila.", color = TextSecondary)
                            }
                        } else {
                            /*
                             * FIFO: only the jeepney at the FRONT of the
                             * line can be dispatched. To send someone else
                             * first, move it to the top with the arrows
                             * ("Selects which one goes first"), or - kapag
                             * naka-ZOOM OUT - i-drag ang kahon.
                             * A "Nakansela" entry is OUT of the line - it
                             * has no dispatch button and rejoins the END
                             * of the pila through "Ibalik sa Pila".
                             */
                            val frontOfLineId = state.activeQueue
                                .filter { onThisRoute(it) }
                                .firstOrNull { !it.isCancelled }
                                ?.id

                            /*
                             * Pinch-to-zoom ang buong listahan (dalawang
                             * daliri). Hindi nito hinaharangan ang
                             * single-finger scroll.
                             */
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            var event =
                                                awaitPointerEvent()
                                            while (event.changes.count { it.pressed } < 2) {
                                                if (event.changes.none { it.pressed }) {
                                                    return@awaitEachGesture
                                                }
                                                event = awaitPointerEvent()
                                            }
                                            while (event.changes.count { it.pressed } >= 2) {
                                                val zoom =
                                                    event.calculateZoom()
                                                if (zoom != 1f) {
                                                    if (zoom < 0.95f) {
                                                        compactView = true
                                                    } else if (zoom > 1.05f) {
                                                        compactView = false
                                                    }
                                                    event.changes.forEach {
                                                        it.consume()
                                                    }
                                                }
                                                event = awaitPointerEvent()
                                            }
                                        }
                                    }
                            ) {
                                searchedQueue.forEachIndexed { index, vehicle ->
                                    val isDragging = dragIndex == index
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .onSizeChanged {
                                                itemHeights[vehicle.id] =
                                                    it.height
                                            }
                                            .graphicsLayer {
                                                translationY =
                                                    if (isDragging) dragOffsetPx else 0f
                                                shadowElevation =
                                                    if (isDragging) 12f else 0f
                                            }
                                            .then(
                                                /*
                                                 * Drag-to-reorder: ZOOM OUT
                                                 * mode lang habang walang
                                                 * search. Long-press tapos
                                                 * hila - lumilipat ng
                                                 * posisyon sa pila.
                                                 */
                                                if (compactView &&
                                                    searchQuery.isBlank() &&
                                                    !vehicle.isCancelled
                                                ) {
                                                    Modifier.pointerInput(vehicle.id) {
                                                        detectDragGesturesAfterLongPress(
                                                            onDragStart = {
                                                                dragIndex = index
                                                                dragOffsetPx = 0f
                                                            },
                                                            onDrag = { _, amount ->
                                                                dragOffsetPx += amount.y
                                                            },
                                                            onDragEnd = {
                                                                val height =
                                                                    (itemHeights[vehicle.id]
                                                                        ?: 1)
                                                                        .toFloat()
                                                                val steps =
                                                                    (dragOffsetPx / height)
                                                                        .roundToInt()
                                                                if (steps != 0) {
                                                                    viewModel.updateVehiclePosition(
                                                                        vehicle.id,
                                                                        vehicle.position + steps
                                                                    )
                                                                }
                                                                dragIndex = null
                                                                dragOffsetPx = 0f
                                                            },
                                                            onDragCancel = {
                                                                dragIndex = null
                                                                dragOffsetPx = 0f
                                                            }
                                                        )
                                                    }
                                                } else {
                                                    Modifier
                                                }
                                            )
                                    ) {
                                        QueueItem(
                                            vehicle = vehicle,
                                            displayPosition = vehicle.position,
                                            compact = compactView,
                                            canReorder = searchQuery.isBlank(),
                                            canDispatch = vehicle.id == frontOfLineId,
                                            onDispatchClick = { vehicleToDispatch = vehicle },
                                            onPositionUpdate = { newPosition -> viewModel.updateVehiclePosition(vehicle.id, newPosition) },
                                            onCancelClick = { viewModel.cancelQueueEntry(vehicle.id) },
                                            onRestoreClick = { viewModel.restoreQueueEntry(vehicle.id) }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
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
                                Text("Wala pang kasaysayan ng dispatch.", color = TextSecondary)
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
                suggestedDeduction = {
                    viewModel.suggestedDeduction(vehicleToDispatch!!.driverId)
                },
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
            Text(text = title, fontSize = 12.sp, color = TextSecondary)
        }
    }
}

@Composable
fun TabButton(
    modifier: Modifier = Modifier,
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp
) {
    val backgroundColor = if (isSelected) Color.White else Color.Transparent
    val textColor = if (isSelected) Color.Black else TextSecondary
    val elevation = if (isSelected) 2.dp else 0.dp

    Card(
        modifier = modifier.fillMaxHeight(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1
            )
        }
    }
}

@Composable
fun QueueItem(
    vehicle: QueueVehicle,
    displayPosition: Int,
    compact: Boolean = false,
    canReorder: Boolean = true,
    canDispatch: Boolean = true,
    onDispatchClick: () -> Unit,
    onPositionUpdate: (Int) -> Unit,
    onCancelClick: () -> Unit,
    onRestoreClick: () -> Unit = {}
) {
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
                }) { Text("Oo", color = ErrorRed, maxLines = 1) }
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
        Column(modifier = Modifier.padding(if (compact) 12.dp else 20.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(
                    modifier = Modifier.size(if (compact) 32.dp else 40.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = EnerjeepBlue
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = displayPosition.toString(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 12.sp else 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WorldIcon(modifier = Modifier.size(16.dp), color = Color.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = vehicle.plateNumber,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (compact) 13.sp else 16.sp
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        StatusBadge(
                            text = if (vehicle.isCancelled) "Nakansela" else vehicle.status,
                            backgroundColor = statusBg,
                            textColor = statusColor
                        )
                    }
                    Text(
                        text = vehicle.driverName,
                        fontSize = if (compact) 12.sp else 14.sp,
                        color = TextSecondary
                    )
                    if (!compact) {
                        Text(text = vehicle.route, fontSize = 12.sp, color = TextSecondary)
                    }

                    /*
                     * Posisyon = the big NUMBER badge on the left only.
                     * The old "Posisyon" button / tap-to-edit is gone;
                     * reordering is done with the big up/down arrows.
                     */
                    Text(
                        text = "Sumali: ${vehicle.time}",
                        fontSize = if (compact) 11.sp else 12.sp,
                        color = TextSecondary
                    )
                }

                /*
                 * Upper-right corner: an X button for "Kanselahin" (with
                 * the confirm dialog), or "Ibalik sa Pila" for cancelled
                 * rows. No more text button in the middle of the row.
                 */
                if (vehicle.isCancelled) {
                    TextButton(
                        onClick = onRestoreClick,
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "Ibalik",
                            fontSize = 11.sp,
                            color = SuccessGreen,
                            maxLines = 1
                        )
                    }
                } else {
                    IconButton(
                        onClick = { showCancelConfirm = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Kanselahin",
                            tint = ErrorRed,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (compact) 8.dp else 20.dp))

            if (canDispatch) {
                Button(
                    onClick = onDispatchClick,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    NavigationIcon(modifier = Modifier.size(16.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("I-dispatch", fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            /*
             * The ONLY position control: two BIG up/down arrows. The old
             * "Posisyon" button + number field + Set/Bawi are gone.
             * Hidden in compact mode, while searching and for cancelled
             * rows (they wait at the end anyway).
             */
            if (canReorder && !compact && !vehicle.isCancelled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (displayPosition > 1) {
                                onPositionUpdate(displayPosition - 1)
                            }
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        ArrowUpIcon(
                            modifier = Modifier.size(28.dp),
                            color = EnerjeepBlue
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            onPositionUpdate(displayPosition + 1)
                        },
                        modifier = Modifier.weight(1f).height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        ArrowDownIcon(
                            modifier = Modifier.size(28.dp),
                            color = EnerjeepBlue
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItem(history: HistoryVehicle) {
    /*
     * History statuses are LABELS, not buttons. "Dispatch" used to be a
     * solid blue chip that looked clickable - it is now a plain muted
     * label like every other status.
     */
    val (statusBg, statusColor) = when (history.status.lowercase()) {
        "nakansela" -> Pair(ErrorRedLight, ErrorRed)
        "tapos na" -> Pair(SuccessGreenLight, SuccessGreen)
        else -> Pair(Color(0xFFECEFF3), Color(0xFF5A6472))
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
                Text(text = history.driverName, fontSize = 12.sp, color = TextSecondary)
                Text(text = history.route, fontSize = 11.sp, color = TextSecondary)
                Text(text = "Sumali: ${history.time}", fontSize = 11.sp, color = TextSecondary)
            }
            StatusBadge(text = history.status, backgroundColor = statusBg, textColor = statusColor)
        }
    }
}

@Composable
fun StatusBadge(text: String, backgroundColor: Color, textColor: Color) {
    // A plain label chip - it must never look like a clickable button.
    Surface(color = backgroundColor, shape = RoundedCornerShape(8.dp)) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = textColor
        )
    }
}

@Composable
fun ActionButton(modifier: Modifier = Modifier, text: String? = null, icon: @Composable () -> Unit, textColor: Color = Color.Black, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(16.dp),
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
    suggestedDeduction: suspend () -> Double = { 0.0 },
    onCancel: () -> Unit,
    onDispatch: (Double, Double) -> Unit
) {
    var income by remember { mutableStateOf("") }
    var otherFees by remember { mutableStateOf("") }

    /*
     * The "bawas" is filled in automatically from the number of seats
     * of the jeep (fixed tier). The admin may still change it here.
     */
    LaunchedEffect(Unit) {
        val suggested = suggestedDeduction()
        if (otherFees.isBlank()) {
            otherFees = suggested
                .toInt()
                .toString()
        }
    }

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

        InfoRow(label = "Plaka", value = plateNumber, icon = { TruckIcon(modifier = Modifier.size(16.dp), color = TextSecondary) })
        InfoRow(label = "Ruta", value = route, icon = { RouteIcon(modifier = Modifier.size(16.dp), color = TextSecondary) })
        InfoRow(label = "Driver", value = driverName, icon = null)

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Kita (Income)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = income,
            onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) income = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { PesoIcon(modifier = Modifier.padding(start = 12.dp), size = 18.dp, color = TextSecondary) },
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Bawas (Awtomatiko batay sa Upuan)", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Awtomatiko itong kinakalkula mula sa bilang ng upuan ng jeep. Maaari itong baguhin dito.",
            fontSize = 11.sp,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = otherFees,
            onValueChange = { if (it.isEmpty() || it.all { char -> char.isDigit() }) otherFees = it },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { PesoIcon(modifier = Modifier.padding(start = 12.dp), size = 18.dp, color = TextSecondary) },
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = FieldBorderColor)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = "Malinis na Kita", color = TextSecondary)
            Text(text = "₱${netValue.toInt()}", fontWeight = FontWeight.Bold, color = EnerjeepBlue)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(56.dp), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, FieldBorderColor)) { Text("Kanselahin", color = Color.Black) }
            Spacer(modifier = Modifier.width(16.dp))
            Button(
                onClick = { onDispatch(income.toDoubleOrNull() ?: 0.0, otherFees.toDoubleOrNull() ?: 0.0) },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EnerjeepBlue),
                shape = RoundedCornerShape(16.dp)
            ) { Text("I-dispatch") }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String, icon: (@Composable () -> Unit)? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) { icon(); Spacer(modifier = Modifier.width(8.dp)) } else { Spacer(modifier = Modifier.width(24.dp)) }
        Text(text = label, color = TextSecondary, modifier = Modifier.weight(1f))
        Text(text = value, fontWeight = FontWeight.Bold)
    }
}

// Icon Definitions
@Composable
fun WorldIcon(modifier: Modifier = Modifier, color: Color = TextSecondary) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width; val height = size.height
        drawCircle(color = color, radius = width * 0.45f, center = Offset(width * 0.5f, height * 0.5f), style = Stroke(width = width * 0.05f))
        drawLine(color = color, start = Offset(width * 0.05f, height * 0.5f), end = Offset(width * 0.95f, height * 0.5f), strokeWidth = width * 0.05f)
        drawOval(color = color, topLeft = Offset(width * 0.3f, height * 0.05f), size = Size(width * 0.4f, height * 0.9f), style = Stroke(width = width * 0.05f))
    }
}
@Composable
fun NavigationIcon(modifier: Modifier = Modifier, color: Color = TextSecondary) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(size.width * 0.5f, 0f); lineTo(size.width, size.height); lineTo(size.width * 0.5f, size.height * 0.8f); lineTo(0f, size.height); close() }
        drawPath(path = path, color = color)
    }
}
@Composable
fun ArrowUpIcon(modifier: Modifier = Modifier, color: Color = TextSecondary) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width; val height = size.height
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(width * 0.5f, height * 0.2f); lineTo(width * 0.8f, height * 0.5f); lineTo(width * 0.2f, height * 0.5f); close() }
        drawPath(path = path, color = color)
        drawLine(color = color, start = Offset(width * 0.5f, height * 0.5f), end = Offset(width * 0.5f, height * 0.8f), strokeWidth = width * 0.1f)
    }
}
@Composable
fun ArrowDownIcon(modifier: Modifier = Modifier, color: Color = TextSecondary) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width; val height = size.height
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(width * 0.5f, height * 0.8f); lineTo(width * 0.8f, height * 0.5f); lineTo(width * 0.2f, height * 0.5f); close() }
        drawPath(path = path, color = color)
        drawLine(color = color, start = Offset(width * 0.5f, height * 0.5f), end = Offset(width * 0.5f, height * 0.2f), strokeWidth = width * 0.1f)
    }
}
@Composable
fun CancelIcon(modifier: Modifier = Modifier, color: Color = TextSecondary) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        drawCircle(color = color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
        drawLine(color = color, start = Offset(size.width * 0.25f, size.height * 0.25f), end = Offset(size.width * 0.75f, size.height * 0.75f), strokeWidth = 2.dp.toPx())
    }
}
@Composable
fun RouteIcon(modifier: Modifier = Modifier, color: Color = TextSecondary) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width; val height = size.height
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(width * 0.2f, height * 0.2f); cubicTo(width * 0.8f, height * 0.2f, width * 0.2f, height * 0.8f, width * 0.8f, height * 0.8f) }
        drawPath(path = path, color = color, style = Stroke(width = width * 0.1f))
        drawCircle(color = color, radius = width * 0.1f, center = Offset(width * 0.2f, height * 0.2f))
        drawCircle(color = color, radius = width * 0.1f, center = Offset(width * 0.8f, height * 0.8f))
    }
}
@Composable
fun TruckIcon(modifier: Modifier = Modifier, color: Color = TextSecondary) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        drawRoundRect(color = color, topLeft = Offset(0f, size.height * 0.2f), size = Size(size.width * 0.6f, size.height * 0.6f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
        drawRoundRect(color = color, topLeft = Offset(size.width * 0.65f, size.height * 0.4f), size = Size(size.width * 0.35f, size.height * 0.4f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
        drawCircle(color = Color.DarkGray, radius = size.width * 0.1f, center = Offset(size.width * 0.2f, size.height * 0.9f))
        drawCircle(color = Color.DarkGray, radius = size.width * 0.1f, center = Offset(size.width * 0.8f, size.height * 0.9f))
    }
}
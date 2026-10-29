package com.mobile.enerjeep.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mobile.enerjeep.R
import com.mobile.enerjeep.navigation.AppNavigator
import com.mobile.enerjeep.ui.components.*
import com.mobile.enerjeep.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.ui.incident.DriverIncidentScreen
import com.mobile.enerjeep.ui.kita.KitaScreen
import com.mobile.enerjeep.ui.profile.DriverProfileScreen
import android.media.RingtoneManager
import androidx.compose.ui.platform.LocalContext



// 1. Driver-Specific Tabs
enum class DriverTab {
    Home, Insidente, Kita, Profile
}

// 2. Master Controller for the Driver Role
@Composable
fun DriverContent(
    appNavigator: AppNavigator,
    emergencyContactName: String? = null,
    emergencyContactNumber: String? = null,
    adminContacts: List<Pair<String, String>> = emptyList(),
    viewModel: DriverViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(DriverTab.Home) }
    var showIncidentForm by remember { mutableStateOf(false) }
    var showEmergencyDialog by remember { mutableStateOf(false) }
    var showAlertsSheet by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Fase 7: dispatch alert (banner + bell + sound)
    val dispatchAlert by viewModel.dispatchAlert.collectAsStateWithLifecycle()
    val recentAlerts by viewModel.recentAlerts.collectAsStateWithLifecycle()
    val hasUnreadAlerts by viewModel.hasUnreadAlerts.collectAsStateWithLifecycle()
    val context = LocalContext.current

    /*
     * Sound kapag may BAGONG dispatch alert - notification tone ng
     * device (walang external audio file na kailangan).
     */
    LaunchedEffect(dispatchAlert?.id) {
        if (dispatchAlert != null) {
            try {
                val uri = RingtoneManager.getDefaultUri(
                    RingtoneManager.TYPE_NOTIFICATION
                )
                RingtoneManager.getRingtone(context, uri)?.play()
            } catch (_: Exception) {
                // Tahimik lang kung hindi available ang audio.
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == DriverTab.Home,
                    onClick = {
                        selectedTab = DriverTab.Home
                        showIncidentForm = false
                    },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EnerjeepBlue,
                        selectedTextColor = EnerjeepBlue,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == DriverTab.Insidente,
                    onClick = { selectedTab = DriverTab.Insidente },
                    icon = { WarningIcon(modifier = Modifier.size(24.dp)) },
                    label = { Text("Insidente", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == DriverTab.Kita,
                    onClick = {
                        selectedTab = DriverTab.Kita
                        showIncidentForm = false
                    },
                    icon = { SalesIcon(size = 24.dp, color = if (selectedTab == DriverTab.Kita) EnerjeepBlue else Color.Gray) },
                    label = { Text("Kita", fontSize = 10.sp) }
                )
                NavigationBarItem(
                    selected = selectedTab == DriverTab.Profile,
                    onClick = {
                        selectedTab = DriverTab.Profile
                        showIncidentForm = false
                    },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile", fontSize = 10.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EnerjeepBlue,
                        selectedTextColor = EnerjeepBlue,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray,
                        indicatorColor = Color.Transparent
                    )
                )
            }
        },
        containerColor = LightGrayBackground
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        ) {
            when (selectedTab) {
                DriverTab.Home -> {
                    // Display-only data for the driver dashboard
                    DriverDashboardContent(
                        firstName = uiState.firstName,
                        plateNumber = uiState.plateNumber,
                        route = uiState.route,
                        queuePosition = uiState.queuePosition,
                        queueStatus = uiState.queueStatus,
                        activeIncidentTitle = uiState.activeIncidentTitle,
                        activeIncidentDate = uiState.activeIncidentDate,
                        activeIncidentPlate = uiState.activeIncidentPlate,
                        openIncidentCount = uiState.openIncidentCount,
                        posisyonBukas = uiState.posisyonBukas,
                        queueLeadName = uiState.queueLeadName,
                        queueLeadPlate = uiState.queueLeadPlate,
                        queueNextName = uiState.queueNextName,
                        queueNextPlate = uiState.queueNextPlate,
                        isInQueue = uiState.activeQueueDocId.isNotBlank() &&
                                !uiState.isCancelledInQueue,
                        travelEnded = uiState.travelEndedToday,
                        dailyEarnings = uiState.dailyEarnings,
                        dispatchAlert = dispatchAlert,
                        hasUnreadAlerts = hasUnreadAlerts,
                        onBellClick = {
                            viewModel.markAlertsRead()
                            showAlertsSheet = true
                        },
                        onDismissAlert = {
                            viewModel.dismissDispatchAlert()
                        },
                        onTaposNa = {
                            viewModel.endTravel()
                        },
                        onEmergencyClick = {
                            showEmergencyDialog = true
                        }
                    )
                }
                DriverTab.Insidente -> {
                    DriverIncidentScreen()
                }
                DriverTab.Kita -> {
                    KitaScreen()
                }
                DriverTab.Profile -> {
                    DriverProfileScreen(appNavigator = appNavigator)
                }
            }

            if (showEmergencyDialog) {
                EmergencyDialog(
                    onDismiss = {
                        showEmergencyDialog = false
                    },
                    emergencyContactName = emergencyContactName,
                    emergencyContactNumber = emergencyContactNumber,
                    adminContacts = adminContacts
                )
            }

            if (showAlertsSheet) {
                DispatchAlertsSheet(
                    alerts = recentAlerts,
                    onDismiss = { showAlertsSheet = false }
                )
            }
        }
    }
}

// 3. The Driver Dashboard UI (Read-Only)
@Composable
fun DriverDashboardContent(
    firstName: String,
    plateNumber: String,
    route: String,
    queuePosition: Int,
    queueStatus: String,
    activeIncidentTitle: String?,
    activeIncidentDate: String?,
    activeIncidentPlate: String?,
    openIncidentCount: Int,
    posisyonBukas: Int = 0,
    queueLeadName: String = "",
    queueLeadPlate: String = "",
    queueNextName: String = "",
    queueNextPlate: String = "",
    isInQueue: Boolean = false,
    travelEnded: Boolean = false,
    dailyEarnings: Double? = null,
    dispatchAlert: DriverDispatchAlert? = null,
    hasUnreadAlerts: Boolean = false,
    onBellClick: () -> Unit = {},
    onDismissAlert: () -> Unit = {},
    onTaposNa: () -> Unit = {},
    onEmergencyClick: () -> Unit = {}
) {
    var showTaposConfirm by remember { mutableStateOf(false) }

    if (showTaposConfirm) {
        AlertDialog(
            onDismissRequest = { showTaposConfirm = false },
            title = { Text("Tapos na ang biyahe?") },
            text = {
                Text(
                    "Aalisin ka sa pila ngayon at mapupunta sa Kasaysayan. " +
                            "Maaari kang bumalik bukas ayon sa inyong pila."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showTaposConfirm = false
                    onTaposNa()
                }) { Text("Oo", color = SuccessGreen, maxLines = 1) }
            },
            dismissButton = {
                TextButton(onClick = { showTaposConfirm = false }) {
                    Text("Hindi", maxLines = 1)
                }
            }
        )
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        // Header (may bell para sa mga dispatch alert)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "Enerjeep App Logo",
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Enerjeep",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onBellClick) {
                Box {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Mga Alerto",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                    if (hasUnreadAlerts) {
                        Surface(
                            modifier = Modifier
                                .size(10.dp)
                                .align(Alignment.TopEnd),
                            shape = RoundedCornerShape(5.dp),
                            color = ErrorRed
                        ) {}
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Greeting
        Text(
            text = "Musta, $firstName!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )
        Text(
            text = "Narito ang iyong pila at status ngayon.",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        /*
         * Fase 7: banner ng dispatch alert - "Na-dispatch ka na!"
         * kasama ang plaka at ruta. Maaaring isara.
         */
        dispatchAlert?.let { alert ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = EnerjeepBlue),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Na-dispatch ka na!",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = listOf(
                                alert.plateNumber,
                                alert.route,
                                alert.time
                            ).filter { it.isNotBlank() }
                                .joinToString(" • "),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp
                        )
                    }
                    IconButton(onClick = onDismissAlert) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Isara",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Blue Status Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = EnerjeepBlue),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Status ng Pila",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = queueStatus,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "#$queuePosition",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = plateNumber,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = route,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        if (posisyonBukas > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color.White.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "Posisyon BUKAS: #$posisyonBukas",
                                    modifier = Modifier.padding(
                                        horizontal = 12.dp,
                                        vertical = 6.dp
                                    ),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        /*
         * Fase 4B: sino ang #1 at sino ang kasunod sa pila ng ruta -
         * para alam ng driver kung sino ang mauuna at sino ang sumusunod.
         */
        if (queueLeadName.isNotBlank() || queueLeadPlate.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = if (route.isNotBlank()) {
                            "Pila Ngayon ($route)"
                        } else {
                            "Pila Ngayon"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = EnerjeepBlue,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "#1",
                                modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 4.dp
                                ),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = listOf(
                                queueLeadName,
                                queueLeadPlate
                            ).filter { it.isNotBlank() }
                                .joinToString(" • "),
                            fontSize = 14.sp,
                            color = Color.Black,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (queueNextName.isNotBlank() ||
                        queueNextPlate.isNotBlank()
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFFECEFF3),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Kasunod",
                                    modifier = Modifier.padding(
                                        horizontal = 10.dp,
                                        vertical = 4.dp
                                    ),
                                    color = Color(0xFF5A6472),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = listOf(
                                    queueNextName,
                                    queueNextPlate
                                ).filter { it.isNotBlank() }
                                    .joinToString(" • "),
                                fontSize = 14.sp,
                                color = Color.Gray,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Incident/Warning Card
        if (openIncidentCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Aktibong Insidente",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(
                            color = WarningOrangeLight,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "$openIncidentCount",
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 6.dp
                                ),
                                color = WarningOrange,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = WarningOrangeLight
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = WarningOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeIncidentTitle ?: "Insidente",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color.Black,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                // The incident can belong to another driver,
                                // so the plate of the incident is shown.
                                // The plate of the signed-in driver is used
                                // when the record has no plate.
                                text = "Plaka " + (activeIncidentPlate?.takeIf { it.isNotBlank() }
                                    ?: plateNumber),
                                fontSize = 13.sp,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = activeIncidentDate ?: "",
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        } else if (openIncidentCount == 0 && activeIncidentTitle == null) {
            // No open incidents: show the count and invite to report one.
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = WarningOrangeLight),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningOrange,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        text = "Walang bukas na insidente.",
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        color = WarningOrange
                    )
                }
            }
        }

        // Malinaw na puwang sa pagitan ng insidente card at ng button.
        Spacer(modifier = Modifier.height(24.dp))

        /*
         * "Tapos Na Bumyahe" - aalis ang driver sa pila (Kasaysayan).
         * May confirm para walang aksidenteng pindot. Kapag tapos na
         * ang biyahe ngayon, pula ang button at lalabas sa ibaba ang
         * buong kita ng araw.
         */
        if (isInQueue || travelEnded) {
            val doneToday = travelEnded && !isInQueue

            Button(
                onClick = {
                    if (!doneToday) {
                        showTaposConfirm = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(24.dp),
                enabled = !doneToday,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SuccessGreen,
                    disabledContainerColor = ErrorRed,
                    disabledContentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Tapos Na Bumyahe",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            /*
             * Pagkatapos pindutin ang "Tapos Na Bumyahe": lalabas dito
             * ang buong kita ng driver sa buong araw - hindi na
             * kailangang pumunta sa Kita tab.
             */
            if (travelEnded) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 2.dp
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Buong Kita Ngayon",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "₱" + String.format(
                                "%,.0f",
                                dailyEarnings ?: 0.0
                            ),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        /*
         * Emergency button: opens the dialog with 911 and the emergency
         * contact saved on the driver's own profile. Drivers used to
         * have no way to reach these numbers at all.
         */
        Button(
            onClick = onEmergencyClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ErrorRed
            )
        ) {
            SirenIcon(
                modifier = Modifier.size(20.dp),
                color = Color.White
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Emergency",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/*
 * Fase 7: listahan ng mga dispatch alert (bell). In-memory lang -
 * habang bukas ang app (walang push/Functions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DispatchAlertsSheet(
    alerts: List<DriverDispatchAlert>,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Mga Alerto",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Isara")
                }
            }
            Spacer(Modifier.height(16.dp))
            if (alerts.isEmpty()) {
                Text(
                    "Wala pang alerto. Ipapakita dito kapag na-dispatch ka.",
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            } else {
                alerts.forEach { alert ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = LightGrayBackground
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = null,
                                tint = EnerjeepBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    "Na-dispatch: " +
                                            listOf(
                                                alert.plateNumber,
                                                alert.route
                                            ).filter { it.isNotBlank() }
                                                .joinToString(" • "),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    alert.time,
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

package com.mobile.enerjeep.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Phone
import androidx.compose.ui.res.painterResource
import com.mobile.enerjeep.R
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.navigation.AppNavigator
import com.mobile.enerjeep.navigation.Screen
import com.mobile.enerjeep.ui.components.*
import com.mobile.enerjeep.ui.theme.*
import androidx.compose.ui.window.Dialog
import com.mobile.enerjeep.ui.queue.QueueScreen
import com.mobile.enerjeep.ui.ledger.LedgerScreen
import com.mobile.enerjeep.ui.incident.IncidentScreen
import com.mobile.enerjeep.ui.incident.IncidentReportFormScreen
import com.mobile.enerjeep.ui.report.ReportScreen
import com.mobile.enerjeep.ui.profile.ProfileScreen
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.mobile.enerjeep.ui.components.ToastFeedback

enum class HomeTab {
    Home, Pila, Talaan, Insidente, MgaUlat, Profile
}

@Composable
fun HomeScreen(
    appNavigator: AppNavigator,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val isAdmin = viewModel.isAdmin()
    val userProfile = viewModel.userProfile

    if (isAdmin) {
        var selectedTab by remember { mutableStateOf(HomeTab.Home) }
        var showIncidentForm by remember { mutableStateOf(false) }
        var showEmergencyDialog by remember { mutableStateOf(false) }
        var openLedgerForm by remember { mutableStateOf(false) }

        val activeJeeps by viewModel.activeJeepsCount
            .collectAsStateWithLifecycle()

        val totalJeeps by viewModel.totalJeepsCount
            .collectAsStateWithLifecycle()

        val totalSales by viewModel.totalSales
            .collectAsStateWithLifecycle()

        val openIncidentCount by viewModel.openIncidentCount
            .collectAsStateWithLifecycle()

        val driverContacts by viewModel.driverContacts
            .collectAsStateWithLifecycle()

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                NavigationBar(
                    containerColor = Color.White,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == HomeTab.Home,
                        onClick = {
                            selectedTab = HomeTab.Home
                            showIncidentForm = false
                        },
                        icon = {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = "Home"
                            )
                        },
                        label = {
                            Text("Home", fontSize = 10.sp)
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = EnerjeepBlue,
                            selectedTextColor = EnerjeepBlue,
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray,
                            indicatorColor = Color.Transparent
                        )
                    )

                    NavigationBarItem(
                        selected = selectedTab == HomeTab.Pila,
                        onClick = {
                            selectedTab = HomeTab.Pila
                            showIncidentForm = false
                        },
                        icon = {
                            ListIcon(modifier = Modifier.size(24.dp))
                        },
                        label = {
                            Text("Pila", fontSize = 10.sp)
                        }
                    )

                    NavigationBarItem(
                        selected = selectedTab == HomeTab.Talaan,
                        onClick = {
                            selectedTab = HomeTab.Talaan
                            showIncidentForm = false
                        },
                        icon = {
                            DocumentIcon(modifier = Modifier.size(24.dp))
                        },
                        label = {
                            Text("Talaan", fontSize = 10.sp)
                        }
                    )

                    NavigationBarItem(
                        selected = selectedTab == HomeTab.Insidente,
                        onClick = {
                            selectedTab = HomeTab.Insidente
                        },
                        icon = {
                            WarningIcon(modifier = Modifier.size(24.dp))
                        },
                        label = {
                            Text("Insidente", fontSize = 10.sp)
                        }
                    )

                    NavigationBarItem(
                        selected = selectedTab == HomeTab.MgaUlat,
                        onClick = {
                            selectedTab = HomeTab.MgaUlat
                            showIncidentForm = false
                        },
                        icon = {
                            DocumentIcon(modifier = Modifier.size(24.dp))
                        },
                        label = {
                            Text("Mga Ulat", fontSize = 10.sp)
                        }
                    )

                    NavigationBarItem(
                        selected = selectedTab == HomeTab.Profile,
                        onClick = {
                            selectedTab = HomeTab.Profile
                            showIncidentForm = false
                        },
                        icon = {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Profile"
                            )
                        },
                        label = {
                            Text("Profile", fontSize = 10.sp)
                        },
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
                    HomeTab.Home -> {
                        HomeContent(
                            firstName = userProfile?.firstName ?: "Admin",
                            activeJeeps = activeJeeps,
                            totalJeeps = totalJeeps,
                            totalSales = totalSales,
                            openIncidentCount = openIncidentCount,

                            // Changed: open Account Management first.
                            onCreateAccountClick = {
                                appNavigator.navigateTo(
                                    Screen.AccountManagement
                                )
                            },

                            onTerminalFeeClick = {
                                selectedTab = HomeTab.Talaan
                                openLedgerForm = true
                            },

                            onReportIncidentClick = {
                                selectedTab = HomeTab.Insidente
                                showIncidentForm = true
                            },

                            onViewQueueClick = {
                                selectedTab = HomeTab.Pila
                            },

                            onEmergencyClick = {
                                showEmergencyDialog = true
                            }
                        )
                    }

                    HomeTab.Pila -> {
                        QueueScreen()
                    }

                    HomeTab.Talaan -> {
                        LedgerScreen(
                            shouldOpenForm = openLedgerForm,
                            onFormHandled = {
                                openLedgerForm = false
                            }
                        )
                    }

                    HomeTab.Insidente -> {
                        if (showIncidentForm) {
                            IncidentReportFormScreen(
                                onBack = {
                                    showIncidentForm = false
                                }
                            )
                        } else {
                            IncidentScreen(
                                onReportClick = {
                                    showIncidentForm = true
                                }
                            )
                        }
                    }

                    HomeTab.MgaUlat -> {
                        ReportScreen()
                    }

                    HomeTab.Profile -> {
                        ProfileScreen()
                    }
                }

                if (showEmergencyDialog) {
                    EmergencyDialog(
                        onDismiss = {
                            showEmergencyDialog = false
                        },
                        emergencyContactName =
                            userProfile?.emergencyContactName,
                        emergencyContactNumber =
                            userProfile?.emergencyContactNumber,
                        driverContacts = driverContacts
                    )
                }
            }
        }
    } else {
        DriverContent(
            appNavigator = appNavigator,
            emergencyContactName =
                userProfile?.emergencyContactName,
            emergencyContactNumber =
                userProfile?.emergencyContactNumber
        )
    }
}

@Composable
fun HomeContent(
    firstName: String,
    activeJeeps: Int,
    totalJeeps: Int,
    totalSales: Double,
    openIncidentCount: Int,
    onCreateAccountClick: () -> Unit,
    onTerminalFeeClick: () -> Unit,
    onReportIncidentClick: () -> Unit,
    onViewQueueClick: () -> Unit,
    onEmergencyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
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
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Musta, $firstName!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Text(
            text = "Buod ng kooperatiba sa isang tingin.",
            fontSize = 16.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Kabuuang Benta",
                value = DashboardSummary.formatPeso(totalSales),
                icon = {
                    SalesIcon(
                        modifier = Modifier.size(24.dp),
                        color = EnerjeepBlue
                    )
                },
                iconBackground = Color.White
            )

            Spacer(modifier = Modifier.width(16.dp))

            SummaryCard(
                modifier = Modifier.weight(1f),
                title = "Aktibong Jeep",
                value = "$activeJeeps/$totalJeeps",
                icon = {
                    TruckIcon(
                        modifier = Modifier.size(24.dp),
                        color = SuccessGreen
                    )
                },
                iconBackground = SuccessGreenLight
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        SummaryCard(
            modifier = Modifier.fillMaxWidth(0.5f),
            title = "Insidente",
            value = openIncidentCount.toString(),
            icon = {
                WarningIcon(
                    modifier = Modifier.size(24.dp),
                    color = WarningOrange
                )
            },
            iconBackground = WarningOrangeLight
        )

        Spacer(modifier = Modifier.height(24.dp))

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
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TruckIcon(
                        modifier = Modifier.size(20.dp),
                        color = EnerjeepBlue
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Buod ng Tracking",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aktibong Jeep Ngayon",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    Text(
                        text = activeJeeps.toString(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Mabilis na Aksyon",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Black
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            QuickActionButton(
                modifier = Modifier.weight(1f),
                text = "Gumawa ng Account",
                icon = {
                    Text(
                        "+",
                        fontSize = 20.sp,
                        color = SuccessGreen,
                        fontWeight = FontWeight.Bold
                    )
                },
                backgroundColor = SuccessGreenLight,
                contentColor = SuccessGreen,
                onClick = onCreateAccountClick
            )

            Spacer(modifier = Modifier.width(12.dp))

            QuickActionButton(
                modifier = Modifier.weight(1f),
                text = "Itala ang Terminal Fee",
                icon = {
                    DocumentIcon(
                        modifier = Modifier.size(20.dp),
                        color = Color.Black
                    )
                },
                backgroundColor = Color.White,
                contentColor = Color.Black,
                onClick = onTerminalFeeClick
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            QuickActionButton(
                modifier = Modifier.weight(1f),
                text = "I-report ang Insidente",
                icon = {
                    WarningIcon(
                        modifier = Modifier.size(20.dp),
                        color = Color.Black
                    )
                },
                backgroundColor = Color.White,
                contentColor = Color.Black,
                onClick = onReportIncidentClick
            )

            Spacer(modifier = Modifier.width(12.dp))

            QuickActionButton(
                modifier = Modifier.weight(1f),
                text = "Tingnan ang Pila",
                icon = {
                    ListIcon(
                        modifier = Modifier.size(20.dp),
                        color = Color.Black
                    )
                },
                backgroundColor = Color.White,
                contentColor = Color.Black,
                onClick = onViewQueueClick
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        QuickActionButton(
            modifier = Modifier.fillMaxWidth(0.5f),
            text = "Emergency",
            icon = {
                SirenIcon(
                    modifier = Modifier.size(20.dp),
                    color = ErrorRed
                )
            },
            backgroundColor = ErrorRedLight,
            contentColor = ErrorRed,
            onClick = onEmergencyClick
        )
    }
}

@Composable
fun EmergencyDialog(
    onDismiss: () -> Unit,
    emergencyContactName: String? = null,
    emergencyContactNumber: String? = null,
    driverContacts: List<DriverEmergencyContact> = emptyList()
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Emergency",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = ErrorRed
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.Black
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Pindutin ang kontak para tawagan agad.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(24.dp))

                EmergencyContactButton(
                    title = "Ambulansya / Pulis",
                    number = "911",
                    icon = Icons.Default.LocalHospital,
                    bgColor = ErrorRed,
                    textColor = Color.White
                )

                /*
                 * The emergency contact saved on the profile of the
                 * account (the person to call when the driver is in an
                 * accident). This used to be missing entirely from the
                 * dialog even though every account stores it.
                 */
                if (!emergencyContactNumber.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    EmergencyContactButton(
                        title = emergencyContactName
                            ?.takeIf { it.isNotBlank() }
                            ?: "Emergency Contact",
                        number = emergencyContactNumber,
                        icon = Icons.Default.Person,
                        bgColor = EnerjeepBlue,
                        textColor = Color.White
                    )
                }

                /*
                 * The emergency contacts that were entered when the
                 * driver accounts were created. On the admin dashboard
                 * the dialog lists them, so the admin can call the
                 * contact of any driver right away.
                 */
                driverContacts.forEach { contact ->
                    Spacer(modifier = Modifier.height(12.dp))

                    EmergencyContactButton(
                        title = contact.contactName,
                        subtitle = listOf(
                            contact.driverName,
                            contact.plateNumber
                        ).filter { it.isNotBlank() }
                            .joinToString(" • "),
                        number = contact.contactNumber,
                        icon = Icons.Default.Person,
                        bgColor = EnerjeepBlue,
                        textColor = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                EmergencyContactButton(
                    title = "Dispatch ng Koop",
                    number = "+63 (2) 1234-5678",
                    icon = Icons.Default.HeadsetMic,
                    bgColor = EnerjeepBlue,
                    textColor = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                EmergencyContactButton(
                    title = "Pamunuan",
                    number = "+63 917 500 0000",
                    icon = Icons.Default.Phone,
                    bgColor = Color(0xFFFBBF24),
                    textColor = Color.Black
                )

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        FieldBorderColor
                    )
                ) {
                    Text("Isara", color = Color.Black)
                }
            }
        }
    }
}

@Composable
fun EmergencyContactButton(
    title: String,
    number: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    bgColor: Color,
    textColor: Color,
    subtitle: String? = null
) {
    val context = LocalContext.current

    var pendingNumber by remember {
        mutableStateOf<String?>(null)
    }

    // Fallback: opens the phone dialer with the number already filled in.
    // The user only has to press the green Call button.
    fun openDialer(cleanNumber: String) {
        try {
            val intent = Intent(
                Intent.ACTION_DIAL,
                Uri.fromParts("tel", cleanNumber, null)
            )

            context.startActivity(intent)

            ToastFeedback.show(
                "Binuksan ang dialer para sa $cleanNumber. Pindutin ang Call."
            )
        } catch (e: ActivityNotFoundException) {
            ToastFeedback.show(
                "Walang available na phone dialer sa device."
            )
        } catch (e: Exception) {
            ToastFeedback.show(
                "Hindi mabuksan ang dialer."
            )
        }
    }

    fun startDirectCall(cleanNumber: String) {
        try {
            val intent = Intent(
                Intent.ACTION_CALL,
                Uri.fromParts("tel", cleanNumber, null)
            )

            context.startActivity(intent)

            // Confirms that the call action was launched,
            // not that the recipient answered.
            ToastFeedback.show(
                "Tumatawag sa $cleanNumber..."
            )
        } catch (e: Exception) {
            // SecurityException (no CALL_PHONE permission),
            // ActivityNotFoundException or any other failure:
            // always fall back to the dialer so the person can
            // still place the emergency call.
            openDialer(cleanNumber)
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->

            val requestedNumber = pendingNumber

            if (requestedNumber != null) {
                if (granted) {
                    startDirectCall(requestedNumber)
                } else {
                    openDialer(requestedNumber)
                }
            }

            pendingNumber = null
        }

    Button(
        onClick = {
            val cleanNumber = number.filter {
                it.isDigit() || it == '+'
            }

            if (cleanNumber.isBlank()) {
                ToastFeedback.show(
                    "Walang valid na contact number."
                )
                return@Button
            }

            // Android does not let a normal app place an emergency call with
            // ACTION_CALL (the system swallows it for numbers such as 911),
            // so the dialer never opened. For emergency numbers always use
            // ACTION_DIAL: the dialer opens with the number filled in and the
            // person presses Call. No CALL_PHONE permission is needed.
            if (isEmergencyServiceNumber(cleanNumber)) {
                openDialer(cleanNumber)
                return@Button
            }

            val supportsTelephony =
                context.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_TELEPHONY
                )

            if (!supportsTelephony) {
                openDialer(cleanNumber)
                return@Button
            }

            val hasPermission =
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CALL_PHONE
                ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                startDirectCall(cleanNumber)
            } else {
                pendingNumber = cleanNumber

                permissionLauncher.launch(
                    Manifest.permission.CALL_PHONE
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor
        ),
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(
            horizontal = 20.dp
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = textColor
                )

                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = textColor.copy(alpha = 0.85f)
                    )
                }

                Text(
                    text = number,
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.9f)
                )
            }
        }
    }
}

private val EMERGENCY_SERVICE_NUMBERS = setOf("911", "112", "117", "999")

private fun isEmergencyServiceNumber(number: String): Boolean =
    number in EMERGENCY_SERVICE_NUMBERS

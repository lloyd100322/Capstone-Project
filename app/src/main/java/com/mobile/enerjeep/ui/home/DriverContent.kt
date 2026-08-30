package com.mobile.enerjeep.ui.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.mobile.enerjeep.R
import com.mobile.enerjeep.navigation.AppNavigator
import com.mobile.enerjeep.ui.components.*
import com.mobile.enerjeep.ui.theme.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.ui.schedule.ScheduleScreen
import com.mobile.enerjeep.ui.incident.DriverIncidentScreen
import com.mobile.enerjeep.ui.kita.KitaScreen
import com.mobile.enerjeep.ui.profile.DriverProfileScreen

/**
 * Created by Bry1337 on 8/9/26.
 * Copyright (c) 2026. All rights reserved.
 */

// 1. Driver-Specific Tabs
enum class DriverTab {
    Home, Iskedyul, Insidente, Kita, Profile
}

// 2. Master Controller for the Driver Role
@Composable
fun DriverContent(
    appNavigator: AppNavigator,
    viewModel: DriverViewModel = hiltViewModel()
) {
    var selectedTab by remember { mutableStateOf(DriverTab.Home) }
    var showIncidentForm by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                    selected = selectedTab == DriverTab.Iskedyul,
                    onClick = {
                        selectedTab = DriverTab.Iskedyul
                        showIncidentForm = false
                    },
                    icon = { DocumentIcon(modifier = Modifier.size(24.dp)) }, // Reusing custom icon
                    label = { Text("Iskedyul", fontSize = 10.sp) }
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
                    icon = { SalesIcon(modifier = Modifier.size(24.dp), color = if (selectedTab == DriverTab.Kita) EnerjeepBlue else Color.Gray) },
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
                        totalInQueue = uiState.totalInQueue,
                        queueStatus = uiState.queueStatus,
                        activeIncidentTitle = uiState.activeIncidentTitle,
                        activeIncidentDate = uiState.activeIncidentDate
                    )
                }
                DriverTab.Iskedyul -> {
                    ScheduleScreen()
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
    totalInQueue: Int,
    queueStatus: String,
    activeIncidentTitle: String?,
    activeIncidentDate: String?
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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

        Spacer(modifier = Modifier.height(32.dp))

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
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Posisyon $queuePosition ng $totalInQueue sa pila",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Incident/Warning Card
        if (activeIncidentTitle != null && activeIncidentDate != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
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
                        Column {
                            Text(
                                text = activeIncidentTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Plaka $plateNumber",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = activeIncidentDate,
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                    Surface(
                        color = WarningOrangeLight,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Babala",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = WarningOrange,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
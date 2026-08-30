package com.mobile.enerjeep.ui.incident

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.R
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.LightGrayBackground
import com.mobile.enerjeep.ui.theme.SuccessGreen
import com.mobile.enerjeep.ui.theme.WarningOrange
import com.mobile.enerjeep.ui.theme.WarningOrangeLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverIncidentScreen(
    viewModel: DriverIncidentViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(LightGrayBackground)
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

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
            Text(text = "Enerjeep", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(text = "Insidente", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)

        Spacer(modifier = Modifier.height(24.dp))

        // Summary Cards
        Row(modifier = Modifier.fillMaxWidth()) {
            IncidentSummaryCard(
                modifier = Modifier.weight(1f),
                count = uiState.openCount,
                label = "Bukas",
                iconColor = WarningOrange,
                borderColor = WarningOrange,
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = WarningOrange, modifier = Modifier.size(24.dp)) }
            )
            Spacer(modifier = Modifier.width(16.dp))
            IncidentSummaryCard(
                modifier = Modifier.weight(1f),
                count = uiState.resolvedCount,
                label = "Naayos",
                iconColor = SuccessGreen,
                borderColor = SuccessGreen,
                icon = { Icon(Icons.Outlined.CheckCircleOutline, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp)) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Search Bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Hanapin ayon sa uri, plaka, o lokasyon", color = Color.Gray, fontSize = 14.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                unfocusedBorderColor = Color.LightGray,
                focusedBorderColor = EnerjeepBlue
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DriverTabButton(
                modifier = Modifier.weight(1f),
                text = "Kasalukuyan",
                isSelected = uiState.selectedTab == IncidentTabType.KASALUKUYAN,
                onClick = { viewModel.onTabSelected(IncidentTabType.KASALUKUYAN) }
            )
            DriverTabButton(
                modifier = Modifier.weight(1f),
                text = "Kasaysayan",
                isSelected = uiState.selectedTab == IncidentTabType.KASAYSAYAN,
                onClick = { viewModel.onTabSelected(IncidentTabType.KASAYSAYAN) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Incident List
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(uiState.filteredIncidents) { incident ->
                DriverIncidentCard(
                    incident = incident,
                    onViewClick = { viewModel.selectIncident(incident) }
                )
            }
        }
    }

    // Details Bottom Sheet
    if (uiState.selectedIncident != null) {
        IncidentDetailBottomSheet(
            incident = uiState.selectedIncident!!,
            onDismiss = { viewModel.clearSelectedIncident() }
        )
    }
}

@Composable
fun IncidentSummaryCard(
    modifier: Modifier = Modifier,
    count: Int,
    label: String,
    iconColor: Color,
    borderColor: Color,
    icon: @Composable () -> Unit
) {
    Card(
        modifier = modifier.height(100.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxHeight().width(4.dp).background(borderColor))
            Column(
                modifier = Modifier.padding(16.dp).fillMaxSize(),
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = iconColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) { icon() }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(text = count.toString(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = label, fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
        }
    }
}

@Composable
fun DriverTabButton(
    modifier: Modifier = Modifier,
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(40.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color.White else Color.Transparent,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = if (isSelected) Color.Black else Color.Gray,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun DriverIncidentCard(
    incident: DriverIncident,
    onViewClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(modifier = Modifier.weight(1f)) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = if (incident.isResolved) SuccessGreen.copy(alpha = 0.1f) else WarningOrangeLight
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (incident.isResolved) SuccessGreen else WarningOrange,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = incident.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = incident.plateNumber, fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(text = incident.description, fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.AccessTime, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = incident.date, fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }

                Surface(
                    color = if (incident.isResolved) SuccessGreen.copy(alpha = 0.1f) else WarningOrangeLight,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (incident.isResolved) "Naayos" else "Bukas",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (incident.isResolved) SuccessGreen else WarningOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onViewClick,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.Visibility, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Tingnan", color = Color.Black, fontSize = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncidentDetailBottomSheet(
    incident: DriverIncident,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        dragHandle = null // Removes the top drag handle pill to match the specific design
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Detalye ng Insidente",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.Black)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title and Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = incident.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Surface(
                    color = if (incident.isResolved) SuccessGreen.copy(alpha = 0.1f) else WarningOrangeLight,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (incident.isResolved) "Naayos" else "Bukas",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (incident.isResolved) SuccessGreen else WarningOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Detail Rows
            DetailRow(icon = Icons.Default.DirectionsBus, label = "Plaka ng Sasakyan", value = incident.plateNumber)
            Spacer(modifier = Modifier.height(16.dp))
            DetailRow(icon = Icons.Default.LocationOn, label = "Lokasyon", value = incident.description)
            Spacer(modifier = Modifier.height(16.dp))
            DetailRow(icon = Icons.Default.CalendarToday, label = "Petsa at Oras", value = incident.date)
            Spacer(modifier = Modifier.height(16.dp))
            DetailRow(icon = Icons.Default.AccountBalanceWallet, label = "Multiya", value = "₱${String.format("%,.0f", incident.multiya)}")
            Spacer(modifier = Modifier.height(16.dp))
            DetailRow(icon = Icons.Default.Description, label = "Naipadala sa Driver", value = if (incident.isSentToDriver) "Oo" else "Hindi")

            Spacer(modifier = Modifier.height(32.dp))

            // Close Button
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(24.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
            ) {
                Text(text = "Isara", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, color = Color.Gray, fontSize = 14.sp)
        }
        Text(text = value, color = Color.Black, fontWeight = FontWeight.Medium, fontSize = 14.sp)
    }
}
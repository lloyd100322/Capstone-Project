package com.mobile.enerjeep.ui.kita

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mobile.enerjeep.R
import com.mobile.enerjeep.ui.components.HistoryDateFilterBar
import com.mobile.enerjeep.ui.components.HistoryFilterState
import com.mobile.enerjeep.ui.components.filterHistoryRecords
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.LightGrayBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitaScreen(viewModel: KitaViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var historyFilter by remember { mutableStateOf(HistoryFilterState()) }
    // Make sure the listeners belong to the driver who is signed in right now.
    LaunchedEffect(Unit) { viewModel.refreshIdentity() }
    val isHistory = uiState.selectedTab == KitaTabType.KASAYSAYAN
    val visibleRecords = if (isHistory) filterHistoryRecords(
        records = uiState.filteredRecords,
        dateText = { it.date },
        filter = historyFilter,
        createdAtMillis = { it.createdAtMillis.takeIf { value -> value > 0L } }
    ) else uiState.filteredRecords

    Column(Modifier.fillMaxSize().background(LightGrayBackground).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.app_icon), contentDescription = "Enerjeep App Logo", modifier = Modifier.size(60.dp))
            Spacer(Modifier.width(12.dp))
            Text("Enerjeep", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Spacer(Modifier.height(32.dp))
        Text("Aking Kita", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(Modifier.height(24.dp))
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = EnerjeepBlue), shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Netong Kita Buwan na To", color = Color.White.copy(alpha = .8f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("₱${String.format("%,.0f", uiState.netEarnings)}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = .2f))
                Spacer(Modifier.height(16.dp))
                SummaryMoneyRow("Kita", uiState.totalKita)
                Spacer(Modifier.height(8.dp))
                SummaryMoneyRow("Bayad", uiState.totalBayad)
                Spacer(Modifier.height(8.dp))
                SummaryMoneyRow("Multa", uiState.totalMultiya)
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KitaTabButton(Modifier.weight(1f), "Kasalukuyan", uiState.selectedTab == KitaTabType.KASALUKUYAN) { viewModel.onTabSelected(KitaTabType.KASALUKUYAN) }
            KitaTabButton(Modifier.weight(1f), "Kasaysayan", isHistory) { viewModel.onTabSelected(KitaTabType.KASAYSAYAN) }
        }
        if (isHistory) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Hanapin ayon sa plaka o driver", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                shape = RoundedCornerShape(24.dp), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, unfocusedBorderColor = Color.LightGray, focusedBorderColor = EnerjeepBlue)
            )
            HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        Spacer(Modifier.height(16.dp))
        if (visibleRecords.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(if (!isHistory) "Wala pang kita buwan na to." else "Walang record sa napiling filter.", color = Color.Gray, fontSize = 14.sp)
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(visibleRecords, key = { it.id }) { record ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(record.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                                Spacer(Modifier.height(2.dp))
                                Text(record.date, fontSize = 12.sp, color = Color.Gray)
                            }
                            Text("₱${String.format("%,.0f", record.amount)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (record.type == "Kita") Color(0xFF10B981) else Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMoneyRow(label: String, amount: Double) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.White.copy(alpha = .8f), fontSize = 14.sp)
        Text("₱${String.format("%,.0f", amount)}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun KitaTabButton(modifier: Modifier = Modifier, text: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(modifier = modifier.height(40.dp).clickable { onClick() }, shape = RoundedCornerShape(20.dp), color = if (isSelected) Color.White else Color.Transparent, border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)) {
        Box(contentAlignment = Alignment.Center) {
            Text(text, color = if (isSelected) Color.Black else Color.Gray, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 14.sp)
        }
    }
}

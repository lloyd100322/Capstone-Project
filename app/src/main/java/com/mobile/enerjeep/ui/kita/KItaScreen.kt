package com.mobile.enerjeep.ui.kita

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.mobile.enerjeep.ui.components.HistoryPeriod
import com.mobile.enerjeep.ui.components.filterHistoryRecords
import com.mobile.enerjeep.ui.theme.EnerjeepBlue
import com.mobile.enerjeep.ui.theme.SuccessGreen
import com.mobile.enerjeep.ui.theme.LightGrayBackground
import com.mobile.enerjeep.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KitaScreen(viewModel: KitaViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    /*
     * Default: "Lahat ng petsa" - laging nakikita ang BUONG records ng
     * driver. (Dati ay "Ngayon" ang default kaya pagkalipas ng araw ay
     * "nawawala" ang kita sa listahan - akala ng driver ay nabura.)
     * Pwede pa ring piliin ang "Ngayon" sa date filter.
     */
    var historyFilter by remember {
        mutableStateOf(HistoryFilterState(period = HistoryPeriod.ALL))
    }
    // Fase 5: plate filter (kapalit ng nawalang search box)
    var plateFilter by remember { mutableStateOf("") }
    // Make sure the listeners belong to the driver who is signed in right now.
    LaunchedEffect(Unit) { viewModel.refreshIdentity() }
    /*
     * Wala nang "Ngayon | Kasaysayan" tabs - ang date filter na lang ang
     * natitira at default nito ay "Ngayon" (HistoryFilterState). Ang
     * summary card ay laging batay sa nakikitang listahan, kaya tugma
     * ang mga numero sa mga record na ipinapakita.
     */
    val filteredByDate = filterHistoryRecords(
        records = uiState.records,
        dateText = { it.date },
        filter = historyFilter,
        createdAtMillis = { it.createdAtMillis.takeIf { value -> value > 0L } }
    )
    val finalRecords = filteredByDate.filter {
        plateFilter.isBlank() ||
                it.title.contains(plateFilter, ignoreCase = true)
    }
    // Ang kita = kabuuan ng mga dispatch earnings. WALANG BAWAS na
    // kahit ano - walang gastos, multa, o donasyon na binabawas dito.
    val total = remember(finalRecords) { KitaHistory.total(finalRecords) }

    Column(Modifier.fillMaxSize().background(LightGrayBackground).verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
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
                Text(if (historyFilter.period == HistoryPeriod.TODAY) "Malinis na Kita Ngayon" else "Malinis na Kita", color = Color.White.copy(alpha = .8f), fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("₱${String.format("%,.0f", total)}", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(24.dp))

        /*
         * Ang charts/statistics ay inilipat sa Mga Ulat ng ADMIN.
         * Dito sa driver Kita: simpleng summary + listahan ng records.
         * Plate filter + date filter (default "Ngayon") na lang ang
         * nasa itaas ng listahan - walang tabs.
         */
        Spacer(Modifier.height(16.dp))
        /*
         * Fase 5: tanggal ang SEARCH BOX - plate filter + date filter
         * na lang (parehong logic sa Ulat ng Kita).
         */
        val plateOptions = remember(uiState.records) {
            uiState.records
                .map { it.title.substringAfter("•", "").trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
        var expandedPlate by remember { mutableStateOf(false) }
        if (plateOptions.size > 1) {
            ExposedDropdownMenuBox(
                expanded = expandedPlate,
                onExpandedChange = { expandedPlate = !expandedPlate }
            ) {
                OutlinedTextField(
                    value = plateFilter.ifBlank { "Lahat ng plaka" },
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(
                            expanded = expandedPlate
                        )
                    },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    singleLine = true
                )
                ExposedDropdownMenu(
                    expanded = expandedPlate,
                    onDismissRequest = { expandedPlate = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Lahat ng plaka") },
                        onClick = {
                            plateFilter = ""
                            expandedPlate = false
                        }
                    )
                    plateOptions.forEach { plate ->
                        DropdownMenuItem(
                            text = { Text(plate) },
                            onClick = {
                                plateFilter = plate
                                expandedPlate = false
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        HistoryDateFilterBar(historyFilter, { historyFilter = it }, Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        if (finalRecords.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (historyFilter.period == HistoryPeriod.TODAY)
                        "Wala pang kita ngayon. Piliin ang \"Lahat ng petsa\" para makita ang nakaraang kita."
                    else
                        "Walang record sa napiling filter.",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            /*
             * Buong page ang scrollable (hindi lang ang listahan) kaya
             * hindi nacucut ang mga record sa ilalim ng navigation bar.
             */
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                finalRecords.forEach { record ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(1.dp)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(record.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.Black)
                                Spacer(Modifier.height(2.dp))
                                Text(record.date, fontSize = 12.sp, color = TextSecondary)
                            }
                            // Lahat ng record ay kita (dispatch earnings) -
                            // walang pulang bawas sa listahan na ito.
                            Text("₱${String.format("%,.0f", record.amount)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SuccessGreen)
                        }
                    }
                }
            }
        }
    }
}


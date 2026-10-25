package com.mobile.enerjeep.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

/** Shared date filters used by every history/kasaysayan list. */
enum class HistoryPeriod(val label: String) {
    ALL("Lahat ng petsa"),
    TODAY("Ngayon"),
    THIS_WEEK("Ngayong linggo"),
    LAST_WEEK("Nakaraang linggo"),
    THIS_MONTH("Ngayong buwan"),
    LAST_MONTH("Nakaraang buwan"),
    THIS_YEAR("Ngayong taon"),
    LAST_YEAR("Nakaraang taon"),
    SPECIFIC_DATE("Pumili ng petsa")
}

data class HistoryFilterState(
    // The system-wide default filter is "Ngayon", not "Lahat ng petsa".
    val period: HistoryPeriod = HistoryPeriod.TODAY,
    val selectedDate: LocalDate? = null,
    val newestFirst: Boolean = true
)

/** Handles the date formats used by existing records without changing Firestore data. */
fun parseHistoryDate(dateText: String?): LocalDate? {
    if (dateText.isNullOrBlank()) return null
    val patterns = listOf(
        "MMM dd, yyyy • hh:mm a", "MMM d, yyyy • h:mm a",
        "MMM dd, yyyy hh:mm a", "MMM d, yyyy h:mm a",
        "MMM dd, yyyy", "MMM d, yyyy", "yyyy-MM-dd", "yyyy/MM/dd",
        "MM/dd/yyyy", "M/d/yyyy", "dd/MM/yyyy"
    )
    for (locale in listOf(Locale.getDefault(), Locale.ENGLISH).distinct()) {
        for (pattern in patterns) {
            try {
                val formatter = SimpleDateFormat(pattern, locale).apply { isLenient = false }
                val parsed = formatter.parse(dateText.trim()) ?: continue
                return Instant.ofEpochMilli(parsed.time).atZone(ZoneId.systemDefault()).toLocalDate()
            } catch (_: Exception) {
                // Try the next legacy date format.
            }
        }
    }
    return null
}

/** Filter/sort loaded records by a timestamp when available, otherwise their displayed date. */
fun <T> filterHistoryRecords(
    records: List<T>,
    dateText: (T) -> String,
    filter: HistoryFilterState,
    createdAtMillis: (T) -> Long? = { null }
): List<T> {
    val today = LocalDate.now()
    val firstDay = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val thisWeekStart = today.with(TemporalAdjusters.previousOrSame(firstDay))
    val lastWeekStart = thisWeekStart.minusWeeks(1)
    val thisMonthStart = today.withDayOfMonth(1)
    val lastMonthStart = thisMonthStart.minusMonths(1)

    fun dateOf(item: T): LocalDate? {
        val millis = createdAtMillis(item)
        if (millis != null && millis > 0L) {
            return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        }
        return parseHistoryDate(dateText(item))
    }

    val matching = records.filter { item ->
        val date = dateOf(item)
        if (filter.period == HistoryPeriod.ALL) true
        else if (date == null) false
        else when (filter.period) {
            HistoryPeriod.ALL -> true
            HistoryPeriod.TODAY -> date == today
            HistoryPeriod.THIS_WEEK -> !date.isBefore(thisWeekStart) && date.isBefore(thisWeekStart.plusWeeks(1))
            HistoryPeriod.LAST_WEEK -> !date.isBefore(lastWeekStart) && date.isBefore(thisWeekStart)
            HistoryPeriod.THIS_MONTH -> date.year == today.year && date.month == today.month
            HistoryPeriod.LAST_MONTH -> !date.isBefore(lastMonthStart) && date.isBefore(thisMonthStart)
            HistoryPeriod.THIS_YEAR -> date.year == today.year
            HistoryPeriod.LAST_YEAR -> date.year == today.year - 1
            HistoryPeriod.SPECIFIC_DATE -> filter.selectedDate != null && date == filter.selectedDate
        }
    }

    // Incoming lists are newest-first. A stable sort on the day keeps that
    // order for records on the same day (the old sortedBy + reversed() flipped it).
    return if (filter.newestFirst) {
        matching.sortedByDescending { item -> dateOf(item) ?: LocalDate.MIN }
    } else {
        matching.asReversed().sortedBy { item -> dateOf(item) ?: LocalDate.MIN }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDateFilterBar(
    state: HistoryFilterState,
    onStateChange: (HistoryFilterState) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var filterMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf(false) }
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    fun message(text: String) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    }
    fun selectPeriod(period: HistoryPeriod) {
        onStateChange(state.copy(period = period, selectedDate = null))
        filterMenu = false
        message("Na-update ang kasaysayan: ${period.label.lowercase(Locale.getDefault())}.")
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        Text(
            text = if (state.period == HistoryPeriod.SPECIFIC_DATE) state.selectedDate?.toString() ?: "Pumili ng petsa" else state.period.label
        )
        Box {
            IconButton(onClick = { filterMenu = true }) {
                Icon(Icons.Default.FilterList, contentDescription = "Filter history by date")
            }
            DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                HistoryPeriod.entries.forEach { period ->
                    DropdownMenuItem(
                        text = { Text(period.label) },
                        onClick = {
                            if (period == HistoryPeriod.SPECIFIC_DATE) {
                                filterMenu = false
                                datePicker = true
                            } else selectPeriod(period)
                        }
                    )
                }
            }
        }
        Box {
            IconButton(onClick = { sortMenu = true }) {
                Icon(Icons.Default.Sort, contentDescription = "Sort history")
            }
            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                DropdownMenuItem(
                    text = { Text("Pinakabago muna") },
                    leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                    onClick = {
                        onStateChange(state.copy(newestFirst = true)); sortMenu = false
                        message("Pinakabago muna ang pagkakasunod-sunod.")
                    }
                )
                DropdownMenuItem(
                    text = { Text("Pinakaluma muna") },
                    leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                    onClick = {
                        onStateChange(state.copy(newestFirst = false)); sortMenu = false
                        message("Pinakaluma muna ang pagkakasunod-sunod.")
                    }
                )
            }
        }
    }

    if (datePicker) {
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) {
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        onStateChange(state.copy(period = HistoryPeriod.SPECIFIC_DATE, selectedDate = selected))
                        message("Na-filter ang kasaysayan para sa $selected.")
                    }
                    datePicker = false
                }) { Text("Piliin") }
            },
            dismissButton = {
                TextButton(onClick = { datePicker = false }) { Text("Kanselahin") }
            }
        ) { DatePicker(state = pickerState) }
    }
}

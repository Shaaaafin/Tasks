@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package dev.quiettasks.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import java.time.*
import java.time.format.DateTimeFormatter

@Composable fun CustomRepeatDialog(initial: String, initialDate: String?, initialTime: String?, dismiss: () -> Unit, apply: (String, String, String?) -> Unit) {
    val parts = remember(initial) { initial.split(';').mapNotNull { it.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap() }
    var start by remember { mutableStateOf(initialDate?.let(LocalDate::parse) ?: LocalDate.now()) }
    var time by remember { mutableStateOf(initialTime) }
    var timeDialog by remember { mutableStateOf(false) }
    var startDialog by remember { mutableStateOf(false) }
    var frequency by remember { mutableStateOf(parts["FREQ"]?.takeIf { it in setOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY") } ?: "WEEKLY") }
    var interval by remember { mutableStateOf(parts["INTERVAL"] ?: "1") }
    var days by remember { mutableStateOf(parts["BYDAY"]?.split(',')?.toSet() ?: setOf(listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")[start.dayOfWeek.value - 1])) }
    var ends by remember { mutableStateOf(if (parts["COUNT"] != null) 2 else if (parts["UNTIL"] != null) 1 else 0) }
    var count by remember { mutableStateOf(parts["COUNT"] ?: "10") }
    var until by remember { mutableStateOf(parts["UNTIL"]?.let { runCatching { LocalDate.parse(it.take(8), DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull() } ?: start.plusMonths(1)) }
    var frequencyMenu by remember { mutableStateOf(false) }
    var dateDialog by remember { mutableStateOf(false) }
    val weekdayCodes = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")
    val first = if (frequency == "WEEKLY" && days.isNotEmpty()) (0L..6L).map(start::plusDays).first { weekdayCodes[it.dayOfWeek.value - 1] in days } else start
    val valid = (ends != 1 || !until.isBefore(first)) && interval.toIntOrNull()?.let { it in 1..999 } == true && (frequency != "WEEKLY" || days.isNotEmpty()) && (ends != 2 || count.toIntOrNull()?.let { it in 1..10000 } == true)
    ModalBottomSheet(onDismissRequest = dismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Repeat schedule", style = MaterialTheme.typography.headlineSmall)
            TextButton({ startDialog = true }) { Text("Starts ${start.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}") }
            if (first != start) Text("First occurrence ${first.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}", style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton({ timeDialog = true }) { Text(time ?: "Set time") }
                if (time != null) IconButton({ time = null }) { TaskIcon(Glyph.CLOSE, "Remove repeat time") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Every"); Spacer(Modifier.width(12.dp))
                Box(Modifier.width(80.dp)) { DialogTextField(interval, { interval = it.filter(Char::isDigit).take(3) }, "Interval", KeyboardOptions(keyboardType = KeyboardType.Number)) }
                Spacer(Modifier.width(8.dp))
                Box {
                    TextButton({ frequencyMenu = true }) { Text(when (frequency) { "DAILY" -> "days"; "WEEKLY" -> "weeks"; "MONTHLY" -> "months"; else -> "years" }); TaskIcon(Glyph.DOWN, null, Modifier.size(16.dp)) }
                    DropdownMenu(frequencyMenu, { frequencyMenu = false }) {
                        listOf("DAILY", "WEEKLY", "MONTHLY", "YEARLY").forEach { value -> DropdownMenuItem(text = { Text(when (value) { "DAILY" -> "Days"; "WEEKLY" -> "Weeks"; "MONTHLY" -> "Months"; else -> "Years" }) }, onClick = { frequency = value; frequencyMenu = false }) }
                    }
                }
            }
            if (frequency == "WEEKLY") Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU").forEachIndexed { index, day ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(if (day in days) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .semantics { selected = day in days; contentDescription = java.time.DayOfWeek.of(index + 1).name.lowercase().replaceFirstChar(Char::uppercase) }.clickable { days = if (day in days) days - day else days + day }, contentAlignment = Alignment.Center) { Text(listOf("M", "T", "W", "T", "F", "S", "S")[index], style = MaterialTheme.typography.labelMedium) }
                }
            }
            Text("Ends", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().clickable { ends = 0 }, verticalAlignment = Alignment.CenterVertically) { RadioButton(ends == 0, { ends = 0 }); Text("Never") }
            Row(Modifier.fillMaxWidth().clickable { ends = 1; dateDialog = true }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(ends == 1, { ends = 1 }); Text("On "); TextButton({ ends = 1; dateDialog = true }) { Text(until.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(ends == 2, { ends = 2 }); Text("After ")
                Box(Modifier.width(96.dp)) { DialogTextField(count, { count = it.filter(Char::isDigit).take(5); ends = 2 }, "Occurrences", KeyboardOptions(keyboardType = KeyboardType.Number)) }
                Text(" times", style = MaterialTheme.typography.bodySmall)
            }
            if (ends == 2) Text("Includes the first occurrence", style = MaterialTheme.typography.bodySmall)
            if (ends == 1 && until.isBefore(first)) Text("End date must be on or after the first occurrence", color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(dismiss) { Text("Cancel") }
                TextButton({
        apply(buildString {
            append("FREQ=$frequency;INTERVAL=${interval.toInt()}")
            if (frequency == "WEEKLY") append(";BYDAY=" + listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU").filter { it in days }.joinToString(","))
            if (ends == 1) append(";UNTIL=${until.format(DateTimeFormatter.BASIC_ISO_DATE)}")
            if (ends == 2) append(";COUNT=${count.toInt()}")
        }, first.toString(), time)
                }, enabled = valid) { Text("Done") }
            }
        }
    }
    if (timeDialog) {
        val selected = time?.let(LocalTime::parse) ?: LocalTime.of(9, 0)
        val state = rememberTimePickerState(selected.hour, selected.minute)
        AlertDialog(onDismissRequest = { timeDialog = false }, title = { Text("Repeat time") }, text = { TimePicker(state) },
            confirmButton = { TextButton({ time = LocalTime.of(state.hour, state.minute).toString(); timeDialog = false }) { Text("Done") } }, dismissButton = { TextButton({ timeDialog = false }) { Text("Cancel") } })
    }
    if (startDialog) {
        val date = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { startDialog = false }, confirmButton = { TextButton({ date.selectedDateMillis?.let {
            val next = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
            if (days == setOf(listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")[start.dayOfWeek.value - 1])) days = setOf(listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")[next.dayOfWeek.value - 1])
            start = next
        }; startDialog = false }) { Text("Done") } }, dismissButton = { TextButton({ startDialog = false }) { Text("Cancel") } }) { DatePicker(date) }
    }
    if (dateDialog) {
        val date = rememberDatePickerState(initialSelectedDateMillis = until.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { dateDialog = false }, confirmButton = { TextButton({ date.selectedDateMillis?.let { until = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }; dateDialog = false }) { Text("Done") } }, dismissButton = { TextButton({ dateDialog = false }) { Text("Cancel") } }) { DatePicker(date) }
    }
}

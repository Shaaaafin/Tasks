@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.quiettasks.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.quiettasks.data.*
import java.time.*

@Composable private fun rememberReminderPermission(): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return { if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
}

@Composable fun QuickAdd(initial: Task, lists: List<TaskList>, onClose: () -> Unit, onSave: (Task, List<Task>) -> Unit) {
    var draft by rememberSaveable(initial.id, stateSaver = TaskDraftSaver) { mutableStateOf(initial) }
    var children by rememberSaveable(initial.id, stateSaver = SubtaskDraftSaver) { mutableStateOf(emptyList<Task>()) }
    var details by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val requestPermission = rememberReminderPermission()
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), dragHandle = null,
        shape = shape, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
            if (initial.parentId != null) Text("New subtask", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))
            PlainField(draft.title, { draft = draft.copy(title = it) }, "New task", Modifier.fillMaxWidth().focusRequester(focus).padding(vertical = 4.dp), 20)
            // Focus the title after the current task field is attached.
            LaunchedEffect(initial.id) { focus.requestFocus() }
            if (details) PlainField(draft.notes, { draft = draft.copy(notes = it) }, "Add details", Modifier.fillMaxWidth().padding(top = 12.dp), 16)
            if (initial.parentId == null) InlineSubtasks(draft, children, { children = it })
            if (draft.dueDate != null) Row(verticalAlignment = Alignment.CenterVertically) {
                InputChip(selected = true, onClick = { date = true }, label = { Text(formatDue(draft.dueDate!!, draft.dueTime)) }, leadingIcon = { TaskIcon(Glyph.TIME, null, Modifier.size(16.dp)) }, trailingIcon = {
                    TaskIcon(Glyph.CLOSE, "Remove date", Modifier.size(16.dp).clickable { draft = draft.copy(dueDate = null, dueTime = null, recurrence = "") })
                })
                if (draft.recurrence.isNotEmpty()) TaskIcon(Glyph.REPEAT, "Repeating", Modifier.padding(start = 8.dp).size(18.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ details = !details }) { TaskIcon(Glyph.DETAILS, "Add details") }
                IconButton({ date = true }) { TaskIcon(Glyph.TIME, "Set date and time") }
                if (initial.parentId == null) IconButton({ repeat = true }) { TaskIcon(Glyph.REPEAT, "Repeat task", tint = if (draft.recurrence.isNotEmpty()) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                IconButton({ draft = draft.copy(starred = !draft.starred) }) { TaskIcon(if (draft.starred) Glyph.STAR else Glyph.STAR_OUTLINE, "Star task", tint = if (draft.starred) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                Spacer(Modifier.weight(1f))
                TextButton({ if (draft.dueDate != null) requestPermission(); onSave(draft, children) }, enabled = draft.title.isNotBlank()) { Text("Save") }
            }
            TaskOrganizationEditor(draft) { draft = it }
            if (lists.size > 1 && initial.parentId == null) Box {
                TextButton({ listMenu = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    TaskIcon(Glyph.LIST, null, Modifier.size(17.dp)); Spacer(Modifier.width(8.dp)); Text(lists.firstOrNull { it.id == draft.listId }?.name ?: "My Tasks"); TaskIcon(Glyph.DOWN, null, Modifier.size(18.dp))
                }
                DropdownMenu(listMenu, { listMenu = false }) { lists.forEach { list -> DropdownMenuItem(text = { Text(list.name) }, onClick = { draft = draft.copy(listId = list.id); listMenu = false }) } }
            }
        }
    }
    if (date) TaskDateDialog(draft, { date = false }) { draft = it; date = false }
    if (repeat) RepeatDialog(draft.recurrence, draft.dueDate, draft.dueTime, { repeat = false }) { rule, start, time -> draft = draft.copy(recurrence = rule, dueDate = start ?: draft.dueDate, dueTime = time); repeat = false }
}

@Composable fun TaskEditor(initial: Task, data: Snapshot, onSave: (Task, List<Task>, Boolean) -> Unit, onClose: () -> Unit,
    onDelete: (Task) -> Unit, onComplete: (Task, List<Task>) -> Unit, onArchive: ((Task, List<Task>) -> Unit)? = null,
) {
    var draft by rememberSaveable(initial.id, stateSaver = TaskDraftSaver) { mutableStateOf(initial) }
    var date by remember { mutableStateOf(false) }
    var repeat by remember { mutableStateOf(false) }
    var deadline by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var listMenu by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    val requestPermission = rememberReminderPermission()
    var children by rememberSaveable(initial.id, stateSaver = SubtaskDraftSaver) {
        mutableStateOf(data.tasks.filter { it.parentId == initial.id && !it.deleted && !it.archived }.sortedBy { it.position })
    }
    fun save() { if (draft.title.isNotBlank()) onSave(draft, children, true) else onClose() }
    BackHandler { save() }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()) {
        Surface(Modifier.weight(1f).fillMaxWidth().padding(top = 8.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column {
            Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton({ save() }) { TaskIcon(Glyph.BACK, "Save and go back") }
                Spacer(Modifier.weight(1f))
                IconButton({ draft = draft.copy(starred = !draft.starred) }) { TaskIcon(if (draft.starred) Glyph.STAR else Glyph.STAR_OUTLINE, "Star task", tint = if (draft.starred) MaterialTheme.colorScheme.primary else LocalContentColor.current) }
                Box {
                    IconButton({ menu = true }) { TaskIcon(Glyph.MORE, "Task options") }
                    DropdownMenu(menu, { menu = false }) {
                        if (onArchive != null) DropdownMenuItem(text = { Text(if (draft.archived) "Restore task" else "Archive task") }, onClick = { menu = false; onArchive(draft, children) })
                        DropdownMenuItem(text = { Text("Delete task") }, leadingIcon = { TaskIcon(Glyph.DELETE, null) }, onClick = { menu = false; delete = true }) }
                }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().padding(horizontal = 24.dp)) {
                Box {
                    TextButton({ listMenu = true }, enabled = initial.parentId == null && children.isEmpty(), contentPadding = PaddingValues(0.dp)) {
                        Text(data.lists.firstOrNull { it.id == draft.listId }?.name ?: "My Tasks"); Spacer(Modifier.width(4.dp)); TaskIcon(Glyph.DOWN, null, Modifier.size(18.dp))
                    }
                    DropdownMenu(listMenu, { listMenu = false }) { data.lists.filter { !it.deleted }.forEach { list -> DropdownMenuItem(text = { Text(list.name) }, onClick = { draft = draft.copy(listId = list.id); listMenu = false }) } }
                }
                PlainField(draft.title, { draft = draft.copy(title = it) }, "Task title", Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 20.dp), 24)
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    TaskIcon(Glyph.DETAILS, null, Modifier.padding(top = 2.dp).size(24.dp)); Spacer(Modifier.width(16.dp))
                    PlainField(draft.notes, { draft = draft.copy(notes = it) }, "Add details", Modifier.weight(1f), 16)
                }
                if (initial.parentId == null) InlineSubtasks(draft, children, { children = it }, enabled = !draft.archived,
                    onCheck = { updated ->
                        children = updated
                        if (updated.any { !it.completed }) draft = draft.copy(completedAt = null)
                        val complete = updated.filter { it.title.isNotBlank() }.let { it.isNotEmpty() && it.all { child -> child.completed } }
                        if (draft.title.isNotBlank()) onSave(draft, updated, complete)
                    })
                TaskOrganizationEditor(draft) { draft = it }
                if (draft.tags.isNotEmpty()) TaskBadges(draft.copy(priority = TaskPriority.NONE))
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TaskIcon(Glyph.DEADLINE, null, Modifier.size(24.dp)); Spacer(Modifier.width(16.dp))
                    if (draft.deadline == null) TextButton({ deadline = true }, contentPadding = PaddingValues(0.dp)) { Text("Add deadline") }
                    else InputChip(selected = false, onClick = { deadline = true }, label = { Text("Due ${formatDue(draft.deadline!!)}") }, trailingIcon = {
                        TaskIcon(Glyph.CLOSE, "Remove deadline", Modifier.size(16.dp).clickable { draft = draft.copy(deadline = null) })
                    })
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    TaskIcon(Glyph.TIME, null, Modifier.size(24.dp)); Spacer(Modifier.width(16.dp))
                    if (draft.dueDate == null) TextButton({ date = true }, contentPadding = PaddingValues(0.dp)) { Text("Add date/time") }
                    else InputChip(selected = false, onClick = { date = true }, label = { Text(formatDue(draft.dueDate!!, draft.dueTime)) }, trailingIcon = {
                        TaskIcon(Glyph.CLOSE, "Remove date", Modifier.size(16.dp).clickable { draft = draft.copy(dueDate = null, dueTime = null, recurrence = "") })
                    })
                }
                if (draft.parentId == null) Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TaskIcon(Glyph.REPEAT, null, Modifier.size(24.dp)); Spacer(Modifier.width(16.dp))
                    TextButton({ repeat = true }, contentPadding = PaddingValues(0.dp)) { Text(repeatLabel(draft.recurrence)) }
                }
                Spacer(Modifier.height(32.dp))
            }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.End) {
            Button({ if (draft.archived) onArchive?.invoke(draft, children) else onComplete(draft, children) }, enabled = draft.title.isNotBlank(), shape = CircleShape) { Text(if (draft.archived) "Restore task" else if (initial.completed) "Mark incomplete" else "Mark completed") }
        }
    }
    if (date) TaskDateDialog(draft, { date = false }, allowRepeat = draft.parentId == null) { draft = it; date = false; requestPermission() }
    if (repeat) RepeatDialog(draft.recurrence, draft.dueDate, draft.dueTime, { repeat = false }) { rule, start, time -> draft = draft.copy(recurrence = rule, dueDate = start ?: draft.dueDate, dueTime = time); repeat = false }
    if (deadline) {
        val deadlineState = rememberDatePickerState(initialSelectedDateMillis = (draft.deadline?.let(LocalDate::parse) ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { deadline = false }, confirmButton = { TextButton({ draft = draft.copy(deadline = deadlineState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }); deadline = false; requestPermission() }) { Text("Done") } }, dismissButton = { TextButton({ deadline = false }) { Text("Cancel") } }) { DatePicker(deadlineState) }
    }
    if (delete) ConfirmDialog("Delete task?", "This task and its subtasks will be deleted on the next sync.", { delete = false }) { onDelete(initial) }
}

@Composable private fun PlainField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier, size: Int) {
    val color = MaterialTheme.colorScheme.onSurface
    BasicTextField(value, onChange, modifier, textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = size.sp, lineHeight = (size + 8).sp, color = color),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), decorationBox = { field -> Box { if (value.isEmpty()) Text(hint, fontSize = size.sp, color = MaterialTheme.colorScheme.onSurfaceVariant); field() } })
}

@Composable private fun TaskDateDialog(initial: Task, dismiss: () -> Unit, allowRepeat: Boolean = initial.parentId == null, apply: (Task) -> Unit) {
    val selectedMillis = initial.dueDate?.let { LocalDate.parse(it).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
    val state = rememberDatePickerState(initialSelectedDateMillis = selectedMillis ?: LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    var time by remember { mutableStateOf(initial.dueTime) }
    var timeDialog by remember { mutableStateOf(false) }
    var recurrence by remember { mutableStateOf(initial.recurrence) }
    var repeat by remember { mutableStateOf(false) }
    DatePickerDialog(onDismissRequest = dismiss, confirmButton = { TextButton({
        apply(initial.copy(dueDate = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }, dueTime = time, recurrence = recurrence))
    }, enabled = state.selectedDateMillis != null) { Text("Done") } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } }) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            DatePicker(state, showModeToggle = true)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ timeDialog = true }) { TaskIcon(Glyph.TIME, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(time ?: "Set time") }
                if (time != null) IconButton({ time = null }) { TaskIcon(Glyph.CLOSE, "Remove time", Modifier.size(18.dp)) }
            }
            if (allowRepeat) TextButton({ repeat = true }, Modifier.padding(horizontal = 16.dp)) { TaskIcon(Glyph.REPEAT, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(repeatLabel(recurrence)) }
        }
    }
    if (timeDialog) {
        val chosen = time?.let(LocalTime::parse) ?: LocalTime.of(9, 0)
        val timeState = rememberTimePickerState(chosen.hour, chosen.minute)
        AlertDialog(onDismissRequest = { timeDialog = false }, title = { Text("Set time") }, text = { TimeInput(timeState) },
            confirmButton = { TextButton({ time = LocalTime.of(timeState.hour, timeState.minute).toString(); timeDialog = false }) { Text("Done") } }, dismissButton = { TextButton({ timeDialog = false }) { Text("Cancel") } })
    }
    if (repeat) RepeatDialog(recurrence, state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString() }, time, { repeat = false }) { rule, start, selectedTime ->
        time = selectedTime
        recurrence = rule
        if (start != null) state.selectedDateMillis = LocalDate.parse(start).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        repeat = false
    }
}

fun repeatLabel(value: String) = when (value) { "" -> "Does not repeat"; "FREQ=DAILY" -> "Daily"; "FREQ=WEEKLY" -> "Weekly"; "FREQ=MONTHLY" -> "Monthly"; "FREQ=YEARLY" -> "Yearly"; else -> "Custom repeat" }
@Composable private fun RepeatDialog(current: String, start: String?, time: String?, dismiss: () -> Unit, apply: (String, String?, String?) -> Unit) {
    var configuration by remember { mutableStateOf<String?>(null) }
    configuration?.let { rule -> CustomRepeatDialog(rule, start, time, dismiss) { value, date, selectedTime -> apply(value, date, selectedTime) }; return }
    fun select(rule: String) {
        if (rule.isEmpty()) apply("", start, time)
        else configuration = if (current.substringBefore(';') == rule) current else rule
    }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Repeat") }, text = { Column {
        listOf("", "FREQ=DAILY", "FREQ=WEEKLY", "FREQ=MONTHLY", "FREQ=YEARLY").forEach { rule ->
            Row(Modifier.fillMaxWidth().clickable { select(rule) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(current.substringBefore(';') == rule, { select(rule) }); Text(repeatLabel(rule)) }
        }
        TextButton({ configuration = current.ifEmpty { "FREQ=WEEKLY" } }) { Text("Custom…") }
    } }, confirmButton = { TextButton(dismiss) { Text("Cancel") } })
}

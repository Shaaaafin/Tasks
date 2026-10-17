@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package dev.quiettasks.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import dev.quiettasks.data.*

fun SortMode.label() = when (this) {
    SortMode.MY_ORDER -> "My order"
    SortMode.DATE -> "Date order"
    SortMode.PRIORITY -> "Priority first"
    SortMode.STARRED -> "Starred first"
    SortMode.TITLE -> "Title"
}

@Composable internal fun TaskSortMenu(sort: SortMode, onSelect: (SortMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton({ expanded = true }) { TaskIcon(Glyph.SORT, "Sort tasks") }
        DropdownMenu(expanded, { expanded = false }) {
            SortMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(mode.label()) },
                onClick = { expanded = false; onSelect(mode) }, trailingIcon = { if (mode == sort) TaskIcon(Glyph.CHECK, null) }) }
        }
    }
}

@Composable internal fun TaskTagFilter(tasks: List<Task>, selected: String?, onSelect: (String?) -> Unit) {
    val tags = normalizeTags(tasks.flatMap { it.tags }).sortedBy { it.lowercase() }
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(tags) { if (selected != null && tags.none { it.equals(selected, true) }) onSelect(null) }
    if (tags.isNotEmpty()) Box {
        IconButton({ expanded = true }) { TaskIcon(Glyph.TAG, selected?.let { "Filter by tag: $it" } ?: "Filter by tag", tint = if (selected == null) LocalContentColor.current else MaterialTheme.colorScheme.primary) }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("All tags") }, onClick = { onSelect(null); expanded = false }, trailingIcon = { if (selected == null) TaskIcon(Glyph.CHECK, null) })
            tags.forEach { tag -> DropdownMenuItem(text = { Text("#$tag") }, onClick = { onSelect(tag); expanded = false }, trailingIcon = { if (tag.equals(selected, true)) TaskIcon(Glyph.CHECK, null) }) }
        }
    }
}

@Composable private fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.HIGH -> tabAccent("red")
    TaskPriority.MEDIUM -> tabAccent("orange")
    TaskPriority.LOW -> tabAccent("blue")
    TaskPriority.NONE -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable internal fun PriorityButton(value: TaskPriority, onChange: (TaskPriority) -> Unit) {
    var open by remember { mutableStateOf(false) }
    TextButton({ open = true }) {
        TaskIcon(Glyph.FLAG, null, Modifier.size(20.dp), priorityColor(value)); Spacer(Modifier.width(8.dp)); Text("Priority: ${value.label}", color = priorityColor(value))
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Task priority") }, text = { Column {
        TaskPriority.entries.forEach { priority ->
            Row(Modifier.fillMaxWidth().clickable { onChange(priority); open = false }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(value == priority, { onChange(priority); open = false }); Text(priority.label, color = priorityColor(priority))
            }
        }
    } }, confirmButton = { TextButton({ open = false }) { Text("Cancel") } })
}

@Composable internal fun TagsButton(tags: List<String>, onChange: (List<String>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column {
        TagsToggle(tags) { focus.clearFocus(force = true); open = !open }
        if (open) InlineTagsEditor(tags, onChange) { open = false }
    }
}

@Composable private fun TagsToggle(tags: List<String>, click: () -> Unit) {
    TextButton(click) { TaskIcon(Glyph.TAG, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text(if (tags.isEmpty()) "Add tags" else "Tags (${tags.size})") }
}

@Composable internal fun TaskOrganizationEditor(task: Task, onChange: (Task) -> Unit) {
    var tagsOpen by rememberSaveable(task.id) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PriorityButton(task.priority) { onChange(task.copy(priority = it)) }
            TagsToggle(task.tags) { focus.clearFocus(force = true); tagsOpen = !tagsOpen }
        }
        if (tagsOpen) InlineTagsEditor(task.tags, { onChange(task.copy(tags = it)) }) { tagsOpen = false }
    }
}

@Composable private fun InlineTagsEditor(tags: List<String>, onChange: (List<String>) -> Unit, close: () -> Unit) {
        var selected by rememberSaveable { mutableStateOf(ArrayList(tags)) }
        var entry by rememberSaveable { mutableStateOf("") }
        fun pending() = normalizeTags(selected + entry.split(','))
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                if (selected.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    selected.forEach { tag -> InputChip(selected = true, onClick = {}, label = { Text("#$tag") }, trailingIcon = {
                        TaskIcon(Glyph.CLOSE, "Remove tag $tag", Modifier.size(16.dp).clickable { selected = ArrayList(selected - tag) })
                    }) }
                }
                DialogTextField(entry, { entry = it }, "Add tags")
                Text("Separate new tags with commas", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                TextButton({ selected = ArrayList(pending()); entry = "" }, enabled = entry.isNotBlank()) { Text("Add") }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(close) { Text("Cancel") }; TextButton({ onChange(pending()); close() }) { Text("Done") }
                }
        }
}

@Composable internal fun TaskBadges(task: Task) {
    if (task.priority != TaskPriority.NONE || task.tags.isNotEmpty()) FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (task.priority != TaskPriority.NONE) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            TaskIcon(Glyph.FLAG, null, Modifier.size(14.dp), priorityColor(task.priority))
            Text("${task.priority.label} priority", style = MaterialTheme.typography.labelSmall, color = priorityColor(task.priority))
        }
        task.tags.forEach { tag -> Surface(shape = RoundedCornerShape(5.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text("#$tag", Modifier.padding(horizontal = 5.dp, vertical = 1.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}

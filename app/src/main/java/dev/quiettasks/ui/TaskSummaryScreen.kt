package dev.quiettasks.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.quiettasks.TaskViewModel
import dev.quiettasks.data.*
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

@Composable private fun currentDay(): LocalDate {
    var today by remember { mutableStateOf(LocalDate.now()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                today = LocalDate.now()
                val now = ZonedDateTime.now()
                delay(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1) + 100)
            }
        }
    }
    return today
}

@Composable internal fun SummaryCard(tasks: List<Task>, data: Snapshot, sort: SortMode, model: TaskViewModel,
    onEdit: (Task) -> Unit, onCustomize: () -> Unit, longPressAction: LongPressAction,
) {
    val today = currentDay()
    var tag by rememberSaveable { mutableStateOf<String?>(null) }
    val filtered = tasks.filter { tag == null || it.tags.any { value -> value.equals(tag, true) } }
    val activeRows = activeTaskRows(filtered, data.tasks)
    val groups = remember(activeRows, today, sort) {
        if (sort == SortMode.DATE) {
            val roots = activeRows.filter { child -> activeRows.none { it.id == child.parentId } }
            taskDateGroups(roots, today).map { group ->
                val ids = group.tasks.mapTo(HashSet()) { it.id }
                group.copy(tasks = taskHierarchy(group.tasks + activeRows.filter { it.parentId in ids }))
            }
        } else activeRows.takeIf { it.isNotEmpty() }?.let {
            listOf(TaskDateGroup(null, if (sort == SortMode.MY_ORDER) "My order" else sort.label(), sortTasks(it, sort)))
        }.orEmpty()
    }
    val completed = completedTaskRows(filtered, data.tasks).sortedByDescending { it.completedAt }
    val drag = rememberTaskDrag(tasks, model::reorder)
    var completedOpen by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val active = sortTasks(activeRows, sort)
    val reorder = sort == SortMode.MY_ORDER && tag == null && longPressAction == LongPressAction.REORDER
    LazyColumn(Modifier.fillMaxSize().taskReorderContainer(active, drag, listState, reorder), state = listState, userScrollEnabled = drag.dragging == null, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp)) {
        item("summary-header") {
            Column(Modifier.padding(start = 8.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    TaskTagFilter(tasks, tag) { tag = it }
                    TaskSortMenu(sort) { mode -> model.updateSettings { it.copy(summarySort = mode) } }
                    Box {
                        IconButton({ menu = true }) { TaskIcon(Glyph.MORE, "Summary options") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("Customize tab") }, onClick = { menu = false; onCustomize() })
                            DropdownMenuItem(text = { Text("Sync now") }, onClick = { menu = false; model.sync() })
                            DropdownMenuItem(text = { Text("Delete all completed tasks") }, enabled = completed.isNotEmpty(), onClick = { menu = false; clear = true })
                        }
                    }
                }

            }
        }
        if (groups.isEmpty()) item("summary-empty") {
            Surface(Modifier.fillMaxWidth().padding(top = 24.dp), shape = RoundedCornerShape(16.dp)) {
                Text(if (tag != null) "No active tasks tagged #$tag" else if (completed.isEmpty()) "No tasks yet. Add a task to get started." else "All caught up", Modifier.padding(24.dp), style = MaterialTheme.typography.bodyLarge)
            }
        }
        groups.forEach { group ->
            val ordered = if (sort == SortMode.MY_ORDER) taskHierarchy(drag.ordered(group.tasks)) else taskHierarchy(group.tasks)
            item("date:${group.key}") {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 12.dp, top = 20.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(group.label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        color = if (group.date != null && group.date < today) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${ordered.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            itemsIndexed(ordered, key = { _, task -> task.id }) { index, task ->
                val list = data.lists.firstOrNull { it.id == task.listId }
                val parent = data.tasks.firstOrNull { it.id == task.parentId && !it.deleted }
                val label = listOfNotNull(list?.let { if (it.icon.isNotEmpty() && !it.icon.startsWith("glyph:")) "${it.icon} ${it.name}" else it.name }, parent?.title).joinToString(" · ")
                Surface(modifier = (if (reorder) Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (drag.isLifted(task.id) || drag.isReleasing(task.id)) null else tween(120)) else Modifier).taskDragVisual(task, active, drag, listState), shape = RoundedCornerShape(topStart = if (index == 0) 16.dp else 0.dp, topEnd = if (index == 0) 16.dp else 0.dp,
                    bottomStart = if (index == ordered.lastIndex) 16.dp else 0.dp, bottomEnd = if (index == ordered.lastIndex) 16.dp else 0.dp),
                    color = taskDragContainerColor(task.id, drag)) {
                    TaskRow(task, parent != null && ordered.any { it.id == parent.id }, label, { model.toggle(task) }, { model.star(task) }, { onEdit(task) }, listColor = tabAccent(list?.color.orEmpty()), onArchive = { model.archive(task) }, onDelete = { model.delete(task) }, longPressAction = longPressAction)
                }
            }
        }
        if (completed.isNotEmpty()) {
            item("summary-completed") {
                Surface(Modifier.padding(top = 16.dp), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { completedOpen = !completedOpen }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Completed (${completed.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TaskIcon(if (completedOpen) Glyph.UP else Glyph.DOWN, if (completedOpen) "Collapse completed tasks" else "Expand completed tasks")
                    }
                }
            }
            if (completedOpen) itemsIndexed(completed, key = { _, task -> task.id }) { _, task ->
                Surface { TaskRow(task, false, null, { model.toggle(task) }, { model.star(task) }, { onEdit(task) }, onArchive = { model.archive(task) }, onDelete = { model.delete(task) }, longPressAction = longPressAction) }
            }
        }
    }
    if (clear) ConfirmDialog("Delete completed tasks?", "Completed tasks in this view will be deleted on the next sync. Parents with subtasks outside this view will be kept.", { clear = false }) { model.clearCompleted(null, tag = tag); clear = false }
}

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)

package dev.quiettasks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Offset
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.quiettasks.TaskViewModel
import dev.quiettasks.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable fun TasksApp(model: TaskViewModel, openTaskId: String? = null, createListId: String? = null) {
    val data by model.snapshot.collectAsStateWithLifecycle()
    val prefs by model.preferences.collectAsStateWithLifecycle()
    val sync by model.syncStatus.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var settings by rememberSaveable { mutableStateOf(false) }
    var archived by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    val editing = data.tasks.firstOrNull { it.id == editingId && !it.deleted }
    var creatingSpec by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val creating = creatingSpec?.let { Task(id = it[0], listId = it[1], parentId = it[2].ifEmpty { null }, title = "") }
    fun create(task: Task) { creatingSpec = listOf(task.id, task.listId, task.parentId.orEmpty()) }
    var consumedTask by rememberSaveable { mutableStateOf(false) }
    var consumedCreate by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(data.lists, createListId) { if (!consumedCreate && createListId != null && data.lists.any { it.id == createListId && !it.deleted }) { create(Task(listId = createListId, title = "")); consumedCreate = true } }
    LaunchedEffect(data.tasks, openTaskId) { if (!consumedTask && openTaskId != null) data.tasks.firstOrNull { it.id == openTaskId && !it.deleted }?.let { editingId = it.id; consumedTask = true } }
    LaunchedEffect(model) { for (message in model.messages) snackbar.showSnackbar(message) }
    TasksTheme(prefs.theme, prefs.deviceColors) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when {
                settings -> SettingsScreen(prefs, sync, model, { settings = false }, { settings = false; archived = true })
                editing != null -> TaskEditor(editing, data,
                    onSave = { task, children, close -> model.saveFamily(task, children) { if (close) editingId = null } },
                    onClose = { editingId = null }, onDelete = { model.delete(it); editingId = null },
                    onComplete = { task, children -> model.saveFamily(task, children, toggleParent = true) { editingId = null } },
                    onArchive = { task, children -> model.saveFamily(task, children) { model.archive(task); editingId = null } })
                archived -> ArchivedTasks(data, prefs.longPressAction, model, { archived = false }, { editingId = it.id })
                else -> TasksHome(data, prefs, sync.running, model, onSettings = { settings = true }, onEdit = { editingId = it.id }, onAdd = ::create)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp))
            creating?.let { task -> QuickAdd(task, data.lists.filter { !it.deleted }, { creatingSpec = null }) { value, children ->
                model.saveFamily(value, children) { creatingSpec = null }
            } }
        }
    }
}

@Composable fun TasksHome(data: Snapshot, prefs: Preferences, syncing: Boolean, model: TaskViewModel,
    onSettings: () -> Unit, onEdit: (Task) -> Unit, onAdd: (Task) -> Unit,
) {
    val lists = data.lists.filter { !it.deleted }
    val tabs = visibleTabIds(lists, prefs).ifEmpty { listOf(ALL_TASKS_TAB) }
    val pager = rememberPagerState(initialPage = tabs.indexOf(prefs.selectedList).coerceAtLeast(0)) { tabs.size }
    var restored by rememberSaveable { mutableStateOf(false) }
    var listDialog by remember { mutableStateOf<TaskList?>(null) }
    var newList by remember { mutableStateOf(false) }
    val currentList = lists.firstOrNull { it.id == tabs.getOrNull(pager.currentPage) }
    fun customize(id: String) {
        listDialog = when (id) {
            ALL_TASKS_TAB -> TaskList(id = id, name = "All", color = prefs.allTabColor, icon = prefs.allTabIcon)
            STARRED_TAB -> TaskList(id = id, name = "Starred", color = prefs.starredTabColor, icon = prefs.starredTabIcon)
            else -> lists.firstOrNull { it.id == id }
        }
    }
    LaunchedEffect(tabs) {
        val target = tabs.indexOf(prefs.selectedList).coerceAtLeast(0)
        pager.scrollToPage(target)
        restored = true
        if (lists.isNotEmpty() && prefs.selectedList !in tabs) model.updateSettings { it.copy(selectedList = tabs[target]) }
    }
    LaunchedEffect(pager.settledPage, pager.isScrollInProgress) {
        if (restored && !pager.isScrollInProgress && lists.isNotEmpty()) {
            val selected = tabs.getOrNull(pager.settledPage) ?: tabs.first()
            if (prefs.selectedList != selected) model.updateSettings { it.copy(selectedList = selected) }
        }
    }
    val headerColor = if (prefs.theme == ThemeMode.AMOLED) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainerLow
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Box(Modifier.fillMaxWidth().height(64.dp).background(headerColor)) {
            Text("Tasks", Modifier.align(Alignment.Center), fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurface)
            IconButton(onSettings, Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    TaskIcon(Glyph.SETTINGS, "Settings", Modifier.size(22.dp), MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        val tabSpecs = tabs.map { id ->
            val list = lists.firstOrNull { it.id == id }
            TaskTabSpec(id, when (id) { ALL_TASKS_TAB -> "All"; STARRED_TAB -> "Starred"; else -> list?.name.orEmpty() },
                when (id) { ALL_TASKS_TAB -> prefs.allTabColor; STARRED_TAB -> prefs.starredTabColor; else -> list?.color ?: "default" },
                when (id) { ALL_TASKS_TAB -> prefs.allTabIcon; STARRED_TAB -> prefs.starredTabIcon; else -> list?.icon.orEmpty() })
        }
        Surface(color = headerColor) { TaskTabs(tabSpecs, pager, ::customize) { newList = true } }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            HorizontalPager(pager, Modifier.fillMaxSize().testTag("task-pages"), verticalAlignment = Alignment.Top, flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = tween(180, easing = FastOutSlowInEasing)), key = { page -> tabs[page] }) { page ->
                val list = lists.firstOrNull { it.id == tabs[page] }
                val starred = tabs[page] == STARRED_TAB
                val visible = data.tasks.filter { !it.deleted && !it.archived && lists.any { list -> list.id == it.listId } }
                if (tabs[page] == ALL_TASKS_TAB) SummaryCard(visible, data, prefs.summarySort, model, onEdit, { customize(ALL_TASKS_TAB) }, prefs.longPressAction)
                else {
                    val tabId = list?.id ?: STARRED_TAB
                    TaskCard(list, visible.filter { if (starred) it.starred else it.listId == list?.id }, data, prefs.listSorts[tabId] ?: prefs.sort, model, onEdit, { listDialog = it }, starred, { customize(STARRED_TAB) },
                        { mode -> model.updateSettings { it.copy(listSorts = it.listSorts + (tabId to mode)) } }, prefs.longPressAction)
                }
            }
            FloatingActionButton(onClick = { (currentList ?: lists.firstOrNull())?.let { onAdd(Task(listId = it.id, title = "")) } },
                Modifier.align(Alignment.BottomEnd).padding(24.dp), shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(3.dp)) { TaskIcon(Glyph.ADD, "Create task", Modifier.size(28.dp)) }
            if (syncing) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        }
    }
    if (newList || listDialog != null) {
        val editing = listDialog
        TabAppearanceDialog(editing?.name.orEmpty(), editing?.color ?: "default", editing?.icon.orEmpty(), editing?.id !in listOf(ALL_TASKS_TAB, STARRED_TAB),
            dismiss = { newList = false; listDialog = null }) { name, color, icon ->
            when (editing?.id) {
                ALL_TASKS_TAB -> model.updateSettings { it.copy(allTabColor = color, allTabIcon = icon) }
                STARRED_TAB -> model.updateSettings { it.copy(starredTabColor = color, starredTabIcon = icon) }
                else -> model.saveList(editing?.copy(name = name, color = color, icon = icon) ?: TaskList(name = name, color = color, icon = icon))
            }
            newList = false; listDialog = null
        }
    }
}

@Composable private fun TaskCard(list: TaskList?, tasks: List<Task>, data: Snapshot, sort: SortMode, model: TaskViewModel,
    onEdit: (Task) -> Unit, onRename: (TaskList) -> Unit, starred: Boolean, onCustomizeStarred: () -> Unit, onSort: (SortMode) -> Unit, longPressAction: LongPressAction,
) {
    var completedOpen by rememberSaveable(list?.id) { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var tag by rememberSaveable(list?.id, starred) { mutableStateOf<String?>(null) }
    var deleteDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    val ordered = sortTasks(tasks.filter { tag == null || it.tags.any { value -> value.equals(tag, true) } }, sort)
    val dragState = rememberTaskDrag(tasks, model::reorder)
    val sourceActive = activeTaskRows(ordered, data.tasks)
    val active = dragState.ordered(sourceActive)
    val done = completedTaskRows(ordered, data.tasks)
    val flattened = taskHierarchy(active)
    val activeIds = active.mapTo(HashSet()) { it.id }
    val listState = rememberLazyListState()
    val reorder = sort == SortMode.MY_ORDER && tag == null && longPressAction == LongPressAction.REORDER
    val cardColor = MaterialTheme.colorScheme.surface
    LazyColumn(Modifier.fillMaxSize().taskReorderContainer(sourceActive, dragState, listState, reorder), state = listState, userScrollEnabled = dragState.dragging == null, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 112.dp)) {
        item("header") {
            Surface(shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (active.isEmpty()) 16.dp else 0.dp, bottomEnd = if (active.isEmpty()) 16.dp else 0.dp), color = cardColor) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (starred) "Starred" else list?.name ?: "My Tasks", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TaskTagFilter(tasks, tag) { tag = it }
                TaskSortMenu(sort, onSort)
                Box {
                    IconButton({ menu = true }) { TaskIcon(Glyph.MORE, "List options") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Sync now") }, leadingIcon = { TaskIcon(Glyph.SYNC, null) }, onClick = { model.sync(); menu = false })
                        if (list != null) {
                            DropdownMenuItem(text = { Text("Customize tab") }, onClick = { onRename(list); menu = false })
                            DropdownMenuItem(text = { Text("Delete list") }, enabled = data.lists.count { !it.deleted } > 1, onClick = { deleteDialog = true; menu = false })
                        } else DropdownMenuItem(text = { Text("Customize tab") }, onClick = { onCustomizeStarred(); menu = false })
                        DropdownMenuItem(text = { Text("Delete all completed tasks") }, enabled = done.isNotEmpty(), onClick = { clearDialog = true; menu = false })
                    }
                }
            }
            }
        }
        if (ordered.isEmpty()) item("empty") {
            if (tag != null) Text("No tasks tagged #$tag", Modifier.padding(24.dp)) else EmptyTasks(starred, Modifier.height(400.dp))
        }
        itemsIndexed(flattened, key = { _, task -> task.id }) { index, task ->
                    val nested = task.parentId != null && task.parentId in activeIds
                    val last = index == flattened.lastIndex
                    Surface(modifier = (if (reorder) Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = if (dragState.isLifted(task.id) || dragState.isReleasing(task.id)) null else tween(120)) else Modifier).taskDragVisual(task, sourceActive, dragState, listState), shape = RoundedCornerShape(bottomStart = if (last) 16.dp else 0.dp, bottomEnd = if (last) 16.dp else 0.dp), color = taskDragContainerColor(task.id, dragState)) {
                        Box(Modifier.padding(bottom = if (last) 12.dp else 0.dp)) { TaskRow(task, nested, if (starred) data.lists.firstOrNull { it.id == task.listId }?.name else null, { model.toggle(task) }, { model.star(task) }, { onEdit(task) }, longPressAction = longPressAction, onArchive = { model.archive(task) }, onDelete = { model.delete(task) }) }
                    }
            }
        if (done.isNotEmpty()) {
            item("completed_header") {
                Surface(Modifier.padding(top = 8.dp), shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = if (completedOpen) 0.dp else 16.dp, bottomEnd = if (completedOpen) 0.dp else 16.dp), color = cardColor) {
                    Row(Modifier.fillMaxWidth().clickable { completedOpen = !completedOpen }.padding(horizontal = 24.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Completed (${done.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TaskIcon(if (completedOpen) Glyph.UP else Glyph.DOWN, if (completedOpen) "Collapse completed tasks" else "Expand completed tasks", Modifier.size(24.dp))
                    }
                }
            }
            if (completedOpen) itemsIndexed(done, key = { _, task -> task.id }) { index, task ->
                Surface(shape = RoundedCornerShape(bottomStart = if (index == done.lastIndex) 16.dp else 0.dp, bottomEnd = if (index == done.lastIndex) 16.dp else 0.dp), color = cardColor) {
                    TaskRow(task, task.parentId != null, null, { model.toggle(task) }, { model.star(task) }, { onEdit(task) }, onArchive = { model.archive(task) }, onDelete = { model.delete(task) }, longPressAction = longPressAction)
                }
            }
        }
    }
    if (deleteDialog && list != null) ConfirmDialog("Delete ${list.name}?", "Its tasks will be deleted from this device and the server on the next sync. The server’s empty calendar will be kept.", { deleteDialog = false }) { model.deleteList(list); deleteDialog = false }
    if (clearDialog) ConfirmDialog("Delete completed tasks?", "Completed tasks in this view will be deleted on the next sync. Parents with subtasks outside this view will be kept.", { clearDialog = false }) { model.clearCompleted(if (starred) null else list?.id, starred, tag); clearDialog = false }
}

@Composable fun TaskRow(task: Task, nested: Boolean, listName: String?, onToggle: () -> Unit, onStar: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier, listColor: Color? = null, onArchive: (() -> Unit)? = null, onDelete: (() -> Unit)? = null, longPressAction: LongPressAction = LongPressAction.REORDER) {
    var actions by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var point by remember { mutableStateOf(IntOffset.Zero) }
    val rowGesture = if (longPressAction == LongPressAction.MENU) Modifier.onGloballyPositioned { coordinates = it }
        .pointerInput(task.id) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val position = coordinates?.localToWindow(down.position) ?: Offset.Zero
                point = IntOffset(position.x.roundToInt(), position.y.roundToInt())
                waitForUpOrCancellation()
            }
        }.combinedClickable(onClick = onEdit, onLongClickLabel = "Task actions", onLongClick = { actions = true })
        else Modifier.clickable(onClick = onEdit)
    Box {
    Row(modifier.fillMaxWidth().then(rowGesture).padding(start = if (nested) 40.dp else 8.dp, end = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.Top) {
        IconButton(onToggle) { TaskIcon(if (task.completed) Glyph.CHECK else Glyph.CIRCLE, if (task.archived) "Restore ${task.title}" else if (task.completed) "Mark ${task.title} incomplete" else "Complete ${task.title}", Modifier.size(20.dp), if (task.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(Modifier.weight(1f).padding(start = 4.dp, top = 12.dp, bottom = 12.dp)) {
            Text(task.title, color = if (task.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp, lineHeight = 24.sp, textDecoration = if (task.completed) TextDecoration.LineThrough else null)
            if (task.notes.isNotBlank() && !task.completed) Text(task.notes, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!task.completed) TaskBadges(task)
            if (!task.completed && task.deadline != null) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val deadlineColor = if (task.deadline < LocalDate.now().toString()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                TaskIcon(Glyph.DEADLINE, null, Modifier.size(14.dp), deadlineColor)
                Text("Due ${formatDue(task.deadline)}", fontSize = 12.sp, color = deadlineColor)
            }
            if (!task.completed && (task.dueDate != null || task.recurrence.isNotEmpty() || listName != null)) Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val overdue = task.dueDate?.let { it < LocalDate.now().toString() } == true
                val dateColor = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                if (task.dueDate != null) { TaskIcon(Glyph.CALENDAR, null, Modifier.size(14.dp), dateColor); Text(formatDue(task.dueDate, task.dueTime), fontSize = 12.sp, color = dateColor) }
                if (task.recurrence.isNotEmpty()) TaskIcon(Glyph.REPEAT, "Repeating task", Modifier.size(14.dp), dateColor)
                if (listName != null) Text(listName, fontSize = 12.sp, color = listColor ?: MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!task.completed) IconButton(onStar) { TaskIcon(if (task.starred) Glyph.STAR else Glyph.STAR_OUTLINE, if (task.starred) "Unstar ${task.title}" else "Star ${task.title}", Modifier.size(23.dp), if (task.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    if (actions) FingerTaskMenu(point, { actions = false }) {
        DropdownMenuItem(text = { Text("Edit") }, onClick = { actions = false; onEdit() })
        if (onArchive != null) DropdownMenuItem(text = { Text(if (task.archived) "Restore" else "Archive") }, onClick = { actions = false; onArchive() })
        if (onDelete != null) DropdownMenuItem(text = { Text("Delete") }, onClick = { actions = false; deleting = true })
    }
    }
    if (deleting) ConfirmDialog("Delete task?", "This task and its subtasks will be deleted on the next sync.", { deleting = false }) { deleting = false; onDelete?.invoke() }
}

@Composable private fun ArchivedTasks(data: Snapshot, longPressAction: LongPressAction, model: TaskViewModel, back: () -> Unit, edit: (Task) -> Unit) {
    BackHandler(onBack = back)
    Scaffold(topBar = { TopAppBar(title = { Text("Archived tasks") }, navigationIcon = { IconButton(back) { TaskIcon(Glyph.BACK, "Back") } }) }) { padding ->
        val tasks = data.tasks.filter { it.archived && !it.deleted && data.lists.any { list -> list.id == it.listId && !list.deleted } }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
            if (tasks.isEmpty()) item { Text("No archived tasks", Modifier.padding(20.dp)) }
            items(tasks, key = { it.id }) { task ->
                Surface(shape = RoundedCornerShape(16.dp)) {
                    TaskRow(task, task.parentId != null, data.lists.firstOrNull { it.id == task.listId }?.name,
                        { model.archive(task) }, { model.star(task) }, { edit(task) }, onArchive = { model.archive(task) }, onDelete = { model.delete(task) }, longPressAction = longPressAction)
                }
            }
        }
    }
}

@Composable private fun EmptyTasks(starred: Boolean, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(bottom = 80.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(124.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .45f)), contentAlignment = Alignment.Center) {
            TaskIcon(if (starred) Glyph.STAR_OUTLINE else Glyph.CHECK, null, Modifier.size(66.dp), MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(28.dp))
        Text(if (starred) "No starred tasks" else "No tasks yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Text(if (starred) "Star important tasks to find them here" else "Add a task to get started", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

fun formatDue(date: String, time: String? = null): String {
    val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
    val label = when (day) { LocalDate.now() -> "Today"; LocalDate.now().plusDays(1) -> "Tomorrow"; else -> day.format(DateTimeFormatter.ofPattern(if (day.year == LocalDate.now().year) "EEE, MMM d" else "MMM d, yyyy")) }
    return label + (time?.let { " · $it" } ?: "")
}

@Composable fun ConfirmDialog(title: String, text: String, dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(confirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(dismiss) { Text("Cancel") } })
}

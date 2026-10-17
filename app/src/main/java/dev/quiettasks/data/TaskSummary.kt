package dev.quiettasks.data

import java.time.LocalDate
import java.time.format.DateTimeFormatter

const val ALL_TASKS_TAB = "@all"
const val STARRED_TAB = "*"

data class TaskDateGroup(val date: LocalDate?, val label: String, val tasks: List<Task>) {
    val key get() = date?.toString() ?: "undated"
}

/** A task appears once, on the earlier of its reminder date and deadline. */
fun taskDateGroups(tasks: List<Task>, today: LocalDate = LocalDate.now()): List<TaskDateGroup> =
    tasks.filter { !it.deleted && !it.archived && !it.completed }.groupBy { task ->
        listOfNotNull(task.dueDate, task.deadline).mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.minOrNull()
    }.entries.sortedWith(compareBy(nullsFirst()) { it.key }).map { (date, values) ->
        val formatted = date?.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "EEE, MMM d" else "MMM d, yyyy"))
        val label = when {
            date == null -> "No date"
            date < today -> "Overdue · $formatted"
            date == today -> "Today"
            date == today.plusDays(1) -> "Tomorrow"
            else -> formatted!!
        }
        TaskDateGroup(date, label, values.sortedWith(compareBy<Task> { it.position }.thenBy { it.id }))
    }

fun movedTaskIds(ids: List<String>, id: String, direction: Int): List<String> {
    val from = ids.indexOf(id)
    val to = from + direction
    if (direction !in listOf(-1, 1) || from < 0 || to !in ids.indices) return ids
    return ids.toMutableList().apply { java.util.Collections.swap(this, from, to) }
}

fun visibleTabIds(lists: List<TaskList>, prefs: Preferences): List<String> = buildList {
    if (prefs.showAllTab) add(ALL_TASKS_TAB)
    if (prefs.showStarredTab) add(STARRED_TAB)
    addAll(lists.filter { !it.deleted }.map { it.id })
}

fun sortTasks(tasks: List<Task>, mode: SortMode): List<Task> = when (mode) {
    SortMode.MY_ORDER -> tasks.sortedWith(compareBy<Task> { it.position }.thenBy { it.id })
    SortMode.DATE -> tasks.sortedWith(compareBy<Task> { listOfNotNull(it.dueDate, it.deadline).minOrNull().orEmpty() }.thenBy { it.dueTime.orEmpty() }.thenBy { it.position })
    SortMode.PRIORITY -> tasks.sortedWith(compareBy<Task> { it.priority.ordinal }.thenBy { it.position })
    SortMode.STARRED -> tasks.sortedWith(compareByDescending<Task> { it.starred }.thenBy { it.position })
    SortMode.TITLE -> tasks.sortedWith(compareBy<Task> { it.title.lowercase() }.thenBy { it.position })
}

/** Completed children remain beside an unfinished parent until the family is complete. */
fun activeTaskRows(tasks: List<Task>, all: List<Task> = tasks): List<Task> {
    val pending = all.filter { !it.deleted && !it.archived && !it.completed }.mapTo(HashSet()) { it.id }
    return tasks.filter { !it.deleted && !it.archived && (!it.completed || it.parentId in pending) }
}

fun completedTaskRows(tasks: List<Task>, all: List<Task> = tasks): List<Task> {
    val visible = all.filter { !it.deleted && !it.archived }.associateBy { it.id }
    return tasks.filter { !it.deleted && !it.archived && it.completed && it.parentId !in visible }
}

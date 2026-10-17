package dev.quiettasks.data

import java.time.*
import java.util.UUID
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import java.time.format.DateTimeFormatter

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class SyncMode { WHILE_USING, CHARGING_ONLY, MANUAL }
enum class LongPressAction { REORDER, MENU }
enum class SortMode { MY_ORDER, DATE, PRIORITY, STARRED, TITLE }
enum class TaskPriority(val ical: Int, val label: String) {
    HIGH(1, "High"), MEDIUM(5, "Medium"), LOW(9, "Low"), NONE(0, "None");
    companion object {
        fun fromIcal(value: Int) = when (value) { in 1..4 -> HIGH; 5 -> MEDIUM; in 6..9 -> LOW; else -> NONE }
    }
}
fun normalizeTags(values: List<String>): List<String> = values.map { it.trim().removePrefix("#").trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase(java.util.Locale.ROOT) }

data class TaskList(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val position: Long = System.currentTimeMillis(),
    val remoteUrl: String? = null,
    val dirty: Boolean = true,
    val deleted: Boolean = false,
    val revision: Long = 1,
    val color: String = "default",
    val icon: String = "",
)

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val listId: String,
    val title: String,
    val notes: String = "",
    val starred: Boolean = false,
    val dueDate: String? = null,
    val dueTime: String? = null,
    val parentId: String? = null,
    val position: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val recurrence: String = "",
    val modifiedAt: Long = System.currentTimeMillis(),
    val remoteHref: String? = null,
    val etag: String? = null,
    val rawCalendar: String? = null,
    val dirty: Boolean = true,
    val deleted: Boolean = false,
    val revision: Long = 1,
    val deadline: String? = null,
    val recurrenceAnchor: String? = null,
    val priority: TaskPriority = TaskPriority.NONE,
    val tags: List<String> = emptyList(),
    val archived: Boolean = false,
) {
    val completed get() = completedAt != null
    fun reminderAt(zone: ZoneId = ZoneId.systemDefault()): Long? = dueDate?.let {
        runCatching { LocalDate.parse(it).atTime(dueTime?.let(LocalTime::parse) ?: LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }
    fun deadlineAt(zone: ZoneId = ZoneId.systemDefault()): Long? = deadline?.let {
        runCatching { LocalDate.parse(it).atTime(9, 0).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
    }
}

data class Snapshot(val lists: List<TaskList> = emptyList(), val tasks: List<Task> = emptyList())
data class Preferences(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val syncMode: SyncMode = SyncMode.WHILE_USING,
    val wifiOnly: Boolean = false,
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val lastSyncAt: Long = 0,
    val lastSyncMessage: String = "Not synced yet",
    val selectedList: String = "",
    val sort: SortMode = SortMode.MY_ORDER,
    val reminders: Boolean = true,
    val deviceColors: Boolean = true,
    val allTabColor: String = "default",
    val allTabIcon: String = "",
    val starredTabColor: String = "default",
    val starredTabIcon: String = "",
    val summarySort: SortMode = SortMode.DATE,
    val listSorts: Map<String, SortMode> = emptyMap(),
    val longPressAction: LongPressAction = LongPressAction.REORDER,
    val showAllTab: Boolean = true,
    val showStarredTab: Boolean = true,
) { val connected get() = serverUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank() }

/** Assign a start date when enabling recurrence. */
fun Task.withRepeat(rule: String, today: LocalDate = LocalDate.now()): Task =
    copy(recurrence = rule, dueDate = if (rule.isNotEmpty()) dueDate ?: today.toString() else dueDate)

/** Calculate the next occurrence using the retained recurrence anchor. */
fun nextOccurrence(task: Task, today: LocalDate = LocalDate.now()): String? {
    val start = task.dueDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    if (task.recurrence.isEmpty()) return null
    return runCatching {
        val anchor = LocalDate.parse(task.recurrenceAnchor ?: task.dueDate)
        val iterator = RecurrenceRule(task.recurrence).iterator(DateTime.parse(anchor.format(DateTimeFormatter.BASIC_ISO_DATE)))
        iterator.fastForward(DateTime.parse(maxOf(start, today).plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE)))
        if (iterator.hasNext()) iterator.nextDateTime().let { LocalDate.of(it.year, it.month + 1, it.dayOfMonth).toString() } else null
    }.getOrNull()
}

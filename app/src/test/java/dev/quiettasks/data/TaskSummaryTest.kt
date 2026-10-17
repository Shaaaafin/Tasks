package dev.quiettasks.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TaskSummaryTest {
    private val today = LocalDate.of(2026, 10, 17)
    @Test fun undatedTasksComeFirstThenPastTodayTomorrowAndLaterAcrossLists() {
        val tasks = listOf(
            Task(id = "later", listId = "a", title = "Later", dueDate = "2026-10-21"),
            Task(id = "tomorrow", listId = "b", title = "Tomorrow", dueDate = "2026-10-18"),
            Task(id = "undated", listId = "b", title = "Undated"),
            Task(id = "today", listId = "a", title = "Today", dueDate = "2026-10-17"),
            Task(id = "past", listId = "a", title = "Past", dueDate = "2026-10-16"),
            Task(id = "done", listId = "a", title = "Done", completedAt = 1),
            Task(id = "archived", listId = "a", title = "Archived", archived = true),
            Task(id = "deleted", listId = "a", title = "Deleted", deleted = true),
        )
        val groups = taskDateGroups(tasks, today)
        assertEquals(listOf("undated", "past", "today", "tomorrow", "later"), groups.flatMap { it.tasks }.map { it.id })
        assertEquals(listOf("No date", "Overdue · Fri, Oct 16", "Today", "Tomorrow", "Wed, Oct 21"), groups.map { it.label })
    }
    @Test fun earlierDeadlineIsShownOnceAndChildUsesItsOwnDate() {
        val parent = Task(id = "parent", listId = "a", title = "Parent", dueDate = "2026-10-21", deadline = "2026-10-17")
        val child = Task(id = "child", listId = "a", title = "Child", parentId = "parent", dueDate = "2026-10-18")
        assertEquals(listOf("Today", "Tomorrow"), taskDateGroups(listOf(parent, child), today).map { it.label })
        assertEquals(2, taskDateGroups(listOf(parent, child), today).sumOf { it.tasks.size })
    }
    @Test fun customOrderMovesAcrossDatesAndPrioritySortingKeepsStarsIndependent() {
        val a = Task(id = "a", listId = "a", title = "A", position = 0, starred = true)
        val b = Task(id = "b", listId = "b", title = "B", position = 1024, dueDate = "2026-10-17", priority = TaskPriority.HIGH)
        assertEquals(listOf("b", "a"), movedTaskIds(listOf("a", "b"), "a", 1))
        assertEquals(listOf("a", "b"), movedTaskIds(listOf("a", "b"), "a", -1))
        assertEquals(listOf(b, a), sortTasks(listOf(a, b), SortMode.PRIORITY))
        assertEquals(listOf(a, b), sortTasks(listOf(b, a), SortMode.MY_ORDER))
    }
    @Test fun choosingRepeatWithoutADateStartsTodayAndKeepsExistingDates() {
        val task = Task(listId = "l", title = "Daily")
        val repeated = task.withRepeat("FREQ=DAILY", today)
        assertEquals(today.toString(), repeated.dueDate)
        assertEquals("2026-10-18", nextOccurrence(repeated, today))
        assertEquals("2026-11-01", task.copy(dueDate = "2026-11-01").withRepeat("FREQ=WEEKLY", today).dueDate)
        assertNull(task.withRepeat("", today).dueDate)
    }
    @Test fun optionalTabsKeepStableListIdentifiers() {
        val lists = listOf(TaskList(id = "a", name = "A"), TaskList(id = "deleted", name = "Deleted", deleted = true))
        assertEquals(listOf(ALL_TASKS_TAB, STARRED_TAB, "a"), visibleTabIds(lists, Preferences()))
        assertEquals(listOf(STARRED_TAB, "a"), visibleTabIds(lists, Preferences(showAllTab = false)))
        assertEquals(listOf(ALL_TASKS_TAB, "a"), visibleTabIds(lists, Preferences(showStarredTab = false)))
        assertEquals(listOf("a"), visibleTabIds(lists, Preferences(showAllTab = false, showStarredTab = false)))
    }
    @Test fun recurrenceStopsAtItsDateOrOccurrenceLimit() {
        val start = Task(listId = "a", title = "Repeat", dueDate = today.toString(), recurrenceAnchor = today.toString(), recurrence = "FREQ=DAILY;INTERVAL=2;COUNT=2")
        assertEquals(today.plusDays(2).toString(), nextOccurrence(start, today))
        assertNull(nextOccurrence(start.copy(dueDate = today.plusDays(2).toString()), today))
        assertNull(nextOccurrence(start.copy(recurrence = "FREQ=DAILY;UNTIL=20261017"), today))
        val weekly = start.copy(recurrence = "FREQ=WEEKLY;INTERVAL=1;BYDAY=SA,SU;COUNT=3")
        assertEquals("2026-10-18", nextOccurrence(weekly, today))
        assertEquals("2026-10-24", nextOccurrence(weekly.copy(dueDate = "2026-10-18"), today))
        assertNull(nextOccurrence(weekly.copy(dueDate = "2026-10-24"), today))
    }

}

package dev.quiettasks.sync

import dev.quiettasks.data.Task
import dev.quiettasks.data.nextOccurrence
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.util.TimeZone

class ICalendarTest {
    private fun calendar(vararg lines: String) = (listOf("BEGIN:VCALENDAR", "VERSION:2.0", "BEGIN:VTODO") + lines + listOf("END:VTODO", "END:VCALENDAR")).joinToString("\r\n", postfix = "\r\n")
    @Test fun escapedTextAndUnicodeRoundTrip() {
        val task = Task(id = "a", listId = "list", title = "Buy milk; bread, eggs \\ tea", notes = "First line\nবাংলা 😀 ".repeat(40), dueDate = "2026-10-21", starred = true)
        val raw = ICalendar.encode(task)
        raw.split("\r\n").forEach { assertTrue("Fold exceeds 75 octets", it.toByteArray().size <= 75) }
        val decoded = ICalendar.decode(raw, "list", "https://example.com/a.ics", "\"1\"")
        assertEquals(task.title, decoded.title); assertEquals(task.notes, decoded.notes)
        assertEquals(task.dueDate, decoded.dueDate); assertNull(decoded.dueTime); assertTrue(decoded.starred)
    }
    @Test fun preservesUnknownFieldsTimezoneAndAlarmsOnEdit() {
        val raw = calendar("UID:from-server", "SUMMARY:Original", "X-PRIVATE-DATA:keep me", "DTSTART;VALUE=DATE:20261020", "DUE;VALUE=DATE:20261021", "BEGIN:VALARM", "ACTION:DISPLAY", "TRIGGER:-PT10M", "DESCRIPTION:Remember", "END:VALARM")
        val decoded = ICalendar.decode(raw, "list", "https://example.com/a", "1")
        val encoded = ICalendar.encode(decoded.copy(title = "Edited"))
        assertTrue(encoded.contains("X-PRIVATE-DATA:keep me")); assertTrue(encoded.contains("TRIGGER:-PT10M"))
        assertTrue(encoded.contains("DTSTART;VALUE=DATE:20261020")); assertEquals("from-server", ICalendar.uid(encoded))
        assertEquals("Edited", ICalendar.decode(encoded, "list", "https://example.com/a", "2").title)
    }
    @Test fun dueTimezoneIsConvertedToDeviceZone() {
        val old = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Dhaka"))
            val task = ICalendar.decode(calendar("UID:tz", "SUMMARY:Meeting", "DUE;TZID=America/New_York:20261017T090000"), "list", "href", "1")
            assertEquals("2026-10-17", task.dueDate); assertEquals("19:00", task.dueTime)
            assertTrue(ICalendar.encode(task).contains("DUE:20261017T130000Z"))
        } finally { TimeZone.setDefault(old) }
    }
    @Test fun completionAndHierarchyRoundTrip() {
        val task = Task(id = "child", listId = "list", title = "Subtask", parentId = "parent", position = 42, completedAt = 1792252800000)
        val decoded = ICalendar.decode(ICalendar.encode(task), "list", "href", "etag")
        assertEquals(task.parentId, decoded.parentId); assertEquals(42L, decoded.position); assertEquals(task.completedAt, decoded.completedAt)
    }
    @Test fun rejectsMultiTaskResourcesInsteadOfOverwritingThem() {
        val raw = "BEGIN:VCALENDAR\r\nBEGIN:VTODO\r\nUID:a\r\nEND:VTODO\r\nBEGIN:VTODO\r\nUID:b\r\nEND:VTODO\r\nEND:VCALENDAR\r\n"
        assertThrows(IllegalArgumentException::class.java) { ICalendar.decode(raw, "list", "href", "1") }
    }
    @Test fun monthlyRepeatUsesOriginalAnchorAcrossShortMonths() {
        val task = Task(listId = "l", title = "Monthly", dueDate = "2026-01-31", recurrence = "FREQ=MONTHLY")
        assertEquals("2026-03-31", nextOccurrence(task, LocalDate.of(2026, 2, 28)))
        assertEquals("2026-03-02", nextOccurrence(task.copy(recurrence = "FREQ=MONTHLY;BYDAY=MO"), LocalDate.of(2026, 2, 28)))
    }
    @Test fun customWeeklyRulesAndCountAreHonored() {
        val task = Task(listId = "l", title = "Repeat", dueDate = "2026-10-19", recurrenceAnchor = "2026-10-19", recurrence = "FREQ=WEEKLY;BYDAY=MO,FR;COUNT=3")
        assertEquals("2026-10-23", nextOccurrence(task, LocalDate.of(2026, 10, 19)))
        assertEquals("2026-10-26", nextOccurrence(task.copy(dueDate = "2026-10-23"), LocalDate.of(2026, 10, 23)))
        assertNull(nextOccurrence(task.copy(dueDate = "2026-10-26"), LocalDate.of(2026, 10, 26)))
    }
    @Test fun deadlineAndScheduledReminderRemainDistinct() {
        val task = Task(listId = "l", title = "Submit", dueDate = "2026-10-20", dueTime = "14:30", deadline = "2026-10-22")
        val raw = ICalendar.encode(task)
        assertTrue(raw.contains("DUE;VALUE=DATE:20261022"))
        val decoded = ICalendar.decode(raw, "l", "href", "etag")
        assertEquals(task.deadline, decoded.deadline); assertEquals(task.dueDate, decoded.dueDate); assertEquals(task.dueTime, decoded.dueTime)
    }
    @Test fun tagsAndPriorityRoundTripSeparatelyFromStarsIncludingEscapedCommas() {
        val original = Task(listId = "l", title = "Organized", priority = dev.quiettasks.data.TaskPriority.HIGH,
            tags = listOf("work", "groceries, local", "বাংলা", "path\\name"), starred = false)
        val raw = ICalendar.encode(original)
        assertTrue(raw.contains("PRIORITY:1")); assertTrue(raw.contains("CATEGORIES:work,groceries\\, local"))
        val decoded = ICalendar.decode(raw, "l", "href", "1")
        assertEquals(original.tags, decoded.tags); assertEquals(original.priority, decoded.priority); assertFalse(decoded.starred)
        val favorite = ICalendar.decode(ICalendar.encode(original.copy(priority = dev.quiettasks.data.TaskPriority.LOW, starred = true)), "l", "href", "2")
        assertTrue(favorite.starred); assertEquals(dev.quiettasks.data.TaskPriority.LOW, favorite.priority)
        val cleared = ICalendar.encode(decoded.copy(rawCalendar = raw, tags = emptyList(), priority = dev.quiettasks.data.TaskPriority.NONE))
        assertFalse(cleared.contains("CATEGORIES:")); assertEquals(dev.quiettasks.data.TaskPriority.NONE, ICalendar.decode(cleared, "l", "href", "3").priority)
    }
    @Test fun oldFavoritesRemainFavoritesAndExternalPrioritiesDoNotBecomeStars() {
        val old = calendar("UID:old", "SUMMARY:Old favorite", "PRIORITY:1").replace("VERSION:2.0", "VERSION:2.0\r\nPRODID:-//Quiet Tasks//Tasks//EN")
        val legacy = ICalendar.decode(old, "l", "href", "1")
        assertTrue(legacy.starred); assertEquals(dev.quiettasks.data.TaskPriority.NONE, legacy.priority)
        val external = ICalendar.decode(calendar("UID:external", "SUMMARY:Priority", "PRIORITY:2", "CATEGORIES:Work,work", "CATEGORIES:Personal"), "l", "href", "1")
        assertFalse(external.starred); assertEquals(dev.quiettasks.data.TaskPriority.HIGH, external.priority)
        assertEquals(listOf("Work", "Personal"), external.tags)
    }
    @Test fun archiveStateRoundTripsAndCanBeClearedWithoutCompletingTask() {
        val task = Task(listId = "l", title = "Archived", archived = true)
        val raw = ICalendar.encode(task)
        val decoded = ICalendar.decode(raw, "l", "href", "1")
        assertTrue(decoded.archived); assertFalse(decoded.completed)
        val restored = ICalendar.encode(decoded.copy(archived = false, rawCalendar = raw))
        assertFalse(restored.contains("X-QUIETTASKS-ARCHIVED:"))
        assertFalse(ICalendar.decode(restored, "l", "href", "2").archived)
    }

}

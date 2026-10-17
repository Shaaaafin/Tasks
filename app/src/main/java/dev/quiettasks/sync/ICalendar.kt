package dev.quiettasks.sync

import dev.quiettasks.data.Task
import dev.quiettasks.data.TaskPriority
import dev.quiettasks.data.normalizeTags
import java.time.*
import java.time.format.DateTimeFormatter

/** VTODO serialization with preservation of supported source properties. */
object ICalendar {
    private val date = DateTimeFormatter.BASIC_ISO_DATE
    private val utc = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC)
    private val local = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val managed = setOf("UID", "SUMMARY", "DESCRIPTION", "STATUS", "COMPLETED", "DUE", "PRIORITY", "CATEGORIES", "X-QUIETTASKS-STARRED", "X-QUIETTASKS-ARCHIVED", "RELATED-TO", "RRULE", "X-APPLE-SORT-ORDER", "LAST-MODIFIED", "DTSTAMP", "SEQUENCE", "X-QUIETTASKS-SCHEDULE", "X-QUIETTASKS-DEADLINE", "X-QUIETTASKS-ANCHOR")
    fun unfold(text: String): List<String> {
        val result = mutableListOf<String>()
        text.replace("\r\n", "\n").replace('\r', '\n').split('\n').forEach { line ->
            if ((line.startsWith(' ') || line.startsWith('\t')) && result.isNotEmpty()) result[result.lastIndex] += line.drop(1)
            else if (line.isNotEmpty()) result += line
        }
        return result
    }
    private fun name(line: String) = line.substringBefore(':').substringBefore(';').uppercase()
    private fun value(line: String) = line.substringAfter(':', "")
    fun escape(text: String) = text.replace("\\", "\\\\").replace("\r\n", "\n").replace('\r', '\n').replace("\n", "\\n").replace(";", "\\;").replace(",", "\\,")
    fun unescape(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            if (text[i] == '\\' && i + 1 < text.length) { i++; append(if (text[i] in "nN") '\n' else text[i]) }
            else append(text[i])
            i++
        }
    }
    private fun textList(text: String): List<String> {
        val parts = mutableListOf<String>(); val part = StringBuilder(); var escaped = false
        text.forEach { c ->
            if (c == ',' && !escaped) { parts += unescape(part.toString()); part.setLength(0) }
            else part.append(c)
            escaped = if (escaped) false else c == '\\'
        }
        parts += unescape(part.toString()); return parts
    }
    /** RFC 5545 folds by UTF-8 octets, never through the middle of a code point. */
    private fun fold(line: String): String = buildString {
        var count = 0
        line.codePoints().forEach { point ->
            val chars = String(Character.toChars(point))
            val size = chars.toByteArray(Charsets.UTF_8).size
            if (count + size > 75) { append("\r\n "); count = 1 }
            append(chars); count += size
        }
    }
    private fun properties(raw: String): List<String> {
        val lines = unfold(raw)
        require(lines.count { it.equals("BEGIN:VTODO", true) } == 1 && lines.count { it.equals("END:VTODO", true) } == 1) { "Expected one VTODO per resource" }
        require(lines.firstOrNull().equals("BEGIN:VCALENDAR", true) && lines.lastOrNull().equals("END:VCALENDAR", true)) { "Invalid calendar resource" }
        require(lines.none { it.equals("BEGIN:VEVENT", true) }) { "Mixed calendar resource is not supported" }
        var inTodo = false
        var depth = 0
        return buildList {
            lines.forEach { line ->
                when {
                    line.equals("BEGIN:VTODO", true) -> inTodo = true
                    line.equals("END:VTODO", true) -> inTodo = false
                    inTodo && line.startsWith("BEGIN:", true) -> depth++
                    inTodo && line.startsWith("END:", true) -> depth--
                    inTodo && depth == 0 -> add(line)
                }
            }
        }
    }
    fun uid(raw: String): String = properties(raw).firstOrNull { name(it) == "UID" }?.let { unescape(value(it)) }?.takeIf { it.isNotBlank() && it.length <= 2048 } ?: error("Missing task UID")
    fun decode(raw: String, listId: String, href: String, etag: String?): Task {
        val lines = properties(raw)
        fun prop(key: String) = lines.firstOrNull { name(it) == key }
        fun text(key: String) = prop(key)?.let { unescape(value(it)) }.orEmpty()
        val deadline = prop("X-QUIETTASKS-DEADLINE")?.let { LocalDate.parse(value(it), date).toString() }
        val dueLine = prop("X-QUIETTASKS-SCHEDULE") ?: if (deadline == null) prop("DUE") else null
        val due = dueLine?.let { value(it) }
        var dueDate: String? = null
        var dueTime: String? = null
        if (due != null) {
            if (due.length == 8) dueDate = LocalDate.parse(due, date).toString()
            else {
                val moment = if (due.endsWith('Z')) Instant.from(utc.parse(due)).atZone(ZoneId.systemDefault())
                else {
                    val zone = Regex("(?:^|;)TZID=([^;:]+)", RegexOption.IGNORE_CASE).find(dueLine.substringBefore(':'))?.groupValues?.get(1)?.trim('"')
                    LocalDateTime.parse(due, local).atZone(zone?.let(ZoneId::of) ?: ZoneId.systemDefault()).withZoneSameInstant(ZoneId.systemDefault())
                }
                dueDate = moment.toLocalDate().toString(); dueTime = moment.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            }
        }
        val modified = prop("LAST-MODIFIED")?.let { runCatching { Instant.from(utc.parse(value(it))).toEpochMilli() }.getOrNull() } ?: System.currentTimeMillis()
        val completed = text("STATUS").equals("COMPLETED", true) || prop("COMPLETED") != null
        val priority = text("PRIORITY").toIntOrNull() ?: 0
        val legacyFavorite = prop("X-QUIETTASKS-STARRED") == null && priority == 1 && unfold(raw).any { it == "PRODID:-//Quiet Tasks//Tasks//EN" }
        return Task(id = uid(raw), listId = listId, title = text("SUMMARY").ifEmpty { "Untitled task" }, notes = text("DESCRIPTION"),
            starred = text("X-QUIETTASKS-STARRED").equals("TRUE", true) || legacyFavorite, dueDate = dueDate, dueTime = dueTime,
            priority = if (legacyFavorite) TaskPriority.NONE else TaskPriority.fromIcal(priority),
            tags = normalizeTags(lines.filter { name(it) == "CATEGORIES" }.flatMap { textList(value(it)) }),
            archived = text("X-QUIETTASKS-ARCHIVED").equals("TRUE", true),
            parentId = text("RELATED-TO").ifEmpty { null }, position = text("X-APPLE-SORT-ORDER").toLongOrNull() ?: modified,
            completedAt = if (completed) prop("COMPLETED")?.let { runCatching { Instant.from(utc.parse(value(it))).toEpochMilli() }.getOrNull() } ?: modified else null,
            recurrence = text("RRULE"), modifiedAt = modified, remoteHref = href, etag = etag, rawCalendar = raw, dirty = false, deadline = deadline,
            recurrenceAnchor = prop("X-QUIETTASKS-ANCHOR")?.let { LocalDate.parse(value(it), date).toString() } ?: prop("DTSTART")?.let { runCatching { LocalDate.parse(value(it).take(8), date).toString() }.getOrNull() } ?: dueDate)
    }
    fun encode(task: Task, parentUid: String? = task.parentId): String {
        val old = task.rawCalendar?.let(::unfold)
        if (task.rawCalendar != null) properties(task.rawCalendar)
        val oldProps = task.rawCalendar?.let(::properties).orEmpty()
        val uid = task.rawCalendar?.let(::uid) ?: task.id
        val sequence = oldProps.firstOrNull { name(it) == "SEQUENCE" }?.let { value(it).toIntOrNull() } ?: 0
        val generated = buildList {
            add("UID:${escape(uid)}"); add("SUMMARY:${escape(task.title)}")
            if (task.notes.isNotEmpty()) add("DESCRIPTION:${escape(task.notes)}")
            add("STATUS:${if (task.completed) "COMPLETED" else "NEEDS-ACTION"}")
            task.completedAt?.let { add("COMPLETED:${utc.format(Instant.ofEpochMilli(it))}") }
            if (task.dueDate != null) {
                val d = LocalDate.parse(task.dueDate)
                val due = if (task.dueTime == null) "DUE;VALUE=DATE:${date.format(d)}"
                else "DUE:${utc.format(d.atTime(LocalTime.parse(task.dueTime)).atZone(ZoneId.systemDefault()).toInstant())}"
                add(due.replaceFirst("DUE", "X-QUIETTASKS-SCHEDULE"))
                if (task.deadline == null) add(due)
                if (task.recurrence.isNotEmpty() && oldProps.none { name(it) == "DTSTART" }) add(due.replaceFirst("DUE", "DTSTART"))
            }
            task.deadline?.let { deadline ->
                add("DUE;VALUE=DATE:${date.format(LocalDate.parse(deadline))}")
                add("X-QUIETTASKS-DEADLINE:${date.format(LocalDate.parse(deadline))}")
            }
            add("PRIORITY:${task.priority.ical}")
            add("X-QUIETTASKS-STARRED:${if (task.starred) "TRUE" else "FALSE"}")
            if (task.archived) add("X-QUIETTASKS-ARCHIVED:TRUE")
            if (task.tags.isNotEmpty()) add("CATEGORIES:${normalizeTags(task.tags).joinToString(",", transform = ::escape)}")
            parentUid?.let { add("RELATED-TO;RELTYPE=PARENT:${escape(it)}") }
            if (task.recurrence.isNotEmpty()) add("RRULE:${task.recurrence}")
            if (task.recurrence.isNotEmpty()) (task.recurrenceAnchor ?: task.dueDate)?.let { add("X-QUIETTASKS-ANCHOR:${date.format(LocalDate.parse(it))}") }
            add("X-APPLE-SORT-ORDER:${task.position}")
            add("LAST-MODIFIED:${utc.format(Instant.ofEpochMilli(task.modifiedAt))}")
            add("DTSTAMP:${utc.format(Instant.ofEpochMilli(task.modifiedAt))}")
            add("SEQUENCE:${sequence + 1}")
        }
        val result = if (old == null) listOf("BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//Quiet Tasks//Tasks//EN", "BEGIN:VTODO") + generated + listOf("END:VTODO", "END:VCALENDAR")
        else buildList {
            var inTodo = false; var depth = 0
            old.forEach { line ->
                when {
                    line.equals("BEGIN:VTODO", true) -> { inTodo = true; add(line); addAll(generated) }
                    line.equals("END:VTODO", true) -> { inTodo = false; add(line) }
                    inTodo && line.startsWith("BEGIN:", true) -> { depth++; add(line) }
                    inTodo && line.startsWith("END:", true) -> { depth--; add(line) }
                    !inTodo || depth > 0 || name(line) !in managed -> add(line)
                }
            }
        }
        return result.joinToString("\r\n", postfix = "\r\n", transform = ::fold)
    }
}

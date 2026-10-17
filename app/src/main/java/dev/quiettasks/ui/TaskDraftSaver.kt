package dev.quiettasks.ui

import androidx.compose.runtime.saveable.Saver
import dev.quiettasks.data.Task

/** Save editable fields, not server payloads or credentials, across activity recreation. */
private fun saveDraft(it: Task): ArrayList<String> = arrayListOf(it.id, it.listId, it.title, it.notes, it.starred.toString(), it.dueDate.orEmpty(), it.dueTime.orEmpty(), it.parentId.orEmpty(), it.position.toString(), it.completedAt?.toString().orEmpty(), it.recurrence, it.deadline.orEmpty(), it.recurrenceAnchor.orEmpty(), it.priority.name, org.json.JSONArray(it.tags).toString(), it.archived.toString())
private fun restoreDraft(values: List<String>): Task = Task(id = values[0], listId = values[1], title = values[2], notes = values[3], starred = values[4].toBoolean(), dueDate = values[5].ifEmpty { null }, dueTime = values[6].ifEmpty { null }, parentId = values[7].ifEmpty { null }, position = values[8].toLong(), completedAt = values[9].toLongOrNull(), recurrence = values[10], deadline = values[11].ifEmpty { null }, recurrenceAnchor = values[12].ifEmpty { null }, priority = values.getOrNull(13)?.let(dev.quiettasks.data.TaskPriority::valueOf) ?: dev.quiettasks.data.TaskPriority.NONE, tags = values.getOrNull(14)?.let { org.json.JSONArray(it) }?.let { array -> List(array.length()) { array.getString(it) } }.orEmpty(), archived = values.getOrNull(15)?.toBoolean() ?: false)
val TaskDraftSaver = Saver<Task, ArrayList<String>>(save = { saveDraft(it) }, restore = ::restoreDraft)
val SubtaskDraftSaver = Saver<List<Task>, String>(
    save = { tasks -> org.json.JSONArray(tasks.map { org.json.JSONArray(saveDraft(it)) }).toString() },
    restore = { json -> org.json.JSONArray(json).let { array -> List(array.length()) { index ->
        array.getJSONArray(index).let { fields -> restoreDraft(List(fields.length()) { fields.getString(it) }) }
    } } },
)


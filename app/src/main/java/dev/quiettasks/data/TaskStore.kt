package dev.quiettasks.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class TaskStore(context: Context) : SQLiteOpenHelper(context, "tasks.db", null, 3) {
    init { setWriteAheadLoggingEnabled(true) }
    private val changesMutable = MutableStateFlow(0L)
    val changes = changesMutable.asStateFlow()
    private val mutable = MutableStateFlow(Snapshot())
    val state = mutable.asStateFlow()
    override fun onConfigure(db: SQLiteDatabase) { db.setForeignKeyConstraintsEnabled(true) }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE lists (id TEXT PRIMARY KEY, name TEXT NOT NULL, position INTEGER NOT NULL, remoteUrl TEXT, dirty INTEGER NOT NULL, deleted INTEGER NOT NULL, revision INTEGER NOT NULL, color TEXT NOT NULL DEFAULT 'default', icon TEXT NOT NULL DEFAULT '')")
        db.execSQL("CREATE TABLE tasks (id TEXT PRIMARY KEY, listId TEXT NOT NULL REFERENCES lists(id), title TEXT NOT NULL, notes TEXT NOT NULL, starred INTEGER NOT NULL, dueDate TEXT, dueTime TEXT, parentId TEXT, position INTEGER NOT NULL, completedAt INTEGER, recurrence TEXT NOT NULL, modifiedAt INTEGER NOT NULL, remoteHref TEXT, etag TEXT, rawCalendar TEXT, dirty INTEGER NOT NULL, deleted INTEGER NOT NULL, revision INTEGER NOT NULL, deadline TEXT, recurrenceAnchor TEXT, priority INTEGER NOT NULL DEFAULT 0, tags TEXT NOT NULL DEFAULT '[]')")
        db.execSQL("CREATE INDEX task_list ON tasks(listId, deleted, position)")
        db.execSQL("CREATE INDEX task_remote ON tasks(listId, remoteHref)")
        db.execSQL("ALTER TABLE tasks ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
        db.insertOrThrow("lists", null, listValues(TaskList(name = "My Tasks")))
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE lists ADD COLUMN color TEXT NOT NULL DEFAULT 'default'")
            db.execSQL("ALTER TABLE lists ADD COLUMN icon TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE tasks ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE tasks ADD COLUMN tags TEXT NOT NULL DEFAULT '[]'")
            // Carry forward category/priority properties retained by the previous codec.
            db.query("tasks", arrayOf("id", "listId", "rawCalendar", "remoteHref", "etag"), "rawCalendar IS NOT NULL", null, null, null, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val decoded = runCatching { dev.quiettasks.sync.ICalendar.decode(cursor.s("rawCalendar"), cursor.s("listId"), cursor.n("remoteHref").orEmpty(), cursor.n("etag")) }.getOrNull() ?: continue
                    db.update("tasks", ContentValues().apply { put("priority", decoded.priority.ical); put("tags", org.json.JSONArray(decoded.tags).toString()) }, "id=?", arrayOf(cursor.s("id")))
                }
            }
        }
        if (oldVersion < 3) db.execSQL("ALTER TABLE tasks ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
    }
    private fun listValues(v: TaskList) = ContentValues().apply {
        put("id", v.id); put("name", v.name); put("position", v.position); put("remoteUrl", v.remoteUrl)
        put("dirty", v.dirty); put("deleted", v.deleted); put("revision", v.revision)
        put("color", v.color); put("icon", v.icon)
    }
    private fun taskValues(v: Task) = ContentValues().apply {
        put("id", v.id); put("listId", v.listId); put("title", v.title); put("notes", v.notes)
        put("starred", v.starred); put("dueDate", v.dueDate); put("dueTime", v.dueTime); put("parentId", v.parentId)
        put("position", v.position); put("completedAt", v.completedAt); put("recurrence", v.recurrence)
        put("modifiedAt", v.modifiedAt); put("remoteHref", v.remoteHref); put("etag", v.etag)
        put("rawCalendar", v.rawCalendar); put("dirty", v.dirty); put("deleted", v.deleted); put("revision", v.revision); put("deadline", v.deadline)
        put("recurrenceAnchor", v.recurrenceAnchor)
        put("priority", v.priority.ical); put("tags", org.json.JSONArray(normalizeTags(v.tags)).toString())
        put("archived", v.archived)
    }
    private fun Cursor.s(key: String): String = getString(getColumnIndexOrThrow(key))
    private fun Cursor.n(key: String): String? = getColumnIndexOrThrow(key).let { if (isNull(it)) null else getString(it) }
    private fun Cursor.l(key: String): Long = getLong(getColumnIndexOrThrow(key))
    private fun Cursor.b(key: String): Boolean = getInt(getColumnIndexOrThrow(key)) != 0
    private fun Cursor.task() = Task(s("id"), s("listId"), s("title"), s("notes"), b("starred"), n("dueDate"), n("dueTime"), n("parentId"), l("position"), n("completedAt")?.toLong(), s("recurrence"), l("modifiedAt"), n("remoteHref"), n("etag"), n("rawCalendar"), b("dirty"), b("deleted"), l("revision"), n("deadline"), n("recurrenceAnchor"), TaskPriority.fromIcal(l("priority").toInt()), org.json.JSONArray(s("tags")).let { array -> List(array.length()) { array.getString(it) } }, b("archived"))
    private fun Cursor.list() = TaskList(s("id"), s("name"), l("position"), n("remoteUrl"), b("dirty"), b("deleted"), l("revision"), s("color"), s("icon"))
    @Synchronized fun snapshot(): Snapshot {
        val lists = readableDatabase.query("lists", null, null, null, null, null, "position").use { c -> buildList { while (c.moveToNext()) add(c.list()) } }
        val tasks = readableDatabase.query("tasks", null, null, null, null, null, "position").use { c -> buildList { while (c.moveToNext()) add(c.task()) } }
        return Snapshot(lists, tasks)
    }
    suspend fun load() = withContext(Dispatchers.IO) { synchronized(this@TaskStore) { publish(false) } }
    private fun publish(localChange: Boolean) { mutable.value = snapshot(); if (localChange) changesMutable.value++ }
    private fun task(id: String): Task? = readableDatabase.query("tasks", null, "id=?", arrayOf(id), null, null, null).use { if (it.moveToFirst()) it.task() else null }
    private fun put(task: Task) {
        val values = taskValues(task)
        if (writableDatabase.update("tasks", values, "id=?", arrayOf(task.id)) == 0) writableDatabase.insertOrThrow("tasks", null, values)
    }
    private fun put(list: TaskList) {
        val values = listValues(list)
        if (writableDatabase.update("lists", values, "id=?", arrayOf(list.id)) == 0) writableDatabase.insertOrThrow("lists", null, values)
    }
    @Synchronized fun saveTask(value: Task) {
        transaction { saveTaskRecord(value) }
        publish(true)
    }
    private fun transaction(block: () -> Unit) {
        writableDatabase.beginTransaction()
        try { block(); writableDatabase.setTransactionSuccessful() }
        finally { writableDatabase.endTransaction() }
    }
    private fun saveTaskRecord(value: Task) {
        require(value.title.isNotBlank()) { "Add a task title" }
        val previous = task(value.id)
        require(snapshot().lists.any { it.id == value.listId && !it.deleted }) { "This list no longer exists" }
        require(value.recurrence.isEmpty() || value.parentId == null) { "Set repeat on the parent task" }
        require(value.parentId == null || (value.parentId != value.id && task(value.parentId)?.let { !it.deleted && it.listId == value.listId && it.parentId == null } == true)) { "Choose a parent in this list" }
        val changedList = previous != null && previous.listId != value.listId
        require(!changedList || snapshot().tasks.none { !it.deleted && it.parentId == value.id }) { "Move subtasks before moving this task" }
        if (changedList && previous!!.remoteHref != null) put(previous.copy(id = java.util.UUID.randomUUID().toString(), deleted = true, dirty = true, revision = previous.revision + 1))
        val prepared = value.copy(title = value.title.trim(), tags = normalizeTags(value.tags), modifiedAt = System.currentTimeMillis(), dirty = true,
            recurrenceAnchor = if (value.recurrence.isEmpty()) null else if (previous == null || previous.recurrence != value.recurrence || previous.dueDate != value.dueDate) value.dueDate else previous.recurrenceAnchor ?: value.dueDate,
            remoteHref = if (changedList) null else previous?.remoteHref, etag = if (changedList) null else previous?.etag,
            rawCalendar = previous?.rawCalendar, revision = (previous?.revision ?: 0) + 1)
        if (previous == null || prepared.copy(dirty = previous.dirty, modifiedAt = previous.modifiedAt, revision = previous.revision) != previous) put(prepared)
    }
    /** Save inline edits and completion as one change for observers and synchronization. */
    @Synchronized fun saveTaskFamily(parent: Task, children: List<Task>, toggleParent: Boolean = false) {
        require(children.map { it.id }.distinct().size == children.size && children.none { it.id == parent.id }) { "Duplicate subtasks" }
        require(parent.parentId == null || children.isEmpty()) { "Subtasks cannot contain subtasks" }
        val current = snapshot().tasks
        val retained = children.filter { it.title.isNotBlank() }
        transaction {
            saveTaskRecord(parent)
            current.filter { it.parentId == parent.id && !it.deleted && !it.archived && retained.none { child -> child.id == it.id } }.forEach {
                put(it.copy(deleted = true, dirty = true, revision = it.revision + 1, modifiedAt = System.currentTimeMillis()))
            }
            retained.forEach { child ->
                require(current.none { it.id == child.id && it.parentId != parent.id }) { "Choose a subtask of this task" }
                saveTaskRecord(child.copy(parentId = parent.id, listId = parent.listId, recurrence = ""))
            }
            val saved = task(parent.id)!!
            if (toggleParent && !saved.archived && !saved.deleted) toggleRecord(saved)
            else if (!saved.archived && !saved.deleted && !saved.completed && retained.isNotEmpty() && retained.all { it.completed }) completeFamily(saved, snapshot().tasks, System.currentTimeMillis())
            else if (saved.completed && retained.any { !it.completed }) put(saved.copy(completedAt = null, dirty = true, revision = saved.revision + 1))
        }
        publish(true)
    }
    @Synchronized fun toggle(id: String) {
        val original = task(id) ?: return
        if (original.deleted || original.archived) return
        transaction { toggleRecord(original) }
        publish(true)
    }
    private fun toggleRecord(original: Task) {
        val now = System.currentTimeMillis()
        val all = snapshot().tasks.filter { !it.deleted && !it.archived }
        if (original.parentId != null) {
            put(original.copy(completedAt = if (original.completed) null else now, dirty = true, modifiedAt = now, revision = original.revision + 1))
            val parent = all.firstOrNull { it.id == original.parentId } ?: return
            if (original.completed && parent.completed) put(parent.copy(completedAt = null, dirty = true, modifiedAt = now, revision = parent.revision + 1))
            else if (!original.completed && !parent.completed && all.filter { it.parentId == parent.id && it.id != original.id }.all { it.completed })
                completeFamily(parent, snapshot().tasks, now)
        } else if (original.completed) {
            (listOf(original) + all.filter { it.parentId == original.id && it.completed }).forEach {
                put(it.copy(completedAt = null, dirty = true, modifiedAt = now, revision = it.revision + 1))
            }
        } else completeFamily(original, all, now)
    }
    private fun completeFamily(parent: Task, all: List<Task>, now: Long) {
        val children = all.filter { it.parentId == parent.id && !it.deleted && !it.archived }
        (listOf(parent) + children).forEach {
            put(it.copy(completedAt = it.completedAt ?: now, recurrence = "", dirty = true, modifiedAt = now, revision = it.revision + 1))
        }
        val nextDate = nextOccurrence(parent) ?: return
        val nextId = java.util.UUID.randomUUID().toString()
        val offset = java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(parent.dueDate), java.time.LocalDate.parse(nextDate))
        fun shift(date: String?) = date?.let { java.time.LocalDate.parse(it).plusDays(offset).toString() }
        put(parent.copy(id = nextId, dueDate = nextDate, completedAt = null, deadline = shift(parent.deadline),
            remoteHref = null, etag = null, rawCalendar = null, dirty = true, revision = 1, modifiedAt = now))
        children.forEach { child -> put(child.copy(id = java.util.UUID.randomUUID().toString(), parentId = nextId,
            dueDate = shift(child.dueDate), deadline = shift(child.deadline), completedAt = null, recurrence = "", recurrenceAnchor = null,
            remoteHref = null, etag = null, rawCalendar = null, dirty = true, revision = 1, modifiedAt = now)) }
    }
    @Synchronized fun deleteTask(id: String, deleted: Boolean = true) {
        writableDatabase.beginTransaction()
        try {
            (snapshot().tasks.filter { it.id == id || it.parentId == id }).forEach { put(it.copy(deleted = deleted, dirty = true, revision = it.revision + 1, modifiedAt = System.currentTimeMillis())) }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        publish(true)
    }
    @Synchronized fun clearCompleted(listId: String?, starredOnly: Boolean, tag: String?): Int {
        val tasks = snapshot().tasks.filter { !it.deleted }
        val ids = tasks.filter { !it.archived && it.completed && (it.parentId == null || tasks.none { parent -> parent.id == it.parentId && !parent.completed }) && (listId == null || it.listId == listId) && (!starredOnly || it.starred) &&
            (tag == null || it.tags.any { value -> value.equals(tag, true) }) }.mapTo(HashSet()) { it.id }
        // Keep any parent that contains a task outside the selected view, including deep imports.
        do { val removed = ids.removeAll(tasks.filter { it.id in ids && tasks.any { child -> child.parentId == it.id && child.id !in ids } }.map { it.id }.toSet()) } while (removed)
        if (ids.isEmpty()) return 0
        writableDatabase.beginTransaction()
        try {
            tasks.filter { it.id in ids }.forEach { put(it.copy(deleted = true, dirty = true, revision = it.revision + 1, modifiedAt = System.currentTimeMillis())) }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        publish(true); return ids.size
    }
    @Synchronized fun archiveTask(id: String, archived: Boolean) {
        val tasks = snapshot().tasks.filter { !it.deleted }
        if (tasks.none { it.id == id }) return
        val ids = hashSetOf(id)
        do { val added = ids.addAll(tasks.filter { it.parentId in ids }.map { it.id }) } while (added)
        if (!archived) {
            var parent = tasks.firstOrNull { it.id == id }?.parentId
            while (parent != null && ids.add(parent)) parent = tasks.firstOrNull { it.id == parent }?.parentId
        }
        writableDatabase.beginTransaction()
        try {
            tasks.filter { it.id in ids && it.archived != archived }.forEach { put(it.copy(archived = archived, dirty = true, revision = it.revision + 1, modifiedAt = System.currentTimeMillis())) }
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        publish(true)
    }
    @Synchronized fun saveList(list: TaskList) {
        require(list.name.isNotBlank()) { "Add a list name" }
        val previous = snapshot().lists.firstOrNull { it.id == list.id }
        val renamed = previous == null || previous.name != list.name.trim()
        put(list.copy(name = list.name.trim(), remoteUrl = previous?.remoteUrl,
            dirty = renamed || previous?.dirty == true, revision = (previous?.revision ?: 0) + 1))
        publish(renamed)
    }
    @Synchronized fun deleteList(id: String) {
        val current = snapshot()
        require(current.lists.count { !it.deleted } > 1) { "Keep at least one list" }
        val list = current.lists.first { it.id == id }
        // Resource tombstones are synchronized individually with If-Match. The empty remote
        // collection is retained: deleting an entire collection could erase concurrent remote edits.
        writableDatabase.beginTransaction()
        try {
            current.tasks.filter { it.listId == id }.forEach { put(it.copy(deleted = true, dirty = true, revision = it.revision + 1)) }
            put(list.copy(deleted = true, dirty = false, revision = list.revision + 1))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        publish(true)
    }
    @Synchronized fun reorder(ids: List<String>) {
        require(ids.distinct().size == ids.size) { "Duplicate tasks in order" }
        val active = activeTaskRows(snapshot().tasks).associateBy { it.id }
        writableDatabase.beginTransaction()
        try { ids.forEachIndexed { index, id -> active[id]?.takeIf { it.position != index * 1024L }?.let { put(it.copy(position = index * 1024L, dirty = true, revision = it.revision + 1)) } }; writableDatabase.setTransactionSuccessful() }
        finally { writableDatabase.endTransaction() }
        publish(true)
    }
    @Synchronized fun applyRemote(list: TaskList) {
        val local = snapshot().lists.firstOrNull { it.id == list.id }
        put(if (local == null) list else list.copy(color = local.color, icon = local.icon)); publish(false)
    }
    @Synchronized fun markListSynced(sent: TaskList, url: String) {
        val current = snapshot().lists.firstOrNull { it.id == sent.id } ?: return
        put(current.copy(remoteUrl = url, dirty = current.revision != sent.revision)); publish(false)
    }
    @Synchronized fun applyRemote(remote: Task): Boolean {
        val current = task(remote.id)
        if (current?.dirty == true) return false
        put(remote.copy(dirty = false, revision = (current?.revision ?: 0) + 1)); publish(false); return true
    }
    @Synchronized fun markSynced(sent: Task, href: String, etag: String?, raw: String) {
        val current = task(sent.id) ?: return
        put(current.copy(remoteHref = href, etag = etag, rawCalendar = raw, dirty = current.revision != sent.revision)); publish(false)
    }
    @Synchronized fun discardSyncedDeletion(sent: Task) {
        val current = task(sent.id) ?: return
        if (current.revision == sent.revision && current.deleted) writableDatabase.delete("tasks", "id=?", arrayOf(sent.id))
        else put(current.copy(remoteHref = null, etag = null, dirty = true))
        publish(false)
    }
    @Synchronized fun remoteDeleted(id: String) {
        val current = task(id) ?: return
        if (!current.dirty) writableDatabase.delete("tasks", "id=?", arrayOf(id))
        publish(false)
    }
    @Synchronized fun preserveConflict(sent: Task, remote: Task?) {
        val current = task(sent.id) ?: return
        writableDatabase.beginTransaction()
        try {
            if (!current.deleted) put(current.copy(id = java.util.UUID.randomUUID().toString(), title = "${current.title} (local copy)", remoteHref = null, etag = null, rawCalendar = null, dirty = true, revision = 1))
            if (remote != null) put(remote.copy(id = sent.id, listId = sent.listId, dirty = false, revision = current.revision + 1))
            else writableDatabase.delete("tasks", "id=?", arrayOf(sent.id))
            writableDatabase.setTransactionSuccessful()
        } finally { writableDatabase.endTransaction() }
        publish(false)
    }
    @Synchronized fun restoreTask(value: Task) { put(value.copy(deleted = false, remoteHref = task(value.id)?.remoteHref, dirty = true, revision = (task(value.id)?.revision ?: value.revision) + 1)); publish(true) }
    @Synchronized fun detachRemote() {
        writableDatabase.execSQL("UPDATE tasks SET remoteHref=NULL, etag=NULL, dirty=1, revision=revision+1")
        writableDatabase.execSQL("UPDATE lists SET remoteUrl=NULL, dirty=1, revision=revision+1")
        publish(false)
    }
}

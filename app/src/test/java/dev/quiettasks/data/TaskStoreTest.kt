package dev.quiettasks.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TaskStoreTest {
    private lateinit var store: TaskStore
    private lateinit var list: TaskList
    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        context.deleteDatabase("tasks.db"); store = TaskStore(context); store.load(); list = store.snapshot().lists.single()
    }
    @After fun close() { store.close() }
    @Test fun changesPersistAcrossReopeningDatabase() {
        val task = Task(listId = list.id, title = "Offline task", notes = "Details")
        store.saveTask(task); store.close()
        store = TaskStore(ApplicationProvider.getApplicationContext())
        assertEquals("Offline task", store.snapshot().tasks.single().title); assertTrue(store.snapshot().tasks.single().dirty)
    }
    @Test fun editsMadeDuringUploadRemainDirty() {
        store.saveTask(Task(listId = list.id, title = "Original"))
        val sent = store.snapshot().tasks.single()
        store.saveTask(sent.copy(title = "Newer edit"))
        store.markSynced(sent, "https://example.com/a.ics", "v1", "old calendar")
        val current = store.snapshot().tasks.single()
        assertEquals("Newer edit", current.title); assertTrue(current.dirty); assertEquals("v1", current.etag)
    }
    @Test fun deletionStaysAsTombstoneUntilServerAcknowledges() {
        store.saveTask(Task(listId = list.id, title = "Delete me"))
        val sent = store.snapshot().tasks.single()
        store.markSynced(sent, "https://example.com/a.ics", "v1", "calendar")
        store.deleteTask(sent.id)
        val deleted = store.snapshot().tasks.single(); assertTrue(deleted.deleted); assertTrue(deleted.dirty)
        store.discardSyncedDeletion(deleted); assertTrue(store.snapshot().tasks.isEmpty())
    }
    @Test fun conflictPreservesBothVersions() {
        store.saveTask(Task(listId = list.id, title = "Local edit"))
        val local = store.snapshot().tasks.single()
        store.preserveConflict(local, local.copy(title = "Server edit", dirty = false, remoteHref = "https://example.com/a"))
        val tasks = store.snapshot().tasks
        assertEquals(2, tasks.size); assertTrue(tasks.any { it.title == "Server edit" && !it.dirty }); assertTrue(tasks.any { it.title == "Local edit (local copy)" && it.dirty && it.remoteHref == null })
    }
    @Test fun completionAlsoCompletesChildren() {
        val parent = Task(listId = list.id, title = "Parent"); store.saveTask(parent)
        store.saveTask(Task(listId = list.id, title = "Child", parentId = parent.id))
        store.toggle(parent.id); assertTrue(store.snapshot().tasks.all { it.completed })
    }
    @Test fun cannotCreateCircularHierarchy() {
        val task = Task(listId = list.id, title = "Parent"); store.saveTask(task)
        assertThrows(IllegalArgumentException::class.java) { store.saveTask(task.copy(parentId = task.id)) }
    }
    @Test fun appearanceTagsPriorityAndOrderPersistWithoutWakingSyncForColors() {
        store.applyRemote(list.copy(remoteUrl = "https://example.com/list/", dirty = false))
        val before = store.changes.value
        store.saveList(store.snapshot().lists.single().copy(color = "green", icon = "🌿"))
        assertEquals(before, store.changes.value); assertFalse(store.snapshot().lists.single().dirty)
        store.applyRemote(list.copy(name = "Remote rename", dirty = false))
        assertEquals("green", store.snapshot().lists.single().color)
        assertEquals("🌿", store.snapshot().lists.single().icon)
        store.saveTask(Task(id = "a", listId = list.id, title = "A", priority = TaskPriority.HIGH, tags = listOf("work", "#Work", " errands ")))
        store.saveTask(Task(id = "b", listId = list.id, title = "B"))
        store.reorder(listOf("b", "a")); store.close()
        store = TaskStore(ApplicationProvider.getApplicationContext())
        val snapshot = store.snapshot()
        assertEquals(listOf("b", "a"), sortTasks(snapshot.tasks, SortMode.MY_ORDER).map { it.id })
        assertEquals(TaskPriority.HIGH, snapshot.tasks.first { it.id == "a" }.priority)
        assertEquals(listOf("work", "errands"), snapshot.tasks.first { it.id == "a" }.tags)
        assertEquals("🌿", snapshot.lists.single().icon)
    }
    @Test fun clearingTaggedCompletedTasksNeverDeletesHiddenSubtasks() {
        store.saveTask(Task(id = "parent", listId = list.id, title = "Parent", completedAt = 1, tags = listOf("work")))
        store.saveTask(Task(id = "child", listId = list.id, title = "Hidden child", parentId = "parent", completedAt = 1, tags = listOf("personal")))
        store.saveTask(Task(id = "other", listId = list.id, title = "Visible", completedAt = 1, tags = listOf("work")))
        assertEquals(1, store.clearCompleted(null, false, "work"))
        assertEquals(setOf("parent", "child"), store.snapshot().tasks.filter { !it.deleted }.map { it.id }.toSet())
        assertEquals(2, store.clearCompleted(null, false, null))
        assertTrue(store.snapshot().tasks.all { it.deleted })
    }
    @Test fun repeatingTaskCarriesTagsAndPriorityToTheNextOccurrence() {
        val today = java.time.LocalDate.now()
        store.saveTask(Task(listId = list.id, title = "Daily", dueDate = today.toString(), recurrence = "FREQ=DAILY", priority = TaskPriority.HIGH, tags = listOf("health")))
        store.toggle(store.snapshot().tasks.single().id)
        val active = store.snapshot().tasks.single { !it.completed }
        assertEquals(today.plusDays(1).toString(), active.dueDate); assertEquals("FREQ=DAILY", active.recurrence)
        assertEquals(TaskPriority.HIGH, active.priority); assertEquals(listOf("health"), active.tags)
        assertEquals("", store.snapshot().tasks.single { it.completed }.recurrence)
    }
    @Test fun upgradingVersionOnePreservesTasksCredentialsReferencesAndImportedCategories() {
        store.saveTask(Task(id = "old", listId = list.id, title = "Existing task", starred = true, dueDate = "2026-10-20"))
        val db = store.writableDatabase
        // Recreate the schema actually shipped in 0.1.0, including its original payload.
        db.execSQL("ALTER TABLE lists DROP COLUMN color"); db.execSQL("ALTER TABLE lists DROP COLUMN icon")
        db.execSQL("ALTER TABLE tasks DROP COLUMN archived"); db.execSQL("ALTER TABLE tasks DROP COLUMN priority"); db.execSQL("ALTER TABLE tasks DROP COLUMN tags")
        db.execSQL("UPDATE tasks SET remoteHref='https://example.com/old.ics', etag='v1', rawCalendar=? WHERE id='old'", arrayOf("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nBEGIN:VTODO\r\nUID:old\r\nSUMMARY:Existing task\r\nPRIORITY:5\r\nCATEGORIES:work,errands\r\nEND:VTODO\r\nEND:VCALENDAR\r\n"))
        db.version = 1; store.close()
        store = TaskStore(ApplicationProvider.getApplicationContext())
        val existing = store.snapshot().tasks.single()
        assertEquals("Existing task", existing.title); assertEquals("2026-10-20", existing.dueDate); assertTrue(existing.starred)
        assertEquals("https://example.com/old.ics", existing.remoteHref); assertEquals("v1", existing.etag)
        assertEquals(TaskPriority.MEDIUM, existing.priority); assertEquals(listOf("work", "errands"), existing.tags)
        assertEquals("default", store.snapshot().lists.single().color); assertEquals(3, store.readableDatabase.version)
    }
    @Test fun versionTwoMigrationPreservesTasksAndStartsUnarchived() {
        store.saveTask(Task(listId = list.id, title = "Existing", tags = listOf("work"), priority = TaskPriority.HIGH))
        store.writableDatabase.execSQL("ALTER TABLE tasks DROP COLUMN archived")
        store.writableDatabase.version = 2; store.close()
        store = TaskStore(ApplicationProvider.getApplicationContext())
        assertFalse(store.snapshot().tasks.single().archived)
        assertEquals(listOf("work"), store.snapshot().tasks.single().tags)
        assertEquals(TaskPriority.HIGH, store.snapshot().tasks.single().priority)
        assertEquals(3, store.readableDatabase.version)
    }
    @Test fun archivingAndRestoringPreserveHierarchyAndDoNotCompleteTasks() {
        store.saveTask(Task(id = "parent", listId = list.id, title = "Parent"))
        store.saveTask(Task(id = "child", listId = list.id, title = "Child", parentId = "parent"))
        store.archiveTask("parent", true)
        assertTrue(store.snapshot().tasks.all { it.archived && !it.completed && it.dirty })
        store.toggle("parent")
        assertTrue(store.snapshot().tasks.none { it.completed })
        store.close(); store = TaskStore(ApplicationProvider.getApplicationContext())
        assertTrue(store.snapshot().tasks.all { it.archived })
        store.archiveTask("child", false)
        assertTrue(store.snapshot().tasks.none { it.archived })
        store.archiveTask("child", true)
        assertFalse(store.snapshot().tasks.first { it.id == "parent" }.archived)
        assertTrue(store.snapshot().tasks.first { it.id == "child" }.archived)
    }

    @Test fun checkingChildrenKeepsThemInlineAndCompletesParentOnlyAfterTheLastOne() {
        val parent = Task(id = "parent", listId = list.id, title = "Parent")
        val first = Task(id = "first", listId = list.id, title = "First", parentId = parent.id, position = 0)
        val second = first.copy(id = "second", title = "Second", position = 1024)
        store.saveTaskFamily(parent, listOf(first, second))
        store.toggle(first.id)
        var tasks = store.snapshot().tasks
        assertFalse(tasks.first { it.id == parent.id }.completed)
        assertEquals(setOf("parent", "first", "second"), activeTaskRows(tasks).map { it.id }.toSet())
        assertTrue(completedTaskRows(tasks).isEmpty())
        assertEquals(0, store.clearCompleted(null, false, null))
        store.toggle(second.id)
        tasks = store.snapshot().tasks
        assertTrue(tasks.all { it.completed })
        assertTrue(activeTaskRows(tasks).isEmpty())
        assertEquals(listOf("parent"), completedTaskRows(tasks).map { it.id })
        store.toggle(first.id)
        tasks = store.snapshot().tasks
        assertFalse(tasks.first { it.id == parent.id }.completed)
        assertFalse(tasks.first { it.id == first.id }.completed)
        assertTrue(tasks.first { it.id == second.id }.completed)
    }
    @Test fun recurringFamiliesCopyUncheckedSubtasksAndRespectOccurrenceLimit() {
        val date = java.time.LocalDate.now()
        val parent = Task(id = "parent", listId = list.id, title = "Daily", dueDate = date.toString(),
            recurrence = "FREQ=DAILY;COUNT=2", deadline = date.plusDays(2).toString())
        val child = Task(id = "child", listId = list.id, title = "Step", parentId = parent.id, position = 1024,
            dueDate = date.toString(), deadline = date.plusDays(1).toString(), priority = TaskPriority.HIGH, tags = listOf("routine"), notes = "Details")
        store.saveTaskFamily(parent, listOf(child))
        store.toggle(child.id)
        var tasks = store.snapshot().tasks
        val next = tasks.single { it.parentId == null && !it.completed }
        val nextChild = tasks.single { it.parentId == next.id }
        assertEquals(date.plusDays(1).toString(), next.dueDate)
        assertEquals(date.toString(), next.recurrenceAnchor)
        assertEquals(date.plusDays(3).toString(), next.deadline)
        assertFalse(nextChild.completed); assertNotEquals(child.id, nextChild.id)
        assertEquals(date.plusDays(1).toString(), nextChild.dueDate)
        assertEquals(date.plusDays(2).toString(), nextChild.deadline)
        assertEquals(child.priority, nextChild.priority); assertEquals(child.tags, nextChild.tags)
        assertEquals(child.notes, nextChild.notes); assertEquals(child.position, nextChild.position)
        assertNull(nextChild.remoteHref); assertNull(nextChild.etag); assertNull(nextChild.rawCalendar)
        store.toggle(next.id)
        tasks = store.snapshot().tasks
        assertEquals(4, tasks.size); assertTrue(tasks.all { it.completed })
    }
    @Test fun inlineCompletionAndParentButtonEachCreateOnlyOneNextFamily() {
        val parent = Task(id = "parent", listId = list.id, title = "Weekly", dueDate = java.time.LocalDate.now().toString(), recurrence = "FREQ=WEEKLY")
        val a = Task(id = "a", listId = list.id, title = "A", parentId = parent.id)
        val b = a.copy(id = "b", title = "B")
        store.saveTaskFamily(parent, listOf(a, b))
        store.saveTaskFamily(parent, listOf(a.copy(completedAt = 1), b))
        assertEquals(3, store.snapshot().tasks.size)
        store.saveTaskFamily(parent, listOf(a.copy(completedAt = 1), b), toggleParent = true)
        val tasks = store.snapshot().tasks
        assertEquals(6, tasks.size)
        assertEquals(3, tasks.count { it.completed })
        assertEquals(2, tasks.count { it.parentId != null && !it.completed })
        assertEquals(1L, tasks.first { it.id == "a" }.completedAt)
    }
    @Test fun inlineSaveCompletesTheFamilyWhenEveryChildIsChecked() {
        val parent = Task(id = "parent", listId = list.id, title = "Parent")
        val child = Task(id = "child", listId = list.id, title = "Step", parentId = parent.id)
        store.saveTaskFamily(parent, listOf(child))
        store.saveTaskFamily(parent.copy(notes = "Edited"), listOf(child.copy(completedAt = 1)))
        assertTrue(store.snapshot().tasks.all { it.completed })
        assertEquals("Edited", store.snapshot().tasks.first { it.id == parent.id }.notes)
    }
    @Test fun familySaveIsAtomicAndRemovedChildrenBecomeSyncTombstones() {
        val parent = Task(id = "parent", listId = list.id, title = "Parent")
        val child = Task(id = "child", listId = list.id, title = "Step", parentId = parent.id)
        val before = store.changes.value
        store.saveTaskFamily(parent, listOf(child, child.copy(id = "blank", title = " ")))
        assertEquals(before + 1, store.changes.value)
        assertEquals(2, store.snapshot().tasks.size)
        val other = Task(id = "other", listId = list.id, title = "Other")
        store.saveTask(other)
        assertThrows(IllegalArgumentException::class.java) { store.saveTaskFamily(parent.copy(title = "Unsaved"), listOf(other)) }
        assertEquals("Parent", store.snapshot().tasks.first { it.id == parent.id }.title)
        assertFalse(store.snapshot().tasks.first { it.id == child.id }.deleted)
        store.saveTaskFamily(parent, emptyList())
        assertTrue(store.snapshot().tasks.first { it.id == child.id }.let { it.deleted && it.dirty })
        assertFalse(store.snapshot().tasks.first { it.id == parent.id }.completed)
    }

    @Test fun checkedChildrenKeepTheirPlaceAndRemainReorderableWithPendingSiblings() {
        val parent = Task(id = "parent", listId = list.id, title = "Parent", position = 0)
        val a = Task(id = "a", listId = list.id, title = "Checked", parentId = parent.id, completedAt = 1, position = 1024)
        val b = a.copy(id = "b", title = "Pending", completedAt = null, position = 2048)
        store.saveTaskFamily(parent, listOf(a, b))
        val sent = store.snapshot().tasks.first { it.id == a.id }
        store.markSynced(sent, "https://example.com/a.ics", "v1", "calendar")
        val synced = store.snapshot().tasks.first { it.id == a.id }
        store.saveTaskFamily(parent, listOf(a, b.copy(notes = "Edited")))
        assertEquals(synced.revision, store.snapshot().tasks.first { it.id == a.id }.revision)
        assertFalse(store.snapshot().tasks.first { it.id == a.id }.dirty)
        store.reorder(listOf("parent", "b", "a"))
        val rows = taskHierarchy(sortTasks(activeTaskRows(store.snapshot().tasks), SortMode.MY_ORDER))
        assertEquals(listOf("parent", "b", "a"), rows.map { it.id })
        assertTrue(rows.last().completed)
    }

}

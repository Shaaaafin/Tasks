package dev.quiettasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.quiettasks.data.*
import dev.quiettasks.reminders.Reminders
import dev.quiettasks.sync.SyncTrigger
import dev.quiettasks.widget.TasksWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TasksApplication
    val snapshot = app.store.state
    val preferences = app.settings.state
    val syncStatus = app.sync.state
    val messages = Channel<String>(Channel.BUFFERED)
    private var foreground: CoroutineScope? = null
    private var auto: Job? = null
    private fun action(block: suspend () -> Unit) { viewModelScope.launch { try { withContext(Dispatchers.IO) { block() } } catch (c: CancellationException) { throw c } catch (e: Exception) { messages.send(e.message ?: "Could not save changes") } } }
    fun visible(value: Boolean) {
        app.sync.visible = value
        foreground?.cancel(); foreground = null
        if (!value) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        foreground = scope
        auto = scope.launch {
            combine(app.store.changes, preferences.map { Triple(it.syncMode, it.wifiOnly, Triple(it.serverUrl, it.username, it.password)) }.distinctUntilChanged()) { revision, config -> revision to config }
                .collectLatest {
                    delay(1200)
                    if (preferences.value.syncMode == SyncMode.WHILE_USING) app.sync.sync(SyncTrigger.FOREGROUND)
                }
        }
    }
    fun sync() { foreground?.launch { messages.send(app.sync.sync(SyncTrigger.MANUAL)) } }
    fun updateSettings(transform: (Preferences) -> Preferences) = action {
        val previous = preferences.value
        app.settings.update(transform)
        val next = preferences.value
        if (previous.reminders != next.reminders) Reminders.schedule(app, snapshot.value.tasks, next.reminders)
        if (previous.theme != next.theme || previous.deviceColors != next.deviceColors)
            withContext(Dispatchers.Main) { TasksWidget.updateAll(app, snapshot.value, next) }
    }
    fun connect(url: String, user: String, password: String) = action {
        require(!syncStatus.value.running) { "Wait for the current sync to finish" }
        val uri = java.net.URI(url.trim())
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null && uri.query == null) { "Enter an HTTPS CalDAV server URL" }
        require(user.isNotBlank() && password.isNotBlank()) { "Enter your username and app password" }
        val old = preferences.value
        if (old.serverUrl != url.trim() || old.username != user.trim()) app.store.detachRemote()
        app.settings.update { it.copy(serverUrl = url.trim(), username = user.trim(), password = password, lastSyncAt = 0, lastSyncMessage = "Account saved. Ready to sync.") }
        messages.send("Account saved")
    }
    fun disconnect() = action {
        require(!syncStatus.value.running) { "Wait for the current sync to finish" }
        app.settings.update { it.copy(serverUrl = "", username = "", password = "", lastSyncAt = 0, lastSyncMessage = "Not synced yet") }
        app.store.detachRemote()
        messages.send("Disconnected. Tasks remain on this device.")
    }
    fun save(task: Task, onSaved: () -> Unit = {}) = action { app.store.saveTask(task); withContext(Dispatchers.Main) { onSaved() } }
    fun saveFamily(task: Task, children: List<Task>, toggleParent: Boolean = false, onSaved: () -> Unit = {}) = action {
        app.store.saveTaskFamily(task, children, toggleParent)
        withContext(Dispatchers.Main) { onSaved() }
    }
    fun star(task: Task) = action {
        val latest = app.store.snapshot().tasks.firstOrNull { it.id == task.id } ?: return@action
        app.store.saveTask(latest.copy(starred = !latest.starred))
    }
    fun toggle(task: Task) = action { app.store.toggle(task.id) }
    fun completeDraft(task: Task, onCompleted: () -> Unit) = action { app.store.saveTask(task); app.store.toggle(task.id); withContext(Dispatchers.Main) { onCompleted() } }
    fun delete(task: Task) = action { app.store.deleteTask(task.id); messages.send("Task deleted") }
    fun archive(task: Task) = action { app.store.archiveTask(task.id, !task.archived); messages.send(if (task.archived) "Task restored" else "Task archived") }
    fun restore(task: Task) = action { app.store.restoreTask(task) }
    fun saveList(list: TaskList) = action { app.store.saveList(list) }
    fun deleteList(list: TaskList) = action { app.store.deleteList(list.id) }
    fun clearCompleted(listId: String?, starredOnly: Boolean = false, tag: String? = null) = action {
        app.store.clearCompleted(listId, starredOnly, tag)
    }
    fun reorder(ids: List<String>) = action { app.store.reorder(ids) }
    override fun onCleared() { foreground?.cancel(); super.onCleared() }
}

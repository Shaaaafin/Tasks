package dev.quiettasks

import android.app.Application
import dev.quiettasks.data.SettingsStore
import dev.quiettasks.data.TaskStore
import dev.quiettasks.reminders.Reminders
import dev.quiettasks.sync.SyncEngine
import dev.quiettasks.sync.SyncScheduler
import dev.quiettasks.widget.TasksWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce

class TasksApplication : Application() {
    val store by lazy { TaskStore(this) }
    val settings by lazy { SettingsStore(this) }
    val sync by lazy { SyncEngine(this, store, settings) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            store.load()
            @OptIn(FlowPreview::class)
            store.state.debounce(200).collect {
                Reminders.schedule(this@TasksApplication, it.tasks, settings.state.value.reminders)
                withContext(Dispatchers.Main) { TasksWidget.updateAll(this@TasksApplication, it, settings.state.value) }
            }
        }
        scope.launch {
            settings.state.map { Triple(it.syncMode, it.wifiOnly, Triple(it.serverUrl, it.username, it.password)) }.distinctUntilChanged().collect {
                SyncScheduler.configure(this@TasksApplication, settings.state.value)
            }
        }
    }
    override fun onTerminate() {
        scope.cancel()
        super.onTerminate()
    }
}

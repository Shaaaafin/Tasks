package dev.quiettasks.reminders

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import dev.quiettasks.MainActivity
import dev.quiettasks.R
import dev.quiettasks.TasksApplication
import dev.quiettasks.data.Task
import kotlinx.coroutines.*

object Reminders {
    private const val CHANNEL = "task_reminders"
    private fun pending(context: Context) = PendingIntent.getBroadcast(context, 1, Intent(context, ReminderReceiver::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private data class Event(val task: Task, val at: Long, val deadline: Boolean) { val key get() = task.id + if (deadline) ":deadline" else ":reminder" }
    private fun events(tasks: List<Task>) = tasks.filter { !it.completed && !it.deleted && !it.archived }.flatMap { task ->
        listOfNotNull(task.reminderAt()?.let { Event(task, it, false) }, task.deadlineAt()?.let { Event(task, it, true) }).distinctBy { it.at }
    }
    private fun delivered(context: Context, event: Event) = context.getSharedPreferences("delivered", Context.MODE_PRIVATE).getLong(event.key, -1) == event.at
    fun schedule(context: Context, tasks: List<Task>, enabled: Boolean) {
        val manager = context.getSystemService(AlarmManager::class.java)
        val notifications = context.getSystemService(NotificationManager::class.java)
        tasks.filter { it.deleted || it.completed || it.archived }.forEach { notifications.cancel(it.id.hashCode()) }
        val next = if (enabled) events(tasks).filter { !delivered(context, it) }.minOfOrNull { it.at } else null
        val schedule = context.getSharedPreferences("alarm", Context.MODE_PRIVATE)
        val old = schedule.getLong("next", -1)
        if (old == (next ?: -1L)) return
        manager.cancel(pending(context))
        schedule.edit().putLong("next", next ?: -1L).apply()
        if (next == null) return
        val at = next.coerceAtLeast(System.currentTimeMillis() + 1000)
        if (Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
        else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
    }
    fun deliver(context: Context, tasks: List<Task>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val now = System.currentTimeMillis() + 1500
        val prefs = context.getSharedPreferences("delivered", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        val due = events(tasks).filter { it.at <= now && !delivered(context, it) }
        due.forEach { event ->
            val task = event.task
            if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                val open = PendingIntent.getActivity(context, task.id.hashCode(), Intent(context, MainActivity::class.java).putExtra("taskId", task.id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.notify(task.id.hashCode(), Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(task.title).setContentText(if (event.deadline) "Due today" else task.notes.ifBlank { "Task reminder" }).setContentIntent(open).setAutoCancel(true).build())
            }
            editor.putLong(event.key, event.at)
        }
        editor.commit()
        context.getSharedPreferences("alarm", Context.MODE_PRIVATE).edit().remove("next").commit()
    }
    fun reset(context: Context) { context.getSharedPreferences("alarm", Context.MODE_PRIVATE).edit().remove("next").commit() }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as TasksApplication
                val tasks = app.store.snapshot().tasks
                if (app.settings.state.value.reminders) Reminders.deliver(context, tasks)
                Reminders.schedule(context, tasks, app.settings.state.value.reminders)
            } finally { result.finish() }
        }
    }
}
class RestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED, AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as TasksApplication
                Reminders.reset(context)
                Reminders.schedule(context, app.store.snapshot().tasks, app.settings.state.value.reminders)
            } finally { result.finish() }
        }
    }
}

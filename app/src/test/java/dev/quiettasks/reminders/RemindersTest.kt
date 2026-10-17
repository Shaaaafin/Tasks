package dev.quiettasks.reminders

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import dev.quiettasks.data.Task
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RemindersTest {
    @Test fun manyTasksUseOneAlarmAndTurningRemindersOffCancelsIt() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        Reminders.reset(context)
        val tasks = (1..100).map { Task(listId = "list", title = "Task $it", dueDate = LocalDate.now().plusDays(it.toLong()).toString()) }
        val manager = Shadows.shadowOf(context.getSystemService(AlarmManager::class.java))
        Reminders.schedule(context, tasks, true)
        assertEquals(1, manager.scheduledAlarms.size)
        assertEquals(tasks.first().reminderAt(), manager.nextScheduledAlarm!!.triggerAtTime)
        Reminders.schedule(context, tasks, false)
        assertTrue(manager.scheduledAlarms.isEmpty())
    }
}

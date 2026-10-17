package dev.quiettasks.sync

import android.app.Application
import android.app.job.JobScheduler
import androidx.test.core.app.ApplicationProvider
import dev.quiettasks.data.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SyncSchedulerTest {
    @Test fun chargingModeSchedulesOnlyJobsWithChargingConstraints() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val prefs = Preferences(serverUrl = "https://example.com", username = "u", password = "p", syncMode = SyncMode.CHARGING_ONLY)
        SyncScheduler.configure(context, prefs)
        val jobs = context.getSystemService(JobScheduler::class.java).allPendingJobs
        assertEquals(2, jobs.size); assertTrue(jobs.all { it.isRequireCharging && it.isPersisted })
        SyncScheduler.configure(context, prefs.copy(syncMode = SyncMode.WHILE_USING))
        assertTrue(context.getSystemService(JobScheduler::class.java).allPendingJobs.isEmpty())
    }
}

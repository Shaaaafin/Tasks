package dev.quiettasks.sync

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import dev.quiettasks.TasksApplication
import dev.quiettasks.data.Preferences
import dev.quiettasks.data.SyncMode
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

object SyncScheduler {
    const val PERIODIC = 4101
    const val ON_CONNECT = 4102
    fun configure(context: Context, preferences: Preferences) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        scheduler.cancel(PERIODIC); scheduler.cancel(ON_CONNECT)
        if (!preferences.connected || preferences.syncMode != SyncMode.CHARGING_ONLY) return
        fun builder(id: Int) = JobInfo.Builder(id, ComponentName(context, ChargingSyncJob::class.java))
            .setRequiresCharging(true).setPersisted(true)
            .setRequiredNetworkType(if (preferences.wifiOnly) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_ANY)
            .setBackoffCriteria(TimeUnit.MINUTES.toMillis(30), JobInfo.BACKOFF_POLICY_EXPONENTIAL)
        scheduler.schedule(builder(PERIODIC).setPeriodic(TimeUnit.HOURS.toMillis(6), TimeUnit.HOURS.toMillis(1)).build())
        scheduler.schedule(builder(ON_CONNECT).build())
    }
}

class ChargingSyncJob : JobService() {
    private var work: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        work = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val app = application as TasksApplication
                app.store.load()
                app.sync.sync(SyncTrigger.CHARGING)
                jobFinished(params, false)
            } catch (_: CancellationException) { /* OS removed the charging constraint. */ }
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { work?.cancel(); return true }
    override fun onDestroy() { work?.cancel(); super.onDestroy() }
}

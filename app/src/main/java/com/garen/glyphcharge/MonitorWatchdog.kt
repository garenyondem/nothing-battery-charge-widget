package com.garen.glyphcharge

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log

object MonitorWatchdog {

    private const val TAG = "MonitorWatchdog"
    const val JOB_ID = 1004
    private const val RESTART_REQUEST = 1005
    private const val PERIOD_MS = 15 * 60 * 1000L
    private const val RESTART_DELAY_MS = 2_000L
    private const val STALE_MS = 2 * 60 * 1000L

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        if (scheduler.getPendingJob(JOB_ID) != null) return

        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(context, MonitorWatchdogJob::class.java)
        )
            .setPeriodic(PERIOD_MS)
            .setPersisted(true)
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_NONE)
            .build()

        runCatching { scheduler.schedule(job) }
            .onFailure { Log.e(TAG, "watchdog schedule failed", it) }
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        context.getSystemService(AlarmManager::class.java)
            ?.cancel(restartIntent(context))
    }

    fun scheduleRestart(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val at = SystemClock.elapsedRealtime() + RESTART_DELAY_MS

        runCatching {
            alarm.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                at,
                restartIntent(context)
            )
        }.onFailure { Log.e(TAG, "restart alarm failed", it) }
    }

    fun needsRestart(context: Context): Boolean {
        if (!ChargingPreferences.isEnabled(context)) return false
        val heartbeat = ChargingPreferences.heartbeat(context)
        if (heartbeat <= 0L) return true
        return System.currentTimeMillis() - heartbeat > STALE_MS
    }

    private fun restartIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            RESTART_REQUEST,
            Intent(context, MonitorRestartReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}

class MonitorWatchdogJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        if (ChargingPreferences.isEnabled(this) && MonitorWatchdog.needsRestart(this)) {
            runCatching { ChargingMonitorService.start(this) }
                .onFailure { Log.e("MonitorWatchdog", "job restart failed", it) }
        }
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

class MonitorRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        if (!ChargingPreferences.isEnabled(context)) return

        runCatching { ChargingMonitorService.start(context) }
            .onFailure { Log.e("MonitorWatchdog", "alarm restart failed", it) }
    }
}

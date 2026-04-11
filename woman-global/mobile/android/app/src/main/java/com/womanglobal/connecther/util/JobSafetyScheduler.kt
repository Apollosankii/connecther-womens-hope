package com.womanglobal.connecther.util

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.womanglobal.connecther.R
import com.womanglobal.connecther.supabase.SupabaseClientProvider
import com.womanglobal.connecther.supabase.SupabaseData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "JobSafety"
private const val CHANNEL_ID = "connecther_safety_checkin"
private const val NOTIF_BASE = 91000

object JobSafetyScheduler {
    const val ACTION_ALARM = "com.womanglobal.connecther.action.SAFETY_ALARM"
    const val ACTION_CONFIRM = "com.womanglobal.connecther.action.SAFETY_CONFIRM"
    const val EXTRA_JOB_ID = "extra_job_id"
    const val EXTRA_HOUR = "extra_hour_index"
    const val EXTRA_INTERVAL_MIN = "extra_interval_min"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = android.app.NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.safety_checkin_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        )
        mgr.createNotificationChannel(ch)
    }

    private fun alarmIntent(context: Context, jobId: Int, hour: Int, intervalMin: Int): Intent =
        Intent(ACTION_ALARM).setPackage(context.packageName)
            .putExtra(EXTRA_JOB_ID, jobId)
            .putExtra(EXTRA_HOUR, hour)
            .putExtra(EXTRA_INTERVAL_MIN, intervalMin)

    private fun alarmRequestCode(jobId: Int, hour: Int): Int = jobId * 10_000 + hour

    /** PendingIntent requestCode for the in-notification "I'm OK" action. */
    fun confirmPendingIntentRequestCode(jobId: Int, hour: Int): Int = jobId * 10_000 + hour + 500_000

    /** First check-in is due one interval after work starts. */
    fun scheduleFirstHour(context: Context, jobId: Int, intervalMin: Int = 60) {
        scheduleNext(context, jobId, hourIndex = 1, delayMs = intervalMin.coerceAtLeast(15) * 60_000L, intervalMin = intervalMin)
    }

    fun scheduleNext(context: Context, jobId: Int, hourIndex: Int, delayMs: Long, intervalMin: Int = 60) {
        val am = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val ctx = context.applicationContext
        val pi = PendingIntent.getBroadcast(
            ctx,
            alarmRequestCode(jobId, hourIndex),
            alarmIntent(ctx, jobId, hourIndex, intervalMin),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = android.os.SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(60_000L)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        } else {
            @Suppress("DEPRECATION")
            am.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
        }
        Log.d(TAG, "Scheduled safety alarm job=$jobId hour=$hourIndex every=${intervalMin}m in ${delayMs}ms")
    }

    /**
     * Best-effort reschedule when app returns online / resumes.
     * If work started long ago and alarms were missed, schedule the next due check-in soon.
     */
    fun ensureDueScheduled(context: Context, jobId: Int, workStartedAtIso: String?, intervalMin: Int = 60) {
        val started = workStartedAtIso?.trim().orEmpty()
        if (started.isBlank()) return
        val startMs = runCatching { java.time.Instant.parse(started).toEpochMilli() }.getOrNull() ?: return
        val intervalMs = intervalMin.coerceAtLeast(15) * 60_000L
        val nowMs = System.currentTimeMillis()
        val elapsed = nowMs - startMs
        if (elapsed < intervalMs) {
            // Not due yet: schedule first.
            val remain = (intervalMs - elapsed).coerceAtLeast(60_000L)
            scheduleNext(context, jobId, hourIndex = 1, delayMs = remain, intervalMin = intervalMin)
            return
        }
        val dueIndex = (elapsed / intervalMs).toInt() + 1
        scheduleNext(context, jobId, hourIndex = dueIndex, delayMs = 60_000L, intervalMin = intervalMin)
    }

    fun cancelForJob(context: Context, jobId: Int) {
        val am = context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val ctx = context.applicationContext
        for (h in 1..72) {
            val pi = PendingIntent.getBroadcast(
                ctx,
                alarmRequestCode(jobId, h),
                // PendingIntent identity ignores extras; interval is irrelevant for cancellation.
                alarmIntent(ctx, jobId, h, intervalMin = 60),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            if (pi != null) {
                am.cancel(pi)
            }
        }
        NotificationManagerCompat.from(ctx).cancel(NOTIF_BASE + jobId)
        Log.d(TAG, "Cancelled safety alarms for job=$jobId")
    }
}

/**
 * Shows hourly safety notification or records "I'm OK" from the notification action.
 */
class SafetyCheckinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val ctx = context.applicationContext
        when (intent.action) {
            JobSafetyScheduler.ACTION_ALARM -> {
                val jobId = intent.getIntExtra(JobSafetyScheduler.EXTRA_JOB_ID, -1)
                val hour = intent.getIntExtra(JobSafetyScheduler.EXTRA_HOUR, 1)
                val intervalMin = intent.getIntExtra(JobSafetyScheduler.EXTRA_INTERVAL_MIN, 60)
                if (jobId < 1) return
                JobSafetyScheduler.ensureChannel(ctx)
                val confirmPi = PendingIntent.getBroadcast(
                    ctx,
                    JobSafetyScheduler.confirmPendingIntentRequestCode(jobId, hour),
                    Intent(JobSafetyScheduler.ACTION_CONFIRM).setPackage(ctx.packageName)
                        .putExtra(JobSafetyScheduler.EXTRA_JOB_ID, jobId)
                        .putExtra(JobSafetyScheduler.EXTRA_HOUR, hour),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(ctx.getString(R.string.safety_checkin_title))
                    .setContentText(ctx.getString(R.string.safety_checkin_message))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_REMINDER)
                    .addAction(R.mipmap.ic_launcher, ctx.getString(R.string.safety_checkin_ok), confirmPi)
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(ctx).notify(NOTIF_BASE + jobId, notif)
            }
            JobSafetyScheduler.ACTION_CONFIRM -> {
                val pending = goAsync()
                val jobId = intent.getIntExtra(JobSafetyScheduler.EXTRA_JOB_ID, -1)
                val hour = intent.getIntExtra(JobSafetyScheduler.EXTRA_HOUR, 1)
                val intervalMin = intent.getIntExtra(JobSafetyScheduler.EXTRA_INTERVAL_MIN, 60)
                if (jobId < 1) {
                    pending.finish()
                    return
                }
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val authed = SupabaseClientProvider.ensureSupabaseSession()
                        if (!authed) {
                            Log.w(TAG, "Safety confirm: no session")
                            return@launch
                        }
                        val err = SupabaseData.providerSubmitJobCheckin(jobId, hour, null, null)
                        if (err == null) {
                            JobSafetyScheduler.scheduleNext(
                                ctx,
                                jobId,
                                hourIndex = hour + 1,
                                delayMs = intervalMin.coerceAtLeast(15) * 60_000L,
                                intervalMin = intervalMin,
                            )
                            NotificationManagerCompat.from(ctx).cancel(NOTIF_BASE + jobId)
                        } else {
                            Log.w(TAG, "Safety confirm failed: $err")
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}

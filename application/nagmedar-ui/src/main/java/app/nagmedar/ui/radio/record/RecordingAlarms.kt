package app.nagmedar.ui.radio.record

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.nagmedar.ui.sleep.Alarms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.videolan.tools.AppScope

/**
 * S-1107: the scheduled recordings on the system's clock. Each enabled schedule has an alarm at its
 * next start (`setExactAndAllowWhileIdle` where the user allowed exact alarms, as the alarm clock
 * does, else the nearest the system gives); when it rings, the recording starts and the next
 * start is set. The alarms are set again after a restart, a change of the clock or of the alarm
 * permission ([app.nagmedar.ui.sleep.AlarmRescheduler]).
 */
object RecordingAlarms {

    private const val TAG = "Nagmedar/RecordingAlarm"
    const val ACTION_START = "app.nagmedar.action.RADIO_RECORDING_START"
    const val EXTRA_SCHEDULE = "schedule"
    private const val REQUEST_BASE = 1_107_000

    private fun pending(context: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_BASE + (id % 100_000).toInt(),
        Intent(context, RecordingAlarmReceiver::class.java).setAction(ACTION_START).putExtra(EXTRA_SCHEDULE, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Takes the alarm of schedule [id] off the system's clock. */
    fun cancel(context: Context, id: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        manager.cancel(pending(context, id))
    }

    /** Sets the alarm of [schedule] at its next start, or takes it off when it has none. */
    fun set(context: Context, schedule: RecordingSchedule, now: Long = System.currentTimeMillis()) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val alarm = pending(context, schedule.id)
        val next = ScheduleMath.next(schedule, now)
        if (next == null) {
            manager.cancel(alarm)
            return
        }
        try {
            if (Alarms.exact(context)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.start, alarm)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.start, alarm)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "the alarm of schedule ${schedule.id} could not be set", e)
        }
    }

    /** Sets the alarm of every schedule (after a restart, or when the schedules changed); blocking. */
    suspend fun rescheduleAll(context: Context) {
        val store = RadioRecordings.store(context)
        store.scheduleList().forEach { set(context, it) }
    }

    /**
     * The alarm of schedule [id] rang: its recording begins for what is left of its time, and the next
     * start is set. A one-off whose time has passed, or a schedule that was turned off or deleted
     * meanwhile, records nothing.
     */
    suspend fun fire(context: Context, id: Long, now: Long = System.currentTimeMillis()) {
        val store = RadioRecordings.store(context)
        val schedule = store.schedule(id) ?: run {
            cancel(context, id)
            return
        }
        start(context, schedule, now)
        set(context, schedule.copy(lastStartedAt = maxOf(schedule.lastStartedAt, now)), now)
    }

    /** Starts the run of [schedule] that is on at [now], if there is one that was not started. */
    private suspend fun start(context: Context, schedule: RecordingSchedule, now: Long) {
        val window = ScheduleMath.running(schedule, now) ?: return
        RadioRecordings.store(context).markStarted(schedule.id, window.start)
        RadioRecordings.center(context).start(schedule.station, endAt = window.end, scheduleId = schedule.id)
    }

    /**
     * Starts the runs that should be on now and were not started: the alarm came late, or the device
     * was off at the time. It is the app in front of the user that does this (a service of the
     * data-sync type cannot be started from a restart on Android 15).
     */
    suspend fun catchUp(context: Context, now: Long = System.currentTimeMillis()) {
        RadioRecordings.store(context).scheduleList().forEach { start(context, it, now) }
    }
}

/** S-1107: a scheduled recording's time has come; only the app's own alarm reaches it. */
class RecordingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RecordingAlarms.ACTION_START) return
        val id = intent.getLongExtra(RecordingAlarms.EXTRA_SCHEDULE, 0L)
        if (id <= 0) return
        val app = context.applicationContext
        val pending = goAsync()
        AppScope.launch(Dispatchers.IO) {
            try {
                RecordingAlarms.fire(app, id)
            } finally {
                pending.finish()
            }
        }
    }
}

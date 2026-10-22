package app.nagmedar.ui.radio.record

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.nagmedar.ui.sleep.Alarms
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.videolan.tools.AppScope

/**
 * S-1107: the scheduled recordings on the system's clock. Each enabled schedule has an alarm at its
 * next start (`setExactAndAllowWhileIdle` where the user allowed exact alarms, as the alarm clock
 * does, else the nearest the system gives); when it rings, the recording starts and the next
 * start is set. The alarms are set again after a restart, a change of the clock or of the alarm
 * permission ([app.nagmedar.ui.sleep.AlarmRescheduler]), and [catchUp] makes up for what was missed:
 * runs begun late, runs a restart cut off, runs that passed unrecorded.
 */
object RecordingAlarms {

    private const val TAG = "Nagmedar/RecordingAlarm"
    const val ACTION_START = "app.nagmedar.action.RADIO_RECORDING_START"
    const val ACTION_CATCH_UP = "app.nagmedar.action.RADIO_RECORDING_CATCH_UP"
    const val EXTRA_SCHEDULE = "schedule"
    private const val REQUEST_BASE = 1_107_000
    private const val CATCH_UP_REQUEST = 1_207_000

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
        lock.withLock {
            ensureRecovered(context)
            start(context, schedule, now)
        }
        set(context, schedule, now)
    }

    /**
     * Starts the run of [schedule] that is on at [now], if there is one that was not started. The run
     * is marked as started only when the recording could begin: where the system refused its service,
     * the app in front of the user tries again.
     */
    private suspend fun start(context: Context, schedule: RecordingSchedule, now: Long): Boolean {
        val window = ScheduleMath.running(schedule, now) ?: return false
        val began = RadioRecordings.center(context).startRun(schedule.station, window, schedule.id)
        if (began) RadioRecordings.store(context).markStarted(schedule.id, window.start)
        return began
    }

    /**
     * Makes up for what the clock could not: begins the runs that are on now and were not begun (the
     * alarm came late, the device was off or restarted, the system held the alarm back), takes up
     * again the runs a restart cut off while their time lasts, and marks the runs whose time passed
     * with nothing recorded as missed. It runs when the app starts, when the device does, when the
     * permission for exact alarms comes back and when the Recordings tab opens.
     */
    suspend fun catchUp(context: Context, now: Long = System.currentTimeMillis()) {
        val store = RadioRecordings.store(context)
        lock.withLock {
            ensureRecovered(context)
            val schedules = store.scheduleList()
            schedules.forEach { start(context, it, now) }
            resume(context, schedules, now)
            markMissed(store, schedules, now)
        }
    }

    /**
     * The app's process has begun (it was started by the user, an alarm, or the device's start): the
     * runs a restart cut off are known, the missed ones marked, and, if a run is on or can be taken
     * up again, an alarm brings the recording from the place the system lets a service be started
     * from (a data-sync service cannot be started by a broadcast of the device's start on Android 15).
     */
    suspend fun onProcessStart(context: Context, now: Long = System.currentTimeMillis()) {
        val store = RadioRecordings.store(context)
        val needed = lock.withLock {
            ensureRecovered(context)
            val schedules = store.scheduleList()
            markMissed(store, schedules, now)
            schedules.any { ScheduleMath.running(it, now) != null } || store.resumable(now).any { run -> schedules.any { it.id == run.scheduleId && it.enabled } }
        }
        if (needed) requestCatchUp(context, START_DELAY_MS)
    }

    /** The runs a restart cut off, begun again as new recordings (their files are new parts: "… (2)"). */
    private suspend fun resume(context: Context, schedules: List<RecordingSchedule>, now: Long) {
        val center = RadioRecordings.center(context)
        for (run in RadioRecordings.store(context).resumable(now)) {
            val schedule = schedules.firstOrNull { it.id == run.scheduleId && it.enabled } ?: continue
            if (center.live.value.any { it.request.scheduleId == run.scheduleId && it.request.windowStart == run.windowStart }) continue
            center.startRun(schedule.station, RecordingWindow(run.windowStart, run.windowEnd), schedule.id)
        }
    }

    /** The runs that began since each schedule was last looked over and are over with nobody recording are marked missed. */
    private suspend fun markMissed(store: RecordingStore, schedules: List<RecordingSchedule>, now: Long) {
        for (schedule in schedules) {
            if (!schedule.enabled) continue
            if (schedule.checkedAt <= 0L) {
                // a schedule from before the runs were kept: its past is not looked into
                store.markChecked(schedule.id, now)
                continue
            }
            val from = maxOf(schedule.checkedAt, now - LOOK_BACK_MS)
            for (window in ScheduleMath.between(schedule, from, now)) {
                if (window.end > now || window.start <= schedule.lastStartedAt) continue
                store.missed(schedule.id, window.start, window.end)
            }
            store.markChecked(schedule.id, now)
        }
    }

    /** Runs left Running by an earlier process are cut off: nothing is recording in a process that has just begun. Once per process. */
    private suspend fun ensureRecovered(context: Context) {
        recoverLock.withLock {
            if (recovered) return
            RadioRecordings.store(context).interruptRunning()
            recovered = true
        }
    }

    /** An alarm in [delayMs] that runs [catchUp]: the start of a recording from a place the system allows, or the check that a killed process is brought back. */
    fun requestCatchUp(context: Context, delayMs: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val alarm = catchUpIntent(context)
        val at = System.currentTimeMillis() + delayMs
        try {
            if (Alarms.exact(context)) manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm) else manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm)
        } catch (e: SecurityException) {
            Log.w(TAG, "the catch-up alarm could not be set", e)
        }
    }

    /** The catch-up alarm taken off (no scheduled recording is going on). */
    fun cancelCatchUp(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(catchUpIntent(context))
    }

    /**
     * While a scheduled recording goes on, the catch-up alarm is pushed [WATCHDOG_MS] ahead again and
     * again: if the app's process is killed, the alarm is what rings after it and takes the recording up.
     */
    fun watch(context: Context) = requestCatchUp(context, WATCHDOG_MS)

    private fun catchUpIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, CATCH_UP_REQUEST,
        Intent(context, RecordingAlarmReceiver::class.java).setAction(ACTION_CATCH_UP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private val lock = Mutex()
    private val recoverLock = Mutex()

    @Volatile
    private var recovered = false

    private const val START_DELAY_MS = 3_000L
    private const val WATCHDOG_MS = 4 * 60_000L

    /** How far back the runs are looked over for ones nobody recorded. */
    private const val LOOK_BACK_MS = 7 * 24 * 3_600_000L
}

/** S-1107: a scheduled recording's time has come, or the catch-up alarm rang; only the app's own alarms reach it. */
class RecordingAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != RecordingAlarms.ACTION_START && action != RecordingAlarms.ACTION_CATCH_UP) return
        val id = intent.getLongExtra(RecordingAlarms.EXTRA_SCHEDULE, 0L)
        if (action == RecordingAlarms.ACTION_START && id <= 0) return
        val app = context.applicationContext
        val pending = goAsync()
        AppScope.launch(Dispatchers.IO) {
            try {
                if (action == RecordingAlarms.ACTION_START) RecordingAlarms.fire(app, id) else RecordingAlarms.catchUp(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Nagmedar/RecordingAlarm", "the alarm could not be handled", e)
            } finally {
                pending.finish()
            }
        }
    }
}

package app.nagmedar.ui.tvlab.remux

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.Immutable
import app.nagmedar.ui.sleep.Alarms
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * How the remux queue stands as a whole (S-1210): [held], paused by the user (the running job
 * waits where it is, no other starts); or waiting until [nightAt] (milliseconds; 0 for none)
 * for its night run, the job running then let to finish, no other started before; [nightCharging],
 * the night run starts only once the device is charging: its time come, [nightAt] stays set
 * until the queue's worker sees the device charge ([waitsForCharger]).
 */
@Immutable
data class QueueControl(val held: Boolean = false, val nightAt: Long = 0, val nightCharging: Boolean = false) {
    /** Whether a night run is set and its time is still to come at [now]. */
    fun waitsForNight(now: Long): Boolean = nightAt > now

    /** Whether the night run's time has come at [now] but it starts only once the device charges, and has not started yet. */
    fun waitsForCharger(now: Long): Boolean = nightCharging && nightAt in 1..now
}

/**
 * S-1210: the night run that starts only once the device charges, as the queue's runner waits
 * for it before the run's first job ([RemuxRunner.drain]).
 */
interface NightCharger {
    /** Whether the night run's time has come and it still waits for the device to charge. */
    fun waiting(): Boolean

    /** The device charges: the night run begins, the queue waits for the charger no more. */
    fun begin()

    /** Waits [ms] at most: less when the charger is plugged in meanwhile. */
    fun await(ms: Long)

    /** No night run waits: the tests' and a queue without one. */
    object None : NightCharger {
        override fun waiting(): Boolean = false

        override fun begin() = Unit

        override fun await(ms: Long) = Unit
    }
}

/** The queue's control, kept in its own preferences file (not the settings' backup): the screen watches [state]. */
class RemuxQueueControl private constructor(private val prefs: SharedPreferences) {
    private val flow = MutableStateFlow(read())

    val state: StateFlow<QueueControl> = flow.asStateFlow()

    val current: QueueControl get() = flow.value

    fun hold(on: Boolean) = write(current.copy(held = on))

    fun night(at: Long, charging: Boolean) = write(current.copy(nightAt = at, nightCharging = charging))

    fun clearNight() = write(current.copy(nightAt = 0, nightCharging = false))

    private fun read() = QueueControl(prefs.getBoolean(HELD, false), prefs.getLong(NIGHT_AT, 0), prefs.getBoolean(NIGHT_CHARGING, false))

    @Synchronized
    private fun write(control: QueueControl) {
        prefs.edit().putBoolean(HELD, control.held).putLong(NIGHT_AT, control.nightAt).putBoolean(NIGHT_CHARGING, control.nightCharging).apply()
        flow.value = control
    }

    companion object {
        private const val FILE = "nagmedar_remux_queue"
        private const val HELD = "held"
        private const val NIGHT_AT = "night_at"
        private const val NIGHT_CHARGING = "night_charging"

        @Volatile
        private var instance: RemuxQueueControl? = null

        fun get(context: Context): RemuxQueueControl = instance ?: synchronized(this) {
            instance ?: RemuxQueueControl(context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)).also { instance = it }
        }
    }
}

/**
 * The queue's night run (S-1210): the queue waits until the time chosen, an alarm then starts its
 * worker (at the minute where the user lets the app set exact alarms, as the radio's schedules do;
 * else as near as the system allows). The worker goes into the foreground at once, while the
 * alarm lets it: Android 12 and later let no foreground job start from the background later, so a
 * worker bound to WorkManager's charging constraint would wait for the app to show. When
 * [QueueControl.nightCharging] asks, that worker waits in the foreground for the charger before
 * the first job ([NightCharger]). The battery and heat rules stay in force while it runs. The
 * alarm is set again after a restart ([app.nagmedar.ui.sleep.AlarmRescheduler]); a night run
 * whose time passed meanwhile starts then, or when the app shows.
 */
object RemuxNight {
    private const val TAG = "Nagmedar/RemuxNight"
    const val ACTION_START = "app.nagmedar.action.REMUX_NIGHT_START"
    private const val REQUEST = 1210

    /** The next [hour]:[minute] after [now]: today's when it is still to come, else tomorrow's. */
    fun next(hour: Int, minute: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), zone).withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        val at = if (today.toInstant().toEpochMilli() > now) today else today.plusDays(1)
        return at.toInstant().toEpochMilli()
    }

    /** The night run set for [at]: the queue waits for it. */
    fun set(context: Context, at: Long, charging: Boolean) {
        val app = context.applicationContext
        RemuxQueueControl.get(app).night(at, charging)
        alarm(app, at)
    }

    /** The night run taken off: the queue goes on now. */
    fun cancel(context: Context) {
        val app = context.applicationContext
        app.getSystemService(AlarmManager::class.java)?.cancel(pending(app))
        RemuxQueueControl.get(app).clearNight()
        RemuxWorker.ensure(app)
    }

    /**
     * The time has come: the worker starts now. A night run that waits for the charger stays set
     * until the worker sees the device charge; any other one ends here.
     */
    fun fire(context: Context) {
        val app = context.applicationContext
        val control = RemuxQueueControl.get(app)
        if (control.current.nightAt == 0L) return
        if (!control.current.nightCharging) control.clearNight()
        RemuxWorker.ensure(app)
    }

    /** After a restart or a change of the clock: the alarm set again, or the run started when its time passed meanwhile. */
    fun reschedule(context: Context, now: Long = System.currentTimeMillis()) {
        val control = RemuxQueueControl.get(context).current
        when {
            control.nightAt == 0L -> Unit
            control.nightAt > now -> alarm(context, control.nightAt)
            else -> fire(context)
        }
    }

    private fun alarm(context: Context, at: Long) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        try {
            if (Alarms.exact(context)) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(context))
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "the night run's alarm could not be set", e)
        }
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST,
        Intent(context, RemuxNightReceiver::class.java).setAction(ACTION_START),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** S-1210: the night run's time has come; only the app's own alarm reaches it. */
class RemuxNightReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RemuxNight.ACTION_START) return
        // the preferences are read off the main thread
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                RemuxNight.fire(app)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

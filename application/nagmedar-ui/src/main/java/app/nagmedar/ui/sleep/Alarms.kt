package app.nagmedar.ui.sleep

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as SystemSettings
import android.util.Log
import app.nagmedar.ui.radio.record.RecordingAlarms
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.resources.PLAYLIST_TYPE_AUDIO
import org.videolan.resources.util.getFromMl
import org.videolan.tools.AppScope
import org.videolan.tools.NAGMEDAR_ALARM
import org.videolan.tools.Settings
import org.videolan.vlc.PlaybackService

/**
 * S-1007: the alarm kept in the settings, set with the system as an alarm clock (its time shows
 * in the status bar and on the lock screen), and rung: the media volume raised to the alarm's
 * loudness if lower, the music started, its volume rising from nothing.
 */
object Alarms {

    private const val TAG = "Nagmedar/Alarm"
    private const val REQUEST = 1007
    const val ACTION_RING = "app.nagmedar.action.ALARM"

    fun load(context: Context): Alarm = Alarm.decode(Settings.getInstance(context).getString(NAGMEDAR_ALARM, null))

    /** [alarm] kept and set (or taken off the system's clock when it is off). */
    fun save(context: Context, alarm: Alarm) {
        Settings.getInstance(context).edit().putString(NAGMEDAR_ALARM, alarm.encode()).apply()
        schedule(context, alarm)
    }

    /** Whether the system lets the alarm ring at its minute (Android 12 and later ask). */
    fun exact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }

    /** Opens the system's page that lets the app set alarms at the minute (Android 12 and later); nothing earlier. */
    fun requestExact(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            context.startActivity(
                Intent(SystemSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /** The alarm on the system's clock, at its next time; without the permission, as near as the system allows. */
    fun schedule(context: Context, alarm: Alarm = load(context)) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val ring = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, AlarmReceiver::class.java).setAction(ACTION_RING),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val at = alarm.next(System.currentTimeMillis())
        if (at == null) {
            manager.cancel(ring)
            return
        }
        try {
            if (exact(context)) {
                val show = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                    PendingIntent.getActivity(context, REQUEST, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                }
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), ring)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, ring)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "the alarm could not be set", e)
        }
    }

    /** The alarm rings: the next one set first, then the music. */
    fun ring(context: Context) {
        val alarm = load(context)
        if (alarm.days.isEmpty()) save(context, alarm.copy(enabled = false)) else schedule(context, alarm)
        play(context, alarm)
    }

    /**
     * [alarm]'s music, as it rings (and as "Try it now" plays it): the media volume raised to its
     * loudness if lower, the music started, its volume rising from nothing.
     */
    fun play(context: Context, alarm: Alarm) {
        raiseMediaVolume(context, alarm.loudness)
        AppScope.launch {
            val service = PlaybackService.instance ?: run {
                PlaybackService.start(context)
                withTimeoutOrNull(10_000) { PlaybackService.serviceFlow.filterNotNull().first() }
            } ?: return@launch
            // from nothing; the engine that takes the music takes this volume too
            service.setVolume(0)
            when (val source = alarm.source) {
                AlarmSource.Last -> service.loadLastPlaylist(PLAYLIST_TYPE_AUDIO)
                is AlarmSource.Playlist -> {
                    val tracks = context.getFromMl { getPlaylist(source.id, false, false)?.getTracks(false, false)?.toList() }.orEmpty()
                    if (tracks.isEmpty()) service.loadLastPlaylist(PLAYLIST_TYPE_AUDIO) else service.load(tracks, 0)
                }
                is AlarmSource.Stream -> {
                    if (source.url.isBlank()) {
                        service.loadLastPlaylist(PLAYLIST_TYPE_AUDIO)
                    } else {
                        service.load(MLServiceLocator.getAbstractMediaWrapper(Uri.parse(source.url.trim())))
                        // a radio that does not answer (no network, gone) does not leave them asleep
                        val answered = withTimeoutOrNull(STREAM_WAIT_MS) {
                            while (!service.isPlaying) delay(500)
                        } != null
                        if (!answered) {
                            Log.w(TAG, "the stream did not answer; the music played last instead")
                            service.loadLastPlaylist(PLAYLIST_TYPE_AUDIO)
                        }
                    }
                }
            }
            ramp(service, alarm.rampSeconds)
        }
    }

    /** The volume from nothing to the usual over [seconds]; a hand on the volume ends it. */
    private suspend fun ramp(service: PlaybackService, seconds: Int) {
        val steps = (seconds * 4).coerceAtLeast(1)
        var set = 0
        for (step in 1..steps) {
            delay(250)
            // someone set the volume: theirs stays (not while the player starts, its volume unsettled)
            if (step > 8 && service.volume != set) return
            set = TARGET_VOLUME * step / steps
            service.setVolume(set)
        }
    }

    /** The device's media volume to [percent] of its range, when it is lower: an alarm heard. */
    private fun raiseMediaVolume(context: Context, percent: Int) {
        val audio = context.getSystemService(AudioManager::class.java) ?: return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val wanted = (max * percent + 50) / 100
        if (audio.getStreamVolume(AudioManager.STREAM_MUSIC) < wanted) {
            runCatching { audio.setStreamVolume(AudioManager.STREAM_MUSIC, wanted, 0) }
        }
    }

    /** The player's usual volume (the service's scale goes to 200). */
    private const val TARGET_VOLUME = 100

    /** How long a stream has to start playing. */
    private const val STREAM_WAIT_MS = 15_000L
}

/** S-1007: the alarm rings; only the app's own alarm reaches it. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Alarms.ACTION_RING) Alarms.ring(context.applicationContext)
    }
}

/** S-1007: after a restart, a change of the time or of the alarm permission, the alarm is set again (S-1107: and the recordings scheduled). */
class AlarmRescheduler : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        Alarms.schedule(app)
        // S-1107: the scheduled radio recordings are set again too (the database is read off the main thread)
        val pending = goAsync()
        AppScope.launch(Dispatchers.IO) {
            try {
                RecordingAlarms.rescheduleAll(app)
            } finally {
                pending.finish()
            }
        }
    }
}

package app.nagmedar.ui.sleep

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.input.hasPrecisePointer
import app.nagmedar.ui.notify.NotificationIds
import app.nagmedar.ui.rememberNagmedarPreferences
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.videolan.vlc.PlaybackService
import java.util.Date

/**
 * Android 17: the alarm's page, over the lock screen. An app that targets 17 makes no sound from the background unless
 * the user started it there (a tap in the app, its notification or widget, a media button), and an alarm is none of
 * these: its music would be silenced. So the alarm posts a notification ([AlarmNotification]) whose full-screen intent
 * opens this page at its time (on a phone in use, a heads-up with the system's alarm tone until it is tapped); the
 * page starts the music as the alarm always did, its volume rising, and Nağmedar's playback goes on after it closes.
 * "Stop the music" ends it; "Keep playing" closes the page. An app that targets 36 rings as before, without it.
 */
class AlarmActivity : ComponentActivity() {
    /** Whether this page started the alarm's music (a page recreated does not start it again). */
    private var started = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        started = savedInstanceState?.getBoolean(KEY_STARTED) ?: !intent.getBooleanExtra(Alarms.EXTRA_RING, false)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val time = DateFormat.getTimeFormat(this).format(Date())
        val mode = if (hasPrecisePointer(this)) InputMode.Pointer else InputMode.Touch
        setContent {
            val prefs = rememberNagmedarPreferences()
            NagmedarTheme(themeMode = ThemeMode.Dark, accent = prefs.accent, inputMode = mode) {
                AlarmPage(
                    time = time,
                    onStop = {
                        PlaybackService.instance?.stop()
                        finish()
                    },
                    onKeepPlaying = { finish() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // shown, the music may start: once a ring
        if (started) return
        started = true
        AlarmNotification.cancel(this)
        Alarms.play(applicationContext, Alarms.load(this))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_STARTED, started)
    }

    private companion object {
        const val KEY_STARTED = "started"
    }
}

/** The alarm's page: the time, and what to do with the music. */
@Composable
internal fun AlarmPage(time: String, onStop: () -> Unit, onKeepPlaying: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(dimens.gapL)
            .testTag("alarm-page"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(NagIcons.Alarm, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(dimens.gapM))
        Text(stringResource(R.string.nag_alarm_title), style = NagTheme.type.title, color = colors.textSecondary, modifier = Modifier.semantics { heading() })
        Text(time, style = NagTheme.type.display, color = colors.text)
        Spacer(Modifier.height(dimens.gapL))
        NagButton(stringResource(R.string.nag_alarm_stop_music), onStop, icon = NagIcons.Stop, primary = true, modifier = Modifier.testTag("alarm-stop"))
        Spacer(Modifier.height(dimens.gapS))
        NagButton(stringResource(R.string.nag_alarm_keep_playing), onKeepPlaying, modifier = Modifier.testTag("alarm-keep"))
    }
}

/**
 * Android 17: the alarm rings as a notification that opens [AlarmActivity]: over the lock screen at once (its full-
 * screen intent), or, on a phone in use, as a heads-up with the system's alarm tone, repeated until it is tapped or
 * swiped away. Nothing without the notification permission (the alarm then plays as before).
 */
internal object AlarmNotification {
    private const val CHANNEL = "nagmedar_alarm"

    /** What the alarm's page lacks: the notifications (it does not come), or the full screen (it waits for a tap). */
    enum class Missing { Notifications, FullScreen }

    fun missing(context: Context): Missing? {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return Missing.Notifications
        return when {
            !canPost(context, manager) -> Missing.Notifications
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !manager.canUseFullScreenIntent() -> Missing.FullScreen
            else -> null
        }
    }

    /** The system's page that gives what is [missing]. */
    fun openSettings(context: Context, missing: Missing) {
        val intent = when (missing) {
            Missing.Notifications -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            Missing.FullScreen -> Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun canPost(context: Context, manager: NotificationManager): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return manager.areNotificationsEnabled()
    }

    /** Whether it was posted. */
    fun post(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!canPost(context, manager)) return false
        if (manager.getNotificationChannel(CHANNEL) == null) {
            val tone = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, context.getString(R.string.nag_alarm_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), tone)
                    setShowBadge(false)
                },
            )
        }
        val open = Intent(context, AlarmActivity::class.java)
            .putExtra(Alarms.EXTRA_RING, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val page = PendingIntent.getActivity(context, NotificationIds.ALARM, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(org.videolan.resources.R.drawable.ic_nagmedar_mark_mono)
            .setContentTitle(context.getString(R.string.nag_alarm_title))
            .setContentText(context.getString(R.string.nag_alarm_ringing))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(page)
            .setFullScreenIntent(page, true)
            .setAutoCancel(true)
            .build()
        // the tone again until it is answered
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        return runCatching { manager.notify(NotificationIds.ALARM, notification) }.isSuccess
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java)?.cancel(NotificationIds.ALARM) }
    }
}

package app.nagmedar.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import app.nagmedar.ui.NagmedarActivity
import app.nagmedar.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.ACTION_REMOTE_BACKWARD
import org.videolan.resources.ACTION_REMOTE_FORWARD
import org.videolan.resources.ACTION_REMOTE_PLAYPAUSE
import org.videolan.resources.START_ACTIVITY
import org.videolan.tools.AppScope
import org.videolan.tools.NAGMEDAR_ACTION_NOW_PLAYING
import org.videolan.tools.Notices
import org.videolan.tools.Settings
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.database.MediaDatabase
import org.videolan.vlc.gui.helpers.AudioUtil
import org.videolan.vlc.media.CoverHook
import org.videolan.vlc.media.PlaybackObserver
import org.videolan.vlc.util.getPendingIntent

/**
 * Classic UI: Nağmedar's one home screen widget, in place of VLC's three (white, black and the
 * mini player, with their settings window): what plays with its cover, previous, play or pause
 * and next; a tap elsewhere opens what plays in Nağmedar (or the app). One row at 4×1, the cover
 * beside the text and the buttons from about 4×2; light or dark with the launcher's theme.
 */
class PlayerWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        PlayerWidgets.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        PlayerWidgets.refresh(context)
    }
}

/** What the widget shows: the media's title and its artist (or what it is), whether it plays, its cover. */
internal data class WidgetState(val title: String?, val subtitle: String?, val playing: Boolean, val hasMedia: Boolean, val coverPath: String?)

/**
 * Draws the widgets placed from the playback service's state: [PlayerWidgetObserver] asks while
 * the service lives, the widget itself when it is placed, resized or the launcher restarts. The
 * cover is read off the main thread, once a media.
 */
internal object PlayerWidgets {
    private var last: WidgetState? = null
    private var cover: Bitmap? = null
    private var coverOf: String? = null
    private var job: Job? = null

    /** The widgets drawn again a moment later (the service's changes come in bursts). Any thread. */
    fun refresh(context: Context, delayMs: Long = 0L) {
        val app = context.applicationContext
        AppScope.launch {
            job?.cancel()
            job = launch {
                if (delayMs > 0) delay(delayMs)
                draw(app, stateOf(PlaybackService.instance))
            }
        }
    }

    /** The service went: the widgets keep what played last, paused, for its play button to start again. */
    fun stopped(context: Context) {
        val app = context.applicationContext
        AppScope.launch {
            job?.cancel()
            draw(app, (last ?: WidgetState(null, null, false, false, null)).copy(playing = false))
        }
    }

    private fun stateOf(service: PlaybackService?): WidgetState {
        val media = service?.currentMediaWrapper ?: return last?.copy(playing = false) ?: WidgetState(null, null, false, false, null)
        val subtitle = service.artist?.takeIf { it.isNotBlank() } ?: service.album?.takeIf { it.isNotBlank() }
        val coverPath = if (media.type == MediaWrapper.TYPE_VIDEO) null else CoverHook.artwork(media)
        return WidgetState(service.title ?: media.title, subtitle, service.isPlaying, true, coverPath)
    }

    private suspend fun draw(context: Context, state: WidgetState) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = runCatching { manager.getAppWidgetIds(ComponentName(context, PlayerWidget::class.java)) }.getOrNull()
        last = state
        if (ids == null || ids.isEmpty()) return
        val path = state.coverPath
        if (path != coverOf) {
            // kept only once read: a draw cancelled meanwhile reads it again
            cover = path?.let { withContext(Dispatchers.IO) { runCatching { AudioUtil.readCoverBitmap(Uri.decode(it), COVER_PX) }.getOrNull() } }
            coverOf = path
        }
        val bar = views(context, R.layout.nagmedar_widget_bar, state, cover)
        val tall = views(context, R.layout.nagmedar_widget_cover, state, cover)
        ids.forEach { id ->
            val views = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                RemoteViews(mapOf(SizeF(180f, 40f) to bar, SizeF(180f, TALL_DP.toFloat()) to tall))
            } else {
                // before Android 12 the size is the launcher's word: the cover from about two rows
                val height = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                if (height >= TALL_DP) tall else bar
            }
            runCatching { manager.updateAppWidget(id, views) }
        }
    }

    private fun views(context: Context, @LayoutRes layout: Int, state: WidgetState, cover: Bitmap?): RemoteViews {
        val views = RemoteViews(context.packageName, layout)
        val said = when {
            !state.hasMedia -> R.string.nag_widget_idle
            state.playing -> R.string.nag_widget_playing
            else -> R.string.nag_widget_paused
        }
        views.setTextViewText(R.id.nag_widget_title, state.title ?: context.getString(R.string.app_name))
        views.setTextViewText(R.id.nag_widget_subtitle, state.subtitle ?: context.getString(said))
        if (cover != null) views.setImageViewBitmap(R.id.nag_widget_cover, cover) else views.setImageViewResource(R.id.nag_widget_cover, R.drawable.nagmedar_widget_no_cover)
        views.setImageViewResource(R.id.nag_widget_play, if (state.playing) R.drawable.nagmedar_pip_pause else R.drawable.nagmedar_pip_play)
        views.setContentDescription(R.id.nag_widget_play, context.getString(if (state.playing) R.string.nag_pause else R.string.nag_play))
        // the service plays, pauses, or starts the last list again when nothing is loaded
        views.setOnClickPendingIntent(R.id.nag_widget_play, context.getPendingIntent(Intent(ACTION_REMOTE_PLAYPAUSE, null, context, PlaybackService::class.java)))
        views.setOnClickPendingIntent(R.id.nag_widget_previous, context.getPendingIntent(Intent(ACTION_REMOTE_BACKWARD, null, context, PlaybackService::class.java)))
        views.setOnClickPendingIntent(R.id.nag_widget_next, context.getPendingIntent(Intent(ACTION_REMOTE_FORWARD, null, context, PlaybackService::class.java)))
        val open = if (state.hasMedia) {
            Intent(NAGMEDAR_ACTION_NOW_PLAYING).setClassName(context, START_ACTIVITY)
        } else {
            Intent(context, NagmedarActivity::class.java)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        views.setOnClickPendingIntent(R.id.nag_widget_root, PendingIntent.getActivity(context, OPEN_REQUEST, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return views
    }

    /** From this height (dp) the widget shows its cover beside the text. */
    private const val TALL_DP = 100
    private const val COVER_PX = 256
    private const val OPEN_REQUEST = 0x4E57
}

/**
 * VLC's widgets went with this version: the launcher took the ones placed away. Once, if VLC's
 * mini player had been placed (VLC's widget table keeps a row for each one it configured), the
 * window says so with a button that asks the launcher to place Nağmedar's (where it can).
 */
object PlayerWidgetMigration {
    private const val KEY_TOLD = "nagmedar_widget_migration_told"

    suspend fun noticeIfNeeded(context: Context) {
        val app = context.applicationContext
        val settings = Settings.getInstance(app)
        if (settings.getBoolean(KEY_TOLD, false)) return
        val placed = withContext(Dispatchers.IO) { runCatching { MediaDatabase.getInstance(app).widgetDao().getAll().isNotEmpty() }.getOrDefault(false) }
        settings.edit().putBoolean(KEY_TOLD, true).apply()
        if (!placed) return
        val manager = AppWidgetManager.getInstance(app)
        val pin = if (manager?.isRequestPinAppWidgetSupported == true) {
            { runCatching { manager.requestPinAppWidget(ComponentName(app, PlayerWidget::class.java), null, null) }; Unit }
        } else {
            null
        }
        Notices.show(app, app.getString(R.string.nag_widget_replaced), long = true, actionLabel = pin?.let { app.getString(R.string.nag_widget_add) }, action = pin)
    }
}

/** Follows the playback service for the widgets while it lives (found by name, as the other observers). */
class PlayerWidgetObserver : PlaybackObserver {
    private var service: PlaybackService? = null

    private val callback = object : PlaybackService.Callback {
        override fun update() {
            service?.let { PlayerWidgets.refresh(it, REFRESH_MS) }
        }

        override fun onMediaEvent(event: IMedia.Event) {
            if (event.type == IMedia.Event.MetaChanged) update()
        }

        override fun onMediaPlayerEvent(event: MediaPlayer.Event) {
            when (event.type) {
                MediaPlayer.Event.Playing, MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped, MediaPlayer.Event.MediaChanged -> update()
            }
        }
    }

    override fun attach(service: PlaybackService) {
        this.service = service
        service.addCallback(callback)
        PlayerWidgets.refresh(service)
    }

    override fun detach() {
        val service = service ?: return
        service.removeCallback(callback)
        this.service = null
        PlayerWidgets.stopped(service)
    }

    private companion object {
        const val REFRESH_MS = 250L
    }
}

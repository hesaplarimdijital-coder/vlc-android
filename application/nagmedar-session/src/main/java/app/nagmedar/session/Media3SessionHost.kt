package app.nagmedar.session

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/** A custom action the service offers next to the transport controls (bookmark, ±10 s, speed …). */
data class SessionAction(val action: String, val label: String, @DrawableRes val icon: Int)

/**
 * Owns the Media3 [MediaSession] for the playback service (S-502). The service keeps its own
 * notification and browse tree; it hands [compatToken] to both, calls [invalidate] when its state
 * or metadata change and [setActions] with the custom actions it currently offers.
 */
@OptIn(UnstableApi::class)
class Media3SessionHost(context: Context, private val source: SessionSource, sessionActivity: PendingIntent?) {

    val player = NagmedarSessionPlayer(Looper.getMainLooper(), source)

    private var actions: List<SessionAction> = emptyList()

    val session: MediaSession = MediaSession.Builder(context, player)
        .setId(SESSION_ID)
        .setCallback(Callback())
        .apply { if (sessionActivity != null) setSessionActivity(sessionActivity) }
        .build()

    /** Token of the platform session behind the Media3 one, for MediaBrowserServiceCompat and MediaStyle. */
    @Suppress("DEPRECATION")
    val compatToken: MediaSessionCompat.Token
        get() = session.sessionCompatToken

    fun invalidate() = player.invalidate()

    /** Replaces the custom actions shown by controllers; unchanged lists are not republished. */
    fun setActions(newActions: List<SessionAction>) {
        if (newActions == actions) return
        actions = newActions
        session.setCustomLayout(newActions.map { it.toButton() })
        // controllers connected before the change need the new commands too
        session.connectedControllers.forEach { controller ->
            session.setAvailableCommands(controller, sessionCommands(), player.availableCommands)
        }
    }

    fun release() {
        session.release()
        player.release()
    }

    private fun SessionAction.toButton(): CommandButton =
        CommandButton.Builder()
            .setSessionCommand(SessionCommand(action, Bundle.EMPTY))
            .setDisplayName(label)
            .setIconResId(icon)
            .build()

    private fun sessionCommands() = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
        .apply { actions.forEach { add(SessionCommand(it.action, Bundle.EMPTY)) } }
        .build()

    private inner class Callback : MediaSession.Callback {

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
            MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands())
                .build()

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            source.customAction(customCommand.customAction, args)
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /** Requests carry only a media id or a search; the player resolves them through the service. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(mediaItems)

        override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean =
            source.mediaButton(intent)
    }

    companion object {
        const val SESSION_ID = "nagmedar"
    }
}

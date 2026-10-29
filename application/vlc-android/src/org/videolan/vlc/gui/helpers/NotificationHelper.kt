/*****************************************************************************
 * NotificationHelper.java
 *
 * Copyright © 2017 VLC authors and VideoLAN
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 */
package org.videolan.vlc.gui.helpers


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.media.session.MediaButtonReceiver
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.resources.ACTION_DISABLE_SERVER
import org.videolan.resources.ACTION_PAUSE_SCAN
import org.videolan.resources.ACTION_RESUME_SCAN
import org.videolan.resources.ACTION_START_SERVER
import org.videolan.resources.ACTION_STOP_SERVER
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.CUSTOM_ACTION
import org.videolan.resources.CUSTOM_ACTION_EPISODE_BACK
import org.videolan.resources.CUSTOM_ACTION_EPISODE_FORWARD
import org.videolan.resources.EXTRA_CUSTOM_ACTION_ID
import org.videolan.resources.START_ACTIVITY
import org.videolan.tools.NAGMEDAR_ACTION_LIBRARY
import org.videolan.tools.getContextWithLocale
import org.videolan.vlc.R
import org.videolan.vlc.StartActivity
import org.videolan.vlc.util.FlagSet
import org.videolan.vlc.util.PlaybackAction
import org.videolan.vlc.util.TextUtils
import kotlin.math.absoluteValue

private const val MEDIALIBRRARY_CHANNEL_ID = "vlc_medialibrary"
private const val PLAYBACK_SERVICE_CHANNEL_ID = "vlc_playback"
private const val REMOTE_ACCESS_CHANNEL_ID = "vlc_remote_access"
private const val REMOTE_ACCESS_OTP_CHANNEL_ID = "vlc_remote_access_otp"
const val MISC_CHANNEL_ID = "misc"
private const val RECOMMENDATION_CHANNEL_ID = "vlc_recommendations"

object NotificationHelper {
    const val TAG = "VLC/NotificationHelper"
    const val VLC_DEBUG_CHANNEL = "vlc_debug"

    private val notificationIntent = Intent()

    fun createPlaybackNotification(ctx: Context, title: String, artist: String,
                                   album: String, cover: Bitmap?, playing: Boolean, pausable: Boolean,
                                   seekable: Boolean, episode: Boolean,
                                   seekInCompactView: Boolean, enabledActions: FlagSet<PlaybackAction>,
                                   sessionToken: MediaSessionCompat.Token?,
                                   spi: PendingIntent?,
                                   castingTo: String? = null): Notification {

        val piStop = MediaButtonReceiver.buildMediaButtonPendingIntent(ctx, PlaybackStateCompat.ACTION_STOP)
        val builder = NotificationCompat.Builder(ctx, PLAYBACK_SERVICE_CHANNEL_ID)
        // Nağmedar: its mark, and its icons on the buttons
        builder.setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(title)
                .setContentText(TextUtils.separatedString(artist, album))
                .setLargeIcon(cover)
                .setTicker(TextUtils.separatedString(title, artist))
                .setAutoCancel(!playing)
                .setOngoing(playing)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setDeleteIntent(piStop)
                .setColor(Color.BLACK)
        // Nağmedar (S-1302): "Casting to Salon TV" over the title while a renderer plays it
        castingTo?.let { builder.setSubText(it) }
        spi?.let { builder.setContentIntent(it) }
        /* Previous */
        builder.addAction(NotificationCompat.Action(R.drawable.nagmedar_notif_previous, ctx.getString(R.string.previous),
                buildMediaButtonPendingIntent(ctx, enabledActions, PlaybackAction.ACTION_SKIP_TO_PREVIOUS)))
        /* Rewind: 1.0, an episode's 15 s back (SessionButtons) */
        builder.addAction(if (episode) {
            NotificationCompat.Action(R.drawable.nagmedar_episode_back_15, ctx.getString(R.string.nagmedar_episode_back),
                    buildCustomButtonPendingIntent(ctx, CUSTOM_ACTION_EPISODE_BACK))
        } else {
            NotificationCompat.Action(R.drawable.nagmedar_pip_back, ctx.getString(R.string.playback_rewind),
                    buildMediaButtonPendingIntent(ctx, enabledActions, PlaybackAction.ACTION_REWIND))
        })
        /* Play/Pause or Stop */
        if (pausable) {
            if (playing) builder.addAction(NotificationCompat.Action(R.drawable.nagmedar_pip_pause, ctx.getString(R.string.pause),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(ctx, PlaybackStateCompat.ACTION_PLAY_PAUSE)))
            else builder.addAction(NotificationCompat.Action(R.drawable.nagmedar_pip_play, ctx.getString(R.string.play),
                    MediaButtonReceiver.buildMediaButtonPendingIntent(ctx, PlaybackStateCompat.ACTION_PLAY_PAUSE)))
        } else builder.addAction(NotificationCompat.Action(R.drawable.nagmedar_notif_stop, ctx.getString(R.string.stop), piStop))
        /* Fast Forward: 1.0, an episode's 30 s forward */
        builder.addAction(if (episode) {
            NotificationCompat.Action(R.drawable.nagmedar_episode_forward_30, ctx.getString(R.string.nagmedar_episode_forward),
                    buildCustomButtonPendingIntent(ctx, CUSTOM_ACTION_EPISODE_FORWARD))
        } else {
            NotificationCompat.Action(R.drawable.nagmedar_pip_forward, ctx.getString(R.string.playback_forward),
                    buildMediaButtonPendingIntent(ctx, enabledActions, PlaybackAction.ACTION_FAST_FORWARD))
        })
        /* Next */
        builder.addAction(NotificationCompat.Action(R.drawable.nagmedar_notif_next, ctx.getString(R.string.next),
                buildMediaButtonPendingIntent(ctx, enabledActions, PlaybackAction.ACTION_SKIP_TO_NEXT)))
        if (AndroidDevices.showMediaStyle) {
            val showActions = if (episode || (seekable && seekInCompactView)) intArrayOf(1, 2, 3) else intArrayOf(0, 2, 4)
            val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(*showActions)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(piStop)
            sessionToken?.let { mediaStyle.setMediaSession(it) }
            builder.setStyle(mediaStyle)
        }
        return builder.build()
    }

    /** Nağmedar's speed gauges, by the speed each needle shows. */
    private val speedIcons = listOf(
            0.50f to R.drawable.nagmedar_notif_speed_0_50,
            0.80f to R.drawable.nagmedar_notif_speed_0_80,
            1.00f to R.drawable.nagmedar_notif_speed_1_00,
            1.10f to R.drawable.nagmedar_notif_speed_1_10,
            1.20f to R.drawable.nagmedar_notif_speed_1_20,
            1.50f to R.drawable.nagmedar_notif_speed_1_50,
            2.00f to R.drawable.nagmedar_notif_speed_2_00,
    )

    /**
     * Nağmedar: the playback speed button's icon, the new interface's speed gauge with its needle
     * turned to the nearest of the speeds drawn (the podcast notification, the system's media
     * controls, Android Auto).
     */
    @DrawableRes
    fun speedIcon(speed: Float): Int = speedIcons.minBy { (speed - it.first).absoluteValue }.second

    private fun buildMediaButtonPendingIntent(ctx: Context, enabledActions: FlagSet<PlaybackAction>, action: PlaybackAction, allowIntent: Boolean = true): PendingIntent? {
        return if (allowIntent && enabledActions.contains(action))
            MediaButtonReceiver.buildMediaButtonPendingIntent(ctx, action.toLong())
        else null
    }

    private fun buildCustomButtonPendingIntent(ctx: Context, actionId: String): PendingIntent {
        val intent = Intent(CUSTOM_ACTION)
        intent.putExtra(EXTRA_CUSTOM_ACTION_ID, actionId)
        return PendingIntent.getBroadcast(ctx, actionId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun createScanNotification(ctx: Context, progressText: String, paused: Boolean, max:Int, progress: Int): Notification {
        // the Nağmedar window on its home page (a VIEW without data opened nothing)
        val intent = Intent(NAGMEDAR_ACTION_LIBRARY).setClassName(ctx, START_ACTIVITY)
        val scanCompatBuilder = NotificationCompat.Builder(ctx, MEDIALIBRRARY_CHANNEL_ID)
                .setContentIntent(PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(ctx.getString(R.string.ml_scanning))
                .setProgress(max, progress, max < 1 || progress < 1)
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setOngoing(true)
        scanCompatBuilder.setContentText(progressText)

        notificationIntent.action = if (paused) ACTION_RESUME_SCAN else ACTION_PAUSE_SCAN
        notificationIntent.`package` = ctx.packageName
        val pi = PendingIntent.getBroadcast(ctx.applicationContext.getContextWithLocale(AppContextProvider.locale), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val playpause = if (paused)
            NotificationCompat.Action(R.drawable.nagmedar_pip_play, ctx.getString(R.string.resume), pi)
        else
            NotificationCompat.Action(R.drawable.nagmedar_pip_pause, ctx.getString(R.string.pause), pi)
        scanCompatBuilder.addAction(playpause)
        return scanCompatBuilder.build()
    }

    fun createRemoteAccessNotification(ctx: Context, connectionTip: String, started: Boolean): Notification {
        val intent = Intent(ctx, StartActivity::class.java).apply { action = "vlc.remoteaccess.share" }
        val remoteAccessCompatBuilder = NotificationCompat.Builder(ctx, REMOTE_ACCESS_CHANNEL_ID)
                .setContentIntent(PendingIntent.getActivity(ctx, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
                .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(ctx.getString(R.string.ra_server_running))
                .setAutoCancel(false)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setOngoing(true)
        remoteAccessCompatBuilder.setContentText(connectionTip)

        //disable
        val disableIntent = Intent()
        disableIntent.action = ACTION_DISABLE_SERVER
        disableIntent.`package` = ctx.packageName
        val piDisable = PendingIntent.getBroadcast(ctx.applicationContext.getContextWithLocale(AppContextProvider.locale), 0, disableIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val disableAction =    NotificationCompat.Action(R.drawable.nagmedar_notif_close, ctx.getString(R.string.ra_disable), piDisable)
        remoteAccessCompatBuilder.addAction(disableAction)

        //Start / Stop
        val notificationIntent = Intent()
        notificationIntent.action = if (started) ACTION_STOP_SERVER else ACTION_START_SERVER
        notificationIntent.`package` = ctx.packageName
        val pi = PendingIntent.getBroadcast(ctx.applicationContext.getContextWithLocale(AppContextProvider.locale), 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val action = NotificationCompat.Action(if (started) R.drawable.nagmedar_notif_stop else R.drawable.nagmedar_pip_play, if (started) ctx.getString(R.string.stop) else ctx.getString(R.string.start), pi)
        remoteAccessCompatBuilder.addAction(action)
        return remoteAccessCompatBuilder.build()
    }

    fun createRemoteAccessOtpNotification(ctx: Context, code:String): Notification {
        val remoteAccessCompatBuilder = NotificationCompat.Builder(ctx, REMOTE_ACCESS_OTP_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nagmedar_mark_mono)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle(ctx.getString(R.string.ra_otp_title))
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
        remoteAccessCompatBuilder.setContentText(ctx.getString(R.string.ra_code, code))

        return remoteAccessCompatBuilder.build()
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun createNotificationChannels(appCtx: Context) {
        if (!AndroidUtil.isOOrLater) return
        val notificationManager = appCtx.getSystemService<NotificationManager>()!!
        val channels = mutableListOf<NotificationChannel>()
        // Playback channel
        if (notificationManager.getNotificationChannel(PLAYBACK_SERVICE_CHANNEL_ID) == null ) {
            val name: CharSequence = appCtx.getString(R.string.playback)
            val description = appCtx.getString(R.string.playback_controls)
            val channel = NotificationChannel(PLAYBACK_SERVICE_CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
            channel.description = description
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channels.add(channel)
        }
        // Scan channel
        if (notificationManager.getNotificationChannel(MEDIALIBRRARY_CHANNEL_ID) == null ) {
            val name = appCtx.getString(R.string.medialibrary_scan)
            val description = appCtx.getString(R.string.Medialibrary_progress)
            val channel = NotificationChannel(MEDIALIBRRARY_CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
            channel.description = description
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channels.add(channel)
        }

        // Web server channel
        if (notificationManager.getNotificationChannel(REMOTE_ACCESS_CHANNEL_ID) == null ) {
            val name = appCtx.getString(R.string.ra_remote_access)
            val description = appCtx.getString(R.string.ra_remote_access_description)
            val channel = NotificationChannel(REMOTE_ACCESS_CHANNEL_ID, name, NotificationManager.IMPORTANCE_MIN)
            channel.description = description
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channels.add(channel)
        }

        // Web server OTP channel
        if (notificationManager.getNotificationChannel(REMOTE_ACCESS_OTP_CHANNEL_ID) == null ) {
            val name = appCtx.getString(R.string.ra_otp)
            val description = appCtx.getString(R.string.ra_otp_description)
            val channel = NotificationChannel(REMOTE_ACCESS_OTP_CHANNEL_ID, name, NotificationManager.IMPORTANCE_HIGH)
            channel.description = description
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                channel.setAllowBubbles(true)
            }
            channels.add(channel)
        }

        // Misc channel
        if (notificationManager.getNotificationChannel(MISC_CHANNEL_ID) == null ) {
            val name = appCtx.getString(R.string.misc)
            val channel = NotificationChannel(MISC_CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channels.add(channel)
        }

        // Recommendations channel
        if (AndroidDevices.isAndroidTv && notificationManager.getNotificationChannel(RECOMMENDATION_CHANNEL_ID) == null) {
            val name = appCtx.getString(R.string.recommendations)
            val description = appCtx.getString(R.string.recommendations_desc)
            val channel = NotificationChannel(RECOMMENDATION_CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW)
            channel.description = description
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channels.add(channel)
        }
        if (channels.isNotEmpty()) notificationManager.createNotificationChannels(channels)
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    fun createDebugServcieChannel(appCtx: Context) {
        val notificationManager = appCtx.getSystemService<NotificationManager>()!!
// Playback channel
        val name = appCtx.getString(R.string.debug_logs)
        val channel = NotificationChannel(VLC_DEBUG_CHANNEL, name, NotificationManager.IMPORTANCE_LOW)
        channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        notificationManager.createNotificationChannel(channel)
    }
}

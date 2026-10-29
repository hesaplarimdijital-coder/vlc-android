/**
 * **************************************************************************
 * MediaSessionCallback.kt
 * ****************************************************************************
 * Copyright © 2018 VLC authors and VideoLAN
 * Author: Geoffrey Métais
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
 * ***************************************************************************
 */
package org.videolan.vlc

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.ContentUris
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import app.nagmedar.playback.SessionButtons
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.medialibrary.Tools
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.resources.CUSTOM_ACTION_BOOKMARK
import org.videolan.resources.CUSTOM_ACTION_EPISODE_BACK
import org.videolan.resources.CUSTOM_ACTION_EPISODE_FORWARD
import org.videolan.resources.CUSTOM_ACTION_FAST_FORWARD
import org.videolan.resources.CUSTOM_ACTION_REPEAT
import org.videolan.resources.CUSTOM_ACTION_REWIND
import org.videolan.resources.CUSTOM_ACTION_SHUFFLE
import org.videolan.resources.CUSTOM_ACTION_SPEED
import org.videolan.resources.util.getFromMl
import org.videolan.resources.util.parcelable
import org.videolan.tools.KEY_IGNORE_HEADSET_MEDIA_BUTTON_PRESSES
import org.videolan.tools.KEY_PLAYBACK_SPEED_AUDIO_GLOBAL
import org.videolan.tools.KEY_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE
import org.videolan.tools.Settings
import org.videolan.vlc.media.CarHook
import org.videolan.vlc.media.MediaSessionBrowser
import org.videolan.vlc.media.PlaylistManager
import org.videolan.vlc.media.VoiceFocus
import org.videolan.vlc.media.VoiceRequest
import org.videolan.vlc.util.Permissions.canCheckBluetoothDevices
import org.videolan.vlc.util.TextUtils
import org.videolan.vlc.util.mergeSorted
import kotlin.math.absoluteValue

private const val TAG = "VLC/MediaSessionCallback"
private const val ONE_SECOND = 1000L

internal class MediaSessionCallback(private val playbackService: PlaybackService) : MediaSessionCompat.Callback() {
    private var prevActionSeek = false

    override fun onPlay() {
        if (playbackService.hasMedia()) playbackService.play()
        else if (!AndroidDevices.isAndroidTv) PlaybackService.loadLastAudio(playbackService)
    }

    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
        val keyEvent = mediaButtonEvent.parcelable(Intent.EXTRA_KEY_EVENT) as KeyEvent? ?: return false

        if (playbackService.detectHeadset &&
            playbackService.settings.getBoolean(KEY_IGNORE_HEADSET_MEDIA_BUTTON_PRESSES, false)) {
            // Wired headset
            if (playbackService.headsetInserted && isWiredHeadsetHardKey(keyEvent)) {
                return true
            }

            // Bluetooth headset
            if (canCheckBluetoothDevices(playbackService.applicationContext)) {
                BluetoothAdapter.getDefaultAdapter()?.let { bluetoothAdapter ->
                    if (BluetoothAdapter.STATE_CONNECTED == bluetoothAdapter.getProfileConnectionState(BluetoothProfile.HEADSET)
                        && isBluetoothHeadsetHardKey(keyEvent)) {
                        return true
                    }
                }
            }
        }

        if (!playbackService.hasMedia()
                && (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
            return if (keyEvent.action == KeyEvent.ACTION_DOWN) {
                PlaybackService.loadLastAudio(playbackService)
                true
            } else false
        }
        /**
         * Implement fast forward and rewind behavior by directly handling the previous and next button events.
         * Normally the buttons are triggered on ACTION_DOWN; however, we ignore the ACTION_DOWN event when
         * isAndroidAutoHardKey returns true, and perform the operation on the ACTION_UP event instead. If the previous or
         * next button is held down, a callback occurs with the long press flag set. When a long press is received,
         * invoke the onFastForward() or onRewind() methods, and set the prevActionSeek flag. The ACTION_UP event
         * action is bypassed if the flag is set. The prevActionSeek flag is reset to false for the next invocation.
         */
        if (isAndroidAutoHardKey(keyEvent) && (keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS || keyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_NEXT)) {
            when (keyEvent.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (playbackService.isSeekable && keyEvent.isLongPress) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_NEXT -> onFastForward()
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> onRewind()
                        }
                        prevActionSeek = true
                    }
                }
                KeyEvent.ACTION_UP -> {
                    if (!prevActionSeek) {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_NEXT -> onSkipToNext()
                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> onSkipToPrevious()
                        }
                    }
                    prevActionSeek = false
                }
            }
            return true
        }
        return super.onMediaButtonEvent(mediaButtonEvent)
    }

    private fun jumpToTimelineEntry(previous: Boolean) {
        val ctx = playbackService.applicationContext
        playbackService.lifecycleScope.launch {
            val currentTime = playbackService.getTime()
            val entryList = getChapterList().toMutableList().apply {
                mergeSorted(getBookmarkList()) { it.time }
            }
            if (entryList.isEmpty()) {
                playbackService.displaySubtitleMessage(ctx.getString(R.string.no_bookmark))
                return@launch
            }
            val index = entryList.binarySearchBy(currentTime) { it.time }
            var eIndex = when {
                index >= 0 -> index + if (previous) -1 else 1
                else -> -index - if (previous) 2 else 1
            }.coerceIn(entryList.indices)
            // Point to the previous element if the time difference is less than 5 seconds
            if (previous && (currentTime - entryList[eIndex].time) < 5_000L) {
                eIndex = (eIndex - 1).coerceIn(entryList.indices)
            }
            // Ignore button press if within 2 seconds of the first or last entry
            if ((eIndex == 0 || eIndex == entryList.size - 1) && (currentTime - entryList[eIndex].time).absoluteValue <= 2_000L)
                return@launch
            // Seek to the correct time
            if ((!previous && entryList[eIndex].time >= currentTime) || (previous && entryList[eIndex].time <= currentTime)) {
                seek(entryList[eIndex].time)
                playbackService.displaySubtitleMessage("${ctx.getString(R.string.jump_to)} ${entryList[eIndex].name}")
            }
        }
    }

    private fun getChapterList(): List<TimelineEntry> {
        val ctx = playbackService.applicationContext
        return playbackService.getChapters(-1)?.mapIndexed { index, item ->
            TimelineEntry(0, item.timeOffset, TextUtils.formatChapterTitle(ctx, index + 1, item.name))
        } ?: emptyList()
    }

    private fun getBookmarkList(): List<TimelineEntry> {
        return playbackService.currentMediaWrapper?.bookmarks?.map { bookmark ->
            TimelineEntry(1, bookmark.time, bookmark.title)
        } ?: emptyList()
    }

    data class TimelineEntry(val type: Int, val time: Long, val name: String)

    /**
     * The following two functions are based on the following KeyEvent captures. They may need to be updated if the behavior changes in the future.
     *
     * KeyEvent from Media Control UI:
     * {action=ACTION_DOWN, keyCode=KEYCODE_MEDIA_PLAY_PAUSE, scanCode=0, metaState=0, flags=0x0, repeatCount=0, eventTime=0, downTime=0, deviceId=-1, source=0x0, displayId=0}
     *
     * KeyEvent from a wired headset's media button:
     * {action=ACTION_DOWN, keyCode=KEYCODE_MEDIA_PLAY_PAUSE, scanCode=0, metaState=0, flags=0x40000000, repeatCount=0, eventTime=0, downTime=0, deviceId=-1, source=0x0, displayId=0}
     *
     * KeyEvent from a Bluetooth earphone:
     * {action=ACTION_DOWN, keyCode=KEYCODE_MEDIA_PLAY, scanCode=0, metaState=0, flags=0x0, repeatCount=0, eventTime=0, downTime=0, deviceId=-1, source=0x0, displayId=0}
     */
    private fun isWiredHeadsetHardKey(keyEvent: KeyEvent): Boolean {
        return !(keyEvent.deviceId == -1 && keyEvent.flags == 0x0)
    }

    private fun isBluetoothHeadsetHardKey(keyEvent: KeyEvent): Boolean {
        return keyEvent.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && keyEvent.deviceId == -1 && keyEvent.flags == 0x0
    }

    /**
     * This function is based on the following KeyEvent captures. This may need to be updated if the behavior changes in the future.
     *
     * KeyEvent from Media Control UI:
     * {action=ACTION_DOWN, keyCode=KEYCODE_MEDIA_NEXT, scanCode=0, metaState=0, flags=0x0, repeatCount=0, eventTime=0, downTime=0, deviceId=-1, source=0x0, displayId=0}
     *
     * KeyEvent from Android Auto Steering Wheel Control:
     * {action=ACTION_DOWN, keyCode=KEYCODE_MEDIA_NEXT, scanCode=0, metaState=0, flags=0x4, repeatCount=0, eventTime=0, downTime=0, deviceId=0, source=0x0, displayId=0}
     *
     * KeyEvent from Android Auto Steering Wheel Control, Holding Switch (Long Press):
     * {action=ACTION_DOWN, keyCode=KEYCODE_MEDIA_NEXT, scanCode=0, metaState=0, flags=0x84, repeatCount=1, eventTime=0, downTime=0, deviceId=0, source=0x0, displayId=0}
     */
    @SuppressLint("LongLogTag")
    private fun isAndroidAutoHardKey(keyEvent: KeyEvent): Boolean {
        val carMode = playbackService.isCarMode()
        if (carMode) Log.i(TAG, "Android Auto Key Press: $keyEvent")
        return carMode && keyEvent.deviceId == 0 && (keyEvent.flags and KeyEvent.FLAG_KEEP_TOUCH_MODE != 0)
    }

    override fun onCustomAction(actionId: String?, extras: Bundle?) {
        when (actionId) {
            CUSTOM_ACTION_SPEED -> {
                val steps = listOf(0.50f, 0.80f, 1.00f, 1.10f, 1.20f, 1.50f, 2.00f)
                val index = 1 + steps.indexOf(steps.minByOrNull { (playbackService.rate - it).absoluteValue })
                val speed = steps[index % steps.size]
                val settings = Settings.getInstance(playbackService)
                settings.edit {
                    if (settings.getBoolean(KEY_PLAYBACK_SPEED_AUDIO_GLOBAL, false))
                        putFloat(KEY_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE, speed)
                    else
                        PlaylistManager.currentPlayedMedia.value?.setStringMeta(MediaWrapper.META_SPEED, speed.toString())
                }
                playbackService.setRate(speed, true)
            }
            CUSTOM_ACTION_BOOKMARK -> {
                playbackService.lifecycleScope.launch {
                    val context = playbackService.applicationContext
                    playbackService.currentMediaWrapper?.let {
                        val bookmark = it.addBookmark(playbackService.getTime())
                        val bookmarkName = context.getString(R.string.bookmark_default_name, Tools.millisToString(playbackService.getTime()))
                        bookmark?.setName(bookmarkName)
                        playbackService.displaySubtitleMessage(context.getString(R.string.saved, bookmarkName),
                                context.resources.getQuantityString(R.plurals.saved_bookmarks_quantity, it.bookmarks.size, it.bookmarks.size))
                    }
                }
            }
            CUSTOM_ACTION_REWIND -> onRewind()
            CUSTOM_ACTION_FAST_FORWARD -> onFastForward()
            // 1.0: an episode's 15 s back and 30 s forward
            CUSTOM_ACTION_EPISODE_BACK -> seek(SessionButtons.episodeSeek(forward = false, playbackService.getTime(), playbackService.length))
            CUSTOM_ACTION_EPISODE_FORWARD -> {
                seek(SessionButtons.episodeSeek(forward = true, playbackService.getTime(), playbackService.length))
                checkForSeekFailure(forward = true)
            }
            CUSTOM_ACTION_SHUFFLE -> if (playbackService.canShuffle()) playbackService.shuffle()
            CUSTOM_ACTION_REPEAT -> playbackService.repeatType = when (playbackService.repeatType) {
                PlaybackStateCompat.REPEAT_MODE_NONE -> PlaybackStateCompat.REPEAT_MODE_ALL
                PlaybackStateCompat.REPEAT_MODE_ALL -> PlaybackStateCompat.REPEAT_MODE_ONE
                PlaybackStateCompat.REPEAT_MODE_ONE -> PlaybackStateCompat.REPEAT_MODE_NONE
                else -> PlaybackStateCompat.REPEAT_MODE_NONE
            }
        }
    }

    /**
     * 1.0: an entry of Android Auto's tree is played as the catalog says ([CarHook]): as tapping it in the app would. A
     * medialibrary media by its id (the session's metadata, the queue) plays as before; the "nothing here" entry plays
     * nothing.
     */
    override fun onPlayFromMediaId(mediaId: String, extras: Bundle?) {
        playbackService.lifecycleScope.launch {
            try {
                when {
                    mediaId == MediaSessionBrowser.ID_NO_MEDIA -> Unit
                    mediaId.startsWith("${MediaSessionBrowser.ID_MEDIA}/") -> {
                        val id = ContentUris.parseId(mediaId.toUri())
                        val tracks = playbackService.applicationContext.getFromMl { getMedia(id)?.tracks }
                        if (isActive) tracks?.let { loadMedia(it.toList()) }
                    }
                    CarHook.catalog?.play(playbackService, mediaId) != true -> throw IllegalStateException("Failed to load: $mediaId")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not play media: $mediaId", e)
                when {
                    playbackService.hasMedia() -> playbackService.play()
                    else -> playbackService.displayPlaybackError(R.string.search_no_result)
                }
            }
        }
    }

    private fun loadMedia(mediaList: List<MediaWrapper>?, position: Int = 0) {
        mediaList?.let {
            if (playbackService.isCarMode())
                mediaList.forEach { mw -> mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO) }
            playbackService.load(mediaList, position)
        }
    }

    /** The assistant's request: the words heard, and the field they are about with what it heard for each (MediaStore's extras). */
    private fun voiceRequest(query: String?, extras: Bundle?): VoiceRequest {
        val focus = when (extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)) {
            MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE -> VoiceFocus.Artist
            MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE -> VoiceFocus.Album
            MediaStore.Audio.Media.ENTRY_CONTENT_TYPE -> VoiceFocus.Title
            MediaStore.Audio.Genres.ENTRY_CONTENT_TYPE -> VoiceFocus.Genre
            MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE -> VoiceFocus.Playlist
            else -> null
        }
        return VoiceRequest(
            query = query.orEmpty(),
            focus = focus,
            artist = extras?.getString(MediaStore.EXTRA_MEDIA_ARTIST),
            album = extras?.getString(MediaStore.EXTRA_MEDIA_ALBUM),
            // "Title (Live)": the assistant hears the title, not what the brackets add
            title = extras?.getString(MediaStore.EXTRA_MEDIA_TITLE)?.substringBefore('(')?.trim(),
            genre = extras?.getString(MediaStore.EXTRA_MEDIA_GENRE),
            playlist = extras?.getString(MediaStore.EXTRA_MEDIA_PLAYLIST),
        )
    }

    private fun seek(position: Long) {
        playbackService.seek(position, fromUser = true)
        playbackService.playlistManager.player.updateProgress(position)
    }

    private fun checkForSeekFailure(forward: Boolean) {
        // S-1009: libVLC's last position stays 0 while the music engine plays: whether it seeks is what counts
        if (!playbackService.isSeekable && (forward || playbackService.getTime() > 0)) {
            val context = playbackService.applicationContext
            playbackService.displaySubtitleMessage(context.getString(R.string.unseekable_stream))
        }
    }

    override fun onPlayFromUri(uri: Uri?, extras: Bundle?) = playbackService.loadUri(uri)

    /**
     * 1.0: "Hey Google, … çal": music first, by the field the assistant names (artist, album, title, genre, playlist), then
     * podcasts (a show, an episode), then radio (the favourites, then the directory); the catalog decides ([CarHook]). A
     * request with nothing in it plays what played last. Nothing found: what was loaded plays on, else Auto says there is
     * no result.
     */
    override fun onPlayFromSearch(query: String?, extras: Bundle?) {
        val playbackState = PlaybackStateCompat.Builder()
                .setActions(playbackService.enabledActions.getCapabilities())
                .setState(PlaybackStateCompat.STATE_CONNECTING, playbackService.getTime(), playbackService.speed)
                .build()
        playbackService.mediaSession.setPlaybackState(playbackState)
        val request = voiceRequest(query, extras)
        playbackService.lifecycleScope.launch {
            val played = try {
                CarHook.catalog?.play(playbackService, request) == true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not play from search: $query", e)
                false
            }
            if (played || !isActive) return@launch
            when {
                playbackService.hasMedia() -> playbackService.play()
                query.isNullOrBlank() -> PlaybackService.loadLastAudio(playbackService, playOnly = true)
                else -> playbackService.displayPlaybackError(R.string.search_no_result)
            }
        }
    }

    override fun onSetShuffleMode(shuffleMode: Int) {
        playbackService.shuffleType = shuffleMode
    }

    override fun onSetRepeatMode(repeatMode: Int) {
        playbackService.repeatType = repeatMode
    }

    override fun onPause() = playbackService.pause()

    override fun onStop() = playbackService.stop()

    override fun onSkipToNext() = when {
        playbackService.isPodcastMode -> jumpToTimelineEntry(false)
        else -> playbackService.next()
    }

    override fun onSkipToPrevious() = when {
        playbackService.isPodcastMode -> jumpToTimelineEntry(true)
        else -> playbackService.previous(false)
    }

    override fun onSeekTo(pos: Long) = seek(if (pos < 0) playbackService.getTime() + pos else pos)

    override fun onFastForward() {
        seek((playbackService.getTime() + Settings.audioJumpDelay * ONE_SECOND).coerceAtMost(playbackService.length))
        checkForSeekFailure(forward = true)
    }

    override fun onRewind() {
        seek((playbackService.getTime() - Settings.audioJumpDelay * ONE_SECOND).coerceAtLeast(0))
        checkForSeekFailure(forward = false)
    }

    override fun onSkipToQueueItem(id: Long) = playbackService.playIndexOrLoadLastPlaylist(id.toInt())

    override fun onSetPlaybackSpeed(speed: Float) = playbackService.setRate(speed.coerceIn(0.5f, 2.0f), false)

}

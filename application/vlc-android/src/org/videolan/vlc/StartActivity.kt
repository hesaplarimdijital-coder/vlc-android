/*
 * *************************************************************************
 *  StartActivity.java
 * **************************************************************************
 *  Copyright © 2015 VLC authors and VideoLAN
 *  Author: Geoffrey Métais
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *  ***************************************************************************
 */

package org.videolan.vlc

import android.Manifest
import android.app.SearchManager
import android.content.Context
import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.resources.ACTION_PLAY_FROM_SEARCH
import org.videolan.resources.ACTION_SEARCH_GMS
import org.videolan.resources.ACTION_VIEW_ARC
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.EXTRA_FIRST_RUN
import org.videolan.resources.EXTRA_SEARCH_BUNDLE
import org.videolan.resources.EXTRA_UPGRADE
import org.videolan.resources.NAGMEDAR_SHELL_ACTIVITY
import org.videolan.resources.PREF_FIRST_RUN
import org.videolan.resources.util.getFromMl
import org.videolan.resources.util.launchForeground
import org.videolan.resources.util.parcelable
import org.videolan.resources.util.startMedialibrary
import org.videolan.tools.AppScope
import org.videolan.tools.KEY_FRAGMENT_ID
import org.videolan.tools.KEY_NAVIGATOR_SCREEN_UNSTABLE
import org.videolan.tools.NAGMEDAR_ACTION_LIBRARY
import org.videolan.tools.NAGMEDAR_ACTION_NOW_PLAYING
import org.videolan.tools.NAGMEDAR_DESTINATION_NOW_PLAYING
import org.videolan.tools.NAGMEDAR_EXTRA_DESTINATION
import org.videolan.tools.NAGMEDAR_EXTRA_REMOTE_ACCESS
import org.videolan.tools.NAGMEDAR_EXTRA_SEARCH
import org.videolan.tools.NAGMEDAR_FIRST_RUN_DONE
import org.videolan.tools.NAGMEDAR_WINDOW_BOUNDS
import org.videolan.tools.PREF_SHOW_VIDEO_SETTINGS_DISCLAIMER
import org.videolan.tools.PREF_TV_UI
import org.videolan.tools.Settings
import org.videolan.tools.awaitAppIsForegroung
import org.videolan.tools.getContextWithLocale
import org.videolan.tools.putSingle
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.media.MediaUtils
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.Permissions
import org.videolan.vlc.util.Util
import org.videolan.vlc.util.checkWatchNextId
import videolan.org.commontools.TV_CHANNEL_PATH_APP
import videolan.org.commontools.TV_CHANNEL_PATH_VIDEO
import videolan.org.commontools.TV_CHANNEL_QUERY_VIDEO_ID
import videolan.org.commontools.TV_CHANNEL_SCHEME

private const val PROPAGATE_RESULT = 1
private const val TAG = "VLC/StartActivity"
class StartActivity : FragmentActivity() {

    /** The answer to Android's storage prompt, while it is asked ([askStoragePermission]). */
    private var storageAnswer: CompletableDeferred<Boolean>? = null

    private val storagePrompt = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        storageAnswer?.complete(granted.values.any { it })
    }

    private val idFromShortcut: Int
        get() {
            if (!AndroidUtil.isNougatMR1OrLater) return 0
            val intent = intent
            val action = intent?.action
            if (!action.isNullOrEmpty()) {
                return when (action) {
                    "vlc.shortcut.video" -> R.id.nav_video
                    "vlc.shortcut.audio" -> R.id.nav_audio
                    "vlc.shortcut.browser" -> R.id.nav_directories
                    "vlc.shortcut.resume" -> R.id.ml_menu_last_playlist
                    else -> 0
                }
            }
            return 0
        }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(newBase?.getContextWithLocale(AppContextProvider.locale))
    }

    override fun getApplicationContext(): Context {
        return super.getApplicationContext().getContextWithLocale(AppContextProvider.locale)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // a beta build opens Nağmedar like any other: VLC's beta welcome screen is not shown
        resume()
    }

    private fun resume() {
        // if browse screen is unstable, revert back to the video screen

        val preferences = Settings.getInstance(this)
        if (preferences.getBoolean(KEY_NAVIGATOR_SCREEN_UNSTABLE, false)) {
            Log.w(TAG, "Crash found in the browser!!!")
            if (!BuildConfig.DEBUG) {
                Log.w(TAG, "Reverting to the default screen")
                preferences.edit(true) {
                    remove(KEY_FRAGMENT_ID)
                    remove(KEY_NAVIGATOR_SCREEN_UNSTABLE)
                }
            }
        }

        val intent = intent
        val action = intent?.action

        if ((Intent.ACTION_VIEW == action || ACTION_VIEW_ARC == action)
                && TV_CHANNEL_SCHEME != intent.data?.scheme) {
            startPlaybackFromApp(intent)
            return
        } else if (Intent.ACTION_SEND == action) {
            val cd = intent.clipData
            val item = if (cd != null && cd.itemCount > 0) cd.getItemAt(0) else null
            val rawUri: Uri? = item?.uri ?: intent.parcelable<Uri>(Intent.EXTRA_STREAM)
            if (rawUri != null) {
                // try to get a mime type, amaze doesn't seem to share a video with it
                val mimeType: String? = intent.type
                    ?.takeIf { it.startsWith("video") || it.startsWith("audio") }
                    ?: runCatching { contentResolver.getType(rawUri) }.getOrNull()
                // If we have a video mime, we can force the video player
                if (mimeType?.startsWith("video") == true) {
                    startPlaybackFromApp(Intent(Intent.ACTION_VIEW, rawUri).apply {
                        setDataAndType(rawUri, mimeType)
                    })
                } else {
                    lifecycleScope.launch {
                        val uri = withContext(Dispatchers.IO) { FileUtils.getUri(rawUri) } ?: rawUri
                        var media = getFromMl { getMedia(uri) }
                        if (media == null)
                            media = MLServiceLocator.getAbstractMediaWrapper(uri)
                        MediaUtils.openMediaNoUi(this@StartActivity, media)
                        finish()
                    }
                }
                return
            } else if (item?.text != null) {
                val textUri = item.text.toString().toUri()
                lifecycleScope.launch {
                    var media = getFromMl { getMedia(textUri) }
                    if (media == null)
                        media = MLServiceLocator.getAbstractMediaWrapper(textUri)
                    MediaUtils.openMediaNoUi(this@StartActivity, media)
                    finish()
                }
                return
            }
            finish()
            return
        }

        // Setting test mode with stubbed media library if required
        if (intent.hasExtra(MLServiceLocator.EXTRA_TEST_STUBS)
                && intent.getBooleanExtra(MLServiceLocator.EXTRA_TEST_STUBS, false)) {
            MLServiceLocator.setLocatorMode(MLServiceLocator.LocatorMode.TESTS)
            Log.i(TAG, "onCreate: Setting test mode`")
        }

        // Start application
        /* Get the current version from package */
        val settings = Settings.getInstance(this)
        val currentVersionNumber = BuildConfig.VLC_VERSION_CODE
        val savedVersionNumber = settings.getInt(PREF_FIRST_RUN, -1)
        /* Check if it's the first run */
        val firstRun = savedVersionNumber == -1
        Settings.firstRun = firstRun
        if (firstRun && AndroidDevices.hasToForceTV()) {
            settings.putSingle(PREF_TV_UI, true)
        }
        val upgrade = firstRun || savedVersionNumber != currentVersionNumber
        // Nağmedar's first-run flow keeps the version once it is done (FirstRunHost)
        if (upgrade && !firstRun) settings.putSingle(PREF_FIRST_RUN, currentVersionNumber)
        val removeOldDevices = savedVersionNumber in 3028201..3028399
        settings.putSingle(PREF_SHOW_VIDEO_SETTINGS_DISCLAIMER, savedVersionNumber < 3060330 && !firstRun)
        // Route search query
        if (Intent.ACTION_SEARCH == action || ACTION_SEARCH_GMS == action) {
            // the system's search: Nağmedar's search page with the words asked for
            startNagmedar(firstRun, upgrade, 0, removeOldDevices) { putExtra(NAGMEDAR_EXTRA_SEARCH, intent.getStringExtra(SearchManager.QUERY).orEmpty()) }
            finish()
            return
        } else if (MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH == action) {
            val serviceInent = Intent(ACTION_PLAY_FROM_SEARCH, null, this, PlaybackService::class.java)
                    .putExtra(EXTRA_SEARCH_BUNDLE, intent.extras)
            launchForeground(serviceInent)
        } else if (Intent.ACTION_VIEW == action && intent.data != null) { //launch from TV Channel
            val data = intent.data
            val path = data!!.path
            if (path == "/$TV_CHANNEL_PATH_APP")
                startNagmedar(firstRun, upgrade, 0, removeOldDevices)
            else if (path == "/$TV_CHANNEL_PATH_VIDEO") {
                var id = java.lang.Long.valueOf(data.getQueryParameter(TV_CHANNEL_QUERY_VIDEO_ID)!!)
                val ctx = this@StartActivity
                lifecycleScope.launch(Dispatchers.IO) {
                    id = checkWatchNextId(ctx, id)
                    withContext(Dispatchers.Main) {
                        MediaUtils.openMediaNoUi(ctx, id)
                    }
                }
            }
        } else {
            if (action != null && action.startsWith("vlc.mediashortcut:")) {
                val split = action.split(":")
                val type = split[split.size - 2]
                val id = split.last()
                lifecycleScope.launch {
                    getFromMl {
                        val album = when(type) {
                         "album" ->   getAlbum(id.toLong())
                         "artist" ->   getArtist(id.toLong())
                         "genre" ->   getGenre(id.toLong())
                         "playlist" ->   getPlaylist(id.toLong(), false, false)
                         else ->   getMedia(id.toLong())
                        }
                        MediaUtils.playTracks(this@StartActivity, album, 0)
                    }
                }
            } else if (action == "vlc.remoteaccess.share") {
                // the remote access notification: Settings, with the window of its addresses
                startNagmedar(firstRun, upgrade, 0, removeOldDevices) {
                    putExtra(NAGMEDAR_EXTRA_DESTINATION, "settings")
                    putExtra(NAGMEDAR_EXTRA_REMOTE_ACCESS, true)
                }
            } else {
                val target = idFromShortcut
                val service = PlaybackService.instance
                if (target == R.id.ml_menu_last_playlist) {
                    PlaybackService.loadLastAudio(this, true)
                    startNagmedar(firstRun, upgrade, R.id.nav_audio, removeOldDevices)
                } else if (service != null && service.isInPiPMode.value == true) {
                    service.isInPiPMode.value = false
                    val startIntent = Intent(this, VideoPlayerActivity::class.java)
                    startIntent.flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
                    startActivity(startIntent)
                }
                else
                    startNagmedar(firstRun, upgrade, target, removeOldDevices)
            }
        }
        FileUtils.copyLua(applicationContext, upgrade)
        FileUtils.copyHrtfs(applicationContext, upgrade)
        if (AndroidDevices.watchDevices) this.enableStorageMonitoring()
        finish()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PROPAGATE_RESULT) {
            setResult(resultCode, data)
            finish()
        }
    }

    /**
     * S-309: the launcher, the shortcuts, the notifications and the system's search open the
     * Nağmedar interface, always: the classic one is not offered any more (on a TV too). Until its
     * first-run flow is done, that flow asks for media access and starts the medialibrary with the
     * chosen sources; afterwards the medialibrary starts here. [extras] adds what the window is
     * asked for (a search, the remote access page).
     */
    private fun startNagmedar(firstRun: Boolean, upgrade: Boolean, target: Int, removeDevices: Boolean, extras: Intent.() -> Unit = {}) {
        if (Settings.getInstance(this).getBoolean(NAGMEDAR_FIRST_RUN_DONE, false)) {
            Thread {
                AppScope.launch {
                    // workaround for a Android 9 bug
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P && !awaitAppIsForegroung()) return@launch
                    this@StartActivity.startMedialibrary(firstRun, upgrade, true, removeDevices)
                }
            }.start()
        }
        // an open Nağmedar window gets the new screen instead of a second window on top
        val intent = Intent(Intent.ACTION_VIEW)
            .setClassName(applicationContext, NAGMEDAR_SHELL_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_FIRST_RUN, firstRun)
            .putExtra(EXTRA_UPGRADE, upgrade)
        when {
            // the playback notification: the page of what plays
            this.intent?.action == NAGMEDAR_ACTION_NOW_PLAYING -> NAGMEDAR_DESTINATION_NOW_PLAYING
            // the library scan's notification: the home page
            this.intent?.action == NAGMEDAR_ACTION_LIBRARY -> "home"
            target == R.id.nav_video -> "movies"
            target == R.id.nav_audio -> "music"
            target == R.id.nav_directories -> "device"
            else -> null
        }?.let { intent.putExtra(NAGMEDAR_EXTRA_DESTINATION, it) }
        intent.extras()
        // S-409: a desktop window opens where it was last (DeX, freeform windows)
        val bounds = Settings.getInstance(this).getString(NAGMEDAR_WINDOW_BOUNDS, null)?.let(Rect::unflattenFromString)
        if (bounds != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            startActivity(intent, ActivityOptions.makeBasic().setLaunchBounds(bounds).toBundle())
        } else {
            startActivity(intent)
        }
    }

    /**
     * A file another app opens here and that is not shared through a content provider needs the
     * storage permission: Android's own prompt asks for it (VLC's explanation window is not shown).
     * True when it is given; refused, the file is not opened.
     */
    private suspend fun askStoragePermission(): Boolean {
        if (isFinishing) return false
        val answer = CompletableDeferred<Boolean>()
        storageAnswer = answer
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (runCatching { storagePrompt.launch(permissions) }.isFailure) return false
        val granted = answer.await()
        storageAnswer = null
        // the helper keeps the last answer for a while: it reads the new one now
        if (granted) Permissions.canReadStorage(applicationContext, skipCache = true)
        return granted
    }

    private fun startPlaybackFromApp(intent: Intent) = lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
        // workaround for a Android 9 bug
        // https://issuetracker.google.com/issues/113122354
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.P && !awaitAppIsForegroung()) {
            finish()
            return@launch
        }
        // Remove FLAG_ACTIVITY_FORWARD_RESULT that is incompatible with startActivityForResult
        intent.flags = Intent.FLAG_ACTIVITY_FORWARD_RESULT.inv() and intent.flags

        // If data shared with content scheme then it should be from a provider and might
        // be read without permissions.
        // If not should check for permission, and ask them if we don't have them
        if ((intent.data != null && intent.data!!.scheme == "content" && FileUtils.getUri(intent.data) != null)
            || (Permissions.canReadStorage(applicationContext) || askStoragePermission())) when {
            intent.type?.startsWith("video") == true -> try {
                startActivityForResult(intent.setClass(this@StartActivity, VideoPlayerActivity::class.java).apply { putExtra(VideoPlayerActivity.FROM_EXTERNAL, true) }, PROPAGATE_RESULT, Util.getFullScreenBundle())
                return@launch
            } catch (ex: SecurityException) {
                intent.data?.let { MediaUtils.openMediaNoUi(this@StartActivity, it) }
            }
            intent.data?.authority == getString(R.string.tv_provider_authority) -> MediaUtils.openMediaNoUiFromTvContent(this@StartActivity, intent.data)
            intent.data?.authority == "skip_to" -> PlaybackService.instance?.playIndex(intent.getIntExtra("index", 0))
            else -> withContext(Dispatchers.IO) { FileUtils.getUri(intent.data)}?.let {
                // Some media might not be properly labeled
                // As the last option, it is safer to reset the player before playing any media
                if (PlaybackService.instance?.isPlaying == true)
                    PlaybackService.instance?.playlistManager?.player?.stop()
                MediaUtils.openMediaNoUi(this@StartActivity, it)
            }
        }
        finish()
    }
}

package app.nagmedar.ui.prefs

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.getSystemService
import app.nagmedar.ui.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.resources.VLCInstance
import org.videolan.resources.VLCOptions
import org.videolan.tools.AUDIO_RESUME_PLAYBACK
import org.videolan.tools.AppScope
import org.videolan.tools.BitmapCache
import org.videolan.tools.DAV1D_THREAD_NUMBER
import org.videolan.tools.FASTPLAY_SPEED
import org.videolan.tools.KEY_AOUT
import org.videolan.tools.KEY_AUDIO_DIGITAL_OUTPUT
import org.videolan.tools.KEY_AUDIO_JUMP_DELAY
import org.videolan.tools.KEY_AUDIO_LAST_PLAYLIST
import org.videolan.tools.KEY_AUDIO_LONG_JUMP_DELAY
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_DEFAULT
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_ENABLE
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_MODE
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_PEAK_PROTECTION
import org.videolan.tools.KEY_AUDIO_REPLAY_GAIN_PREAMP
import org.videolan.tools.KEY_AUDIO_SHOW_BOOKMARK_MARKERS
import org.videolan.tools.KEY_AUDIO_SHOW_BOOkMARK_BUTTONS
import org.videolan.tools.KEY_AUDIO_SHOW_CHAPTER_BUTTONS
import org.videolan.tools.KEY_AUDIO_SHOW_TRACK_NUMBERS
import org.videolan.tools.KEY_CASTING_AUDIO_ONLY
import org.videolan.tools.KEY_CASTING_PASSTHROUGH
import org.videolan.tools.KEY_CASTING_QUALITY
import org.videolan.tools.KEY_CURRENT_AUDIO
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_ARTIST
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_THUMB
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_TITLE
import org.videolan.tools.KEY_CURRENT_MEDIA
import org.videolan.tools.KEY_CURRENT_MEDIA_RESUME
import org.videolan.tools.KEY_CUSTOM_LIBVLC_OPTIONS
import org.videolan.tools.KEY_DEBLOCKING
import org.videolan.tools.KEY_ENABLE_FRAME_SKIP
import org.videolan.tools.KEY_ENABLE_HEADSET_DETECTION
import org.videolan.tools.KEY_ENABLE_TIME_STRETCHING_AUDIO
import org.videolan.tools.KEY_ENABLE_VERBOSE_MODE
import org.videolan.tools.KEY_INCLUDE_MISSING
import org.videolan.tools.KEY_INCOGNITO
import org.videolan.tools.KEY_MEDIA_LAST_PLAYLIST
import org.videolan.tools.KEY_MEDIA_LAST_PLAYLIST_RESUME
import org.videolan.tools.KEY_NETWORK_CACHING_VALUE
import org.videolan.tools.KEY_OPENGL
import org.videolan.tools.KEY_PLAYBACK_SPEED_AUDIO_GLOBAL
import org.videolan.tools.KEY_PREFERRED_RESOLUTION
import org.videolan.tools.KEY_PREFER_SMBV1
import org.videolan.tools.KEY_QUICK_PLAY
import org.videolan.tools.KEY_QUICK_PLAY_DEFAULT
import org.videolan.tools.KEY_RESTRICT_SETTINGS
import org.videolan.tools.KEY_SAFE_MODE_PIN
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND_COLOR
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_BOLD
import org.videolan.tools.KEY_SUBTITLES_COLOR
import org.videolan.tools.KEY_SUBTITLES_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_OUTLINE
import org.videolan.tools.KEY_SUBTITLES_OUTLINE_COLOR
import org.videolan.tools.KEY_SUBTITLES_OUTLINE_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_OUTLINE_SIZE
import org.videolan.tools.KEY_SUBTITLES_SHADOW
import org.videolan.tools.KEY_SUBTITLES_SHADOW_COLOR
import org.videolan.tools.KEY_SUBTITLES_SHADOW_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_SIZE
import org.videolan.tools.KEY_SUBTITLE_TEXT_ENCODING
import org.videolan.tools.KEY_VIDEO_DOUBLE_TAP_JUMP_DELAY
import org.videolan.tools.KEY_VIDEO_JUMP_DELAY
import org.videolan.tools.KEY_VIDEO_LONG_JUMP_DELAY
import org.videolan.tools.Notices
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.tools.SLEEP_TIMER_DEFAULT_INTERVAL
import org.videolan.tools.SLEEP_TIMER_DEFAULT_RESET_INTERACTION
import org.videolan.tools.SLEEP_TIMER_DEFAULT_WAIT
import org.videolan.tools.Settings
import org.videolan.tools.VIDEO_HUD_TIMEOUT
import org.videolan.tools.VIDEO_RESUME_PLAYBACK
import org.videolan.tools.coerceInOrDefault
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.gui.helpers.MedialibraryUtils
import org.videolan.vlc.gui.helpers.restartMediaPlayer
import java.io.File
import java.io.OutputStream
import java.security.MessageDigest
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A window that "All settings" opens over its page. */
sealed interface PrefDialog {
    data class Choice(val item: PrefItem) : PrefDialog
    data class MultiChoice(val item: PrefItem) : PrefDialog
    data class Text(val item: PrefItem) : PrefDialog
    data class Number(val item: PrefItem) : PrefDialog
    data class Colour(val item: PrefItem) : PrefDialog

    /** A question VLC asked before a change it cannot take back: [onConfirm] makes it. */
    data class Confirm(val title: String, val message: String, val button: String, val onConfirm: () -> Unit) : PrefDialog

    /** The change takes effect when the app starts again: now, or later. */
    data object Restart : PrefDialog

    /** The sleep timer every playback starts with (VLC's default sleep timer). */
    data object SleepDefault : PrefDialog

    /** The permissions the app has, each with the way to change it. */
    data object Permissions : PrefDialog

    /** The parental control's PIN asked for: [onDone] when it was right (or made), [onCancel] when the user left. */
    data class Pin(val mode: PinMode, val onDone: () -> Unit, val onCancel: () -> Unit = {}) : PrefDialog
}

/** What the PIN window does: make the first PIN, check it, or change it. */
enum class PinMode { Create, Check, Modify }

/** A file asked of the system's picker: a soundfont to use, or a database written where the user wants it. */
enum class PrefFile { SoundFont, MediaDatabase, AppDatabase }

/**
 * The behaviour of VLC's preference fragments, behind "All settings": every change is written to
 * VLC's SharedPreferences under its own key, then what the fragment did on that change is done
 * here ([changed]): libVLC and the player restarted, the in-memory [Settings] fields set again,
 * the related keys corrected; the lines that did something on a click do it ([click]), with
 * Nağmedar's windows instead of VLC's dialogs and the system's picker instead of VLC's.
 * Long work (libVLC, the media library, files) runs off the main thread, in [AppScope] so that
 * leaving the page does not stop it.
 */
@Stable
class AllSettingsController(
    private val context: Context,
    val values: PreferenceValues,
    val pages: Map<String, PrefPage>,
) {
    /** Every keyed line of every page, for the dependencies and the search. */
    val items: Map<String, PrefItem> = pages.values.flatMap { it.items }.filter { it.key != null }.associateBy { it.key!! }

    var dialog by mutableStateOf<PrefDialog?>(null)


    /** Set by [click] for a file the system's picker is to ask for; the screen launches it and clears it. */
    var fileRequest by mutableStateOf<PrefFile?>(null)

    /** The subtitles' look changed: libVLC starts again when their page is left (PreferencesSubtitles). */
    private var subtitlesChanged = false

    val pinSet: Boolean get() = !values.string(KEY_SAFE_MODE_PIN, "").isNullOrBlank()

    /** Settings are behind the PIN (parental control's "Restrict settings"). */
    val restricted: Boolean get() = values.bool(KEY_RESTRICT_SETTINGS, false) && pinSet

    fun toggle(item: PrefItem, on: Boolean) {
        val key = item.key ?: return
        when {
            // turning these off asks first, as VLC did (ConfirmPreferenceChangeDialog)
            key == PLAYBACK_HISTORY && !on -> dialog = PrefDialog.Confirm(
                context.getString(R.string.playback_history_title),
                context.getString(R.string.playback_history_warning),
                context.getString(R.string.nag_prefs_turn_off),
            ) {
                values.putBoolean(key, false)
                changed(key)
            }
            key == AUDIO_RESUME_PLAYBACK && !on -> dialog = PrefDialog.Confirm(
                context.getString(R.string.audio_resume_playback_title),
                context.getString(R.string.audio_resume_playback_warning),
                context.getString(R.string.nag_prefs_turn_off),
            ) {
                values.remove(
                    KEY_AUDIO_LAST_PLAYLIST, KEY_MEDIA_LAST_PLAYLIST_RESUME, KEY_CURRENT_AUDIO_RESUME_TITLE, KEY_CURRENT_AUDIO_RESUME_ARTIST,
                    KEY_CURRENT_AUDIO_RESUME_THUMB, KEY_CURRENT_AUDIO, KEY_CURRENT_MEDIA, KEY_CURRENT_MEDIA_RESUME,
                )
                values.putBoolean(key, false)
            }
            else -> {
                values.putBoolean(key, on)
                changed(key)
            }
        }
    }

    fun choose(item: PrefItem, value: String) {
        val key = item.key ?: return
        if (key == "subtitles_presets") {
            applySubtitlePreset(value)
            return
        }
        if (!item.persistent) return
        values.putString(key, value)
        changed(key)
    }

    fun chooseSet(item: PrefItem, value: Set<String>) {
        val key = item.key ?: return
        values.putSet(key, value)
        changed(key)
    }

    fun type(item: PrefItem, text: String) {
        val key = item.key ?: return
        values.putString(key, text)
        changed(key)
    }

    fun setNumber(item: PrefItem, value: Int) {
        val key = item.key ?: return
        values.putInt(key, value.coerceIn(item.min, item.max))
        changed(key)
    }

    fun setColour(item: PrefItem, argb: Int) {
        val key = item.key ?: return
        values.putInt(key, argb)
        changed(key)
    }

    /** What a [PrefKind.Link] line does that is neither a page nor a Nağmedar section; false when it is one of those. */
    fun click(item: PrefItem): Boolean {
        when (item.key) {
            "permissions" -> dialog = PrefDialog.Permissions
            "default_sleep_timer" -> dialog = PrefDialog.SleepDefault
            "soundfont" -> fileRequest = PrefFile.SoundFont
            "modify_pin_code" -> dialog = PrefDialog.Pin(PinMode.Modify, onDone = { notice(R.string.pin_code_modified) })
            "clear_history" -> dialog = PrefDialog.Confirm(
                context.getString(R.string.clear_playback_history),
                context.getString(R.string.clear_history_message),
                context.getString(R.string.clear_history),
                ::clearHistory,
            )
            "clear_media_db" -> if (libraryBusy()) {
                notice(R.string.settings_ml_block_scan, error = true)
            } else {
                dialog = PrefDialog.Confirm(
                    context.getString(R.string.clear_media_db),
                    context.getString(R.string.clear_media_db_message),
                    context.getString(R.string.clear),
                    ::clearMediaDatabase,
                )
            }
            "clear_app_data" -> dialog = PrefDialog.Confirm(
                context.getString(R.string.clear_app_data),
                context.getString(R.string.clear_app_data_message),
                context.getString(R.string.clear),
            ) { context.getSystemService<ActivityManager>()?.clearApplicationUserData() }
            "quit_app" -> Process.killProcess(Process.myPid())
            "dump_media_db" -> if (libraryBusy()) notice(R.string.settings_ml_block_scan, error = true) else fileRequest = PrefFile.MediaDatabase
            "dump_app_db" -> fileRequest = PrefFile.AppDatabase
            else -> return false
        }
        return true
    }

    /** The file name the system's picker suggests for a database written out. */
    fun suggestedName(file: PrefFile): String = when (file) {
        PrefFile.MediaDatabase -> Medialibrary.VLC_MEDIA_DB_NAME.trimStart('/')
        PrefFile.AppDatabase -> DATABASES_ZIP
        PrefFile.SoundFont -> ""
    }

    /** The file the picker gave for [file]; nothing is read or written without one. */
    fun picked(file: PrefFile, uri: Uri?) {
        if (uri == null) return
        AppScope.launch {
            when (file) {
                PrefFile.SoundFont -> {
                    // VLC's MediaUtils.useAsSoundFont copied a path; a picked document is copied by its stream
                    val copied = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                VLCOptions.getSoundFontFile(context).outputStream().use { input.copyTo(it) }
                            } != null
                        }.getOrDefault(false)
                    }
                    if (copied) {
                        runCatching { VLCInstance.restart() }
                        dialog = PrefDialog.Restart
                    } else {
                        notice(R.string.nag_prefs_file_failed, error = true)
                    }
                }
                PrefFile.MediaDatabase, PrefFile.AppDatabase -> {
                    val written = withContext(Dispatchers.IO) {
                        runCatching {
                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                if (file == PrefFile.MediaDatabase) copyMediaDatabase(out) else zipDatabases(out)
                            } == true
                        }.getOrDefault(false)
                    }
                    notice(if (written) R.string.nag_prefs_database_saved else R.string.dump_db_failure, error = !written)
                }
            }
        }
    }

    /** A page was left: the subtitles' look applies from the next start of libVLC, as in PreferencesSubtitles.onStop. */
    fun left(pageId: String) {
        if (pageId == PreferenceCatalog.SUBTITLES && subtitlesChanged) {
            subtitlesChanged = false
            restartLibVlc(player = true)
        }
    }

    /** The default sleep timer: [minutes] null turns it off. */
    fun setSleepDefault(minutes: Int?, wait: Boolean, reset: Boolean) {
        values.prefs.edit().apply {
            putLong(SLEEP_TIMER_DEFAULT_INTERVAL, minutes?.let { it * 60_000L } ?: -1L)
            putBoolean(SLEEP_TIMER_DEFAULT_WAIT, wait)
            putBoolean(SLEEP_TIMER_DEFAULT_RESET_INTERACTION, reset)
        }.apply()
    }

    /** True when [pin] is the parental control's PIN. */
    fun pinMatches(pin: String): Boolean = sha256(pin) == values.string(KEY_SAFE_MODE_PIN, "")

    fun savePin(pin: String) {
        values.putString(KEY_SAFE_MODE_PIN, sha256(pin))
    }

    /** The app starts again: its window opens anew, then this process ends (VLC's restart dialog only ended it). */
    fun restartApp() {
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let { intent ->
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) }
        }
        Process.killProcess(Process.myPid())
    }

    /** What the classic fragment did after [key] changed (their onSharedPreferenceChanged and onPreferenceTreeClick). */
    private fun changed(key: String) {
        when (key) {
            // PreferencesFragment
            PLAYBACK_HISTORY -> if (values.bool(key, true)) {
                values.putBoolean(AUDIO_RESUME_PLAYBACK, true)
                values.putBoolean(VIDEO_RESUME_PLAYBACK, true)
            }
            VIDEO_RESUME_PLAYBACK -> values.remove(KEY_MEDIA_LAST_PLAYLIST, KEY_MEDIA_LAST_PLAYLIST_RESUME, KEY_CURRENT_MEDIA_RESUME, KEY_CURRENT_MEDIA)
            // PreferencesUi
            KEY_INCLUDE_MISSING -> Settings.includeMissing = values.bool(key, true)
            KEY_INCOGNITO -> Settings.incognitoMode = values.bool(key, false)
            // PreferencesVideo
            KEY_PREFERRED_RESOLUTION -> restartLibVlc(player = true)
            // PreferencesVideoControls
            VIDEO_HUD_TIMEOUT -> Settings.videoHudDelay = values.int(key, 4).coerceInOrDefault(1, 15, -1)
            KEY_VIDEO_JUMP_DELAY -> Settings.videoJumpDelay = values.int(key, 10)
            KEY_VIDEO_LONG_JUMP_DELAY -> Settings.videoLongJumpDelay = values.int(key, 20)
            KEY_VIDEO_DOUBLE_TAP_JUMP_DELAY -> Settings.videoDoubleTapJumpDelay = values.int(key, 10)
            FASTPLAY_SPEED -> Settings.fastplaySpeed = values.int(key, 20) / 10f
            // PreferencesSubtitles
            in subtitleLook -> subtitlesChanged = true
            // PreferencesAudio
            KEY_ENABLE_HEADSET_DETECTION -> {
                val event = PlaybackService.headSetDetection
                if (event.hasObservers()) event.value = values.bool(key, true)
            }
            KEY_AUDIO_REPLAY_GAIN_ENABLE, KEY_AUDIO_REPLAY_GAIN_MODE, KEY_AUDIO_REPLAY_GAIN_PEAK_PROTECTION -> restartLibVlc(player = true)
            KEY_AUDIO_REPLAY_GAIN_DEFAULT, KEY_AUDIO_REPLAY_GAIN_PREAMP -> {
                val fallback = if (key == KEY_AUDIO_REPLAY_GAIN_DEFAULT) "-7.0" else "0.0"
                val text = values.string(key, fallback).orEmpty()
                // the gain is kept as libVLC reads it: "-7.0", with a point
                val formatted = text.replace(',', '.').toDoubleOrNull()?.let {
                    DecimalFormat("###0.0###", DecimalFormatSymbols(Locale.ENGLISH)).format(it)
                } ?: fallback
                if (formatted != text) values.putString(key, formatted)
                restartLibVlc(player = true)
            }
            // PreferencesAudioControls
            KEY_AUDIO_JUMP_DELAY -> Settings.audioJumpDelay = values.int(key, 10)
            KEY_AUDIO_LONG_JUMP_DELAY -> Settings.audioLongJumpDelay = values.int(key, 20)
            KEY_AUDIO_SHOW_TRACK_NUMBERS -> Settings.audioShowTrackNumbers.postValue(values.bool(key, false))
            KEY_AUDIO_SHOW_BOOkMARK_BUTTONS -> if (!values.bool(key, true)) values.putBoolean(KEY_AUDIO_SHOW_BOOKMARK_MARKERS, false)
            // PreferencesCasting
            KEY_CASTING_PASSTHROUGH, KEY_CASTING_QUALITY, KEY_CASTING_AUDIO_ONLY -> restartLibVlc(player = true)
            // PreferencesAdvanced
            KEY_AOUT -> {
                if (values.string(KEY_AOUT, "0") == "2") values.putBoolean(KEY_AUDIO_DIGITAL_OUTPUT, false)
                restartLibVlc(player = true)
            }
            NETWORK_CACHING -> {
                val typed = values.string(key, "0").orEmpty().trim()
                val value = typed.toIntOrNull()?.coerceIn(0, MAX_NETWORK_CACHING) ?: 0
                values.putInt(KEY_NETWORK_CACHING_VALUE, value)
                if (typed != value.toString()) {
                    values.putString(key, value.toString())
                    notice(R.string.network_caching_popup, error = true)
                }
                restartLibVlc(player = true)
            }
            KEY_CUSTOM_LIBVLC_OPTIONS -> AppScope.launch {
                try {
                    VLCInstance.restart()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "libVLC refused the custom options", e)
                    notice(R.string.custom_libvlc_options_invalid, error = true)
                    values.putString(key, "")
                    runCatching { VLCInstance.restart() }
                }
                restartMediaPlayer()
            }
            DAV1D_THREAD_NUMBER -> {
                val threads = values.string(key, "").orEmpty()
                if (threads.isNotEmpty() && (threads.toIntOrNull() ?: 0) < 1) {
                    notice(R.string.dav1d_thread_number_invalid, error = true)
                    values.putString(key, "")
                }
            }
            KEY_OPENGL, KEY_DEBLOCKING, KEY_ENABLE_FRAME_SKIP, KEY_ENABLE_TIME_STRETCHING_AUDIO, KEY_ENABLE_VERBOSE_MODE -> restartLibVlc(player = true)
            KEY_PREFER_SMBV1 -> {
                restartLibVlc(player = false)
                dialog = PrefDialog.Restart
            }
            KEY_QUICK_PLAY -> if (!values.bool(key, false)) values.putBoolean(KEY_QUICK_PLAY_DEFAULT, false)
            // PreferencesAndroidAuto: the lists are kept as Ints too, under "<key>_val"
            AUTO_QUEUE_POSITION, AUTO_QUEUE_FORMAT -> values.string(key, null)?.toIntOrNull()?.let { values.putInt("${key}_val", it) }
            KEY_PLAYBACK_SPEED_AUDIO_GLOBAL -> PlaybackService.instance?.let { service ->
                service.playlistManager.getCurrentMedia()?.let { service.playlistManager.restoreSpeed(it) }
            }
        }
        if (key in audioControls) Settings.onAudioControlsChanged()
        if (key in androidAuto) PlaybackService.updateState()
    }

    /** VLC's subtitle presets (PreferencesSubtitles): every look back to its default, then the preset's changes. */
    private fun applySubtitlePreset(preset: String) {
        values.prefs.edit().apply {
            putString(KEY_SUBTITLES_SIZE, "16")
            putBoolean(KEY_SUBTITLES_BOLD, false)
            putInt(KEY_SUBTITLES_COLOR, WHITE)
            putInt(KEY_SUBTITLES_COLOR_OPACITY, 255)
            putBoolean(KEY_SUBTITLES_BACKGROUND, false)
            putInt(KEY_SUBTITLES_BACKGROUND_COLOR, BLACK)
            putInt(KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, 255)
            putBoolean(KEY_SUBTITLES_SHADOW, true)
            putInt(KEY_SUBTITLES_SHADOW_COLOR, BLACK)
            putInt(KEY_SUBTITLES_SHADOW_COLOR_OPACITY, 128)
            putBoolean(KEY_SUBTITLES_OUTLINE, true)
            putInt(KEY_SUBTITLES_OUTLINE_COLOR, BLACK)
            putInt(KEY_SUBTITLES_OUTLINE_COLOR_OPACITY, 255)
            when (preset) {
                "1" -> putString(KEY_SUBTITLES_SIZE, "13")
                "2" -> {
                    putString(KEY_SUBTITLES_SIZE, "10")
                    putBoolean(KEY_SUBTITLES_BACKGROUND, true)
                    putBoolean(KEY_SUBTITLES_SHADOW, false)
                    putBoolean(KEY_SUBTITLES_OUTLINE, false)
                }
                "3" -> {
                    putBoolean(KEY_SUBTITLES_BACKGROUND, true)
                    putInt(KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, 128)
                    putBoolean(KEY_SUBTITLES_SHADOW, false)
                }
                "4" -> putInt(KEY_SUBTITLES_COLOR, YELLOW)
                "5" -> {
                    putInt(KEY_SUBTITLES_COLOR, YELLOW)
                    putBoolean(KEY_SUBTITLES_BACKGROUND, true)
                    putInt(KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, 128)
                    putBoolean(KEY_SUBTITLES_SHADOW, false)
                }
            }
        }.apply()
        subtitlesChanged = true
    }

    /** A short notice in the window's notice area (an invalid value put right, a database saved). */
    private fun notice(@StringRes text: Int, error: Boolean = false) = Notices.show(context, text, long = error, error = error)

    private fun libraryBusy() = runCatching { Medialibrary.getInstance().isWorking }.getOrDefault(false)

    private fun clearHistory() {
        AppScope.launch(Dispatchers.IO) { runCatching { Medialibrary.getInstance().clearHistory(Medialibrary.HISTORY_TYPE_GLOBAL) } }
        values.remove(
            KEY_AUDIO_LAST_PLAYLIST, KEY_MEDIA_LAST_PLAYLIST, KEY_MEDIA_LAST_PLAYLIST_RESUME, KEY_CURRENT_AUDIO, KEY_CURRENT_MEDIA,
            KEY_CURRENT_MEDIA_RESUME, KEY_CURRENT_AUDIO_RESUME_TITLE, KEY_CURRENT_AUDIO_RESUME_ARTIST, KEY_CURRENT_AUDIO_RESUME_THUMB,
        )
    }

    /** The media library emptied and its folders scanned again; its thumbnails, in the app's own folder, go too. The media files stay. */
    private fun clearMediaDatabase() {
        if (libraryBusy()) {
            notice(R.string.settings_ml_block_scan, error = true)
            return
        }
        AppScope.launch {
            val roots = withContext(Dispatchers.IO) {
                val library = Medialibrary.getInstance()
                val folders = library.foldersList.orEmpty()
                library.clearDatabase(false)
                context.getExternalFilesDir(null)?.let { File(it.absolutePath + Medialibrary.MEDIALIB_FOLDER_NAME) }
                    ?.listFiles()?.filter { it.isFile }?.forEach { it.delete() }
                folders
            }
            BitmapCache.clear()
            roots.forEach { MedialibraryUtils.addDir(it.removePrefix("file://"), context) }
        }
    }

    private fun copyMediaDatabase(out: OutputStream): Boolean {
        val db = File(context.getDir("db", Context.MODE_PRIVATE).toString() + Medialibrary.VLC_MEDIA_DB_NAME)
        if (!db.isFile) return false
        db.inputStream().use { it.copyTo(out) }
        return true
    }

    /** The app's Room databases in one ZIP, as VLC's "Dump app database" made it. */
    private fun zipDatabases(out: OutputStream): Boolean {
        val dir = File(context.getDir("db", Context.MODE_PRIVATE).parent!! + "/databases")
        val files = dir.listFiles()?.filter { it.isFile }.orEmpty()
        if (files.isEmpty()) return false
        ZipOutputStream(out).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(file.name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return true
    }

    private fun restartLibVlc(player: Boolean) {
        AppScope.launch {
            try {
                VLCInstance.restart()
            } catch (e: IllegalStateException) {
                Log.w(TAG, "libVLC did not start again", e)
            }
            if (player) restartMediaPlayer()
        }
    }

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString(":") { "%02x".format(it) }

    private companion object {
        const val TAG = "Nagmedar/AllSettings"
        const val NETWORK_CACHING = "network_caching"
        const val MAX_NETWORK_CACHING = 60_000
        const val AUTO_QUEUE_POSITION = "android_auto_queue_info_pos"
        const val AUTO_QUEUE_FORMAT = "android_auto_queue_format"
        const val DATABASES_ZIP = "vlc_database.zip"
        const val WHITE = 0xFFFFFFFF.toInt()
        const val BLACK = 0xFF000000.toInt()
        const val YELLOW = 0xFFFFFF00.toInt()

        val subtitleLook = setOf(
            KEY_SUBTITLES_SIZE, KEY_SUBTITLES_BOLD, KEY_SUBTITLE_TEXT_ENCODING, KEY_SUBTITLES_COLOR, KEY_SUBTITLES_COLOR_OPACITY,
            KEY_SUBTITLES_BACKGROUND_COLOR, KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, KEY_SUBTITLES_BACKGROUND, KEY_SUBTITLES_OUTLINE,
            KEY_SUBTITLES_OUTLINE_SIZE, KEY_SUBTITLES_OUTLINE_COLOR, KEY_SUBTITLES_OUTLINE_COLOR_OPACITY, KEY_SUBTITLES_SHADOW,
            KEY_SUBTITLES_SHADOW_COLOR, KEY_SUBTITLES_SHADOW_COLOR_OPACITY,
        )

        val audioControls = setOf(
            KEY_AUDIO_JUMP_DELAY, KEY_AUDIO_LONG_JUMP_DELAY, "audio_force_shuffle", "blurred_cover_background", KEY_AUDIO_SHOW_TRACK_NUMBERS,
            KEY_AUDIO_SHOW_CHAPTER_BUTTONS, KEY_AUDIO_SHOW_BOOkMARK_BUTTONS, KEY_AUDIO_SHOW_BOOKMARK_MARKERS,
        )

        val androidAuto = setOf(
            "android_auto_title_scale_val", "android_auto_subtitle_scale_val", AUTO_QUEUE_POSITION, AUTO_QUEUE_FORMAT,
            KEY_PLAYBACK_SPEED_AUDIO_GLOBAL, "enable_android_auto_speed_buttons", "enable_android_auto_seek_buttons",
        )
    }
}

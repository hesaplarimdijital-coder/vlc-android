package app.nagmedar.ui.prefs

import android.content.Context
import android.content.res.Resources
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.os.ConfigurationCompat
import app.nagmedar.ui.R
import org.videolan.medialibrary.Tools
import org.videolan.resources.AndroidDevices
import org.videolan.tools.FASTPLAY_SPEED
import org.videolan.tools.KEY_ANDROID_AUTO_QUEUE_INFO_POS_VAL
import org.videolan.tools.KEY_AOUT
import org.videolan.tools.KEY_AUDIO_DIGITAL_OUTPUT
import org.videolan.tools.KEY_AUDIO_PREFERRED_LANGUAGE
import org.videolan.tools.KEY_SUBTITLE_PREFERRED_LANGUAGE
import org.videolan.tools.LocaleUtils
import org.videolan.tools.LocaleUtils.getLocales
import org.videolan.tools.SLEEP_TIMER_DEFAULT_INTERVAL
import org.videolan.tools.SLEEP_TIMER_DEFAULT_RESET_INTERACTION
import org.videolan.tools.SLEEP_TIMER_DEFAULT_WAIT
import org.videolan.tools.VIDEO_HUD_TIMEOUT
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.gui.preferences.PreferenceVisibilityManager
import org.videolan.vlc.isVLC4
import org.videolan.vlc.util.LocaleUtil
import java.text.NumberFormat
import java.util.Locale

/** Where a line of VLC's pages leads in Nağmedar's own settings, which already have it. */
enum class NagmedarLink {
    /** Settings › Library: each section's folders (VLC's "Media library folders"). */
    Library,

    /** Settings › Appearance: Nağmedar's theme (VLC's day and night choice). */
    Appearance,

    /** The equaliser page (S-906). */
    Equalizer,

    /** About, with the licences (S-209). */
    About,

    /** Settings › Remote access (S-1304). */
    RemoteAccess,

    /** Settings › Backup (S-1502): VLC's export and restore of its settings, and more. */
    Backup,
}

/**
 * What of VLC's preference XML "All settings" shows, and how: the pages it opens (the dev,
 * optional-feature, extension and widget pages are not among them), the lines that only VLC's own
 * screens read (left out), those Nağmedar's settings already have ([links]), and the rules VLC's
 * fragments applied in code: the visibility of a line, its entries, its summary.
 */
object PreferenceCatalog {

    private const val TAG = "Nagmedar/Preferences"

    const val ROOT = "preferences"
    const val VIDEO = "video_category"
    const val VIDEO_CONTROLS = "video_controls"
    const val SUBTITLES = "subtitles_category"
    const val AUDIO = "audio_category"
    const val AUDIO_CONTROLS = "audio_controls"
    const val PARENTAL = "parental_control"
    const val ANDROID_AUTO = "android_auto_category"

    /** The pages, by the key of the line that opens each. */
    private val pages = linkedMapOf(
        ROOT to R.xml.preferences,
        "ui_category" to R.xml.preferences_ui,
        VIDEO to R.xml.preferences_video,
        VIDEO_CONTROLS to R.xml.preferences_video_controls,
        SUBTITLES to R.xml.preferences_subtitles,
        AUDIO to R.xml.preferences_audio,
        AUDIO_CONTROLS to R.xml.preferences_audio_controls,
        "casting_category" to R.xml.preferences_casting,
        PARENTAL to R.xml.preferences_parental_control,
        ANDROID_AUTO to R.xml.preferences_android_auto,
        "adv_category" to R.xml.preferences_adv,
    )

    /** The lines that open a part of Nağmedar's settings instead. */
    val links = mapOf(
        "directories" to NagmedarLink.Library,
        "app_theme" to NagmedarLink.Appearance,
        "equalizer" to NagmedarLink.Equalizer,
        "licenses" to NagmedarLink.About,
        "remote_access_category" to NagmedarLink.RemoteAccess,
        "export_settings" to NagmedarLink.Backup,
        "restore_settings" to NagmedarLink.Backup,
    )

    /** Where a key asked for from outside leads when no page here has it (VLC's remote access page is Nağmedar's). */
    fun linkFor(key: String): NagmedarLink? = links[key] ?: if (key.startsWith("remote_access") || key == "enable_remote_access") NagmedarLink.RemoteAccess else null

    /**
     * Lines that only the classic interface's own screens read (its lists and grids, its TV mode,
     * its language, its way into Nağmedar) or that open developer tools of VLC's: not shown. Their
     * keys stay as they are. The second display's choice is gone too: the player stays in its own
     * window and the system mirrors it (VideoPlayerActivity); so is VLC's own popup window for
     * picture in picture, which is always Android's. Safe mode is not offered: only the classic
     * interface's screens kept files and lists behind the PIN, and a TV keeps it.
     */
    private val dropped = setOf(
        "nagmedar_shell", "tv_ui", "set_locale", "list_title_ellipsize", "show_headers", "show_video_thumbnails",
        "media_seen", "audio_resume_card", "optional_features", "debug_logs", "nightly_install", "show_update",
        "secondary_display_category", "secondary_display_category_summary", "enable_clone_mode", "popup_force_legacy",
        "safe_mode",
    )

    /**
     * Every page, read from the APK; the video and audio pages lead to their controls' pages, as the
     * player's panels did. A page that cannot be read is left out (the first page then is empty).
     */
    fun load(res: Resources): Map<String, PrefPage> {
        val read = LinkedHashMap<String, PrefPage>()
        pages.forEach { (id, xml) ->
            val title = when (id) {
                VIDEO_CONTROLS -> res.getString(R.string.player_controls)
                AUDIO_CONTROLS -> res.getString(R.string.controls_prefs_category)
                else -> null
            }
            runCatching { PreferenceXml.parse(res, xml, id, title) }
                .onSuccess { read[id] = it }
                .onFailure { Log.w(TAG, "Could not read the preference page $id", it) }
        }
        if (ROOT !in read) read[ROOT] = PrefPage(ROOT, res.getString(R.string.preferences), emptyList())
        read[VIDEO]?.let { page -> read[VIDEO] = page.withLink(VIDEO_CONTROLS, res.getString(R.string.player_controls)) }
        read[AUDIO]?.let { page -> read[AUDIO] = page.withLink(AUDIO_CONTROLS, res.getString(R.string.controls_prefs_category)) }
        return read
    }

    private fun PrefPage.withLink(key: String, title: String) =
        copy(sections = sections + PrefSection(null, null, listOf(PrefItem(PrefKind.Link, key, title))))

    /** True when the line [key] opens another page of these. */
    fun isPage(key: String?): Boolean = key != null && key != ROOT && key in pages

    /** Whether [item] is shown, as VLC's PreferenceVisibilityManager and its fragments decided (not on a TV). */
    fun visible(item: PrefItem, values: PreferenceValues): Boolean {
        val key = item.key ?: return item.shown
        if (key in dropped) return false
        return when (key) {
            // the subtitles' colours show with their switch (PreferencesSubtitles)
            "subtitles_background_color", "subtitles_background_color_opacity" -> values.bool("subtitles_background", false)
            "subtitles_shadow_color", "subtitles_shadow_color_opacity" -> values.bool("subtitles_shadow", true)
            "subtitles_outline_color", "subtitles_outline_color_opacity" -> values.bool("subtitles_outline", true)
            // its size makes libVLC draw badly, VLC hides it
            "subtitles_outline_size" -> false
            KEY_AUDIO_DIGITAL_OUTPUT -> values.string(KEY_AOUT, "0") != "2"
            // the video controls (PreferencesVideoControls)
            "popup_keepscreen" -> !AndroidDevices.isAndroidTv && Build.VERSION.SDK_INT < Build.VERSION_CODES.O
            "audio_boost", "enable_double_tap_seek", "enable_double_tap_play", "enable_scale_gesture", "enable_swipe_seek",
            "enable_fastplay", FASTPLAY_SPEED, "screenshot_mode", "lock_use_sensor" -> !AndroidDevices.isAndroidTv
            "enable_volume_gesture", "enable_brightness_gesture" -> AndroidDevices.hasTsp
            else -> item.shown && PreferenceVisibilityManager.isPreferenceVisible(key, values.prefs, false)
        }
    }

    /** Whether [item] can be changed: its dependency's state (`android:dependency`), and VLC's own rules. */
    fun enabled(item: PrefItem, values: PreferenceValues, all: Map<String, PrefItem>, context: Context): Boolean {
        when (item.key) {
            "android_auto_queue_format" -> if (values.int(KEY_ANDROID_AUTO_QUEUE_INFO_POS_VAL, 3) <= 0) return false
            "audio_boost", "enable_volume_gesture" -> if (volumeFixed(context)) return false
        }
        val dependency = item.dependency?.let { all[it] } ?: return true
        val on = values.checked(dependency)
        val blocks = if (dependency.disableDependentsState) on else !on
        return !blocks && enabled(dependency, values, all, context)
    }

    /** A device whose volume cannot be changed: VLC's audio boost and volume gesture do nothing there. */
    fun volumeFixed(context: Context): Boolean = context.getSystemService<AudioManager>()?.isVolumeFixed == true

    /** The entries of [item] VLC's fragments filled in code: the languages, the complete audio outputs of libVLC 4. */
    fun entries(item: PrefItem, context: Context): Pair<List<String>, List<String>> = when (item.key) {
        KEY_SUBTITLE_PREFERRED_LANGUAGE, KEY_AUDIO_PREFERRED_LANGUAGE -> {
            val locales = LocaleUtils.getLocalesUsedInProject(
                BuildConfig.TRANSLATION_ARRAY,
                context.getString(R.string.no_track_preference),
                context.getLocales(),
            )
            locales.localeEntries.toList() to locales.localeEntryValues.toList()
        }
        KEY_AOUT -> if (isVLC4() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.resources.getStringArray(R.array.aouts_complete).toList() to context.resources.getStringArray(R.array.aouts_complete_values).toList()
        } else {
            item.entries to item.entryValues
        }
        else -> item.entries to item.entryValues
    }

    /** The summary of [item] under its title, with what VLC's fragments put there in code; null for none. */
    fun summary(item: PrefItem, values: PreferenceValues, context: Context): String? {
        val res = context.resources
        val key = item.key
        val summary = when (key) {
            KEY_SUBTITLE_PREFERRED_LANGUAGE -> values.string(KEY_SUBTITLE_PREFERRED_LANGUAGE, null).let { lang ->
                if (lang.isNullOrEmpty()) res.getString(R.string.no_track_preference) else res.getString(R.string.track_preference, lang)
            }
            KEY_AUDIO_PREFERRED_LANGUAGE -> values.string(KEY_AUDIO_PREFERRED_LANGUAGE, null).let { lang ->
                if (lang.isNullOrEmpty()) res.getString(R.string.no_track_preference) else res.getString(R.string.track_preference, LocaleUtil.getLocaleName(lang))
            }
            KEY_AUDIO_DIGITAL_OUTPUT -> res.getString(
                if (values.bool(KEY_AUDIO_DIGITAL_OUTPUT, false)) R.string.audio_digital_output_enabled else R.string.audio_digital_output_disabled,
            )
            VIDEO_HUD_TIMEOUT -> values.number(item).let { seconds ->
                if (seconds > 15) res.getString(R.string.timeout_infinite) else res.getString(R.string.video_hud_timeout_summary, seconds.toString())
            }
            FASTPLAY_SPEED -> res.getString(R.string.nag_prefs_speed, speed(values.number(item), res))
            "default_sleep_timer" -> sleepSummary(values, context)
            else -> when (item.kind) {
                PrefKind.Number -> res.getString(R.string.jump_delay_summary, values.number(item).toString())
                PrefKind.Choice -> item.summary?.let { text ->
                    if ("%s" !in text) text else text.replace("%s", choiceLabel(item, values, context).orEmpty())
                }
                PrefKind.Text -> if (item.simpleSummary) values.text(item).ifEmpty { res.getString(R.string.nag_prefs_not_set) } else item.summary
                PrefKind.Slider -> item.summary?.replace("%s", values.number(item).toString())
                else -> item.summary
            }
        } ?: return null
        // VLC said so when the device's volume is fixed (PreferencesVideoControls)
        if ((key == "audio_boost" || key == "enable_volume_gesture") && volumeFixed(context)) return res.getString(R.string.system_volume_disabled, summary)
        return summary.takeIf { it.isNotBlank() }
    }

    /** The entry of [item]'s current value, as a ListPreference shows it. */
    fun choiceLabel(item: PrefItem, values: PreferenceValues, context: Context): String? {
        val (entries, entryValues) = entries(item, context)
        val index = entryValues.indexOf(values.text(item))
        return entries.getOrNull(index)
    }

    /** The tap-and-hold speed VLC keeps in tenths: 20 is "2×", 15 is "1,5×" in Turkish. */
    private fun speed(tenths: Int, res: Resources): String {
        val locale = ConfigurationCompat.getLocales(res.configuration)[0] ?: Locale.getDefault()
        return NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(tenths / 10.0)
    }

    private fun sleepSummary(values: PreferenceValues, context: Context): String {
        val interval = values.long(SLEEP_TIMER_DEFAULT_INTERVAL, -1L)
        if (interval == -1L) return context.getString(R.string.disabled)
        fun yesNo(on: Boolean) = context.getString(if (on) R.string.nag_choice_yes else R.string.nag_choice_no)
        return context.getString(
            R.string.default_sleep_timer_summary,
            Tools.millisToString(interval),
            yesNo(values.bool(SLEEP_TIMER_DEFAULT_WAIT, false)),
            yesNo(values.bool(SLEEP_TIMER_DEFAULT_RESET_INTERACTION, false)),
        )
    }
}

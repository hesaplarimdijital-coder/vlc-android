package app.nagmedar.ui.visual

import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import app.nagmedar.ui.R

/**
 * S-611: what a visualiser draws. projectM is not in this build: libprojectM needs OpenGL ES and
 * its presets, and libVLC's projectM, goom and visual modules are left out of the published AAR
 * (the libvlcjni build disables them), so the three drawn here are the visualisers there are.
 */
enum class VisualizerKind(@StringRes val label: Int) {
    Spectrum(R.string.nag_vis_spectrum),
    Vu(R.string.nag_vis_vu),
    Scope(R.string.nag_vis_scope),
}

/** S-611: how often a visualiser draws at most; each frame is a read, an analysis and a redraw. */
enum class VisualizerFps(val fps: Int) { Fps15(15), Fps30(30), Fps60(60) }

/**
 * S-611: the visualisers' settings: shown with music (the now playing pane, the phone's player),
 * shown in the video player (over audio alone, or beside the picture), the kind, the frame rate
 * cap, and whether they run while Battery Saver is on (they do not, unless asked).
 */
@Immutable
data class VisualizerSettings(
    val music: Boolean = false,
    val video: Boolean = false,
    val kind: VisualizerKind = VisualizerKind.Spectrum,
    val fps: VisualizerFps = VisualizerFps.Fps30,
    val onBatterySaver: Boolean = false,
) {
    fun write(editor: SharedPreferences.Editor): SharedPreferences.Editor = editor
        .putBoolean(KEY_MUSIC, music)
        .putBoolean(KEY_VIDEO, video)
        .putString(KEY_KIND, kind.name)
        .putString(KEY_FPS, fps.name)
        .putBoolean(KEY_BATTERY_SAVER, onBatterySaver)

    companion object {
        const val KEY_MUSIC = "nagmedar_visualizer_music"
        const val KEY_VIDEO = "nagmedar_visualizer_video"
        const val KEY_KIND = "nagmedar_visualizer_kind"
        const val KEY_FPS = "nagmedar_visualizer_fps"
        const val KEY_BATTERY_SAVER = "nagmedar_visualizer_battery_saver"
        val keys = setOf(KEY_MUSIC, KEY_VIDEO, KEY_KIND, KEY_FPS, KEY_BATTERY_SAVER)

        fun read(prefs: SharedPreferences) = VisualizerSettings(
            music = prefs.getBoolean(KEY_MUSIC, false),
            video = prefs.getBoolean(KEY_VIDEO, false),
            kind = VisualizerKind.entries.firstOrNull { it.name == prefs.getString(KEY_KIND, null) } ?: VisualizerKind.Spectrum,
            fps = VisualizerFps.entries.firstOrNull { it.name == prefs.getString(KEY_FPS, null) } ?: VisualizerFps.Fps30,
            onBatterySaver = prefs.getBoolean(KEY_BATTERY_SAVER, false),
        )
    }
}

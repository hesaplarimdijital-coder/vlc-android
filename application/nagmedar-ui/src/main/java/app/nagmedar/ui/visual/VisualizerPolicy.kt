package app.nagmedar.ui.visual

import android.content.Context
import android.os.BatteryManager
import androidx.core.content.getSystemService

/** S-611: why a visualiser does not draw now. */
enum class VisualizerPause {
    /** Its pane is not on the screen, or the app is behind another. */
    Hidden,

    /** The screen is off. */
    ScreenOff,

    /** Battery Saver is on, and the settings do not ask for it to run all the same. */
    BatterySaver,

    /** Nothing plays: what was drawn falls to rest, then the drawing stops. */
    NotPlaying,
}

/** S-611: what a visualiser's pane knows of the device and the playback. */
data class VisualizerConditions(
    val shown: Boolean,
    val screenOn: Boolean,
    val powerSave: Boolean,
    val playing: Boolean,
)

/** S-611: whether a visualiser draws, and how often. */
sealed interface VisualizerRun {
    data class Running(val fps: Int) : VisualizerRun

    data class Paused(val reason: VisualizerPause) : VisualizerRun
}

/**
 * S-611: the battery rules. A visualiser reads and draws only while its pane is on a lit screen in
 * front, music plays, and Battery Saver is off (or the settings let it run under it), at most at
 * the frame rate the settings cap it to. The engine's tap and the system's visualiser are open only
 * while it runs.
 */
object VisualizerPolicy {

    fun decide(settings: VisualizerSettings, conditions: VisualizerConditions): VisualizerRun = when {
        !conditions.shown -> VisualizerRun.Paused(VisualizerPause.Hidden)
        !conditions.screenOn -> VisualizerRun.Paused(VisualizerPause.ScreenOff)
        conditions.powerSave && !settings.onBatterySaver -> VisualizerRun.Paused(VisualizerPause.BatterySaver)
        !conditions.playing -> VisualizerRun.Paused(VisualizerPause.NotPlaying)
        else -> VisualizerRun.Running(settings.fps.fps)
    }
}

/**
 * S-611: what a visualiser costs the battery, as an estimate to show beside the choice: a fixed
 * part (the source open, the screen kept drawing a region) and a part per frame (the read, the
 * FFT, the redraw), in milliwatts. The figures are a model from a mid-range device's power
 * profile (a full-screen redraw at 60 fps about 250 mW, the 2048-point FFT and its read under
 * 10 mW); the device's battery gives the share per hour.
 */
object VisualizerCost {

    /** The battery a device is taken to have when it does not say: a phone's, 5000 mAh at 3.85 V. */
    const val DEFAULT_BATTERY_WH = 19.25

    /** The battery's nominal voltage, for its capacity in watt-hours. */
    private const val NOMINAL_VOLTS = 3.85

    fun milliwatts(kind: VisualizerKind, fps: Int): Double = when (kind) {
        VisualizerKind.Spectrum -> 40.0 + 4.0 * fps
        VisualizerKind.Scope -> 35.0 + 3.5 * fps
        VisualizerKind.Vu -> 25.0 + 2.0 * fps
    }

    /** Percent of a full battery of [batteryWh] used in an hour. */
    fun percentPerHour(kind: VisualizerKind, fps: Int, batteryWh: Double): Double =
        milliwatts(kind, fps) / 1000.0 / batteryWh.coerceAtLeast(1.0) * 100.0

    /** The device's battery in watt-hours, from its charge counter and level; the default when it does not say. */
    fun batteryWh(context: Context): Double {
        val battery = context.getSystemService<BatteryManager>() ?: return DEFAULT_BATTERY_WH
        val microAmpHours = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return fullWh(microAmpHours, percent) ?: DEFAULT_BATTERY_WH
    }

    /** A full battery's watt-hours from its charge now (µAh) at [percent]; null when they do not tell. */
    fun fullWh(microAmpHours: Int, percent: Int): Double? {
        if (microAmpHours <= 0 || microAmpHours == Int.MIN_VALUE || percent !in 5..100) return null
        val full = microAmpHours / 1e6 * 100.0 / percent * NOMINAL_VOLTS
        return full.takeIf { it in 2.0..200.0 }
    }
}

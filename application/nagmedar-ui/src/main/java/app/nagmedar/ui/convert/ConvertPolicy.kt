package app.nagmedar.ui.convert

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/** How hot the device is, as PowerManager reports it. */
enum class Heat { None, Light, Moderate, Severe, Critical, Emergency, Shutdown }

/** What the converter watches while it works: the battery and the heat. */
data class DeviceState(val batteryPercent: Int, val charging: Boolean, val heat: Heat) {
    companion object {
        /** Mains power, a full battery, cool: nothing to wait for. */
        val FINE = DeviceState(100, charging = true, heat = Heat.None)
    }
}

/** What the converter does next. */
sealed interface ConvertSteer {
    data object Go : ConvertSteer

    /** Carry on, waiting [waitMs] now and then: a warm device gets about half the work. */
    data class Slow(val waitMs: Long) : ConvertSteer

    /** Wait [waitMs] and ask again; [reason] is [ConvertReason.Battery] or [ConvertReason.Thermal]. */
    data class Pause(val reason: ConvertReason, val waitMs: Long) : ConvertSteer

    /** Give up: the files left fail with [reason]; the user starts them again when the device is ready. */
    data class Stop(val reason: ConvertReason) : ConvertSteer
}

/**
 * S-1010: the converter's rules, the remux queue's (S-1206): on battery, it pauses under
 * [PAUSE_BELOW] percent and goes on from [RESUME_ABOVE] or when charging; under [STOP_BELOW] it
 * stops, so a long batch never drains the device. A moderately hot device slows it down, a
 * severely hot one pauses it until it cools; critical heat stops it.
 */
object ConvertPolicy {
    const val PAUSE_BELOW = 15
    const val RESUME_ABOVE = 20
    const val STOP_BELOW = 5

    /** How long a paused job waits before looking again. */
    const val PAUSE_WAIT_MS = 5_000L

    /** A slowed job's wait after each [CHECK_MS] of work: about half the work. */
    const val SLOW_WAIT_MS = 500L

    /** How often the device is looked at while a file is being converted. */
    const val CHECK_MS = 500L

    /** [paused] tells whether the job already waits: the battery must then climb to [RESUME_ABOVE]. */
    fun decide(state: DeviceState, paused: Boolean): ConvertSteer {
        if (!state.charging) {
            if (state.batteryPercent < STOP_BELOW) return ConvertSteer.Stop(ConvertReason.Battery)
            val threshold = if (paused) RESUME_ABOVE else PAUSE_BELOW
            if (state.batteryPercent < threshold) return ConvertSteer.Pause(ConvertReason.Battery, PAUSE_WAIT_MS)
        }
        return when (state.heat) {
            Heat.None, Heat.Light -> ConvertSteer.Go
            Heat.Moderate -> ConvertSteer.Slow(SLOW_WAIT_MS)
            Heat.Severe -> ConvertSteer.Pause(ConvertReason.Thermal, PAUSE_WAIT_MS)
            Heat.Critical, Heat.Emergency, Heat.Shutdown -> ConvertSteer.Stop(ConvertReason.Thermal)
        }
    }
}

/** The device's state as Android reports it: the sticky battery broadcast and the thermal status. */
class AndroidDeviceState(private val context: Context) : () -> DeviceState {
    private val power: PowerManager? = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    override fun invoke(): DeviceState {
        val battery = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 100
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val charging = battery == null || plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val heat = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power != null) {
            when (power.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_LIGHT -> Heat.Light
                PowerManager.THERMAL_STATUS_MODERATE -> Heat.Moderate
                PowerManager.THERMAL_STATUS_SEVERE -> Heat.Severe
                PowerManager.THERMAL_STATUS_CRITICAL -> Heat.Critical
                PowerManager.THERMAL_STATUS_EMERGENCY -> Heat.Emergency
                PowerManager.THERMAL_STATUS_SHUTDOWN -> Heat.Shutdown
                else -> Heat.None
            }
        } else {
            Heat.None
        }
        return DeviceState(percent, charging, heat)
    }
}

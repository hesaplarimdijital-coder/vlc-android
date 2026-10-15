package app.nagmedar.ui.tvlab.remux

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import app.nagmedar.data.RemuxJobError

/** How hot the device is, as PowerManager reports it (S-1206). */
enum class Thermal { None, Light, Moderate, Severe, Critical, Emergency, Shutdown }

/** What the queue watches while a job runs: the battery and the heat. */
data class DeviceCondition(val batteryPercent: Int, val charging: Boolean, val thermal: Thermal) {
    companion object {
        /** Mains power, a full battery, cool: nothing to wait for. */
        val FINE = DeviceCondition(100, charging = true, thermal = Thermal.None)
    }
}

/** What the engine is told at its next step. */
sealed interface Steer {
    data object Go : Steer

    /** Carry on, but wait [waitMs] between steps: a warm device gets half the work a second. */
    data class Slow(val waitMs: Int) : Steer

    /** Wait [waitMs] and ask again; [reason] is [RemuxJobError.BATTERY] or [RemuxJobError.THERMAL]. */
    data class Pause(val reason: String, val waitMs: Int) : Steer

    /** Give up: the job fails with [reason]; the user starts it again when the device is ready. */
    data class Stop(val reason: String) : Steer
}

/**
 * The rules of the queue (S-1206): on battery, a job pauses under [PAUSE_BELOW] percent and goes
 * on from [RESUME_ABOVE] or when charging; under [STOP_BELOW] it stops, so the remux never
 * drains the device. A moderately hot device slows the job down; a severely hot one pauses it
 * until it cools; critical heat stops it.
 */
object RemuxPolicy {
    const val PAUSE_BELOW = 15
    const val RESUME_ABOVE = 20
    const val STOP_BELOW = 5

    /** How long a paused job waits before looking again. */
    const val PAUSE_WAIT_MS = 5_000

    /** The wait between steps of a slowed job; a step is about a quarter of a second of work. */
    const val SLOW_WAIT_MS = 300

    /** [paused] tells whether the job already waits: the battery must then climb to [RESUME_ABOVE]. */
    fun decide(condition: DeviceCondition, paused: Boolean): Steer {
        if (!condition.charging) {
            if (condition.batteryPercent < STOP_BELOW) return Steer.Stop(RemuxJobError.BATTERY)
            val threshold = if (paused) RESUME_ABOVE else PAUSE_BELOW
            if (condition.batteryPercent < threshold) return Steer.Pause(RemuxJobError.BATTERY, PAUSE_WAIT_MS)
        }
        return when (condition.thermal) {
            Thermal.None, Thermal.Light -> Steer.Go
            Thermal.Moderate -> Steer.Slow(SLOW_WAIT_MS)
            Thermal.Severe -> Steer.Pause(RemuxJobError.THERMAL, PAUSE_WAIT_MS)
            Thermal.Critical, Thermal.Emergency, Thermal.Shutdown -> Steer.Stop(RemuxJobError.THERMAL)
        }
    }
}

/** The device's condition as Android reports it: the sticky battery broadcast and the thermal status. */
class DeviceConditions(private val context: Context) : () -> DeviceCondition {
    private val power: PowerManager? = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    override fun invoke(): DeviceCondition {
        val battery = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else 100
        val plugged = battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val status = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN
        val charging = battery == null || plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && power != null) {
            when (power.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_LIGHT -> Thermal.Light
                PowerManager.THERMAL_STATUS_MODERATE -> Thermal.Moderate
                PowerManager.THERMAL_STATUS_SEVERE -> Thermal.Severe
                PowerManager.THERMAL_STATUS_CRITICAL -> Thermal.Critical
                PowerManager.THERMAL_STATUS_EMERGENCY -> Thermal.Emergency
                PowerManager.THERMAL_STATUS_SHUTDOWN -> Thermal.Shutdown
                else -> Thermal.None
            }
        } else {
            Thermal.None
        }
        return DeviceCondition(percent, charging, thermal)
    }
}

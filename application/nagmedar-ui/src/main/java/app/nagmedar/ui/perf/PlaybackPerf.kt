package app.nagmedar.ui.perf

import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import android.os.Trace
import app.nagmedar.ui.player.info.StatsSample

/** The battery now: its charge in µAh (0 when the device does not tell it), its level in percent, whether it is running down. */
data class BatteryReading(val chargeMicroAh: Long, val percent: Int, val discharging: Boolean)

fun interface BatteryProbe {
    fun read(): BatteryReading?
}

/** The battery as BatteryManager tells it. */
class SystemBattery(context: Context) : BatteryProbe {
    private val manager = context.applicationContext.getSystemService(BatteryManager::class.java)

    override fun read(): BatteryReading? {
        val manager = manager ?: return null
        val percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (percent !in 1..100) return null
        val charge = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).toLong().coerceAtLeast(0)
        // plugged in and full is "not charging", and not running down either
        val discharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) == BatteryManager.BATTERY_STATUS_DISCHARGING
        } else {
            !manager.isCharging
        }
        return BatteryReading(charge, percent, discharging)
    }
}

/**
 * The battery used in an hour, in percent, between [start] and [end] [wallMs] apart: from the
 * charge counter when the device has one (the full charge as the start's level makes it), from
 * the level otherwise, which takes longer to tell. Null when it charged at either end or the
 * time was too short.
 */
fun batteryPerHour(start: BatteryReading?, end: BatteryReading?, wallMs: Long): Double? {
    if (start == null || end == null || !start.discharging || !end.discharging) return null
    val counted = start.chargeMicroAh > 0 && end.chargeMicroAh > 0
    if (wallMs < if (counted) PlaybackPerf.BATTERY_COUNTER_MS else PlaybackPerf.BATTERY_LEVEL_MS) return null
    val used = if (counted) {
        val full = start.chargeMicroAh * 100.0 / start.percent
        (start.chargeMicroAh - end.chargeMicroAh) * 100.0 / full
    } else {
        (start.percent - end.percent).toDouble()
    }
    return (used * 3_600_000.0 / wallMs).coerceAtLeast(0.0)
}

/**
 * S-1404: one playback after the other, as the player shows them: [start] when a film begins
 * (which ends the one before), a [sample] of libVLC's statistics a second, [finish] when it ends
 * or the player closes. libVLC counts a film's frames from its start, so the last sample holds
 * them all; a second whose frames moved on counts as played. A playback shorter than
 * [MIN_SECONDS] is no record.
 */
class PlaybackPerf(
    private val battery: BatteryProbe,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val wallClock: () -> Long = System::currentTimeMillis,
) {
    private class Film(val key: String, val name: String, val startMs: Long, val battery: BatteryReading?) {
        var width = 0
        var height = 0
        var range = ""
        var last: StatsSample? = null
        var seconds = 0
    }

    private var film: Film? = null

    /** The film playing, by its key (its URI); null when none. */
    val playing: String? get() = film?.key

    /** Whether the playing film's picture is still to be told ([describe]). */
    val needsPicture: Boolean get() = film?.let { it.width == 0 } == true

    /** A film with [key] and [name] began: the record of the one before it, if it makes one. */
    fun start(key: String, name: String): PlaybackRecord? {
        val ended = finish()
        film = Film(key, name, clock(), battery.read())
        return ended
    }

    /** The picture of the film [key], once known; another film's is left. */
    fun describe(key: String, width: Int, height: Int, range: String) {
        val film = film?.takeIf { it.key == key } ?: return
        if (width > 0 && height > 0) {
            film.width = width
            film.height = height
        }
        if (range.isNotEmpty()) film.range = range
    }

    /**
     * A second's statistics. Fewer frames than before are the next film's, come before its
     * [start]: they are left.
     */
    fun sample(stats: StatsSample?) {
        val film = film ?: return
        stats ?: return
        val before = film.last
        val due = stats.displayed + stats.lost
        val dueBefore = before?.let { it.displayed + it.lost } ?: 0
        if (before != null && (stats.displayed < before.displayed || stats.lost < before.lost)) return
        if (due > dueBefore) film.seconds++
        film.last = stats
    }

    /** The playing film ended: its record, if it makes one. */
    fun finish(): PlaybackRecord? {
        val film = film ?: return null
        this.film = null
        val last = film.last ?: return null
        if (film.seconds < MIN_SECONDS) return null
        return PlaybackRecord(
            atMs = wallClock(),
            name = film.name,
            width = film.width,
            height = film.height,
            range = film.range,
            seconds = film.seconds,
            shown = last.displayed,
            dropped = last.lost,
            audioLost = last.audioLost,
            batteryPerHour = batteryPerHour(film.battery, battery.read(), clock() - film.startMs),
        )
    }

    companion object {
        const val MIN_SECONDS = 5

        /** With a charge counter, five minutes tell the battery used; with the level alone, half an hour. */
        const val BATTERY_COUNTER_MS = 5 * 60_000L
        const val BATTERY_LEVEL_MS = 30 * 60_000L

        /** The film's frames as counters of a system trace (Perfetto, the macrobenchmarks' traces). */
        fun trace(stats: StatsSample?) {
            if (stats == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
            Trace.setCounter("nagmedar.video.shown", stats.displayed)
            Trace.setCounter("nagmedar.video.dropped", stats.lost)
        }
    }
}

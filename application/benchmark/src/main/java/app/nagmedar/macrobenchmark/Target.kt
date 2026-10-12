package app.nagmedar.macrobenchmark

import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until

/** The app measured: its benchmark build, beside the installed one. */
const val TARGET = "app.nagmedar.benchmark"

/** The new interface's window and the screen it opens on (S-309's launcher shortcuts use the same extra). */
const val SHELL_ACTIVITY = "app.nagmedar.ui.NagmedarActivity"
const val EXTRA_DESTINATION = "nagmedar_destination"

/** Where a file to play is handed to VLC, as another app's "Open with" does. */
const val START_ACTIVITY = "org.videolan.vlc.StartActivity"

/** The app's log of what it measured of itself (app.nagmedar.ui.perf.PerfLog). */
const val PERF_TAG = "NagmedarPerf"

/** What the macrobenchmarks hold Nağmedar to; buildsystem/benchmark/thresholds.json has the rest. */
const val MAX_DROPPED_PERCENT = 0.1

/**
 * The run's arguments (am instrument -e): "nagmedar.videos", the films to play, by their path on
 * the device and comma-separated; "nagmedar.seconds", how long a longer film plays (60).
 */
object Arguments {
    private val arguments get() = InstrumentationRegistry.getArguments()

    val videos: List<String> get() = arguments.getString("nagmedar.videos").orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

    val playSeconds: Int get() = arguments.getString("nagmedar.seconds")?.toIntOrNull() ?: 60
}

fun uiDevice(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

/**
 * The app once opened and its first-run flow skipped, step by step ("Atla"), so that every
 * measure starts in the library: a fresh install would otherwise measure the welcome.
 */
fun UiDevice.pastFirstRun() {
    executeShellCommand("am start -W -n $TARGET/$SHELL_ACTIVITY")
    for (step in 0..FIRST_RUN_STEPS) {
        val skip = wait(Until.findObject(By.res("first-run-skip")), if (step == 0) 5_000 else 3_000) ?: break
        skip.click()
        waitForIdle()
    }
    pressHome()
}

private const val FIRST_RUN_STEPS = 4

/** What [find] finds within [timeoutMs], asked again as the screen fills; null if nothing. */
fun waitFor(timeoutMs: Long = 10_000, find: () -> UiObject2?): UiObject2? {
    val until = SystemClock.uptimeMillis() + timeoutMs
    while (true) {
        find()?.let { return it }
        if (SystemClock.uptimeMillis() > until) return null
        SystemClock.sleep(250)
    }
}

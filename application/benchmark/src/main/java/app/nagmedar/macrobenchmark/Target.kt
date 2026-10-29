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

/**
 * The run's arguments (am instrument -e): "nagmedar.seconds", how long the track plays (10);
 * "nagmedar.feed", a podcast feed the Baseline Profile's run subscribes to when the app has none.
 */
object Arguments {
    private val arguments get() = InstrumentationRegistry.getArguments()

    val playSeconds: Int get() = arguments.getString("nagmedar.seconds")?.toIntOrNull() ?: 10

    val feed: String? get() = arguments.getString("nagmedar.feed")?.takeIf { it.isNotBlank() }
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

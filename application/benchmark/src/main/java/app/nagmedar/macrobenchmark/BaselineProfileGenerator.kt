package app.nagmedar.macrobenchmark

import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Nağmedar 1.0: the Baseline Profile and the startup profile of the app, collected from its
 * benchmark build (release code, names kept) on the CI emulator: the cold start alone for the
 * startup profile, then the listener's path on a phone and on a tablet screen: the Music's table
 * scrolled, a track played and the phone player opened, the Podcasts and an episode's page, the
 * Radio. buildsystem/benchmark/baseline-profile.sh runs it and merges what it writes into
 * application/app/src/main/baselineProfiles. A step the screen does not offer (no music, no
 * podcast, no network for the Radio) is passed over, so the screens may change under it; the run
 * logs each step (tag NagmedarProfile) and leaves the screen's hierarchy where an awaited element
 * was missing, beside the profiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    private val device = uiDevice()

    @Before
    fun prepare() {
        screen(PHONE)
        device.pastFirstRun()
        Arguments.feed?.let { device.subscribeOnce(it) }
        device.waitForLibrary()
    }

    @After
    fun restoreScreen() {
        device.executeShellCommand("wm size reset")
        device.executeShellCommand("wm density reset")
    }

    @Test
    fun startup() = rule.collect(
        packageName = TARGET,
        maxIterations = ITERATIONS,
        stableIterations = STABLE_ITERATIONS,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun phone() = journey(PHONE)

    @Test
    fun tablet() = journey(TABLET)

    private fun journey(size: Screen) {
        screen(size)
        rule.collect(packageName = TARGET, maxIterations = ITERATIONS, stableIterations = STABLE_ITERATIONS) {
            pressHome()
            startActivityAndWait()
            music()
            podcasts()
            radio()
            pressHome()
        }
    }

    /** The Music's table flung down and back, then a track of it played and its player opened. */
    private fun MacrobenchmarkScope.music() {
        open("music")
        val list = waitFor { device.findObject(By.res("music"))?.findObject(By.scrollable(true)) } ?: return device.missing("music-list")
        trace("music: the table")
        passOver {
            list.setGestureMargin(device.displayWidth / 5)
            repeat(2) { list.fling(Direction.DOWN) }
            list.fling(Direction.UP)
            device.waitForIdle()
        }
        passOver { waitFor { device.findObject(By.res("music"))?.findObject(By.scrollable(true))?.findObject(By.clickable(true)) }?.click() }
        // the phone's mini player opens the phone player; a tablet shows the track in its inspector
        passOver { waitFor(5_000) { device.findObject(By.res("mini-player")) }?.click() }
        if (waitFor(5_000) { device.findObject(By.res("phone-player")) } != null) {
            trace("music: the phone player")
            device.waitForIdle()
            device.pressBack()
        } else {
            device.missing("phone-player")
        }
        device.waitForIdle()
    }

    /** A podcast's page and the page of one of its episodes. */
    private fun MacrobenchmarkScope.podcasts() {
        open("podcasts")
        val show = waitFor { device.findObject(By.res("podcast-row")) ?: device.findObject(By.res("podcast-tile")) } ?: return device.missing("podcast-row")
        passOver { show.click() }
        val episode = waitFor { device.findObject(By.res("podcast-episode")) }
        if (episode != null) {
            trace("podcasts: the podcast's page")
            passOver { episode.click() }
            if (waitFor { device.findObject(By.res("episode-detail")) } != null) trace("podcasts: the episode's page") else device.missing("episode-detail")
            device.waitForIdle()
            device.pressBack()
        } else {
            device.missing("podcast-episode")
        }
        device.pressBack()
        device.waitForIdle()
    }

    /** The Radio's stations (the directory's, so only with network) scrolled. */
    private fun MacrobenchmarkScope.radio() {
        open("radio")
        val list = waitFor { device.findObject(By.res("radio-list"))?.findObject(By.scrollable(true)) ?: device.findObject(By.res("radio-list")) }
        if (list != null) passOver {
            trace("radio: the stations")
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(Direction.DOWN)
        } else {
            device.missing("radio-list")
        }
        device.waitForIdle()
    }

    /** One of the app's destinations, as its launcher shortcuts open them. */
    private fun MacrobenchmarkScope.open(route: String) {
        device.executeShellCommand("am start -W -n $TARGET/$SHELL_ACTIVITY --es $EXTRA_DESTINATION $route")
        device.waitForIdle()
    }

    private fun screen(size: Screen) {
        device.executeShellCommand("wm size ${size.size}")
        device.executeShellCommand("wm density ${size.density}")
    }

    /** A screen size and density: [PHONE] as the Galaxy S24's, [TABLET] as a landscape tablet's. */
    private class Screen(val size: String, val density: Int)

    private companion object {
        val PHONE = Screen("1080x2340", 420)
        val TABLET = Screen("2560x1600", 320)
        const val ITERATIONS = 6
        const val STABLE_ITERATIONS = 2
    }
}

private const val TAG = "NagmedarProfile"

private fun trace(step: String) {
    Log.i(TAG, step)
}

/**
 * The hierarchy of the screen on which [element] was awaited in vain, once per element, beside the
 * profiles (the test app's media folder, where BaselineProfileRule writes them).
 */
@Suppress("DEPRECATION")
private fun UiDevice.missing(element: String) {
    Log.w(TAG, "missing: $element")
    passOver {
        val dir = InstrumentationRegistry.getInstrumentation().context.externalMediaDirs.firstOrNull() ?: return@passOver
        val file = File(dir, "missing-$element.xml")
        if (!file.exists()) {
            dir.mkdirs()
            dumpWindowHierarchy(file)
        }
    }
}

/** [step], or nothing when the screen changed under it (a row gone stale, a list that moved). */
private inline fun passOver(step: () -> Unit) {
    try {
        step()
    } catch (e: RuntimeException) {
        // the step is optional: the profile is what the rest of the path makes
    }
}

/** Subscribes the app to [feed] (the Podcasts' "Add by address") when it follows no podcast yet. */
private fun UiDevice.subscribeOnce(feed: String) {
    executeShellCommand("am start -W -n $TARGET/$SHELL_ACTIVITY --es $EXTRA_DESTINATION podcasts")
    val followed = waitFor(5_000) { findObject(By.res("podcast-row")) ?: findObject(By.res("podcast-tile")) }
    if (followed == null) passOver {
        waitFor { findObject(By.res("podcast-add")) }?.click() ?: missing("podcast-add")
        val field: UiObject2? = waitFor { findObject(By.res("podcast-add-field")) }
        if (field != null) {
            field.setText(feed)
            waitFor { findObject(By.res("podcast-add-submit")) }?.click() ?: missing("podcast-add-submit")
            val done = waitFor(30_000) { findObject(By.res("podcast-page")) ?: findObject(By.res("podcast-row")) ?: findObject(By.res("podcast-tile")) }
            if (done != null) trace("subscribed to $feed") else missing("podcast-subscribed")
        } else {
            missing("podcast-add-field")
        }
    }
    pressHome()
}

/** The Music's table once it shows a track: the first-run scan of the device's music has ended. */
private fun UiDevice.waitForLibrary() {
    executeShellCommand("am start -W -n $TARGET/$SHELL_ACTIVITY --es $EXTRA_DESTINATION music")
    waitFor(30_000) { findObject(By.res("music"))?.findObject(By.scrollable(true))?.findObject(By.clickable(true)) }
    pressHome()
}

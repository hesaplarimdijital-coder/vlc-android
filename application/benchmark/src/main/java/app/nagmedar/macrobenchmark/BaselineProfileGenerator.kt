package app.nagmedar.macrobenchmark

import android.graphics.Rect
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
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
        // 1.0.3: a playing track's seek bar and times never leave the screen idle: each look at it (and each wait for
        // it) would take the default ten seconds, and a bar awaited for fifteen was looked for twice
        Configurator.getInstance().setWaitForIdleTimeout(IDLE_MS)
        // the first run and the subscription on the tablet's screen, where its dialog is a window
        // of the app's; each test then sets the screen it walks
        screen(TABLET)
        device.pastFirstRun()
        device.waitForLibrary()
        Arguments.feed?.let { device.subscribeOnce(it) }
        screen(PHONE)
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
            list.insideTheBars(device)
            repeat(2) { list.fling(Direction.DOWN) }
            list.fling(Direction.UP)
            device.waitForIdle()
        }
        device.freshScreen()
        // a track by its title (the phone's rows also hold a ⋯ button, which opens a sheet instead):
        // the CI run's tracks are "Profile track …" (emulator.yml), another library's first row; a
        // row of the table, not a card of the "recently played" shelf above it, which plays nothing.
        // 1.0.1: the middle one of those in sight, since the column titles' band takes every press
        // over it (a row half under the pinned titles no longer plays) and the bars lie over the end
        passOver {
            waitFor {
                device.findObject(By.res("music"))?.let { music ->
                    val shelf = music.findObject(By.res("recent-shelf"))?.visibleBounds
                    music.findObjects(By.textStartsWith("Profile track"))
                        .filter { shelf == null || !Rect.intersects(shelf, it.visibleBounds) }
                        .sortedBy { it.visibleBounds.top }
                        .let { rows -> rows.getOrNull(rows.size / 2) }
                        ?: music.findObject(By.scrollable(true))?.findObject(By.clickable(true))
                }
            }?.let { row ->
                val at = row.visibleCenter
                trace("music: a tap at ${at.x},${at.y}")
                row.click()
            }
        }
        // the phone's mini player opens the phone player (its first clickable part, the track's
        // title, does); 1.0.2: a tablet's transport bar opens the big player over the window
        // with its "open the player" button
        passOver {
            val open = waitFor(15_000) {
                device.freshScreen()
                device.findObject(By.res("transport-open-player")) ?: device.findObject(By.res("mini-player"))
            }
            if (open == null) device.missing("player-bar")
            if (open?.resourceName?.endsWith("transport-open-player") == true) {
                open.click()
            } else {
                (open?.findObject(By.clickable(true)) ?: open)?.click()
            }
        }
        val player = waitFor(10_000) {
            device.freshScreen()
            device.findObject(By.res("phone-player"))
        }
        if (player != null) {
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
            list.insideTheBars(device)
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
        screenName = size.name
        device.executeShellCommand("wm size ${size.size}")
        device.executeShellCommand("wm density ${size.density}")
    }

    /** A screen size and density: [PHONE] as the Galaxy S24's, [TABLET] as a landscape tablet's. */
    private class Screen(val name: String, val size: String, val density: Int)

    private companion object {
        val PHONE = Screen("phone", "1080x2340", 420)
        val TABLET = Screen("tablet", "2560x1600", 320)
        const val ITERATIONS = 6
        const val STABLE_ITERATIONS = 2
        const val IDLE_MS = 3_000L
    }
}

private const val TAG = "NagmedarProfile"

/** The screen walked (phone, tablet), in the log and the names of the hierarchies left behind. */
private var screenName = ""

private fun trace(step: String) {
    Log.i(TAG, "$screenName: $step")
}

/**
 * The hierarchy of the screen on which [element] was awaited in vain, once per element, beside the
 * profiles (the test app's media folder, where BaselineProfileRule writes them).
 */
@Suppress("DEPRECATION")
private fun UiDevice.missing(element: String) {
    Log.w(TAG, "$screenName: missing: $element")
    passOver {
        val dir = InstrumentationRegistry.getInstrumentation().context.externalMediaDirs.firstOrNull() ?: return@passOver
        val file = File(dir, "missing-$screenName-$element.xml")
        if (!file.exists()) {
            dir.mkdirs()
            dumpWindowHierarchy(file)
            // 1.0.3: what the screen showed and what the system knew of the playback then, beside the hierarchy (which
            // is UiAutomator's copy of the screen, and may be older than the screen)
            try {
                takeScreenshot(File(dir, "missing-$screenName-$element.png"))
                File(dir, "missing-$screenName-$element-session.txt").writeText(executeShellCommand("dumpsys media_session"))
            } catch (e: Exception) {
                Log.w(TAG, "$screenName: missing: $element: nothing more kept: $e")
            }
        }
    }
}

/**
 * 1.0.3: a list's swipes kept inside its own part of the screen. A phone's list reaches behind the bottom bar, the
 * mini player and the system's navigation bar (the shell draws behind them): a swipe from its lower edge would start
 * on the bar and scroll nothing.
 */
private fun UiObject2.insideTheBars(device: UiDevice) {
    setGestureMargins(device.displayWidth / 5, device.displayHeight / 10, device.displayWidth / 5, device.displayHeight / 3)
}

/**
 * 1.0.3: UiAutomator's copy of the screen forgotten, so that what it finds next is read from the app again: the copy
 * is refreshed by the app's accessibility events, and a screen that changes much at once (a list that moved, a bar
 * that came) has been seen to leave old rows in it. Setting the service's own settings again clears it.
 */
private fun UiDevice.freshScreen() {
    passOver {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo ?: return@passOver
        automation.serviceInfo = info
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
    // right after the first run the window may keep the Music: started again until it shows the Podcasts
    var add: UiObject2? = null
    for (attempt in 1..3) {
        executeShellCommand("am start -W -n $TARGET/$SHELL_ACTIVITY --es $EXTRA_DESTINATION podcasts")
        add = waitFor(8_000) { findObject(By.res("podcast-add")) }
        if (add != null) break
        executeShellCommand("am force-stop $TARGET")
    }
    // twice at most: a first try that the window lost (it was redrawn meanwhile) saves nothing
    var done: UiObject2? = waitFor(3_000) { findObject(By.res("podcast-row")) ?: findObject(By.res("podcast-tile")) }
    for (attempt in 1..2) {
        if (done != null) break
        passOver {
            (add ?: waitFor(8_000) { findObject(By.res("podcast-add")) })?.click() ?: missing("podcast-add")
            // the dialog is a window of its own, whose test tags may not be resource ids: its text field
            // is then the screen's one EditText, and Enter in it subscribes
            val field: UiObject2? = waitFor { findObject(By.res("podcast-add-field")) ?: findObject(By.clazz("android.widget.EditText")) }
            if (field != null) {
                field.setText(feed)
                findObject(By.res("podcast-add-submit"))?.click() ?: pressEnter()
                done = waitFor(60_000) { findObject(By.res("podcast-page")) ?: findObject(By.res("podcast-row")) ?: findObject(By.res("podcast-tile")) }
                if (done != null) trace("subscribed to $feed") else missing("podcast-subscribed")
            } else {
                missing("podcast-add-field")
            }
        }
        add = null
    }
    pressHome()
}

/** The Music's table once it shows a track: the first-run scan of the device's music has ended. */
private fun UiDevice.waitForLibrary() {
    executeShellCommand("am start -W -n $TARGET/$SHELL_ACTIVITY --es $EXTRA_DESTINATION music")
    waitFor(30_000) { findObject(By.res("music"))?.findObject(By.scrollable(true))?.findObject(By.clickable(true)) }
    pressHome()
}

package app.nagmedar.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-1404: lists flung down and back up, their frames timed: Settings, the longest page, the
 * Films (a grid on a tablet, a table in DeX), which needs films on the device, and (S-803) the
 * Music's track table, which needs music. thresholds.json: nine frames in ten on time.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun firstRun() = uiDevice().pastFirstRun()

    @Test
    fun settings() = scroll("settings") { device.findObject(By.res("settings")) }

    @Test
    fun films() = scroll("movies") { device.findObject(By.res("films"))?.findObject(By.scrollable(true)) }

    @Test
    fun music() = scroll("music") { device.findObject(By.res("music"))?.findObject(By.scrollable(true)) }

    private fun scroll(route: String, list: MacrobenchmarkScope.() -> UiObject2?) = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
        iterations = 5,
        startupMode = StartupMode.COLD,
        setupBlock = {
            pressHome()
            startActivityAndWait(Intent().setClassName(TARGET, SHELL_ACTIVITY).putExtra(EXTRA_DESTINATION, route))
        },
    ) {
        val target = waitFor { list() }
        assumeTrue("nothing to scroll on “$route”: the Films need films on the device, the Music music", target != null)
        target!!.setGestureMargin(device.displayWidth / 5)
        repeat(3) { target.fling(Direction.DOWN) }
        repeat(3) { target.fling(Direction.UP) }
        device.waitForIdle()
    }
}

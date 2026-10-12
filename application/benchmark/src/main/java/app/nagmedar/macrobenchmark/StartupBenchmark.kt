package app.nagmedar.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * S-1404: the start-up from the launcher icon to the library's first frame: cold (no process)
 * and warm (the process kept, the window gone). thresholds.json: a cold one within a second.
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Before
    fun firstRun() = uiDevice().pastFirstRun()

    @Test
    fun cold() = startup(StartupMode.COLD)

    @Test
    fun warm() = startup(StartupMode.WARM)

    private fun startup(mode: StartupMode) = rule.measureRepeated(
        packageName = TARGET,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(BaselineProfileMode.UseIfAvailable),
        iterations = 10,
        startupMode = mode,
        setupBlock = { pressHome() },
    ) {
        startActivityAndWait()
    }
}

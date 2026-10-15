package app.nagmedar.ui.visual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/**
 * S-611: the battery rules: a visualiser draws only shown, on a lit screen, with something
 * playing and Battery Saver off (or let run under it), at the frame rate's cap; and the estimate
 * of what it costs, in the battery's share an hour.
 */
class VisualizerPolicyTest {

    private val running = VisualizerConditions(shown = true, screenOn = true, powerSave = false, playing = true)

    @Test
    fun itDrawsAtTheCap() {
        assertEquals(VisualizerRun.Running(30), VisualizerPolicy.decide(VisualizerSettings(), running))
        assertEquals(VisualizerRun.Running(60), VisualizerPolicy.decide(VisualizerSettings(fps = VisualizerFps.Fps60), running))
        assertEquals(VisualizerRun.Running(15), VisualizerPolicy.decide(VisualizerSettings(fps = VisualizerFps.Fps15), running))
    }

    @Test
    fun hiddenOffOrSavingItPauses() {
        val settings = VisualizerSettings()
        assertEquals(VisualizerRun.Paused(VisualizerPause.Hidden), VisualizerPolicy.decide(settings, running.copy(shown = false, screenOn = false)))
        assertEquals(VisualizerRun.Paused(VisualizerPause.ScreenOff), VisualizerPolicy.decide(settings, running.copy(screenOn = false)))
        assertEquals(VisualizerRun.Paused(VisualizerPause.BatterySaver), VisualizerPolicy.decide(settings, running.copy(powerSave = true)))
        assertEquals(VisualizerRun.Paused(VisualizerPause.NotPlaying), VisualizerPolicy.decide(settings, running.copy(playing = false)))
    }

    @Test
    fun batterySaverCanBeLetThrough() {
        val settings = VisualizerSettings(onBatterySaver = true)
        assertEquals(VisualizerRun.Running(30), VisualizerPolicy.decide(settings, running.copy(powerSave = true)))
        // but never with the screen off
        assertEquals(VisualizerRun.Paused(VisualizerPause.ScreenOff), VisualizerPolicy.decide(settings, running.copy(powerSave = true, screenOn = false)))
    }

    @Test
    fun theCostGrowsWithTheFrameRate() {
        assertEquals(280.0, VisualizerCost.milliwatts(VisualizerKind.Spectrum, 60), 1e-9)
        assertEquals(85.0, VisualizerCost.milliwatts(VisualizerKind.Vu, 30), 1e-9)
        // 280 mW of a 19.25 Wh battery: 1.45 % an hour
        assertEquals(1.4545, VisualizerCost.percentPerHour(VisualizerKind.Spectrum, 60, VisualizerCost.DEFAULT_BATTERY_WH), 1e-3)
        assertEquals(0.12, VisualizerCost.percentPerHour(VisualizerKind.Vu, 15, 45.9), 1e-2)
    }

    @Test
    fun theBatteryFromItsCharge() {
        // 2.5 Ah at 50 %: a 5 Ah battery at 3.85 V
        assertEquals(19.25, VisualizerCost.fullWh(2_500_000, 50)!!, 1e-9)
        // the Tab S11 Ultra's 11 600 mAh
        assertEquals(44.66, VisualizerCost.fullWh(11_600_000, 100)!!, 1e-2)
        assertNull(VisualizerCost.fullWh(0, 50))
        assertNull(VisualizerCost.fullWh(Int.MIN_VALUE, 50))
        assertNull(VisualizerCost.fullWh(2_500_000, 0))
        assertNull(VisualizerCost.fullWh(50, 100))
    }

    @Test
    fun theShareIsWrittenInTheLanguage() {
        assertEquals("%0,7", percentText(0.7, Locale.forLanguageTag("tr-TR")))
        assertEquals("0.7%", percentText(0.7, Locale.UK))
        assertEquals("%12", percentText(12.4, Locale.forLanguageTag("tr-TR")))
    }
}

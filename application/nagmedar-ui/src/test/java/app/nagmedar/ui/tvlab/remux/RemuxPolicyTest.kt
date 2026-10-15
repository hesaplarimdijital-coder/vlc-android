package app.nagmedar.ui.tvlab.remux

import app.nagmedar.data.RemuxJobError
import org.junit.Assert.assertEquals
import org.junit.Test

/** S-1206: what the queue does about the battery and the heat. */
class RemuxPolicyTest {

    private fun on(battery: Int, charging: Boolean = false, thermal: Thermal = Thermal.None, paused: Boolean = false) =
        RemuxPolicy.decide(DeviceCondition(battery, charging, thermal), paused)

    @Test
    fun onMainsPowerTheBatteryDoesNotMatter() {
        assertEquals(Steer.Go, on(3, charging = true))
        assertEquals(Steer.Go, on(100))
        assertEquals(Steer.Go, on(15))
    }

    @Test
    fun onBatteryTheJobPausesUnderFifteenAndGoesOnFromTwenty() {
        assertEquals(Steer.Pause(RemuxJobError.BATTERY, RemuxPolicy.PAUSE_WAIT_MS), on(14))
        // paused, it waits for the battery to climb: 16 is not enough, 20 is; charging ends the wait at once
        assertEquals(Steer.Pause(RemuxJobError.BATTERY, RemuxPolicy.PAUSE_WAIT_MS), on(16, paused = true))
        assertEquals(Steer.Go, on(20, paused = true))
        assertEquals(Steer.Go, on(16, charging = true, paused = true))
        // not paused yet, 16 goes on
        assertEquals(Steer.Go, on(16))
    }

    @Test
    fun aDrainedBatteryStopsTheJob() {
        assertEquals(Steer.Stop(RemuxJobError.BATTERY), on(4))
        assertEquals(Steer.Stop(RemuxJobError.BATTERY), on(0, paused = true))
        assertEquals(Steer.Go, on(4, charging = true))
    }

    @Test
    fun heatSlowsPausesThenStops() {
        assertEquals(Steer.Go, on(80, thermal = Thermal.Light))
        assertEquals(Steer.Slow(RemuxPolicy.SLOW_WAIT_MS), on(80, thermal = Thermal.Moderate))
        assertEquals(Steer.Pause(RemuxJobError.THERMAL, RemuxPolicy.PAUSE_WAIT_MS), on(80, thermal = Thermal.Severe))
        assertEquals(Steer.Stop(RemuxJobError.THERMAL), on(80, thermal = Thermal.Critical))
        assertEquals(Steer.Stop(RemuxJobError.THERMAL), on(80, charging = true, thermal = Thermal.Shutdown))
        // a low battery comes first: it would run out while the device cools
        assertEquals(Steer.Pause(RemuxJobError.BATTERY, RemuxPolicy.PAUSE_WAIT_MS), on(10, thermal = Thermal.Moderate))
    }
}

package app.nagmedar.ui.player.effects

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** Temporary: reports what EffectsOProbeTest saw (CI diagnosis only). */
@RunWith(AndroidJUnit4::class)
class EffectsPProbeTest {
    @Test
    fun report() {
        fail("PROBE ${ProbeInfo.text}")
    }
}

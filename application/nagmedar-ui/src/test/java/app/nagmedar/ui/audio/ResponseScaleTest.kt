package app.nagmedar.ui.audio

import org.junit.Assert.assertEquals
import org.junit.Test

/** S-906: the response graph holds its curve. */
class ResponseScaleTest {

    @Test
    fun twelveDecibelsEachWayAtLeast() {
        val scale = ResponseScale.of(listOf(-3.0, 4.5))
        assertEquals(-12.0, scale.bottom, 0.0)
        assertEquals(12.0, scale.top, 0.0)
        assertEquals(listOf(-12.0, -6.0, 0.0, 6.0, 12.0), scale.lines)
    }

    @Test
    fun anAutomaticPreampWidensItDown() {
        val scale = ResponseScale.of(listOf(-20.9, -14.2, -7.0))
        assertEquals(-24.0, scale.bottom, 0.0)
        assertEquals(12.0, scale.top, 0.0)
        assertEquals(listOf(-24.0, -18.0, -12.0, -6.0, 0.0, 6.0, 12.0), scale.lines)
    }

    @Test
    fun farOutItStopsAndItsLinesThin() {
        val scale = ResponseScale.of(listOf(-60.0, 30.0))
        assertEquals(-48.0, scale.bottom, 0.0)
        assertEquals(24.0, scale.top, 0.0)
        assertEquals(listOf(-48.0, -36.0, -24.0, -12.0, 0.0, 12.0, 24.0), scale.lines)
    }
}

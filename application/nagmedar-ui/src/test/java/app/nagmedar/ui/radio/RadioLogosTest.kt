package app.nagmedar.ui.radio

import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1105: a logo is read within a bound, and one that failed is asked for again only when the failure was the network's. */
class RadioLogosTest {

    /** An answer that never ends, like a chunked body that goes on: counts what was taken from it. */
    private class Endless : Source {
        var produced = 0L

        override fun read(sink: Buffer, byteCount: Long): Long {
            sink.write(ByteArray(byteCount.toInt()))
            produced += byteCount
            return byteCount
        }

        override fun timeout() = Timeout.NONE

        override fun close() {}
    }

    @Test
    fun aSmallBodyIsReadWhole() {
        val bytes = ByteArray(1_000) { it.toByte() }
        assertArrayEquals(bytes, RadioLogos.readBounded(Buffer().write(bytes), 2_000))
        assertArrayEquals(bytes, RadioLogos.readBounded(Buffer().write(bytes), 1_000))
    }

    @Test
    fun aBodyOneByteOverTheBoundIsRefused() {
        assertNull(RadioLogos.readBounded(Buffer().write(ByteArray(1_001)), 1_000))
    }

    @Test
    fun anEndlessBodyIsNotReadBeyondTheBound() {
        val endless = Endless()
        assertNull(RadioLogos.readBounded(endless.buffer(), 64 * 1024))
        // a few segments past the bound at most, not the whole stream
        assertTrue("read ${endless.produced}", endless.produced < 64 * 1024 + 64 * 1024)
    }

    @Test
    fun aRefusalIsForGood() {
        var now = 1_000L
        val failures = LogoFailures({ now }, 5 * 60_000L)
        failures.never("https://a.example/logo.png")
        assertTrue(failures.blocked("https://a.example/logo.png"))
        now += 24 * 60 * 60_000L
        assertTrue(failures.blocked("https://a.example/logo.png"))
        assertFalse(failures.blocked("https://b.example/logo.png"))
    }

    @Test
    fun aNetworkFailureIsForgottenAfterAWhile() {
        var now = 1_000L
        val failures = LogoFailures({ now }, 5 * 60_000L)
        failures.later("https://a.example/logo.png")
        assertTrue(failures.blocked("https://a.example/logo.png"))
        now += 4 * 60_000L
        assertTrue(failures.blocked("https://a.example/logo.png"))
        now += 2 * 60_000L
        assertFalse(failures.blocked("https://a.example/logo.png"))
    }

    @Test
    fun aNetworkFailureDoesNotUndoARefusal() {
        var now = 0L
        val failures = LogoFailures({ now }, 60_000L)
        failures.never("u")
        failures.later("u")
        now += 10 * 60_000L
        assertTrue(failures.blocked("u"))
        assertEquals(false, failures.blocked("other"))
    }
}

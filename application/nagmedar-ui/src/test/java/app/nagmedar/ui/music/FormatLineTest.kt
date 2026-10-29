package app.nagmedar.ui.music

import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.0.1: the phone player's format line that does not fit goes on two lines: the source over how it plays. */
class FormatLineTest {

    @Test
    fun theSourceOverTheOutput() {
        assertEquals("FLAC 16/44.1\nAAudio · 48 kHz", formatOnTwoLines("FLAC 16/44.1 · AAudio · 48 kHz"))
        assertEquals("FLAC 24/96\nUSB DAC · 96 kHz · Bit-perfect", formatOnTwoLines("FLAC 24/96 · USB DAC · 96 kHz · Bit-perfect"))
    }

    @Test
    fun aLineOfOnePartStays() {
        assertEquals("MP3 320 kb/s", formatOnTwoLines("MP3 320 kb/s"))
    }
}

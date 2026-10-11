package app.nagmedar.ui.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.videolan.vlc.ScanProgress

/** S-308: the scan line of the library states reads MediaParsingService's progress. */
class LibraryStatesTest {

    @Test
    fun parsingProgress() {
        val scan = ScanState.from(ScanProgress(32f, "Medya ayrıştırılıyor 32,00% · 1248/3900", inDiscovery = false))
        assertTrue(scan.active)
        assertFalse(scan.discovering)
        assertEquals(0.32f, scan.fraction!!, 0.0001f)
        assertEquals(1248, scan.done)
        assertEquals(3900, scan.total)
    }

    @Test
    fun discoveryHasNoCount() {
        val scan = ScanState.from(ScanProgress(0f, "Keşfediliyor /storage/emulated/0/Movies", inDiscovery = true))
        assertTrue(scan.active)
        assertTrue(scan.discovering)
        assertNull(scan.fraction)
        assertNull(scan.done)
    }

    @Test
    fun noProgressIsIdle() {
        assertEquals(ScanState.Idle, ScanState.from(null))
        assertFalse(ScanState.Idle.active)
    }

    @Test
    fun digitGroups() {
        assertEquals("999", groupDigits(999))
        assertEquals("3 900", groupDigits(3900))
        assertEquals("12 480", groupDigits(12480))
        assertEquals("1 000 000", groupDigits(1_000_000))
        assertEquals("-4 812", groupDigits(-4812))
    }
}

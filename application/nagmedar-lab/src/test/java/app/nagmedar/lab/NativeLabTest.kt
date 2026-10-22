package app.nagmedar.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** [NativeLab.loaded]: what the main thread reads, which never loads the library itself. */
class NativeLabTest {
    @Test
    fun theFlagTellsWhatTheLoadFoundAndNeverLoads() {
        // a JVM test has no libnagmedar_lab.so; another test may have asked for it already (null before that)
        val before = NativeLab.loaded
        // reading the flag loaded nothing: it says the same until the library is asked for
        assertEquals(before, NativeLab.loaded)
        assertFalse(NativeLab.available)
        assertEquals(false, NativeLab.loaded)
    }
}

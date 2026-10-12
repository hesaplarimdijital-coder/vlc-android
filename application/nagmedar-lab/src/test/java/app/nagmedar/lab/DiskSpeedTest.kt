package app.nagmedar.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1204: [DiskSpeed] reads the JSON of native/lab/disktest.c. */
class DiskSpeedTest {
    @Test
    fun theSpeedIsRead() {
        val speed = DiskSpeed.parse("""{"version":1,"error":"","bytes":1073741824,"ms":2604,"mb_per_s":412.3,"first_ms":11,"complete":true}""")!!
        assertEquals(1_073_741_824L, speed.bytes)
        assertEquals(412.3, speed.megabytesPerSecond, 0.001)
        assertEquals(3_298_400L, speed.kbps)
        assertEquals(11L, speed.firstMs)
        assertTrue(speed.complete)
        assertNull(DiskSpeed.parse("""{"version":1,"error":"read: Input/output error"}"""))
        assertEquals("read: Input/output error", DiskSpeed.error("""{"version":1,"error":"read: Input/output error"}"""))
    }
}

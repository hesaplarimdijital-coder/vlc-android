package app.nagmedar.ui.podcast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** S-1101: the dates and lengths of feeds, in the forms they come in. */
class FeedDatesTest {

    /** 2026-10-10 06:00 UTC. */
    private val moment = 1_791_612_000_000L

    @Test
    fun rfc822InItsVariants() {
        assertEquals(moment, FeedDates.parse("Sat, 10 Oct 2026 09:00:00 +0300"))
        assertEquals(moment, FeedDates.parse("Sat, 10 Oct 2026 06:00:00 GMT"))
        assertEquals(moment, FeedDates.parse("Sat, 10 Oct 2026 06:00:00 +0000 (UTC)"))
        assertEquals(moment, FeedDates.parse("Sat, 10 Oct 2026 01:00:00 EST"))
        assertEquals(moment, FeedDates.parse("Sat, 10 Oct 2026 02:00:00 EDT"))
        assertEquals(moment, FeedDates.parse("Sat, 10 Oct 2026 01:00:00 -05:00"))
        // no weekday, no seconds, a two-digit year, a long month name, a single-digit hour
        assertEquals(moment, FeedDates.parse("10 Oct 2026 06:00 UTC"))
        assertEquals(moment, FeedDates.parse("10 Oct 26 06:00 GMT"))
        assertEquals(moment, FeedDates.parse("Saturday, 10 October 2026 6:00:00 GMT"))
        assertEquals(moment, FeedDates.parse("  Sat, 10 Oct 2026 06:00:00 GMT\n"))
        // no zone at all: UTC
        assertEquals(moment, FeedDates.parse("Sat, 10 Oct 2026 06:00:00"))
    }

    @Test
    fun iso8601() {
        assertEquals(moment, FeedDates.parse("2026-10-10T09:00:00+03:00"))
        assertEquals(moment, FeedDates.parse("2026-10-10T06:00:00Z"))
        assertEquals(moment, FeedDates.parse("2026-10-10T06:00:00.123Z"))
        assertEquals(moment, FeedDates.parse("2026-10-10 06:00:00"))
        assertEquals(1_791_590_400_000L, FeedDates.parse("2026-10-10"))
    }

    @Test
    fun unixTime() {
        assertEquals(moment, FeedDates.parse("1791612000"))
        assertEquals(moment, FeedDates.parse("1791612000000"))
    }

    @Test
    fun whatIsNoDateIsNull() {
        listOf("geçen salı", "", "   ", "31 Foo 2026 00:00:00 GMT", "32 Oct 2026 00:00:00 GMT", "31 Nov 2026 00:00:00 GMT", "10 Oct 1900", "10 Oct 2999", "yarın", "0")
            .forEach { assertNull(it, FeedDates.parse(it)) }
        assertNull(FeedDates.parse(null))
    }

    @Test
    fun lengths() {
        assertEquals(3_723_000L, FeedDates.duration("3723"))
        assertEquals(3_723_500L, FeedDates.duration("3723.5"))
        assertEquals(3_723_500L, FeedDates.duration("3723,5"))
        assertEquals(3_723_000L, FeedDates.duration("62:03"))
        assertEquals(3_723_000L, FeedDates.duration("1:02:03"))
        assertEquals(3_723_000L, FeedDates.duration(" 01:02:03 "))
        assertEquals(60_500L, FeedDates.duration("00:01:00.500"))
    }

    @Test
    fun whatIsNoLengthIsZero() {
        listOf("", "  ", "12:xx", "1:2:3:4", "-5", "99999999", "abc", "NaN").forEach { assertEquals(it, 0L, FeedDates.duration(it)) }
        assertEquals(0L, FeedDates.duration(null))
    }
}

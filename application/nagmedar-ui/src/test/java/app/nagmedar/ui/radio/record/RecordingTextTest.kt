package app.nagmedar.ui.radio.record

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** S-207, S-1107: the words on days, schedules, notices and the notifications, in both languages. */
@RunWith(AndroidJUnit4::class)
class RecordingTextTest {

    private val zone = ZoneId.of("Europe/Istanbul")
    private val now = ZonedDateTime.of(2026, 10, 14, 9, 20, 0, 0, zone).toInstant().toEpochMilli()
    private val res: Resources get() = ApplicationProvider.getApplicationContext<Context>().resources
    private val words = object : RecordingWords {
        override fun get(id: Int, vararg args: Any): String = res.getString(id, *args)
    }
    private val size = { bytes: Long -> "${bytes / 1024} KB" }

    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun request(scheduleId: Long? = null) = RecordingRequest("a", RecordingStation("Sabah Seansı", "https://yayin.example/sabah", uuid = "e5"), scheduleId = scheduleId)

    @Test
    @Config(qualifiers = "en")
    fun daysAndTimesInEnglish() {
        assertEquals("Today 09:05", RecordingText.began(res, at(14, 9, 5), now, zone, Locale.ENGLISH))
        assertEquals("Yesterday 21:30", RecordingText.began(res, at(13, 21, 30), now, zone, Locale.ENGLISH))
        assertEquals("9 October 08:00", RecordingText.began(res, at(9, 8), now, zone, Locale.ENGLISH))
        assertEquals("07:30–08:30", RecordingText.range(7, 30, 60))
        assertEquals("23:30–01:00", RecordingText.range(23, 30, 90))
        assertEquals("45 min", RecordingText.length(res, 45))
        assertEquals("2 h", RecordingText.length(res, 120))
        assertEquals("1 h 30 min", RecordingText.length(res, 90))
    }

    @Test
    @Config(qualifiers = "tr")
    fun daysAndTimesInTurkish() {
        val locale = Locale.forLanguageTag("tr")
        assertEquals("Bugün 09:05", RecordingText.began(res, at(14, 9, 5), now, zone, locale))
        assertEquals("Dün 21:30", RecordingText.began(res, at(13, 21, 30), now, zone, locale))
        assertEquals("9 Ekim 08:00", RecordingText.began(res, at(9, 8), now, zone, locale))
    }

    @Test
    @Config(qualifiers = "en")
    fun sizesAreWrittenAsTheRestOfTheAppWritesThem() {
        assertEquals("900 B", RecordingText.size(900, Locale.ENGLISH))
        assertEquals("12 kB", RecordingText.size(12_000, Locale.ENGLISH))
        assertEquals("500 MB", RecordingText.size(500_000_000, Locale.ENGLISH))
        assertEquals("10 GB", RecordingText.size(10_000_000_000, Locale.ENGLISH))
        assertEquals("1.5 GB", RecordingText.size(1_500_000_000, Locale.ENGLISH))
        assertEquals("1,5 GB", RecordingText.size(1_500_000_000, Locale.forLanguageTag("tr")))
    }

    @Test
    @Config(qualifiers = "en")
    fun howLongARecordingHasGoneOn() {
        assertEquals("0 s", RecordingText.elapsed(res, -5))
        assertEquals("40 s", RecordingText.elapsed(res, 40_000))
        assertEquals("23 min", RecordingText.elapsed(res, 23 * 60_000L + 5_000))
        assertEquals("1 h 5 min", RecordingText.elapsed(res, 65 * 60_000L))
        assertEquals("2 h", RecordingText.elapsed(res, 120 * 60_000L))
    }

    @Test
    @Config(qualifiers = "en")
    fun repeatsAreSaidInWords() {
        val station = RecordingSamples.caz
        fun schedule(repeat: Repeat, weekday: Int = 1) = RecordingSchedule.of(station, now.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }, 7, 0, 60, repeat, zone, now).copy(weekday = weekday)
        assertEquals("Once", RecordingText.repeat(res, schedule(Repeat.Once), Locale.ENGLISH))
        assertEquals("Every day", RecordingText.repeat(res, schedule(Repeat.Daily), Locale.ENGLISH))
        assertEquals("Weekdays", RecordingText.repeat(res, schedule(Repeat.Weekdays), Locale.ENGLISH))
        assertEquals("Every Friday", RecordingText.repeat(res, schedule(Repeat.Weekly, 5), Locale.ENGLISH))
    }

    @Test
    @Config(qualifiers = "tr")
    fun repeatsInTurkish() {
        val locale = Locale.forLanguageTag("tr")
        val schedule = RecordingSchedule.of(RecordingSamples.caz, java.time.LocalDate.of(2026, 10, 14), 7, 0, 60, Repeat.Weekly, zone, now).copy(weekday = 1)
        assertEquals("Her Pazartesi", RecordingText.repeat(res, schedule, locale))
    }

    @Test
    @Config(qualifiers = "en")
    fun whenAScheduleRunsNextOrWhyItDoesNot() {
        val daily = RecordingSchedule.of(RecordingSamples.caz, java.time.LocalDate.of(2026, 10, 14), 7, 30, 60, Repeat.Daily, zone, now, id = 1)
        val next = ScheduleMath.next(daily, now, zone)
        assertEquals("Next: tomorrow at 07:30", RecordingText.next(res, ScheduleRow(daily, next, running = false, conflicts = 0), now, zone, Locale.ENGLISH))
        assertEquals("Recording now", RecordingText.next(res, ScheduleRow(daily, next, running = true, conflicts = 0), now, zone, Locale.ENGLISH))
        assertEquals("Off", RecordingText.next(res, ScheduleRow(daily.copy(enabled = false), next, running = false, conflicts = 0), now, zone, Locale.ENGLISH))
        assertEquals("Done", RecordingText.next(res, ScheduleRow(daily, null, running = false, conflicts = 0), now, zone, Locale.ENGLISH))
    }

    @Test
    @Config(qualifiers = "en")
    fun noticesAreOneLines() {
        assertEquals("“So What” deleted", RecordingText.notice(res, RecordingsNotice.Deleted(listOf(1), "So What")))
        assertEquals("3 tracks deleted", RecordingText.notice(res, RecordingsNotice.Deleted(listOf(1, 2, 3), null)))
        assertEquals("1 partial track deleted", RecordingText.notice(res, RecordingsNotice.PartialDeleted(listOf(1))))
        assertEquals("Recording Sabah Seansı", RecordingText.notice(res, RecordingsNotice.Started("Sabah Seansı")))
        assertEquals("2 entries without a file removed", RecordingText.notice(res, RecordingsNotice.Gone(2)))
        assertTrue(RecordingText.notice(res, RecordingsNotice.Recording(RecordingNotice.Full("Gece Cazı", 3))).contains("3"))
        assertTrue(RecordingText.undoable(RecordingsNotice.Deleted(listOf(1), null)))
        assertTrue(!RecordingText.undoable(RecordingsNotice.Renamed("x")))
    }

    // --- the notifications

    private fun live(station: String, state: SessionState) =
        LiveRecording(RecordingRequest("id-$station", RecordingStation(station, "https://yayin.example/$station", uuid = station)), state)

    @Test
    @Config(qualifiers = "en")
    fun theOngoingNotificationNamesTheTrackAndTheCount() {
        val state = SessionState(phase = Phase.Recording, startedAt = now - 60_000, track = TrackTag("Miles Davis", "So What"), tracksSaved = 2, bytes = 8 * 1024, splits = true)
        val one = RecordingNotifications.text(listOf(live("Caz", state)), words, size)
        assertEquals("Caz", one.title)
        assertEquals("Now: Miles Davis - So What", one.text)
        assertEquals("Saved: 2 · 8 KB", one.detail)
        assertEquals(now - 60_000, one.since)
        val two = RecordingNotifications.text(listOf(live("Caz", state), live("Haber", state.copy(startedAt = now - 120_000))), words, size)
        assertEquals("Recording 2 stations", two.title)
        assertEquals("Caz · Haber", two.text)
        assertEquals(now - 120_000, two.since)
    }

    @Test
    @Config(qualifiers = "en")
    fun theOngoingNotificationSaysWhatIsGoingOn() {
        fun text(state: SessionState) = RecordingNotifications.text(listOf(live("Caz", state)), words, size).text
        assertEquals("Connecting…", text(SessionState(phase = Phase.Connecting)))
        assertEquals("Reconnecting…", text(SessionState(phase = Phase.Reconnecting)))
        assertEquals("No titles: one file", text(SessionState(phase = Phase.Recording, splits = false)))
        assertEquals("Waiting for the first title…", text(SessionState(phase = Phase.Recording, splits = true)))
        assertEquals("Now: Yalnız Başlık", text(SessionState(phase = Phase.Recording, track = TrackTag(null, "Yalnız Başlık"))))
    }

    @Test
    @Config(qualifiers = "en")
    fun endsTheUserShouldKnowAreTold() {
        fun end(end: SessionEnd, scheduled: Boolean = false) =
            RecordingNotifications.endText(EndedRecording(request(if (scheduled) 5 else null), end, SessionState(tracksSaved = 3, bytes = 4096)), words, size)
        assertNull(end(SessionEnd.UserStopped))
        // a run the user asked for by hand and let finish needs no word; a scheduled one does
        assertNull(end(SessionEnd.TimeUp))
        assertEquals("Sabah Seansı recorded" to "Saved: 3 · 4 KB", end(SessionEnd.TimeUp, scheduled = true))
        assertEquals("Recording stopped: low on space", end(SessionEnd.StorageLow)!!.first)
        assertEquals("Recording stopped: size limit", end(SessionEnd.LimitReached)!!.first)
        assertEquals("Recording stopped: no folder", end(SessionEnd.NoFolder)!!.first)
        assertTrue(end(SessionEnd.Failed(503))!!.second.contains("503"))
        assertNotNull(end(SessionEnd.Failed(null)))
        assertTrue(end(SessionEnd.Unsupported(UnsupportedStream.Segmented))!!.second.contains("HLS"))
        assertTrue(end(SessionEnd.Unsupported(UnsupportedStream.Format))!!.second.contains("Ogg"))
    }

    @Test
    @Config(qualifiers = "tr")
    fun endsInTurkish() {
        val ended = EndedRecording(request(), SessionEnd.Unsupported(UnsupportedStream.Segmented), SessionState())
        val (title, text) = RecordingNotifications.endText(ended, words, size)!!
        assertEquals("Sabah Seansı kayda alınamaz", title)
        assertTrue(text.contains("HLS"))
    }
}

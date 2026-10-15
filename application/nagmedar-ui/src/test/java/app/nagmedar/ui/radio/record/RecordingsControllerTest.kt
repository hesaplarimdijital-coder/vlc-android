package app.nagmedar.ui.radio.record

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import java.time.ZonedDateTime

/** S-207: what the Recordings tab does with the tracks, the schedules and the recordings going on. */
@RunWith(AndroidJUnit4::class)
class RecordingsControllerTest {

    private val zone = ZoneId.of("Europe/Istanbul")
    private val now = ZonedDateTime.of(2026, 10, 14, 9, 20, 0, 0, zone).toInstant().toEpochMilli()
    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    private val control = FakeControl()
    private val environment = FakeEnvironment()
    private val player = FakeTrackPlayer()
    private var settings = RecordingSettings()

    private val yesterday = now - 24 * 3_600_000L
    private val first = RecordingSamples.track(1, RecordingSamples.sabah, "s1", yesterday, 0, "Sezen Aksu", "Hadi Bakalım", complete = false)
    private val second = RecordingSamples.track(2, RecordingSamples.sabah, "s1", yesterday, 4, "Tarkan", "Hepsi Senin Mi")
    private val third = RecordingSamples.track(3, RecordingSamples.sabah, "s1", yesterday, 8, "Barış Manço", "Gesi Bağları", complete = false)
    private val jazz = RecordingSamples.track(4, RecordingSamples.caz, "s2", now - 3_600_000L, 0, "Miles Davis", "So What", minutes = 9)

    @After
    fun stop() {
        scope.cancel()
    }

    private fun controller(
        data: FakeRecordingsData,
        parallel: Int = RecordingHub.MAX_PARALLEL,
    ) = RecordingsController(data, control, { settings }, environment, player, scope, { now }, { zone }, Dispatchers.Unconfined, parallel)

    @Test
    fun tracksAreGroupedByStationAndRecordingNewestFirst() {
        val controller = controller(FakeRecordingsData(listOf(first, second, third, jazz)))
        val groups = controller.groups
        assertEquals(listOf("Gece Cazı", "Sabah Seansı"), groups.map { it.station.name })
        val sabah = groups.last()
        assertEquals(3, sabah.tracks)
        assertEquals(listOf(1L, 2L, 3L), sabah.sessions.single().tracks.map { it.id })
        assertEquals(2, sabah.sessions.single().partial)
        // the newest station is open to its newest recording, the others are shut
        assertTrue(RecordingsController.stationKey(groups.first()) in controller.open)
        assertTrue(RecordingsController.sessionKey(groups.first().sessions.first()) in controller.open)
        assertFalse(RecordingsController.stationKey(sabah) in controller.open)
        controller.toggle(RecordingsController.stationKey(sabah))
        assertTrue(RecordingsController.stationKey(sabah) in controller.open)
        controller.toggle(RecordingsController.stationKey(sabah))
        assertFalse(RecordingsController.stationKey(sabah) in controller.open)
    }

    @Test
    fun nothingRecordedYetIsEmptyNotLoading() {
        val controller = controller(FakeRecordingsData())
        assertEquals(emptyList<RecordedTrack>(), controller.tracks)
        assertTrue(controller.groups.isEmpty())
    }

    @Test
    fun aDeletedTrackLeavesTheListAndComesBackWithUndo() {
        val data = FakeRecordingsData(listOf(second, jazz))
        val controller = controller(data)
        controller.delete(listOf(jazz))
        val notice = controller.notice as RecordingsNotice.Deleted
        assertEquals(listOf(4L), notice.ids)
        assertEquals("So What", notice.name)
        assertEquals(listOf(2L), controller.tracks!!.map { it.id })
        assertTrue(RecordingText.undoable(notice))
        controller.undo(notice)
        assertEquals(setOf(2L, 4L), controller.tracks!!.map { it.id }.toSet())
    }

    @Test
    fun aFailingDatabaseIsSaidNotThrown() {
        val data = FakeRecordingsData(listOf(second)).apply { failNextTrash = true }
        val controller = controller(data)
        controller.delete(listOf(second))
        assertEquals(RecordingsNotice.Failed, controller.notice)
        assertEquals(listOf(2L), controller.tracks!!.map { it.id })
    }

    @Test
    fun keepingOnlyCompleteTracksDeletesThePartialOnes() {
        val data = FakeRecordingsData(listOf(first, second, third))
        val controller = controller(data)
        val session = controller.groups.single().sessions.single()
        controller.keepComplete(session)
        val notice = controller.notice as RecordingsNotice.PartialDeleted
        assertEquals(setOf(1L, 3L), notice.ids.toSet())
        assertEquals(listOf(2L), controller.tracks!!.map { it.id })
        controller.undo(notice)
        assertEquals(3, controller.tracks!!.size)
    }

    @Test
    fun aRecordingOfCompleteTracksHasNothingPartialToDelete() {
        val controller = controller(FakeRecordingsData(listOf(second)))
        controller.keepComplete(controller.groups.single().sessions.single())
        assertEquals(RecordingsNotice.NothingPartial, controller.notice)
        assertEquals(1, controller.tracks!!.size)
    }

    @Test
    fun renamingKeepsTheExtensionAndCleansTheName() {
        val data = FakeRecordingsData(listOf(second))
        val controller = controller(data)
        controller.rename(second, "Tarkan: Hepsi/Senin Mi?")
        val renamed = controller.notice as RecordingsNotice.Renamed
        assertTrue(renamed.name.endsWith(".mp3"))
        assertFalse(renamed.name.any { it in "/:?" })
        assertEquals(renamed.name, controller.tracks!!.single().fileName)
    }

    @Test
    fun anEmptyOrRefusedRenameIsSaid() {
        val data = FakeRecordingsData(listOf(second))
        val controller = controller(data)
        controller.rename(second, "  ")
        assertEquals(RecordingsNotice.RenameFailed, controller.notice)
        controller.noticed()
        assertNull(controller.notice)
        data.renameFails = true
        controller.rename(second, "Başka")
        assertEquals(RecordingsNotice.RenameFailed, controller.notice)
        // the same name is no change at all
        controller.noticed()
        data.renameFails = false
        controller.rename(second, second.fileName.removeSuffix(".mp3"))
        assertNull(controller.notice)
    }

    @Test
    fun playingSkipsTracksWhoseFilesAreGone() {
        val data = FakeRecordingsData(listOf(first, second, third), gone = setOf(first.fileUri))
        val controller = controller(data)
        assertEquals(setOf(1L), controller.missing)
        val session = controller.groups.single().sessions.single()
        controller.play(session, third)
        assertEquals(listOf(2L, 3L), player.plays.single().first.map { it.id })
        assertEquals(1, player.plays.single().second)
        // a track that is gone does not play
        controller.play(session, first)
        assertEquals(1, player.plays.size)
        controller.playAll(session)
        assertEquals(0, player.plays.last().second)
        assertEquals(2, player.plays.last().first.size)
    }

    @Test
    fun trackWhoseFileIsGoneCanBeTakenOutOfTheList() {
        val data = FakeRecordingsData(listOf(first, second), gone = setOf(first.fileUri))
        val controller = controller(data)
        controller.forgetMissing()
        assertEquals(RecordingsNotice.Gone(1), controller.notice)
        assertEquals(listOf(2L), controller.tracks!!.map { it.id })
    }

    @Test
    fun theTabWhenShownPurgesAndCatchesUpOnce() {
        val data = FakeRecordingsData(listOf(second))
        val controller = controller(data)
        controller.opened()
        controller.opened()
        assertEquals(1, environment.caughtUp)
    }

    @Test
    fun theRoomLeftSaysWhenRecordingIsHeldBack() {
        environment.free = 200L * StorageLimits.MB
        val controller = controller(FakeRecordingsData(listOf(second)))
        controller.refresh()
        val storage = controller.storage!!
        assertEquals(200L * StorageLimits.MB, storage.freeBytes)
        assertTrue(storage.verdict is StorageVerdict.Stop || storage.verdict is StorageVerdict.Warn)
        environment.free = 40L * StorageLimits.GB
        controller.refresh()
        assertEquals(StorageVerdict.Ok, controller.storage!!.verdict)
        assertTrue(controller.storage!!.fraction in 0f..1f)
    }

    @Test
    fun theFolderAndTheAlarmPermissionAreReadAgain() {
        val controller = controller(FakeRecordingsData())
        environment.place = FolderState.Missing("SD card/Radio")
        environment.exact = false
        controller.refresh()
        assertEquals(FolderState.Missing("SD card/Radio"), controller.folder)
        assertFalse(controller.folder.usable)
        assertFalse(controller.exactAlarms)
    }

    @Test
    fun recordingAStationAsksTheControl() {
        val controller = controller(FakeRecordingsData())
        controller.record(RecordingSamples.sabah)
        assertEquals(listOf(RecordingSamples.sabah), control.started)
        assertEquals(RecordingsNotice.Started("Sabah Seansı"), controller.notice)
        controller.stop("x")
        controller.stopAll()
        assertEquals(listOf("x"), control.stopped)
        assertEquals(1, control.stoppedAll)
    }

    @Test
    fun theRecordingsGoingOnAreFollowed() {
        val controller = controller(FakeRecordingsData())
        assertFalse(controller.recording(RecordingSamples.sabah.key))
        control.live.value = listOf(LiveRecording(RecordingRequest("a", RecordingSamples.sabah), SessionState(phase = Phase.Recording)))
        assertTrue(controller.recording(RecordingSamples.sabah.key))
        assertEquals(1, controller.live.size)
    }

    @Test
    fun whatTheControlSaysOfARefusedStartIsPassedOn() {
        val controller = controller(FakeRecordingsData())
        control.notices.tryEmit(RecordingNotice.Merged("Sabah Seansı"))
        assertEquals(RecordingsNotice.Recording(RecordingNotice.Merged("Sabah Seansı")), controller.notice)
    }

    // --- schedules

    @Test
    fun theEditorOffersTheNextFullHour() {
        val controller = controller(FakeRecordingsData())
        controller.schedule(RecordingSamples.caz)
        val draft = controller.editor!!
        assertEquals(RecordingSamples.caz, draft.station)
        assertEquals(10, draft.hour)
        assertEquals(0, draft.minute)
        assertEquals(11, draft.endHour)
        controller.change(draft.copy(durationMinutes = 90))
        assertEquals(11, controller.editor!!.endHour)
        assertEquals(30, controller.editor!!.endMinute)
        controller.closeEditor()
        assertNull(controller.editor)
    }

    @Test
    fun anEndTimeBecomesALengthAcrossMidnight() {
        val draft = ScheduleDraft(station = RecordingSamples.caz, hour = 23, minute = 30).endingAt(1, 0)
        assertEquals(90, draft.durationMinutes)
        assertEquals(1, draft.endHour)
        // an end equal to the start is the shortest recording, not none
        assertEquals(1, draft.endingAt(23, 30).durationMinutes)
    }

    @Test
    fun aDraftWithoutAStationIsNotReady() {
        val draft = ScheduleDraft()
        assertFalse(draft.ready)
        assertNull(draft.toSchedule(now, zone))
        val controller = controller(FakeRecordingsData())
        controller.change(draft)
        controller.saveSchedule()
        assertNotNull(controller.editor)
    }

    @Test
    fun savingASchedulePutsItInTheDatabaseAndSetsItsAlarm() {
        val data = FakeRecordingsData()
        val controller = controller(data)
        controller.schedule(RecordingSamples.caz)
        controller.change(controller.editor!!.copy(repeat = Repeat.Daily, durationMinutes = 120))
        controller.saveSchedule()
        assertNull(controller.editor)
        val saved = environment.saved.single()
        assertTrue(saved.id > 0)
        assertEquals(Repeat.Daily, saved.repeat)
        assertEquals(120, saved.durationMinutes)
        val row = controller.schedules.single()
        assertEquals(10, ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(row.next!!.start), zone).hour)
        assertFalse(row.running)
    }

    @Test
    fun aOneOffForAnHourThatPassedIsForTomorrow() {
        val draft = ScheduleDraft(station = RecordingSamples.caz, hour = 8, minute = 0, repeat = Repeat.Once)
        val schedule = draft.toSchedule(now, zone)!!
        val start = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(schedule.startAt), zone)
        assertEquals(15, start.dayOfMonth)
        assertEquals(8, start.hour)
    }

    @Test
    fun aWeeklyScheduleKeepsItsWeekday() {
        val draft = ScheduleDraft(station = RecordingSamples.caz, hour = 21, minute = 0, repeat = Repeat.Weekly, weekday = 5)
        assertEquals(5, draft.toSchedule(now, zone)!!.weekday)
    }

    @Test
    fun schedulesThatOverlapAreSaidInTheEditor() {
        val other = RecordingSchedule.of(RecordingSamples.sabah, now.date(), 10, 0, 60, Repeat.Daily, zone, now, id = 7)
        val controller = controller(FakeRecordingsData(schedules = listOf(other)), parallel = 1)
        controller.schedule(RecordingSamples.caz)
        val check = controller.check(controller.editor!!)
        assertEquals(listOf(7L), check.conflicts.map { it.id })
        assertTrue(check.tooMany)
        // at another hour they do not meet
        val apart = controller.check(controller.editor!!.copy(hour = 14))
        assertTrue(apart.conflicts.isEmpty())
        assertFalse(apart.tooMany)
    }

    @Test
    fun editingASchedulesDoesNotMeetItself() {
        val own = RecordingSchedule.of(RecordingSamples.sabah, now.date(), 10, 0, 60, Repeat.Daily, zone, now, id = 7)
        val controller = controller(FakeRecordingsData(schedules = listOf(own)))
        controller.edit(own)
        assertEquals(7L, controller.editor!!.id)
        val check = controller.check(controller.editor!!)
        assertTrue(check.conflicts.isEmpty())
        assertFalse(check.tooMany)
    }

    @Test
    fun aDeletedScheduleIsTakenBackWithItsAlarm() {
        val own = RecordingSchedule.of(RecordingSamples.sabah, now.date(), 10, 0, 60, Repeat.Daily, zone, now, id = 7)
        val data = FakeRecordingsData(schedules = listOf(own))
        val controller = controller(data)
        controller.deleteSchedule(own)
        assertEquals(listOf(7L), environment.removed)
        assertTrue(controller.schedules.isEmpty())
        val notice = controller.notice as RecordingsNotice.ScheduleDeleted
        assertTrue(RecordingText.undoable(notice))
        controller.undo(notice)
        assertEquals(1, controller.schedules.size)
        assertEquals(RecordingSamples.sabah, environment.saved.single().station)
    }

    @Test
    fun aScheduleCanBeSwitchedOffAndOn() {
        val own = RecordingSchedule.of(RecordingSamples.sabah, now.date(), 10, 0, 60, Repeat.Daily, zone, now, id = 7)
        val controller = controller(FakeRecordingsData(schedules = listOf(own)))
        controller.enable(own, false)
        assertFalse(controller.schedules.single().schedule.enabled)
        assertFalse(environment.saved.last().enabled)
        controller.enable(own, true)
        assertTrue(controller.schedules.single().schedule.enabled)
    }

    @Test
    fun aScheduleRunningNowIsMarked() {
        val own = RecordingSchedule.of(RecordingSamples.sabah, now.date(), 9, 0, 60, Repeat.Daily, zone, now, id = 7)
        val controller = controller(FakeRecordingsData(schedules = listOf(own)))
        control.live.value = listOf(LiveRecording(RecordingRequest("run", RecordingSamples.sabah, scheduleId = 7), SessionState(phase = Phase.Recording)))
        assertTrue(controller.schedules.single().running)
    }

    private fun Long.date() = java.time.Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
}

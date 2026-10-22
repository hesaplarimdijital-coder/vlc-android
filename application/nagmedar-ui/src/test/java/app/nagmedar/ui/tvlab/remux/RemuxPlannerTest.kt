package app.nagmedar.ui.tvlab.remux

import app.nagmedar.lab.AudioTech
import app.nagmedar.lab.DolbyVisionMode
import app.nagmedar.lab.StreamRef
import app.nagmedar.lab.StreamType
import app.nagmedar.lab.SubtitleTech
import app.nagmedar.lab.VideoTech
import app.nagmedar.ui.tvlab.BuiltInProfiles
import app.nagmedar.ui.tvlab.ProfileCheck
import app.nagmedar.ui.tvlab.sampleTech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * S-1205, S-1207: the planner's draft of the sample film for the LG B4, which plays neither its
 * TrueHD nor its DTS-HD (the default): Dolby Vision converted, an E-AC-3 track beside both, the
 * one beside the DTS-HD the default. Undoing the compatibility tracks, in the dialog or with the
 * batch's switch, gives the audio default back: a film is never written without one.
 */
class RemuxPlannerTest {

    private val names = object : ProfileCheck.StreamNames {
        override fun video(video: VideoTech) = video.codec

        override fun audio(track: AudioTech) = "${track.codec} ${track.language}"

        override fun subtitles(tracks: List<SubtitleTech>) = tracks.joinToString { it.codec }
    }

    private val b4 = BuiltInProfiles.lgB4Usb
    private val trueHd = StreamRef(StreamType.Audio, 0)
    private val dts = StreamRef(StreamType.Audio, 1)

    private fun draft(options: PlanOptions = PlanOptions()): PlanDraft {
        val tech = sampleTech()
        return PlanDraft.of(tech, ProfileCheck(names).check(tech, b4, null), b4, names, "Kuzey Işığı (remux)", options)
    }

    private fun PlanDraft.track(ref: StreamRef) = audio.first { it.ref == ref }

    private fun PlanDraft.compatibility() = suggestions.first { it.kind == SuggestionKind.Compatibility }

    /** The default flag the plan writes for the source's DTS-HD: null leaves the source's own (the default). */
    private fun PlanDraft.dtsDefault() = plan().streams.first { it.source == dts && it.encode == null }.isDefault

    @Test
    fun theDeviceStartsWithTheTrackBesideTheDefault() {
        val draft = draft()
        assertEquals(listOf(trueHd, dts), draft.added.map { it.source })
        assertEquals(dts, draft.added.single { it.makeDefault }.source)
        assertTrue("no other audio track is the default", draft.audio.none { it.isDefault })
        assertEquals(false, draft.dtsDefault())
        assertEquals(DolbyVisionMode.ConvertToProfile81, draft.plan().dolbyVision)
    }

    @Test
    fun undoingTheCompatibilityTracksGivesTheDefaultBack() {
        val draft = draft()
        draft.apply(draft.compatibility(), false)
        assertTrue(draft.added.isEmpty())
        assertTrue(draft.track(dts).isDefault)
        assertNull("the source's own default flag, written as it is", draft.dtsDefault())
        assertTrue(draft.plan().streams.none { it.encode != null })
        // applied again: the added track takes the flag again, and gives it back again
        draft.apply(draft.compatibility(), true)
        assertEquals(false, draft.dtsDefault())
        draft.apply(draft.compatibility(), false)
        assertTrue(draft.track(dts).isDefault)
    }

    @Test
    fun theBatchsSwitchGivesTheDefaultBackToo() {
        val draft = draft()
        draft.applyOptions(PlanOptions(addCompatibility = false))
        assertTrue(draft.added.isEmpty())
        assertTrue(draft.track(dts).isDefault)
        assertFalse(draft.track(trueHd).isDefault)
        draft.applyOptions(PlanOptions())
        assertEquals(dts, draft.added.single { it.makeDefault }.source)
        assertTrue(draft.audio.none { it.isDefault })
        // made without them from the start, the plan keeps the source's default as it is
        assertNull(draft(PlanOptions(addCompatibility = false)).dtsDefault())
    }

    @Test
    fun removingTheDefaultAddedTrackOrItsFlagGivesTheDefaultBack() {
        val draft = draft()
        draft.setAddedDefault(draft.added.single { it.makeDefault }, false)
        assertTrue(draft.track(dts).isDefault)
        assertEquals(2, draft.added.size)
        // made the default again, then taken off the plan
        val beside = draft.added.first { it.source == dts }
        draft.setAddedDefault(beside, true)
        assertFalse(draft.track(dts).isDefault)
        draft.removeAdded(beside)
        assertTrue(draft.track(dts).isDefault)
        // the other added track was never the default: removing it changes no flag
        draft.removeAdded(draft.added.single())
        assertTrue(draft.track(dts).isDefault)
        assertTrue(draft.audio.count { it.isDefault } == 1)
    }

    @Test
    fun aDefaultTheUserChoseIsKept() {
        val draft = draft()
        val beside = draft.added.single { it.makeDefault }
        draft.setDefault(draft.track(trueHd), true)
        assertFalse(beside.makeDefault)
        draft.removeAdded(beside)
        draft.apply(draft.compatibility(), false)
        assertTrue(draft.track(trueHd).isDefault)
        assertFalse("the user's choice is not undone by the old default", draft.track(dts).isDefault)
    }
}

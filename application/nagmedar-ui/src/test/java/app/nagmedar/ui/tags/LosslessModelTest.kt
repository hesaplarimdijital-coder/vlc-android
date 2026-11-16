package app.nagmedar.ui.tags

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.tags.ContentRef
import app.nagmedar.tags.quality.LosslessCheckResult
import app.nagmedar.tags.quality.LosslessVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 1.4.0: one FLAC or WAV file opened is looked at for whether it is what it says, and the library told. */
@RunWith(AndroidJUnit4::class)
class LosslessModelTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val asked = mutableListOf<ContentRef>()
    private val report = losslessReport()
    private var answer: LosslessCheckResult = LosslessCheckResult.Checked(report)
    private val fake = FakeTagFiles(scope = scope, lossless = { ref -> asked += ref; answer })

    @After
    fun tearDown() {
        TagSessions.forget()
        scope.cancel()
    }

    private fun editor(vararg names: String): TagEditorModel {
        names.forEach { fake.tags.put(sampleRef(it), "TITLE" to listOf(it)) }
        return TagEditorModel(names.map { sampleTarget(it) }, fake.files).also { it.load() }
    }

    @Test
    fun aFlacIsCheckedAndTheLibraryToldTheVerdict() {
        val editor = editor("02 Garip.flac")
        assertEquals(LosslessCheckState.Done(report), editor.lossless)
        assertEquals(listOf(sampleRef("02 Garip.flac")), asked)
        assertEquals(listOf(sampleRef("02 Garip.flac") to LosslessVerdict.Transcoded), fake.verdicts)
    }

    @Test
    fun aFileWhoseAudioCannotBeReadSaysSoAndALossyOneIsNotLookedAt() {
        answer = LosslessCheckResult.Unreadable
        val unreadable = editor("03.wav")
        assertEquals(LosslessCheckState.Failed, unreadable.lossless)
        assertEquals(listOf(sampleRef("03.wav") to null), fake.verdicts)
        TagSessions.forget()

        val mp3 = editor("04 Haliç.mp3")
        assertEquals(LosslessCheckState.None, mp3.lossless)
        assertEquals(listOf(sampleRef("03.wav")), asked)
    }

    @Test
    fun severalFilesAreNotChecked() {
        val editor = editor("01.flac", "02.flac")
        assertEquals(LosslessCheckState.None, editor.lossless)
        assertEquals(emptyList<ContentRef>(), asked)
    }
}

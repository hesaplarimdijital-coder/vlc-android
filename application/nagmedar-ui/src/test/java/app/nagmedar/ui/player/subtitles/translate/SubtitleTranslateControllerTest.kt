package app.nagmedar.ui.player.subtitles.translate

import app.nagmedar.translate.TranslationDirection
import app.nagmedar.translate.TranslationModel
import app.nagmedar.ui.player.subtitles.tools.ToolsFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The models and the jobs as nothing has happened yet; the engine as [engine] says, its asks counted. */
private class QuietTranslationService(private val engine: Boolean) : TranslationService {
    var asked = 0

    override suspend fun engineAvailable(): Boolean {
        asked++
        return engine
    }

    override fun model(model: TranslationModel): Flow<ModelStatus> = flowOf(ModelStatus.Missing)

    override fun online() = true

    override fun download(model: TranslationModel) = Unit

    override fun cancelDownload(model: TranslationModel) = Unit

    override suspend fun delete(model: TranslationModel) = Unit

    override suspend fun start(id: String, bytes: ByteArray, source: ToolsFile, outputName: String, direction: TranslationDirection) = true

    override fun cancel() = Unit

    override fun job(): Flow<JobInfo?> = flowOf(null)
}

/**
 * S-710: the translation's window asks whether the engine is there when it opens, off the main
 * thread (its library reads the APK as it loads), not when it is made on the main thread.
 */
class SubtitleTranslateControllerTest {

    private val scope = CoroutineScope(Dispatchers.Unconfined)

    @Test
    fun theEngineIsAskedWhenTheWindowOpensNotWhenItIsMade() {
        val service = QuietTranslationService(engine = false)
        val translation = SubtitleTranslateController(service, scope, "tr")
        assertEquals(0, service.asked)
        assertTrue(translation.state.engine)

        translation.attach(null)
        assertEquals(1, service.asked)
        assertFalse(translation.state.engine)
    }

    @Test
    fun anEngineThatIsThereStaysSo() {
        val translation = SubtitleTranslateController(QuietTranslationService(engine = true), scope, "tr")
        translation.attach(null)
        assertTrue(translation.state.engine)
    }
}

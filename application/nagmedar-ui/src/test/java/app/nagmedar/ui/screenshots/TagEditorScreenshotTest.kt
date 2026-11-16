package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.tags.EmbeddedPicture
import app.nagmedar.tags.TagField
import app.nagmedar.ui.tags.LocalTagLookups
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.nagmedar.ui.tags.FakeTagFiles
import app.nagmedar.ui.tags.LocalTagFiles
import app.nagmedar.ui.tags.TagEditorPage
import app.nagmedar.ui.tags.TagEditorPane
import app.nagmedar.ui.tags.TagEditorSection
import app.nagmedar.ui.tags.TagFiles
import app.nagmedar.ui.tags.TagSessions
import app.nagmedar.ui.tags.losslessReport
import app.nagmedar.tags.quality.LosslessCheckResult
import app.nagmedar.ui.tags.fakeTagLookups
import app.nagmedar.ui.tags.musicBrainzMatch
import app.nagmedar.ui.tags.rememberTagEditor
import app.nagmedar.ui.tags.sampleRef
import app.nagmedar.ui.tags.sampleTarget
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream

/**
 * 1.2.0, for approval: the tag editor (Metamatik's) as the music panel's "Düzenle" in DeX, a changed field and its
 * "değişti"; three tracks together in the dark; the phone's page; Settings › Etiket düzenleyici.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TagEditorScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val fake = FakeTagFiles(scope = scope)

    @After
    fun tearDown() {
        TagSessions.forget()
        scope.cancel()
    }

    private fun songs() {
        val cover = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFB5482E.toInt()) }
        val bytes = ByteArrayOutputStream().also { cover.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        fake.tags.put(
            sampleRef("01 Bir Sana Bir Bana.flac"),
            "TITLE" to listOf("Bir Sana Bir Bana"),
            "ARTIST" to listOf("Moğollar", "Cahit Berkay"),
            "ALBUM" to listOf("Moğollar"),
            "ALBUMARTIST" to listOf("Moğollar"),
            "DATE" to listOf("1971"),
            "GENRE" to listOf("Anadolu rock"),
            "TRACKNUMBER" to listOf("1"),
            "TRACKTOTAL" to listOf("10"),
            "COMPOSER" to listOf("Cahit Berkay"),
            "REPLAYGAIN_TRACK_GAIN" to listOf("-6.20 dB"),
            "MUSICBRAINZ_TRACKID" to listOf("8f1c2d3e-0000-4000-8000-000000000001"),
            pictures = listOf(EmbeddedPicture(bytes, "image/png")),
        )
        fake.tags.put(sampleRef("02 Garip.flac"), "TITLE" to listOf("Garip"), "ARTIST" to listOf("Moğollar"), "ALBUM" to listOf("Moğollar"), "GENRE" to listOf("Rock"), "TRACKNUMBER" to listOf("2"))
        fake.tags.put(sampleRef("03 Haliç.mp3"), "TITLE" to listOf("Haliç'te Güneşin Batışı"), "ARTIST" to listOf("Moğollar"), "TRACKNUMBER" to listOf("3/10"))
    }

    /** 1.3.0: three files on two discs of one album, one with its artist garbled, for the batch's tools. */
    private fun twoDiscs() {
        fake.tags.put(sampleRef("01 Bir Sana Bir Bana.flac"), "TITLE" to listOf("Bir Sana Bir Bana"), "ARTIST" to listOf("Moğollar"), "ALBUM" to listOf("Moğollar CD1"), "ALBUMARTIST" to listOf("Moğollar"))
        fake.tags.put(sampleRef("02 Garip.flac"), "TITLE" to listOf("Garip"), "ARTIST" to listOf("MoÄŸollar"), "ALBUM" to listOf("Moğollar CD2"), "ALBUMARTIST" to listOf("Moğollar"))
        fake.tags.put(sampleRef("03 Haliç.mp3"), "TITLE" to listOf("Haliç"), "ARTIST" to listOf("Moğollar"), "ALBUM" to listOf("Moğollar CD2"), "ALBUMARTIST" to listOf("Moğollar"))
    }

    /** The head's cover is decoded off the main thread: drawn only once it is there, else the shot is now with it, now without. */
    private fun waitForCover() {
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("tag-head-picture").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun capture(
        name: String,
        theme: ThemeMode,
        input: InputMode,
        data: () -> Unit = { songs() },
        withCover: Boolean = false,
        files: TagFiles = fake.files,
        waitFor: String? = null,
        content: @Composable () -> Unit,
    ) {
        data()
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = theme, inputMode = input) {
                CompositionLocalProvider(LocalTagFiles provides files) {
                    Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) { content() }
                }
            }
        }
        compose.waitForIdle()
        if (withCover) waitForCover()
        if (waitFor != null) compose.waitUntil(10_000) { compose.onAllNodesWithTag(waitFor).fetchSemanticsNodes().isNotEmpty() }
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    /** The panel's width beside the page, as the music view has it. */
    @Composable
    private fun Panel(content: @Composable () -> Unit) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f))
            Box(Modifier.width(376.dp).fillMaxHeight().background(NagTheme.colors.surface)) { content() }
        }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
    fun panelDex() = capture("tag-editor-panel-dex-light", ThemeMode.Light, InputMode.Pointer, withCover = true) {
        Panel {
            val target = sampleTarget("01 Bir Sana Bir Bana.flac")
            val model = rememberTagEditor(listOf(target))
            LaunchedEffect(model) { model.set(TagField.Album.key, listOf("Moğollar (1971)")) }
            TagEditorPane(listOf(target))
        }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
    fun manyDexDark() = capture("tag-editor-many-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
        Panel {
            TagEditorPane(listOf(sampleTarget("01 Bir Sana Bir Bana.flac"), sampleTarget("02 Garip.flac"), sampleTarget("03 Haliç.mp3")))
        }
    }

    /** 1.3.0: the batch's tools: the two discs made one album, the names read by a pattern, the garbled artist. */
    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
    fun batchToolsDex() = capture("tag-editor-batch-dex-light", ThemeMode.Light, InputMode.Pointer, data = { twoDiscs() }) {
        Panel {
            val targets = listOf(sampleTarget("01 Bir Sana Bir Bana.flac"), sampleTarget("02 Garip.flac"), sampleTarget("03 Haliç.mp3"))
            val model = rememberTagEditor(targets)
            LaunchedEffect(model) {
                model.choosePattern("{track} {title}")
                model.readNames(true)
            }
            TagEditorPane(targets)
        }
    }

    /** 1.4.0: a FLAC made from an MP3, as the editor's card says it, with its spectrum ending in a wall at 16 kHz. */
    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
    fun losslessDex() {
        val checked = FakeTagFiles(fake.tags, scope, lossless = { LosslessCheckResult.Checked(losslessReport()) })
        capture("tag-editor-lossless-dex-light", ThemeMode.Light, InputMode.Pointer, withCover = true, files = checked.files, waitFor = "tag-lossless-chart") {
            Panel { TagEditorPane(listOf(sampleTarget("01 Bir Sana Bir Bana.flac"))) }
        }
    }

    @Test @Config(qualifiers = "tr-w390dp-h844dp-hdpi")
    fun phonePage() = capture("tag-editor-phone-light", ThemeMode.Light, InputMode.Touch, withCover = true) {
        TagEditorPage(listOf(sampleTarget("01 Bir Sana Bir Bana.flac")), onClose = {})
    }

    @Test @Config(qualifiers = "tr-w1280dp-h900dp-mdpi-keysexposed-qwerty")
    fun lookupComparisonDex() {
        val tools = fakeTagLookups(musicBrainzMatch())
        songs()
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Light, inputMode = InputMode.Pointer) {
                CompositionLocalProvider(LocalTagFiles provides fake.files, LocalTagLookups provides tools) {
                    Box(Modifier.fillMaxSize().background(NagTheme.colors.background)) {
                        Panel { TagEditorPane(listOf(sampleTarget("01 Bir Sana Bir Bana.flac"))) }
                    }
                }
            }
        }
        compose.waitForIdle()
        waitForCover()
        compose.onNodeWithTag("tag-find-info").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("tag-lookup-result-0").performClick()
        compose.waitForIdle()
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { drawWindows(Canvas(bitmap)) }
        ShellScreenshotTest.save("tag-lookup-compare-dex-light", bitmap)
    }

    /** Every window up, the activity's first, each where it is: a dialog is a window of its own. */
    private fun drawWindows(canvas: Canvas) {
        val global = Class.forName("android.view.WindowManagerGlobal").getMethod("getInstance").invoke(null)
        val roots = global.javaClass.getDeclaredField("mRoots").apply { isAccessible = true }.get(global) as List<*>
        roots.forEach { root ->
            val view = root?.javaClass?.getMethod("getView")?.invoke(root) as? android.view.View ?: return@forEach
            val at = IntArray(2)
            view.getLocationOnScreen(at)
            canvas.save()
            canvas.translate(at[0].toFloat(), at[1].toFloat())
            view.draw(canvas)
            canvas.restore()
        }
    }

    @Test @Config(qualifiers = "tr-w1280dp-h800dp-mdpi-keysexposed-qwerty")
    fun settingsDex() = capture("settings-tags-dex-dark", ThemeMode.Dark, InputMode.Pointer) {
        Box(Modifier.padding(top = 16.dp), contentAlignment = Alignment.TopStart) { TagEditorSection(backups = 37 to 18_400_000L) }
    }
}

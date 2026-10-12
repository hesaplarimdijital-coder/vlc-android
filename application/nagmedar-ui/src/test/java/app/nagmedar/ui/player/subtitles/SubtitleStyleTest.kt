package app.nagmedar.ui.player.subtitles

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_BOLD
import org.videolan.tools.KEY_SUBTITLES_COLOR
import org.videolan.tools.KEY_SUBTITLES_FONT
import org.videolan.tools.KEY_SUBTITLES_OUTLINE
import org.videolan.tools.KEY_SUBTITLES_OUTLINE_SIZE
import org.videolan.tools.KEY_SUBTITLES_SHADOW
import org.videolan.tools.KEY_SUBTITLES_SIZE

/** The mockups' look: the thin preset, its colour, its box. */
internal val sampleLook = SubtitlePreset.Thin.over(SubtitleStyle())

internal class FakeStyleStore(var kept: SubtitleStyle = SubtitleStyle()) : SubtitleStyleStore {
    var saves = 0

    override fun load(): SubtitleStyle = kept

    override fun save(style: SubtitleStyle) {
        kept = style
        saves++
    }
}

internal class FakeNativeStyle(override val live: Boolean) : NativeSubtitleStyle {
    override val supportsLayout: Boolean = live
    val applied = mutableListOf<SubtitleStyle>()

    override fun apply(style: SubtitleStyle) {
        applied += style
    }
}

/**
 * S-705: the subtitles' look, a preset or by hand, kept for every film in the app's settings and
 * in VLC's, from which libVLC starts (the size as VLC's text scale, to the percent); the classic
 * settings' look is read the first time. Until the native side takes a look while a film plays,
 * it waits for libVLC to start again. The section shows each change in its preview.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "tr-w1280dp-h800dp-mdpi")
class SubtitleStyleTest {

    @get:Rule
    val compose = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("style-test", Context.MODE_PRIVATE)

    @After
    fun clear() {
        prefs.edit().clear().commit()
        LibVlcRestart.pending = false
    }

    @Test
    fun aLookGoesIntoTheSettingsAndComesBack() {
        val look = SubtitleStyle(SubtitleFont.Serif, bold = true, sizePx = 44, outlinePx = 2, shadow = false, backgroundPercent = 60, colourArgb = SubtitleColour.Yellow.argb, raisePx = 40, inBlackBars = true, ass = AssStyles.Ours)
        assertEquals(look, SubtitleStyleCodec.decode(SubtitleStyleCodec.encode(look)))
        // unknown keys skipped, missing ones as VLC has them, nonsense left out
        assertEquals(SubtitleStyle(sizePx = 44, colourArgb = SubtitleColour.Cyan.argb), SubtitleStyleCodec.decode("size=44;colour=ff7fe8ff;later=1;outline=5"))
        assertNull(SubtitleStyleCodec.decode(""))
    }

    @Test
    fun aPresetChangesTheLookAndLeavesWhereTheSubtitlesStand() {
        val placed = SubtitleStyle(raisePx = 40, inBlackBars = true)
        val thin = SubtitlePreset.Thin.over(placed)
        assertEquals(44, thin.sizePx)
        assertEquals(2, thin.outlinePx)
        assertEquals(40, thin.raisePx)
        assertTrue(thin.inBlackBars)
        assertEquals(SubtitlePreset.Thin, SubtitlePreset.of(thin))
        assertEquals(SubtitlePreset.Vlc, SubtitlePreset.of(SubtitleStyle()))
        assertNull("changed by hand", SubtitlePreset.of(thin.copy(sizePx = 46)))
    }

    @Test
    fun vlcsSettingsGetTheLookLibVlcStartsWith() {
        val store = PrefsSubtitleStyleStore(prefs)
        store.save(SubtitleStyle(SubtitleFont.Mono, bold = true, sizePx = 44, outlinePx = 0, shadow = false, backgroundPercent = 60, colourArgb = SubtitleColour.Yellow.argb))
        assertEquals("24.615", prefs.getString(KEY_SUBTITLES_SIZE, null))
        assertTrue(prefs.getBoolean(KEY_SUBTITLES_BOLD, false))
        assertEquals(SubtitleColour.Yellow.argb, prefs.getInt(KEY_SUBTITLES_COLOR, 0))
        assertFalse(prefs.getBoolean(KEY_SUBTITLES_OUTLINE, true))
        assertFalse(prefs.getBoolean(KEY_SUBTITLES_SHADOW, true))
        assertTrue(prefs.getBoolean(KEY_SUBTITLES_BACKGROUND, false))
        assertEquals(153, prefs.getInt(KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, 0))
        assertEquals("monospace", prefs.getString(KEY_SUBTITLES_FONT, null))
        // the system's own family: no option
        store.save(SubtitleStyle())
        assertNull(prefs.getString(KEY_SUBTITLES_FONT, null))
        assertEquals(SubtitleStyle(), store.load())
    }

    @Test
    fun everySizeComesBackAsItsTextScale() {
        // VLCOptions: --sub-text-scale is 1 600 over the size setting, which libVLC cuts to a whole number
        for (px in SubtitleStyle.MIN_SIZE_PX..SubtitleStyle.MAX_SIZE_PX step SubtitleStyle.SIZE_STEP_PX) {
            val style = SubtitleStyle(sizePx = px)
            val setting = PrefsSubtitleStyleStore.relativeSize(style).toFloat()
            assertEquals("$px px", style.textScale, (1600 / setting).toInt())
        }
        assertEquals(101, SubtitleStyle().textScale)
        assertEquals(65, SubtitleStyle(sizePx = 44).textScale)
    }

    @Test
    fun theClassicSettingsLookIsReadTheFirstTime() {
        prefs.edit()
            .putString(KEY_SUBTITLES_SIZE, "13")
            .putBoolean(KEY_SUBTITLES_OUTLINE, false)
            .putString(KEY_SUBTITLES_OUTLINE_SIZE, "6")
            .putBoolean(KEY_SUBTITLES_BACKGROUND, true)
            .putInt(KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, 128)
            .putInt(KEY_SUBTITLES_COLOR, SubtitleColour.Yellow.argb)
            .commit()
        val look = PrefsSubtitleStyleStore(prefs).load()
        assertEquals(84, look.sizePx)
        assertEquals(0, look.outlinePx)
        assertEquals(50, look.backgroundPercent)
        assertEquals(SubtitleColour.Yellow, look.colour)
        assertTrue(look.shadow)
    }

    @Test
    fun aLookWaitsForTheNextFilmUntilTheNativeSideTakesItAtOnce() {
        val store = FakeStyleStore()
        val pending = SubtitleStyleController(store, PendingNativeSubtitleStyle, CoroutineScope(Dispatchers.Unconfined), saveAfterMs = 0)
        assertFalse(pending.waiting)
        pending.choose(SubtitlePreset.Boxed)
        assertTrue(pending.waiting)
        assertTrue(LibVlcRestart.pending)
        assertEquals(SubtitlePreset.Boxed, pending.preset)
        assertEquals(60, store.kept.backgroundPercent)
        // the same look again writes nothing
        pending.update(pending.style)
        assertEquals(1, store.saves)
        pending.release()
        assertEquals(1, store.saves)

        LibVlcRestart.pending = false
        val native = FakeNativeStyle(live = true)
        val live = SubtitleStyleController(FakeStyleStore(), native, CoroutineScope(Dispatchers.Unconfined), saveAfterMs = 0)
        live.update(live.style.copy(sizePx = 52))
        assertEquals(listOf(SubtitleStyle(sizePx = 52)), native.applied)
        assertFalse(live.waiting)
        assertFalse(LibVlcRestart.pending)
        assertTrue(live.supportsLayout)
    }

    private val changes = mutableListOf<SubtitleStyle>()
    private val presets = mutableListOf<SubtitlePreset>()

    private fun section(style: SubtitleStyle = sampleLook, waiting: Boolean = false) {
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Box(Modifier.width(360.dp)) {
                    SubtitleStyleSection(
                        style = style,
                        preset = SubtitlePreset.of(style),
                        waiting = waiting,
                        layoutSupported = false,
                        onStyle = { changes += it },
                        onPreset = { presets += it },
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun theSectionChangesTheLook() {
        section()
        compose.onNodeWithText("GÖRÜNÜM").assertExists()
        compose.onNodeWithTag("style-preview").assertExists()
        compose.onNodeWithText("44").assertExists()
        compose.onNodeWithText("2 px").assertExists()
        compose.onNodeWithText("%0").assertExists()
        compose.onNode(hasText("Sarı") and hasClickAction()).performClick()
        assertEquals(listOf(SubtitlePreset.Yellow), presets)

        compose.onNodeWithText("Tırnaksız · Normal").performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Tırnaklı · Kalın") and hasClickAction()).performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Camgöbeği").performClick()
        compose.onNodeWithTag("style-shadow").performClick()
        compose.onNodeWithContentDescription("Boyut").performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        assertEquals(
            listOf(
                sampleLook.copy(font = SubtitleFont.Serif, bold = true),
                sampleLook.copy(colourArgb = SubtitleColour.Cyan.argb),
                sampleLook.copy(shadow = true),
                sampleLook.copy(sizePx = SubtitleStyle.MAX_SIZE_PX),
            ),
            changes,
        )
        // where the subtitles stand waits for the native side
        compose.onNodeWithTag("style-black-bars").assertIsNotEnabled()
        compose.onNodeWithTag("style-pending").assertExists()
        compose.onNodeWithTag("style-waiting").assertDoesNotExist()
    }

    @Test
    fun aChangeSaysWhenItReachesTheSubtitles() {
        section(sampleLook.copy(backgroundPercent = 60), waiting = true)
        compose.onNodeWithTag("style-waiting").assertExists()
        compose.onNodeWithText("%60").assertExists()
    }

    @Test
    @Config(qualifiers = "en-w1280dp-h800dp-mdpi")
    fun inEnglish() {
        section(sampleLook.copy(backgroundPercent = 60))
        compose.onNodeWithText("APPEARANCE").assertExists()
        compose.onNodeWithText("60%").assertExists()
        compose.onNodeWithText("Sans serif · Regular").assertExists()
    }
}

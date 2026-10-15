package app.nagmedar.ui.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.effects.AdvancedVideo
import app.nagmedar.ui.player.effects.AdvancedVideoActions
import app.nagmedar.ui.player.effects.AdvancedVideoSection
import app.nagmedar.ui.player.effects.AdvancedVideoView
import app.nagmedar.ui.player.effects.AnaglyphScheme
import app.nagmedar.ui.player.effects.Effect
import app.nagmedar.ui.player.effects.EffectPreview
import app.nagmedar.ui.player.effects.EffectsPanel
import app.nagmedar.ui.player.effects.LogoOverlay
import app.nagmedar.ui.player.effects.OverlayPosition
import app.nagmedar.ui.player.effects.TextClock
import app.nagmedar.ui.player.effects.TextOverlay
import app.nagmedar.ui.player.effects.TextSize
import app.nagmedar.ui.player.effects.VideoTransform
import app.nagmedar.ui.player.effects.sampleEffects
import app.nagmedar.ui.player.panel.PanelTab
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.NagmedarTheme
import app.nagmedar.ui.theme.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * S-610: the effects tab's advanced video section: its preview on a still frame with a logo, a
 * text and a clock, the picture turned by an angle and the CPU warning (DeX, in English, a half
 * window in two columns); anaglyph 3D waiting for "Apply now" on a phone; and a board of each
 * effect's preview on its own.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlayerAdvancedEffectsScreenshotTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val overlays = AdvancedVideo(
        logo = LogoOverlay(enabled = true, file = "/cache/overlays/logo-1.png", name = "kanal-logosu.png", position = OverlayPosition.TopRight, opacityPercent = 80),
        text = TextOverlay(enabled = true, text = "Nağmedar", position = OverlayPosition.BottomLeft, size = TextSize.Medium, clock = TextClock.Time),
        angle = 12,
    )

    private val threeD = AdvancedVideo(anaglyph = true, anaglyphScheme = AnaglyphScheme.RedCyan)

    /** 21:04:33 on 15 October 2026, UTC: the preview's clock. */
    private val now = 1_792_098_273_000L

    private fun view(video: AdvancedVideo, waiting: Boolean = false) = AdvancedVideoView(
        video = video,
        frame = sideBySideFrame(),
        logo = sampleLogo(),
        pictureHeight = 1080,
        waiting = waiting,
        canApplyNow = true,
        nowMs = now,
    )

    private fun capture(name: String, view: AdvancedVideoView, input: InputMode = InputMode.Pointer, controls: Boolean = true, scroll: Boolean = false) {
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = input) {
                PlayerWithPanel(PanelTab.Effects, controls) { columns ->
                    EffectsPanel(
                        sampleEffects, Effect.entries.toSet(), remembered = false, onChange = {}, onRemember = {}, onMakeDefault = {}, onEqualizerWindow = {},
                        columns = columns,
                        advanced = { AdvancedVideoSection(view, AdvancedVideoActions(onChange = {})) },
                    )
                }
            }
        }
        compose.waitForIdle()
        // a short panel: the section scrolled into it, as a finger would
        if (scroll) compose.onNodeWithTag("advanced-apply").performScrollTo()
        compose.waitForIdle()
        save(name)
    }

    private fun save(name: String) {
        val window = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(window.width, window.height, Bitmap.Config.ARGB_8888)
        compose.runOnIdle { window.draw(Canvas(bitmap)) }
        ShellScreenshotTest.save(name, bitmap)
    }

    @Test @Config(qualifiers = "tr-w1280dp-h1600dp-mdpi")
    fun dex() = capture("player-effects-advanced-dex", view(overlays))

    @Test @Config(qualifiers = "en-w1280dp-h1600dp-mdpi")
    fun dexEnglish() = capture("player-effects-advanced-dex-en", view(overlays))

    @Test @Config(qualifiers = "tr-w728dp-h1200dp-mdpi")
    fun halfWindow() = capture("player-effects-advanced-half", view(overlays, waiting = true), scroll = true)

    @Test @Config(qualifiers = "tr-w411dp-h891dp-hdpi")
    fun phone() = capture("player-effects-advanced-phone", view(threeD, waiting = true), input = InputMode.Touch, controls = false, scroll = true)

    /** Each effect's preview alone: the mirror, a quarter turn, a free angle, 3D, the logo, the text with the date. */
    @Test @Config(qualifiers = "tr-w1000dp-h800dp-mdpi")
    fun previews() {
        val frame = sideBySideFrame()
        val logo = sampleLogo()
        val cases = listOf(
            "Ayna" to (AdvancedVideo() to VideoTransform(flipHorizontal = true)),
            "90°" to (AdvancedVideo() to VideoTransform(rotation = 90)),
            "30°" to (AdvancedVideo(angle = 30) to VideoTransform()),
            "3B" to (threeD to VideoTransform()),
            "Logo" to (AdvancedVideo(logo = LogoOverlay(enabled = true, file = "/l.png", position = OverlayPosition.Center, opacityPercent = 60)) to VideoTransform()),
            "Yazı" to (AdvancedVideo(text = TextOverlay(enabled = true, text = "Canlı", position = OverlayPosition.Top, size = TextSize.Large, colour = 0xFFE14D, clock = TextClock.DateTime)) to VideoTransform()),
        )
        compose.runOnUiThread { compose.activity.actionBar?.hide() }
        compose.setContent {
            NagmedarTheme(themeMode = ThemeMode.Dark, inputMode = InputMode.Pointer) {
                Column(Modifier.fillMaxSize().background(NagTheme.colors.background).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    cases.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            row.forEach { (label, case) ->
                                Column(Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(label, style = NagTheme.type.label, color = NagTheme.colors.text)
                                    EffectPreview(frame, case.first, case.second, logo, 1080, now, label)
                                }
                            }
                        }
                    }
                    // without a frame: the sample picture
                    Column(Modifier.width(300.dp)) { EffectPreview(null, overlays, VideoTransform(), logo, 1080, now, "") }
                }
            }
        }
        compose.waitForIdle()
        save("player-effects-previews")
    }
}

/** A side-by-side 3D frame: the same scene in each half, the near ball shifted between the eyes. */
private fun sideBySideFrame(): ImageBitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).also { bitmap ->
    val canvas = Canvas(bitmap)
    for (half in 0..1) {
        val x0 = half * 320f
        canvas.save()
        canvas.clipRect(x0, 0f, x0 + 320f, 360f)
        canvas.drawRect(x0, 0f, x0 + 320f, 360f, Paint().apply { shader = LinearGradient(0f, 0f, 0f, 360f, 0xFF203A5A.toInt(), 0xFF8FB8D0.toInt(), Shader.TileMode.CLAMP) })
        canvas.drawRect(x0, 250f, x0 + 320f, 360f, Paint().apply { color = 0xFF3E6B3F.toInt() })
        canvas.drawCircle(x0 + 150f + half * 18f, 200f, 46f, Paint().apply { color = 0xFFEDEDED.toInt() })
        canvas.restore()
    }
}.asImageBitmap()

/** A channel's logo: a rounded square with a mark in it. */
private fun sampleLogo(): ImageBitmap = Bitmap.createBitmap(160, 96, Bitmap.Config.ARGB_8888).also { bitmap ->
    val canvas = Canvas(bitmap)
    canvas.drawRoundRect(0f, 0f, 160f, 96f, 18f, 18f, Paint().apply { color = 0xFFE5483B.toInt() })
    canvas.drawCircle(48f, 48f, 26f, Paint().apply { color = 0xFFFFFFFF.toInt() })
    canvas.drawRect(88f, 30f, 140f, 66f, Paint().apply { color = 0xFFFFFFFF.toInt() })
}.asImageBitmap()

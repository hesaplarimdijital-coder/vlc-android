package app.nagmedar.ui.player.effects

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.ui.player.subtitles.LibVlcRestart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.videolan.resources.NagmedarVlcOptions
import org.videolan.tools.KEY_NAG_SCALER
import org.videolan.tools.KEY_NAG_SECONDARY_SUB_TOP
import org.videolan.tools.KEY_NAG_SUBTITLE_BLACK_BARS
import org.videolan.tools.KEY_NAG_SUBTITLE_MARGIN
import org.videolan.tools.KEY_NAG_TONE_MAPPING
import org.videolan.tools.KEY_NAG_TRANSFORM
import org.videolan.tools.KEY_OPENGL

/**
 * S-505, S-506: the effects libVLC does through Nağmedar's additions and its options. The
 * picture's turn becomes VLC's transform filter's type; the audio filters chain up; the video
 * output, its tone mapping and scaler and the turn become VLC's settings, waiting for its next
 * start; the options read the settings back. Without the additions, nothing is promised.
 */
@RunWith(AndroidJUnit4::class)
class LibVlcEffectsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences("libvlc-effects-test", Context.MODE_PRIVATE)

    @Before
    fun clear() {
        prefs.edit().clear().commit()
        NagmedarVlcOptions.sessionVideo = NagmedarVlcOptions.SessionVideo()
        NagmedarVlcOptions.markStarted(NagmedarVlcOptions.SessionVideo(), prefs)
        LibVlcRestart.pending = false
    }

    @Test
    fun theTurnBecomesVlcsTransform() {
        assertNull(LibVlcEffects.vlcTransform(VideoTransform()))
        assertEquals("90", LibVlcEffects.vlcTransform(VideoTransform(rotation = 90)))
        assertEquals("180", LibVlcEffects.vlcTransform(VideoTransform(rotation = 180)))
        assertEquals("270", LibVlcEffects.vlcTransform(VideoTransform(rotation = 270)))
        assertEquals("hflip", LibVlcEffects.vlcTransform(VideoTransform(flipHorizontal = true)))
        assertEquals("vflip", LibVlcEffects.vlcTransform(VideoTransform(flipVertical = true)))
        // both mirrors are a half turn
        assertEquals("180", LibVlcEffects.vlcTransform(VideoTransform(flipHorizontal = true, flipVertical = true)))
        // a quarter turn and a mirror are a transposition
        assertEquals("transpose", LibVlcEffects.vlcTransform(VideoTransform(rotation = 90, flipHorizontal = true)))
        assertEquals("antitranspose", LibVlcEffects.vlcTransform(VideoTransform(rotation = 90, flipVertical = true)))
        assertEquals("transpose", LibVlcEffects.vlcTransform(VideoTransform(rotation = 270, flipVertical = true)))
        assertEquals("antitranspose", LibVlcEffects.vlcTransform(VideoTransform(rotation = 270, flipHorizontal = true)))
        // every way a picture can lie is one of VLC's eight
        val all = (0..3).flatMap { quarter -> listOf(false, true).flatMap { h -> listOf(false, true).map { v -> VideoTransform(quarter * 90, h, v) } } }
        assertEquals(8, all.map { LibVlcEffects.vlcTransform(it) }.toSet().size)
    }

    @Test
    fun theAudioFiltersChainUp() {
        assertEquals("", LibVlcEffects.audioFilterChain(nightMode = false, dialogueDb = 0))
        assertEquals("compressor", LibVlcEffects.audioFilterChain(nightMode = true, dialogueDb = 0))
        assertEquals("nagdialogue", LibVlcEffects.audioFilterChain(nightMode = false, dialogueDb = 6))
        assertEquals("compressor:nagdialogue", LibVlcEffects.audioFilterChain(nightMode = true, dialogueDb = 6))
    }

    @Test
    fun whatLibVlcTakesAsItStartsGoesIntoVlcsSettings() {
        val effects = LibVlcEffects({ null }, prefs)
        assertEquals(LibVlcEffects.DEFERRED, effects.deferred)
        assertTrue(effects.supported.containsAll(LibVlcEffects.DEFERRED))
        // the published libVLC lacks the additions: nothing live is promised
        assertFalse(Effect.NightMode in effects.supported)
        assertFalse(Effect.AutoCrop in effects.supported)

        effects.setHdr(HdrOutput.ToneMap, ToneMapping.Hable, Scaler.Spline36)
        assertEquals("1", prefs.getString(KEY_OPENGL, null))
        assertEquals("hable", prefs.getString(KEY_NAG_TONE_MAPPING, null))
        assertEquals("spline36", prefs.getString(KEY_NAG_SCALER, null))
        assertTrue(LibVlcRestart.pending)
        LibVlcRestart.pending = false
        // the same again is no change
        effects.setHdr(HdrOutput.ToneMap, ToneMapping.Hable, Scaler.Spline36)
        assertFalse(LibVlcRestart.pending)

        effects.setTransform(VideoTransform(rotation = 90))
        assertEquals("90", prefs.getString(KEY_NAG_TRANSFORM, null))
        assertTrue(LibVlcRestart.pending)
        effects.setTransform(VideoTransform())
        assertNull(prefs.getString(KEY_NAG_TRANSFORM, null))
    }

    @Test
    fun vlcsOptionsReadTheSettingsBack() {
        // VLC's own choice of output, nothing asked that needs OpenGL: the player's defaults
        assertNull(NagmedarVlcOptions.vout(NagmedarVlcOptions.VOUT_AUTO, prefs))
        val defaults = NagmedarVlcOptions.options(prefs)
        assertTrue("--no-spu-fill" in defaults)
        assertTrue("--deinterlace-filter=deinterlace,glblend" in defaults)
        assertTrue("--secondary-sub-alignment=4" in defaults)
        assertFalse(defaults.any { it.startsWith("--gl-") || it.startsWith("--video-filter") || it.startsWith("--sub-margin") })

        prefs.edit()
            .putString(KEY_NAG_TONE_MAPPING, "bt2390")
            .putString(KEY_NAG_SCALER, "ewa_lanczos")
            .putString(KEY_NAG_TRANSFORM, "hflip")
            .putInt(KEY_NAG_SUBTITLE_MARGIN, 60)
            .putBoolean(KEY_NAG_SUBTITLE_BLACK_BARS, true)
            .putBoolean(KEY_NAG_SECONDARY_SUB_TOP, false)
            .commit()
        // a scaler or a turn needs the OpenGL output, which VLC's own choice would not take
        assertEquals("--vout=gles2,none", NagmedarVlcOptions.vout(NagmedarVlcOptions.VOUT_AUTO, prefs))
        // asked for Android's own output, it stays
        assertEquals("--vout=android_display,none", NagmedarVlcOptions.vout(NagmedarVlcOptions.VOUT_ANDROID, prefs))
        val options = NagmedarVlcOptions.options(prefs)
        assertEquals(
            listOf(
                "--sub-margin=60", "--spu-fill", "--deinterlace-filter=deinterlace,glblend",
                "--gl-tone-mapping-function=2", "--gl-upscaler=6", "--gl-downscaler=6",
                "--video-filter=transform", "--transform-type=hflip",
            ),
            options,
        )
    }

    @Test
    fun theOutputChoiceIsVlcsOpenGlSetting() {
        assertEquals(NagmedarVlcOptions.VOUT_AUTO, LibVlcEffects.vout(HdrOutput.Auto))
        assertEquals(NagmedarVlcOptions.VOUT_ANDROID, LibVlcEffects.vout(HdrOutput.Passthrough))
        assertEquals(NagmedarVlcOptions.VOUT_OPENGL, LibVlcEffects.vout(HdrOutput.ToneMap))
    }

    /** S-610: the session's filters around the transform, the sources over the picture, and the films' options for the CPU's. */
    @Test
    fun theSessionsEffectsJoinVlcsOptions() {
        prefs.edit().putString(KEY_NAG_TRANSFORM, "90").commit()
        val options = AdvancedVideoOptions.build(
            AdvancedVideo(
                logo = LogoOverlay(enabled = true, file = "/cache/logo.png"),
                text = TextOverlay(enabled = true, text = "x"),
                angle = 15,
                anaglyph = true,
            ),
        )
        LibVlcVideoOptions.set(options)
        assertTrue(LibVlcRestart.pending)
        // nothing shows before libVLC starts with them: the films are not asked for their frames yet
        assertTrue(NagmedarVlcOptions.mediaOptions().isEmpty())
        val vlc = NagmedarVlcOptions.options(prefs)
        assertTrue("--video-filter=anaglyph:transform:rotate" in vlc)
        assertTrue("--transform-type=90" in vlc)
        assertTrue("--sub-source=logo:marq" in vlc)
        assertTrue(vlc.containsAll(options.options))
        // reading the options (a feedback report) does not start them
        assertEquals(VideoFilterOptions(), LibVlcVideoOptions.started())
        // a feedback report leaves the overlays' text and the logo's path out
        assertEquals(
            listOf("--video-filter=transform", "--transform-type=90"),
            NagmedarVlcOptions.withoutSession(vlc).filter { it.startsWith("--video-filter") || it.startsWith("--transform") || it.startsWith("--sub-source") || it.startsWith("--logo") || it.startsWith("--marq") || it.startsWith("--rotate") || it.startsWith("--anaglyph") },
        )
        // libVLC made with them: they show, and films hand their frames over
        NagmedarVlcOptions.markStarted(NagmedarVlcOptions.sessionVideo, prefs)
        assertEquals(options, LibVlcVideoOptions.started())
        assertEquals("90", NagmedarVlcOptions.startedTransform)
        assertEquals(listOf(":no-mediacodec-dr", ":no-omxil-dr"), NagmedarVlcOptions.mediaOptions())
        LibVlcVideoOptions.set(VideoFilterOptions())
        assertFalse(NagmedarVlcOptions.options(prefs).any { it.startsWith("--sub-source") || it.contains("rotate") })
        NagmedarVlcOptions.markStarted(NagmedarVlcOptions.sessionVideo, prefs)
        assertTrue(NagmedarVlcOptions.mediaOptions().isEmpty())
    }
}

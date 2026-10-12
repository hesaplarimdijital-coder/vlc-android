package app.nagmedar.ui.player.effects

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.nagmedar.data.MediaSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A film's effects as the mockups' panel shows them: a little more saturation, gamma 1,06, night mode. */
internal val sampleEffects = PlayerEffects(
    color = ColorAdjust(saturation = 6, gamma = 1.06f),
    autoCrop = true,
    hdrOutput = HdrOutput.Passthrough,
    scaler = Scaler.EwaLanczos,
    debanding = true,
    nightMode = true,
    dialogueDb = 3,
    channelLayout = ChannelLayout.Stereo,
    equalizer = EqualizerPreset.Rock.key,
)

/** What the effects asked of the classic player and of the native side. */
internal class FakeEffectsTarget : PlayerEffectsTarget {
    val calls = mutableListOf<String>()

    override fun setAspect(aspect: AspectChoice) {
        calls += "aspect:${aspect.key}"
    }

    override fun setEqualizer(choice: String?, gainDb: Float) {
        calls += "equalizer:$choice:${"%.1f".format(java.util.Locale.ROOT, gainDb)}"
    }
}

internal class FakeNativeEffects(override val supported: Set<Effect>) : NativeEffects {
    val calls = mutableListOf<String>()

    override fun setColorAdjust(adjust: ColorAdjust) {
        calls += "color:${adjust.saturation}"
    }

    override fun setAutoCrop(enabled: Boolean) {
        calls += "crop:$enabled"
    }

    override fun setTransform(transform: VideoTransform) {
        calls += "transform:${transform.rotation}"
    }

    override fun setHdr(output: HdrOutput, toneMapping: ToneMapping, scaler: Scaler) {
        calls += "hdr:${output.key}:${scaler.key}"
    }

    override fun setDeinterlace(mode: Deinterlace) {
        calls += "deinterlace:${mode.key}"
    }

    override fun setDebanding(enabled: Boolean) {
        calls += "debanding:$enabled"
    }

    override fun setNightMode(enabled: Boolean) {
        calls += "night:$enabled"
    }

    override fun setDialogueBoost(db: Int) {
        calls += "dialogue:$db"
    }

    override fun setChannelLayout(layout: ChannelLayout) {
        calls += "channels:${layout.key}"
    }
}

/** The per-file settings and the defaults, in memory. */
internal class FakeEffectsStore : EffectsStore {
    val files = mutableMapOf<String, PlayerEffects>()
    var saved = 0
    private var defaults: PlayerEffects? = null

    override suspend fun load(uri: String): PlayerEffects? = files[uri]

    override suspend fun save(uri: String, effects: PlayerEffects) {
        files[uri] = effects
        saved++
    }

    override suspend fun forget(uri: String) {
        files.remove(uri)
    }

    override fun defaults(): PlayerEffects? = defaults

    override fun saveDefaults(effects: PlayerEffects) {
        defaults = effects
    }
}

/**
 * S-603: the effects apply at once and only what changed; what the native side cannot do yet
 * waits; a film remembers its effects when asked and brings them back; the defaults serve the
 * rest; the per-file columns and the defaults read back what they were given.
 */
@RunWith(AndroidJUnit4::class)
class EffectsModelTest {

    private val target = FakeEffectsTarget()

    @Test
    fun theEngineAppliesWhatChangedOnly() {
        val native = FakeNativeEffects(Effect.entries.toSet())
        val engine = EffectsEngine(target, native)
        // the film as it is: nothing to do
        engine.apply(PlayerEffects())
        assertEquals(emptyList<String>(), target.calls + native.calls)
        engine.apply(PlayerEffects(aspect = AspectChoice.Scope, gainPercent = 200))
        assertEquals(listOf("aspect:2.39:1", "equalizer:null:6.0"), target.calls)
        target.calls.clear()
        // a slider dragged along: only its own effect, every step
        engine.apply(PlayerEffects(aspect = AspectChoice.Scope, gainPercent = 200, color = ColorAdjust(saturation = 6)))
        engine.apply(PlayerEffects(aspect = AspectChoice.Scope, gainPercent = 200, color = ColorAdjust(saturation = 7)))
        assertEquals(emptyList<String>(), target.calls)
        assertEquals(listOf("color:6", "color:7"), native.calls)
        native.calls.clear()
        engine.apply(sampleEffects)
        assertTrue(native.calls.containsAll(listOf("crop:true", "hdr:passthrough:ewa_lanczos", "debanding:true", "night:true", "dialogue:3", "channels:stereo")))
        assertTrue("rock, at the film's level", "equalizer:rock:0.0" in target.calls)
    }

    @Test
    fun whatTheNativeSideLacksWaits() {
        val engine = EffectsEngine(target, PendingNativeEffects)
        assertEquals(setOf(Effect.Aspect, Effect.Equalizer, Effect.Gain), engine.supported)
        val native = FakeNativeEffects(setOf(Effect.ColorAdjust))
        val partly = EffectsEngine(target, native)
        partly.apply(sampleEffects)
        assertEquals(listOf("color:6"), native.calls)
    }

    @Test
    fun aFilmRemembersItsEffectsWhenAskedAndTheDefaultsServeTheRest() {
        val store = FakeEffectsStore()
        val controller = EffectsController(EffectsEngine(target), store, CoroutineScope(Dispatchers.Unconfined), saveAfterMs = 0)
        controller.onMediaStarted("file:///kuzey.mkv")
        assertEquals(PlayerEffects(), controller.effects)
        assertFalse(controller.remembered)
        // changed, not remembered: the film plays so, nothing is written
        controller.update { it.copy(aspect = AspectChoice.Scope) }
        assertEquals(listOf("aspect:2.39:1"), target.calls)
        assertEquals(0, store.saved)
        // "Save for this file": written now and at every change after
        controller.remember(true)
        assertEquals(AspectChoice.Scope, store.files["file:///kuzey.mkv"]?.aspect)
        controller.update { it.copy(gainPercent = 150) }
        assertEquals(150, store.files["file:///kuzey.mkv"]?.gainPercent)
        // "Make default", then another film: the defaults, not remembered
        controller.update { it.copy(nightMode = true) }
        controller.makeDefault()
        controller.onMediaStarted("file:///baska.mkv")
        assertFalse(controller.remembered)
        assertTrue(controller.effects.nightMode)
        // back to the first film: its own effects
        controller.onMediaStarted("file:///kuzey.mkv")
        assertTrue(controller.remembered)
        assertEquals(150, controller.effects.gainPercent)
        // cleared: forgotten, the effects stay for now
        controller.remember(false)
        assertNull(store.files["file:///kuzey.mkv"])
        assertEquals(150, controller.effects.gainPercent)
    }

    @Test
    fun theSameFilmGoingOnPlayingKeepsWhatWasChanged() {
        val store = FakeEffectsStore()
        val controller = EffectsController(EffectsEngine(target), store, CoroutineScope(Dispatchers.Unconfined), saveAfterMs = 0)
        controller.onMediaStarted("file:///kuzey.mkv")
        controller.update { it.copy(aspect = AspectChoice.Fill) }
        // a pause and Playing again: the same film
        controller.onMediaStarted("file:///kuzey.mkv")
        assertEquals(AspectChoice.Fill, controller.effects.aspect)
        // the player goes: its next film starts from the film as it is
        target.calls.clear()
        controller.release()
        assertEquals(listOf("aspect:auto"), target.calls)
    }

    @Test
    fun theColumnsReadBackWhatTheyWereGiven() {
        val columns = EffectsCodec.columns(sampleEffects)
        assertEquals("auto", columns.aspectRatio)
        assertEquals("auto", columns.deinterlace)
        assertEquals("rock", columns.equalizerPreset)
        val settings = MediaSettings(
            "file:///kuzey.mkv",
            audioDelayMs = -120,
            aspectRatio = columns.aspectRatio,
            deinterlace = columns.deinterlace,
            equalizerPreset = columns.equalizerPreset,
            effects = columns.effects,
        )
        assertEquals(sampleEffects, EffectsCodec.fromSettings(settings))
        // nothing remembered: the player's own
        assertNull(EffectsCodec.fromSettings(MediaSettings("file:///kuzey.mkv", audioDelayMs = -120)))
        // the defaults, as one text
        val defaults = sampleEffects.copy(aspect = AspectChoice.Wide, deinterlace = Deinterlace.Yadif2x, equalizer = null)
        assertEquals(defaults, EffectsCodec.decodeDefaults(EffectsCodec.encodeDefaults(defaults)))
    }

    @Test
    fun unknownOrBrokenValuesReadAsTheFilmsOwn() {
        val settings = MediaSettings(
            "file:///kuzey.mkv",
            aspectRatio = "21:9",
            deinterlace = "warp",
            equalizerPreset = "jazz",
            effects = "{\"brightness\":400,\"gamma\":9,\"rotation\":45,\"hdr\":\"dolby\",\"gain\":-5}",
        )
        val effects = EffectsCodec.fromSettings(settings)!!
        assertEquals(AspectChoice.Auto, effects.aspect)
        assertEquals(Deinterlace.Auto, effects.deinterlace)
        assertNull(effects.equalizer)
        assertEquals(ColorAdjust.MAX, effects.color.brightness)
        assertEquals(ColorAdjust.GAMMA_MAX, effects.color.gamma)
        assertEquals(0, effects.transform.rotation)
        assertEquals(HdrOutput.Auto, effects.hdrOutput)
        assertEquals(0, effects.gainPercent)
        assertNull(EffectsCodec.decodeDefaults("{bozuk"))
        assertEquals(PlayerEffects(), EffectsCodec.fromSettings(settings.copy(effects = "{bozuk", aspectRatio = "auto")))
    }

    @Test
    fun quarterTurnsGoRound() {
        val transform = VideoTransform()
        assertEquals(270, transform.rotatedLeft().rotation)
        assertEquals(0, transform.rotatedRight().rotatedRight().rotatedRight().rotatedRight().rotation)
        assertTrue(transform.flippedHorizontally().flipHorizontal)
        assertTrue(transform.flippedHorizontally().flippedHorizontally().isNeutral)
    }

    @Test
    fun gainIsInDecibels() {
        assertEquals(0f, PlayerEffects(gainPercent = 100).gainDb, 0.01f)
        assertEquals(6.02f, PlayerEffects(gainPercent = 200).gainDb, 0.01f)
        assertEquals(-6.02f, PlayerEffects(gainPercent = 50).gainDb, 0.01f)
        assertEquals(PlayerEffects.MIN_GAIN_DB, PlayerEffects(gainPercent = 0).gainDb, 0.01f)
    }
}

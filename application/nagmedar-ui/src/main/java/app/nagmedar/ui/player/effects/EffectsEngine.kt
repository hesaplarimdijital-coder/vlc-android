package app.nagmedar.ui.player.effects

import app.nagmedar.ui.player.equalizer.VlcEqualizer

/**
 * The native side of the effects panel (S-603): what libVLC applies while a film plays once the
 * native work lands, the video filters (S-505: colours, crop, rotation, deinterlacing, debanding),
 * the HDR output and scaler (S-506) and the audio filters (night mode, dialogue, channels). Each
 * call changes the playing film at once. [supported] says which of them this build's libVLC has;
 * the panel shows the others switched off, with a note.
 */
interface NativeEffects {
    val supported: Set<Effect>

    /**
     * Of [supported], the effects libVLC takes as it starts rather than while a film plays (the
     * video output, its tone mapping and scaler, the picture turned): they are kept for its next
     * start, and the panel says so.
     */
    val deferred: Set<Effect> get() = emptySet()

    fun setColorAdjust(adjust: ColorAdjust)

    fun setAutoCrop(enabled: Boolean)

    fun setTransform(transform: VideoTransform)

    fun setHdr(output: HdrOutput, toneMapping: ToneMapping, scaler: Scaler)

    fun setDeinterlace(mode: Deinterlace)

    fun setDebanding(enabled: Boolean)

    fun setNightMode(enabled: Boolean)

    fun setDialogueBoost(db: Int)

    fun setChannelLayout(layout: ChannelLayout)
}

/** This build: libVLC from Maven has none of the native effects yet. */
object PendingNativeEffects : NativeEffects {
    override val supported: Set<Effect> = emptySet()

    override fun setColorAdjust(adjust: ColorAdjust) = Unit

    override fun setAutoCrop(enabled: Boolean) = Unit

    override fun setTransform(transform: VideoTransform) = Unit

    override fun setHdr(output: HdrOutput, toneMapping: ToneMapping, scaler: Scaler) = Unit

    override fun setDeinterlace(mode: Deinterlace) = Unit

    override fun setDebanding(enabled: Boolean) = Unit

    override fun setNightMode(enabled: Boolean) = Unit

    override fun setDialogueBoost(db: Int) = Unit

    override fun setChannelLayout(layout: ChannelLayout) = Unit
}

/** What the classic player already does through libVLC's Java API: the picture's shape and the equaliser. */
interface PlayerEffectsTarget {
    fun setAspect(aspect: AspectChoice)

    /**
     * The equaliser: [choice] is [PlayerEffects.EQUALIZER_OFF], an [EqualizerPreset]'s key, or null
     * for the player's own setting; [gainDb] raises or lowers the sound on top of it.
     */
    fun setEqualizer(choice: String?, gainDb: Float)

    /**
     * The player's own setting as its equaliser window has it now: [own], or none while it is
     * off; [gainDb] on top. By default it is read where VLC keeps it.
     */
    fun setPlayersEqualizer(own: VlcEqualizer?, gainDb: Float) = setEqualizer(null, gainDb)
}

/**
 * Applies the panel's effects to the playing film, each change at once and only what changed: a
 * slider dragged along does not set the equaliser again. The first film starts from the player as
 * it is ([PlayerEffects] defaults); a film that follows starts from the previous one's effects,
 * which the player keeps.
 */
class EffectsEngine(private val target: PlayerEffectsTarget, private val native: NativeEffects = PendingNativeEffects) {

    /** What changes the film now: the aspect ratio and the equaliser, and what the native side has. */
    val supported: Set<Effect> = setOf(Effect.Aspect, Effect.Equalizer, Effect.Gain) + native.supported

    private var applied = PlayerEffects()

    /** S-610: a new player plays the film (libVLC started again): nothing is applied to it yet. */
    fun reset() {
        applied = PlayerEffects()
    }

    /** What libVLC takes as it starts rather than at once. */
    val deferred: Set<Effect> get() = native.deferred

    /**
     * [newFilm]: a film just started, for which the player set its own equaliser again (VLC's,
     * when it is on): the effects' equaliser goes back over it. [release]: the player goes, and
     * what libVLC keeps for its next start ([deferred]) is left as it is.
     */
    fun apply(effects: PlayerEffects, newFilm: Boolean = false, release: Boolean = false) {
        val last = applied
        applied = effects
        if (effects.aspect != last.aspect) target.setAspect(effects.aspect)
        val ownEqualizer = effects.equalizer != null || effects.gainPercent != 100
        if (effects.equalizer != last.equalizer || effects.gainPercent != last.gainPercent || (newFilm && ownEqualizer)) {
            target.setEqualizer(effects.equalizer, effects.gainDb)
        }
        val can = if (release) native.supported - native.deferred else native.supported
        if (Effect.ColorAdjust in can && effects.color != last.color) native.setColorAdjust(effects.color)
        if (Effect.AutoCrop in can && effects.autoCrop != last.autoCrop) native.setAutoCrop(effects.autoCrop)
        if (Effect.Transform in can && effects.transform != last.transform) native.setTransform(effects.transform)
        if ((Effect.HdrOutput in can || Effect.ToneMapping in can || Effect.Scaler in can) &&
            (effects.hdrOutput != last.hdrOutput || effects.toneMapping != last.toneMapping || effects.scaler != last.scaler)
        ) {
            native.setHdr(effects.hdrOutput, effects.toneMapping, effects.scaler)
        }
        if (Effect.Deinterlace in can && effects.deinterlace != last.deinterlace) native.setDeinterlace(effects.deinterlace)
        if (Effect.Debanding in can && effects.debanding != last.debanding) native.setDebanding(effects.debanding)
        if (Effect.NightMode in can && effects.nightMode != last.nightMode) native.setNightMode(effects.nightMode)
        if (Effect.Dialogue in can && effects.dialogueDb != last.dialogueDb) native.setDialogueBoost(effects.dialogueDb)
        if (Effect.ChannelLayout in can && effects.channelLayout != last.channelLayout) native.setChannelLayout(effects.channelLayout)
    }

    /**
     * The player's own equaliser changed (its window): a film that follows it ([PlayerEffects.equalizer]
     * null) hears [own] at once (null: off), with its gain on top; a film with its own keeps it.
     */
    fun playersEqualizerChanged(effects: PlayerEffects, own: VlcEqualizer?) {
        if (effects.equalizer == null) target.setPlayersEqualizer(own, effects.gainDb)
    }
}

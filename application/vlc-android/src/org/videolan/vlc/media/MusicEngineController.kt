package org.videolan.vlc.media

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.lifecycle.MutableLiveData
import app.nagmedar.audio.CrossfadeCurve
import app.nagmedar.audio.DsdMode
import app.nagmedar.audio.DspMeter
import app.nagmedar.audio.DspSettings
import app.nagmedar.audio.EngineState
import app.nagmedar.audio.MusicEngine
import app.nagmedar.audio.MusicFormats
import app.nagmedar.audio.NativeAudio
import app.nagmedar.audio.OutputFormat
import app.nagmedar.audio.ReplayGainMode
import app.nagmedar.audio.ReplayGainSettings
import app.nagmedar.playback.EngineId
import app.nagmedar.playback.EngineMedia
import app.nagmedar.playback.PlaybackEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.tools.NAGMEDAR_AUDIO_BALANCE
import org.videolan.tools.NAGMEDAR_AUDIO_MONO
import org.videolan.tools.NAGMEDAR_AUDIO_PITCH
import org.videolan.tools.NAGMEDAR_CROSSFADE_CURVE
import org.videolan.tools.NAGMEDAR_CROSSFADE_EQUAL_POWER
import org.videolan.tools.NAGMEDAR_CROSSFADE_LINEAR
import org.videolan.tools.NAGMEDAR_CROSSFADE_MS
import org.videolan.tools.NAGMEDAR_CROSSFADE_SKIP_ALBUM
import org.videolan.tools.NAGMEDAR_DSD_DOP
import org.videolan.tools.NAGMEDAR_DSD_MODE
import org.videolan.tools.NAGMEDAR_DSP
import org.videolan.tools.NAGMEDAR_MUSIC_ENGINE
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_ALBUM
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_CLIP
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_MODE
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_OFF
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_PREAMP
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_TRACK
import org.videolan.tools.NAGMEDAR_REPLAYGAIN_UNTAGGED
import org.videolan.tools.Settings
import org.videolan.vlc.PlaybackService

/**
 * Nağmedar Ses Motoru as the playback service sees it (S-901, ADR-06): the second
 * [PlaybackEngine] behind the PlaylistManager, for the local music library. It talks to the
 * manager the way the libVLC player does, with [MediaPlayer.Event]s, so that the service, its
 * media session and its notification see one player. The progress and speed shown by the
 * classic interface are the libVLC player's LiveData, which this engine feeds while it plays.
 */
class MusicEngineController(
    private val context: Context,
    private val progress: MutableLiveData<Progress>,
    private val speed: MutableLiveData<Float>,
) : PlaybackEngine, CoroutineScope {

    override val coroutineContext = Dispatchers.Main.immediate + SupervisorJob()

    override val engineId = EngineId.NagmedarAudio

    private var engine: MusicEngine? = null
    private var listener: MediaPlayerEventListener? = null
    private var ticker: Job? = null
    private var ids = 0L
    private var trackId = -1L
    private var nextId = -1L
    private var playingUri: Uri? = null

    /**
     * S-904: the engine went on to the track given to [setNext] by itself, without a gap; the
     * playlist manager moves its indices along before the Playing event that follows.
     */
    var onHandOver: (() -> Unit)? = null
    private var volume = 100
    private var rate = 1f
    private var lastTime = 0L

    override var seekable = false
        private set
    override var pausable = false
        private set

    /** The engine's output as open now; null while it is not. */
    val outputFormat: OutputFormat? get() = engine?.outputFormat

    val signalPath: String get() = engine?.signalPath() ?: "{}"

    /** The setting: the engine takes the local music library unless turned off (Settings › Music). */
    private val enabled: Boolean get() = Settings.getInstance(context).getBoolean(NAGMEDAR_MUSIC_ENGINE, true)

    override fun canPlay(media: EngineMedia): Boolean =
        MusicEngine.available && enabled && media.isAudio && media.isLocal && !PlaybackService.hasRenderer() &&
            MusicFormats.canPlay(Uri.parse(media.uri), media.mimeType)

    private val events = Channel<MediaPlayer.Event>(Channel.UNLIMITED)

    init {
        launch {
            for (event in events) listener?.onEvent(event)
        }
    }

    private fun send(type: Int, arg: Long = 0L) {
        events.trySend(EngineEvent(type, arg))
    }

    private fun sendFloat(type: Int, value: Float) {
        events.trySend(EngineEvent(type, value))
    }

    private val engineListener = object : MusicEngine.Listener {
        override fun onStateChanged(state: EngineState) {
            when (state) {
                EngineState.Playing -> {
                    PlayerController.playbackState = PlaybackStateCompat.STATE_PLAYING
                    startTicker()
                    send(MediaPlayer.Event.Playing)
                }
                EngineState.Paused -> {
                    PlayerController.playbackState = PlaybackStateCompat.STATE_PAUSED
                    stopTicker()
                    send(MediaPlayer.Event.Paused)
                }
                EngineState.Stopped -> {
                    PlayerController.playbackState = PlaybackStateCompat.STATE_STOPPED
                    stopTicker()
                    send(MediaPlayer.Event.Stopped)
                }
                EngineState.Ended -> stopTicker()
            }
        }

        override fun onTrackStarted(id: Long) {
            val engine = engine ?: return
            val length = engine.lengthMs
            lastTime = engine.timeMs
            updateProgress(lastTime, length)
            if (id == nextId && id != trackId) {
                // a gapless hand-over (S-904): the manager's indices move on, then the service hears a new Playing
                trackId = id
                nextId = -1L
                onHandOver?.invoke()
                send(MediaPlayer.Event.LengthChanged, length)
                send(MediaPlayer.Event.Playing)
            } else {
                send(MediaPlayer.Event.LengthChanged, length)
                // a track opened while another played: the state stays, the service still hears a Playing for it
                if (engine.state == EngineState.Playing) send(MediaPlayer.Event.Playing)
            }
        }

        override fun onTrackEnded(id: Long) = Unit

        override fun onQueueEmpty() {
            /* no next was set (the list's end, a stream next, stop after this): the manager's own next() */
            send(MediaPlayer.Event.EndReached)
        }

        override fun onError(id: Long, message: String) {
            Log.w(TAG, "track $id: $message")
            if (id == trackId || id < 0) send(MediaPlayer.Event.EncounteredError)
        }

        override fun onOutputChanged(format: OutputFormat) {
            if (org.videolan.BuildConfig.DEBUG) Log.i(TAG, "output ${format.rate} Hz, ${format.channels} ch, ${format.bits} bit")
        }

        override fun onDeviceLost() {
            Log.i(TAG, "the output's device is gone; the engine reopens it")
        }
    }

    private fun engine(): MusicEngine? {
        engine?.let { return it }
        if (!MusicEngine.available) return null
        val created = MusicEngine(context, engineListener)
        if (!created.available) {
            created.close()
            return null
        }
        created.useAAudio()
        created.volume = volume / 100.0
        created.tempo = rate.toDouble()
        engine = created
        applyCrossfadeSettings()
        applyDspSettings()
        applyReplayGainSettings()
        applyAudioSettings()
        Settings.getInstance(context).registerOnSharedPreferenceChangeListener(settingsListener)
        return created
    }

    /** S-906, S-907: the chain and ReplayGain follow their settings; a screen writes them and the engine takes them at its next block. */
    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            NAGMEDAR_DSP -> applyDspSettings()
            NAGMEDAR_CROSSFADE_MS, NAGMEDAR_CROSSFADE_CURVE -> applyCrossfadeSettings()
            NAGMEDAR_REPLAYGAIN_MODE, NAGMEDAR_REPLAYGAIN_PREAMP, NAGMEDAR_REPLAYGAIN_UNTAGGED, NAGMEDAR_REPLAYGAIN_CLIP -> applyReplayGainSettings()
            NAGMEDAR_AUDIO_PITCH, NAGMEDAR_AUDIO_BALANCE, NAGMEDAR_AUDIO_MONO, NAGMEDAR_DSD_MODE -> applyAudioSettings()
        }
    }

    /** S-909, S-908: the pitch, balance, mono and the DSD mode as set. */
    fun applyAudioSettings() {
        val engine = engine ?: return
        val settings = Settings.getInstance(context)
        engine.pitch = settings.getFloat(NAGMEDAR_AUDIO_PITCH, 0f).toDouble()
        engine.balance = settings.getFloat(NAGMEDAR_AUDIO_BALANCE, 0f).toDouble()
        engine.mono = settings.getBoolean(NAGMEDAR_AUDIO_MONO, false)
        engine.dsdMode = if (settings.getString(NAGMEDAR_DSD_MODE, null) == NAGMEDAR_DSD_DOP) DsdMode.Dop else DsdMode.Pcm
    }

    /** S-907: ReplayGain as set; the tracks loaded take a change at once. */
    val replayGain: ReplayGainSettings get() = engine?.replayGain ?: replayGainSettings()

    /** The gain in dB the playing track gets from ReplayGain. */
    val replayGainDb: Double get() = engine?.replayGainDb ?: 0.0

    fun applyReplayGainSettings() {
        val engine = engine ?: return
        engine.replayGain = replayGainSettings()
    }

    private fun replayGainSettings(): ReplayGainSettings {
        val settings = Settings.getInstance(context)
        val mode = when (settings.getString(NAGMEDAR_REPLAYGAIN_MODE, NAGMEDAR_REPLAYGAIN_OFF)) {
            NAGMEDAR_REPLAYGAIN_TRACK -> ReplayGainMode.Track
            NAGMEDAR_REPLAYGAIN_ALBUM -> ReplayGainMode.Album
            else -> ReplayGainMode.Off
        }
        return ReplayGainSettings(
            mode = mode,
            preampDb = settings.getFloat(NAGMEDAR_REPLAYGAIN_PREAMP, 0f).toDouble().coerceIn(-15.0, 15.0),
            untaggedDb = settings.getFloat(NAGMEDAR_REPLAYGAIN_UNTAGGED, 0f).toDouble().coerceIn(-15.0, 15.0),
            preventClipping = settings.getBoolean(NAGMEDAR_REPLAYGAIN_CLIP, true),
        )
    }

    /** The DSP chain as set (S-906); the defaults, nothing in the chain, until the user touches it. */
    val dsp: DspSettings get() = engine?.dsp ?: DspSettings.fromJson(Settings.getInstance(context).getString(NAGMEDAR_DSP, null))

    /** Whether the chain changes the samples right now. */
    val dspActive: Boolean get() = engine?.dspActive ?: false

    /** The clip meter (S-906); with [reset] the counts start over. */
    fun dspMeter(reset: Boolean = false): DspMeter = engine?.dspMeter(reset) ?: DspMeter(0, 0, 0.0, 0.0)

    /** Reads NAGMEDAR_DSP into the engine. */
    fun applyDspSettings() {
        val engine = engine ?: return
        engine.dsp = DspSettings.fromJson(Settings.getInstance(context).getString(NAGMEDAR_DSP, null))
    }

    /**
     * Plays [mw] from [time] with events to [listener]; false when the engine cannot open it,
     * in which case the manager plays it with libVLC.
     */
    internal fun startPlayback(mw: MediaWrapper, listener: MediaPlayerEventListener, time: Long): Boolean {
        val engine = engine() ?: return false
        val uri = mw.uri ?: return false
        this.listener = listener
        trackId = ++ids
        nextId = -1L
        playingUri = uri
        seekable = true
        pausable = true
        lastTime = time
        updateProgress(time, mw.length)
        send(MediaPlayer.Event.Opening)
        if (!engine.open(trackId, uri, time)) {
            seekable = false
            pausable = false
            return false
        }
        send(MediaPlayer.Event.SeekableChanged, 1L)
        send(MediaPlayer.Event.PausableChanged, 1L)
        engine.play()
        return true
    }

    /**
     * S-904: the track to follow the one playing, decoded ahead and started in the same output
     * buffer; null when nothing follows in the engine (the manager then hears EndReached).
     * S-905: with a crossfade set, the two overlap, unless [current] and [mw] are an album's
     * consecutive tracks and the setting keeps those joins gapless.
     */
    fun setNext(mw: MediaWrapper?, current: MediaWrapper? = null): Boolean {
        val engine = engine ?: return false
        val uri = mw?.uri
        if (uri == null) {
            if (nextId != -1L) {
                nextId = -1L
                engine.setNext(-1L, null)
            }
            return true
        }
        val crossfade = applyCrossfadeSettings() > 0 && !(crossfadeSkipsAlbum && consecutiveInAlbum(current, mw))
        val id = ++ids
        if (!engine.setNext(id, uri, crossfade)) {
            nextId = -1L
            return false
        }
        nextId = id
        return true
    }

    private val crossfadeSkipsAlbum: Boolean get() = Settings.getInstance(context).getBoolean(NAGMEDAR_CROSSFADE_SKIP_ALBUM, true)

    /** Reads the crossfade settings into the engine; returns its length in milliseconds (0 when off or bit-perfect). */
    fun applyCrossfadeSettings(): Int {
        val engine = engine ?: return 0
        val settings = Settings.getInstance(context)
        val ms = settings.getInt(NAGMEDAR_CROSSFADE_MS, 0).coerceIn(0, 12_000)
        val curve = if (settings.getString(NAGMEDAR_CROSSFADE_CURVE, NAGMEDAR_CROSSFADE_EQUAL_POWER) == NAGMEDAR_CROSSFADE_LINEAR) CrossfadeCurve.Linear else CrossfadeCurve.EqualPower
        engine.setCrossfade(ms, curve)
        return ms
    }

    /** Whether [next] follows [current] on the same album and disc: a join that is meant to be gapless. */
    private fun consecutiveInAlbum(current: MediaWrapper?, next: MediaWrapper): Boolean {
        if (current == null || current.albumId <= 0L || current.albumId != next.albumId) return false
        if (current.discNumber != next.discNumber) return false
        return next.trackNumber == current.trackNumber + 1
    }

    override fun play() {
        engine?.play()
    }

    override fun pause(): Boolean {
        val engine = engine ?: return false
        if (engine.state != EngineState.Playing || !pausable) return false
        engine.pause()
        return true
    }

    override fun stop() {
        engine?.stop()
        PlayerController.playbackState = PlaybackStateCompat.STATE_STOPPED
        stopTicker()
        seekable = false
        pausable = false
        playingUri = null
        updateProgress(0L, 0L)
        lastTime = 0L
        send(MediaPlayer.Event.Stopped)
    }

    override fun isPlaying() = engine?.state == EngineState.Playing

    override fun isPaused() = engine?.state == EngineState.Paused

    override fun setTime(time: Long, fast: Boolean) {
        if (!seekable) return
        engine?.seek(time)
        updateProgress(time)
    }

    override fun getCurrentTime() = engine?.timeMs ?: progress.value?.time ?: 0L

    override fun getLength() = engine?.lengthMs?.takeIf { it > 0 } ?: progress.value?.length ?: 0L

    override fun getRate() = rate

    /** The speed: the engine's tempo (S-909), the pitch kept, as libVLC's rate is. */
    override fun setRate(rate: Float, save: Boolean) {
        this.rate = rate.coerceIn(NativeAudio.TEMPO_MIN.toFloat(), NativeAudio.TEMPO_MAX.toFloat())
        engine?.tempo = this.rate.toDouble()
        speed.postValue(this.rate)
    }

    override fun getVolume() = volume

    /** VLC's percent, 100 = the samples as they are; linear, as the Android output applies it. */
    override fun setVolume(volume: Int): Int {
        this.volume = volume.coerceIn(0, 200)
        engine?.volume = this.volume / 100.0
        return 0
    }

    override fun release() {
        stopTicker()
        if (engine != null) Settings.getInstance(context).unregisterOnSharedPreferenceChangeListener(settingsListener)
        engine?.close()
        engine = null
        listener = null
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = launch {
            while (isActive) {
                val engine = engine ?: break
                val time = engine.timeMs
                val length = engine.lengthMs
                if (time != lastTime) {
                    lastTime = time
                    updateProgress(time, length)
                    send(MediaPlayer.Event.TimeChanged, time)
                    if (length > 0) sendFloat(MediaPlayer.Event.PositionChanged, (time.toDouble() / length).toFloat())
                }
                delay(TICK_MS)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun updateProgress(time: Long = progress.value?.time ?: 0L, length: Long = progress.value?.length ?: 0L) {
        progress.value = progress.value?.apply {
            this.time = time
            this.length = length
        } ?: Progress(time, length)
    }

    /** A libVLC player event as the engine raises it; the constructors are the parent's. */
    private class EngineEvent : MediaPlayer.Event {
        constructor(type: Int, arg1: Long) : super(type, arg1)
        constructor(type: Int, argf: Float) : super(type, argf)
    }

    companion object {
        private const val TAG = "NagmedarAudio"
        private const val TICK_MS = 250L
    }
}

package org.videolan.vlc.media

import android.content.Context
import android.net.Uri
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.lifecycle.MutableLiveData
import app.nagmedar.audio.EngineState
import app.nagmedar.audio.MusicEngine
import app.nagmedar.audio.MusicFormats
import app.nagmedar.audio.OutputFormat
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
import org.videolan.tools.NAGMEDAR_MUSIC_ENGINE
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
    private var trackId = 0L
    private var playingUri: Uri? = null
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
            val length = engine?.lengthMs ?: 0L
            updateProgress(engine?.timeMs ?: 0L, length)
            send(MediaPlayer.Event.LengthChanged, length)
        }

        override fun onTrackEnded(id: Long) = Unit

        override fun onQueueEmpty() {
            /* the playlist manager's own next(): a gapless hand-over comes with S-904 */
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
        engine = created
        return created
    }

    /**
     * Plays [mw] from [time] with events to [listener]; false when the engine cannot open it,
     * in which case the manager plays it with libVLC.
     */
    fun startPlayback(mw: MediaWrapper, listener: MediaPlayerEventListener, time: Long): Boolean {
        val engine = engine() ?: return false
        val uri = mw.uri ?: return false
        this.listener = listener
        trackId++
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

    /** The speed; S-909 brings the tempo change to the engine, until then the value is kept. */
    override fun setRate(rate: Float, save: Boolean) {
        this.rate = rate
        speed.postValue(rate)
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

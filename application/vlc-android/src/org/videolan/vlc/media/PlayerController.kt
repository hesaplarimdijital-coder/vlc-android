package org.videolan.vlc.media

import android.content.Context
import android.support.v4.media.session.PlaybackStateCompat
import android.widget.Toast
import app.nagmedar.audio.latency.AudioDelayMix
import app.nagmedar.playback.EngineId
import app.nagmedar.playback.EngineMedia
import app.nagmedar.playback.PlaybackEngine
import androidx.annotation.MainThread
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import org.videolan.BuildConfig
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMediaList
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.VLCInstance
import org.videolan.resources.VLCOptions
import org.videolan.tools.KEY_EQUALIZER_ENABLED
import org.videolan.tools.*
import org.videolan.vlc.*
import org.videolan.vlc.gui.dialogs.adapters.VlcTrack
import org.videolan.vlc.repository.EqualizerRepository
import org.videolan.vlc.repository.SlaveRepository
import kotlin.math.absoluteValue

/** The libVLC player; Nağmedar's PlaybackEngine contract (S-501) is the transport subset PlaylistManager uses. */
class PlayerController(val context: Context) : MediaPlayer.EventListener, CoroutineScope, PlaybackEngine {
    override val coroutineContext = Dispatchers.Main.immediate + SupervisorJob()

    //    private val exceptionHandler by lazy(LazyThreadSafetyMode.NONE) { CoroutineExceptionHandler { _, _ -> onPlayerError() } }
    private val playerContext by lazy(LazyThreadSafetyMode.NONE) { newSingleThreadContext("vlc-player") }
    private val settings by lazy(LazyThreadSafetyMode.NONE) { Settings.getInstance(context) }
    val progress by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<Progress>().apply { value = Progress() } }
    val speed by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<Float>().apply { value = 1.0F } }
    private val slaveRepository by lazy { SlaveRepository.getInstance(context) }

    var mediaplayer = newMediaPlayer()
        private set
    override var seekable = false
    override var pausable = false
    @Volatile var hasRenderer = false
        private set

    fun canDoPassthrough() = mediaplayer.hasMedia() && !mediaplayer.isReleased && mediaplayer.canDoPassthrough()

    fun getMedia(): IMedia? = mediaplayer.media

    override val engineId = EngineId.LibVlc

    /** libVLC plays everything the music engine (S-901) does not take: streams, radio, podcasts, music it cannot, films as audio. */
    override fun canPlay(media: EngineMedia) = true

    override fun play() {
        if (mediaplayer.hasMedia() && !mediaplayer.isReleased) mediaplayer.play()
    }

    override fun pause(): Boolean {
        if (isPlaying() && mediaplayer.hasMedia() && pausable) {
            mediaplayer.pause()
            return true
        }
        return false
    }

    override fun stop() {
        if (mediaplayer.hasMedia() && !mediaplayer.isReleased) mediaplayer.stop()
        setPlaybackStopped()
    }

    private fun releaseMedia(player: MediaPlayer = mediaplayer) = player.media?.let {
        it.setEventListener(null)
        it.release()
    }

    private var mediaplayerEventListener: MediaPlayerEventListener? = null
    internal suspend fun startPlayback(media: IMedia, listener: MediaPlayerEventListener, time: Long) {
        mediaplayerEventListener = listener
        resetPlaybackState(time, media.duration)
        mediaplayer.setEventListener(null)
        withContext(Dispatchers.IO) { if (!mediaplayer.isReleased) mediaplayer.media = media.apply { if (hasRenderer) parse() } }
        mediaplayer.setEventListener(this@PlayerController)
        if (!mediaplayer.isReleased) {
            if (Settings.getInstance(context).getBoolean(KEY_EQUALIZER_ENABLED, false)) withContext(Dispatchers.IO) {
                val repository = EqualizerRepository.getInstance(context)
                mediaplayer.setEqualizer(repository.getCurrentEqualizer(context).getEqualizer())
            }
            mediaplayer.play()
            // S-613: libVLC starts every media at no delay; the output's own goes back under it
            audioDelay.mediaStarted()
            audioDelay.casting = hasRenderer
            if (audioDelay.outputLatencyUs != 0L) applyAudioDelay()
        }
    }

    private fun resetPlaybackState(time: Long, duration: Long) {
        seekable = true
        pausable = true
        lastTime = time
        updateProgress(time, duration)
    }

    @MainThread
    fun restart() {
        val mp = mediaplayer
        mediaplayer = newMediaPlayer()
        if (userVolume != 100) mediaplayer.volume = userVolume
        release(mp)
    }

    fun setPosition(position: Float) {
        if (seekable && mediaplayer.hasMedia() && !mediaplayer.isReleased) mediaplayer.position = position
    }

    override fun setTime(time: Long, fast: Boolean) {
        if (seekable && mediaplayer.hasMedia() && !mediaplayer.isReleased) mediaplayer.setTime(time, fast)
    }

    override fun isPlaying() = playbackState == PlaybackStateCompat.STATE_PLAYING

    override fun isPaused() = playbackState == PlaybackStateCompat.STATE_PAUSED

    fun getAudioTracksCount() = if (!mediaplayer.isReleased && mediaplayer.hasMedia()) mediaplayer.getAudioTracksCount() else 0

    fun getAudioTracks(): Array<out VlcTrack>? = if (!mediaplayer.isReleased && mediaplayer.hasMedia()) mediaplayer.getAllAudioTracks() else emptyArray()

    fun getAudioTrack():String = if (!mediaplayer.isReleased && mediaplayer.hasMedia()) mediaplayer.getSelectedAudioTrack()?.getId() ?: "-1" else "-1"

    fun setAudioTrack(index: String) = !mediaplayer.isReleased && mediaplayer.hasMedia() && mediaplayer.setAudioTrack(index)

    fun setAudioDigitalOutputEnabled(enabled: Boolean) = !mediaplayer.isReleased && mediaplayer.setAudioDigitalOutputEnabled(enabled)

    /** S-613: the user's audio delay and the output device's, which libVLC gets together; not the device's while casting. */
    private val audioDelay = AudioDelayMix()

    /**
     * S-613: what the output device plays late (its latency profile), in microseconds; applied
     * under the audio delay the user sets, which stays the file's own. The observer that follows
     * the output devices sets it.
     */
    var outputLatencyUs: Long
        get() = audioDelay.outputLatencyUs
        set(value) {
            if (audioDelay.outputLatencyUs == value) return
            audioDelay.outputLatencyUs = value
            applyAudioDelay()
        }

    /** The audio delay as the user sees it: the file's own, the output's share left out. */
    fun getAudioDelay() = if (mediaplayer.hasMedia() && !mediaplayer.isReleased) audioDelay.userDelayUs else 0L

    override fun getRate() = if (mediaplayer.hasMedia() && !mediaplayer.isReleased && playbackState != PlaybackStateCompat.STATE_STOPPED) mediaplayer.rate else 1.0f

    fun setAudioDelay(delay: Long): Boolean {
        audioDelay.userDelayUs = delay
        return applyAudioDelay()
    }

    /** The user's delay and the output's together into libVLC; false while there is no media to take it. */
    private fun applyAudioDelay(): Boolean {
        audioDelay.casting = hasRenderer
        return !mediaplayer.isReleased && mediaplayer.hasMedia() && mediaplayer.setAudioDelay(audioDelay.combinedUs)
    }

    fun setEqualizer(equalizer: MediaPlayer.Equalizer?) = mediaplayer.setEqualizer(equalizer)

    fun setRenderer(renderer: RendererItem?) {
        if (!mediaplayer.isReleased) mediaplayer.setRenderer(renderer)
        hasRenderer = renderer !== null
        // S-1302: the output's latency profile (S-613) is left out while casting, and back after
        val delay = audioDelay.rendererChanged(hasRenderer)
        if (!mediaplayer.isReleased && mediaplayer.hasMedia()) mediaplayer.setAudioDelay(delay)
    }

    override fun release() = release(mediaplayer)

    fun release(player: MediaPlayer = mediaplayer) {
        player.setEventListener(null)
        // restart() releases the player it replaced: its own media, not the new one's
        releaseMedia(player)
        launch(Dispatchers.IO) {
            if (BuildConfig.DEBUG) { // Warn if player release is blocking
                try {
                    withTimeout(5000) { player.release() }
                } catch (exception: TimeoutCancellationException) {
                    launch { Toast.makeText(context, "media stop has timeouted!", Toast.LENGTH_LONG).show() }
                }
            } else player.release()
        }
        setPlaybackStopped()
    }

    fun setSlaves(media: IMedia, mw: MediaWrapper) = launch {
        if (mediaplayer.isReleased) return@launch
        val slaves = mw.slaves
        slaves?.let { it.forEach { slave -> media.addSlave(slave) } }
        media.release()
        slaveRepository.getSlaves(mw.location).forEach { slave ->
            if (!slaves.contains(slave)) mediaplayer.addSlave(slave.type, slave.uri.toUri(), false)
        }
        slaves?.let { slaveRepository.saveSlaves(mw) }
    }

    private fun newMediaPlayer() : MediaPlayer {
        return MediaPlayer(VLCInstance.getInstance(context)).apply {
            setAudioDigitalOutputEnabled(VLCOptions.isAudioDigitalOutputEnabled(settings))
            VLCOptions.getAout(settings)?.let { setAudioOutput(it) }
            setRenderer(PlaybackService.renderer.value)
        }
    }

    override fun getCurrentTime() = progress.value?.time ?: 0L

    override fun getLength() = progress.value?.length ?: 0L

    override fun setRate(rate: Float, save: Boolean) {
        if (mediaplayer.isReleased) return
        mediaplayer.rate = rate
        speed.postValue(rate)
    }

    /**
     * Update current media meta and return true if player needs to be updated
     *
     * @param id of the Meta event received, -1 for none
     * @return true if UI needs to be updated
     */
    internal fun updateCurrentMeta(id: Int, mw: MediaWrapper?): Boolean {
        if (id == IMedia.Meta.Publisher) return false
        mw?.updateMeta(mediaplayer)
        return id != IMedia.Meta.NowPlaying || mw?.nowPlaying !== null
    }

    fun getChapters(title: Int): Array<out MediaPlayer.Chapter>? = if (!mediaplayer.isReleased) mediaplayer.getChapters(title) else emptyArray()

    fun getTitles(): Array<out MediaPlayer.Title>? = if (!mediaplayer.isReleased) mediaplayer.titles else emptyArray()

    fun getChapterIdx() = if (!mediaplayer.isReleased) mediaplayer.chapter else -1

    fun setChapterIdx(chapter: Int) {
        if (!mediaplayer.isReleased) mediaplayer.chapter = chapter
    }

    fun getTitleIdx() = if (!mediaplayer.isReleased) mediaplayer.title else -1

    fun setTitleIdx(title: Int) {
        if (!mediaplayer.isReleased)  mediaplayer.title = title
    }

    /**
     * Nağmedar: the volume asked for, VLC's percent (100 = the samples as they are). libVLC 4 keeps a
     * volume only once an audio output exists, so before the first sound plays it reports none
     * (a negative number, which read as 0 in the new interface's slider, and once set back, muted
     * the phone); this is what the interface shows and what every new output gets.
     */
    private var userVolume = 100

    override fun getVolume(): Int {
        val reported = if (!mediaplayer.isReleased) mediaplayer.volume else -1
        return if (reported >= 0) reported else userVolume
    }

    override fun setVolume(volume: Int): Int {
        userVolume = volume.coerceIn(0, 200)
        return if (!mediaplayer.isReleased) mediaplayer.setVolume(userVolume) else -1
    }

    /** The output is there now (Playing): the volume asked for, where the output started at another. */
    private fun applyUserVolume() {
        if (mediaplayer.isReleased) return
        val reported = mediaplayer.volume
        if (reported >= 0 && reported != userVolume) mediaplayer.setVolume(userVolume)
    }

    suspend fun expand(): IMediaList? {
        return mediaplayer.media?.let {
            return withContext(playerContext) {
                mediaplayer.setEventListener(null)
                val items = it.subItems()
                it.release()
                mediaplayer.setEventListener(this@PlayerController)
                items
            }
        }
    }

    private var lastTime = 0L
    var lastPosition = 0F
    @OptIn(ObsoleteCoroutinesApi::class)
    private val eventActor = actor<MediaPlayer.Event>(capacity = Channel.UNLIMITED, start = CoroutineStart.UNDISPATCHED) {
        for (event in channel) {
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    playbackState = PlaybackStateCompat.STATE_PLAYING
                    // S-613: the output's delay again, in case the input was not there to take it at the start
                    if (audioDelay.outputLatencyUs != 0L) applyAudioDelay()
                    applyUserVolume()
                }
                MediaPlayer.Event.Paused -> playbackState = PlaybackStateCompat.STATE_PAUSED
                MediaPlayer.Event.EncounteredError -> setPlaybackStopped()
                MediaPlayer.Event.PausableChanged -> pausable = event.pausable
                MediaPlayer.Event.SeekableChanged -> seekable = event.seekable
                MediaPlayer.Event.LengthChanged -> updateProgress(newLength = event.lengthChanged)
                MediaPlayer.Event.TimeChanged -> {
                    val time = event.timeChanged
                    if ((time - lastTime).absoluteValue > 950L) {
                        updateProgress(newTime = time)
                        lastTime = time
                    }
                }
                MediaPlayer.Event.PositionChanged -> {
                    lastPosition = event.positionChanged

                }
            }
            mediaplayerEventListener?.onEvent(event)
        }
    }

    @JvmOverloads
    fun updateProgress(newTime: Long = progress.value?.time ?: 0L, newLength: Long = progress.value?.length ?: 0L) {
        progress.value = progress.value?.apply { time = newTime; length = newLength }
    }

    override fun onEvent(event: MediaPlayer.Event?) {
        if (event != null) eventActor.trySend(event)
    }

    private fun setPlaybackStopped() {
        playbackState = PlaybackStateCompat.STATE_STOPPED
        updateProgress(0L, 0L)
        lastTime = 0L
    }

    //    private fun onPlayerError() {
//        launch(UI) {
//            restart()
//            Toast.makeText(context, context.getString(R.string.feedback_player_crashed), Toast.LENGTH_LONG).show()
//        }
//    }
    companion object {
        /** The state of the engine playing: libVLC's, or the music engine's (S-901), which sets it too. */
        @Volatile var playbackState = PlaybackStateCompat.STATE_NONE
            internal set
    }
}

const val NO_LENGTH_PROGRESS_MAX = 1000
class Progress(var time: Long = 0L, var length: Long = 0L)

internal interface MediaPlayerEventListener {
    suspend fun onEvent(event: MediaPlayer.Event)
}

private fun Array<IMedia.Slave>?.contains(item: IMedia.Slave) : Boolean {
    if (this == null) return false
    for (slave in this) if (slave.uri == item.uri) return true
    return false
}
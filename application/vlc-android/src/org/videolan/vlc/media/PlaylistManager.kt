package org.videolan.vlc.media

import android.content.Intent
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.MutableLiveData
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import app.nagmedar.audio.FileTags
import app.nagmedar.audio.cue.CueSheet
import app.nagmedar.audio.cue.CueSheets
import app.nagmedar.audio.cue.CueUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.FactoryManager
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.RendererItem
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.interfaces.IMediaFactory
import org.videolan.libvlc.util.AndroidUtil
import org.videolan.medialibrary.MLServiceLocator
import org.videolan.medialibrary.interfaces.Medialibrary
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.AndroidDevices
import org.videolan.resources.AppContextProvider
import org.videolan.resources.EXIT_PLAYER
import org.videolan.resources.PLAYLIST_TYPE_AUDIO
import org.videolan.resources.PLAYLIST_TYPE_VIDEO
import org.videolan.resources.PLAY_FROM_SERVICE
import org.videolan.resources.DecodingHook
import org.videolan.resources.NetworkMediaHook
import org.videolan.resources.SubtitleEncodingHook
import org.videolan.resources.VLCInstance
import org.videolan.resources.VLCOptions
import org.videolan.resources.util.VLCCrashHandler
import org.videolan.tools.AUDIO_DELAY_GLOBAL
import org.videolan.tools.AUDIO_RESUME_PLAYBACK
import org.videolan.tools.AUDIO_SHUFFLING
import org.videolan.tools.AUDIO_STOP_AFTER
import org.videolan.tools.AppScope
import org.videolan.tools.DAV1D_THREAD_NUMBER
import org.videolan.tools.HTTP_USER_AGENT
import org.videolan.tools.KEY_ALWAYS_FAST_SEEK
import org.videolan.tools.KEY_AUDIO_CONFIRM_RESUME
import org.videolan.tools.KEY_AUDIO_FORCE_SHUFFLE
import org.videolan.tools.KEY_AUDIO_LAST_PLAYLIST
import org.videolan.tools.KEY_CURRENT_AUDIO
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_ARTIST
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_THUMB
import org.videolan.tools.KEY_CURRENT_AUDIO_RESUME_TITLE
import org.videolan.tools.KEY_CURRENT_MEDIA
import org.videolan.tools.KEY_CURRENT_MEDIA_IS_AUDIO
import org.videolan.tools.KEY_CURRENT_MEDIA_RESUME
import org.videolan.tools.KEY_INCOGNITO
import org.videolan.tools.KEY_INCOGNITO_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE
import org.videolan.tools.KEY_INCOGNITO_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE
import org.videolan.tools.KEY_MEDIA_LAST_PLAYLIST
import org.videolan.tools.KEY_MEDIA_LAST_PLAYLIST_RESUME
import org.videolan.tools.KEY_PLAYBACK_SPEED_AUDIO_GLOBAL
import org.videolan.tools.KEY_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE
import org.videolan.tools.KEY_PLAYBACK_SPEED_VIDEO_GLOBAL
import org.videolan.tools.KEY_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE
import org.videolan.tools.KEY_SAVE_INDIVIDUAL_AUDIO_DELAY
import org.videolan.tools.KEY_VIDEO_APP_SWITCH
import org.videolan.tools.KEY_VIDEO_CONFIRM_RESUME
import org.videolan.tools.MEDIA_SHUFFLING
import org.videolan.tools.PLAYBACK_HISTORY
import org.videolan.tools.POSITION_IN_AUDIO_LIST
import org.videolan.tools.POSITION_IN_MEDIA
import org.videolan.tools.POSITION_IN_MEDIA_LIST
import org.videolan.tools.POSITION_IN_SONG
import org.videolan.tools.SLEEP_TIMER_DEFAULT_INTERVAL
import org.videolan.tools.SLEEP_TIMER_DEFAULT_RESET_INTERACTION
import org.videolan.tools.SLEEP_TIMER_DEFAULT_WAIT
import org.videolan.tools.Settings
import org.videolan.tools.VIDEO_PAUSED
import org.videolan.tools.VIDEO_RESUME_PLAYBACK
import org.videolan.tools.VIDEO_SPEED
import org.videolan.tools.isAppStarted
import org.videolan.tools.putSingle
import org.videolan.vlc.BuildConfig
import org.videolan.vlc.PlaybackService
import org.videolan.vlc.R
import org.videolan.vlc.gui.browser.BaseBrowserFragment
import org.videolan.vlc.gui.video.VideoPlayerActivity
import org.videolan.vlc.repository.SlaveRepository
import org.videolan.vlc.util.FileUtils
import org.videolan.vlc.util.FontCache
import org.videolan.vlc.util.awaitMedialibraryStarted
import org.videolan.vlc.util.isSchemeFD
import org.videolan.vlc.util.isSchemeHttpOrHttps
import org.videolan.vlc.util.isSchemeStreaming
import org.videolan.vlc.util.setResumeProgram
import org.videolan.vlc.util.updateNextProgramAfterThumbnailGeneration
import org.videolan.vlc.util.updateWithMLMeta
import org.videolan.vlc.util.validateLocation
import android.net.Uri
import app.nagmedar.playback.CastHandover
import app.nagmedar.playback.CastItem
import app.nagmedar.playback.EngineHost
import app.nagmedar.playback.EngineId
import app.nagmedar.playback.EngineMedia
import app.nagmedar.playback.EngineRouter
import app.nagmedar.playback.Handover
import app.nagmedar.playback.HandoverHost
import app.nagmedar.playback.PlaybackEngine
import java.security.SecureRandom
import java.util.Calendar
import java.util.Locale
import java.util.Stack
import kotlin.math.max

private const val TAG = "VLC/PlaylistManager"
private const val PREVIOUS_LIMIT_DELAY = 5000L
private const val PLAYLIST_AUDIO_REPEAT_MODE_KEY = "audio_repeat_mode"
private const val PLAYLIST_VIDEO_REPEAT_MODE_KEY = "video_repeat_mode"

class PlaylistManager(val service: PlaybackService) : MediaWrapperList.EventListener, IMedia.EventListener, CoroutineScope {
    private var endReachedFor: String? = null
    override val coroutineContext = Dispatchers.Main.immediate + SupervisorJob()

    companion object {
        val showAudioPlayer = MutableLiveData<Boolean>().apply { value = false }
        val currentPlayedMedia = MutableLiveData<MediaWrapper?>().apply { value = null }
        val playingState = MutableLiveData<Boolean>().apply { value = false }
        // The playback will periodically modify the media by saving its meta
        // When playing audio, it will have no impact on UI but will trigger the media modified ML callback
        // On slow devices, it will trigger an unwanted "refresh animation". This flag prevents it
        var skipMediaUpdateRefresh = false
        private val mediaList = MediaWrapperList()
        fun hasMedia() = mediaList.size() != 0
        val repeating = MutableStateFlow(PlaybackStateCompat.REPEAT_MODE_NONE)
        val shuffling = MutableStateFlow(false)
        var playingAsAudio = false
    }

    private val medialibrary by lazy(LazyThreadSafetyMode.NONE) { Medialibrary.getInstance() }
    val player by lazy(LazyThreadSafetyMode.NONE) { PlayerController(service.applicationContext) }

    /**
     * Engine selection (S-501, ADR-05): Nağmedar Ses Motoru for the local music library (S-901),
     * libVLC ([player]) for everything else.
     */
    val musicEngine by lazy(LazyThreadSafetyMode.NONE) {
        MusicEngineController(service.applicationContext, player.progress, player.speed).also { it.onHandOver = { onEngineHandOver() } }
    }
    val engines by lazy(LazyThreadSafetyMode.NONE) { EngineHost(EngineRouter(listOf(musicEngine, player))) }

    /** The engine playing the current item: the transport goes through it. */
    val engine: PlaybackEngine get() = engines.current

    /**
     * S-904: the engine handed over to the next item without a gap, so the indices follow as
     * next() and playIndex() would have moved them; the Playing event that follows loads the
     * new item's meta. Repeat one plays the same item again.
     */
    private fun onEngineHandOver() {
        clearABRepeat()
        getCurrentMedia()?.addFlags(MediaWrapper.MEDIA_FROM_START)
        if (repeating.value != PlaybackStateCompat.REPEAT_MODE_ONE && isValidPosition(nextIndex)) {
            previous.push(currentIndex)
            currentIndex = nextIndex
        }
        newMedia = true
        launch {
            determinePrevAndNextIndices()
            service.onNewPlayback()
        }
    }

    /**
     * S-904: what the music engine decodes ahead: the next item when it plays in the engine too
     * (the same item again on repeat one), nothing when the list ends, the next is libVLC's or
     * playback stops after this item.
     */
    private fun syncEngineNext() {
        if (engine !== musicEngine) return
        val next = when {
            stopAfter == currentIndex -> null
            repeating.value == PlaybackStateCompat.REPEAT_MODE_ONE -> getCurrentMedia()
            isValidPosition(nextIndex) -> mediaList.getMedia(nextIndex)
            else -> null
        }
        musicEngine.setNext(next?.takeIf { musicEngine.canPlay(it.toEngineMedia()) }, getCurrentMedia())
    }
    private val settings by lazy(LazyThreadSafetyMode.NONE) { Settings.getInstance(service) }
    private val ctx by lazy(LazyThreadSafetyMode.NONE) { service.applicationContext }
    var currentIndex = -1
        set(value) {
            field = value
            currentPlayedMedia.postValue(mediaList.getMedia(value))
        }
    private var nextIndex = -1
    private var prevIndex = -1
    private var previous = Stack<Int>()
    var stopAfter = -1

    /**
     * Playback stops after the item at [position] (-1: it goes on). The music engine decodes the
     * next track ahead (S-904): when the item playing is the one, it is told at once that nothing
     * follows; Nağmedar's sleep timer (S-1007) and the classic "stop after" both come here.
     */
    fun setStopAfterPosition(position: Int) {
        val was = stopAfter
        stopAfter = position
        if (position == currentIndex || was == currentIndex) syncEngineNext()
    }

    var shuffling: Boolean = false
        set(value) {
            field = value
            AppScope.launch {
                PlaylistManager.shuffling.emit(value)
            }
        }
    var videoBackground = false
        private set
    var isBenchmark = false
    var isHardware = false
    var browserAudioActive = false
    private var preBrowserVolume = -1
    private var parsed = false
    var savedTime = 0L

    /**
     * S-1302: the item handed over between the music engine and libVLC, to go on where it was;
     * [film]: a film cast until now, which the video player takes back.
     */
    private class CastResume(val index: Int, val uri: Uri, val positionMs: Long, val paused: Boolean, val film: Boolean = false)

    private var castResume: CastResume? = null

    /** S-1302: casting ends for something else to play here: the film cast until now is not brought back. */
    private var quietCastEnd = false

    /** S-1302: tracker modules passed over in a row while casting: a queue of nothing else stops. */
    private var castSkips = 0
    private var random = SecureRandom()
    private var newMedia = false
    @Volatile
    private var expanding = false
    @Volatile
    private var entryUrl : String? = null


    /** The stream or playlist that was expanded, and the addresses of the media it gave. */
    private class Expansion(val from: String, val children: Set<String>)

    @Volatile
    private var expansion: Expansion? = null

    /**
     * S-1106: the address of the stream or playlist the current media was expanded from (the radio
     * strip follows a station through its playlist, of one entry or many); null when the current
     * media is not one of the entries of the last expansion (another media has taken over).
     */
    val expandedFrom: String?
        get() = expansion?.takeIf { getCurrentMedia()?.uri?.toString() in it.children }?.from
    val abRepeat by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<ABRepeat>().apply { value = ABRepeat() } }
    val abRepeatOn by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<Boolean>().apply { value = false } }
    val videoStatsOn by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<Boolean>().apply { value = false } }
    val delayValue by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<DelayValues>().apply { value = DelayValues() } }
    val waitForConfirmation by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<WaitConfirmation?>().apply { value = null } }
    val waitForConfirmationAudio by lazy(LazyThreadSafetyMode.NONE) { MutableLiveData<WaitConfirmation?>().apply { value = null } }
    private var lastPrevious = -1L

    private val mediaFactory = FactoryManager.getFactory(IMediaFactory.factoryId) as IMediaFactory
    lateinit var videoResumeStatus: ResumeStatus
    lateinit var audioResumeStatus: ResumeStatus

    fun hasCurrentMedia() = isValidPosition(currentIndex)

    fun canRepeat() = mediaList.size() > 0

    fun hasPlaylist() = mediaList.size() > 1

    fun canShuffle() = mediaList.size() > 2

    fun isValidPosition(position: Int) = position in 0 until mediaList.size()
    private var shouldDisableCookieForwarding: Boolean = false

    init {
        AppScope.launch { repeating.emit(settings.getInt(PLAYLIST_AUDIO_REPEAT_MODE_KEY, PlaybackStateCompat.REPEAT_MODE_NONE)) }
        resetResumeStatus()
    }

    fun resetResumeStatus() {
        val string = settings.getString(KEY_VIDEO_CONFIRM_RESUME, "0")
        videoResumeStatus = if (string == "2") ResumeStatus.ASK else if (string == "0") ResumeStatus.ALWAYS else ResumeStatus.NEVER
        val audio = settings.getString(KEY_AUDIO_CONFIRM_RESUME, "0")
        audioResumeStatus = if (audio == "2") ResumeStatus.ASK else if (audio == "0") ResumeStatus.ALWAYS else ResumeStatus.NEVER
        waitForConfirmation.postValue(null)
        waitForConfirmationAudio.postValue(null)
    }

    /**
     * Loads a selection of files (a non-user-supplied collection of media)
     * into the primary or "currently playing" playlist.
     *
     * @param mediaPathList A list of locations to load
     * @param position The position to start playing at
     */
    @MainThread
    fun loadLocations(mediaPathList: List<String>, position: Int) {
        if (BuildConfig.BETA) {
            Log.d(TAG, "loadLocations with values: ", Exception("Call stack"))
            mediaPathList.forEach {
                try {
                    Log.d(TAG, "Media location: $it")
                } catch (e: NullPointerException) {
                    Log.d(TAG, "Media crash", e)
                }
            }
        }
        launch {
            val mediaList = ArrayList<MediaWrapper>()
            withContext(Dispatchers.IO) {
                for (location in mediaPathList) {
                    var mediaWrapper = medialibrary.getMedia(location)
                    if (mediaWrapper === null) {
                        if (!location.validateLocation()) {
                            Log.w(TAG, "Invalid location $location")
                            service.showToast(if (location.toUri().scheme == "missing")service.resources.getString(R.string.missing_location) else service.resources.getString(R.string.invalid_location,  location), Toast.LENGTH_SHORT)
                            continue
                        }
                        Log.v(TAG, "Creating on-the-fly Media object for $location")
                        mediaWrapper = MLServiceLocator.getAbstractMediaWrapper(location.toUri())
                        if (BuildConfig.BETA) Log.d(TAG, "Adding $mediaWrapper to the queue")
                        mediaList.add(mediaWrapper)
                    } else {
                        if (BuildConfig.BETA) Log.d(TAG, "Adding $mediaWrapper to the queue")
                        mediaList.add(mediaWrapper)
                    }
                }
            }
            load(mediaList, position)
        }
    }

    @MainThread
    suspend fun load(list: List<MediaWrapper>, position: Int, mlUpdate: Boolean = false, avoidErasingStop:Boolean = false) {
        saveMediaList()
        savePosition()
        mediaList.removeEventListener(this@PlaylistManager)
        previous.clear()
        videoBackground = false
        if (BuildConfig.BETA) {
            Log.d(TAG, "load with values: ", Exception("Call stack"))
            list.forEach {
                try {
                    Log.d(TAG, "Media location: ${it.uri}")
                } catch (e: NullPointerException) {
                    Log.d(TAG, "Media crash", e)
                }
            }
        }
        mediaList.replaceWith(list)
        if (!hasMedia()) {
            Log.w(TAG, "Warning: empty media list, nothing to play !")
            return
        }
        currentIndex = if (isValidPosition(position)) position else 0

        // Add handler after loading the list
        mediaList.addEventListener(this@PlaylistManager)
        val instance = Settings.getInstance(AppContextProvider.appContext)
        if (!avoidErasingStop) instance.putSingle(AUDIO_STOP_AFTER, -1)
        stopAfter = instance.getInt(AUDIO_STOP_AFTER, -1)
        if (stopAfter < position) stopAfter = -1
        clearABRepeat()
        engine.setRate(1.0f, false)
        playIndex(currentIndex)
        service.onPlaylistLoaded()
        if (mlUpdate) {
            service.awaitMedialibraryStarted()
            mediaList.replaceWith(withContext(Dispatchers.IO) { mediaList.copy.updateWithMLMeta() })
            getCurrentMedia()?.let { refreshTrackMeta(it) }
            if (BuildConfig.BETA) {
                Log.d(TAG, "load after ml update with values: ")
                mediaList.copy.forEach { Log.d(TAG, "Media location: ${it.uri}") }
            }
            service.onMediaListChanged()
            service.showNotification()
        }
        if (settings.getBoolean(KEY_AUDIO_FORCE_SHUFFLE, false) && getCurrentMedia()?.type == MediaWrapper.TYPE_AUDIO && !shuffling && canShuffle()) shuffle()
        if (settings.getLong(SLEEP_TIMER_DEFAULT_INTERVAL, -1L) != -1L) {
            service.waitForMediaEnd = settings.getBoolean(SLEEP_TIMER_DEFAULT_WAIT, false)
            service.resetOnInteraction = settings.getBoolean(SLEEP_TIMER_DEFAULT_RESET_INTERACTION, false)
            val sleepTime = Calendar.getInstance()
            sleepTime.timeInMillis += settings.getLong(SLEEP_TIMER_DEFAULT_INTERVAL, -1L)
            sleepTime.set(Calendar.SECOND, 0)
            service.setSleepTimer(sleepTime)
        }
    }

    @Volatile
    private var loadingLastPlaylist = false
    fun loadLastPlaylist(type: Int = PLAYLIST_TYPE_AUDIO) : Boolean {
        if (loadingLastPlaylist) return true
        loadingLastPlaylist = true
        val currentMediaKey = when (type) {
            PLAYLIST_TYPE_AUDIO -> KEY_CURRENT_AUDIO
            PLAYLIST_TYPE_VIDEO -> KEY_CURRENT_MEDIA
            else -> KEY_CURRENT_MEDIA_RESUME
        }
        val locationsKey = when (type) {
            PLAYLIST_TYPE_AUDIO -> KEY_AUDIO_LAST_PLAYLIST
            PLAYLIST_TYPE_VIDEO -> KEY_MEDIA_LAST_PLAYLIST
            else -> KEY_MEDIA_LAST_PLAYLIST_RESUME
        }
        val audio = type == PLAYLIST_TYPE_AUDIO
        val currentMedia = settings.getString(currentMediaKey, "")
        if (currentMedia.isNullOrEmpty()) {
            loadingLastPlaylist = false
            return false
        }
        val locations = settings.getString(locationsKey, null)
                ?.split(" ".toRegex())?.dropLastWhile { it.isEmpty() }?.toTypedArray()
        if (locations.isNullOrEmpty()) {
            loadingLastPlaylist = false
            return false
        }
        launch {
            val playList = withContext(Dispatchers.IO) {
                locations.asSequence().mapTo(ArrayList(locations.size)) {
                    val uri = it.toUri()
                    // S-808: a CUE sheet's track comes back with what its sheet says of it
                    (if (CueUri.isTrack(uri)) CueMedia.restore(ctx, uri, medialibrary.getMedia(CueUri.file(uri))?.length ?: 0L) else null)
                        ?: MLServiceLocator.getAbstractMediaWrapper(uri)
                }
            }
            // load playlist
            shuffling = settings.getBoolean(if (audio) AUDIO_SHUFFLING else MEDIA_SHUFFLING, false)
            val position = max(0, settings.getInt(if (audio) POSITION_IN_AUDIO_LIST else POSITION_IN_MEDIA_LIST, 0))
            savedTime = settings.getLong(if (audio) POSITION_IN_SONG else POSITION_IN_MEDIA, -1)
            if (!audio && position < playList.size && settings.getBoolean(VIDEO_PAUSED, false)) {
                playList[position].addFlags(MediaWrapper.MEDIA_PAUSED)
            }
            if (audio && position < playList.size) playList[position].addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
            load(playList, position, mlUpdate = true, avoidErasingStop = true)
            loadingLastPlaylist = false
            if (!audio) {
                val rate = settings.getFloat(VIDEO_SPEED, engine.getRate())
                if (rate != 1.0f) engine.setRate(rate, false)
            }
        }
        return true
    }

    fun play() {
        if (hasMedia()) engine.play()
    }

    fun pause() {
        if (engine.pause()) {
            savePosition()
            launch {
                //needed to save the current media in audio mode when it's a video played as audio
                saveCurrentMedia()
                saveMediaList()
            }
            if (getCurrentMedia()?.isPodcast == true) saveMediaMeta()
        }
    }

    @MainThread
    fun next(force : Boolean = false) {
        mediaList.getMedia(currentIndex)?.let {
            if (it.type == MediaWrapper.TYPE_VIDEO || it.isPodcast) saveMediaMeta()
        }
        val size = mediaList.size()
        if (force || repeating.value != PlaybackStateCompat.REPEAT_MODE_ONE) {
            previous.push(currentIndex)
            currentIndex = nextIndex
            if (size == 0 || currentIndex < 0 || currentIndex >= size) {
                Log.w(TAG, "Warning: invalid next index, aborted !")
                stop()
                return
            }
            videoBackground = videoBackground || (!player.isVideoPlaying() && player.canSwitchToVideo())
        }
        launch { playIndex(currentIndex) }
    }

    fun restart() {
        val isPlaying = engine.isPlaying() && isAudioList()
        stop()
        if (isPlaying) PlaybackService.loadLastAudio(service)
    }

    fun stop(systemExit: Boolean = false, video: Boolean = false) {
        clearABRepeat()
        if (stopAfter != -1) Settings.getInstance(AppContextProvider.appContext).putSingle(AUDIO_STOP_AFTER, stopAfter)
        stopAfter = -1
        videoBackground = false
        val job = getCurrentMedia()?.let {
            savePosition(video = video)
            launch(start = CoroutineStart.UNDISPATCHED) {
                saveMediaMeta().join()
                if (AndroidDevices.isAndroidTv && AndroidUtil.isOOrLater && video) {
                    setResumeProgram(service.applicationContext, it)
                    updateNextProgramAfterThumbnailGeneration(service, service.applicationContext, it)
                }
            }
        }
        service.setSleepTimer(null)
        mediaList.removeEventListener(this)
        previous.clear()
        currentIndex = -1
        if (systemExit) {
            player.release()
            musicEngine.release()
        } else {
            // S-1009: the music engine stops too; restarting libVLC alone left it playing
            if (engine === musicEngine) musicEngine.stop()
            player.restart()
        }
        mediaList.clear()
        showAudioPlayer.value = false
        service.onPlaybackStopped(systemExit)
        //Close video player if started
        LocalBroadcastManager.getInstance(ctx).sendBroadcast(Intent(EXIT_PLAYER))
        if (systemExit) launch(start = CoroutineStart.UNDISPATCHED) {
            job?.join()
            cancel()
            player.cancel()
        }
        playingState.value = false
    }

    @MainThread
    fun previous(force: Boolean) {
        mediaList.getMedia(currentIndex)?.let { if (it.type == MediaWrapper.TYPE_VIDEO) saveMediaMeta() }
        if (hasPrevious() &&
                ((force || !engine.seekable || (engine.getCurrentTime() < PREVIOUS_LIMIT_DELAY) || (lastPrevious != -1L && System.currentTimeMillis() - lastPrevious < PREVIOUS_LIMIT_DELAY)))) {
            val size = mediaList.size()
            currentIndex = prevIndex
            if (previous.size > 0) previous.pop()
            if (size == 0 || prevIndex < 0 || currentIndex >= size) {
                Log.w(TAG, "Warning: invalid previous index, aborted !")
                engine.stop()
                return
            }
            launch { playIndex(currentIndex) }
            lastPrevious = -1L
        } else {
            if (engine === player) player.setPosition(0F) else engine.setTime(0L)
            lastPrevious = System.currentTimeMillis()
        }
    }

    @MainThread
    fun shuffle() {
        if (shuffling) previous.clear()
        shuffling = !shuffling
        savePosition()
        launch { determinePrevAndNextIndices() }
    }

    /**
     * Will set the repeating variable from the value that has been saved in settings
     */
    private fun setRepeatTypeFromSettings() {
        AppScope.launch {
            repeating.emit(if (getCurrentMedia()?.type == MediaWrapper.TYPE_VIDEO) {
                settings.getInt(PLAYLIST_VIDEO_REPEAT_MODE_KEY, PlaybackStateCompat.REPEAT_MODE_NONE)
            } else
                settings.getInt(PLAYLIST_AUDIO_REPEAT_MODE_KEY, PlaybackStateCompat.REPEAT_MODE_NONE)
            )
        }
    }

    @MainThread
    fun setRepeatType(repeatType: Int) {
        AppScope.launch { repeating.emit(repeatType) }
        if (getCurrentMedia()?.type == MediaWrapper.TYPE_VIDEO)
            settings.putSingle(PLAYLIST_VIDEO_REPEAT_MODE_KEY, repeating.value)
        else
            settings.putSingle(PLAYLIST_AUDIO_REPEAT_MODE_KEY, repeating.value)
        savePosition()
        launch { determinePrevAndNextIndices() }
    }

    fun setRenderer(item: RendererItem?) {
        val wasCasting = player.hasRenderer
        // S-1302: a film cast until now, held where it is before its sound comes out here
        val film = if (wasCasting && item == null) filmBack() else null
        quietCastEnd = false
        player.setRenderer(item)
        showAudioPlayer.value = PlayerController.playbackState != PlaybackStateCompat.STATE_STOPPED && (item !== null || !player.isVideoPlaying())
        // S-1302: the music engine's track goes on in libVLC, which sends it; back to the engine afterwards
        if (CastHandover.change(castHost, wasCasting, item != null) == Handover.StaysHere) {
            service.showToast(ctx.getString(R.string.nagmedar_cast_tracker_here), Toast.LENGTH_LONG)
        }
        // in front, the video player opens on it; in the background it waits, paused, for the player to open
        if (film != null && isAppStarted()) switchToVideo()
    }

    /** S-1302: casting is about to end for something else to play here: the film cast until now stays closed. Main thread. */
    fun castEndsForNewPlayback() {
        if (player.hasRenderer) quietCastEnd = true
    }

    /**
     * S-1302: casting a film ended from the music side (the mini player, the notification, the
     * picker) while the video player is not on screen: the film is paused where it is (nothing
     * plays out loud here meanwhile) and kept for the video player, which goes on there: playing
     * if it played and the app is in front, else paused. Null when there is no film to bring
     * back (the player on screen does it itself; a live stream that cannot pause goes on as it is).
     */
    private fun filmBack(): CastResume? {
        if (quietCastEnd || VideoPlayerActivity.shown) return null
        val media = getCurrentMedia() ?: return null
        if (media.type != MediaWrapper.TYPE_VIDEO || engines.current.engineId != EngineId.LibVlc) return null
        val index = castHost.index
        val playing = castHost.playing
        if (index < 0 || !(playing || castHost.paused)) return null
        if (playing && !player.pause()) return null
        val inFront = isAppStarted()
        media.removeFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
        // in the background its picture waits for the player: libVLC's new video track does not open it
        videoBackground = !inFront
        return CastResume(index, media.uri, castHost.positionMs.coerceAtLeast(0L), paused = !playing || !inFront, film = true)
            .also { castResume = it }
    }

    /** S-1302: the playback as a handover between the music engine and libVLC sees it. */
    private val castHost = object : HandoverHost {
        override val engine: EngineId get() = engines.current.engineId
        override val index: Int get() = currentIndex.takeIf { isValidPosition(it) } ?: -1
        override val positionMs: Long get() = engines.current.getCurrentTime()
        override val playing: Boolean get() = engines.current.isPlaying()
        override val paused: Boolean get() = engines.current.isPaused()
        override val item: CastItem? get() = castItemOf(getCurrentMedia()?.uri)

        override fun engineTakes(): Boolean = getCurrentMedia()?.let { musicEngine.canPlay(it.toEngineMedia()) } == true

        override fun replay(replay: Handover.Replay) {
            val uri = getCurrentMedia()?.uri ?: return
            launch {
                // libVLC plays a CUE sheet's track at the file's times (S-808); the engine at the track's
                val from = if (replay.to == EngineId.NagmedarAudio && CueUri.isTrack(uri)) {
                    withContext(Dispatchers.IO) { CueSheets.track(ctx, uri) }?.let { it.from * 1000L / CueSheet.FRAMES_PER_SECOND } ?: 0L
                } else {
                    0L
                }
                // the queue moved on meanwhile: nothing to go on with
                if (currentIndex != replay.index || getCurrentMedia()?.uri != uri) return@launch
                castResume = CastResume(replay.index, uri, (replay.positionMs - from).coerceAtLeast(0L), replay.paused)
                playIndex(replay.index)
            }
        }
    }

    suspend fun playIndex(index: Int, flags: Int = 0, forceResume:Boolean = false, forceRestart:Boolean = false) {
        // The fonts are scanned when the first text renderer is created, which would otherwise
        // delay the playback by several seconds on the first run. See [FontCache]
        // Wait before reading any state, as the queue can change in the meantime
        FontCache.await(service) {
            service.showToast(service.getString(R.string.font_cache_building), Toast.LENGTH_LONG)
        }

        videoBackground = videoBackground || (!player.isVideoPlaying() && player.canSwitchToVideo())
        if (mediaList.size() == 0) {
            Log.w(TAG, "Warning: empty media list, nothing to play !")
            return
        }
        currentIndex = if (isValidPosition(index)) {
            index
        } else {
            Log.w(TAG, "Warning: index $index out of bounds")
            0
        }

        val mw = mediaList.getMedia(index) ?: return
        // S-1302: a handover between the music engine and libVLC goes on where it was
        val resume = castResume?.takeIf { it.index == index && it.uri == mw.uri }
        castResume = null
        // the position comes from the handover, not from what the video player kept
        if (resume != null) savedTime = 0L
        val mediaFromMl = medialibrary.getMedia(mw.uri)
        if (mediaFromMl != null)
            mw.time = mediaFromMl.time

        val isInCustomPiP: Boolean = service.isInPiPMode.value ?: false
        if (mw.type == MediaWrapper.TYPE_VIDEO && !isAppStarted() && !isInCustomPiP) videoBackground = true
        val isVideoPlaying = mw.type == MediaWrapper.TYPE_VIDEO && player.isVideoPlaying()
        setRepeatTypeFromSettings()
        if (!videoBackground && isVideoPlaying) mw.addFlags(MediaWrapper.MEDIA_VIDEO)
        if (videoBackground) mw.addFlags(MediaWrapper.MEDIA_FORCE_AUDIO)
        if (isBenchmark) mw.addFlags(MediaWrapper.MEDIA_BENCHMARK)
        parsed = false
        player.switchToVideo = false
        // a CUE sheet's track (S-808) keeps the sheet's title, not its file's name
        if (mw.uri.scheme == "content" && !CueUri.isTrack(mw.uri)) withContext(Dispatchers.IO) { MediaUtils.retrieveMediaTitle(mw) }

        if (mw.type != MediaWrapper.TYPE_VIDEO || isVideoPlaying || player.hasRenderer
                || mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO)) {
            var uri = withContext(Dispatchers.IO) { FileUtils.getUri(mw.uri) }
            if (uri == null) {
                skipMedia()
                return
            }
            //PiP TV
            if (!isBenchmark && AndroidDevices.isTv && isVideoPlaying) {
                VideoPlayerActivity.startOpened(ctx, mw.uri, currentIndex)
            }
            val title = mw.getMetaLong(MediaWrapper.META_TITLE)
            if (title > 0) uri = "$uri#$title".toUri()

            // Nağmedar (S-1104): a podcast episode starts where its show's rule put it (the player set its time), never by VLC's resume setting or its dialog
            val ownResume = resume == null && withContext(Dispatchers.IO) { runCatching { ResumeHook.owner?.ownsResume(mw.location) }.getOrNull() } == true
            val start: Long
            if (resume != null) {
                start = resume.positionMs
            } else if (ownResume) {
                start = if (forceRestart) 0L else getStartTime(mw)
                if (forceRestart) savedTime = 0L
            } else if (isVideoPlaying) {
                start = if (forceRestart
                    || videoResumeStatus == ResumeStatus.NEVER) 0L else getStartTime(mw)
                // If we explicitly restart or NEVER resume is set, ensure any leftover savedTime is cleared
                if (start == 0L && (forceRestart || videoResumeStatus == ResumeStatus.NEVER)) savedTime = 0L
                if (!forceResume && videoResumeStatus == ResumeStatus.ASK && start > 0 && isAppStarted()) {
                    waitForConfirmation.postValue(WaitConfirmation(mw.title, index, flags))
                    return
                }
            } else {
                start = if (forceRestart
                    || audioResumeStatus == ResumeStatus.NEVER) 0L else getStartTime(mw)
                // If we explicitly restart or NEVER resume is set, ensure any leftover savedTime is cleared
                if (start == 0L && (forceRestart || audioResumeStatus == ResumeStatus.NEVER)) savedTime = 0L
                if (!forceResume && audioResumeStatus == ResumeStatus.ASK && start > 0 && isAppStarted()) {
                    val confirmation = WaitConfirmation(mw.title, index, flags)
                    waitForConfirmationAudio.postValue(confirmation)
                    return
                }
            }
            // Nağmedar (S-1302): a tracker module plays on this device alone: passed over while casting, and said
            val castItem = castItemOf(mw.uri)
            if (player.hasRenderer && castItem != null && CastHandover.skippedWhileCasting(castItem)) {
                service.showToast(ctx.getString(R.string.nagmedar_cast_tracker_skipped, mw.title), Toast.LENGTH_LONG)
                if (++castSkips < mediaList.size()) skipMedia() else stop()
                return
            }
            castSkips = 0
            // Nağmedar (S-901): the local music library plays through its own engine when it takes the item
            if (engines.prepareFor(mw.toEngineMedia()) === musicEngine && musicEngine.startPlayback(mw, mediaplayerEventListener, start)) {
                if (resume?.paused == true) musicEngine.pause()
                if (browserAudioActive) musicEngine.setVolume(0)
                newMedia = true
                determinePrevAndNextIndices()
                service.onNewPlayback()
                return
            }
            // the engine could not take it: libVLC does, as for everything else
            engines.prepareFor(EngineMedia(uri.toString(), isAudio = false, isLocal = false))
            // S-808: a CUE sheet's track is a part of its file: libVLC plays the file between the part's times
            val cuePart = if (CueUri.isTrack(mw.uri)) withContext(Dispatchers.IO) { CueSheets.track(ctx, mw.uri) } else null
            //todo restore position as well when we move to VLC 4.0
            val media = mediaFactory.getFromUri(VLCInstance.getInstance(service), if (cuePart != null) CueUri.file(uri) else uri)
            //fixme workaround to prevent the issue described in https://code.videolan.org/videolan/vlc-android/-/issues/2106
            if (shouldDisableCookieForwarding) {
                shouldDisableCookieForwarding = false
                media.addOption(":no-http-forward-cookies")
            }
            settings.getString(HTTP_USER_AGENT, null)?.let {
                 media.addOption(":http-user-agent=$it")
            }
            val dav1dThreadNumber = settings.getString(DAV1D_THREAD_NUMBER, "") ?: ""
            if (dav1dThreadNumber.isNotEmpty() && dav1dThreadNumber.toInt() >= 1) {
                media.addOption(":dav1d-thread-frames=$dav1dThreadNumber")
            }
            //todo in VLC 4.0, this should be done by using libvlc_media_player_set_time instead of start-time
            if (cuePart != null) {
                val from = cuePart.from.toDouble() / CueSheet.FRAMES_PER_SECOND
                media.addOption(":start-time=${"%.3f".format(Locale.ROOT, from + start / 1000.0)}")
                cuePart.to?.let { media.addOption(":stop-time=${"%.3f".format(Locale.ROOT, it.toDouble() / CueSheet.FRAMES_PER_SECOND)}") }
            } else if (resume != null) {
                // S-1302: a handover goes on to the millisecond
                media.addOption(":start-time=${"%.3f".format(Locale.ROOT, start / 1000.0)}")
            } else {
                media.addOption(":start-time=${start/1000L}")
            }
            // Nağmedar (S-1302): music goes to a Chromecast without a picture (its cover would be
            // sent as a film); a film keeps its picture, unless VLC's "audio only" casting is set
            if (mw.type != MediaWrapper.TYPE_VIDEO) media.addOption(":no-sout-chromecast-video")
            // Nağmedar (S-702): the subtitles' character set, read from the film's subtitle files or chosen for it
            SubtitleEncodingHook.finder?.takeIf { mw.type == MediaWrapper.TYPE_VIDEO }?.let { finder ->
                val kept = runCatching { SlaveRepository.getInstance(ctx).getSlaves(mw.location).map { it.uri } }.getOrDefault(emptyList())
                runCatching { finder.find(ctx, mw.uri, uri, kept) }.getOrNull()
            }?.let { media.addOption(":subsdec-encoding=$it") }
            // Nağmedar (S-507): a film the hardware decoder would colour wrong (Dolby Vision profile 5) is decoded in software
            val software = DecodingHook.adviser?.takeIf { mw.type == MediaWrapper.TYPE_VIDEO }
                ?.let { adviser -> runCatching { adviser.needsSoftwareDecoding(ctx, mw.uri) }.getOrDefault(false) } ?: false
            // S-1302: a track handed over while paused stays paused
            val resumePaused = if (resume?.paused == true) MediaWrapper.MEDIA_PAUSED else 0
            VLCOptions.setMediaOptions(media, ctx, flags or mw.flags or resumePaused or (if (software) MediaWrapper.MEDIA_NO_HWACCEL else 0), PlaybackService.hasRenderer())
            // Nağmedar (S-1301): a saved network server's sign-in, as libVLC's options take it, never in the address
            NetworkMediaHook.options?.let { hook -> withContext(Dispatchers.IO) { runCatching { hook.optionsFor(uri) }.getOrDefault(emptyList()) } }
                ?.forEach { media.addOption(it) }
            /* keeping only video during benchmark */
            if (isBenchmark) {
                media.addOption(":no-audio")
                media.addOption(":no-spu")
                if (isHardware) {
                    media.addOption(":codec=mediacodec_ndk,mediacodec_jni,none")
                    isHardware = false
                }
            }
            media.setEventListener(this@PlaylistManager)
            player.startPlayback(media, mediaplayerEventListener, start)
            player.setSlaves(media, mw)
            if (browserAudioActive) player.setVolume(0)
            newMedia = true
            determinePrevAndNextIndices()
            service.onNewPlayback()
        } else { //Start VideoPlayer for first video, it will trigger playIndex when ready.
            // S-1302: the film brought back from casting goes on where it was once the player plays it
            castResume = resume
            if (engine.isPlaying()) engine.stop()
            VideoPlayerActivity.startOpened(ctx, mw.uri, currentIndex)
        }
    }

    private fun skipMedia() {
        if (currentIndex != nextIndex) next()
        else stop()
    }

    fun onServiceDestroyed() {
        player.release()
        musicEngine.release()
    }

    fun setBrowserAudio(enable: Boolean) {
        if (browserAudioActive == enable) return
        browserAudioActive = enable
        if (enable) {
            preBrowserVolume = engine.getVolume()
            engine.setVolume(0)
        } else {
            engine.setVolume(if (preBrowserVolume >= 0) preBrowserVolume else 100)
            preBrowserVolume = -1
        }
    }

    @MainThread
    fun switchToVideo(): Boolean {
        val media = getCurrentMedia()
        // S-1302: a film brought back from casting opens even before libVLC shows its picture here
        val castFilm = media != null && castResume?.let { it.film && it.index == currentIndex && it.uri == media.uri } == true
        if (media === null || (!castFilm && (media.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) || !player.canSwitchToVideo())))
            return false
        // played on meanwhile: the player takes it as it plays, not from the handover's position
        if (castFilm && player.isPlaying()) castResume = null
        val hasRenderer = player.hasRenderer
        videoBackground = false
        showAudioPlayer.postValue(false)
        if (player.isVideoPlaying() && !hasRenderer) {//Player is already running, just send it an intent
            player.setVideoTrackEnabled(true)
            LocalBroadcastManager.getInstance(service).sendBroadcast(
                    VideoPlayerActivity.getIntent(PLAY_FROM_SERVICE,
                            media, false, currentIndex))
        } else if (!player.switchToVideo) { //Start the video player
            VideoPlayerActivity.startOpened(AppContextProvider.appContext, media.uri, currentIndex)
            if (!hasRenderer) player.switchToVideo = true
        }
        return true
    }

    fun setVideoTrackEnabled(enabled: Boolean) {
        if (!hasMedia()) return
        if (enabled) getCurrentMedia()?.addFlags(MediaWrapper.MEDIA_VIDEO)
        else getCurrentMedia()?.removeFlags(MediaWrapper.MEDIA_VIDEO)
        player.setVideoTrackEnabled(enabled)
    }

    fun hasPrevious() = prevIndex != -1

    fun hasNext() = nextIndex != -1

    @MainThread
    override fun onItemAdded(index: Int, mrl: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemAdded")
        if (currentIndex >= index && !expanding) ++currentIndex
        addUpdateActor.trySend(Unit)
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private val addUpdateActor = actor<Unit>(capacity = Channel.CONFLATED) {
        for (update in channel) {
            determinePrevAndNextIndices()
            executeUpdate()
            saveMediaList()
        }
    }

    @MainThread
    override fun onItemRemoved(index: Int, mrl: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemDeleted")
        val currentRemoved = currentIndex == index
        if (currentIndex >= index && !expanding) --currentIndex
        launch {
            determinePrevAndNextIndices()
            if (currentRemoved && !expanding) {
                when {
                    nextIndex != -1 -> next()
                    currentIndex != -1 -> playIndex(currentIndex, 0)
                    else -> stop()
                }
            }
            executeUpdate()
            saveMediaList()
        }
    }

    private fun executeUpdate() {
        service.executeUpdate(true)
    }

    fun saveMediaMeta(end:Boolean = false) = launch(start = CoroutineStart.UNDISPATCHED) outerLaunch@ {
        if (endReachedFor != null && endReachedFor == getCurrentMedia()?.uri.toString() && !end) return@outerLaunch
        val titleIdx = player.getTitleIdx()
        val currentMedia = getCurrentMedia() ?: return@outerLaunch
        if (currentMedia.uri.scheme.isSchemeFD()) return@outerLaunch
        if (Settings.getInstance(AppContextProvider.appContext).getBoolean(KEY_INCOGNITO, false)) return@outerLaunch
        //Save progress
        val time = if (engine === player) player.mediaplayer.time else engine.getCurrentTime()
        val length = engine.getLength()
        val canSwitchToVideo = player.canSwitchToVideo()
        launch(Dispatchers.IO) innerLaunch@ {
            val media = if (entryUrl != null) medialibrary.getMedia(entryUrl) else medialibrary.findMedia(currentMedia) ?: return@innerLaunch
            if (showAudioPlayer.value != true) BaseBrowserFragment.needRefresh.postValue(true)
            if (media.id == 0L) return@innerLaunch
            if (titleIdx > 0) media.setLongMeta(MediaWrapper.META_TITLE, titleIdx.toLong())
            //checks if the [MediaPlayer] has not been reset in the meantime to prevent saving 0
            if (time != 0L || engine.isPlaying())
                if (media.type == MediaWrapper.TYPE_VIDEO || canSwitchToVideo || media.isPodcast) {
                    if (length == 0L) {
                        media.time = -1L
                        media.position = player.lastPosition
                        medialibrary.setLastPosition(media.id, if (end) 1F else media.position)
                    } else {
                        //todo verify that this info is persisted in DB
                        if (media.length <= 0 && length > 0) media.length = length
                        try {
                            when (medialibrary.setLastTime(media.id, if (end) length else time)) {
                                Medialibrary.ML_SET_TIME_ERROR -> {
                                }
                                Medialibrary.ML_SET_TIME_END, Medialibrary.ML_SET_TIME_BEGIN -> media.time = 0
                                Medialibrary.ML_SET_TIME_AS_IS -> {
                                    media.time = time
                                    // the Medialibrary sometimes doesn't have the media's length,
                                    // and won't know that the file has ended. This forces the ML
                                    // to mark the file as finished and reset it's time
                                    if (time == length) {
                                        medialibrary.setLastTime(media.id, 0)
                                        media.seen = 1L
                                        media.playCount += 1
                                        media.time = 0
                                    }
                                }
                            }
                        } catch (e: NullPointerException) {
                            VLCCrashHandler.saveLog(e, "NullPointerException in PlaylistManager saveMediaMeta")
                        }
                    }
                }
            if (media.type != MediaWrapper.TYPE_VIDEO && !canSwitchToVideo && !media.isPodcast) skipMediaUpdateRefresh = true

        }
    }

    fun setSpuTrack(index: String) {
        if (!player.setSpuTrack(index)) return
        val media = getCurrentMedia() ?: return
        if (media.id != 0L) launch(Dispatchers.IO) { media.setStringMeta(MediaWrapper.META_SUBTITLE_TRACK, index) }
    }

    fun setAudioDelay(delay: Long) {
        if (!player.setAudioDelay(delay)) return
        val media = getCurrentMedia() ?: return
        if (media.id != 0L && settings.getBoolean(KEY_SAVE_INDIVIDUAL_AUDIO_DELAY, true)) {
            launch(Dispatchers.IO) { media.setLongMeta(MediaWrapper.META_AUDIODELAY, player.getAudioDelay()) }
        }
    }

    fun setSpuDelay(delay: Long) {
        if (!player.setSpuDelay(delay)) return
        val media = getCurrentMedia() ?: return
        if (media.id != 0L) launch(Dispatchers.IO) { media.setLongMeta(MediaWrapper.META_SUBTITLE_DELAY, player.getSpuDelay()) }
    }

    private fun loadMediaMeta(media: MediaWrapper) {
        if (player.canSwitchToVideo()) {
            val savedDelay = media.getMetaLong(MediaWrapper.META_AUDIODELAY)
            val globalDelay = Settings.getInstance(AppContextProvider.appContext).getLong(AUDIO_DELAY_GLOBAL, 0L)
            if (savedDelay == 0L && globalDelay != 0L) {
                player.setAudioDelay(globalDelay)
            } else if (settings.getBoolean(KEY_SAVE_INDIVIDUAL_AUDIO_DELAY, true)) {
                player.setAudioDelay(savedDelay)
            }
            val abStart = if (settings.getBoolean(PLAYBACK_HISTORY, true))  media.getMetaLong(MediaWrapper.META_AB_REPEAT_START) else 0L
            if (abStart != 0L) {
                abRepeatOn.value = true
                val abStop = media.getMetaLong(MediaWrapper.META_AB_REPEAT_STOP)
                abRepeat.postValue(ABRepeat(abStart, if (abStop == 0L) -1L else abStop))
            }
            player.setSpuTrack(media.getMetaLong(MediaWrapper.META_SUBTITLE_TRACK).toString())
            player.setSpuDelay(media.getMetaLong(MediaWrapper.META_SUBTITLE_DELAY))
        }
        restoreSpeed(media)
    }

    /**
     * Restore the playback speed of the current media.
     *
     * @param media
     */
    fun restoreSpeed(media: MediaWrapper) {
        val incognitoMode = settings.getBoolean(KEY_INCOGNITO, false)
        val playbackSpeedModeAll = settings.getBoolean(if (player.isVideoPlaying()) KEY_PLAYBACK_SPEED_VIDEO_GLOBAL else KEY_PLAYBACK_SPEED_AUDIO_GLOBAL, false)
        val playbackRate = when {
            incognitoMode && playbackSpeedModeAll -> settings.getFloat(if (player.isVideoPlaying()) KEY_INCOGNITO_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE else KEY_INCOGNITO_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE, 1.0f)
            //restore all
            playbackSpeedModeAll -> settings.getFloat(if (player.isVideoPlaying()) KEY_PLAYBACK_SPEED_VIDEO_GLOBAL_VALUE else KEY_PLAYBACK_SPEED_AUDIO_GLOBAL_VALUE, 1.0f)
            incognitoMode -> 1F
            //restore one
            else -> try {
                media.getMetaString(MediaWrapper.META_SPEED).toFloat()
            } catch (e: Exception) {
                1F
            }
        }
        engine.setRate(playbackRate, false)
    }

    @Synchronized
    fun saveCurrentMedia(forceVideo:Boolean = false) {
        if (settings.getBoolean(KEY_INCOGNITO, false)) return
        val media = getCurrentMedia() ?: return
        val isAudio = isAudioList() || forceVideo
        settings.putSingle(KEY_CURRENT_MEDIA_IS_AUDIO, isAudio)

        if (media.uri.scheme.isSchemeFD()) {
            if (isAudio) {
                settings.putSingle(KEY_CURRENT_AUDIO_RESUME_TITLE, "")
                settings.putSingle(KEY_CURRENT_AUDIO_RESUME_ARTIST, "")
                settings.putSingle(KEY_CURRENT_AUDIO_RESUME_THUMB, "")
            }
            return
        }
        val saveAudioPlayQueue = settings.getBoolean(AUDIO_RESUME_PLAYBACK, true)
        val saveVideoPlayQueue = settings.getBoolean(VIDEO_RESUME_PLAYBACK, true)
        if (!isAudio && saveVideoPlayQueue) {
            settings.putSingle(KEY_CURRENT_MEDIA_RESUME, media.location)
            // !player.isVideoPlaying means that there are no view attached, but if the media is a
            // video then it must have been played as Audio
            if (Settings.getInstance(ctx).getString(KEY_VIDEO_APP_SWITCH, "0") == "1" || !player.isVideoPlaying()) {
                settings.putSingle(KEY_CURRENT_AUDIO_RESUME_TITLE, media.title ?: "")
                settings.putSingle(KEY_CURRENT_AUDIO_RESUME_ARTIST, media.artistName ?: "")
                settings.putSingle(KEY_CURRENT_AUDIO_RESUME_THUMB, media.artworkURL ?: "")
            }
            settings.putSingle(KEY_CURRENT_MEDIA, media.location)
        }
        if (isAudio && saveAudioPlayQueue) {
            settings.putSingle(KEY_CURRENT_MEDIA_RESUME, media.location)
            settings.putSingle(KEY_CURRENT_AUDIO, media.location)
            settings.putSingle(KEY_CURRENT_AUDIO_RESUME_TITLE, media.title ?: "")
            settings.putSingle(KEY_CURRENT_AUDIO_RESUME_ARTIST, media.artistName ?: "")
            // S-1009: the widget shows Nağmedar's album cover when the service is gone too
            settings.putSingle(KEY_CURRENT_AUDIO_RESUME_THUMB, CoverHook.file(media)?.path ?: media.artworkURL ?: "")
        }
    }

    suspend fun saveMediaList(forceVideo:Boolean = false) {
        if (settings.getBoolean(KEY_INCOGNITO, false)) return
        val currentMedia = getCurrentMedia() ?: return
        if (currentMedia.uri.scheme.isSchemeFD()) return
        val locations = StringBuilder()
        val isAudio = isAudioList() || forceVideo
        withContext(Dispatchers.Default) {
            val list = mediaList.copy.takeIf { it.isNotEmpty() } ?: return@withContext
            for (mw in list) locations.append(" ").append(mw.uri.toString())
            //We save a concatenated String because putStringSet is APIv11.
            if (isAudio) {
                if (settings.getBoolean(AUDIO_RESUME_PLAYBACK, true)) {
                    settings.putSingle(KEY_MEDIA_LAST_PLAYLIST_RESUME, locations.toString().trim())
                    settings.putSingle(KEY_AUDIO_LAST_PLAYLIST, locations.toString().trim())
                }
            } else {
                if (settings.getBoolean(VIDEO_RESUME_PLAYBACK, true)) {
                    settings.putSingle(KEY_MEDIA_LAST_PLAYLIST_RESUME, locations.toString().trim())
                    settings.putSingle(KEY_MEDIA_LAST_PLAYLIST, locations.toString().trim())
                }
            }
        }
    }

    override fun onItemMoved(indexBefore: Int, indexAfter: Int, mrl: String) {
        if (BuildConfig.DEBUG) Log.i(TAG, "CustomMediaListItemMoved")
        when (currentIndex) {
            indexBefore -> {
                currentIndex = indexAfter
                if (indexAfter > indexBefore)
                    --currentIndex
            }
            in indexAfter until indexBefore -> ++currentIndex
            in (indexBefore + 1) until indexAfter -> --currentIndex
        }

        // If we are in random mode, we completely reset the stored previous track
        // as their indices changed.
        previous.clear()
        addUpdateActor.trySend(Unit)
    }

    private suspend fun determinePrevAndNextIndices(expand: Boolean = false) {
        val media = getCurrentMedia()
        if (expand && media !== null) {
            expanding = true
            nextIndex = expand(media.type == MediaWrapper.TYPE_STREAM, media.hasFlag(MediaWrapper.MEDIA_NO_PARSE))
            expanding = false
        } else {
            nextIndex = -1
        }
        prevIndex = -1

        if (nextIndex == -1) {
            // No subitems; play the next item.
            val size = mediaList.size()
            shuffling = shuffling and (size > 2)

            if (shuffling) {
                if (!previous.isEmpty()) {
                    prevIndex = previous.peek()
                    while (!isValidPosition(prevIndex)) {
                        previous.removeAt(previous.size - 1)
                        if (previous.isEmpty()) {
                            prevIndex = -1
                            break
                        }
                        prevIndex = previous.peek()
                    }
                }
                // If we've played all songs already in shuffle, then either
                // reshuffle or stop (depending on RepeatType).
                if (previous.size + 1 == size) {
                    if (repeating.value == PlaybackStateCompat.REPEAT_MODE_NONE) {
                        nextIndex = -1
                        syncEngineNext()
                        return
                    } else {
                        previous.clear()
                    }
                }
                // Find a new index not in previous.
                do {
                    nextIndex = random.nextInt(size)
                } while (nextIndex == currentIndex || previous.contains(nextIndex))
            } else {
                // normal playback
                if (currentIndex > 0) prevIndex = currentIndex - 1
                nextIndex = when {
                    currentIndex + 1 < size -> currentIndex + 1
                    repeating.value == PlaybackStateCompat.REPEAT_MODE_NONE -> -1
                    else -> 0
                }
            }
        }
        syncEngineNext()
    }

    fun previousTotalTime(): Long {
        val index = currentIndex
        val copy = mediaList.copy
        return when {
            copy.size == 0 || index < 0 -> {
                0
            }
            shuffling -> {
                copy.asSequence()
                        .filterIndexed { prevIndex, _ -> previous.contains(prevIndex) }
                        .map { it.length }
                        .sum()
            }
            else -> {
                copy.asSequence()
                        .take(index)
                        .map { it.length }
                        .sum()
            }
        }
    }

    /**
     * Expand the current media.
     * @return the index of the media was expanded, and -1 if no media was expanded
     */
    @MainThread
    private suspend fun expand(updateHistory: Boolean, skipNextParsing:Boolean = false): Int {
        entryUrl = null
        expansion = null
        if (BuildConfig.BETA) Log.d(TAG, "expand with values: ", Exception("Call stack"))
        val index = currentIndex
        val expandedMedia = getCurrentMedia()
        val stream = expandedMedia?.type == MediaWrapper.TYPE_STREAM
        val ml = player.expand()
        var ret = -1

        if (ml != null && ml.count > 0) {
            val mrl = if (updateHistory) expandedMedia?.location else null
            val children = HashSet<String>()
            mediaList.removeEventListener(this)
            mediaList.remove(index)
            for (i in 0 until ml.count) {
                val child = ml.getMediaAt(i)
                //fixme workaround to prevent the issue described in https://code.videolan.org/videolan/vlc-android/-/issues/2106
                if (isSchemeHttpOrHttps(child.uri.scheme) && child.uri.authority?.endsWith(".youtube.com") == true) {
                    shouldDisableCookieForwarding = true
                }
                if (!skipNextParsing)
                    withContext(Dispatchers.IO) { child.parse() }
                children.add(child.uri.toString())
                if (BuildConfig.BETA)  Log.d(TAG, "inserting: ${child.uri}")
                mediaList.insert(index + i, MLServiceLocator.getAbstractMediaWrapper(child).apply {
                    if (skipNextParsing)
                        addFlags(MediaWrapper.MEDIA_NO_PARSE)
                })
                child.release()
            }
            mediaList.addEventListener(this)
            if (stream && expandedMedia != null) expansion = Expansion(expandedMedia.location, children)
            addUpdateActor.trySend(Unit)
            service.onMediaListChanged()
            if (mrl !== null && ml.count == 1) {
                getCurrentMedia()?.apply {
                    AppScope.launch(Dispatchers.IO) {
                        if (stream) {
                            type = MediaWrapper.TYPE_STREAM
                            entryUrl = mrl
                            medialibrary.getMedia(mrl)?.run { if (id > 0) medialibrary.removeExternalMedia(id) }
                        } else if (!uri.scheme.isSchemeFD()) {
                            medialibrary.addToHistory(mrl, title)
                        }
                    }
                }
            }
            ret = index
        }
        ml?.release()
        return ret
    }

    fun getCurrentMedia() = mediaList.getMedia(currentIndex)

    fun getPrevMedia() = if (isValidPosition(prevIndex)) mediaList.getMedia(prevIndex) else null

    fun getNextMedia() = if (isValidPosition(nextIndex)) mediaList.getMedia(nextIndex) else null

    fun getMedia(position: Int) = mediaList.getMedia(position)

    private fun getStartTime(mw: MediaWrapper) : Long {
        val start = when {
            mw.hasFlag(MediaWrapper.MEDIA_FROM_START) -> {
                mw.removeFlags(MediaWrapper.MEDIA_FROM_START)
                0L
            }
            mw.time <= 0L -> when {
                savedTime > 0L -> savedTime
                else -> 0L
            }
            else -> mw.time
        }
        savedTime = 0L
        return start
    }

    @Synchronized
    private fun savePosition(reset: Boolean = false, video: Boolean = false) {
        if (settings.getBoolean(KEY_INCOGNITO, false)) return
        if (!hasMedia()) return
        settings.edit {
            val audio = !video && isAudioList()
            putBoolean(if (audio) AUDIO_SHUFFLING else MEDIA_SHUFFLING, shuffling)
            putInt(if (audio) POSITION_IN_AUDIO_LIST else POSITION_IN_MEDIA_LIST, if (reset) 0 else currentIndex)
            putLong(if (audio) POSITION_IN_SONG else POSITION_IN_MEDIA, if (reset) 0L else engine.getCurrentTime())
            if (!audio) {
                putFloat(VIDEO_SPEED, engine.getRate())
            }
            //we reached the end. The next proposed media should be the first of the list
            if (reset) {
                val media = getMediaList()[0]
                if(audio && settings.getBoolean(AUDIO_RESUME_PLAYBACK, true)) putString(KEY_CURRENT_AUDIO, media.location)
            }
        }
    }

    /**
     * Append to the current existing playlist
     */
    @MainThread
    suspend fun append(list: List<MediaWrapper>, index: Int = 0) {
        if (BuildConfig.BETA) Log.d(TAG, "append with values: ", Exception("Call stack"))
        if (!hasCurrentMedia()) {
            launch { load(list, index, mlUpdate = true) }
            return
        }
        val newList = withContext(Dispatchers.IO) { list.updateWithMLMeta() }
        mediaList.removeEventListener(this)
        for (media in newList) mediaList.add(media)
        if (BuildConfig.BETA) newList.forEach {
            try {
                Log.d(TAG, "Media location: ${it.uri}")
            } catch (e: NullPointerException) {
                Log.d(TAG, "Media crash", e)
            }
        }
        mediaList.addEventListener(this)
        addUpdateActor.trySend(Unit)
        if (settings.getBoolean(KEY_AUDIO_FORCE_SHUFFLE, false) && getCurrentMedia()?.type == MediaWrapper.TYPE_AUDIO  && !shuffling && canShuffle()) shuffle()
    }

    /**
     * Insert into the current existing playlist
     */

    @MainThread
    fun insertNext(list: List<MediaWrapper>) {
        if (BuildConfig.BETA) Log.d(TAG, "insertNext with values: ", Exception("Call stack"))
        if (BuildConfig.BETA) list.forEach { Log.d(TAG, "Media location: ${it.uri}") }
        if (!hasCurrentMedia()) {
            launch { load(list, 0) }
            return
        }
        val startIndex = currentIndex + 1
        for ((index, mw) in list.withIndex()) mediaList.insert(startIndex + index, mw)
    }

    /**
     * Move an item inside the playlist.
     */
    @MainThread
    fun moveItem(positionStart: Int, positionEnd: Int) = mediaList.move(positionStart, positionEnd)

    @MainThread
    fun insertItem(position: Int, mw: MediaWrapper) {
        if (BuildConfig.BETA) Log.d(TAG, "insertItem with values: ", Exception("Call stack"))
        if (BuildConfig.BETA) Log.d(TAG, "Media location: ${mw.uri}")
        mediaList.insert(position, mw)
    }


    @MainThread
    fun remove(position: Int) = mediaList.remove(position)

    @MainThread
    fun removeLocation(location: String) = mediaList.remove(location)

    fun getMediaListSize()= mediaList.size()

    fun getMediaList(): List<MediaWrapper> = mediaList.copy

    fun setABRepeatValue(media: MediaWrapper?, time: Long) {
        val value = abRepeat.value ?: ABRepeat()
        when {
            value.start == -1L -> {
                value.start = time
            }
            value.start > time && time > -1 -> {
                value.stop = value.start
                value.start = time
            }
            else -> {
                value.stop = time
            }
        }
        if (settings.getBoolean(PLAYBACK_HISTORY, true)) {
            media?.setLongMeta(MediaWrapper.META_AB_REPEAT_START, value.start)
            media?.setLongMeta(MediaWrapper.META_AB_REPEAT_STOP, value.stop)
        }
        abRepeat.value = value
    }

    fun setDelayValue(time: Long, start: Boolean) {
        val value = delayValue.value ?: DelayValues()
        if (start) value.start = time else value.stop = time
        delayValue.value = value
    }

    fun resetDelayValues() {
        delayValue.postValue(DelayValues())
    }

    fun resetABRepeatValues(media: MediaWrapper?) {
        abRepeat.value = ABRepeat()
        media?.setLongMeta(MediaWrapper.META_AB_REPEAT_START, 0L)
        media?.setLongMeta(MediaWrapper.META_AB_REPEAT_STOP, 0L)
    }

    fun toggleABRepeat() {
        abRepeatOn.value = !abRepeatOn.value!!
        if (abRepeatOn.value == false) {
            abRepeat.value = ABRepeat()
        }
    }

    fun toggleStats() {
        videoStatsOn.value = !videoStatsOn.value!!
    }

    fun clearABRepeat() {
        abRepeat.value = abRepeat.value?.apply {
            start = -1L
            stop = -1L
        }
        abRepeatOn.value = false
    }

    override fun onEvent(event: IMedia.Event) {
        var update = true
        when (event.type) {
            IMedia.Event.MetaChanged -> {
                /* Update Meta if file is already parsed */
                if (parsed && player.updateCurrentMeta(event.metaId, getCurrentMedia())) service.onMediaListChanged()
                if (BuildConfig.DEBUG) Log.i(TAG, "Media.Event.MetaChanged: " + event.metaId)
            }
            IMedia.Event.ParsedChanged -> {
                if (BuildConfig.DEBUG) Log.i(TAG, "Media.Event.ParsedChanged")
                player.updateCurrentMeta(-1, getCurrentMedia())
                parsed = true
            }
            else -> update = false
        }
        if (update) {
            service.onMediaEvent(event)
            if (parsed) {
                service.notifyTrackChanged()
                service.showNotification()
            }
        }
    }

    private val mediaplayerEventListener = object : MediaPlayerEventListener {
        private var lastTimeMetaSaved = 0L

        override suspend fun onEvent(event: MediaPlayer.Event) {
            when (event.type) {
                MediaPlayer.Event.Playing -> {
                    val mw = withContext(Dispatchers.IO) {
                        getCurrentMedia()?.let {
                            medialibrary.findMedia(it).apply {
                                if (type == -1) type = it.type
                            }
                        }
                    } ?: return
                    if (newMedia) {
                        loadMediaMeta(mw)
                        mw.length = engine.getLength()
                        saveMediaList()
                        savePosition()
                        saveCurrentMedia()
                        newMedia = false
                        if (player.hasRenderer || (!player.isVideoPlaying() && (mw.hasFlag(MediaWrapper.MEDIA_FORCE_AUDIO) || !player.canSwitchToVideo()))) {
                            showAudioPlayer.value = true
                        }
                        savePlaycount(mw)
                        if (mw.title == mw.fileName || (mw.type == MediaWrapper.TYPE_STREAM && (mw.title != player.mediaplayer.media?.getMeta(IMedia.Meta.Title, true) || mw.artistName != player.mediaplayer.media?.getMeta(IMedia.Meta.Artist, true)))) {
                            // used for initial metadata update. We avoid the metadata load when the initial MediaPlayer.Event.ESSelected is sent to avoid race conditions
                            refreshTrackMeta(mw)
                        }
                    }
                    playingState.value = true
                }
                MediaPlayer.Event.EndReached -> {
                    clearABRepeat()
                    getCurrentMedia()?.addFlags(MediaWrapper.MEDIA_FROM_START)
                    if (currentIndex != nextIndex) {
                        endReachedFor = getCurrentMedia()?.uri.toString()
                        saveMediaMeta(true)
                        if (isBenchmark) player.setPreviousStats()
                        if (nextIndex == -1) savePosition(reset = true)
                    }
                    if (stopAfter == currentIndex) {
                        if (BuildConfig.DEBUG) Log.d("AUDIO_STOP_AFTER", "reset")
                        stopAfter = -1
                        stop()
                    } else {
                        if (isBenchmark) player.setCurrentStats()
                        determinePrevAndNextIndices(true)
                        if (!hasNext()) getCurrentMedia()?.let {
                            if (AndroidDevices.isAndroidTv && AndroidUtil.isOOrLater && !isAudioList()) {
                                setResumeProgram(service.applicationContext, it)
                                updateNextProgramAfterThumbnailGeneration(service, service.applicationContext, it)
                            }
                        }
                        next()
                    }
                }
                MediaPlayer.Event.EncounteredError -> {
                    val location = try {
                        getCurrentMedia()?.location
                    } catch (e: NullPointerException) {
                        null
                    }
                    Log.w(TAG, "Invalid location $location")

                    service.showToast(if (location != null && location.toUri().scheme == "missing") service.getString(R.string.missing_location) else service.getString(R.string.invalid_location, location
                            ?: ""), Toast.LENGTH_SHORT, true)
                    if (location != null) PlaybackService.lastError.postValue(location)
                    if (currentIndex != nextIndex) next() else stop()
                }
                MediaPlayer.Event.TimeChanged -> {
                    abRepeat.value?.let {
                        val fastSeek = settings.getBoolean(KEY_ALWAYS_FAST_SEEK, false)
                        if (it.stop != -1L && engine.getCurrentTime() > it.stop) service.setTime(it.start, false)
                        if ((fastSeek && engine.getCurrentTime() < it.start - 30000L)
                            || (!fastSeek && engine.getCurrentTime() < it.start))
                            service.setTime(it.start, false)
                    }
                    if (engine.getCurrentTime() % 10 == 0L) savePosition()
                    val now = System.currentTimeMillis()
                    if (now - lastTimeMetaSaved > 5000L){
                        lastTimeMetaSaved = now
                        saveMediaMeta()
                    }
                }
                MediaPlayer.Event.SeekableChanged -> {
                    if (event.seekable)
                        getCurrentMedia()?.let { media ->
                            restoreSpeed(media)
                        }
                }
                MediaPlayer.Event.ESSelected -> {
                    getCurrentMedia()?.let { media ->
                        if (engine.isPlaying()) {
                            if (media.type == MediaWrapper.TYPE_STREAM && (media.title != player.mediaplayer.media?.getMeta(IMedia.Meta.Title, true) || media.artistName != player.mediaplayer.media?.getMeta(IMedia.Meta.Artist, true))) {
                                refreshTrackMeta(media)
                            }
                        }
                    }
                }
            }
            service.onMediaPlayerEvent(event)
        }
    }

    /**
     * Refresh the track meta as the title and then updates the player
     * Useful for web radios changing the metadata during playback
     * @param mw: The [MediaWrapper] to be updated
     */
    private fun refreshTrackMeta(mw: MediaWrapper) {
        if (engine !== player) {
            // S-1009: libVLC's media is another item's while the music engine plays: the file's own tags
            if (mw.title == mw.fileName) readEngineTrackTags(mw)
            return
        }
        mw.updateMeta(player.mediaplayer)
        service.onMediaListChanged()
        service.showNotification()
    }

    /** S-1009: the title and artist of a music engine's track the medialibrary has no tags of, from the file's own. */
    private fun readEngineTrackTags(mw: MediaWrapper) {
        val uri = mw.uri ?: return
        launch {
            val (title, artist) = withContext(Dispatchers.IO) {
                FileTags.tag(ctx, uri, "TITLE")?.trim() to FileTags.tag(ctx, uri, "ARTIST")?.trim()
            }
            if (title.isNullOrEmpty() && artist.isNullOrEmpty()) return@launch
            if (!title.isNullOrEmpty()) mw.title = title
            if (!artist.isNullOrEmpty()) mw.setArtist(artist)
            service.onMediaListChanged()
            service.showNotification()
        }
    }

    private suspend fun savePlaycount(mw: MediaWrapper) {
        if (Settings.getInstance(AppContextProvider.appContext).getBoolean(KEY_INCOGNITO, false)) return
        var currentMedia = mw
        // S-808: a CUE sheet's track is not a file of its own: the medialibrary is not given it
        if (settings.getBoolean(PLAYBACK_HISTORY, true) && !mw.uri.scheme.isSchemeFD() && !CueUri.isTrack(mw.uri)) withContext(Dispatchers.IO) {
            var id = mw.id
            if (id == 0L) {
                var internalMedia = medialibrary.findMedia(mw)
                if (internalMedia != null && internalMedia.id != 0L) {
                    id = internalMedia.id
                } else {
                    internalMedia = if (mw.type == MediaWrapper.TYPE_STREAM || isSchemeStreaming(mw.uri.scheme)) {
                        medialibrary.addStream(entryUrl ?: mw.uri.toString(), mw.title)
                    } else {
                        medialibrary.addMedia(mw.uri.toString(), mw.length)
                    }
                    if (internalMedia != null) {
                        currentMedia = internalMedia
                        id = internalMedia.id
                        getCurrentMedia()?.let { currentMedia -> if (internalMedia.title != currentMedia.title) internalMedia.rename(currentMedia.title) }
                    }
                }
            }
            val canSwitchToVideo = player.canSwitchToVideo()
            /*
             * Because progress isn't saved for non podcast audio (ie. saveMediaMeta), it is
             * necessary to mark the media as played to add them to history and increment their
             * playcount.
             */
            if (id != 0L && currentMedia.type != MediaWrapper.TYPE_VIDEO && !canSwitchToVideo && !currentMedia.isPodcast) {
                currentMedia.markAsPlayed()
            }
        }
    }

    internal fun isAudioList() = !player.isVideoPlaying() && mediaList.isAudioList
}

class ABRepeat(var start: Long = -1L, var stop: Long = -1L)
class DelayValues(var start: Long = -1L, var stop: Long = -1L)
class WaitConfirmation(val title: String, val index: Int, val flags: Int, var used: Boolean = false)
enum class ResumeStatus {
    ALWAYS, ASK, NEVER
}

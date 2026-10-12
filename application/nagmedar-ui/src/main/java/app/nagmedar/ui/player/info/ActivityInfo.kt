package app.nagmedar.ui.player.info

import android.provider.OpenableColumns
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.medialibrary.interfaces.media.MediaWrapper
import org.videolan.resources.NagmedarVlcOptions
import org.videolan.tools.KEY_AOUT
import org.videolan.tools.KEY_HARDWARE_ACCELERATION
import org.videolan.tools.KEY_OPENGL
import org.videolan.tools.Settings
import org.videolan.vlc.gui.video.VideoPlayerActivity
import java.io.File

/** The video output the settings choose (S-506). */
enum class VideoOutputChoice { Auto, Android, OpenGl }

/**
 * What the classic player tells of the playing film (S-605): its file, its tracks as libVLC sees
 * them, how the settings ask for it to be decoded and output, and its statistics.
 */
internal class ActivityInfoSource(private val activity: VideoPlayerActivity) {

    private val media: MediaWrapper? get() = activity.service?.currentMediaWrapper

    fun uri(): String? = media?.uri?.toString()

    /** The file's name, as the file manager shows it. */
    fun fileName(): String? {
        val uri = media?.uri ?: return null
        return media?.fileName?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment?.substringAfterLast('/')
    }

    fun lengthMs(): Long = activity.service?.length ?: 0L

    /** The file's size: a local file's, or what its provider says; null for streams. */
    fun sizeBytes(): Long? {
        val uri = media?.uri ?: return null
        return runCatching {
            when (uri.scheme) {
                null, "file" -> File(uri.path ?: return null).takeIf { it.isFile }?.length()
                "content" -> activity.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                    if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
                }
                else -> null
            }
        }.getOrNull()
    }

    /** The tracks libVLC plays, for a file the media probe has not read. */
    fun tracks(): PlayerTracks {
        val player = activity.service?.mediaplayer ?: return PlayerTracks()
        val video = player.getTracks(IMedia.Track.Type.Video).orEmpty()
            .filterIsInstance<IMedia.VideoTrack>()
            .let { all -> all.firstOrNull { it.selected } ?: all.firstOrNull() }
            ?.let { VideoTrackFacts(it.codec.orEmpty(), it.width, it.height, if (it.frameRateDen > 0) it.frameRateNum.toDouble() / it.frameRateDen else 0.0) }
        val audio = player.getTracks(IMedia.Track.Type.Audio).orEmpty().map { track ->
            val sound = track as? IMedia.AudioTrack
            TrackFacts(track.codec.orEmpty(), track.language.orEmpty(), track.name.orEmpty(), track.selected, sound?.channels ?: 0, sound?.rate ?: 0)
        }
        val subtitles = player.getTracks(IMedia.Track.Type.Text).orEmpty().map { track ->
            TrackFacts(track.codec.orEmpty(), track.language.orEmpty(), track.name.orEmpty(), track.selected)
        }
        return PlayerTracks(video, audio, subtitles)
    }

    /** How the settings ask for the video to be decoded, and whether hardware decoding failed on this file. */
    fun decoding(): VideoDecoding {
        if (media?.hasFlag(MediaWrapper.MEDIA_NO_HWACCEL) == true) return VideoDecoding.SoftwareFallback
        return when (Settings.getInstance(activity).getString(KEY_HARDWARE_ACCELERATION, "-1")?.toIntOrNull()) {
            0 -> VideoDecoding.Software
            1 -> VideoDecoding.Hardware
            2 -> VideoDecoding.HardwareDirect
            else -> VideoDecoding.Automatic
        }
    }

    /** The video output the settings choose (S-506): VLC's own choice, Android's, or OpenGL. */
    fun videoOutput(): VideoOutputChoice = when (Settings.getInstance(activity).getString(KEY_OPENGL, "-1")?.toIntOrNull()) {
        NagmedarVlcOptions.VOUT_ANDROID -> VideoOutputChoice.Android
        NagmedarVlcOptions.VOUT_OPENGL -> VideoOutputChoice.OpenGl
        else -> if (NagmedarVlcOptions.needsOpenGl(Settings.getInstance(activity))) VideoOutputChoice.OpenGl else VideoOutputChoice.Auto
    }

    /** The audio output the settings choose: AAudio unless they ask for AudioTrack or OpenSL ES. */
    fun audioOutput(): String = when (Settings.getInstance(activity).getString(KEY_AOUT, "-1")?.toIntOrNull()) {
        1 -> AOUT_AUDIOTRACK
        2 -> AOUT_OPENSLES
        else -> AOUT_AAUDIO
    }

    /** The playing film's statistics now: the input bitrate (libVLC's bytes per microsecond, in Mbit/s) and the pictures. */
    fun stats(): StatsSample? {
        val player = activity.service?.mediaplayer ?: return null
        val playing = player.media ?: return null
        return try {
            playing.stats?.let {
                StatsSample(
                    inputMbps = it.inputBitrate * BITS_PER_BYTE,
                    displayed = it.displayedPictures,
                    lost = it.lostPictures,
                    demuxMbps = it.demuxBitrate * BITS_PER_BYTE,
                    corrupted = it.demuxCorrupted,
                    discontinuities = it.demuxDiscontinuity,
                    audioPlayed = it.playedAbuffers,
                    audioLost = it.lostAbuffers,
                )
            }
        } finally {
            playing.release()
        }
    }

    private companion object {
        const val BITS_PER_BYTE = 8f
        const val AOUT_AAUDIO = "AAudio"
        const val AOUT_AUDIOTRACK = "AudioTrack"
        const val AOUT_OPENSLES = "OpenSL ES"
    }
}

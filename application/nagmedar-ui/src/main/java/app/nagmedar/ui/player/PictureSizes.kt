package app.nagmedar.ui.player

import android.content.res.Resources
import app.nagmedar.ui.R
import org.videolan.libvlc.MediaPlayer
import org.videolan.tools.Settings
import org.videolan.tools.VIDEO_RATIO
import org.videolan.tools.putSingle
import org.videolan.vlc.gui.video.VideoPlayerActivity

/**
 * The picture's sizes the player offers, in the touch layout's sheet and for A, C and Z: libVLC's
 * scale types, fitted first, then filled (cropped), stretched, pixel for pixel, then the ratios.
 */
internal val pictureSizes: List<MediaPlayer.ScaleType> = listOf(
    MediaPlayer.ScaleType.SURFACE_BEST_FIT, MediaPlayer.ScaleType.SURFACE_FIT_SCREEN, MediaPlayer.ScaleType.SURFACE_FILL,
    MediaPlayer.ScaleType.SURFACE_ORIGINAL, MediaPlayer.ScaleType.SURFACE_16_9, MediaPlayer.ScaleType.SURFACE_4_3,
    MediaPlayer.ScaleType.SURFACE_16_10, MediaPlayer.ScaleType.SURFACE_2_1, MediaPlayer.ScaleType.SURFACE_221_1,
    MediaPlayer.ScaleType.SURFACE_235_1, MediaPlayer.ScaleType.SURFACE_239_1, MediaPlayer.ScaleType.SURFACE_5_4,
)

/** A picture size's name: "Fit", "Fill the screen (crop)"; a ratio as it is written in both languages, "2.35:1". */
internal fun pictureSizeLabel(resources: Resources, scale: MediaPlayer.ScaleType): String = when (scale) {
    MediaPlayer.ScaleType.SURFACE_BEST_FIT -> resources.getString(R.string.nag_scale_fit)
    MediaPlayer.ScaleType.SURFACE_FIT_SCREEN -> resources.getString(R.string.nag_scale_fill)
    MediaPlayer.ScaleType.SURFACE_FILL -> resources.getString(R.string.nag_scale_stretch)
    MediaPlayer.ScaleType.SURFACE_ORIGINAL -> resources.getString(R.string.nag_scale_original)
    MediaPlayer.ScaleType.SURFACE_16_9 -> "16:9"
    MediaPlayer.ScaleType.SURFACE_4_3 -> "4:3"
    MediaPlayer.ScaleType.SURFACE_16_10 -> "16:10"
    MediaPlayer.ScaleType.SURFACE_2_1 -> "2:1"
    MediaPlayer.ScaleType.SURFACE_221_1 -> "2.21:1"
    MediaPlayer.ScaleType.SURFACE_235_1 -> "2.35:1"
    MediaPlayer.ScaleType.SURFACE_239_1 -> "2.39:1"
    MediaPlayer.ScaleType.SURFACE_5_4 -> "5:4"
    else -> resources.getString(R.string.nag_scale_fit)
}

/** The film's picture at [scale], kept for the next films as the classic player keeps it; its name said over the video. */
internal fun applyPictureSize(activity: VideoPlayerActivity, scale: MediaPlayer.ScaleType) {
    val player = activity.service?.mediaplayer?.takeIf { !it.isReleased } ?: return
    player.videoScale = scale
    Settings.getInstance(activity).putSingle(VIDEO_RATIO, scale.ordinal)
    activity.chrome?.showInfo(pictureSizeLabel(activity.resources, scale), PICTURE_SAID_MS)
}

private const val PICTURE_SAID_MS = 1_000

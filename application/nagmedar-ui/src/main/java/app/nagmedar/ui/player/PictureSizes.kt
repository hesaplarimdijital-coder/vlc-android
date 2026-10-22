package app.nagmedar.ui.player

import android.content.res.Resources
import androidx.core.os.ConfigurationCompat
import app.nagmedar.ui.R
import org.videolan.libvlc.MediaPlayer
import org.videolan.tools.Settings
import org.videolan.tools.VIDEO_RATIO
import org.videolan.tools.putSingle
import org.videolan.vlc.gui.video.VideoPlayerActivity
import java.text.NumberFormat
import java.util.Locale

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

/** A picture size's name: "Fit", "Fill the screen (crop)"; a ratio as [ratioLabel] writes it, "2,35:1" in Turkish. */
internal fun pictureSizeLabel(resources: Resources, scale: MediaPlayer.ScaleType): String {
    val locale = ConfigurationCompat.getLocales(resources.configuration)[0] ?: Locale.getDefault()
    return when (scale) {
        MediaPlayer.ScaleType.SURFACE_BEST_FIT -> resources.getString(R.string.nag_scale_fit)
        MediaPlayer.ScaleType.SURFACE_FIT_SCREEN -> resources.getString(R.string.nag_scale_fill)
        MediaPlayer.ScaleType.SURFACE_FILL -> resources.getString(R.string.nag_scale_stretch)
        MediaPlayer.ScaleType.SURFACE_ORIGINAL -> resources.getString(R.string.nag_scale_original)
        MediaPlayer.ScaleType.SURFACE_16_9 -> ratioLabel(16.0, 9, locale)
        MediaPlayer.ScaleType.SURFACE_4_3 -> ratioLabel(4.0, 3, locale)
        MediaPlayer.ScaleType.SURFACE_16_10 -> ratioLabel(16.0, 10, locale)
        MediaPlayer.ScaleType.SURFACE_2_1 -> ratioLabel(2.0, 1, locale)
        MediaPlayer.ScaleType.SURFACE_221_1 -> ratioLabel(2.21, 1, locale)
        MediaPlayer.ScaleType.SURFACE_235_1 -> ratioLabel(2.35, 1, locale)
        MediaPlayer.ScaleType.SURFACE_239_1 -> ratioLabel(2.39, 1, locale)
        MediaPlayer.ScaleType.SURFACE_5_4 -> ratioLabel(5.0, 4, locale)
        else -> resources.getString(R.string.nag_scale_fit)
    }
}

/**
 * A ratio of the picture, [width] to [height], its decimals as the language writes them
 * (TERIMLER): "2,39:1" in Turkish, "2.39:1" in English; "16:9". The picture sizes and the
 * effects tab write their ratios with it.
 */
internal fun ratioLabel(width: Double, height: Int, locale: Locale): String {
    val format = NumberFormat.getNumberInstance(locale).apply {
        maximumFractionDigits = 2
        isGroupingUsed = false
    }
    return format.format(width) + ":" + height
}

/** The film's picture at [scale], kept for the next films as the classic player keeps it; its name said over the video. */
internal fun applyPictureSize(activity: VideoPlayerActivity, scale: MediaPlayer.ScaleType) {
    val player = activity.service?.mediaplayer?.takeIf { !it.isReleased } ?: return
    player.videoScale = scale
    Settings.getInstance(activity).putSingle(VIDEO_RATIO, scale.ordinal)
    activity.chrome?.showInfo(pictureSizeLabel(activity.resources, scale), PICTURE_SAID_MS)
}

private const val PICTURE_SAID_MS = 1_000

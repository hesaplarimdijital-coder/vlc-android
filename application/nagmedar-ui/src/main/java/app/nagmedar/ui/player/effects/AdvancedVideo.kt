package app.nagmedar.ui.player.effects

import androidx.compose.runtime.Immutable
import app.nagmedar.ui.R
import kotlin.math.roundToInt

/**
 * Where an overlay stands in the picture (S-610), as VLC's logo and marq filters place it: their
 * "position" option ([vlc]: 1 left, 2 right, 4 top, 8 bottom, added; 0 the centre).
 */
enum class OverlayPosition(override val key: String, override val label: Int, val vlc: Int) : EffectOption {
    TopLeft("top-left", R.string.nag_fx_pos_top_left, 5),
    Top("top", R.string.nag_fx_pos_top, 4),
    TopRight("top-right", R.string.nag_fx_pos_top_right, 6),
    Left("left", R.string.nag_fx_pos_left, 1),
    Center("center", R.string.nag_fx_pos_center, 0),
    Right("right", R.string.nag_fx_pos_right, 2),
    BottomLeft("bottom-left", R.string.nag_fx_pos_bottom_left, 9),
    Bottom("bottom", R.string.nag_fx_pos_bottom, 8),
    BottomRight("bottom-right", R.string.nag_fx_pos_bottom_right, 10),
    ;

    val left: Boolean get() = vlc and 1 != 0
    val right: Boolean get() = vlc and 2 != 0
    val top: Boolean get() = vlc and 4 != 0
    val bottom: Boolean get() = vlc and 8 != 0
}

/** The text's height, a share of the picture's: VLC's marq takes it in the picture's pixels. */
enum class TextSize(override val key: String, override val label: Int, val share: Float) : EffectOption {
    Small("small", R.string.nag_fx_text_small, 0.035f),
    Medium("medium", R.string.nag_fx_text_medium, 0.05f),
    Large("large", R.string.nag_fx_text_large, 0.08f),
}

/**
 * A clock after the text, as VLC's marq writes it ([format], strftime): none, the time, the date
 * and time; [pattern] is the same for the preview (SimpleDateFormat).
 */
enum class TextClock(override val key: String, override val label: Int, val format: String?, val pattern: String?) : EffectOption {
    None("none", R.string.nag_fx_clock_none, null, null),
    Time("time", R.string.nag_fx_clock_time, "%H:%M:%S", "HH:mm:ss"),
    DateTime("datetime", R.string.nag_fx_clock_date_time, "%Y-%m-%d %H:%M", "yyyy-MM-dd HH:mm"),
}

/**
 * VLC's anaglyph schemes: which colours of the picture's left half and right half reach the eye
 * behind each filter ([left], [right] as RGB masks); a colour both have is their mean.
 */
enum class AnaglyphScheme(override val key: String, override val label: Int, val left: Int, val right: Int) : EffectOption {
    RedCyan("red-cyan", R.string.nag_fx_3d_red_cyan, 0xFF0000, 0x00FFFF),
    RedGreen("red-green", R.string.nag_fx_3d_red_green, 0xFF0000, 0x00FF00),
    RedBlue("red-blue", R.string.nag_fx_3d_red_blue, 0xFF0000, 0x0000FF),
    MagentaCyan("magenta-cyan", R.string.nag_fx_3d_magenta_cyan, 0xFF00FF, 0x00FFFF),
    Trioscopic("trioscopic", R.string.nag_fx_3d_trioscopic, 0x00FF00, 0xFF00FF),
    ;

    /** How much of the left half's red, green and blue, then the right half's, the picture keeps (0, ½ or 1). */
    fun weights(): Pair<FloatArray, FloatArray> {
        fun has(mask: Int, channel: Int) = mask and (0xFF shl (16 - 8 * channel)) != 0
        val l = FloatArray(3)
        val r = FloatArray(3)
        for (c in 0..2) {
            val both = has(left, c) && has(right, c)
            l[c] = if (!has(left, c)) 0f else if (both) 0.5f else 1f
            r[c] = if (!has(right, c)) 0f else if (both) 0.5f else 1f
        }
        return l to r
    }
}

/** A picture over the video (VLC's logo): a PNG the app keeps ([file], a path libVLC reads), where, how opaque. */
@Immutable
data class LogoOverlay(
    val enabled: Boolean = false,
    /** The picked picture, copied as a PNG into the app's cache; null until one is picked. */
    val file: String? = null,
    /** The picked file's name, as the panel shows it. */
    val name: String? = null,
    val position: OverlayPosition = OverlayPosition.TopRight,
    val opacityPercent: Int = 80,
)

/** Text over the video (VLC's marq): what, where, how big, its colour (RGB), how opaque, and a clock after it. */
@Immutable
data class TextOverlay(
    val enabled: Boolean = false,
    val text: String = "",
    val position: OverlayPosition = OverlayPosition.BottomLeft,
    val size: TextSize = TextSize.Medium,
    val colour: Int = OVERLAY_COLOURS.first(),
    val opacityPercent: Int = 100,
    val clock: TextClock = TextClock.None,
)

/** The text's colours offered: white, yellow, cyan, green, red, black. */
val OVERLAY_COLOURS: List<Int> = listOf(0xFFFFFF, 0xFFE14D, 0x4DD9FF, 0x7CE38B, 0xFF5A5A, 0x000000)

/**
 * The advanced video effects (S-610): a logo and a text over the picture, the picture turned by
 * any angle, a side-by-side 3D film as an anaglyph. For the session: every film gets them until
 * they are changed, and the app forgets them when it goes; they are not a film's own (S-603's
 * "Save for this file" leaves them). The mirror and the quarter turns are S-505's [VideoTransform].
 */
@Immutable
data class AdvancedVideo(
    val logo: LogoOverlay = LogoOverlay(),
    val text: TextOverlay = TextOverlay(),
    /** The picture turned clockwise by 0 to 359 degrees, within its frame. */
    val angle: Int = 0,
    val anaglyph: Boolean = false,
    val anaglyphScheme: AnaglyphScheme = AnaglyphScheme.RedCyan,
) {
    val isNeutral: Boolean get() = AdvancedVideoOptions.build(this).isEmpty

    /** Whether a filter of these works on pictures in memory (rotate, anaglyph): see [AdvancedVideoOptions]. */
    val onCpu: Boolean get() = AdvancedVideoOptions.build(this).cpu
}

/**
 * What the preview draws the effects on (S-610). A frame read from the file has none of them yet:
 * the best. The screen's frame has those libVLC shows drawn already (the session's it started
 * with, S-505's turn), so drawing the effects over it would draw them twice: it is taken only
 * while none shows. Else a sample picture.
 */
enum class PreviewSource {
    Raw,
    Screen,
    Sample,
    ;

    companion object {
        fun choose(raw: Boolean, screen: Boolean, inForce: Boolean): PreviewSource = when {
            raw -> Raw
            screen && !inForce -> Screen
            else -> Sample
        }

        /** Whether libVLC shows effects already: the session's it started with, or a transform. */
        fun inForce(started: VideoFilterOptions, startedTransform: String?): Boolean = !started.isEmpty || startedTransform != null
    }
}

/** libVLC's options for [AdvancedVideo]: video filters before and after the transform, sub-sources, their options. */
@Immutable
data class VideoFilterOptions(
    val before: List<String> = emptyList(),
    val after: List<String> = emptyList(),
    val subSources: List<String> = emptyList(),
    val options: List<String> = emptyList(),
    val cpu: Boolean = false,
) {
    val isEmpty: Boolean get() = before.isEmpty() && after.isEmpty() && subSources.isEmpty()
}

/**
 * [AdvancedVideo] as libVLC 4's options (S-610). libVLC 4 builds a player's video filters
 * ("video-filter") and the sources drawn over the picture ("sub-source") from the instance's
 * options as the video output opens; a film's own options (":video-filter=…") never reach the
 * output, which belongs to the player, and the published libVLC has no call that changes them
 * while a film plays (libvlc_video_set_logo_* and _marquee_* are not in its Java API). So they
 * are options of libVLC's next start ([org.videolan.resources.NagmedarVlcOptions]), and the
 * panel's "Apply now" starts libVLC again and reopens the film where it was.
 *
 * What they cost, from how VLC 4 does them:
 * - logo and marq are sub-sources: VLC draws them as subpictures, which the OpenGL output lays
 *   over the picture as textures and Android's own output in its subtitle layer. The picture
 *   stays on the GPU: hardware decoding, direct rendering and HDR passthrough stay. Light.
 * - transform (S-505's mirror and quarter turns) needs the OpenGL output: no HDR passthrough
 *   (tone mapped), the GPU's work only.
 * - rotate and anaglyph are CPU filters (rotate turns planar and packed YUV, anaglyph only 8-bit
 *   I420): the filter chain needs the pictures in memory, so MediaCodec hands its frames over
 *   (":no-mediacodec-dr") instead of drawing them into the screen's surface, 10-bit HDR is
 *   converted down, and each frame is turned or mixed by the CPU. Heavy on 4K (dropped frames,
 *   battery): the panel warns. The device check is the Info tab's dropped frames with each on.
 */
object AdvancedVideoOptions {
    /** The picture's height when the playing film's is not known. */
    const val DEFAULT_HEIGHT = 1080

    /** An overlay's distance from the picture's edges, a share of its height. */
    const val MARGIN_SHARE = 0.03f

    fun build(video: AdvancedVideo, pictureHeight: Int = DEFAULT_HEIGHT): VideoFilterOptions {
        val height = pictureHeight.takeIf { it > 0 } ?: DEFAULT_HEIGHT
        val margin = (height * MARGIN_SHARE).roundToInt()
        val before = ArrayList<String>()
        val after = ArrayList<String>()
        val sources = ArrayList<String>()
        val options = ArrayList<String>()
        // a side-by-side picture is read as it comes, before anything turns it
        if (video.anaglyph) {
            before += "anaglyph"
            options += "--anaglyph-scheme=${video.anaglyphScheme.key}"
        }
        val angle = normalAngle(video.angle)
        if (angle != 0) {
            after += "rotate"
            options += "--rotate-angle=$angle"
        }
        val logo = video.logo
        val file = logo.file?.takeIf { logo.enabled && logoPathUsable(it) }
        if (file != null) {
            sources += "logo"
            val (x, y) = offsets(logo.position, margin)
            options += listOf(
                "--logo-file=$file",
                "--logo-position=${logo.position.vlc}",
                "--logo-x=$x",
                "--logo-y=$y",
                "--logo-opacity=${opacity(logo.opacityPercent)}",
            )
        }
        val text = video.text
        val marquee = marqueeText(text.text, text.clock).takeIf { text.enabled }
        if (!marquee.isNullOrEmpty()) {
            sources += "marq"
            val (x, y) = offsets(text.position, margin)
            options += listOf(
                "--marq-marquee=$marquee",
                "--marq-position=${text.position.vlc}",
                "--marq-x=$x",
                "--marq-y=$y",
                "--marq-size=${textPixels(text.size, height)}",
                "--marq-color=${text.colour and 0xFFFFFF}",
                "--marq-opacity=${opacity(text.opacityPercent)}",
                "--marq-timeout=0",
            )
            if (text.clock != TextClock.None) options += "--marq-refresh=$CLOCK_REFRESH_MS"
        }
        return VideoFilterOptions(before, after, sources, options, cpu = before.isNotEmpty() || after.isNotEmpty())
    }

    /** 0 to 359, whatever was asked: −90 is 270, 360 is 0. */
    fun normalAngle(degrees: Int): Int = degrees.mod(360)

    /**
     * The text as VLC's marq takes it: its "%" written "%%" (marq passes the text to strftime),
     * on one line, the clock's format after it.
     */
    fun marqueeText(text: String, clock: TextClock): String {
        val line = text.map { if (it.isISOControl()) ' ' else it }.joinToString("").trim().replace("%", "%%")
        return listOfNotNull(line.takeIf { it.isNotEmpty() }, clock.format).joinToString(" ")
    }

    /** The text's height in the picture's pixels. */
    fun textPixels(size: TextSize, pictureHeight: Int): Int = (pictureHeight * size.share).roundToInt().coerceIn(MIN_TEXT_PX, MAX_TEXT_PX)

    /** 0 to 100 % as VLC's 0 to 255. */
    fun opacity(percent: Int): Int = (percent.coerceIn(0, 100) * 255 + 50) / 100

    /**
     * A path VLC's logo-file reads as one picture: it splits its value at ";" (pictures) and ","
     * (a picture's delay and opacity). The app's own copies never have them.
     */
    fun logoPathUsable(path: String): Boolean = path.isNotBlank() && ';' !in path && ',' !in path

    /**
     * The overlay's offsets from the edges it is aligned to (VLC moves an aligned subpicture
     * inwards by them; a centred one would move off centre, so it gets none).
     */
    fun offsets(position: OverlayPosition, margin: Int): Pair<Int, Int> =
        (if (position.left || position.right) margin else 0) to (if (position.top || position.bottom) margin else 0)

    /**
     * Where VLC puts an item of [itemWidth] × [itemHeight] in a picture of [width] × [height] at
     * [position], [margin] from its edges: its top left corner (vout_subpictures.c's SpuRegionPlace).
     */
    fun place(position: OverlayPosition, width: Float, height: Float, itemWidth: Float, itemHeight: Float, margin: Float): Pair<Float, Float> {
        val x = when {
            position.left -> margin
            position.right -> width - itemWidth - margin
            else -> width / 2 - itemWidth / 2
        }
        val y = when {
            position.top -> margin
            position.bottom -> height - itemHeight - margin
            else -> height / 2 - itemHeight / 2
        }
        return x to y
    }

    private const val MIN_TEXT_PX = 8
    private const val MAX_TEXT_PX = 400
    private const val CLOCK_REFRESH_MS = 1000
}

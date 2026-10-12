package app.nagmedar.ui.player.subtitles

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.nagmedar.ui.player.LibVlcExtensions
import app.nagmedar.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.videolan.libvlc.MediaPlayer
import org.videolan.tools.KEY_NAG_ASS_OURS
import org.videolan.tools.KEY_NAG_SUBTITLE_BLACK_BARS
import org.videolan.tools.KEY_NAG_SUBTITLE_MARGIN
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND_COLOR
import org.videolan.tools.KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_BOLD
import org.videolan.tools.KEY_SUBTITLES_COLOR
import org.videolan.tools.KEY_SUBTITLES_COLOR_OPACITY
import org.videolan.tools.KEY_SUBTITLES_FONT
import org.videolan.tools.KEY_SUBTITLES_OUTLINE
import org.videolan.tools.KEY_SUBTITLES_OUTLINE_SIZE
import org.videolan.tools.KEY_SUBTITLES_SHADOW
import org.videolan.tools.KEY_SUBTITLES_SIZE
import org.videolan.tools.Settings
import java.util.Locale
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The subtitles' fonts (S-705): the system's families, which libVLC's text renderer finds on
 * Android ([family], as /system/etc/fonts.xml names them).
 */
enum class SubtitleFont(val key: String, val family: String, @StringRes val label: Int) {
    Sans("sans", "sans-serif", R.string.nag_style_font_sans),
    Serif("serif", "serif", R.string.nag_style_font_serif),
    Mono("mono", "monospace", R.string.nag_style_font_mono),
    Condensed("condensed", "sans-serif-condensed", R.string.nag_style_font_condensed),
    ;

    companion object {
        fun of(key: String?): SubtitleFont? = entries.firstOrNull { it.key == key }
    }
}

/** The colours offered, as the mockups' player has them: white, a warm yellow, cyan, grey. */
enum class SubtitleColour(val key: String, val argb: Int, @StringRes val label: Int) {
    White("white", 0xFFFFFFFF.toInt(), R.string.nag_style_white),
    Yellow("yellow", 0xFFFFE45C.toInt(), R.string.nag_style_yellow),
    Cyan("cyan", 0xFF7FE8FF.toInt(), R.string.nag_style_cyan),
    Grey("grey", 0xFFD0D4DA.toInt(), R.string.nag_style_grey),
    ;

    companion object {
        fun of(argb: Int): SubtitleColour? = entries.firstOrNull { it.argb == argb }
    }
}

/** What the subtitles of ASS files look like: as the file styles them, or as this look has them. */
enum class AssStyles(val key: String, @StringRes val label: Int) {
    Keep("file", R.string.nag_style_ass_file),
    Ours("ours", R.string.nag_style_ass_ours),
    ;

    companion object {
        fun of(key: String?): AssStyles? = entries.firstOrNull { it.key == key }
    }
}

/**
 * How the subtitles look (S-705), for every film. The text's [sizePx] is its height on a 1080p
 * picture (VLC's normal is 68); [outlinePx] is VLC's outline: none, thin, normal or thick;
 * [backgroundPercent] the box behind the text. [raisePx], [inBlackBars] and [ass] wait for the
 * player's native side.
 */
@Immutable
data class SubtitleStyle(
    val font: SubtitleFont = SubtitleFont.Sans,
    val bold: Boolean = false,
    val sizePx: Int = VLC_SIZE_PX,
    val outlinePx: Int = VLC_OUTLINE_PX,
    val shadow: Boolean = true,
    val backgroundPercent: Int = 0,
    val colourArgb: Int = SubtitleColour.White.argb,
    /** How far above the picture's bottom edge, in pixels of a 1080p picture. */
    val raisePx: Int = 0,
    /** Under a wide film, in the black bar rather than over the picture. */
    val inBlackBars: Boolean = false,
    val ass: AssStyles = AssStyles.Keep,
) {
    val colour: SubtitleColour? get() = SubtitleColour.of(colourArgb)

    /** VLC 4's text scale, in percent of its normal size. */
    val textScale: Int get() = (sizePx * 100 / VLC_SIZE_EXACT).roundToInt().coerceIn(MIN_SCALE, MAX_SCALE)

    companion object {
        /** VLC's normal text: a sixteenth of the picture's height, 67,5 px on a 1080p one. */
        const val VLC_SIZE_EXACT = 67.5
        const val VLC_SIZE_PX = 68
        const val VLC_OUTLINE_PX = 4
        const val MIN_SIZE_PX = 24
        const val MAX_SIZE_PX = 120
        const val SIZE_STEP_PX = 2
        val OUTLINES = listOf(0, 2, 4, 6)
        const val MAX_RAISE_PX = 200
        private const val MIN_SCALE = 10
        private const val MAX_SCALE = 500
    }
}

/** The ready looks (S-705); a look changed by hand is none of them ("custom"). */
enum class SubtitlePreset(val key: String, @StringRes val label: Int, private val look: SubtitleStyle) {
    /** VLC's own: the classic player's subtitles. */
    Vlc("vlc", R.string.nag_style_preset_vlc, SubtitleStyle()),

    /** The mockups' player: smaller, a thin outline, no shadow. */
    Thin("thin", R.string.nag_style_preset_thin, SubtitleStyle(sizePx = 44, outlinePx = 2, shadow = false)),

    /** The cinema's yellow. */
    Yellow("yellow", R.string.nag_style_preset_yellow, SubtitleStyle(sizePx = 60, outlinePx = 2, colourArgb = SubtitleColour.Yellow.argb)),

    /** White on a dark box, for bright pictures. */
    Boxed("boxed", R.string.nag_style_preset_boxed, SubtitleStyle(sizePx = 56, outlinePx = 0, shadow = false, backgroundPercent = 60)),
    ;

    /** [style] with this preset's look; where the subtitles stand and the ASS choice stay. */
    fun over(style: SubtitleStyle): SubtitleStyle =
        look.copy(raisePx = style.raisePx, inBlackBars = style.inBlackBars, ass = style.ass)

    fun matches(style: SubtitleStyle): Boolean = over(style) == style

    companion object {
        fun of(style: SubtitleStyle): SubtitlePreset? = entries.firstOrNull { it.matches(style) }
    }
}

/** "font=serif;bold=1;size=44;…": a look in the settings, unknown keys skipped, missing ones as VLC has them. */
object SubtitleStyleCodec {
    fun encode(style: SubtitleStyle): String = listOf(
        "font" to style.font.key,
        "bold" to style.bold.flag(),
        "size" to style.sizePx.toString(),
        "outline" to style.outlinePx.toString(),
        "shadow" to style.shadow.flag(),
        "background" to style.backgroundPercent.toString(),
        "colour" to "%08x".format(Locale.ROOT, style.colourArgb),
        "raise" to style.raisePx.toString(),
        "bars" to style.inBlackBars.flag(),
        "ass" to style.ass.key,
    ).joinToString(";") { (key, value) -> "$key=$value" }

    fun decode(text: String?): SubtitleStyle? {
        if (text.isNullOrBlank()) return null
        val fields = text.split(';').mapNotNull { part ->
            val at = part.indexOf('=')
            if (at <= 0) null else part.substring(0, at).trim() to part.substring(at + 1).trim()
        }.toMap()
        val base = SubtitleStyle()
        return SubtitleStyle(
            font = SubtitleFont.of(fields["font"]) ?: base.font,
            bold = fields["bold"]?.let { it == "1" } ?: base.bold,
            sizePx = fields["size"]?.toIntOrNull()?.coerceIn(SubtitleStyle.MIN_SIZE_PX, SubtitleStyle.MAX_SIZE_PX) ?: base.sizePx,
            outlinePx = fields["outline"]?.toIntOrNull()?.takeIf { it in SubtitleStyle.OUTLINES } ?: base.outlinePx,
            shadow = fields["shadow"]?.let { it == "1" } ?: base.shadow,
            backgroundPercent = fields["background"]?.toIntOrNull()?.coerceIn(0, 100) ?: base.backgroundPercent,
            colourArgb = fields["colour"]?.toLongOrNull(16)?.toInt() ?: base.colourArgb,
            raisePx = fields["raise"]?.toIntOrNull()?.coerceIn(0, SubtitleStyle.MAX_RAISE_PX) ?: base.raisePx,
            inBlackBars = fields["bars"]?.let { it == "1" } ?: base.inBlackBars,
            ass = AssStyles.of(fields["ass"]) ?: base.ass,
        )
    }

    private fun Boolean.flag() = if (this) "1" else "0"
}

/** Where the look is kept: the app's settings, and VLC's own subtitle settings, which libVLC starts with. */
interface SubtitleStyleStore {
    fun load(): SubtitleStyle

    fun save(style: SubtitleStyle)
}

/**
 * The look in the settings ([KEY_STYLE]) and in VLC's subtitle settings, which the classic
 * settings screen shows too and libVLC reads as it starts (VLCOptions): its size, weight, colour,
 * outline, shadow, box and font. Before the first look is saved, the one VLC's settings make is
 * read, so what was set in the classic settings stays.
 */
class PrefsSubtitleStyleStore(private val prefs: SharedPreferences) : SubtitleStyleStore {

    constructor(context: Context) : this(Settings.getInstance(context))

    override fun load(): SubtitleStyle = SubtitleStyleCodec.decode(prefs.getString(KEY_STYLE, null)) ?: fromVlc()

    override fun save(style: SubtitleStyle) {
        val editor = prefs.edit()
        editor.putString(KEY_STYLE, SubtitleStyleCodec.encode(style))
        editor.putString(KEY_SUBTITLES_SIZE, relativeSize(style))
        editor.putBoolean(KEY_SUBTITLES_BOLD, style.bold)
        editor.putInt(KEY_SUBTITLES_COLOR, style.colourArgb)
        editor.putInt(KEY_SUBTITLES_COLOR_OPACITY, OPAQUE)
        editor.putBoolean(KEY_SUBTITLES_OUTLINE, style.outlinePx > 0)
        // VLC's thinnest outline where there is none: the switch above turns it off
        editor.putString(KEY_SUBTITLES_OUTLINE_SIZE, style.outlinePx.coerceAtLeast(SubtitleStyle.OUTLINES[1]).toString())
        editor.putBoolean(KEY_SUBTITLES_SHADOW, style.shadow)
        editor.putBoolean(KEY_SUBTITLES_BACKGROUND, style.backgroundPercent > 0)
        editor.putInt(KEY_SUBTITLES_BACKGROUND_COLOR, BLACK)
        editor.putInt(KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, (style.backgroundPercent * OPAQUE + 50) / 100)
        // the system's own family needs no option
        if (style.font == SubtitleFont.Sans) editor.remove(KEY_SUBTITLES_FONT) else editor.putString(KEY_SUBTITLES_FONT, style.font.family)
        // S-503: where the subtitles stand, as libVLC's options take it (NagmedarVlcOptions)
        editor.putInt(KEY_NAG_SUBTITLE_MARGIN, style.raisePx)
        editor.putBoolean(KEY_NAG_SUBTITLE_BLACK_BARS, style.inBlackBars)
        editor.putBoolean(KEY_NAG_ASS_OURS, style.ass == AssStyles.Ours)
        editor.apply()
    }

    /** The look VLC's subtitle settings make: the classic settings' size, weight, colour, outline, shadow and box. */
    private fun fromVlc(): SubtitleStyle {
        val base = SubtitleStyle()
        val size = prefs.getString(KEY_SUBTITLES_SIZE, null)?.toFloatOrNull()?.takeIf { it > 0f }
        val outline = prefs.getString(KEY_SUBTITLES_OUTLINE_SIZE, null)?.toIntOrNull()?.takeIf { it in SubtitleStyle.OUTLINES }
        return base.copy(
            font = SubtitleFont.entries.firstOrNull { it.family == prefs.getString(KEY_SUBTITLES_FONT, null) } ?: base.font,
            bold = prefs.getBoolean(KEY_SUBTITLES_BOLD, base.bold),
            sizePx = size?.let { snapSize((PICTURE_HEIGHT / it).roundToInt()) } ?: base.sizePx,
            outlinePx = if (prefs.getBoolean(KEY_SUBTITLES_OUTLINE, true)) outline ?: base.outlinePx else 0,
            shadow = prefs.getBoolean(KEY_SUBTITLES_SHADOW, base.shadow),
            backgroundPercent = if (prefs.getBoolean(KEY_SUBTITLES_BACKGROUND, false)) {
                (prefs.getInt(KEY_SUBTITLES_BACKGROUND_COLOR_OPACITY, OPAQUE) * 100 + OPAQUE / 2) / OPAQUE
            } else {
                0
            },
            colourArgb = runCatching { prefs.getInt(KEY_SUBTITLES_COLOR, base.colourArgb) }.getOrDefault(base.colourArgb) or OPAQUE_ALPHA,
        )
    }

    companion object {
        const val KEY_STYLE = "nagmedar_subtitle_style"
        private const val PICTURE_HEIGHT = 1080f
        private const val OPAQUE = 255
        private const val OPAQUE_ALPHA = 0xFF shl 24
        private const val BLACK = 0xFF000000.toInt()

        /**
         * VLC's subtitle size setting, from which VLCOptions makes libVLC's text scale (1 600 over
         * it, cut to a whole number): rounded down, so that it comes back to the same scale.
         */
        fun relativeSize(style: SubtitleStyle): String = "%.3f".format(Locale.ROOT, floor(VLC_RELATIVE * 100 / style.textScale * 1000) / 1000)

        /** VLC's normal size setting, at a text scale of 100. */
        private const val VLC_RELATIVE = 16.0
    }
}

/** [px] on the size slider's steps. */
fun snapSize(px: Int): Int =
    ((px.toFloat() / SubtitleStyle.SIZE_STEP_PX).roundToInt() * SubtitleStyle.SIZE_STEP_PX).coerceIn(SubtitleStyle.MIN_SIZE_PX, SubtitleStyle.MAX_SIZE_PX)

/**
 * The native side of the look (S-503): libVLC taking the text's size while a film plays
 * ([liveSize], Nağmedar's own libVLC), and the settings only it has: where the subtitles stand,
 * the black bars, the ASS styles ([supportsLayout], VLC's options as libVLC starts again). The
 * published libVLC has none of it ([PendingNativeSubtitleStyle]): the look reaches the subtitles
 * as libVLC starts again ([LibVlcRestart]).
 */
interface NativeSubtitleStyle {
    /** Whether the text's size changes the playing film's subtitles at once. */
    val liveSize: Boolean

    val supportsLayout: Boolean

    /** The playing film's subtitles take what [style] changes at once: their size. */
    fun apply(style: SubtitleStyle)
}

object PendingNativeSubtitleStyle : NativeSubtitleStyle {
    override val liveSize: Boolean = false
    override val supportsLayout: Boolean = false

    override fun apply(style: SubtitleStyle) = Unit
}

/**
 * libVLC with Nağmedar's additions ([LibVlcExtensions]): the text's size at once, through its
 * text scale; the rest, and the subtitles' place, through its options as it starts again.
 */
class LibVlcSubtitleStyle(private val player: () -> MediaPlayer?) : NativeSubtitleStyle {
    override val liveSize: Boolean get() = LibVlcExtensions.hasTextScale
    override val supportsLayout: Boolean = true

    override fun apply(style: SubtitleStyle) {
        val player = player() ?: return
        LibVlcExtensions.setSpuTextScale(player, style.textScale / 100f)
    }
}

/**
 * The subtitles' look (S-705): chosen in the subtitles' tab, a preset or by hand, kept for every
 * film. Until the native side takes it while a film plays, it waits for libVLC to start again:
 * as the player closes, when nothing plays ([LibVlcRestart]).
 */
@Stable
class SubtitleStyleController(
    private val store: SubtitleStyleStore,
    private val native: NativeSubtitleStyle,
    private val scope: CoroutineScope,
    /** A slider's moves write once, after they rest this long. */
    private val saveAfterMs: Long = 300,
) {
    var style by mutableStateOf(store.load())
        private set

    /** Whether a change waits for the player to open again. */
    var waiting by mutableStateOf(false)
        private set

    val preset: SubtitlePreset? get() = SubtitlePreset.of(style)

    val supportsLayout: Boolean get() = native.supportsLayout

    private var saving: Job? = null
    private var unsaved = false

    fun update(next: SubtitleStyle) {
        if (next == style) return
        val before = style
        style = next
        val sizeOnly = next == before.copy(sizePx = next.sizePx)
        if (native.liveSize) native.apply(next)
        if (!(sizeOnly && native.liveSize)) {
            waiting = true
            LibVlcRestart.pending = true
        }
        unsaved = true
        saving?.cancel()
        saving = scope.launch {
            if (saveAfterMs > 0) delay(saveAfterMs)
            save()
        }
    }

    fun choose(preset: SubtitlePreset) = update(preset.over(style))

    /** A film started: the playing player takes the look's size, when it can (a player outlives libVLC's options). */
    fun onMediaStarted() {
        if (native.liveSize) native.apply(style)
    }

    /** The player goes: what is left to save is saved. */
    fun release() {
        saving?.cancel()
        save()
    }

    private fun save() {
        if (!unsaved) return
        unsaved = false
        store.save(style)
    }
}

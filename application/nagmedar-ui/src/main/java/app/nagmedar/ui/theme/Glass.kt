package app.nagmedar.ui.theme

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

/**
 * How much of the content shows through a glass surface (Desktop Kit 1.0). Text sits on [Thin] and
 * [Regular]; they keep `text` readable (4.5:1) even over white (dark themes) or black (light theme)
 * content. [UltraThin] is for surfaces without small text. 1.0.3: [Thick] carries small words of
 * every tone, the secondary and tertiary ones too, and keeps them at 4.5:1 over the same content and
 * the accent's over the brightest (the phone's bottom chrome, the user's glass B: the mini player's
 * card with its artist line and times, the bottom bar).
 */
enum class GlassThickness { UltraThin, Thin, Regular, Thick }

/**
 * What glass looks like on this device right now:
 * - [Blur]: Android 12+ (API 31) with hardware rendering: the content behind is blurred (Haze).
 *   A blur source cannot be drawn into a software canvas (`View.draw(Canvas(bitmap))`); to capture
 *   a window as a picture use `PixelCopy`, or provide [LocalGlassMode] = [Translucent] while drawing it;
 * - [Translucent]: Android 11 and older (and Robolectric's software renderer): a translucent tint, no blur;
 * - [Solid]: battery saver or "Remove animations" (animator scale 0) is on: an opaque surface,
 *   nothing is recorded or blurred.
 */
enum class GlassMode { Blur, Translucent, Solid }

/**
 * One glass material: the tint drawn over the blurred content, the blur radius and a little
 * noise; [translucent] is used without blur, [solid] in battery saver.
 */
@Immutable
data class GlassMaterial(
    val tint: Color,
    val blurRadius: Dp,
    val noise: Float,
    val translucent: Color,
    val solid: Color,
)

/**
 * Frosted glass for the phone's chrome (Desktop Kit 1.0's DeskGlass), tuned to the light, dark and
 * OLED palettes.
 *
 * Only these surfaces are glass, because every blurred surface costs a GPU pass per frame: the
 * phone's bottom bar and the player strip above it, the phone's top bar once content is scrolled
 * under it, the sheets ([app.nagmedar.ui.components.GlassBottomSheet]), menus and context menus.
 * 1.0.3 (the user's decision of 6 Nov, B): of these only the phone's bottom chrome is glass, one
 * [GlassThickness.Thick] surface; the top bar, sheets and menus are solid.
 *
 * [app.nagmedar.ui.shell.NagmedarShell] records the content area as the glass source
 * ([LocalGlassSource]) and picks the [GlassMode] ([LocalGlassMode]); [glass] draws a surface with it.
 */
object NagGlass {
    /** Glass over the window chrome colour: the bars, the top bar. */
    fun chrome(colors: NagColors, thickness: GlassThickness = GlassThickness.Regular): GlassMaterial =
        material(colors, colors.chrome, thickness)

    /** Glass over the raised surface colour: menus, sheets, the phone's mini player. */
    fun raised(colors: NagColors, thickness: GlassThickness = GlassThickness.Regular): GlassMaterial =
        material(colors, colors.surfaceRaised, thickness)

    /**
     * A material over [base]. The tint takes a trace (4 %) of the accent, so glass follows an
     * accent change (and cross-fades with it, as the artwork's colour does in the player).
     */
    fun material(colors: NagColors, base: Color, thickness: GlassThickness): GlassMaterial {
        val oled = colors.isDark && colors.background == Color.Black
        val alpha = tintAlpha(colors.isDark, oled, thickness)
        val tinted = mixSrgb(colors.accent, base, 0.04f)
        return GlassMaterial(
            tint = tinted.copy(alpha = alpha),
            blurRadius = when (thickness) {
                GlassThickness.UltraThin -> 16.dp
                GlassThickness.Thin -> 22.dp
                // the thick glass blurs as the regular one: only its tint is denser
                GlassThickness.Regular, GlassThickness.Thick -> 28.dp
            },
            noise = 0.04f,
            translucent = tinted.copy(alpha = if (oled) 0.97f else 0.96f),
            solid = base,
        )
    }

    /** Opacity of the tint over the blur, per palette. */
    fun tintAlpha(dark: Boolean, oled: Boolean, thickness: GlassThickness): Float = when {
        oled -> when (thickness) {
            GlassThickness.UltraThin -> 0.62f
            GlassThickness.Thin -> 0.76f
            GlassThickness.Regular -> 0.86f
            GlassThickness.Thick -> 0.88f
        }
        dark -> when (thickness) {
            GlassThickness.UltraThin -> 0.55f
            GlassThickness.Thin -> 0.72f
            GlassThickness.Regular -> 0.82f
            GlassThickness.Thick -> 0.88f
        }
        else -> when (thickness) {
            GlassThickness.UltraThin -> 0.50f
            GlassThickness.Thin -> 0.68f
            GlassThickness.Regular -> 0.80f
            GlassThickness.Thick -> 0.90f
        }
    }

    /**
     * The colour a glass surface shows in [mode] over [behind] (the content, already blurred).
     * Used by the contrast tests; the screen draws the real thing.
     */
    fun composite(material: GlassMaterial, mode: GlassMode, behind: Color): Color = when (mode) {
        GlassMode.Blur -> material.tint.compositeOver(behind)
        GlassMode.Translucent -> material.translucent.compositeOver(behind)
        GlassMode.Solid -> material.solid
    }

    /** The mode for these conditions; see [GlassMode]. */
    fun modeFor(sdk: Int, powerSave: Boolean, reducedMotion: Boolean, hardwareAccelerated: Boolean): GlassMode = when {
        app.nagmedar.ui.shell.DepthExperiment.glassSolid -> GlassMode.Solid
        powerSave || reducedMotion -> GlassMode.Solid
        sdk < Build.VERSION_CODES.S || !hardwareAccelerated -> GlassMode.Translucent
        else -> GlassMode.Blur
    }

    /** The glass source of the surrounding shell, if any. */
    val source: HazeState?
        @Composable @ReadOnlyComposable get() = LocalGlassSource.current

    /** The glass mode of the surrounding shell (or of the device, outside a shell). */
    val mode: GlassMode
        @Composable get() = LocalGlassMode.current ?: rememberSystemGlassMode()
}

/**
 * The content that glass surfaces blur. [app.nagmedar.ui.shell.NagmedarShell] provides its content
 * area; null outside the shell, where glass is the solid surface.
 */
val LocalGlassSource = staticCompositionLocalOf<HazeState?> { null }

/**
 * Forces a [GlassMode] below this point (null follows the device). The shell provides the mode it
 * picked, so menus and sheets opened from a screen agree with the bars; tests may pin one here.
 */
val LocalGlassMode = staticCompositionLocalOf<GlassMode?> { null }

/** The [GlassMode] this device allows now; follows battery saver and the animation setting live. */
@Composable
fun rememberSystemGlassMode(): GlassMode {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = NagTheme.motion.reduced
    var powerSave by remember { mutableStateOf(isPowerSave(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                powerSave = isPowerSave(context)
            }
        }
        val registered = runCatching {
            context.registerReceiver(receiver, IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED))
        }.isSuccess
        // it may have changed between the first read and the registration
        powerSave = isPowerSave(context)
        onDispose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
    }
    return NagGlass.modeFor(Build.VERSION.SDK_INT, powerSave, reduced, view.isHardwareAccelerated && !isTestRenderer)
}

/**
 * Robolectric draws into software canvases (the screenshot tests draw the window into a bitmap),
 * which cannot draw the blur's render nodes; tests get the translucent look instead (as on Android 11).
 */
private val isTestRenderer: Boolean = Build.FINGERPRINT == "robolectric"

private fun isPowerSave(context: Context): Boolean =
    (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true

/**
 * Marks this element's drawing as glass source [state]: the content glass surfaces blur. Only
 * records while [mode] is [GlassMode.Blur]; otherwise nothing changes. 1.0.3: a source that starts
 * recording (the app started, battery saver or "Animasyonları kaldır" turned off) draws itself once
 * more two frames later ([drawnAgain]): with Haze 2.0.1 the glass over content that did not draw
 * again stayed empty, the rows sharp beneath it, until the page drew anew.
 */
@Composable
fun Modifier.glassSource(state: HazeState?, mode: GlassMode): Modifier {
    if (state == null || mode != GlassMode.Blur) return this
    return drawnAgain(state).hazeSource(state)
}

/**
 * Draws a glass surface of [material] in [shape] behind this element's content:
 * - [GlassMode.Blur] with a [source]: the source's content blurred and tinted (Haze);
 * - [GlassMode.Translucent]: the translucent tint, no blur;
 * - [GlassMode.Solid], or no source to blur: the opaque [GlassMaterial.solid] colour.
 *
 * [enabled] false draws [rest] instead (a top bar that is glass only while content is under it).
 */
@Composable
fun Modifier.glass(
    material: GlassMaterial,
    shape: Shape = RectangleShape,
    source: HazeState? = LocalGlassSource.current,
    mode: GlassMode = NagGlass.mode,
    enabled: Boolean = true,
    rest: Color = Color.Transparent,
): Modifier = when {
    !enabled -> if (rest == Color.Transparent) this else background(rest, shape)
    mode == GlassMode.Blur && source != null -> {
        // Desktop Kit 2: a Haze 2 style is a replayable program, made once per material, not per frame
        val style = remember(material) { material.blurStyle() }
        val input = remember(source) { HazeInput.Sources(source) }
        this
            .clip(shape)
            // as its source: drawn once more once it blurs, should it have drawn before the source first recorded
            .drawnAgain(source)
            .hazeBlur(input = input, style = style)
    }
    mode == GlassMode.Translucent && source != null -> background(material.translucent, shape)
    else -> background(material.solid, shape)
}

/**
 * 1.0.3: what follows this modifier (a glass source or a blur) draws once more two frames after it
 * first came for [key]: what a page drawn anew did for the glass after a switch at run time.
 */
@Composable
private fun Modifier.drawnAgain(key: Any): Modifier {
    val again = remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key) {
        withFrameNanos { }
        withFrameNanos { }
        again.intValue++
    }
    return drawWithContent {
        // read while drawing: the count's change draws this, and what follows it, again
        if (again.intValue >= 0) drawContent()
    }
}

/** The Haze blur of this material (Desktop Kit 2): its tint over the blurred content, the radius, a little noise. */
internal fun GlassMaterial.blurStyle(): HazeBlurStyle {
    val material = this
    return HazeBlurStyle {
        backgroundColor(material.solid)
        colorEffects(listOf(HazeColorEffect.tint(material.tint)))
        blurRadius(material.blurRadius)
        noiseFactor(material.noise)
        fallbackColorEffect(HazeColorEffect.tint(material.translucent))
    }
}

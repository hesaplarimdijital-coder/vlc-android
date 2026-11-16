package app.nagmedar.ui.music

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 1.1.3: one album's band becoming the next's as ebru is made, over [MARBLING_MS]: the new inks' drops fall one after
 * another on the band there was and spread, each pushing what lay there before it outwards (the band's own drop map,
 * undone pixel by pixel), while the old paper and inks fade into the new paper; then an unseen stylus is drawn down and
 * up the band, line after line, pulling the inks after it, then across it: the band's own steps ([EbruPlan]), each drawn
 * as a tip going through the tray. At its end it is the new band, made as before (its torn edge too, the old one's
 * becoming it on the way). 1.1.5: across once, over the time the fine comb and the slow wave had after it.
 *
 * Drawn by an AGSL shader (Android 13 and later), at most [BUDGET_PX] pixels a frame (a larger band drawn smaller and
 * stretched while it moves).
 */
internal class EbruMarbling private constructor(internal val shader: Shader, private val plan: EbruPlan) {

    /** 0 to 1 over the transition. */
    val progress = Animatable(0f)

    suspend fun run() {
        progress.animateTo(1f, tween(MARBLING_MS, easing = LinearEasing))
    }

    /** The shader set for this frame of a drawing [width] × [height] pixels. */
    fun frame(width: Float, height: Float) {
        if (Build.VERSION.SDK_INT >= 33) MarblingShader.frame(shader, progress.value, plan.width / width, plan.height / height)
    }

    /** What share of a band [width] × [height] pixels wide each side of the drawing is. */
    fun scaleFor(width: Int, height: Int): Float = min(1f, sqrt(BUDGET_PX.toFloat() / (width.toFloat() * height).coerceAtLeast(1f)))

    companion object {
        /** Robolectric's screenshots catch no transition; nor does an Android before 13 have the shader. */
        val supported: Boolean = Build.VERSION.SDK_INT >= 33 && !Ebru.synchronous

        /** The transition from [old] (made of [oldInks]) to [made]; null where the shader cannot be had. */
        fun of(old: Bitmap, oldInks: EbruInks, made: EbruMade): EbruMarbling? {
            if (Build.VERSION.SDK_INT < 33) return null
            val band = made.band ?: return null
            val stones = made.stones ?: return null
            return runCatching { EbruMarbling(MarblingShader.make(old, oldInks.paper, band, stones, made.plan), made.plan) }.getOrNull()
        }
    }
}

/** How long a band takes to be made over the last one (the user's 2.5 s). */
internal const val MARBLING_MS = 2500

/**
 * The most pixels the transition's shader draws a frame: a Galaxy S24's and a Tab S11 Ultra's band whole (a smaller GPU's
 * larger band is drawn smaller and stretched while it moves).
 */
private const val BUDGET_PX = 1_200_000

/** The transition, drawn [EbruMarbling.scaleFor] as large as it is shown and stretched to it. */
@Composable
internal fun MarblingBand(marbling: EbruMarbling, modifier: Modifier = Modifier) {
    val brush = remember(marbling) { ShaderBrush(marbling.shader) }
    Layout(
        content = {
            Spacer(
                Modifier.drawBehind {
                    // a software canvas (a view drawn into a bitmap) cannot run the shader: nothing of it there
                    if (!drawContext.canvas.nativeCanvas.isHardwareAccelerated) return@drawBehind
                    marbling.frame(size.width, size.height)
                    drawRect(brush)
                },
            )
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val s = marbling.scaleFor(width, height)
        val drawnWidth = (width * s).roundToInt().coerceAtLeast(1)
        val drawnHeight = (height * s).roundToInt().coerceAtLeast(1)
        val drawn = measurables.first().measure(Constraints.fixed(drawnWidth, drawnHeight))
        layout(width, height) {
            drawn.placeWithLayer(0, 0) {
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = width / drawnWidth.toFloat()
                scaleY = height / drawnHeight.toFloat()
                // drawn at its own size, then stretched: the shader runs for the smaller drawing's pixels only
                compositingStrategy = if (drawnWidth < width) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            }
        }
    }
}

@RequiresApi(33)
internal object MarblingShader {

    /**
     * The shader for the transition from [old] (on [oldPaper]) to [band], whose drops before the stylus are [stones];
     * [source]: the shader's (a test's in an older shading language: Robolectric's Skia samples with sample()).
     */
    fun make(old: Bitmap, oldPaper: Int, band: Bitmap, stones: Bitmap, plan: EbruPlan, source: String = SOURCE): RuntimeShader = RuntimeShader(source).apply {
        setInputShader("oldBand", sampled(old))
        setInputShader("newBand", sampled(band))
        setInputShader("stones", sampled(stones))
        floats("size", plan.width.toFloat(), plan.height.toFloat())
        val (mx, my) = Ebru.stonesMargin(plan.width, plan.height)
        floats("stonesAt", mx.toFloat(), my.toFloat())
        floats("toBand", 1f, 1f)
        floats("t", 0f)
        // the last drop first: the shader undoes them from the last to fall
        val count = min(plan.count, MAX_DROPS)
        val drops = FloatArray(MAX_DROPS * 4)
        for (i in 0 until count) {
            val from = (count - 1 - i) * 4
            for (j in 0 until 4) drops[i * 4 + j] = plan.drops[from + j]
        }
        floats("count", count.toFloat())
        setFloatUniform("drops", drops)
        for (i in 0 until 5) colour("ink$i", plan.inks[min(i, plan.inks.size - 1)])
        colour("paper", plan.paper)
        colour("oldPaper", oldPaper)
        lines("vert", plan.vertical, VERTICAL_LINES)
        lines("horiz", plan.horizontal, HORIZONTAL_LINES)
        floats("alphas", plan.vertical.alpha, plan.horizontal.alpha)
        floats("lambdas", plan.vertical.lambda, plan.horizontal.lambda)
    }

    fun frame(shader: Shader, t: Float, toBandX: Float, toBandY: Float) {
        val runtime = shader as RuntimeShader
        runtime.floats("t", t)
        runtime.floats("toBand", toBandX, toBandY)
    }

    /** A uniform's floats, through the setter of arrays (any size; Robolectric's shadow sets only that one). */
    private fun RuntimeShader.floats(name: String, vararg values: Float) = setFloatUniform(name, values)

    /** An opaque colour, as the bands' bitmaps have it (sRGB). */
    private fun RuntimeShader.colour(name: String, argb: Int) =
        floats(name, (argb shr 16 and 0xFF) / 255f, (argb shr 8 and 0xFF) / 255f, (argb and 0xFF) / 255f, 1f)

    private fun RuntimeShader.lines(name: String, lines: EbruLines, most: Int) =
        floats(name, lines.first, lines.step, min(lines.count, most).toFloat(), if (lines.alternate) 1f else 0f)

    private fun sampled(bitmap: Bitmap) = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
        filterMode = BitmapShader.FILTER_MODE_LINEAR
    }

    /** The lines the shader undoes at most: a band has 15 down and up, 7 across. */
    private const val VERTICAL_LINES = 16
    private const val HORIZONTAL_LINES = 8

    /**
     * Each pixel of the band at time t (0…1) is found where it came from: through the steps made by then, undone from
     * the last (the stylus across, then the stylus down: each line as far as its tip has gone), into the
     * drops before the stylus ([stones]); while they are still falling, through the drops fallen (each growing to its
     * size as it spreads) into the band there was, its paper and inks fading into the new paper.
     */
    internal val SOURCE = """
        uniform shader oldBand;
        uniform shader newBand;
        uniform shader stones;
        uniform float2 size;
        uniform float2 stonesAt;
        uniform float2 toBand;
        uniform float t;
        uniform float count;
        uniform float4 drops[150];
        uniform half4 ink0;
        uniform half4 ink1;
        uniform half4 ink2;
        uniform half4 ink3;
        uniform half4 ink4;
        uniform half4 paper;
        uniform half4 oldPaper;
        uniform float4 vert;
        uniform float4 horiz;
        uniform float2 alphas;
        uniform float2 lambdas;

        half4 inkOf(float i) {
            if (i < 0.5) { return ink0; }
            if (i < 1.5) { return ink1; }
            if (i < 2.5) { return ink2; }
            if (i < 3.5) { return ink3; }
            return ink4;
        }

        float ease(float x) {
            return x * x * (3.0 - 2.0 * x);
        }

        // line k of n drawn one after another over [a, b], each overlapping the next by half
        float lineAt(float k, float n, float a, float b) {
            float step = (b - a) / (n + 1.0);
            return clamp((t - a - k * step) / (2.0 * step), 0.0, 1.0);
        }

        // where a point came from before a line through base along dir pulled it, the tip p of its way along the band
        float2 untine(float2 q, float2 base, float2 dir, float alpha, float lambda, float p, float soft, float len) {
            if (p <= 0.0) { return q; }
            float2 rel = q - base;
            float d = abs(dir.x * rel.y - dir.y * rel.x);
            float shift = alpha * lambda / (d + lambda);
            float f = 1.0;
            if (p < 1.0) {
                float s = dot(rel, dir);
                float near = (dir.x + dir.y < 0.0) ? -len : 0.0;
                float tip = mix(near - soft, near + len + soft, ease(p));
                f = 1.0 - smoothstep(tip - soft, tip + soft, s);
            }
            return q - dir * (shift * f);
        }

        // the drops fallen by now on the band there was, the last first, each pushing what was there outwards (in rows
        // of 15: a loop of the compiler's stays under 128 turns)
        half4 fallen(float2 p) {
            float2 q = p;
            float px = max(toBand.x, 1.0);
            half4 acc = half4(0.0);
            float remain = 1.0;
            float last = max(count - 1.0, 1.0);
            for (int row = 0; row < 10; row++) {
                if (float(row * 15) >= count) { break; }
                for (int col = 0; col < 15; col++) {
                    float i = float(row * 15 + col);
                    if (i >= count) { break; }
                    float4 d = drops[row * 15 + col];
                    float k = count - 1.0 - i;
                    float g = clamp((t - 0.28 * k / last) / 0.05, 0.0, 1.0);
                    if (g > 0.0) {
                        float inv = 1.0 - g;
                        float r = d.z * (1.0 - inv * inv * inv);
                        float2 v = q - d.xy;
                        float d2 = dot(v, v);
                        float len = sqrt(d2);
                        float cover = clamp(0.5 - (len - r) / px, 0.0, 1.0);
                        if (cover > 0.0) {
                            acc += inkOf(d.w) * half(remain * cover);
                            remain *= 1.0 - cover;
                            if (remain < 0.004) { return acc; }
                        }
                        q = d.xy + v * (sqrt(max(d2 - r * r, 0.0)) / max(len, 0.0001));
                    }
                }
            }
            half4 o = oldBand.eval(q);
            half4 before = o + oldPaper * (1.0 - o.a);
            return acc + mix(before, paper, half(smoothstep(0.02, 0.26, t))) * half(remain);
        }

        half4 main(float2 fragCoord) {
            float2 p = fragCoord * toBand;
            half4 colour;
            if (t < 0.33) {
                colour = fallen(p);
            } else {
                float2 q = p;
                for (int i = 0; i < 8; i++) {
                    float k = horiz.z - 1.0 - float(i);
                    if (k < 0.0) { break; }
                    float back = (horiz.w > 0.5 && mod(k, 2.0) > 0.5) ? -1.0 : 1.0;
                    q = untine(q, float2(0.0, horiz.x + k * horiz.y), float2(back, 0.0), alphas.y, lambdas.y,
                        lineAt(k, horiz.z, 0.62, 0.90), size.x * 0.10, size.x);
                }
                for (int i = 0; i < 16; i++) {
                    float k = vert.z - 1.0 - float(i);
                    if (k < 0.0) { break; }
                    float back = (vert.w > 0.5 && mod(k, 2.0) > 0.5) ? -1.0 : 1.0;
                    q = untine(q, float2(vert.x + k * vert.y, 0.0), float2(0.0, back), alphas.x, lambdas.x,
                        lineAt(k, vert.z, 0.33, 0.66), size.y * 0.20, size.y);
                }
                colour = stones.eval(q + stonesAt);
            }
            half a = mix(oldBand.eval(p).a, newBand.eval(p).a, half(smoothstep(0.16, 0.40, t)));
            half4 drawn = half4(colour.rgb * a, a);
            return mix(drawn, newBand.eval(p), half(smoothstep(0.90, 1.0, t)));
        }
    """.trimIndent()
}

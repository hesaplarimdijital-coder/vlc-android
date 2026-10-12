/*
 * Nağmedar: libVLC's options for what the player's native side cannot change while a film plays
 * (S-503 … S-506). Read as libVLC starts, like the subtitles' font; a change waits for libVLC to
 * start again (the player's LibVlcRestart).
 */
package org.videolan.resources

import android.content.SharedPreferences
import org.videolan.tools.KEY_NAG_ASS_OURS
import org.videolan.tools.KEY_NAG_SCALER
import org.videolan.tools.KEY_NAG_SECONDARY_SUB_TOP
import org.videolan.tools.KEY_NAG_SUBTITLE_BLACK_BARS
import org.videolan.tools.KEY_NAG_SUBTITLE_MARGIN
import org.videolan.tools.KEY_NAG_TONE_MAPPING
import org.videolan.tools.KEY_NAG_TRANSFORM

object NagmedarVlcOptions {

    /** VLC's "opengl" setting: the video output chosen by itself, Android's own, or OpenGL. */
    const val VOUT_AUTO = -1
    const val VOUT_ANDROID = 0
    const val VOUT_OPENGL = 1

    /** libplacebo's tone-mapping curves, by VLC's option values (TONEMAP_* in modules/video_output/libplacebo/utils.h). */
    val toneMappings: Map<String, Int> = mapOf("auto" to 0, "bt2390" to 2, "reinhard" to 3, "mobius" to 4, "hable" to 5)

    /** VLC's OpenGL scalers, by its option values (vlc_gl_scale in modules/video_output/opengl/gl_scale.h). */
    val scalers: Map<String, Int> = mapOf("auto" to 0, "spline36" to 2, "ewa_lanczos" to 6, "bilinear" to 8)

    /** VLC's transform filter's types. */
    val transforms: Set<String> = setOf("90", "180", "270", "hflip", "vflip", "transpose", "antitranspose")

    /**
     * The "--vout" option, or null for VLC's own choice. [opengl] is VLC's setting; left to itself,
     * the OpenGL output is taken when something only it does is asked for: a scaler, or the
     * picture turned.
     */
    fun vout(opengl: Int, pref: SharedPreferences): String? {
        val wanted = if (opengl == VOUT_AUTO && needsOpenGl(pref)) VOUT_OPENGL else opengl
        return when (wanted) {
            VOUT_OPENGL -> "--vout=gles2,none"
            VOUT_ANDROID -> "--vout=android_display,none"
            else -> null
        }
    }

    /** Whether something asked for happens only in the OpenGL output. */
    fun needsOpenGl(pref: SharedPreferences): Boolean = scaler(pref) != 0 || transform(pref) != null

    /** The options after "--vout": the subtitles' place, the OpenGL output's colours and scaling, the picture's turn. */
    fun options(pref: SharedPreferences): List<String> {
        val options = ArrayList<String>()
        // S-503: the subtitles' place
        val margin = pref.getInt(KEY_NAG_SUBTITLE_MARGIN, 0)
        if (margin > 0) options.add("--sub-margin=$margin")
        options.add(if (pref.getBoolean(KEY_NAG_SUBTITLE_BLACK_BARS, false)) "--spu-fill" else "--no-spu-fill")
        if (pref.getBoolean(KEY_NAG_ASS_OURS, false)) options.add("--codec=subsdec")
        if (pref.getBoolean(KEY_NAG_SECONDARY_SUB_TOP, true)) {
            options.add("--secondary-sub-alignment=$ALIGN_TOP")
            options.add("--secondary-sub-margin=$TOP_MARGIN")
        }
        // S-505: hardware-decoded pictures can only be deinterlaced on the GPU, by blending
        options.add("--deinterlace-filter=deinterlace,glblend")
        // S-506: the OpenGL output's tone mapping and scaling
        toneMappings[pref.getString(KEY_NAG_TONE_MAPPING, "auto")]?.takeIf { it != 0 }?.let { options.add("--gl-tone-mapping-function=$it") }
        scaler(pref).takeIf { it != 0 }?.let {
            options.add("--gl-upscaler=$it")
            options.add("--gl-downscaler=$it")
        }
        // S-505: the picture turned or mirrored, which the OpenGL output draws
        transform(pref)?.let {
            options.add("--video-filter=transform")
            options.add("--transform-type=$it")
        }
        return options
    }

    private fun scaler(pref: SharedPreferences): Int = scalers[pref.getString(KEY_NAG_SCALER, "auto")] ?: 0

    private fun transform(pref: SharedPreferences): String? = pref.getString(KEY_NAG_TRANSFORM, null)?.takeIf { it in transforms }

    /** VLC's SUBPICTURE_ALIGN_TOP. */
    private const val ALIGN_TOP = 4

    /** Top-aligned secondary subtitles pushed down a little from the edge (VLC's margin moves them up). */
    private const val TOP_MARGIN = -24
}

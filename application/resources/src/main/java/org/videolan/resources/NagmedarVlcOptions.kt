/*
 * Nağmedar: libVLC's options for what the player's native side cannot change while a film plays
 * (S-503 … S-506). Read as libVLC starts, like the subtitles' font; a change waits for libVLC to
 * start again (the player's LibVlcRestart).
 */
package org.videolan.resources

import android.content.SharedPreferences
import android.os.Build
import org.videolan.tools.KEY_NAG_ASS_OURS
import org.videolan.tools.KEY_NAG_SCALER
import org.videolan.tools.KEY_NAG_SECONDARY_SUB_TOP
import org.videolan.tools.KEY_NAG_SUBTITLE_BLACK_BARS
import org.videolan.tools.KEY_NAG_SUBTITLE_MARGIN
import org.videolan.tools.KEY_NAG_TONE_MAPPING
import org.videolan.tools.KEY_NAG_TRANSFORM
import org.videolan.tools.KEY_OPENGL

object NagmedarVlcOptions {

    /**
     * S-610: the advanced video effects of this session, as the player sets them: the video filters
     * before the transform (anaglyph, which reads a side-by-side picture as it comes) and after it
     * (rotate), the sub-sources (logo, marq) and their options. Kept in memory only: they last
     * as long as the app does, and libVLC reads them as it starts.
     */
    data class SessionVideo(
        val before: List<String> = emptyList(),
        val after: List<String> = emptyList(),
        val subSources: List<String> = emptyList(),
        val options: List<String> = emptyList(),
        /** A filter works on pictures in memory: hardware decoding hands its frames over rather than drawing them itself. */
        val cpu: Boolean = false,
    )

    @Volatile
    var sessionVideo = SessionVideo()

    /** What libVLC started with, of [sessionVideo]: the effects showing. */
    @Volatile
    var startedVideo = SessionVideo()
        private set

    /** The transform's type libVLC started with (S-505), null for none: the picture's turn showing. */
    @Volatile
    var startedTransform: String? = null
        private set

    /**
     * Whether libVLC started with Android's own video output alone ([VOUT_ANDROID]): it shows only
     * the pictures MediaCodec draws into the screen's surface itself.
     */
    @Volatile
    var startedAndroidOutput: Boolean = false
        private set

    /** libVLC was made with [session] and the settings in [pref] ([VLCInstance]): they show now. */
    fun markStarted(session: SessionVideo, pref: SharedPreferences) {
        startedVideo = session
        startedTransform = transform(pref)
        startedAndroidOutput = output(setting(pref), pref) == VOUT_ANDROID
    }

    /**
     * [options] without the session's effects (S-610): what the user wrote over the video and the
     * picture's path stay on the device, out of a feedback report.
     */
    fun withoutSession(options: List<String>): List<String> {
        val session = sessionVideo
        if (session.before.isEmpty() && session.after.isEmpty() && session.subSources.isEmpty()) return options
        val filters = (session.before + session.after).toSet()
        return options.mapNotNull { option ->
            when {
                option in session.options -> null
                option.startsWith("--sub-source=") -> null
                option.startsWith("--video-filter=") ->
                    option.removePrefix("--video-filter=").split(':').filter { it !in filters }.takeIf { it.isNotEmpty() }?.joinToString(":", prefix = "--video-filter=")
                else -> option
            }
        }
    }

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
    fun vout(opengl: Int, pref: SharedPreferences): String? = when (output(opengl, pref)) {
        VOUT_OPENGL -> "--vout=gles2,none"
        VOUT_ANDROID -> "--vout=android_display,none"
        else -> null
    }

    /** The output [vout] asks for: [opengl], or OpenGL when VLC's choice is left to it and something only OpenGL does is asked for. */
    private fun output(opengl: Int, pref: SharedPreferences): Int = if (opengl == VOUT_AUTO && needsOpenGl(pref)) VOUT_OPENGL else opengl

    /** VLC's "opengl" setting, as [VLCOptions] reads it as libVLC starts. */
    private fun setting(pref: SharedPreferences): Int = runCatching { pref.getString(KEY_OPENGL, null)?.toIntOrNull() }.getOrNull() ?: VOUT_AUTO

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
        // S-505: the picture turned or mirrored, which the OpenGL output draws; S-610: the session's
        // filters around it, and what is drawn over the picture
        val session = sessionVideo
        val transform = transform(pref)
        val filters = session.before + listOfNotNull(transform?.let { "transform" }) + session.after
        if (filters.isNotEmpty()) options.add("--video-filter=" + filters.joinToString(":"))
        transform?.let { options.add("--transform-type=$it") }
        if (session.subSources.isNotEmpty()) options.add("--sub-source=" + session.subSources.joinToString(":"))
        options.addAll(session.options)
        return options
    }

    /**
     * S-610: a film's options for the session's effects: a filter that works on pictures in memory
     * needs MediaCodec to hand its frames over (still decoded by the hardware) rather than draw them
     * into the screen's surface itself.
     *
     * So does a picture stored [turned] (a phone's portrait recording, whose track asks for a turn
     * of 90°, 180° or 270°). Drawing it itself, MediaCodec is asked to turn it, but on Android 12
     * and later this libVLC (4.0.0-eap29) takes its pictures through an AImageReader, which drops
     * the turn, and places them for the turned size: the picture shows sideways and cut to a
     * square (modules/codec/omxil/mediacodec.c, modules/video_output/android/display.c). Handed
     * over, it keeps its turn, and libVLC's OpenGL output turns it. Not with Android's own output
     * alone ([startedAndroidOutput]), which would show nothing.
     */
    fun mediaOptions(turned: Boolean = false): List<String> =
        if (startedVideo.cpu || (turned && handsTurnedOver)) listOf(":no-mediacodec-dr", ":no-omxil-dr") else emptyList()

    /** Whether a film whose picture is stored turned is handed over ([mediaOptions]): on Android 12 and later, without Android's own output alone. */
    val handsTurnedOver: Boolean
        get() = !startedAndroidOutput && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    private fun scaler(pref: SharedPreferences): Int = scalers[pref.getString(KEY_NAG_SCALER, "auto")] ?: 0

    private fun transform(pref: SharedPreferences): String? = pref.getString(KEY_NAG_TRANSFORM, null)?.takeIf { it in transforms }

    /** VLC's SUBPICTURE_ALIGN_TOP. */
    private const val ALIGN_TOP = 4

    /** Top-aligned secondary subtitles pushed down a little from the edge (VLC's margin moves them up). */
    private const val TOP_MARGIN = -24
}

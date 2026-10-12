package app.nagmedar.ui.player.effects

import android.content.Context
import android.net.Uri
import app.nagmedar.lab.MediaTech
import app.nagmedar.ui.library.TechRepository
import org.videolan.resources.SoftwareDecodingAdviser
import java.util.Collections

/**
 * Dolby Vision profile 5 (S-507): its base layer is not HDR10 but Dolby's own IPT-PQ-C2 colour
 * space, which the hardware decoder hands to the screen as it is, purple and green. Decoded in
 * software, libVLC's avcodec keeps the RPU metadata with each picture, the picture goes to the
 * OpenGL output (Android's own takes only the hardware decoder's pictures), and libplacebo
 * reshapes it to the colours meant. So such a film is decoded in software, as the probe tells
 * before it starts; profiles 7 and 8 keep an HDR10 base layer and stay with the hardware.
 */
object DolbyVisionDecoding : SoftwareDecodingAdviser {

    /** Dolby Vision's profile whose base layer is not HDR10. */
    const val PROFILE_5 = 5

    /** The films decided for, so that the Info tab says why they are decoded in software. */
    private val software: MutableSet<String> = Collections.synchronizedSet(HashSet())

    override suspend fun needsSoftwareDecoding(context: Context, media: Uri): Boolean {
        val key = media.toString()
        val tech = TechRepository.get(context.applicationContext).probeNow(key)
        val needed = tech?.let(::needsSoftware) ?: false
        if (needed) software.add(key) else software.remove(key)
        return needed
    }

    /** Whether the film at [uri] was sent to the software decoder for its Dolby Vision. */
    fun decidedFor(uri: String?): Boolean = uri != null && uri in software

    /** Whether a film's probe says the hardware decoder would colour it wrong. */
    fun needsSoftware(tech: MediaTech): Boolean = tech.video?.dvProfile == PROFILE_5
}

package app.nagmedar.ui.library

import app.nagmedar.lab.AudioTech
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.SubtitleTech

/** Probe results shaped like the mockups' files, for label tests (S-802). */
object TechSamples {

    fun audio(codec: String, profile: String = "", channels: Int = 6, layout: String = "5.1(side)", language: String = "eng",
              default: Boolean = false, commentary: Boolean = false) =
        AudioTech(codec, profile, channels, layout, 48000, 24, 0, language, "", default, false, commentary)

    fun subtitle(codec: String, language: String) = SubtitleTech(codec, language, "", false, false, false)

    fun tech(audio: List<AudioTech>, subtitles: List<SubtitleTech> = emptyList(),
             bitrate: Long = 56_000_000, size: Long = 58_400_000_000, format: String = "matroska,webm") =
        MediaTech(format, "", 8_280_000, size, bitrate, 12, audio, subtitles)

    /** Kuzey Işığı: TrueHD Atmos, DTS-HD MA, a commentary, PGS and SRT subtitles. */
    val kuzeyIsigi = tech(
        listOf(
            audio("truehd", "Dolby TrueHD + Dolby Atmos", 8, "7.1", "eng", default = true),
            audio("dts", "DTS-HD MA", 6, "5.1(side)", "tur"),
            audio("ac3", "", 2, "stereo", "", commentary = true),
        ),
        listOf(subtitle("hdmv_pgs_subtitle", "tur"), subtitle("hdmv_pgs_subtitle", "eng"), subtitle("subrip", "tur")),
    )

    val demirVadi = tech(listOf(audio("truehd", "", 8, "7.1", default = true)))
    val camSehir = tech(listOf(audio("eac3", "Dolby Digital Plus + Dolby Atmos", 6, default = true)), format = "mov,mp4,m4a,3gp,3g2,mj2")
}

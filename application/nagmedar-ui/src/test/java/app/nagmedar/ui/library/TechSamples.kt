package app.nagmedar.ui.library

import app.nagmedar.lab.AudioTech
import app.nagmedar.lab.MediaTech
import app.nagmedar.lab.SubtitleTech
import app.nagmedar.lab.VideoTech

/** Probe results shaped like the mockups' films, for label tests and screenshots (S-802). */
object TechSamples {

    fun video(
        width: Int = 3840,
        height: Int = 2160,
        codec: String = "hevc",
        profile: String = "Main 10",
        fps: Double = 23.976,
        transfer: String = "smpte2084",
        maxLuminance: Double = 1000.0,
        hdr10Plus: Boolean = false,
        dvProfile: Int = 0,
        dvCompatibility: Int = 0,
        dvEnhancement: String = "",
        bitDepth: Int = 10,
    ) = VideoTech(
        codec, profile, width, height, fps, bitDepth, transfer, "bt2020", "bt2020nc", maxLuminance, 0.0001, 1000, 400, hdr10Plus,
        dvProfile, if (dvProfile > 0) 6 else 0, dvCompatibility, dvEnhancement, dvEnhancement.isNotEmpty(), dvProfile > 0,
    )

    fun audio(codec: String, profile: String = "", channels: Int = 6, layout: String = "5.1(side)", language: String = "eng",
              default: Boolean = false, commentary: Boolean = false) =
        AudioTech(codec, profile, channels, layout, 48000, 24, 0, language, "", default, false, commentary)

    fun subtitle(codec: String, language: String) = SubtitleTech(codec, language, "", false, false, false)

    fun tech(video: VideoTech?, audio: List<AudioTech>, subtitles: List<SubtitleTech> = emptyList(),
             bitrate: Long = 56_000_000, size: Long = 58_400_000_000, format: String = "matroska,webm") =
        MediaTech(format, "", 8_280_000, size, bitrate, 12, video, audio, subtitles)

    /** Kuzey Işığı: profile 7 FEL, TrueHD Atmos, DTS-HD MA, a commentary, PGS and SRT subtitles. */
    val kuzeyIsigi = tech(
        video(dvProfile = 7, dvCompatibility = 6, dvEnhancement = "FEL"),
        listOf(
            audio("truehd", "Dolby TrueHD + Dolby Atmos", 8, "7.1", "eng", default = true),
            audio("dts", "DTS-HD MA", 6, "5.1(side)", "tur"),
            audio("ac3", "", 2, "stereo", "", commentary = true),
        ),
        listOf(subtitle("hdmv_pgs_subtitle", "tur"), subtitle("hdmv_pgs_subtitle", "eng"), subtitle("subrip", "tur")),
    )

    val sessizLiman = tech(video(), listOf(audio("truehd", "Dolby TrueHD + Dolby Atmos", 8, "7.1", default = true)))
    val demirVadi = tech(video(dvProfile = 8, dvCompatibility = 1), listOf(audio("truehd", "", 8, "7.1", default = true)))
    val kirikPusula = tech(video(hdr10Plus = true), listOf(audio("eac3", "", 6, default = true)), bitrate = 32_000_000, size = 31_200_000_000)
    val maviKanal = tech(
        video(1920, 1080, "h264", "High", 24.0, "bt709", 0.0, bitDepth = 8),
        listOf(audio("dts", "DTS", 6, default = true)),
        bitrate = 12_400_000,
        size = 9_800_000_000,
    )
    val camSehir = tech(video(dvProfile = 5, dvCompatibility = 0), listOf(audio("eac3", "Dolby Digital Plus + Dolby Atmos", 6, default = true)),
        format = "mov,mp4,m4a,3gp,3g2,mj2")
}

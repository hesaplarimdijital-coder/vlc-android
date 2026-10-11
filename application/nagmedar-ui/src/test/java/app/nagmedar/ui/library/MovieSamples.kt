package app.nagmedar.ui.library

import app.nagmedar.lab.MediaTech

/** The mockup's films as library items with their probes (S-802); one is still being probed. */
object MovieSamples {
    private fun item(title: String, minutes: Int, progress: Float = 0f, width: Int = 3840, height: Int = 2160) =
        VideoItem("file:///storage/1A2B-3C4D/Filmler/$title.mkv", title, minutes * 60_000L, progress, width, height)

    val kuzeyIsigi = item("Kuzey Işığı", 138, progress = 0.52f)
    val sessizLiman = item("Sessiz Liman", 112)
    val demirVadi = item("Demir Vadi", 124)
    val maviKanal = item("Mavi Kanal", 98, width = 1920, height = 1080)
    val kirikPusula = item("Kırık Pusula", 140, progress = 0.18f)
    val camSehir = item("Cam Şehir", 119)
    /** Not probed yet: the card shows the medialibrary's size and the duration. */
    val yedinciKat = item("Yedinci Kat", 104, width = 1920, height = 1080)

    val items = listOf(kuzeyIsigi, sessizLiman, demirVadi, maviKanal, kirikPusula, camSehir, yedinciKat)

    val tech: Map<String, MediaTech> = mapOf(
        kuzeyIsigi.key to TechSamples.kuzeyIsigi,
        sessizLiman.key to TechSamples.sessizLiman,
        demirVadi.key to TechSamples.demirVadi,
        maviKanal.key to TechSamples.maviKanal,
        kirikPusula.key to TechSamples.kirikPusula,
        camSehir.key to TechSamples.camSehir,
    )

    fun state(item: VideoItem) = if (tech.containsKey(item.key)) TechState.Ready else TechState.Probing
}

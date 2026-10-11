package app.nagmedar.ui.library

import app.nagmedar.lab.MediaTech

/** The mockup's films and a few series as library items with their probes (S-801, S-802). */
object MovieSamples {
    private fun item(folder: String, title: String, minutes: Int, progress: Float = 0f, width: Int = 3840, height: Int = 2160, added: Long = 0,
                     plays: Long = 0, sidecars: Set<String> = emptySet()): VideoItem {
        val path = "$folder/$title.mkv"
        return VideoItem("file://$path", title, minutes * 60_000L, progress, width, height, null, path, added, plays, sidecars)
    }

    private const val T7 = "/storage/1A2B-3C4D/Filmler"
    private const val DEVICE = "/storage/emulated/0/Movies"

    /** Watched once and being watched again. */
    val kuzeyIsigi = item("$T7/Bilim Kurgu", "Kuzey Işığı", 138, progress = 0.52f, added = 7, plays = 1)
    val sessizLiman = item("$T7/Dram", "Sessiz Liman", 112, added = 6, sidecars = setOf("tur"))
    val demirVadi = item("$T7/Bilim Kurgu", "Demir Vadi", 124, added = 5)
    val maviKanal = item(DEVICE, "Mavi Kanal", 98, width = 1920, height = 1080, added = 4, plays = 1)
    val kirikPusula = item("$T7/Dram", "Kırık Pusula", 140, progress = 0.18f, added = 3)
    val camSehir = item("$T7/Bilim Kurgu", "Cam Şehir", 119, added = 2)
    /** Not probed yet: the card shows the medialibrary's size and the duration. */
    val yedinciKat = item(DEVICE, "Yedinci Kat", 104, width = 1920, height = 1080, added = 1)

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

    private fun episode(show: String, season: Int, episode: Int, plays: Long = 0, progress: Float = 0f, title: String = ""): VideoItem {
        val name = "S%02dE%02d".format(season, episode) + if (title.isNotEmpty()) " $title" else ""
        val path = "/storage/1A2B-3C4D/Diziler/$show/Sezon $season/$name.mkv"
        return VideoItem("file://$path", name, 52 * 60_000L, progress, 3840, 2160, null, path, 0, plays, emptySet())
    }

    /** Kuzey Rüzgârı: two seasons, the first watched, the second in progress; Liman: a new show. */
    val episodes: List<VideoItem> = buildList {
        for (e in 1..6) add(episode("Kuzey Rüzgârı", 1, e, plays = 1, title = if (e == 1) "Başlangıç" else ""))
        add(episode("Kuzey Rüzgârı", 2, 1, plays = 1, title = "Dönüş"))
        add(episode("Kuzey Rüzgârı", 2, 2, progress = 0.35f, title = "Fener"))
        for (e in 3..5) add(episode("Kuzey Rüzgârı", 2, e))
        for (e in 1..3) add(episode("Liman", 1, e))
    }

    val library: VideoLibrary get() = buildVideoLibrary(items + episodes)
}

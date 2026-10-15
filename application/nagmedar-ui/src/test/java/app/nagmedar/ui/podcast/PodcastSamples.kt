package app.nagmedar.ui.podcast

import app.nagmedar.data.PodcastEpisode
import app.nagmedar.data.PodcastFeed
import app.nagmedar.data.PodcastFeedSummary

/** S-1101: shows and episodes for screenshots and screen tests; no network, no database. */
internal object PodcastSamples {
    /** 2026-10-12 12:00 UTC. */
    const val NOW = 1_791_806_400_000L
    private const val DAY = 86_400_000L

    private fun feed(id: Long, title: String, author: String?, url: String, description: String? = null, link: String? = null, funding: String? = null) =
        PodcastFeed(id = id, feedUrl = url, title = title, author = author, description = description, link = link, fundingUrl = funding, subscribedAt = NOW - 30 * DAY, refreshedAt = NOW)

    val gece = feed(
        1, "Gece Yayını", "Gece Ekibi", "https://feeds.example.org/gece.xml",
        description = "Her hafta gece yarısı: müzik, edebiyat ve sohbet. Konuklarımızla Itrî\'den Cinuçen\'e, Neva Kâr\'dan Segâh\'a Türk müziğinin köşelerinde dolaşıyoruz.",
        link = "https://gece.example.org", funding = "https://gece.example.org/destek",
    )
    val bilim = feed(2, "Bilim ve Teknik Sohbetleri", "Ayşe Demir", "https://feeds.example.org/bilim.xml", "Haftanın bilim haberleri.")
    val yolculuk = feed(3, "Yolculuk", "Yolculuk Ekibi", "https://yolculuk.example.org/feed")
    val tarih = feed(4, "Tarih Sohbetleri", null, "https://tarih.example.org/rss.xml")
    val kitap = feed(5, "Kitap Kulübü", "Mert Aksoy", "https://kitap.example.org/rss")
    val makam = feed(6, "Makam Dersleri", "Itrî Vakfı", "https://makam.example.org/feed.xml")

    val summaries = listOf(
        PodcastFeedSummary(bilim, 148, 0),
        PodcastFeedSummary(gece, 42, 3),
        PodcastFeedSummary(kitap, 31, 0),
        PodcastFeedSummary(makam, 12, 1),
        PodcastFeedSummary(tarih, 96, 0),
        PodcastFeedSummary(yolculuk, 7, 0),
    ).sortedBy { it.feed.title }

    private fun episode(id: Long, title: String, daysAgo: Int, minutes: Int, played: Boolean = false, fresh: Boolean = false) = PodcastEpisode(
        id = id, feedId = 1, guid = "gece-$id", title = title, audioUrl = "https://cdn.example.org/gece/$id.mp3", mimeType = "audio/mpeg",
        durationMs = minutes * 60_000L + 3_000, publishedAt = NOW - daysAgo * DAY, played = played,
        addedAt = if (fresh) NOW - daysAgo * DAY else gece.subscribedAt,
    )

    val episodes = listOf(
        episode(10, "Üçüncü gece: Neva Kâr", 1, 62, fresh = true),
        episode(9, "İkinci gece: Itrî ve mirası", 3, 74, fresh = true),
        episode(8, "Gece yarısı sohbeti: bir bestenin doğuşu", 5, 48, fresh = true),
        episode(7, "Birinci gece", 12, 55, played = true),
        episode(6, "Tanıtım", 30, 4, played = true),
    )

    val page = PodcastPage(PodcastFeedSummary(gece, 42, 3), episodes)

    val hits = listOf(
        PodcastHit("https://feeds.example.org/gece.xml", "Gece Yayını", "Gece Ekibi", episodeCount = 42, source = HitSource.PodcastIndex),
        PodcastHit("https://gece2.example.org/rss", "Gece Kuşları", "Deniz Ak", episodeCount = 118),
        PodcastHit("https://geceyarisi.example.org/feed", "Gece Yarısı Masalları", "Masal Atölyesi", episodeCount = 9),
        PodcastHit("https://bozuk.example.org/sayfa", "Gece Müziği", "Bir Yazar", episodeCount = null),
        PodcastHit("https://nobet.example.org/rss", "Gece Nöbeti", null, episodeCount = 230, source = HitSource.ITunes),
    )

    fun model(
        subscriptions: List<PodcastFeedSummary>? = summaries,
        query: String = "",
        search: SearchUi = SearchUi.Idle,
        subscribing: Map<String, SubscribeUi> = emptyMap(),
        page: PodcastPage? = null,
        add: AddUi? = null,
        importing: ImportUi? = null,
    ) = PodcastScreenModel(subscriptions, query, search, subscribing, page, add, importing)
}

package app.nagmedar.ui.player.subtitles.providers

import app.nagmedar.ui.player.subtitles.OnlineProblem
import app.nagmedar.ui.player.subtitles.OnlineQuery
import app.nagmedar.ui.player.subtitles.OnlineQuota
import app.nagmedar.ui.player.subtitles.OnlineSubtitle
import app.nagmedar.ui.player.subtitles.OnlineSubtitlesController
import app.nagmedar.ui.player.subtitles.OpenSubtitlesApi

/**
 * OpenSubtitles.com as the first provider (S-707): the API of S-706 with Nağmedar's own key, its
 * answers in the common model. A search goes by the file's fingerprint when there is one and by
 * the name when that finds nothing (or there is none), as it always did.
 */
class OpenSubtitlesProvider(private val api: OpenSubtitlesApi, override val displayName: String = NAME) : SubtitleProvider {
    override val id: String = ID

    override val capabilities = ProviderCapabilities(
        searchByHash = true,
        searchByImdb = true,
        searchByName = true,
        searchBySeasonEpisode = true,
        searchByLanguage = true,
        download = true,
        needsLogin = false,
        needsKey = true,
        rateLimit = RateLimit(downloadsPerDay = OnlineSubtitlesController.ANONYMOUS_DOWNLOADS),
    )

    override fun unavailable(): OnlineProblem? = if (api.available) null else OnlineProblem.NoKey

    override suspend fun search(query: SubtitleQuery): List<SubtitleResult> {
        var found = query.hash?.let { api.search(OnlineQuery(it, null, query.languages)) }.orEmpty()
        if (found.isEmpty() && (!query.name.isNullOrBlank() || query.imdbId != null)) {
            found = api.search(OnlineQuery(null, query.name, query.languages, query.imdbId, query.season, query.episode))
        }
        return found.map { resultOf(it, displayName) }
    }

    override suspend fun download(result: SubtitleResult): SubtitleFile {
        val ticket = api.download(result.id.toLong())
        val bytes = api.fetch(ticket.link)
        val quota = OnlineQuota(ticket.remaining, ticket.remaining + ticket.requests, OnlineSubtitlesController.resetAt(ticket.resetUtc))
        return SubtitleFile(bytes, ticket.fileName, quota)
    }

    companion object {
        const val ID = "opensubtitles"
        const val NAME = "OpenSubtitles"

        /** An answer of the API in the common model. */
        fun resultOf(found: OnlineSubtitle, providerName: String = NAME) = SubtitleResult(
            provider = ID,
            id = found.fileId.toString(),
            providerName = providerName,
            fileName = found.fileName,
            release = found.release,
            language = found.language,
            downloads = found.downloads,
            rating = found.rating,
            hashMatch = found.hashMatch,
            hearingImpaired = found.hearingImpaired,
            fps = found.fps,
            title = found.title,
            year = found.year,
            machineTranslated = found.machineTranslated,
        )
    }
}

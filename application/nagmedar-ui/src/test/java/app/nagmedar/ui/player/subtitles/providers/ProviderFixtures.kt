package app.nagmedar.ui.player.subtitles.providers

import app.nagmedar.ui.player.subtitles.OnlineProblem
import app.nagmedar.ui.player.subtitles.OnlineQuota
import kotlinx.coroutines.delay

/** A provider the tests run: what it finds, how long it takes, how it fails. */
internal class FakeProvider(
    override val id: String,
    override val displayName: String = id,
    var results: List<SubtitleResult> = emptyList(),
    var delayMs: Long = 0,
    var failure: Exception? = null,
    var problem: OnlineProblem? = null,
    override val capabilities: ProviderCapabilities = ProviderCapabilities(searchByName = true, searchByLanguage = true),
    /** Called in search: lets a test see providers run at the same time. */
    var onSearch: suspend () -> Unit = {},
) : SubtitleProvider {
    val queries = mutableListOf<SubtitleQuery>()
    val downloads = mutableListOf<SubtitleResult>()

    override fun unavailable(): OnlineProblem? = problem

    override suspend fun search(query: SubtitleQuery): List<SubtitleResult> {
        queries += query
        onSearch()
        if (delayMs > 0) delay(delayMs)
        failure?.let { throw it }
        return results
    }

    override suspend fun download(result: SubtitleResult, onQuota: (OnlineQuota) -> Unit): SubtitleFile {
        downloads += result
        failure?.let { throw it }
        return SubtitleFile("1\n00:00:01,000 --> 00:00:02,000\nmerhaba\n".toByteArray(), result.fileName)
    }
}

internal fun result(
    provider: String,
    id: String,
    release: String = "Northern.Light.2024.1080p.WEB-DL",
    language: String = "en",
    downloads: Long = 0,
    rating: Double? = null,
    hashMatch: Boolean = false,
    providerName: String = provider,
) = SubtitleResult(
    provider = provider,
    id = id,
    providerName = providerName,
    fileName = "$release.$language.srt",
    release = release,
    language = language,
    downloads = downloads,
    rating = rating,
    hashMatch = hashMatch,
)

internal class MemoryProviderStore(var kept: ProviderSettings = ProviderSettings()) : ProviderSettingsStore {
    override fun load(): ProviderSettings = kept

    override fun save(settings: ProviderSettings) {
        kept = settings
    }
}

package app.nagmedar.ui.player.subtitles.providers

import androidx.compose.runtime.Immutable
import app.nagmedar.ui.player.subtitles.OnlineProblem
import app.nagmedar.ui.player.subtitles.OnlineQuota

/*
 * Where subtitles come from (S-707). A provider is a Kotlin class of the app's own, in the list
 * the registry holds; no code is loaded from outside. The search asks every provider that is
 * turned on, at the same time, in the same words ([SubtitleQuery]), and gets the same kind of
 * answer ([SubtitleResult]) from each, which it merges into one list.
 */

/** How many a provider lets one have: downloads in a day, requests in a second. */
@Immutable
data class RateLimit(val downloadsPerDay: Int? = null, val requestsPerSecond: Int? = null)

/** What a provider can be asked, and what it asks in return. */
@Immutable
data class ProviderCapabilities(
    val searchByHash: Boolean = false,
    val searchByImdb: Boolean = false,
    val searchByName: Boolean = false,
    val searchBySeasonEpisode: Boolean = false,
    val searchByLanguage: Boolean = false,
    /** Whether a result can be downloaded (a provider may only point at pages). */
    val download: Boolean = true,
    /** An account of the user's own. */
    val needsLogin: Boolean = false,
    /** A key of the app's, which a build may be made without. */
    val needsKey: Boolean = false,
    val rateLimit: RateLimit? = null,
)

/**
 * What is asked: the file's fingerprint, the film's IMDb number, its name, the season and the
 * episode of a series, the languages wanted in the order wanted. A provider uses what it can.
 */
@Immutable
data class SubtitleQuery(
    val hash: String? = null,
    val imdbId: String? = null,
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val languages: List<String> = emptyList(),
)

/** A subtitle file a provider has, the same for every provider. */
@Immutable
data class SubtitleResult(
    /** The provider's id, and the result's id for it. */
    val provider: String,
    val id: String,
    /** The provider as the user reads it: "OpenSubtitles", "Local folder". */
    val providerName: String,
    val fileName: String,
    /** The release it was timed for: "Northern.Light.2024.1080p.WEB-DL". */
    val release: String,
    /** "tr", "en", "pt-BR"; empty when not known. */
    val language: String,
    val downloads: Long = 0,
    /** 0 to 1 where the provider can say. */
    val rating: Double? = null,
    /** Timed for this very file (found by its fingerprint). */
    val hashMatch: Boolean = false,
    val hearingImpaired: Boolean = false,
    val fps: Double? = null,
    val title: String = "",
    val year: Int? = null,
    val machineTranslated: Boolean = false,
    /** The other providers that have the same file, when the merge found them. */
    val alsoFrom: List<String> = emptyList(),
) {
    /** Unique among all providers' results. */
    val key: String get() = "$provider/$id"

    /** "OpenSubtitles", or "OpenSubtitles + Local folder". */
    val sources: String get() = (listOf(providerName) + alsoFrom).joinToString(" + ")
}

/** A downloaded file: its bytes, its name, and the quota the provider reported. */
class SubtitleFile(val bytes: ByteArray, val fileName: String, val quota: OnlineQuota? = null)

interface SubtitleProvider {
    /** Stable, lower case: kept in the settings. */
    val id: String

    /** As the user reads it. */
    val displayName: String

    val capabilities: ProviderCapabilities

    /** Why the provider cannot be asked now (no key, no folder), or null when it can. */
    fun unavailable(): OnlineProblem?

    /** Failures are thrown (see [app.nagmedar.ui.player.subtitles.OnlineFailure]); the search reports them per provider. */
    suspend fun search(query: SubtitleQuery): List<SubtitleResult>

    suspend fun download(result: SubtitleResult): SubtitleFile
}

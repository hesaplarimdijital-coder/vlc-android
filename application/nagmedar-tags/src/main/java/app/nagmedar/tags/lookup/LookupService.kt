/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.3.0: from Metamatik (core/domain/src/main/kotlin/com/bobbyesp/metadator/core/domain/lookup/LookupService.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags.lookup

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Asks every enabled provider at once and merges their answers into one ranked list. One provider
 * failing does not hide the others' results; the outcome is only an error when all of them fail.
 */
class LookupService(
    private val providers: List<MetadataProvider>,
    /** Nağmedar 1.2.0: the providers turned on, by id (all of them unless told otherwise). */
    private val enabled: suspend () -> Set<String> = { providers.mapTo(mutableSetOf()) { it.id } },
) {
    val availableProviders: List<MetadataProvider>
        get() = providers

    /**
     * The covers the enabled providers can name from ids the file already carries, without a
     * search, each with the provider that named it.
     */
    suspend fun artworkFor(
        identifiers: Map<String, String>
    ): List<Pair<MetadataProvider, ProvidedArtwork>> {
        val enabled = enabled()
        return providers
            .filter { it.id in enabled }
            .flatMap { provider -> provider.artworkFor(identifiers).map { provider to it } }
    }

    suspend fun search(query: LookupQuery): LookupOutcome {
        val enabled = enabled()
        val active = providers.filter { it.id in enabled }
        if (active.isEmpty()) return LookupOutcome.NoProviders
        if (query.isBlank) return LookupOutcome.Results(emptyList(), failedProviders = emptyList())

        val results = coroutineScope {
            active.map { provider -> async { provider to provider.search(query) } }.awaitAll()
        }
        val candidates =
            results
                .flatMap { (_, result) -> (result as? LookupResult.Success)?.candidates.orEmpty() }
                // Once each: a catalogue can name a song twice (a recording twice on one release),
                // and a list cannot show one item twice.
                .distinctBy { it.providerId to it.id }
        val failed = results.filter { (_, result) -> result !is LookupResult.Success }

        return when {
            failed.size == results.size && results.all { it.second == LookupResult.Offline } ->
                LookupOutcome.Offline
            failed.size == results.size && results.any { it.second == LookupResult.RateLimited } ->
                LookupOutcome.RateLimited
            failed.size == results.size -> LookupOutcome.Failed
            else ->
                LookupOutcome.Results(
                    candidates = rank(query, candidates),
                    failedProviders = failed.map { it.first.displayName },
                )
        }
    }
}

sealed interface LookupOutcome {
    data class Results(val candidates: List<LookupCandidate>, val failedProviders: List<String>) :
        LookupOutcome

    data object NoProviders : LookupOutcome

    data object Offline : LookupOutcome

    data object RateLimited : LookupOutcome

    data object Failed : LookupOutcome
}

/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.4.0: from Metamatik (core/domain/src/test/kotlin/com/bobbyesp/metadator/core/domain/artwork/LibraryCoverScannerTest.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags.artwork

import app.nagmedar.tags.ContentRef
import app.nagmedar.tags.LibraryFile
import app.nagmedar.tags.AudioProperties
import app.nagmedar.tags.EmbeddedPicture
import app.nagmedar.tags.TagMap
import app.nagmedar.tags.TagReadResult
import app.nagmedar.tags.TagReader
import app.nagmedar.tags.TagSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryCoverScannerTest {
    private val withCover = track(1)
    private val without = track(2)
    private val unsupported = track(3)

    /** Files by whether they hold a picture: null for a format TagLib reads no tags of. */
    private class CoverFiles(private val files: Map<ContentRef, Boolean?>) : TagReader {
        val reads = mutableListOf<ContentRef>()

        override suspend fun read(ref: ContentRef, includePictures: Boolean): TagReadResult {
            reads += ref
            if (ref !in files) return TagReadResult.NotFound
            val hasCover = files[ref] ?: return TagReadResult.Unsupported
            val pictures =
                if (hasCover) listOf(EmbeddedPicture(byteArrayOf(1, 2, 3), "image/jpeg"))
                else emptyList()
            return TagReadResult.Success(TagSnapshot(TagMap.Empty, pictures))
        }

        override suspend fun readAudioProperties(ref: ContentRef): AudioProperties? = null
    }

    private val files =
        CoverFiles(mapOf(withCover.ref to true, without.ref to false, unsupported.ref to null))

    /** A scanner working in the test's background scope, as it works in the app's. */
    private fun TestScope.scanner(
        store: CoverCheckStore,
        reader: TagReader = files,
        busy: Flow<Boolean> = flowOf(false),
    ): LibraryCoverScanner {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return LibraryCoverScanner(
            reader = reader,
            store = store,
            scope = backgroundScope,
            busy = busy,
            io = dispatcher,
            default = dispatcher,
        )
    }

    @Test
    fun `every song is checked, and those without a picture are listed`() = runTest {
        val store = MemoryCoverCheckStore()
        val scanner = scanner(store)

        scanner.submit(listOf(withCover, without, unsupported))
        runCurrent()

        val state = scanner.state.value
        assertEquals(setOf(without.ref.uri), state.withoutCover)
        assertTrue(state.lacksCover(without.ref.uri))
        assertFalse(state.lacksCover(unsupported.ref.uri))
        assertEquals(3, state.checked)
        assertEquals(3, state.total)
        assertFalse(state.isRunning)
        assertEquals(
            mapOf(withCover.ref.uri to true, without.ref.uri to false, unsupported.ref.uri to null),
            store.checks.mapValues { it.value.hasCover },
        )
    }

    @Test
    fun `a song checked before is not read again`() = runTest {
        val kept =
            CoverCheck(without.ref.uri, without.dateModifiedEpochSeconds, without.sizeBytes, false)
        val reader = CoverFiles(emptyMap())
        val scanner = scanner(MemoryCoverCheckStore(listOf(kept)), reader)

        scanner.submit(listOf(without))
        runCurrent()

        assertEquals(setOf(without.ref.uri), scanner.state.value.withoutCover)
        assertTrue(reader.reads.isEmpty())
    }

    @Test
    fun `a song saved with a cover is read again, before songs never read`() = runTest {
        val before = CoverCheck(withCover.ref.uri, withCover.dateModifiedEpochSeconds - 60, 900, false)
        val scanner = scanner(MemoryCoverCheckStore(listOf(before)))

        scanner.submit(listOf(without, withCover))
        runCurrent()

        assertEquals(listOf(withCover.ref, without.ref), files.reads)
        assertEquals(setOf(without.ref.uri), scanner.state.value.withoutCover)
    }

    @Test
    fun `a file that could not be opened is tried again next time, an unsupported one is not`() =
        runTest {
            val store = MemoryCoverCheckStore()
            val gone = track(4)
            val scanner = scanner(store)

            scanner.submit(listOf(unsupported, gone))
            runCurrent()

            assertEquals(2, scanner.state.value.checked)
            assertEquals(setOf(unsupported.ref.uri), store.checks.keys)
        }

    @Test
    fun `a song gone from the library is forgotten`() = runTest {
        val store = MemoryCoverCheckStore()
        val scanner = scanner(store)

        scanner.submit(listOf(withCover, without))
        runCurrent()
        scanner.submit(listOf(withCover))
        runCurrent()

        assertEquals(setOf(withCover.ref.uri), store.checks.keys)
        assertEquals(emptySet<String>(), scanner.state.value.withoutCover)
        assertEquals(1, scanner.state.value.total)
    }

    @Test
    fun `nothing is read while more urgent work is`() = runTest {
        val busy = MutableStateFlow(true)
        val scanner = scanner(MemoryCoverCheckStore(), busy = busy)

        scanner.submit(listOf(withCover, without))
        runCurrent()
        assertTrue(files.reads.isEmpty())
        assertTrue(scanner.state.value.isRunning)

        busy.value = false
        runCurrent()
        assertEquals(2, files.reads.size)
        assertEquals(setOf(without.ref.uri), scanner.state.value.withoutCover)
    }

    @Test
    fun `a large library is checked and kept whole`() = runTest {
        val songs = (1L..60L).map(::track)
        val store = MemoryCoverCheckStore()
        val reader = CoverFiles(songs.associate { it.ref to (it.ref.uri.substringAfterLast('/').toLong() % 2 == 0L) })
        val scanner = scanner(store, reader)

        scanner.submit(songs)
        runCurrent()

        assertEquals(60, store.checks.size)
        assertEquals(30, scanner.state.value.withoutCover.size)
        assertFalse(scanner.state.value.isRunning)
    }

    @Test
    fun `the same read says which essential tags a file lacks and whether its text looks garbled`() = runTest {
        val whole = track(5)
        val lacking = track(6)
        val reader = object : TagReader {
            override suspend fun read(ref: ContentRef, includePictures: Boolean): TagReadResult = TagReadResult.Success(
                TagSnapshot(
                    if (ref == whole.ref) {
                        TagMap.of(
                            "TITLE" to listOf("Garip"), "ARTIST" to listOf("Moğollar"), "ALBUM" to listOf("Moğollar"),
                            "ALBUMARTIST" to listOf("Moğollar"), "DATE" to listOf("1971"), "TRACKNUMBER" to listOf("2"),
                        )
                    } else {
                        TagMap.of("TITLE" to listOf("Garip"), "ARTIST" to listOf("MoÄŸollar"), "ALBUM" to listOf("Moğollar"), "TRACKNUMBER" to listOf("2"), "DATE" to listOf(" "))
                    },
                    emptyList(),
                ),
            )

            override suspend fun readAudioProperties(ref: ContentRef): AudioProperties? = null
        }
        val store = MemoryCoverCheckStore()
        val scanner = scanner(store, reader)

        scanner.submit(listOf(whole, lacking))
        runCurrent()

        val state = scanner.state.value
        assertEquals(setOf(lacking.ref.uri), state.needsAttention)
        assertEquals(setOf(lacking.ref.uri), state.garbled)
        // a date of blanks is none
        assertEquals(setOf("ALBUMARTIST", "DATE"), store.checks.getValue(lacking.ref.uri).missing)
        assertEquals(emptySet<String>(), store.checks.getValue(whole.ref.uri).missing)
        // both lack a picture
        assertEquals(setOf(whole.ref.uri, lacking.ref.uri), state.withoutCover)
    }

    private fun track(id: Long) =
        LibraryFile(
            ref = ContentRef("content://media/external/audio/media/$id"),
            displayName = "$id.mp3",
            mimeType = "audio/mpeg",
            sizeBytes = 1_000 + id,
            dateModifiedEpochSeconds = 1_700_000_000 + id,
        )
}

/** Cover checks in memory. */
class MemoryCoverCheckStore(initial: List<CoverCheck> = emptyList()) : CoverCheckStore {
    val checks = initial.associateByTo(mutableMapOf()) { it.uri }

    override suspend fun load(): List<CoverCheck> = checks.values.toList()

    override suspend fun save(checks: List<CoverCheck>) {
        checks.forEach { this.checks[it.uri] = it }
    }

    override suspend fun remove(uris: Collection<String>) {
        uris.forEach(checks::remove)
    }
}

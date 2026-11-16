/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.2.0: from Metamatik (core/domain/src/testFixtures/kotlin/com/bobbyesp/metadator/core/domain/Fakes.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags

import app.nagmedar.tags.files.MediaIndexer
import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Files in memory, read and written like TagLib does: a write replaces the whole property map, and
 * a key the format does not support is silently dropped.
 */
class FakeTagFiles(
    initial: Map<ContentRef, TagSnapshot> = emptyMap(),
    private val unsupportedKeys: Set<String> = emptySet(),
) : TagReader, TagWriter {
    val files = initial.toMutableMap()
    var writeResult: TagWriteResult? = null
    var failNextWrites = 0
    val writes = mutableListOf<ContentRef>()
    val reads = mutableListOf<ContentRef>()

    override suspend fun read(ref: ContentRef, includePictures: Boolean): TagReadResult {
        reads += ref
        val snapshot = files[ref] ?: return TagReadResult.NotFound
        return TagReadResult.Success(
            if (includePictures) snapshot else snapshot.copy(pictures = emptyList())
        )
    }

    override suspend fun readAudioProperties(ref: ContentRef) = AudioProperties(1000, 320, 44100, 2)

    override suspend fun write(
        ref: ContentRef,
        tags: TagMap,
        pictures: List<EmbeddedPicture>?,
    ): TagWriteResult {
        writeResult?.let {
            return it
        }
        if (failNextWrites > 0) {
            failNextWrites--
            return TagWriteResult.Failed("disk on fire")
        }
        val current = files[ref] ?: return TagWriteResult.NotFound
        writes += ref
        val stored = TagMap.of(tags.toMap().filterKeys { it !in unsupportedKeys })
        files[ref] = TagSnapshot(stored, pictures ?: current.pictures)
        return TagWriteResult.Written
    }
}

/**
 * Backups in memory. Saving and pruning throw in a cancelled coroutine, as Room's do: they do their
 * work in `withContext`.
 */
class FakeBackupStore : TagBackupStore {
    val backups = mutableMapOf<BackupId, TagBackup>()
    /** What each [prune] was asked to keep, in order. */
    val prunes = mutableListOf<Int>()
    /** Which [save], counted from 1, fails as Room's does on a full disk. */
    var failingSave: Int? = null
    /** What [prune] throws, if anything. */
    var pruneFailure: Throwable? = null
    private var saves = 0
    private var nextId = 1L

    override suspend fun save(
        ref: ContentRef,
        snapshot: TagSnapshot,
        createdAtMillis: Long,
    ): BackupId {
        currentCoroutineContext().ensureActive()
        if (++saves == failingSave) throw IOException("No space left on device")
        val id = BackupId(nextId++)
        backups[id] = TagBackup(id, ref, snapshot, createdAtMillis)
        return id
    }

    override suspend fun load(id: BackupId) = backups[id]

    override suspend fun history(ref: ContentRef) = backups.values.filter { it.ref == ref }

    override suspend fun delete(id: BackupId) {
        backups.remove(id)
    }

    override suspend fun clear() = backups.clear()

    override suspend fun prune(keep: Int) {
        currentCoroutineContext().ensureActive()
        pruneFailure?.let { throw it }
        prunes += keep
        backups.keys.sortedByDescending { it.value }.drop(keep).forEach(backups::remove)
    }
}

class FakeIndexer : MediaIndexer {
    val rescanned = mutableListOf<ContentRef>()

    override suspend fun rescan(refs: List<ContentRef>) {
        rescanned += refs
    }
}

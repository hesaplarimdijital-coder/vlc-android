/*
 * Copyright (C) 2026  Gabriel Fontán (BobbyESP)
 * Nağmedar 1.2.0: from Metamatik (core/domain/src/main/kotlin/com/bobbyesp/metadator/core/domain/save/RestoreBackupUseCase.kt), a fork of Metadator, under the AGPL-3.0.
 */
package app.nagmedar.tags.save

import app.nagmedar.tags.files.MediaIndexer
import app.nagmedar.tags.BackupId
import app.nagmedar.tags.TagBackupStore
import app.nagmedar.tags.TagWriteResult
import app.nagmedar.tags.TagWriter

/** Puts a file's tags and pictures back as they were before a save: what Undo does. */
class RestoreBackupUseCase(
    private val backups: TagBackupStore,
    private val writer: TagWriter,
    private val indexer: MediaIndexer,
) {
    suspend operator fun invoke(id: BackupId): RestoreOutcome {
        val backup = backups.load(id) ?: return RestoreOutcome.BackupGone
        return when (
            val result = writer.write(backup.ref, backup.snapshot.tags, backup.snapshot.pictures)
        ) {
            TagWriteResult.Written -> {
                backups.delete(id)
                indexer.rescan(listOf(backup.ref))
                RestoreOutcome.Restored
            }
            TagWriteResult.NeedsAccess -> RestoreOutcome.NeedsAccess
            TagWriteResult.NotFound -> RestoreOutcome.FileGone
            is TagWriteResult.Failed -> RestoreOutcome.Failed(result.message)
        }
    }
}

sealed interface RestoreOutcome {
    data object Restored : RestoreOutcome

    data object BackupGone : RestoreOutcome

    data object NeedsAccess : RestoreOutcome

    data object FileGone : RestoreOutcome

    data class Failed(val message: String) : RestoreOutcome
}

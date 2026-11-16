package app.nagmedar.tags.files

import app.nagmedar.tags.artwork.CoverCheck
import app.nagmedar.tags.artwork.CoverCheckStore
import app.nagmedar.tags.quality.LosslessVerdict
import app.nagmedar.tags.quality.QualityStore
import app.nagmedar.tags.quality.StoredVerdict
import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** Nağmedar 1.4.0: the lossless check's verdicts, a file's a line ([LineStore]): its size, its date, the verdict. */
class FileQualityStore(file: File, io: CoroutineDispatcher = Dispatchers.IO) : QualityStore {
    private val lines = LineStore(file, io)

    override suspend fun load(): List<StoredVerdict> = lines.load().mapNotNull { (uri, fields) ->
        if (fields.size != 3) return@mapNotNull null
        val size = fields[0].toLongOrNull() ?: return@mapNotNull null
        val modified = fields[1].toLongOrNull() ?: return@mapNotNull null
        val verdict = when (val name = fields[2]) {
            UNREADABLE -> null
            else -> LosslessVerdict.entries.firstOrNull { it.name == name } ?: return@mapNotNull null
        }
        StoredVerdict(uri, size, modified, verdict)
    }

    override suspend fun save(verdict: StoredVerdict) {
        lines.save(listOf(verdict.uri to listOf(verdict.sizeBytes.toString(), verdict.modifiedEpochSeconds.toString(), verdict.verdict?.name ?: UNREADABLE)))
    }

    private companion object {
        /** A file none of whose audio could be decoded. */
        const val UNREADABLE = "?"
    }
}

/**
 * Nağmedar 1.4.0: what was read of the library's files, a file's a line ([LineStore]): its date, its size, whether it
 * holds a cover, the essential tags it lacks, whether its text looks garbled.
 */
class FileCoverCheckStore(file: File, io: CoroutineDispatcher = Dispatchers.IO) : CoverCheckStore {
    private val lines = LineStore(file, io)

    override suspend fun load(): List<CoverCheck> = lines.load().mapNotNull { (uri, fields) ->
        if (fields.size != 5) return@mapNotNull null
        val modified = fields[0].toLongOrNull() ?: return@mapNotNull null
        val size = fields[1].toLongOrNull() ?: return@mapNotNull null
        val hasCover = yesNo(fields[2]) ?: return@mapNotNull null
        val missing = if (fields[3] == UNKNOWN) null else fields[3].split(',').filter { it.isNotEmpty() }.toSet()
        val garbled = yesNo(fields[4]) ?: return@mapNotNull null
        CoverCheck(uri, modified, size, hasCover.value, missing, garbled.value)
    }

    override suspend fun save(checks: List<CoverCheck>) {
        lines.save(
            checks.map { check ->
                check.uri to listOf(
                    check.dateModifiedEpochSeconds.toString(),
                    check.sizeBytes.toString(),
                    yesNo(check.hasCover),
                    check.missing?.joinToString(",") ?: UNKNOWN,
                    yesNo(check.garbled),
                )
            },
        )
    }

    override suspend fun remove(uris: Collection<String>) = lines.remove(uris)

    /** A yes, a no or an unknown as written; null for what is none of them. */
    private class Known(val value: Boolean?)

    private fun yesNo(field: String): Known? = when (field) {
        YES -> Known(true)
        NO -> Known(false)
        UNKNOWN -> Known(null)
        else -> null
    }

    private fun yesNo(value: Boolean?): String = when (value) {
        true -> YES
        false -> NO
        null -> UNKNOWN
    }

    private companion object {
        const val YES = "1"
        const val NO = "0"
        const val UNKNOWN = "?"
    }
}

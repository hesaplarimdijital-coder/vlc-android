/*
 * Nağmedar 1.5.0: from Walkman Sync (core/src/main/kotlin/com/walkmansync/core/domain/DefaultTagService.kt), the owner's own app.
 */
package app.nagmedar.walkman.domain

import app.nagmedar.walkman.fs.RandomAccessSource
import app.nagmedar.walkman.fs.ReadableTree
import app.nagmedar.walkman.model.AudioFormat
import app.nagmedar.walkman.model.AudioTags
import app.nagmedar.walkman.model.FileEntry
import app.nagmedar.walkman.model.NodeHandle
import app.nagmedar.walkman.model.SyncError
import app.nagmedar.walkman.model.SyncException
import app.nagmedar.walkman.runtime.AppDispatchers
import app.nagmedar.walkman.runtime.Logger
import app.nagmedar.walkman.tags.TagFallback
import app.nagmedar.walkman.tags.TagReader
import app.nagmedar.walkman.tags.TagService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/**
 * [TagReader] + [TagFallback] birleşimi. Kaynağı bu sınıf açar ve kapatır; eşzamanlı okuma sayısı
 * [maxConcurrency] ile sınırlıdır. Dosyaya özgü hatalar yutulur (null); iptal ve ölümcül birim hataları yayılır.
 */
class DefaultTagService(
    private val reader: TagReader,
    private val fallback: TagFallback,
    private val dispatchers: AppDispatchers,
    private val logger: Logger,
    maxConcurrency: Int = 4,
) : TagService {

    private val permits = Semaphore(maxConcurrency.coerceAtLeast(1))

    override suspend fun <H : NodeHandle> read(tree: ReadableTree<H>, file: FileEntry<H>): AudioTags? {
        val format = file.format ?: return null
        if (!format.isAudio) return null
        return permits.withPermit {
            try {
                tree.openRandomAccess(file.handle).use { source ->
                    withContext(dispatchers.io) { readFrom(source, format, file.relativePath) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SyncException) {
                if (isFatalVolumeError(e.error)) throw e
                logger.w(TAG, "Etiket okunamadı (${file.relativePath}): ${e.error}")
                null
            } catch (e: Exception) {
                logger.w(TAG, "Etiket okunamadı (${file.relativePath})", e)
                null
            }
        }
    }

    private fun readFrom(source: RandomAccessSource, format: AudioFormat, path: String): AudioTags? {
        val primary = try {
            reader.read(source, format)
        } catch (e: Exception) {
            logger.w(TAG, "Ayrıştırıcı hatası ($path)", e)
            null
        }
        val merged = if (primary == null || primary.title == null || primary.durationMs == null) {
            val extra = try {
                fallback.read(source, format)
            } catch (e: Exception) {
                logger.w(TAG, "Yedek okuyucu hatası ($path)", e)
                null
            }
            (primary ?: AudioTags()).mergedWith(extra)
        } else {
            primary
        }
        return merged.takeUnless { it.isEmpty }
    }

    private fun isFatalVolumeError(error: SyncError): Boolean = when (error) {
        is SyncError.VolumeMissing, is SyncError.VolumeMismatch, is SyncError.PermissionLost,
        is SyncError.VolumeReadOnly -> true
        is SyncError.IoFailure -> error.isFatal
        else -> false
    }

    private companion object {
        const val TAG = "TagService"
    }
}

/*
 * Nağmedar 1.5.0: from Walkman Sync (core/src/main/kotlin/com/walkmansync/core/engine/DefaultScanWorkflow.kt), the owner's own app.
 */
package app.nagmedar.walkman.engine

import app.nagmedar.walkman.delta.DeltaEngine
import app.nagmedar.walkman.delta.DeltaRequest
import app.nagmedar.walkman.fs.SourceTree
import app.nagmedar.walkman.fs.TargetTree
import app.nagmedar.walkman.fs.TreeNode
import app.nagmedar.walkman.index.IndexLoadResult
import app.nagmedar.walkman.index.IndexStore
import app.nagmedar.walkman.index.SyncIndex
import app.nagmedar.walkman.model.PlanWarning
import app.nagmedar.walkman.model.ScanPhase
import app.nagmedar.walkman.model.ScanProgress
import app.nagmedar.walkman.model.SyncError
import app.nagmedar.walkman.model.SyncException
import app.nagmedar.walkman.model.SyncPlan
import app.nagmedar.walkman.model.TargetHandle
import app.nagmedar.walkman.model.TargetLayout
import app.nagmedar.walkman.model.VolumePair
import app.nagmedar.walkman.model.VolumeRole
import app.nagmedar.walkman.runtime.AppDispatchers
import app.nagmedar.walkman.runtime.Clock
import app.nagmedar.walkman.runtime.Logger
import app.nagmedar.walkman.scan.TreeScanner
import app.nagmedar.walkman.workflow.ScanWorkflow
import java.io.IOException

/** Tarama aşamalarını sırayla yürütür; bkz. [ScanWorkflow]. */
class DefaultScanWorkflow(
    private val indexStore: IndexStore,
    private val treeScanner: TreeScanner,
    private val deltaEngine: DeltaEngine,
    private val clock: Clock,
    @Suppress("unused") private val dispatchers: AppDispatchers,
    private val logger: Logger,
) : ScanWorkflow {

    override suspend fun scan(
        pair: VolumePair,
        source: SourceTree,
        target: TargetTree,
        onProgress: (ScanProgress) -> Unit,
    ): SyncPlan {
        val warnings = LinkedHashSet<PlanWarning>()

        // 1. İndeks
        onProgress(ScanProgress(ScanPhase.LOADING_INDEX))
        var mayPersist = true
        val index: SyncIndex = when (val loaded = indexStore.load(target)) {
            is IndexLoadResult.Loaded -> {
                if (loaded.recoveredFromBackup) warnings.add(PlanWarning.INDEX_RECOVERED_FROM_BACKUP)
                loaded.index
            }
            IndexLoadResult.Missing -> SyncIndex.empty(pair.target, clock.nowMillis())
            is IndexLoadResult.Foreign -> {
                warnings.add(PlanWarning.FOREIGN_INDEX_IGNORED)
                mayPersist = false
                SyncIndex.empty(pair.target, clock.nowMillis())
            }
            is IndexLoadResult.Corrupt -> {
                logger.w(TAG, "İndeks bozuk, sıfırlandı: ${loaded.detail}")
                warnings.add(PlanWarning.INDEX_RESET_CORRUPT)
                mayPersist = false
                SyncIndex.empty(pair.target, clock.nowMillis())
            }
            is IndexLoadResult.Unsupported -> throw SyncException(SyncError.IndexUnsupported(loaded.schemaVersion))
        }

        // 2. Hedef taraması
        onProgress(ScanProgress(ScanPhase.LISTING_TARGET))
        val targetScan = treeScanner.scan(target, TargetLayout.MUSIC_DIR) { files, dirs, path ->
            onProgress(ScanProgress(ScanPhase.LISTING_TARGET, files, 0, dirs, path))
        }

        // 3. Artık .wsync.part temizliği
        onProgress(ScanProgress(ScanPhase.CLEANING_STALE_PARTS, 0, targetScan.partFiles.size))
        cleanStaleParts(target, targetScan.partFiles, onProgress)

        // 4. Kaynak taraması
        onProgress(ScanProgress(ScanPhase.LISTING_SOURCE))
        val sourceScan = treeScanner.scan(source, null) { files, dirs, path ->
            onProgress(ScanProgress(ScanPhase.LISTING_SOURCE, files, 0, dirs, path))
        }

        // 5-6. Fark motoru
        val fileSystem = target.fileSystem()
        val free = target.freeBytes()
        val result = deltaEngine.computePlan(
            DeltaRequest(
                pair = pair,
                source = source,
                target = target,
                sourceScan = sourceScan,
                targetScan = targetScan,
                index = index,
                targetFileSystem = fileSystem,
                targetFreeBytes = free,
                warnings = warnings.toSet(),
                nowMillis = clock.nowMillis(),
            ),
            onProgress,
        )

        // 7. Etiket önbelleği değiştiyse kaydet (yabancı/bozuk indeksin üzerine yazılmaz; hata yutulur)
        if (result.targetKeysChanged && mayPersist) {
            try {
                indexStore.save(target, result.plan.baseIndex)
            } catch (e: SyncException) {
                logger.w(TAG, "İndeks önbelleği kaydedilemedi", e)
            } catch (e: IOException) {
                logger.w(TAG, "İndeks önbelleği kaydedilemedi", e)
            }
        }
        return result.plan
    }

    /** Dosya bazlı silme hatası taramayı durdurmaz; ölümcül birim hatası ve iptal yayılır. */
    private suspend fun cleanStaleParts(
        target: TargetTree,
        parts: List<TreeNode<TargetHandle>>,
        onProgress: (ScanProgress) -> Unit,
    ) {
        val seen = HashSet<String>()
        var done = 0
        val all = ArrayList<TreeNode<TargetHandle>>(parts)
        target.resolve(TargetLayout.indexPartPathFor(target.root.relativePath))?.let { all.add(it) }
        for (node in all) {
            if (!seen.add(SyncIndex.foldPath(node.relativePath))) continue
            try {
                target.delete(node)
                logger.d(TAG, "Artık .part silindi: ${node.relativePath}")
            } catch (e: SyncException) {
                if (e.error.isFatal) throw e
                logger.w(TAG, "Artık .part silinemedi: ${node.relativePath}", e)
            } catch (e: IOException) {
                if (!target.isAvailable()) throw SyncException(SyncError.VolumeMissing(VolumeRole.TARGET, target.volume), e)
                logger.w(TAG, "Artık .part silinemedi: ${node.relativePath}", e)
            }
            done++
            onProgress(ScanProgress(ScanPhase.CLEANING_STALE_PARTS, done, all.size, 0, node.relativePath))
        }
    }

    private companion object {
        const val TAG = "ScanWorkflow"
    }
}

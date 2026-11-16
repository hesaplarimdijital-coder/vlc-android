/*
 * Nağmedar 1.5.0: from Walkman Sync (core/src/main/kotlin/com/walkmansync/core/runtime/Modules.kt), the owner's own app.
 */
package app.nagmedar.walkman.runtime

import app.nagmedar.walkman.delta.DeltaEngine
import app.nagmedar.walkman.index.IndexCodec
import app.nagmedar.walkman.index.IndexStore
import app.nagmedar.walkman.path.FileNameSanitizer
import app.nagmedar.walkman.path.PathPlanner
import app.nagmedar.walkman.scan.TreeScanner
import app.nagmedar.walkman.tags.TagReader
import app.nagmedar.walkman.tags.TagService
import app.nagmedar.walkman.transfer.TransferRunner
import app.nagmedar.walkman.workflow.ScanWorkflow
import app.nagmedar.walkman.workflow.SyncCoordinator
import app.nagmedar.walkman.workflow.TreeOpener
import app.nagmedar.walkman.workflow.VolumeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Basit manuel DI (Hilt YOK). İki ajan birbirinin sınıf adlarını bilmeden yalnızca bu iki fabrika
 * imzasıyla bağlanır:
 *
 *  - B2 yazar: `app.nagmedar.walkman.domain.DomainModules`
 *    ```
 *    object DomainModules {
 *        fun create(fallback: TagFallback, dispatchers: AppDispatchers, clock: Clock, logger: Logger): DomainModule
 *    }
 *    ```
 *  - B1 yazar: `app.nagmedar.walkman.engine.EngineModules`
 *    ```
 *    object EngineModules {
 *        fun create(domain: DomainModule, dispatchers: AppDispatchers, clock: Clock, logger: Logger): EngineModule
 *    }
 *    ```
 * Nağmedar'da `Walkman.graph`'ın kabı (`WalkmanContainer`) ikisini çağırır. Tüm örnekler süreç boyunca tekildir.
 */
interface DomainModule {
    val sanitizer: FileNameSanitizer
    val pathPlanner: PathPlanner
    val tagReader: TagReader
    val tagService: TagService
    val deltaEngine: DeltaEngine
    val indexCodec: IndexCodec
}

interface EngineModule {
    val treeScanner: TreeScanner
    val indexStore: IndexStore
    val scanWorkflow: ScanWorkflow
    val transferRunner: TransferRunner

    /** Süreç başına BİR kez çağrılır (`WalkmanContainer`). [scope] uygulama ömürlü olmalı. */
    fun createCoordinator(
        volumes: StateFlow<VolumeSnapshot>,
        opener: TreeOpener,
        scope: CoroutineScope,
    ): SyncCoordinator
}

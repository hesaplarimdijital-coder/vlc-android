/*
 * Nağmedar 1.5.0: from Walkman Sync (core/src/main/kotlin/com/walkmansync/core/engine/EngineModules.kt), the owner's own app.
 */
package app.nagmedar.walkman.engine

import app.nagmedar.walkman.index.IndexStore
import app.nagmedar.walkman.runtime.AppDispatchers
import app.nagmedar.walkman.runtime.Clock
import app.nagmedar.walkman.runtime.DomainModule
import app.nagmedar.walkman.runtime.EngineModule
import app.nagmedar.walkman.runtime.Logger
import app.nagmedar.walkman.scan.TreeScanner
import app.nagmedar.walkman.transfer.TransferRunner
import app.nagmedar.walkman.workflow.ScanWorkflow
import app.nagmedar.walkman.workflow.SyncCoordinator
import app.nagmedar.walkman.workflow.TreeOpener
import app.nagmedar.walkman.workflow.VolumeSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/** Motor katmanının fabrikası (bkz. runtime/Modules.kt). */
object EngineModules {
    fun create(domain: DomainModule, dispatchers: AppDispatchers, clock: Clock, logger: Logger): EngineModule =
        DefaultEngineModule(domain, dispatchers, clock, logger)
}

private class DefaultEngineModule(
    domain: DomainModule,
    dispatchers: AppDispatchers,
    private val clock: Clock,
    private val logger: Logger,
) : EngineModule {
    override val treeScanner: TreeScanner = DefaultTreeScanner(dispatchers, logger, clock)
    override val indexStore: IndexStore = TargetIndexStore(domain.indexCodec, logger, dispatchers)
    override val scanWorkflow: ScanWorkflow =
        DefaultScanWorkflow(indexStore, treeScanner, domain.deltaEngine, clock, dispatchers, logger)
    override val transferRunner: TransferRunner = AtomicTransferRunner(indexStore, clock, dispatchers, logger)

    override fun createCoordinator(
        volumes: StateFlow<VolumeSnapshot>,
        opener: TreeOpener,
        scope: CoroutineScope,
    ): SyncCoordinator =
        DefaultSyncCoordinator(volumes, opener, scanWorkflow, transferRunner, clock, logger, scope)
}

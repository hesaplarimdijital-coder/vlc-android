/*
 * Nağmedar 1.5.0: from Walkman Sync (app/src/main/kotlin/com/walkmansync/{WalkmanSyncApp,AppGraph,AppContainer}.kt),
 * the owner's own app.
 */
package app.nagmedar.walkman

import android.app.Application
import android.content.Context
import android.content.Intent
import app.nagmedar.walkman.domain.DomainModules
import app.nagmedar.walkman.engine.EngineModules
import app.nagmedar.walkman.media.MediaMetadataTagFallback
import app.nagmedar.walkman.platform.AndroidLogger
import app.nagmedar.walkman.runtime.AppDispatchers
import app.nagmedar.walkman.runtime.Clock
import app.nagmedar.walkman.runtime.Logger
import app.nagmedar.walkman.storage.SafVolumeRegistry
import app.nagmedar.walkman.storage.VolumeRegistry
import app.nagmedar.walkman.sync.DefaultSyncController
import app.nagmedar.walkman.sync.SyncController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The objects of "Walkman'a aktar" for the life of the process (Walkman Sync's manual wiring, in the order its AppGraph
 * gave): the cards, the scan and the copy, and the one bridge the page sees, [syncController].
 */
interface WalkmanGraph {
    val dispatchers: AppDispatchers
    val clock: Clock
    val logger: Logger
    val appScope: CoroutineScope
    val volumeRegistry: VolumeRegistry
    val syncController: SyncController
}

/**
 * 1.5.0: Walkman Sync in Nağmedar: new music from the tablet's card copied onto a Walkman's card, only added, never
 * overwritten or deleted. Its graph is made at its first use (cheap: no I/O; the cards are first read on I/O threads).
 */
object Walkman {
    @Volatile
    private var graph: WalkmanGraph? = null

    fun graph(context: Context): WalkmanGraph = graph ?: synchronized(this) {
        graph ?: WalkmanContainer(context.applicationContext as Application).also { graph = it }
    }

    /** Whether the graph is made: the page has been opened in this process, or a copy runs. */
    val started: Boolean get() = graph != null

    /**
     * What a tap on the notifications opens: the app's page of the copy. Nağmedar's page sets it (its own intent, with
     * its destination); the app's launch intent until then.
     */
    @Volatile
    var openApp: (Context) -> Intent? = { context -> context.packageManager.getLaunchIntentForPackage(context.packageName) }
}

private class WalkmanContainer(app: Application) : WalkmanGraph {
    override val dispatchers: AppDispatchers = AppDispatchers.Default
    override val clock: Clock = Clock.System
    override val logger: Logger = AndroidLogger
    override val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val domain = DomainModules.create(MediaMetadataTagFallback(app, logger), dispatchers, clock, logger)
    private val engine = EngineModules.create(domain, dispatchers, clock, logger)

    override val volumeRegistry: VolumeRegistry = SafVolumeRegistry(app, dispatchers, clock, logger, appScope)

    private val coordinator = engine.createCoordinator(volumeRegistry.snapshot, volumeRegistry, appScope)

    override val syncController: SyncController = DefaultSyncController(app, coordinator, volumeRegistry)
}

/*
 * Nağmedar 1.5.0: from Walkman Sync (core/src/main/kotlin/com/walkmansync/core/domain/DomainModules.kt), the owner's own app.
 */
package app.nagmedar.walkman.domain

import app.nagmedar.walkman.delta.DeltaEngine
import app.nagmedar.walkman.index.IndexCodec
import app.nagmedar.walkman.index.json.JsonIndexCodec
import app.nagmedar.walkman.path.FileNameSanitizer
import app.nagmedar.walkman.path.PathPlanner
import app.nagmedar.walkman.path.impl.DefaultPathPlanner
import app.nagmedar.walkman.path.impl.FatFileNameSanitizer
import app.nagmedar.walkman.runtime.AppDispatchers
import app.nagmedar.walkman.runtime.Clock
import app.nagmedar.walkman.runtime.DomainModule
import app.nagmedar.walkman.runtime.Logger
import app.nagmedar.walkman.tags.TagFallback
import app.nagmedar.walkman.tags.TagReader
import app.nagmedar.walkman.tags.TagService
import app.nagmedar.walkman.tags.parsers.CompositeTagReader

/** Alan katmanı fabrikası (bkz. [DomainModule]). Tüm örnekler durumsuzdur ve thread-safe'tir. */
object DomainModules {
    fun create(fallback: TagFallback, dispatchers: AppDispatchers, clock: Clock, logger: Logger): DomainModule {
        val nameSanitizer = FatFileNameSanitizer()
        val planner = DefaultPathPlanner(nameSanitizer)
        val tagParser = CompositeTagReader()
        val tags = DefaultTagService(tagParser, fallback, dispatchers, logger)
        val delta = DefaultDeltaEngine(tags, planner, dispatchers, clock, logger)
        val codec = JsonIndexCodec()
        return object : DomainModule {
            override val sanitizer: FileNameSanitizer = nameSanitizer
            override val pathPlanner: PathPlanner = planner
            override val tagReader: TagReader = tagParser
            override val tagService: TagService = tags
            override val deltaEngine: DeltaEngine = delta
            override val indexCodec: IndexCodec = codec
        }
    }
}

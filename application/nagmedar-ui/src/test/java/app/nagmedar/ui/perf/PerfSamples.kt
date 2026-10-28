package app.nagmedar.ui.perf

/*
 * S-1404: what a Tab S11 Ultra measured: seven cold start-ups (a slow one among them, the media
 * library's first scan).
 */

/** 12 October 2026, 10:41 UTC. */
const val SAMPLE_TIME = 1_791_801_660_000L

val sampleStartups = listOf(640L, 702L, 588L, 655L, 1_210L, 630L, 610L).mapIndexed { index, ms -> StartupRecord(SAMPLE_TIME - index * 3_600_000L, ms) }

fun samplePerfView() = PerfView(sampleStartups)

/** Records kept in memory. */
class MemoryPerfStore : PerfStore {
    val startups = mutableListOf<StartupRecord>()

    override fun startups() = startups.toList()

    override fun add(record: StartupRecord) = startups.add(0, record)

    override fun clear() {
        startups.clear()
    }
}

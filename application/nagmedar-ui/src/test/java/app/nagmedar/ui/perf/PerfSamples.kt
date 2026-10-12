package app.nagmedar.ui.perf

import app.nagmedar.ui.player.info.StatsSample

/*
 * S-1404: what a Tab S11 Ultra measured: seven cold start-ups (a slow one among them, the media
 * library's first scan) and four playbacks: a UHD remux from the external drive, off the
 * charger; the test set's HDR10 sample and its 100 Mbit/s load test, which drops frames; a 1080p
 * film.
 */

/** 12 October 2026, 10:41 UTC. */
const val SAMPLE_TIME = 1_791_801_660_000L

val sampleStartups = listOf(640L, 702L, 588L, 655L, 1_210L, 630L, 610L).mapIndexed { index, ms -> StartupRecord(SAMPLE_TIME - index * 3_600_000L, ms) }

val samplePlaybacks = listOf(
    PlaybackRecord(SAMPLE_TIME, "Kuzey_Isigi_2160p_UHD_Remux.mkv", 3840, 2160, "DV P8", 6_735, 161_480, 12, 0, 9.8),
    PlaybackRecord(SAMPLE_TIME - 600_000, "hdr10_2160p23976_hevc10_eac3.mkv", 3840, 2160, "HDR10", 10, 240, 0, 0, null),
    PlaybackRecord(SAMPLE_TIME - 660_000, "stress_2160p23976_hevc10_100mbps.mkv", 3840, 2160, "HDR10", 15, 352, 8, 2, null),
    PlaybackRecord(SAMPLE_TIME - 86_400_000, "Gece_Mavisi_1080p.mp4", 1920, 1080, "SDR", 2_520, 60_420, 3, 0, 6.1),
)

fun samplePerfView() = PerfView(sampleStartups, samplePlaybacks)

/** libVLC's statistics of a film after [displayed] frames shown and [lost] dropped. */
fun stats(displayed: Long, lost: Long = 0, audioLost: Long = 0) = StatsSample(inputMbps = 60f, displayed = displayed, lost = lost, audioLost = audioLost)

/** Records kept in memory. */
class MemoryPerfStore : PerfStore {
    val startups = mutableListOf<StartupRecord>()
    val playbacks = mutableListOf<PlaybackRecord>()

    override fun startups() = startups.toList()

    override fun playbacks() = playbacks.toList()

    override fun add(record: StartupRecord) = startups.add(0, record)

    override fun add(record: PlaybackRecord) = playbacks.add(0, record)

    override fun clear() {
        startups.clear()
        playbacks.clear()
    }
}

package app.nagmedar.ui.convert

import app.nagmedar.audio.convert.ConvertFailure
import app.nagmedar.audio.convert.ConvertResult
import app.nagmedar.audio.convert.ConvertSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1010: a job's files one after the other: made, left alone, failed, stopped, taken up again; the device's rules. */
class ConvertRunnerTest {

    /**
     * A folder in memory: the paths there (with what each holds), the temporary files made, the
     * files put in place and removed. [sources] are the paths that are the sources themselves.
     */
    private class MemoryDestination(
        val ready: Boolean = true,
        there: Set<String> = emptySet(),
        val refuse: Set<String> = emptySet(),
        val sources: Set<String> = emptySet(),
        val commitFails: Boolean = false,
    ) : ConvertDestination {
        val files = there.associateWith { "old" }.toMutableMap()
        val made = mutableListOf<String>()
        val placed = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        private fun find(path: String) = files.keys.firstOrNull { it.equals(path, ignoreCase = true) }
        override fun ready() = ready
        override fun exists(item: ConvertItem) = find(item.path) != null
        override fun isSource(item: ConvertItem) = find(item.path)?.let { it in sources } ?: false
        override fun createTemporary(item: ConvertItem): String? {
            if (item.path in refuse) return null
            val temporary = (item.folders + TreeDestination.temporaryName(item)).joinToString("/")
            files[temporary] = "new"
            made += temporary
            return "doc:$temporary"
        }
        override fun commit(handle: String, item: ConvertItem, replace: Boolean): Boolean {
            val before = find(item.path)
            if (commitFails || (before != null && (!replace || before in sources))) return false
            before?.let { files.remove(it) }
            files.remove(handle.removePrefix("doc:"))
            files[item.path] = "new"
            placed += item.path
            return true
        }
        override fun delete(handle: String) {
            deleted += handle
            files.remove(handle.removePrefix("doc:"))
        }
    }

    /** An engine that reports [steps] progress calls per file; [answers] gives a file's result by its title. */
    private class FakeEngine(val steps: Int = 4, val answers: Map<String, ConvertResult> = emptyMap(), val onStep: (String, Int) -> Unit = { _, _ -> }) : ConvertEngine {
        val calls = mutableListOf<Pair<String, ConvertSettings>>()
        override fun convert(item: ConvertItem, output: String, settings: ConvertSettings, progress: (Double) -> Boolean): ConvertResult {
            calls += output to settings
            for (step in 1..steps) {
                onStep(item.title, step)
                if (!progress(step.toDouble() / steps)) return ConvertResult.Cancelled
            }
            return answers[item.title] ?: ConvertResult.Done(48_000L * 3, 48_000, 2, 1000L + item.title.length, 7, true, null)
        }
    }

    /** A clock that moves [stepMs] at each look, and the sleeps asked for. */
    private class Clock(val stepMs: Long = 300) {
        var now = 0L
        val sleeps = mutableListOf<Long>()
        fun read(): Long = now.also { now += stepMs }
        fun sleep(ms: Long) {
            sleeps += ms
            now += ms
        }
    }

    private fun item(title: String) = ConvertItem("file:///m/$title.flac", title, listOf("Ada", "Yol"), "$title.opus")

    private fun job(vararg titles: String, skip: Boolean = true) =
        ConvertJob("j1", 0L, ConvertOptions(preset = ConvertPreset.Opus160, skipExisting = skip, folder = "content://tree"), titles.map(::item))

    private fun runner(engine: ConvertEngine, destination: ConvertDestination, device: () -> DeviceState = { DeviceState.FINE }, clock: Clock = Clock()) =
        ConvertRunner(engine, destination, device, clock::read, clock::sleep)

    @Test
    fun everyFileMadeInOrder() {
        val engine = FakeEngine()
        val destination = MemoryDestination()
        val progress = mutableListOf<ConvertProgress>()
        val saved = mutableListOf<ConvertSummary>()
        val summary = runner(engine, destination).run(job("Bir", "İki"), onProgress = { progress += it }, onItem = { saved += it })
        assertEquals(2, summary.converted)
        assertTrue(summary.finished)
        // each written under a hidden temporary name, then put in place
        assertEquals(listOf("Ada/Yol/.Bir.opus.part", "Ada/Yol/.İki.opus.part"), destination.made)
        assertEquals(listOf("doc:Ada/Yol/.Bir.opus.part", "doc:Ada/Yol/.İki.opus.part"), engine.calls.map { it.first })
        assertEquals(listOf("Ada/Yol/Bir.opus", "Ada/Yol/İki.opus"), destination.placed)
        assertEquals(setOf("Ada/Yol/Bir.opus", "Ada/Yol/İki.opus"), destination.files.keys)
        assertEquals(160_000, engine.calls.first().second.bitrate)
        // each file's start is told, then its progress; the second file starts after the first's count
        assertEquals(0, progress.first().index)
        assertEquals(1, progress.last().index)
        assertEquals(1, progress.last { it.index == 1 }.converted)
        assertTrue(progress.all { it.overall in 0f..1f })
        // the summary is kept after each file, and once finished
        assertEquals(listOf(1, 2, 2), saved.map { it.outcomes.size })
        assertEquals(listOf(false, false, true), saved.map { it.finished })
    }

    @Test
    fun filesThereAreLeftAloneOrReplaced() {
        val there = setOf("Ada/Yol/bir.opus")
        val skipped = runner(FakeEngine(), MemoryDestination(there = there)).run(job("Bir", "İki"))
        assertEquals(1, skipped.skipped)
        assertEquals(1, skipped.converted)
        assertTrue(skipped.outcomes[0] is ItemOutcome.Skipped)
        // asked not to skip: the file there is replaced once the new one is whole, never written into
        val destination = MemoryDestination(there = there)
        val replaced = runner(FakeEngine(), destination).run(job("Bir", skip = false))
        assertEquals(1, replaced.converted)
        assertEquals(listOf("Ada/Yol/.Bir.opus.part"), destination.made)
        assertEquals(mapOf("Ada/Yol/Bir.opus" to "new"), destination.files)
    }

    @Test
    fun theSourceInThePlannedPlaceIsNeverTouched() {
        // an M4A library converted to AAC into its own folder: the planned file is the source
        val there = setOf("Ada/Yol/Bir.opus")
        val engine = FakeEngine()
        val destination = MemoryDestination(there = there, sources = there)
        val summary = runner(engine, destination).run(job("Bir", "İki", skip = false))
        assertEquals(ConvertReason.Source, summary.failed.single().reason)
        assertEquals(1, summary.converted)
        assertEquals("old", destination.files["Ada/Yol/Bir.opus"])
        // nothing made for it, nothing removed
        assertEquals(listOf("doc:Ada/Yol/.İki.opus.part"), engine.calls.map { it.first })
        assertTrue(destination.deleted.isEmpty())
    }

    @Test
    fun aFailureLeavesTheFileThereAsItWas() {
        val there = setOf("Ada/Yol/Bozuk.opus", "Ada/Yol/Yer.opus")
        val engine = FakeEngine(answers = mapOf("Bozuk" to ConvertResult.Failed(ConvertFailure.Engine, "decoding")))
        val destination = MemoryDestination(there = there)
        runner(engine, destination).run(job("Bozuk", skip = false))
        assertEquals("old", destination.files["Ada/Yol/Bozuk.opus"])
        assertEquals(listOf("doc:Ada/Yol/.Bozuk.opus.part"), destination.deleted)
        // the new one could not be put in place: the old one stays, the new one goes
        val stuck = MemoryDestination(there = there, commitFails = true)
        val summary = runner(FakeEngine(), stuck).run(job("Yer", skip = false))
        assertEquals(ConvertReason.Output, summary.failed.single().reason)
        assertEquals("old", stuck.files["Ada/Yol/Yer.opus"])
        assertEquals(listOf("doc:Ada/Yol/.Yer.opus.part"), stuck.deleted)
    }

    @Test
    fun anEngineThatThrowsFailsTheFileNotTheJob() {
        val throwing = ConvertEngine { item, _, _, _ -> if (item.title == "Bir") throw IllegalStateException("boom") else ConvertResult.Done(1, 48_000, 2, 10, 0, false, null) }
        val destination = MemoryDestination()
        val summary = runner(throwing, destination).run(job("Bir", "İki"))
        assertEquals(ConvertReason.Engine, summary.failed.single().reason)
        assertEquals("boom", summary.failed.single().detail)
        assertEquals(1, summary.converted)
        assertEquals(listOf("doc:Ada/Yol/.Bir.opus.part"), destination.deleted)
        assertTrue(summary.finished)
    }

    @Test
    fun theFileUnderWayIsRecordedAndALeftoverRemoved() {
        val partials = mutableListOf<String?>()
        val destination = MemoryDestination()
        runner(FakeEngine(steps = 1), destination).run(job("Bir"), leftover = "doc:Ada/Yol/.Eski.opus.part", onPartial = { partials += it })
        // the earlier run's temporary file goes first; then this file's is recorded while it is written
        assertEquals("doc:Ada/Yol/.Eski.opus.part", destination.deleted.first())
        assertEquals(listOf(null, "doc:Ada/Yol/.Bir.opus.part", null), partials)
    }

    @Test
    fun aFailedFileIsRemovedAndTheRestGoOn() {
        val engine = FakeEngine(answers = mapOf("Bozuk" to ConvertResult.Failed(ConvertFailure.Engine, "decoding: Invalid data")))
        val destination = MemoryDestination(refuse = setOf("Ada/Yol/Yer.opus"))
        val summary = runner(engine, destination).run(job("Bozuk", "Yer", "Sağlam"))
        val failed = summary.failed
        assertEquals(listOf(ConvertReason.Engine, ConvertReason.Output), failed.map { it.reason })
        assertEquals("decoding: Invalid data", failed[0].detail)
        assertEquals(listOf("doc:Ada/Yol/.Bozuk.opus.part"), destination.deleted)
        assertEquals(1, summary.converted)
        assertFalse(summary.cancelled)
    }

    @Test
    fun noEngineFailsEveryFile() {
        val engine = FakeEngine(answers = mapOf("Bir" to ConvertResult.Failed(ConvertFailure.NoEngine)))
        val summary = runner(engine, MemoryDestination()).run(job("Bir", "İki", "Üç"))
        assertEquals(List(3) { ConvertReason.NoEngine }, summary.failed.map { it.reason })
        assertEquals(1, engine.calls.size)
    }

    @Test
    fun aFolderGoneFailsEveryFileAtOnce() {
        val engine = FakeEngine()
        val summary = runner(engine, MemoryDestination(ready = false)).run(job("Bir", "İki"))
        assertEquals(List(2) { ConvertReason.Folder }, summary.failed.map { it.reason })
        assertTrue(engine.calls.isEmpty())
        assertTrue(summary.finished)
    }

    @Test
    fun cancelledInAFile() {
        var stop = false
        val engine = FakeEngine(onStep = { title, step -> if (title == "İki" && step == 2) stop = true })
        val destination = MemoryDestination()
        val summary = runner(engine, destination).run(job("Bir", "İki", "Üç"), cancelled = { stop })
        assertEquals(1, summary.converted)
        assertEquals(listOf(ConvertReason.Cancelled, ConvertReason.Cancelled), summary.failed.map { it.reason })
        assertTrue(summary.cancelled)
        // the file under way is removed, the one after never made
        assertEquals(listOf("doc:Ada/Yol/.İki.opus.part"), destination.deleted)
        assertEquals(2, engine.calls.size)
    }

    @Test
    fun interruptedSaysNothingOfTheFileUnderWay() {
        var interrupted = false
        val engine = FakeEngine(onStep = { title, step -> if (title == "İki" && step == 3) interrupted = true })
        val destination = MemoryDestination()
        val saved = mutableListOf<ConvertSummary>()
        val summary = runner(engine, destination).run(job("Bir", "İki", "Üç"), interrupted = { interrupted }, onItem = { saved += it })
        assertEquals(1, summary.outcomes.size)
        assertFalse(summary.finished)
        assertEquals(listOf("doc:Ada/Yol/.İki.opus.part"), destination.deleted)
        // nothing under the planned name for the file cut short
        assertFalse("Ada/Yol/İki.opus" in destination.files)
        // taken up again, it goes on after the first file
        val again = FakeEngine()
        val done = runner(again, destination).run(job("Bir", "İki", "Üç"), done = saved.last())
        assertEquals(3, done.converted)
        assertEquals(listOf("doc:Ada/Yol/.İki.opus.part", "doc:Ada/Yol/.Üç.opus.part"), again.calls.map { it.first })
        assertEquals(listOf("Bir", "İki", "Üç"), done.outcomes.map { it.item.title })
    }

    @Test
    fun aLowBatteryWaitsForTheCharger() {
        val clock = Clock()
        val states = ArrayDeque(listOf(DeviceState(12, charging = false, heat = Heat.None), DeviceState(18, false, Heat.None), DeviceState(18, true, Heat.None)))
        val waiting = mutableListOf<ConvertReason?>()
        val engine = FakeEngine(steps = 1)
        val summary = runner(engine, MemoryDestination(), { if (states.size > 1) states.removeFirst() else states.first() }, clock)
            .run(job("Bir"), onProgress = { waiting += it.waiting })
        assertEquals(1, summary.converted)
        // two waits: under 15 %, then at 18 % (it must climb to 20 % or charge)
        assertEquals(listOf(ConvertPolicy.PAUSE_WAIT_MS, ConvertPolicy.PAUSE_WAIT_MS), clock.sleeps)
        assertEquals(ConvertReason.Battery, waiting.first())
    }

    @Test
    fun anEmptyBatteryStopsTheJob() {
        val engine = FakeEngine()
        val summary = runner(engine, MemoryDestination(), { DeviceState(4, charging = false, heat = Heat.None) }).run(job("Bir", "İki"))
        assertEquals(List(2) { ConvertReason.Battery }, summary.failed.map { it.reason })
        assertTrue(engine.calls.isEmpty())
    }

    @Test
    fun heatSlowsPausesAndStops() {
        // moderately hot while converting: waits now and then
        val clock = Clock(stepMs = 600)
        var heat = Heat.None
        val engine = FakeEngine(steps = 6, onStep = { _, step -> if (step == 2) heat = Heat.Moderate })
        runner(engine, MemoryDestination(), { DeviceState(100, true, heat) }, clock).run(job("Bir"))
        assertTrue(clock.sleeps.isNotEmpty())
        assertTrue(clock.sleeps.all { it == ConvertPolicy.SLOW_WAIT_MS })
        // critical in the middle of a file: it is removed, the rest fail for the heat
        var hot = Heat.None
        val destination = MemoryDestination()
        val burning = FakeEngine(steps = 6, onStep = { _, step -> if (step == 3) hot = Heat.Critical })
        val summary = runner(burning, destination, { DeviceState(100, true, hot) }, Clock(stepMs = 600)).run(job("Bir", "İki"))
        assertEquals(List(2) { ConvertReason.Thermal }, summary.failed.map { it.reason })
        assertEquals(listOf("doc:Ada/Yol/.Bir.opus.part"), destination.deleted)
    }

    @Test
    fun theRules() {
        assertEquals(ConvertSteer.Go, ConvertPolicy.decide(DeviceState.FINE, paused = false))
        assertEquals(ConvertSteer.Go, ConvertPolicy.decide(DeviceState(10, charging = true, heat = Heat.Light), paused = false))
        assertEquals(ConvertSteer.Pause(ConvertReason.Battery, ConvertPolicy.PAUSE_WAIT_MS), ConvertPolicy.decide(DeviceState(14, false, Heat.None), paused = false))
        assertEquals(ConvertSteer.Go, ConvertPolicy.decide(DeviceState(16, false, Heat.None), paused = false))
        assertEquals(ConvertSteer.Pause(ConvertReason.Battery, ConvertPolicy.PAUSE_WAIT_MS), ConvertPolicy.decide(DeviceState(19, false, Heat.None), paused = true))
        assertEquals(ConvertSteer.Go, ConvertPolicy.decide(DeviceState(20, false, Heat.None), paused = true))
        assertEquals(ConvertSteer.Stop(ConvertReason.Battery), ConvertPolicy.decide(DeviceState(4, false, Heat.None), paused = false))
        assertEquals(ConvertSteer.Slow(ConvertPolicy.SLOW_WAIT_MS), ConvertPolicy.decide(DeviceState(100, true, Heat.Moderate), paused = false))
        assertEquals(ConvertSteer.Pause(ConvertReason.Thermal, ConvertPolicy.PAUSE_WAIT_MS), ConvertPolicy.decide(DeviceState(100, true, Heat.Severe), paused = false))
        assertEquals(ConvertSteer.Stop(ConvertReason.Thermal), ConvertPolicy.decide(DeviceState(100, true, Heat.Emergency), paused = false))
    }
}

package app.nagmedar.lab

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** S-1206: the plan the TV Lab queues, its text for the engine and its JSON for the row; the engine's answers read. */
class RemuxPlanTest {

    private val video = VideoTech(
        codec = "hevc", profile = "Main 10", width = 3840, height = 2160, fps = 23.976, bitDepth = 10, transfer = "smpte2084",
        primaries = "bt2020", matrix = "bt2020nc", maxLuminance = 1000.0, minLuminance = 0.0001, maxCll = 1000, maxFall = 400,
        hdr10Plus = false, dvProfile = 7, dvLevel = 6, dvCompatibility = 6, dvEnhancement = "MEL", dvEnhancementLayer = true, dvRpu = true,
    )

    private fun audio(codec: String, language: String) =
        AudioTech(codec, "", 6, "5.1", 48_000, 24, 0, language, "", isDefault = false, forced = false, commentary = false)

    private val tech = MediaTech(
        format = "matroska,webm", brand = "", durationMs = 7_905_000, sizeBytes = 58_400_000_000, bitrate = 59_100_000, chapters = 32,
        video = video,
        audio = listOf(audio("truehd", "eng"), audio("dts", "tur")),
        subtitles = listOf(SubtitleTech("hdmv_pgs_subtitle", "tur", "", isDefault = false, forced = false, hearingImpaired = false)),
    )

    @Test
    fun theDefaultPlanKeepsEveryTrackInOrderAndConvertsProfile7WhenAsked() {
        val plan = RemuxPlan.default(tech, convertDolbyVision = true) { it.toString() }
        assertEquals(listOf("v:0", "a:0", "a:1", "s:0"), plan.streams.map { it.source.toString() })
        assertEquals(listOf("hevc", "truehd", "dts", "hdmv_pgs_subtitle"), plan.streams.map { it.codec })
        assertEquals("a:1", plan.streams[2].label)
        assertEquals(DolbyVisionMode.ConvertToProfile81, plan.dolbyVision)
        assertTrue(plan.chapters && plan.attachments && plan.metadata)
        assertTrue(plan.copiesEverything)
        // flags, language and title stay the source's unless the planner sets them
        assertTrue(plan.streams.all { it.isDefault == null && it.forced == null && it.language == null && it.title == null })
        // not asked, or not profile 7: the metadata is copied as it is
        assertEquals(DolbyVisionMode.Keep, RemuxPlan.default(tech, convertDolbyVision = false).dolbyVision)
        val profile8 = tech.copy(video = video.copy(dvProfile = 8, dvCompatibility = 1, dvEnhancement = "", dvEnhancementLayer = false))
        assertEquals(DolbyVisionMode.Keep, RemuxPlan.default(profile8, convertDolbyVision = true).dolbyVision)
        assertEquals(listOf("a:0", "a:1", "s:0"), RemuxPlan.default(tech.copy(video = null), true).streams.map { it.source.toString() })
    }

    @Test
    fun theEngineReadsTheTextAsRemuxHDescribesIt() {
        val plan = RemuxPlan(
            streams = listOf(
                PlannedStream(StreamRef(StreamType.Video, 0), "hevc"),
                PlannedStream(StreamRef(StreamType.Audio, 1), "dts", isDefault = true, language = "tur"),
                PlannedStream(StreamRef(StreamType.Audio, 0), "truehd", isDefault = false, forced = false, title = "Yorum\tbir\nsatır"),
                PlannedStream(StreamRef(StreamType.Subtitle, 0), "subrip", title = ""),
            ),
            dolbyVision = DolbyVisionMode.ConvertToProfile81,
            attachments = false,
        )
        assertEquals(
            "dv\tconvert81\nchapters\t1\nattachments\t0\nmetadata\t1\n" +
                "stream\tv:0\n" +
                "stream\ta:1\tdefault=1\tlang=tur\n" +
                "stream\ta:0\tdefault=0\tforced=0\ttitle=Yorum bir satır\n" +
                "stream\ts:0\ttitle=\n",
            plan.toNative(),
        )
        assertEquals("dv\tkeep\nchapters\t1\nattachments\t1\nmetadata\t1\nstream\tv:0\n", RemuxPlan(listOf(PlannedStream(StreamRef(StreamType.Video, 0), "h264"))).toNative())
    }

    @Test
    fun aPlanWithAnEncodedTrackWritesAnEncodeLine() {
        val plan = RemuxPlan(listOf(PlannedStream(StreamRef(StreamType.Video, 0), "hevc"), PlannedStream(StreamRef(StreamType.Audio, 0), "truehd", encode = EncodeSpec(EncodeCodec.EAC3))))
        assertFalse(plan.copiesEverything)
        assertTrue(plan.toNative().endsWith("encode\ta:0\teac3\tdefault=0\n"))
    }

    @Test
    fun thePlanSurvivesItsJson() {
        val plan = RemuxPlan(
            streams = listOf(
                PlannedStream(StreamRef(StreamType.Video, 0), "hevc", label = "HEVC Main 10 · 3840×2160"),
                PlannedStream(StreamRef(StreamType.Audio, 1), "dts", label = "DTS-HD MA 5.1 · Türkçe", isDefault = true, language = "tur", title = ""),
                PlannedStream(StreamRef(StreamType.Audio, 0), "truehd", forced = false, encode = EncodeSpec(EncodeCodec.EAC3, 640, 6, makeDefault = true)),
            ),
            dolbyVision = DolbyVisionMode.ConvertToProfile81,
            chapters = false,
            attachments = true,
            metadata = false,
        )
        assertEquals(plan, RemuxPlan.parse(plan.toJson()))
        assertNull(RemuxPlan.parse("{}"))
        assertNull(RemuxPlan.parse("""{"streams":[{"source":"x:0"}]}"""))
        assertNull(StreamRef.parse("a:"))
        assertNull(StreamRef.parse("q:1"))
        assertEquals(StreamRef(StreamType.Subtitle, 12), StreamRef.parse("s:12"))
    }

    @Test
    fun theEnginesStatsAreRead() {
        val json = """{"error":"","cancelled":false,"streams":[{"ref":"v:0","source_index":0,"output_index":0,"codec":"hevc","packets":259,"bytes":97759},""" +
            """{"ref":"a:0","source_index":1,"output_index":1,"codec":"aac","packets":475,"bytes":95401}],"attachments":1,"chapters":2,""" +
            """"video_codec":"hevc","video_packets":259,"video_units":795,"hash_scope":"base_layer_nal_units","video_hash":"fad1","source_seconds":11.029,""" +
            """"media_seconds":10.761,"bytes_in":208314,"bytes_out":173324,"wall_seconds":0.014,"paused_seconds":2.5,"peak_rss_kb":6896,""" +
            """"dv":{"input_profile":7,"input_el_type":"MEL","output_profile":8,"rpus_converted":259,"rpus_failed":0,"el_nals_dropped":795,"el_bytes_dropped":16583,"first_rpu_error":""}}"""
        val result = RemuxResult.parse(json)!!
        assertTrue(result.ok)
        assertEquals(2, result.streams.size)
        assertEquals(StreamRef(StreamType.Audio, 0), result.streams[1].source)
        assertEquals(259L, result.videoPackets)
        assertEquals("base_layer_nal_units", result.hashScope)
        assertEquals("fad1", result.videoHash)
        assertEquals(2.5, result.pausedSeconds, 0.0)
        assertEquals(7, result.dolbyVision.inputProfile)
        assertEquals("MEL", result.dolbyVision.inputEnhancement)
        assertEquals(795L, result.dolbyVision.enhancementNalsDropped)
        assertEquals(json, result.json)
        val cancelled = RemuxResult.parse("""{"error":"cancelled","cancelled":true}""")!!
        assertFalse(cancelled.ok)
        assertTrue(cancelled.cancelled)
        assertNull(RemuxResult.parse("not json"))
    }

    @Test
    fun theVerdictIsRead() {
        val json = """{"ok":true,"error":"","cancelled":false,"hash_scope":"base_layer_nal_units","expected_hash":"fad1","hash":"fad1",""" +
            """"expected_packets":259,"packets":259,"units":795,"duration_seconds":11.029,"dv_expected_profile":8,"dv_profile":8,"dv_compatibility":1,""" +
            """"dv_el_present":0,"rpus":259,"rpus_bad":0,"packets_without_rpu":0}"""
        val v = RemuxVerification.parse(json)!!
        assertTrue(v.ok)
        assertEquals("fad1", v.hash)
        assertEquals(259L, v.packets)
        assertEquals(8, v.dvProfile)
        assertFalse(v.dvEnhancementPresent)
        assertEquals(json, v.json)
        val bad = RemuxVerification.parse("""{"ok":false,"hash":"0000","expected_hash":"fad1","packets":258,"expected_packets":259}""")!!
        assertFalse(bad.ok)
        assertEquals(258L, bad.packets)
    }
}

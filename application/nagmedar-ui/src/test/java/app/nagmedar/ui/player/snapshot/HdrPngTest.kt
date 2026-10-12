package app.nagmedar.ui.player.snapshot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.util.zip.CRC32
import java.util.zip.InflaterInputStream
import kotlin.math.abs

/**
 * S-606: an HDR screenshot is a 16-bit PNG that says it is BT.2020 with the PQ or HLG transfer
 * (cICP) and carries the film's light levels (cLLI); its samples are the frame's codes; the
 * subtitles sit at the reference white.
 */
class HdrPngTest {

    private class Chunk(val type: String, val data: ByteArray)

    /** Every chunk of a PNG, each one's CRC checked. */
    private fun chunks(png: ByteArray): List<Chunk> {
        val input = DataInputStream(png.inputStream())
        val signature = ByteArray(8).also { input.readFully(it) }
        assertArrayEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A), signature)
        val found = mutableListOf<Chunk>()
        while (true) {
            val length = input.readInt()
            val type = ByteArray(4).also { input.readFully(it) }
            val data = ByteArray(length).also { input.readFully(it) }
            val crc = input.readInt()
            val expected = CRC32().apply {
                update(type)
                update(data)
            }.value.toInt()
            assertEquals("CRC of ${String(type)}", expected, crc)
            found += Chunk(String(type, Charsets.US_ASCII), data)
            if (String(type) == "IEND") break
        }
        return found
    }

    private fun int(data: ByteArray, at: Int) = DataInputStream(data.inputStream(at, 4)).readInt()

    /** The picture's rows, unfiltered: each sample as a code value. */
    private fun samples(chunks: List<Chunk>, width: Int, height: Int): List<IntArray> {
        val compressed = chunks.filter { it.type == "IDAT" }.fold(ByteArrayOutputStream()) { out, chunk -> out.apply { write(chunk.data) } }.toByteArray()
        val raw = InflaterInputStream(compressed.inputStream()).readBytes()
        val stride = width * 6
        assertEquals(height * (stride + 1), raw.size)
        return (0 until height).map { y ->
            val filter = raw[y * (stride + 1)].toInt()
            val line = raw.copyOfRange(y * (stride + 1) + 1, (y + 1) * (stride + 1))
            for (i in line.indices) {
                val left = if (i >= 6) line[i - 6].toInt() and 0xFF else 0
                line[i] = when (filter) {
                    0 -> line[i]
                    1 -> ((line[i].toInt() and 0xFF) + left).toByte()
                    else -> error("filter $filter")
                }
            }
            IntArray(width * 3) { ((line[it * 2].toInt() and 0xFF) shl 8) or (line[it * 2 + 1].toInt() and 0xFF) }
        }
    }

    @Test
    fun aPqPictureSaysSoAndKeepsItsCodes() {
        val rows = listOf(
            shortArrayOf(0, 0, 0, 1000, 2000, 3000, -1, 0x7FFF, 12),
            shortArrayOf(42, 42, 42, 0, -1, 0, 30_000, 31_000, 32_000),
        )
        val out = ByteArrayOutputStream()
        HdrPng.write(out, 3, 2, HdrTransfer.Pq, ContentLight(maxCll = 1000, maxFall = 400)) { y, rgb -> rows[y].copyInto(rgb) }
        val chunks = chunks(out.toByteArray())
        assertEquals("IHDR", chunks.first().type)
        assertEquals("IEND", chunks.last().type)
        val header = chunks.first().data
        assertEquals(3, int(header, 0))
        assertEquals(2, int(header, 4))
        assertEquals(listOf(16, 2, 0, 0, 0), header.drop(8).map { it.toInt() })
        // BT.2020, PQ, RGB, full range; before the picture
        val cicp = chunks.single { it.type == "cICP" }
        assertEquals(listOf(9, 16, 0, 1), cicp.data.map { it.toInt() })
        assertTrue(chunks.indexOf(cicp) < chunks.indexOfFirst { it.type == "IDAT" })
        // 1000 and 400 cd/m², in 0.0001 cd/m²
        val light = chunks.single { it.type == "cLLI" }.data
        assertEquals(10_000_000, int(light, 0))
        assertEquals(4_000_000, int(light, 4))
        val samples = samples(chunks, 3, 2)
        assertArrayEquals(rows[0].map { it.toInt() and 0xFFFF }.toIntArray(), samples[0])
        assertArrayEquals(rows[1].map { it.toInt() and 0xFFFF }.toIntArray(), samples[1])
    }

    @Test
    fun anHlgPictureWithoutLightLevels() {
        val out = ByteArrayOutputStream()
        HdrPng.write(out, 1, 1, HdrTransfer.Hlg, ContentLight(0, 0)) { _, rgb -> rgb.fill(500) }
        val chunks = chunks(out.toByteArray())
        assertEquals(listOf(9, 18, 0, 1), chunks.single { it.type == "cICP" }.data.map { it.toInt() })
        assertTrue(chunks.none { it.type == "cLLI" })
        assertArrayEquals(intArrayOf(500, 500, 500), samples(chunks, 1, 1)[0])
    }

    @Test
    fun aLargePictureIsCutIntoChunks() {
        val out = ByteArrayOutputStream()
        // noise does not compress: well over one IDAT chunk
        var seed = 7
        HdrPng.write(out, 640, 360, HdrTransfer.Pq, null) { _, rgb ->
            for (i in rgb.indices) {
                seed = seed * 1_103_515_245 + 12_345
                rgb[i] = (seed ushr 8).toShort()
            }
        }
        val chunks = chunks(out.toByteArray())
        assertTrue(chunks.count { it.type == "IDAT" } > 1)
        assertEquals(360, samples(chunks, 640, 360).size)
    }

    @Test
    fun halfFloatsBecomeCodes() {
        assertEquals(0, HalfToCode.code(0x0000).toInt() and 0xFFFF)
        assertEquals(32_768, HalfToCode.code(0x3800).toInt() and 0xFFFF)
        assertEquals(65_535, HalfToCode.code(0x3C00).toInt() and 0xFFFF)
        // above 1 and below 0 are clipped, NaN is black
        assertEquals(65_535, HalfToCode.code(0x4000).toInt() and 0xFFFF)
        assertEquals(0, HalfToCode.code(0xBC00.toShort()).toInt() and 0xFFFF)
        assertEquals(0, HalfToCode.code(0x7E00).toInt() and 0xFFFF)
        assertEquals(0.000_060_975_55f, halfToFloat(0x03FF), 1e-9f)
        assertEquals(65_504f, halfToFloat(0x7BFF))
    }

    @Test
    fun subtitlesSitAtTheReferenceWhite() {
        fun channels(codes: Long) = listOf((codes ushr 32).toInt() and 0xFFFF, (codes ushr 16).toInt() and 0xFFFF, codes.toInt() and 0xFFFF)
        // 203 cd/m² in PQ is 58 % of the signal; HLG's reference white is 75 %
        val pqWhite = channels(SubtitleColours(HdrTransfer.Pq).codes(0xFFFFFFFF.toInt()))
        pqWhite.forEach { assertTrue("$it", abs(it / 65_535.0 - 0.5807) < 0.001) }
        val hlgWhite = channels(SubtitleColours(HdrTransfer.Hlg).codes(0xFFFFFFFF.toInt()))
        hlgWhite.forEach { assertTrue("$it", abs(it / 65_535.0 - 0.75) < 0.001) }
        // black is black; a pure red stays red, in BT.2020's terms
        assertEquals(listOf(0, 0, 0), channels(SubtitleColours(HdrTransfer.Hlg).codes(0xFF000000.toInt())))
        val red = channels(SubtitleColours(HdrTransfer.Pq).codes(0xFFFF0000.toInt()))
        assertTrue(red.toString(), red[0] > red[1] && red[1] > red[2])
        assertEquals(1.0, SubtitleColours.pq(1.0), 1e-9)
        assertEquals(0.5, SubtitleColours.hlg(1.0 / 12), 1e-9)
    }
}

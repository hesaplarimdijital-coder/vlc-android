package app.nagmedar.ui.player.snapshot

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.OutputStream
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt

/** The film's light levels from its metadata, in cd/m², for the PNG's cLLI chunk; 0 when unknown. */
data class ContentLight(val maxCll: Int, val maxFall: Int)

/**
 * A 16-bit PNG of an HDR picture (S-606) that says how it is coded: BT.2020 primaries, the PQ or
 * HLG transfer and full range in its cICP chunk (PNG's third edition), and the film's light
 * levels in cLLI. Viewers that read cICP show it as HDR; the others show its code values.
 */
object HdrPng {

    /**
     * Writes a [width] × [height] picture; [row] fills one row's red, green and blue code values
     * (0..65535, as shorts) for the row index it is given, top to bottom.
     */
    fun write(
        out: OutputStream,
        width: Int,
        height: Int,
        transfer: HdrTransfer,
        light: ContentLight?,
        row: (y: Int, rgb: ShortArray) -> Unit,
    ) {
        require(width > 0 && height > 0)
        val data = DataOutputStream(out)
        data.write(SIGNATURE)
        chunk(data, "IHDR") {
            writeInt(width)
            writeInt(height)
            writeByte(16) // bits per sample
            writeByte(2) // truecolour: red, green, blue
            writeByte(0) // deflate
            writeByte(0) // adaptive filtering
            writeByte(0) // not interlaced
        }
        chunk(data, "cICP") {
            writeByte(BT2020_PRIMARIES)
            writeByte(transfer.cicp)
            writeByte(0) // RGB, no matrix
            writeByte(1) // full range
        }
        if (light != null && (light.maxCll > 0 || light.maxFall > 0)) {
            chunk(data, "cLLI") {
                // in units of 0.0001 cd/m²
                writeInt(light.maxCll * 10_000)
                writeInt(light.maxFall * 10_000)
            }
        }
        val idat = IdatStream(data)
        val deflater = Deflater(DEFLATE_LEVEL)
        DeflaterOutputStream(idat, deflater, IDAT_SIZE).use { zip ->
            val rgb = ShortArray(width * 3)
            val raw = ByteArray(width * BYTES_PER_PIXEL)
            val filtered = ByteArray(width * BYTES_PER_PIXEL + 1)
            for (y in 0 until height) {
                row(y, rgb)
                for (i in rgb.indices) {
                    val v = rgb[i].toInt()
                    raw[i * 2] = (v shr 8).toByte()
                    raw[i * 2 + 1] = v.toByte()
                }
                // "Sub": each byte less the same byte of the pixel to its left
                filtered[0] = FILTER_SUB
                for (i in raw.indices) {
                    val left = if (i >= BYTES_PER_PIXEL) raw[i - BYTES_PER_PIXEL].toInt() else 0
                    filtered[i + 1] = (raw[i] - left).toByte()
                }
                zip.write(filtered)
            }
        }
        deflater.end()
        idat.finish()
        chunk(data, "IEND") {}
        data.flush()
    }

    private fun chunk(out: DataOutputStream, type: String, body: DataOutputStream.() -> Unit) {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply(body).flush()
        writeChunk(out, type, bytes.toByteArray(), bytes.size())
    }

    private fun writeChunk(out: DataOutputStream, type: String, data: ByteArray, length: Int) {
        val name = type.toByteArray(Charsets.US_ASCII)
        val crc = CRC32().apply {
            update(name)
            update(data, 0, length)
        }
        out.writeInt(length)
        out.write(name)
        out.write(data, 0, length)
        out.writeInt(crc.value.toInt())
    }

    /** The compressed picture, cut into IDAT chunks as it comes. */
    private class IdatStream(private val out: DataOutputStream) : OutputStream() {
        private val buffer = ByteArray(IDAT_SIZE)
        private var size = 0

        override fun write(b: Int) {
            if (size == buffer.size) flushChunk()
            buffer[size++] = b.toByte()
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            var from = off
            var left = len
            while (left > 0) {
                if (size == buffer.size) flushChunk()
                val n = minOf(left, buffer.size - size)
                System.arraycopy(b, from, buffer, size, n)
                size += n
                from += n
                left -= n
            }
        }

        // the zip stream closes this: the data stays open for the chunks after
        override fun close() = Unit

        fun finish() {
            if (size > 0) flushChunk()
        }

        private fun flushChunk() {
            writeChunk(out, "IDAT", buffer, size)
            size = 0
        }
    }

    private val SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)
    private const val BT2020_PRIMARIES = 9
    private const val BYTES_PER_PIXEL = 6
    private const val FILTER_SUB: Byte = 1
    private const val IDAT_SIZE = 256 * 1024

    /** Fast enough for a 4K picture in a second or two, a little larger than the best. */
    private const val DEFLATE_LEVEL = 3
}

/**
 * Half floats (an F16 bitmap's samples) to 16-bit code values: 0..1 to 0..65535, clipped; a
 * table of every half, made once.
 */
object HalfToCode {
    private val table: ShortArray by lazy {
        ShortArray(65_536) { bits ->
            val value = halfToFloat(bits)
            val clipped = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
            (clipped * 65_535f + 0.5f).toInt().toShort()
        }
    }

    fun code(half: Short): Short = table[half.toInt() and 0xFFFF]
}

/** A half float's value: its sign, five bits of exponent and ten of fraction. */
fun halfToFloat(bits: Int): Float {
    val sign = if (bits and 0x8000 != 0) -1f else 1f
    val exponent = bits ushr 10 and 0x1F
    val fraction = bits and 0x3FF
    return sign * when (exponent) {
        0 -> fraction / 1024f * 2f.pow(-14)
        0x1F -> if (fraction == 0) Float.POSITIVE_INFINITY else Float.NaN
        else -> (1f + fraction / 1024f) * 2f.pow(exponent - 15)
    }
}

/**
 * The subtitles' colours in an HDR picture: sRGB at the HDR reference white (203 cd/m², ITU-R
 * BT.2408), in BT.2020 and coded with the picture's transfer, as 16-bit code values.
 */
class SubtitleColours(private val transfer: HdrTransfer) {
    private val cache = HashMap<Int, Long>()

    /** [rgb] (the low 24 bits of an ARGB colour) as red, green and blue codes packed 16 bits each. */
    fun codes(rgb: Int): Long = cache.getOrPut(rgb and 0xFFFFFF) {
        val r = srgbToLinear(rgb shr 16 and 0xFF)
        val g = srgbToLinear(rgb shr 8 and 0xFF)
        val b = srgbToLinear(rgb and 0xFF)
        // BT.709 to BT.2020 (ITU-R BT.2087)
        val r2 = 0.6274 * r + 0.3293 * g + 0.0433 * b
        val g2 = 0.0691 * r + 0.9195 * g + 0.0114 * b
        val b2 = 0.0164 * r + 0.0880 * g + 0.8956 * b
        (code(r2) shl 32) or (code(g2) shl 16) or code(b2)
    }

    private fun code(linear: Double): Long {
        val signal = when (transfer) {
            HdrTransfer.Pq -> pq(linear * REFERENCE_WHITE / PQ_PEAK)
            // HLG's reference white is 75 % of the signal, a scene light of 0.26496
            HdrTransfer.Hlg -> hlg(linear * HLG_REFERENCE_SCENE)
        }
        return (signal.coerceIn(0.0, 1.0) * 65_535 + 0.5).toLong()
    }

    companion object {
        const val REFERENCE_WHITE = 203.0
        private const val PQ_PEAK = 10_000.0
        private const val HLG_REFERENCE_SCENE = 0.26496

        fun srgbToLinear(value: Int): Double {
            val v = value / 255.0
            return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }

        /** SMPTE ST 2084's inverse EOTF: light (1 is 10 000 cd/m²) to signal. */
        fun pq(light: Double): Double {
            val m1 = 2610.0 / 16384
            val m2 = 2523.0 / 4096 * 128
            val c1 = 3424.0 / 4096
            val c2 = 2413.0 / 4096 * 32
            val c3 = 2392.0 / 4096 * 32
            val y = light.coerceAtLeast(0.0).pow(m1)
            return ((c1 + c2 * y) / (1 + c3 * y)).pow(m2)
        }

        /** ITU-R BT.2100's HLG OETF: scene light (0..1) to signal. */
        fun hlg(light: Double): Double {
            val e = light.coerceAtLeast(0.0)
            val a = 0.17883277
            val b = 1 - 4 * a
            val c = 0.5 - a * ln(4 * a)
            return if (e <= 1.0 / 12) sqrt(3 * e) else a * ln(12 * e - b) + c
        }
    }
}

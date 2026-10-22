package app.nagmedar.ui.radio.record

import java.io.ByteArrayOutputStream

/**
 * What [TransportStream.audio] found in a segment of MPEG-TS: [format] is the audio's format when
 * the program has sound only, of a kind that is cut into frames (AAC in ADTS, MPEG audio), and
 * [audio] its bytes, ready for a framer; [format] is null otherwise (pictures, another codec, no
 * program table), and the segment is kept whole.
 */
class TsAudio(val format: AudioFormat?, val audio: ByteArray)

/** S-1107: takes the audio out of MPEG transport stream segments (ISO/IEC 13818-1), as far as HLS radio needs. */
object TransportStream {
    private const val PACKET = 188
    private const val SYNC = 0x47

    /** Whether [data] is a transport stream: sync bytes one packet apart. */
    fun matches(data: ByteArray, length: Int = data.size): Boolean =
        length >= PACKET * 2 && data[0].toInt() == SYNC && data[PACKET].toInt() == SYNC && (length < PACKET * 3 || data[PACKET * 2].toInt() == SYNC)

    /** The audio of the segment [data]; [TsAudio.format] is null when it cannot be had as frames. */
    fun audio(data: ByteArray): TsAudio {
        var pmtPid = -1
        var audioPid = -1
        var audioType = 0
        var video = false
        var foundTable = false
        val out = ByteArrayOutputStream()
        var inPes = false
        var at = 0
        while (at + PACKET <= data.size) {
            if (data[at].toInt() != SYNC) {
                // lost the packets: the next sync byte that has another a packet on
                val next = (at + 1 until data.size - PACKET).firstOrNull { data[it].toInt() == SYNC && data[it + PACKET].toInt() == SYNC } ?: break
                at = next
                continue
            }
            val b1 = data[at + 1].toInt() and 0xFF
            val b2 = data[at + 2].toInt() and 0xFF
            val b3 = data[at + 3].toInt() and 0xFF
            val pid = ((b1 and 0x1F) shl 8) or b2
            val start = b1 and 0x40 != 0
            val error = b1 and 0x80 != 0
            val control = (b3 shr 4) and 3
            var payload = at + 4
            if (control and 2 != 0) payload += 1 + (data[payload].toInt() and 0xFF)
            val end = at + PACKET
            at += PACKET
            if (error || control and 1 == 0 || payload >= end) continue
            when {
                pid == 0 && start && pmtPid < 0 -> pmtPid = programMap(data, payload, end)
                pid == pmtPid && start && !foundTable -> {
                    val streams = streams(data, payload, end) ?: continue
                    foundTable = true
                    for ((type, streamPid) in streams) {
                        when (type) {
                            TYPE_ADTS, TYPE_MPEG1, TYPE_MPEG2 -> if (audioPid < 0) {
                                audioPid = streamPid
                                audioType = type
                            }
                            in VIDEO_TYPES -> video = true
                            else -> Unit
                        }
                    }
                }
                pid == audioPid -> {
                    if (start) {
                        // a PES packet: 00 00 01, stream id, length, two flag bytes, header length
                        if (end - payload < 9 || data[payload].toInt() != 0 || data[payload + 1].toInt() != 0 || data[payload + 2].toInt() != 1) {
                            inPes = false
                            continue
                        }
                        val body = payload + 9 + (data[payload + 8].toInt() and 0xFF)
                        inPes = true
                        if (body < end) out.write(data, body, end - body)
                    } else if (inPes) {
                        out.write(data, payload, end - payload)
                    }
                }
            }
        }
        val format = when {
            !foundTable || video || audioPid < 0 -> null
            audioType == TYPE_ADTS -> AudioFormat.Aac
            else -> AudioFormat.Mp3
        }
        return TsAudio(format, if (format == null) ByteArray(0) else out.toByteArray())
    }

    /** The PID of the first program's map in a PAT section at [from]; -1 when it is not there. */
    private fun programMap(data: ByteArray, from: Int, end: Int): Int {
        val section = from + 1 + (data[from].toInt() and 0xFF)
        if (section + 12 > end || data[section].toInt() != 0) return -1
        val length = ((data[section + 1].toInt() and 0x0F) shl 8) or (data[section + 2].toInt() and 0xFF)
        val last = minOf(section + 3 + length - 4, end)
        var i = section + 8
        while (i + 4 <= last) {
            val program = ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            val pid = ((data[i + 2].toInt() and 0x1F) shl 8) or (data[i + 3].toInt() and 0xFF)
            // program 0 is the network table
            if (program != 0) return pid
            i += 4
        }
        return -1
    }

    /** The (stream type, PID) pairs of a PMT section at [from]; null when the section does not fit in the packet. */
    private fun streams(data: ByteArray, from: Int, end: Int): List<Pair<Int, Int>>? {
        val section = from + 1 + (data[from].toInt() and 0xFF)
        if (section + 12 > end || data[section].toInt() != 2) return null
        val length = ((data[section + 1].toInt() and 0x0F) shl 8) or (data[section + 2].toInt() and 0xFF)
        val last = section + 3 + length - 4
        if (last > end) return null
        var i = section + 12 + (((data[section + 10].toInt() and 0x0F) shl 8) or (data[section + 11].toInt() and 0xFF))
        val out = ArrayList<Pair<Int, Int>>()
        while (i + 5 <= last) {
            val type = data[i].toInt() and 0xFF
            val pid = ((data[i + 1].toInt() and 0x1F) shl 8) or (data[i + 2].toInt() and 0xFF)
            val info = ((data[i + 3].toInt() and 0x0F) shl 8) or (data[i + 4].toInt() and 0xFF)
            out += type to pid
            i += 5 + info
        }
        return out
    }

    private const val TYPE_MPEG1 = 0x03
    private const val TYPE_MPEG2 = 0x04
    private const val TYPE_ADTS = 0x0F

    /** MPEG-1/2 video, MPEG-4 part 2, AVC, HEVC and VVC. */
    private val VIDEO_TYPES = setOf(0x01, 0x02, 0x10, 0x1B, 0x24, 0x33)
}

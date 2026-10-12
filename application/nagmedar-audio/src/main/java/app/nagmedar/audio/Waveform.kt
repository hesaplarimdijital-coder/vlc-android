package app.nagmedar.audio

/**
 * S-1001: a track's waveform as the engine measured it: each bucket's peak and RMS over all the
 * channels, 0..1, in order from the start. [buckets] holds them interleaved, peak first.
 */
class Waveform(private val buckets: FloatArray) {

    val size: Int get() = buckets.size / 2

    fun peak(index: Int): Float = buckets[2 * index]

    fun rms(index: Int): Float = buckets[2 * index + 1]

    /** As bytes, for a cache file: each value in 0..255. */
    fun toBytes(): ByteArray = ByteArray(buckets.size) { (buckets[it].coerceIn(0f, 1f) * 255f + 0.5f).toInt().toByte() }

    override fun equals(other: Any?): Boolean = other is Waveform && buckets.contentEquals(other.buckets)

    override fun hashCode(): Int = buckets.contentHashCode()

    companion object {
        /** From [toBytes]; null when the bytes cannot be one. */
        fun fromBytes(bytes: ByteArray): Waveform? =
            if (bytes.isEmpty() || bytes.size % 2 != 0) null else Waveform(FloatArray(bytes.size) { (bytes[it].toInt() and 0xFF) / 255f })
    }
}

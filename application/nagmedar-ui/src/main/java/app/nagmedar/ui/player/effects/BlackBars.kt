package app.nagmedar.ui.player.effects

/** The black bars above and below a picture, as rows of the sample they were found in. */
data class BlackBars(val top: Int, val bottom: Int) {
    val isNone: Boolean get() = top == 0 && bottom == 0
}

/**
 * Where a wide film's black bars are (S-505's auto crop), from a small copy of the picture: a
 * row is a bar when nearly all of it is dark, and the bars are the dark rows at the top and the
 * bottom. A frame that is nearly all dark (a fade, a black frame) tells nothing, and several
 * frames are read so that a dark sky is not taken for a bar: the bars are the least found.
 */
object BlackBarsFinder {

    /** Below this (0 … 255), a pixel's brightness is black. */
    const val BLACK = 24

    /** This share of a row's pixels may be brighter and the row still be a bar: a logo, noise. */
    const val NOISE = 0.03

    /** A frame with bars over this share of its height tells nothing. */
    const val TOO_DARK = 0.8

    /**
     * The bars in a frame of [width] × [height] pixels whose brightness is [luma] (0 … 255, a
     * row at a time); null when the frame tells nothing.
     */
    fun find(luma: IntArray, width: Int, height: Int): BlackBars? {
        if (width <= 0 || height <= 0 || luma.size < width * height) return null
        val allowed = (width * NOISE).toInt().coerceAtLeast(1)
        fun dark(row: Int): Boolean {
            var bright = 0
            val from = row * width
            for (x in 0 until width) if (luma[from + x] > BLACK && ++bright > allowed) return false
            return true
        }
        var top = 0
        while (top < height && dark(top)) top++
        var bottom = 0
        while (bottom < height - top && dark(height - 1 - bottom)) bottom++
        if (top + bottom >= height * TOO_DARK) return null
        return BlackBars(top, bottom)
    }

    /** The bars every frame of [frames] has: the least at the top and at the bottom; null without a frame. */
    fun agree(frames: List<BlackBars>): BlackBars? =
        if (frames.isEmpty()) null else BlackBars(frames.minOf { it.top }, frames.minOf { it.bottom })

    /**
     * The bars of a [sampleHeight]-row sample as pixels of a [pictureHeight]-row picture, a
     * little inside the bar so that a dark edge is not left: whole rows of the sample, and none
     * for a bar too thin to matter.
     */
    fun scale(bars: BlackBars, sampleHeight: Int, pictureHeight: Int): BlackBars {
        if (sampleHeight <= 0 || pictureHeight <= 0) return BlackBars(0, 0)
        fun rows(sample: Int): Int {
            val px = sample * pictureHeight / sampleHeight
            return if (px < MIN_BAR_PX) 0 else px
        }
        return BlackBars(rows(bars.top), rows(bars.bottom))
    }

    /** The brightness of ARGB pixels, as the rec. 601 luma. */
    fun luma(argb: IntArray): IntArray = IntArray(argb.size) { i ->
        val p = argb[i]
        ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
    }

    /** A bar thinner than this, in picture pixels, is left (an encoder's black line). */
    const val MIN_BAR_PX = 8
}

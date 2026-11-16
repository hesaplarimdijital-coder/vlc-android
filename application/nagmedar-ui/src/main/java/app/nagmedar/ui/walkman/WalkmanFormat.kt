/*
 * Nağmedar 1.5.0: from Walkman Sync (app/src/main/kotlin/com/walkmansync/ui/UiFormat.kt), the owner's own app.
 */
package app.nagmedar.ui.walkman

import java.util.Locale

private val SIZE_UNITS = arrayOf("B", "KB", "MB", "GB", "TB")

/** Bytes as they are read (in 1024s); a dash for a size not known. */
internal fun formatBytes(bytes: Long): String {
    if (bytes < 0) return DASH
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < SIZE_UNITS.lastIndex) {
        value /= 1024.0
        unit++
    }
    if (unit == 0) return "$bytes B"
    val pattern = when {
        value >= 100.0 -> "%.0f %s"
        value >= 10.0 -> "%.1f %s"
        else -> "%.2f %s"
    }
    return String.format(Locale.getDefault(), pattern, value, SIZE_UNITS[unit])
}

/** Bytes a second; a dash before the first measure. */
internal fun formatRate(bytesPerSecond: Long): String =
    if (bytesPerSecond <= 0) DASH else formatBytes(bytesPerSecond) + "/s"

/** A time: 4:05 or 1:02:03; less than nothing is nothing. */
internal fun formatDuration(millis: Long): String {
    val totalSeconds = (millis.coerceAtLeast(0) + 500) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

/** The time left; a dash when it is not known. */
internal fun formatEta(millis: Long?): String = if (millis == null || millis < 0) DASH else formatDuration(millis)

/** A share as a whole percentage (0..100). */
internal fun formatPercent(fraction: Float): String = "${(fraction.coerceIn(0f, 1f) * 100f).toInt()}%"

internal const val DASH = "—"

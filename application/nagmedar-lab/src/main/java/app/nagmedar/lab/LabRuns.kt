package app.nagmedar.lab

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Locale

private const val NO_NATIVE = "Bu sürümde yerel laboratuvar kitaplığı yok (libnagmedar_lab.so). CI'dan alınan APK'yı kullanın."

private fun ParcelFileDescriptor.procPath() = "/proc/self/fd/$fd"

fun Context.displayName(uri: Uri): String =
    runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment ?: uri.toString()

private fun mb(bytes: Long) = String.format(Locale.ROOT, "%.1f MB", bytes / 1e6)

private fun sec(value: Double) = String.format(Locale.ROOT, "%.1f sn", value)

/** SP-03: Dolby Vision profile 7 -> 8.1 remux of a file the user picked into one they created. */
object DvConversion {

    suspend fun run(context: Context, input: Uri, output: Uri, maxSeconds: Double, mp4: Boolean, progress: NativeLab.Progress): String =
        withContext(Dispatchers.IO) {
            if (!NativeLab.available) return@withContext NO_NATIVE
            val inName = context.displayName(input)
            val outName = context.displayName(output)
            val json = context.contentResolver.openFileDescriptor(input, "r").use { src ->
                context.contentResolver.openFileDescriptor(output, "rw").use { dst ->
                    if (src == null || dst == null) return@withContext "Dosya açılamadı."
                    NativeLab.dvConvert(src.procPath(), dst.procPath(), maxSeconds, mp4, progress)
                }
            }
            report(JSONObject(json), inName, outName, maxSeconds, mp4)
        }

    fun report(r: JSONObject, inName: String, outName: String, maxSeconds: Double, mp4: Boolean): String = buildString {
        appendLine("Nağmedar Dolby Vision dönüştürme raporu (SP-03)")
        appendLine("Kaynak: $inName")
        appendLine("Çıktı: $outName (${if (mp4) "MP4" else "MKV"})")
        appendLine("Kapsam: ${if (maxSeconds > 0) "ilk ${maxSeconds.toInt()} sn" else "tüm dosya"}")
        appendLine()
        val profile = r.optInt("input_profile")
        val el = r.optString("input_el_type")
        appendLine("Kaynak profil: ${if (profile == 0) "Dolby Vision yok" else "P$profile${if (el.isNotEmpty()) " ($el)" else ""}"}")
        appendLine("Çıktı profil: ${if (r.optInt("output_profile") == 0) "-" else "P${r.optInt("output_profile")}.1"}")
        appendLine("Video paketi: ${r.optLong("video_packets")}")
        appendLine("Dönüştürülen RPU: ${r.optLong("rpus_converted")}, başarısız: ${r.optLong("rpus_failed")}")
        appendLine("Atılan EL NAL: ${r.optLong("el_nals_dropped")} (${mb(r.optLong("el_bytes_dropped"))})")
        appendLine("Kopyalanan iz: ${r.optInt("streams_copied")}, atlanan: ${r.optInt("streams_skipped")}")
        appendLine()
        val media = r.optDouble("media_seconds")
        val wall = r.optDouble("wall_seconds")
        appendLine("Okunan: ${mb(r.optLong("bytes_in"))}, yazılan: ${mb(r.optLong("bytes_out"))}")
        appendLine("Süre: ${sec(wall)} (${sec(media)} medya; ${if (wall > 0) String.format(Locale.ROOT, "%.1f×", media / wall) else "-"} gerçek zaman)")
        if (wall > 0) appendLine("Hız: ${String.format(Locale.ROOT, "%.0f MB/sn", r.optLong("bytes_in") / 1e6 / wall)}")
        appendLine("En yüksek bellek (süreç): ${r.optLong("peak_rss_kb") / 1024} MB")
        r.optString("first_rpu_error").takeIf { it.isNotEmpty() }?.let { appendLine("İlk RPU hatası: $it") }
        r.optString("error").takeIf { it.isNotEmpty() }?.let { appendLine("Hata: $it") }
        appendLine()
        appendLine("Sıradaki adım: çıktıyı harici diske kopyalayıp LG B4'te açın; Dolby Vision logosu görünüyor mu, takılma var mı not edin.")
    }
}

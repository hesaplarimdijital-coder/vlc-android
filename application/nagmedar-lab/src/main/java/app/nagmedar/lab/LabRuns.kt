package app.nagmedar.lab

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
            val result = JSONObject(json)
            // a failed, refused (profile 5) or cancelled conversion leaves a partial file; without an RPU the
            // output is still a complete HDR10 remux and stays
            val deleted = isPartial(result) &&
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, output) }.getOrDefault(false)
            report(result, inName, outName, maxSeconds, mp4) + if (deleted) "\nYarım kalan çıktı dosyası silindi.\n" else ""
        }

    fun isPartial(r: JSONObject): Boolean {
        val error = r.optString("error")
        return error.isNotEmpty() && !error.startsWith("no Dolby Vision RPU found")
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

/** SP-05: whisper.cpp on this device. Models come from the whisper.cpp repository on Hugging Face. */
object WhisperLab {

    enum class Model(val file: String, val label: String) {
        Base("ggml-base-q5_1.bin", "base q5_1 (57 MB)"),
        Small("ggml-small-q5_1.bin", "small q5_1 (181 MB)"),
        Turbo("ggml-large-v3-turbo-q5_0.bin", "large-v3-turbo q5_0 (547 MB)"),
        ;

        val url get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$file"
    }

    fun modelFile(context: Context, model: Model) = File(context.getExternalFilesDir("whisper") ?: context.filesDir, model.file)

    /** Downloads the model once; returns the file. */
    suspend fun ensureModel(context: Context, model: Model, progress: NativeLab.Progress): File = withContext(Dispatchers.IO) {
        val file = modelFile(context, model)
        if (file.exists() && file.length() > 1_000_000) return@withContext file
        val part = File(file.parentFile, file.name + ".part")
        val connection = URL(model.url).openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 20_000
        connection.readTimeout = 60_000
        try {
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    var done = 0L
                    var last = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        if (done - last > 2_000_000) {
                            last = done
                            progress.onProgress(if (total > 0) done.toDouble() / total else 0.0, "model ${mb(done)}")
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (!part.renameTo(file)) throw IllegalStateException("model could not be saved")
        file
    }

    suspend fun run(context: Context, input: Uri, model: Model, maxSeconds: Double, progress: NativeLab.Progress): String =
        withContext(Dispatchers.IO) {
            if (!NativeLab.available) return@withContext NO_NATIVE
            val modelFile = ensureModel(context, model, progress)
            val name = context.displayName(input)
            val json = context.contentResolver.openFileDescriptor(input, "r").use { src ->
                if (src == null) return@withContext "Dosya açılamadı."
                NativeLab.whisper(modelFile.absolutePath, src.procPath(), "tr", maxSeconds, 0, progress)
            }
            report(JSONObject(json), name, model)
        }

    fun report(r: JSONObject, name: String, model: Model): String = buildString {
        appendLine("Nağmedar whisper.cpp raporu (SP-05)")
        appendLine("Kaynak: $name")
        appendLine("Model: ${model.label}, dil: ${r.optString("language")}, iş parçacığı: ${r.optInt("threads")}")
        appendLine("Ses: ${sec(r.optDouble("audio_seconds"))}")
        appendLine("Çözme: ${sec(r.optDouble("decode_seconds"))}, model yükleme: ${sec(r.optDouble("load_seconds"))}")
        appendLine("Tanıma: ${sec(r.optDouble("inference_seconds"))} — gerçek zaman katsayısı ${String.format(Locale.ROOT, "%.2f", r.optDouble("real_time_factor"))} (1'in altı gerçek zamandan hızlı)")
        appendLine("Bölüt: ${r.optInt("segments")}")
        appendLine("Sistem: ${r.optString("system")}")
        r.optString("error").takeIf { it.isNotEmpty() }?.let { appendLine("Hata: $it") }
        appendLine()
        appendLine("Doğruluk için metin (ilk 1500 karakter):")
        appendLine(r.optString("text").trim().take(1500))
        appendLine()
        appendLine("SRT:")
        append(r.optString("srt"))
    }
}

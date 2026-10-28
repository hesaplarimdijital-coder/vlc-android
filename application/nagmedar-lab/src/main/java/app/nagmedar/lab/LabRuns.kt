package app.nagmedar.lab

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
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

    /** [model] on [input]'s first [maxSeconds]: on the GPU when [gpu] asks for it and the device has one (S-709), else on the processor. */
    suspend fun run(context: Context, input: Uri, model: Model, maxSeconds: Double, gpu: Boolean, progress: NativeLab.Progress): String =
        withContext(Dispatchers.IO) {
            if (!NativeLab.available) return@withContext NO_NATIVE
            val modelFile = ensureModel(context, model, progress)
            val name = context.displayName(input)
            val json = context.contentResolver.openFileDescriptor(input, "r").use { src ->
                if (src == null) return@withContext "Dosya açılamadı."
                NativeLab.whisper(modelFile.absolutePath, src.procPath(), "tr", maxSeconds, 0, gpu, progress)
            }
            report(JSONObject(json), name, model)
        }

    /** "GPU · Vulkan (Mali-G925…), flash attention açık" or "İşlemci · 4 iş parçacığı", and why the GPU asked for was not used. */
    private fun engine(r: JSONObject): String {
        val where = if (r.optBoolean("gpu")) {
            "GPU · Vulkan (${r.optString("device")})"
        } else {
            "İşlemci"
        }
        val attention = if (r.optBoolean("flash_attn")) "flash attention açık" else "flash attention kapalı"
        val fallback = when (r.optString("fallback")) {
            "" -> ""
            "none" -> " — GPU yok ya da kullanılamıyor"
            "busy" -> " — GPU'yu başka bir iş kullanıyordu"
            "init" -> " — model GPU'ya yüklenemedi"
            "failed" -> " — GPU iş sırasında durdu, işlemci devraldı"
            else -> " — ${r.optString("fallback")}"
        }
        val detail = r.optString("fallback_detail").takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty()
        return "$where · ${r.optInt("threads")} iş parçacığı, $attention$fallback$detail"
    }

    fun report(r: JSONObject, name: String, model: Model): String = buildString {
        appendLine("Nağmedar whisper.cpp raporu (SP-05)")
        appendLine("Kaynak: $name")
        appendLine("Model: ${model.label}, dil: ${r.optString("language")}")
        appendLine("Hızlandırma: ${engine(r)}")
        appendLine("Ses: ${sec(r.optDouble("audio_seconds"))}")
        appendLine("Çözme: ${sec(r.optDouble("decode_seconds"))}, model yükleme: ${sec(r.optDouble("load_seconds"))}")
        appendLine("Tanıma: ${sec(r.optDouble("inference_seconds"))} — gerçek zaman katsayısı ${String.format(Locale.ROOT, "%.2f", r.optDouble("real_time_factor"))} (1'in altı gerçek zamandan hızlı)")
        val audio = r.optDouble("audio_seconds")
        val inference = r.optDouble("inference_seconds")
        if (audio > 0 && inference > 0) appendLine("Hız: gerçek zamanın ${String.format(Locale.ROOT, "%.1f", audio / inference)} katı")
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

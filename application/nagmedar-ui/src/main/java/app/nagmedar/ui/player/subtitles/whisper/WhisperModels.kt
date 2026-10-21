package app.nagmedar.ui.player.subtitles.whisper

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import app.nagmedar.ui.R
import java.io.File

/**
 * S-709: the whisper.cpp models the subtitles from speech can use, the three SP-05 measures, from
 * the whisper.cpp repository on Hugging Face. Until SP-05's decision small q5_1 is the default,
 * the balance of Turkish accuracy and speed for a Dimensity 9400+; base is quicker and rougher,
 * large-v3-turbo the most accurate and the slowest.
 */
enum class WhisperModel(val key: String, val file: String, val sizeMb: Int, @StringRes val label: Int, @StringRes val hint: Int) {
    Base("base", "ggml-base-q5_1.bin", 57, R.string.nag_whisper_model_base, R.string.nag_whisper_model_base_hint),
    Small("small", "ggml-small-q5_1.bin", 181, R.string.nag_whisper_model_small, R.string.nag_whisper_model_small_hint),
    Turbo("turbo", "ggml-large-v3-turbo-q5_0.bin", 547, R.string.nag_whisper_model_turbo, R.string.nag_whisper_model_turbo_hint),
    ;

    val url: String get() = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/$file"

    /** The size Hugging Face is expected to serve, give or take: a file far from it is not the model. */
    val expectedBytes: Long get() = sizeMb * 1_000_000L

    companion object {
        val Default = Small

        fun of(key: String?): WhisperModel = entries.firstOrNull { it.key == key } ?: Default
    }
}

/** The languages a film's speech is heard in: found by whisper, or said. */
enum class WhisperLanguage(val code: String, @StringRes val label: Int) {
    Auto("auto", R.string.nag_whisper_lang_auto),
    Turkish("tr", R.string.nag_whisper_lang_tr),
    English("en", R.string.nag_whisper_lang_en),
    ;

    companion object {
        fun of(code: String?): WhisperLanguage = entries.firstOrNull { it.code == code } ?: Auto
    }
}

/**
 * S-709: where the models are kept: the app's own storage that no backup takes and no other app
 * reads. A model is there whole and checked, or not at all; a download under way is its ".part"
 * beside it, with what Hugging Face said of the file (".part.json") so it goes on from there.
 */
object WhisperModels {
    private const val FOLDER = "whisper-models"

    fun folder(context: Context): File = File(context.noBackupFilesDir, FOLDER)

    fun file(context: Context, model: WhisperModel): File = File(folder(context), model.file)

    internal fun part(context: Context, model: WhisperModel): File = File(folder(context), model.file + ".part")

    internal fun partInfo(context: Context, model: WhisperModel): File = File(folder(context), model.file + ".part.json")

    /** Whether [model] is on the device, downloaded and checked. Reads the disk. */
    fun ready(context: Context, model: WhisperModel): Boolean = file(context, model).isFile

    /** What [model] and a download of it take on the device, bytes. Reads the disk. */
    fun bytesOnDisk(context: Context, model: WhisperModel): Long =
        listOf(file(context, model), part(context, model)).filter { it.isFile }.sumOf { it.length() }

    /** [model] removed from the device, with a download of it half way. Writes the disk. */
    fun delete(context: Context, model: WhisperModel): Boolean {
        val gone = listOf(file(context, model), part(context, model), partInfo(context, model)).map { !it.exists() || it.delete() }
        return gone.all { it }
    }
}

/** S-709: the subtitles from speech as Settings › Subtitles chooses them: the model and the language. */
class WhisperPrefs(private val prefs: SharedPreferences) {
    var model: WhisperModel
        get() = WhisperModel.of(prefs.getString(KEY_MODEL, null))
        set(value) = prefs.edit().putString(KEY_MODEL, value.key).apply()

    var language: WhisperLanguage
        get() = WhisperLanguage.of(prefs.getString(KEY_LANGUAGE, null))
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.code).apply()

    /** The finished job whose outcome was put away: it is not shown again. */
    var seen: String?
        get() = prefs.getString(KEY_SEEN, null)
        set(value) = prefs.edit().putString(KEY_SEEN, value).apply()

    /** The job Android's time limit for a job in the background ended (WorkManager tells it only as cancelled). */
    var timedOut: String?
        get() = prefs.getString(KEY_TIMED_OUT, null)
        set(value) = prefs.edit().putString(KEY_TIMED_OUT, value).apply()

    companion object {
        const val KEY_MODEL = "nagmedar_whisper_model"
        const val KEY_LANGUAGE = "nagmedar_whisper_language"

        /** This device's, as [KEY_TIMED_OUT]: a backup leaves them out. */
        const val KEY_SEEN = "nagmedar_whisper_seen"
        const val KEY_TIMED_OUT = "nagmedar_whisper_timed_out"
    }
}

/**
 * S-709: how many threads whisper gets: the performance cores, those of the clusters faster than
 * the slowest one (four of the Dimensity 9400+'s eight: its Cortex-X925 and three X4). A device
 * whose cores all run alike gets half of them; never more than eight.
 */
object PerformanceCores {
    fun count(): Int {
        val speeds = (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { cpu ->
            runCatching { File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull()
        }
        return threadsFor(speeds, Runtime.getRuntime().availableProcessors())
    }

    /** The threads for cores of these top [speeds] (kHz), [cores] in all. */
    fun threadsFor(speeds: List<Long>, cores: Int): Int {
        val slowest = speeds.minOrNull()
        val fast = if (slowest == null || speeds.all { it == slowest }) cores / 2 else speeds.count { it > slowest }
        return fast.coerceIn(1, MAX_THREADS)
    }

    private const val MAX_THREADS = 8
}

/** Bytes as the decimal megabytes the models' sizes are told in, rounded. */
internal fun megabytes(bytes: Long): Int = ((bytes + 500_000) / 1_000_000).toInt()

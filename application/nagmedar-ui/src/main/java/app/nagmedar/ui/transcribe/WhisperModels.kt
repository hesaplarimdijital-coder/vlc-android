package app.nagmedar.ui.transcribe

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.StringRes
import app.nagmedar.ui.R
import java.io.File

/**
 * S-709: the whisper.cpp models the transcripts can be made with, the three SP-05 measures, from
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

/** The languages a show's speech can be said to be in for its transcripts (ShowSettingsDialog): found by whisper, or said. */
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
 * S-709: where whisper runs: on the GPU through Vulkan where the device has one, else on the
 * processor ([Auto]); or on the processor alone ([CpuOnly]), which the same episode both ways
 * compares with.
 */
enum class WhisperAcceleration(val key: String, @StringRes val label: Int, @StringRes val hint: Int) {
    Auto("auto", R.string.nag_whisper_accel_auto, R.string.nag_whisper_accel_auto_hint),
    CpuOnly("cpu", R.string.nag_whisper_accel_cpu, R.string.nag_whisper_accel_cpu_hint),
    ;

    companion object {
        fun of(key: String?): WhisperAcceleration = entries.firstOrNull { it.key == key } ?: Auto
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

/**
 * S-709: the transcription from speech as Settings › Podcasts › Transcripts (Whisper) chooses it: the model and where it
 * runs; and this device's: what its GPU did last, and the jobs waiting ([WhisperQueueStore]).
 */
class WhisperPrefs(private val prefs: SharedPreferences) {
    var model: WhisperModel
        get() = WhisperModel.of(prefs.getString(KEY_MODEL, null))
        set(value) = prefs.edit().putString(KEY_MODEL, value.key).apply()

    var acceleration: WhisperAcceleration
        get() = WhisperAcceleration.of(prefs.getString(KEY_ACCELERATION, null))
        set(value) = prefs.edit().putString(KEY_ACCELERATION, value.key).apply()

    /**
     * The job whose transcription is on the GPU right now: on the disk before the GPU is asked
     * (commit, not apply: a driver that takes the app down leaves no time for a later write), and
     * cleared when it answers. Still there when a job begins: the app stopped while the GPU worked.
     */
    @set:SuppressLint("ApplySharedPref")
    var gpuRunning: String?
        get() = prefs.getString(KEY_GPU_RUNNING, null)
        set(value) {
            prefs.edit().putString(KEY_GPU_RUNNING, value).commit()
        }

    /** The app stopped while the GPU worked: jobs run on the processor until Settings tries the GPU again. */
    @set:SuppressLint("ApplySharedPref")
    var gpuStopped: Boolean
        get() = prefs.getBoolean(KEY_GPU_STOPPED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_GPU_STOPPED, value).commit()
        }

    /** The jobs waiting and the last failures, as [WhisperQueue.toJson] writes them. */
    var queue: String?
        get() = prefs.getString(KEY_QUEUE, null)
        set(value) = prefs.edit().putString(KEY_QUEUE, value).apply()

    companion object {
        const val KEY_MODEL = "nagmedar_whisper_model"
        const val KEY_ACCELERATION = "nagmedar_whisper_acceleration"

        /** This device's, as [KEY_QUEUE]: what its GPU did last. */
        const val KEY_GPU_RUNNING = "nagmedar_whisper_gpu_running"
        const val KEY_GPU_STOPPED = "nagmedar_whisper_gpu_stopped"

        /** This device's: the jobs waiting, which a backup leaves out (their episodes' ids are this device's). */
        const val KEY_QUEUE = "nagmedar_whisper_queue"

        /** 1.0: the keys of the subtitles from speech of 0.5 (their language, the last job's outcome), which a backup leaves out. */
        val REMOVED_KEYS = arrayOf("nagmedar_whisper_language", "nagmedar_whisper_seen", "nagmedar_whisper_timed_out")
    }
}

/** Bytes as the decimal megabytes the models' sizes are told in, rounded. */
internal fun megabytes(bytes: Long): Int = ((bytes + 500_000) / 1_000_000).toInt()

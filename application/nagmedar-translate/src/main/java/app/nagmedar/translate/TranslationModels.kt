package app.nagmedar.translate

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** The two ways the offline translation goes (S-710): Turkish into English and English into Turkish. */
enum class TranslationDirection(val source: String, val target: String) {
    TurkishToEnglish("tr", "en"),
    EnglishToTurkish("en", "tr"),
}

/** A file of a model: its name in the model's folder, its size and its SHA-256, pinned here. */
data class ModelFile(val name: String, val size: Long, val sha256: String)

/**
 * A translation model (S-710): an OPUS-MT model of the University of Helsinki (Helsinki-NLP),
 * converted to CTranslate2, downloaded from [baseUrl] (a Hugging Face repository at a fixed
 * commit, so its files never change under the pinned sums) into the app's private storage.
 */
data class TranslationModel(
    val id: String,
    val direction: TranslationDirection,
    /** "OPUS-MT tc-big en-tr": the model's name as its makers give it. */
    val name: String,
    /** The model's licence, as its makers give it ("CC BY 4.0", "Apache 2.0"). */
    val license: String,
    /** Where the original model is. */
    val upstream: String,
    val baseUrl: String,
    val files: List<ModelFile>,
) {
    /** The bytes to download. */
    val size: Long get() = files.sumOf { it.size }

    fun url(file: ModelFile): String = "$baseUrl/${file.name}"
}

/**
 * The models the app offers (S-710). English into Turkish is the large OPUS-MT "tc-big" model:
 * the base English-Turkic one translates Turkish much worse. Turkish into English is the base
 * OPUS-MT model, in int8: a quarter of the size, and good.
 */
object TranslationModels {
    val turkishToEnglish = TranslationModel(
        id = "opus-mt-tr-en-int8",
        direction = TranslationDirection.TurkishToEnglish,
        name = "OPUS-MT tr-en",
        license = "Apache 2.0",
        upstream = "https://huggingface.co/Helsinki-NLP/opus-mt-tr-en",
        baseUrl = "https://huggingface.co/manancode/opus-mt-tr-en-ctranslate2-android/resolve/6f3b2b349a15c6f46ecadbd3fd84d949bac65f95",
        files = listOf(
            ModelFile("model.bin", 78_209_395, "cd59fd05d819d94315b56692c9934732565cf6925594f494fdb182c7534036dc"),
            ModelFile("config.json", 223, "8f6496adfc930cbfecbe8281112197705c488fab47d34b4829b06d7f478909af"),
            ModelFile("shared_vocabulary.json", 1_263_120, "b18703ef326b780864b2fbfb66d9168b4bc6a0ec0490a7422262647967ecff79"),
            ModelFile("source.spm", 839_750, "4a6356e3ee892f2896dbf0b5cabeb51fdf16e03c70b0702cefc8b847e504241c"),
            ModelFile("target.spm", 796_647, "4cc15427e50b80e53b7dcf5f4d8a52edfe219b8d8e290c031f5578f5760b335f"),
        ),
    )

    val englishToTurkish = TranslationModel(
        id = "opus-mt-tc-big-en-tr-f16",
        direction = TranslationDirection.EnglishToTurkish,
        name = "OPUS-MT tc-big en-tr",
        license = "CC BY 4.0",
        upstream = "https://huggingface.co/Helsinki-NLP/opus-mt-tc-big-en-tr",
        baseUrl = "https://huggingface.co/ooeoeo/opus-mt-tc-big-en-tr-ct2-float16/resolve/4729dd2c625df6a5782d0a2225a36b14d52925b0",
        files = listOf(
            ModelFile("model.bin", 471_794_189, "98f9d6fb3d59f18e66ed87f26c9ef37ee0d7c00f1185402523089452497adae5"),
            ModelFile("config.json", 223, "8f6496adfc930cbfecbe8281112197705c488fab47d34b4829b06d7f478909af"),
            ModelFile("shared_vocabulary.json", 1_112_966, "19bb107d0d72fde62e5c28378da7eb1bb379a2203b9957890618d3df18ba2bb3"),
            ModelFile("source.spm", 796_953, "180d3f94ddfee5585d78b702290ded0f4f9bd40f299349c8383b32a579fefad4"),
            ModelFile("target.spm", 833_391, "cd0c475692178b6c626aadaef01bd8f98d8053946938e4e67005fc1e4811d797"),
        ),
    )

    val all: List<TranslationModel> = listOf(englishToTurkish, turkishToEnglish)

    fun of(direction: TranslationDirection): TranslationModel = all.first { it.direction == direction }
}

/**
 * Where the models are kept (S-710): a folder each under [root] (the app's private storage, left
 * out of backups). A file comes in as "<name>.part", which a download goes on from; it takes its
 * name only once its size and SHA-256 are the pinned ones. A model is ready when every file is.
 */
class ModelStore(private val root: File) {

    fun folder(model: TranslationModel): File = File(root, model.id)

    fun file(model: TranslationModel, file: ModelFile): File = File(folder(model), file.name)

    fun part(model: TranslationModel, file: ModelFile): File = File(folder(model), file.name + PART)

    /** Whether [file] is in place: verified when it came in, and still its size. */
    fun has(model: TranslationModel, file: ModelFile): Boolean = file(model, file).let { it.isFile && it.length() == file.size }

    fun isReady(model: TranslationModel): Boolean = model.files.all { has(model, it) }

    /** The bytes already here of [model]: the files in place and the parts under way. */
    fun downloaded(model: TranslationModel): Long = model.files.sumOf { file ->
        if (has(model, file)) file.size else part(model, file).takeIf { it.isFile }?.length()?.coerceAtMost(file.size) ?: 0L
    }

    /** The bytes [model] takes on the disk now. */
    fun used(model: TranslationModel): Long = folder(model).listFiles()?.sumOf { it.length() } ?: 0L

    /** [model]'s files gone, the parts too. */
    fun delete(model: TranslationModel): Boolean {
        val folder = folder(model)
        return !folder.exists() || folder.deleteRecursively()
    }

    /**
     * The finished part of [file] checked and given its name: true when its size and SHA-256 are
     * the pinned ones; else the part is removed (the next download starts it again).
     */
    fun accept(model: TranslationModel, file: ModelFile): Boolean {
        val part = part(model, file)
        val good = part.isFile && part.length() == file.size && part.inputStream().use { sha256(it) } == file.sha256
        if (!good) {
            part.delete()
            return false
        }
        val target = file(model, file)
        target.delete()
        return part.renameTo(target)
    }

    companion object {
        const val PART = ".part"

        fun sha256(input: InputStream): String {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}

package app.nagmedar.ui.podcast

import app.nagmedar.ui.transcribe.WhisperLanguage
import java.util.Locale

/**
 * The language Whisper hears a show's episodes in: the one chosen in the show's settings ("auto" for Whisper's own
 * guess), else the feed's (`language`), else Whisper's guess. A feed's tag is Whisper's when its first part is one of
 * the languages whisper.cpp knows ("tr-TR" → "tr"; the old codes "iw", "in", "nb", "jv" and "fil" as Whisper writes them).
 */
object TranscriptLanguage {
    /** The code Whisper is given for a show with [chosen] (its settings') and [feed] (its feed's tag). */
    fun choose(chosen: String?, feed: String?): String {
        if (chosen == WhisperLanguage.Auto.code) return WhisperLanguage.Auto.code
        return chosen?.let(::whisperCode) ?: feed?.let(::whisperCode) ?: WhisperLanguage.Auto.code
    }

    /** [tag]'s language as whisper.cpp names it; null for one it does not know. */
    fun whisperCode(tag: String): String? {
        val primary = tag.trim().lowercase(Locale.ROOT).substringBefore('-').substringBefore('_')
        val code = OLD_CODES[primary] ?: primary
        return code.takeIf { it in WHISPER }
    }

    private val OLD_CODES = mapOf("iw" to "he", "in" to "id", "nb" to "no", "jv" to "jw", "fil" to "tl")

    /** whisper.cpp's languages (its g_lang table). */
    private val WHISPER = setOf(
        "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr", "pl", "ca", "nl", "ar", "sv", "it", "id", "hi", "fi", "vi",
        "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "no", "th", "ur", "hr", "bg", "lt", "la", "mi", "ml", "cy", "sk",
        "te", "fa", "lv", "bn", "sr", "az", "sl", "kn", "et", "mk", "br", "eu", "is", "hy", "ne", "mn", "bs", "kk", "sq", "sw",
        "gl", "mr", "pa", "si", "km", "sn", "yo", "so", "af", "oc", "ka", "be", "tg", "sd", "gu", "am", "yi", "lo", "uz", "fo",
        "ht", "ps", "tk", "nn", "mt", "sa", "lb", "my", "bo", "tl", "mg", "as", "tt", "haw", "ln", "ha", "ba", "jw", "su", "yue",
    )
}

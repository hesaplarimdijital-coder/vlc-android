package app.nagmedar.ui.player.subtitles

import androidx.compose.runtime.Immutable
import app.nagmedar.ui.input.DropKind
import app.nagmedar.ui.input.dropKind
import java.util.Locale

/**
 * A subtitle file found beside a video (S-701): its name, where it is, the language its name
 * tells (an ISO 639-1 code: "tr" for “Kuzey Işığı.tr.srt”), whether it is the forced one (the
 * foreign lines only) or for the hard of hearing.
 */
@Immutable
data class SubtitleCandidate(
    val name: String,
    val uri: String,
    val language: String? = null,
    val forced: Boolean = false,
    val sdh: Boolean = false,
)

/** Whether a file of this name is a subtitle the player loads: SRT, ASS/SSA, VTT, SUB/IDX and their kin. */
fun isSubtitleFile(name: String): Boolean = dropKind(name, null) == DropKind.Subtitle

private fun extensionOf(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT)

private fun baseOf(name: String) = name.substringBeforeLast('.')

/** What may follow a video's name in its subtitle's: “Film.tr.srt”, “Film - TR.srt”, “Film_eng.srt”, “Film [Türkçe].srt”. */
private val separators = setOf('.', ' ', '_', '-', '[', '(')

private val tokenSplit = Regex("""[\s._\-\[\]()]+""")

/**
 * The subtitle files among [files] (their names and where they are) that go with the video
 * [video]: those named as the video is, or with a language, "forced" or "SDH" right after its
 * name (“Film 2.tr.srt” and “Film Part II.srt” are other films'). A VobSub's .sub goes with its
 * .idx, which stands for both. The ones in [prefer]'s language come first, the full ones before
 * the forced, then by name. The library's filters read the languages more simply
 * ([app.nagmedar.ui.library.Sidecars]).
 */
fun matchSubtitles(video: String, files: List<Pair<String, String>>, prefer: String? = null): List<SubtitleCandidate> {
    val videoBase = baseOf(video).lowercase(Locale.ROOT)
    if (videoBase.isEmpty()) return emptyList()
    val names = files.map { it.first.lowercase(Locale.ROOT) }.toSet()
    return files.mapNotNull { (name, uri) ->
        if (!isSubtitleFile(name)) return@mapNotNull null
        val lower = baseOf(name).lowercase(Locale.ROOT)
        val rest = when {
            lower == videoBase -> ""
            lower.startsWith(videoBase) && lower[videoBase.length] in separators -> lower.substring(videoBase.length)
            else -> return@mapNotNull null
        }
        // VobSub: the index opens its pictures
        if (extensionOf(name) == "sub" && "$lower.idx" in names) return@mapNotNull null
        val tokens = rest.split(tokenSplit).filter { it.isNotEmpty() }
        // what follows the video's name starts with what the subtitle is: “Film 2.tr.srt” is the sequel's
        val first = tokens.firstOrNull()
        if (first != null && languageOf(first) == null && first !in forcedWords && first !in sdhWords) return@mapNotNull null
        SubtitleCandidate(
            name = name,
            uri = uri,
            language = tokens.firstNotNullOfOrNull(::languageOf),
            forced = tokens.any { it in forcedWords },
            sdh = tokens.any { it in sdhWords },
        )
    }.sortedWith(
        compareBy<SubtitleCandidate>({ if (prefer != null && it.language == prefer) 0 else 1 }, { it.forced }, { it.sdh }, { it.name.lowercase(Locale.ROOT) }),
    )
}

private val forcedWords = setOf("forced", "zorunlu", "foreign")

/** Whether a track's or a file's [name] says it is the forced subtitles: "Forced", "Zorunlu", "Foreign parts only". */
fun isForcedName(name: String): Boolean = name.lowercase(Locale.ROOT).split(tokenSplit).any { it in forcedWords }

/** "hi" is the hard of hearing in a file's name, not Hindi. */
private val sdhWords = setOf("sdh", "hi", "cc", "hoh")

/** ISO 639-2 bibliographic codes that differ from the terminology ones Java knows. */
private val bibliographic = mapOf(
    "ger" to "de", "fre" to "fr", "dut" to "nl", "chi" to "zh", "cze" to "cs", "gre" to "el", "per" to "fa", "rum" to "ro",
    "slo" to "sk", "alb" to "sq", "arm" to "hy", "baq" to "eu", "bur" to "my", "geo" to "ka", "ice" to "is", "mac" to "mk",
    "mao" to "mi", "may" to "ms", "tib" to "bo", "wel" to "cy",
)

/** Languages as file names write them in Turkish: “Film.Türkçe.srt”. */
private val turkishNames = mapOf(
    "türkçe" to "tr", "turkce" to "tr", "ingilizce" to "en", "almanca" to "de", "fransızca" to "fr", "fransizca" to "fr",
    "ispanyolca" to "es", "italyanca" to "it", "rusça" to "ru", "rusca" to "ru", "arapça" to "ar", "arapca" to "ar",
    "japonca" to "ja", "korece" to "ko", "çince" to "zh", "cince" to "zh", "yunanca" to "el", "hollandaca" to "nl",
    "portekizce" to "pt", "farsça" to "fa", "farsca" to "fa",
)

/** Codes and English names of the languages Java knows: "tr", "tur", "turkish". */
private val languages: Map<String, String> by lazy {
    val known = HashMap<String, String>()
    for (code in Locale.getISOLanguages()) {
        val locale = Locale.forLanguageTag(code)
        known[code] = code
        runCatching { locale.isO3Language }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { known.putIfAbsent(it, code) }
        locale.getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT).takeIf { it.isNotEmpty() && it != code }?.let { known.putIfAbsent(it, code) }
    }
    known.putAll(bibliographic)
    known.putAll(turkishNames)
    // words that are codes too, and mean something else in a file's name: the hard of hearing, a sequel's numeral
    known.remove("hi")
    known.remove("cc")
    known.remove("ii")
    known
}

/** The language a word of a subtitle's name tells, as an ISO 639-1 code; null when it tells none. */
fun languageOf(token: String): String? = languages[token.lowercase(Locale.ROOT)]

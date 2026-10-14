package app.nagmedar.ui.radio

import java.text.Collator
import java.util.IllformedLocaleException
import java.util.Locale

/** S-1105: names of the directory's countries, languages and bitrates as the screen words them. */
object RadioLabels {

    /** The codecs the filter offers. */
    val CODECS = listOf("MP3", "AAC", "AAC+", "OGG", "FLAC", "OPUS")

    /** The lowest bitrates the filter offers, in kbit/s. */
    val BITRATES = listOf(64, 96, 128, 192, 256, 320)

    /** A country in the language of the interface ("Türkiye"); the directory's own name when the code is not known. */
    fun country(code: String?, fallback: String?, locale: Locale): String {
        val named = code?.let {
            try {
                Locale.Builder().setRegion(it).build().getDisplayCountry(locale)
            } catch (e: IllformedLocaleException) {
                ""
            }
        }.orEmpty()
        return named.takeIf { it.isNotBlank() && !it.equals(code, ignoreCase = true) } ?: fallback?.takeIf { it.isNotBlank() } ?: code.orEmpty()
    }

    /** The two-letter codes of languages by their English names in lower case ("turkish" to "tr"): the directory names languages in English. */
    private val codesByEnglishName: Map<String, String> by lazy {
        Locale.getISOLanguages().associateBy { Locale.forLanguageTag(it).getDisplayLanguage(Locale.ENGLISH).lowercase(Locale.ROOT) }
    }

    /**
     * A language in the language of the interface ("Türkçe"), from its [code] or else from the
     * directory's English [name]; the name, capitalised, when neither is a language known.
     */
    fun language(name: String, code: String?, locale: Locale): String {
        val known = code?.takeIf { it.length in 2..3 } ?: codesByEnglishName[name.trim().lowercase(Locale.ROOT)]
        val named = known?.let { Locale.forLanguageTag(it).getDisplayLanguage(locale) }.orEmpty()
        val shown = named.takeIf { it.isNotBlank() && !it.equals(known, ignoreCase = true) } ?: name
        return shown.replaceFirstChar { it.uppercase(locale) }
    }

    /** The countries in the interface's alphabet, each with its label. */
    fun countries(list: List<DirectoryCountry>, locale: Locale): List<Pair<DirectoryCountry, String>> {
        val collator = Collator.getInstance(locale)
        return list.map { it to country(it.code, it.name, locale) }.sortedWith { a, b -> collator.compare(a.second, b.second) }
    }

    /** The languages in the interface's alphabet, each with its label. */
    fun languages(list: List<DirectoryLanguage>, locale: Locale): List<Pair<DirectoryLanguage, String>> {
        val collator = Collator.getInstance(locale)
        return list.map { it to language(it.name, it.code, locale) }.sortedWith { a, b -> collator.compare(a.second, b.second) }
    }

    /** A station's place line: "Türkiye · Türkçe". */
    fun place(station: Station, locale: Locale): String = listOfNotNull(
        station.countryCode?.let { country(it, station.country, locale) } ?: station.country,
        station.language?.let { language(it, null, locale) },
    ).filter { it.isNotBlank() }.joinToString(" · ")
}

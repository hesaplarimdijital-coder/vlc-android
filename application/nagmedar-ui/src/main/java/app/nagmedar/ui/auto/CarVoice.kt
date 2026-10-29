package app.nagmedar.ui.auto

import app.nagmedar.ui.search.foldForSearch
import org.videolan.vlc.media.VoiceRequest

/** What a spoken request asks for: anything (music first, then podcasts, then radio), or one of the three. */
enum class VoiceKind { Any, Music, Podcast, Radio }

/** A spoken request's words without its command ("çal", "play") and without the kind it names ("radyosunu"). */
data class VoiceQuery(val text: String, val kind: VoiceKind) {
    val isEmpty: Boolean get() = text.isBlank()
}

/** The words of [VoiceRequest.query] without the command and the kind they name. */
val VoiceRequest.parsed: VoiceQuery get() = CarVoice.parse(query)

/** Nothing asked for ("çal", "play"): what played last plays on. */
val VoiceRequest.isEmpty: Boolean get() = focus == null && parsed.let { it.isEmpty && it.kind == VoiceKind.Any }

/**
 * 1.0: Android Auto's search and voice requests read the way people say them, in Turkish and English: "Tarkan'ı çal",
 * "Fularsız Entellik podcast'ini çal", "Radyo Eksen'i aç", "play Jazz FM radio". Words are compared folded as the app's
 * search folds them ([foldForSearch]), with a Turkish case ending after an apostrophe and the punctuation left out
 * ([fold]), and ranked by [score].
 */
object CarVoice {
    /** A request names a thing as it is. */
    const val EXACT = 4

    /** The thing's name starts with the request. */
    const val PREFIX = 3

    /** Each word of the request starts a word of the thing's name. */
    const val WORDS = 2

    /** The request is somewhere in the thing's name. */
    const val PART = 1

    /** The least a voice request takes as an answer: a word-for-word match. */
    const val ENOUGH = WORDS

    // the command at the end of a Turkish request, or at the start of an English one, folded
    private val endCommands = setOf("cal", "calsana", "calar", "oynat", "ac", "acsana", "dinle", "dinlet", "baslat", "please", "lutfen")
    private val startCommands = listOf("play", "listen to", "put on", "start", "open", "shuffle")

    // the app named in the request: "Nağmedar'da", "on Nağmedar"
    private val app = setOf("nagmedar", "nagmedarda", "nagmedardan", "nagmedarla")
    private val appLinks = setOf("on", "in", "with", "from", "ile", "uzerinden")

    // the kind a request names after its words ("Eksen radyosunu"), folded
    private val podcastWords = setOf(
        "podcast", "podcasti", "podcastini", "podcastleri", "podcastlerini", "bolum", "bolumu", "bolumunu", "episode",
    )
    private val radioWords = setOf("radyo", "radyosu", "radyosunu", "radyoyu", "radio", "station", "istasyon", "istasyonu", "istasyonunu")
    private val musicWords = setOf(
        "muzik", "muzigi", "muzigini", "music", "sarki", "sarkilar", "sarkilari", "sarkilarini", "sarkisini", "parca", "parcalari",
        "parcalarini", "parcasini", "album", "albumu", "albumunu", "songs", "song",
    )

    /**
     * The Turkish case endings a name takes after an apostrophe ("Tarkan'ı", "Eksen'i", "podcast'ini", "Nağmedar'da"),
     * folded, longest first; an English one ("Don't", "Rock'n'Roll") stays.
     */
    private val endings = listOf(
        "ndaki", "ndeki", "indan", "inden", "undan", "unden", "ndan", "nden", "daki", "deki", "taki", "teki",
        "nin", "nun", "ini", "unu", "ina", "ine", "una", "une", "nda", "nde", "dan", "den", "tan", "ten", "yla", "yle",
        "in", "un", "yi", "yu", "ni", "nu", "ya", "ye", "na", "ne", "da", "de", "ta", "te", "la", "le", "i", "u", "a", "e",
    )
    private val suffix = Regex("['’`´](?:" + endings.joinToString("|") + ")(?![\\p{L}\\p{N}])")
    private val notWord = Regex("[^\\p{L}\\p{N}]+")

    // a run of letters and digits as said, apostrophes inside ("Eksen'i", "Rock'n'Roll"); the apostrophes it is cut at
    private val wordRun = Regex("[\\p{L}\\p{M}\\p{N}]+(?:['’`´][\\p{L}\\p{M}\\p{N}]+)*")
    private val apostrophe = Regex("['’`´]")

    /** [text] for comparing: [foldForSearch]'s, without an apostrophe's Turkish ending, one space between words. */
    fun fold(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return suffix.replace(foldForSearch(text), "").replace(notWord, " ").trim()
    }

    /**
     * How well [candidate] answers [query]: [EXACT], [PREFIX], [WORDS], [PART], or 0 for not at all. Both are folded
     * first; an empty one answers nothing.
     */
    fun score(query: String?, candidate: String?): Int = foldedScore(fold(query), fold(candidate))

    /** [score] of texts already [fold]ed. */
    fun foldedScore(query: String, candidate: String): Int {
        if (query.isEmpty() || candidate.isEmpty()) return 0
        if (candidate == query) return EXACT
        if (candidate.startsWith("$query ")) return PREFIX
        val words = candidate.split(' ')
        if (query.split(' ').all { word -> words.any { it.startsWith(word) } }) return WORDS
        return if (candidate.contains(query)) PART else 0
    }

    /** The best of [items] for [query] by [name] when it answers at least [least]; the first of equals wins. */
    fun <T> best(query: String, items: Iterable<T>, least: Int = ENOUGH, name: (T) -> String?): Pair<T, Int>? {
        val folded = fold(query)
        if (folded.isEmpty()) return null
        var found: Pair<T, Int>? = null
        for (item in items) {
            val score = foldedScore(folded, fold(name(item)))
            if (score >= least && score > (found?.second ?: 0)) {
                found = item to score
                if (score == EXACT) break
            }
        }
        return found
    }

    /** [raw] as a request: the command and the app's name taken off, the kind it names at its end (or a podcast at its start) read off. */
    fun parse(raw: String?): VoiceQuery {
        val (words, kind) = request(raw)
        return VoiceQuery(words.joinToString(" ") { it.folded }, kind)
    }

    /**
     * The words of [raw] [parse] keeps, as they were said: their letters and capitals as heard ("Güneş FM"), an
     * apostrophe's Turkish ending left out. What a service that does not fold searches with (the radio directory).
     */
    fun spoken(raw: String?): String = request(raw).first.joinToString(" ") { it.said }

    /** A word of a request: as said, and [fold]ed. */
    private class Word(val said: String, val folded: String)

    /**
     * [raw]'s words: each run of letters and digits (apostrophes inside), folded as [fold] folds it; a run [fold] cuts at
     * its apostrophes ("Rock'n'Roll") gives its parts, its Turkish ending ("Eksen'i") left out of both.
     */
    private fun wordsOf(raw: String?): List<Word> {
        if (raw.isNullOrBlank()) return emptyList()
        val out = ArrayList<Word>()
        for (run in wordRun.findAll(raw)) {
            val folded = fold(run.value).split(' ').filter { it.isNotEmpty() }
            if (folded.isEmpty()) continue
            val said = run.value.split(apostrophe).filter { it.isNotEmpty() }
            if (said.size >= folded.size) {
                folded.forEachIndexed { i, word -> out += Word(said[i], word) }
            } else {
                // a folding that changed the parts: the run as said stands for its first word
                out += Word(run.value, folded.first())
                folded.drop(1).forEach { out += Word("", it) }
            }
        }
        return out
    }

    /** [raw]'s words without the command, the app's name and the kind named, and that kind. */
    private fun request(raw: String?): Pair<List<Word>, VoiceKind> {
        val words = wordsOf(raw).toMutableList()
        // "play …", "listen to …"
        startCommands.map { it.split(' ') }.firstOrNull { command -> words.size > command.size && words.subList(0, command.size).map { it.folded } == command }
            ?.let { command -> repeat(command.size) { words.removeAt(0) } }
        // "… on Nağmedar", "Nağmedar'da …", "… Nağmedar ile"
        stripApp(words)
        var kind = VoiceKind.Any
        while (words.isNotEmpty()) {
            val last = words.last().folded
            val named = when (last) {
                in podcastWords -> VoiceKind.Podcast
                in radioWords -> VoiceKind.Radio
                in musicWords -> VoiceKind.Music
                else -> null
            }
            when {
                last in endCommands -> Unit
                kind == VoiceKind.Any && named != null -> kind = named
                else -> break
            }
            words.removeAt(words.lastIndex)
            stripApp(words)
        }
        // "the podcast …", "podcast …": English names the kind first
        if (kind == VoiceKind.Any) {
            val lead = if (words.firstOrNull()?.folded == "the") 1 else 0
            if (words.getOrNull(lead)?.folded == "podcast" && words.size > lead + 1) {
                repeat(lead + 1) { words.removeAt(0) }
                kind = VoiceKind.Podcast
            }
        }
        return words to kind
    }

    private fun stripApp(words: MutableList<Word>) {
        if (words.isEmpty()) return
        if (words.last().folded in app) {
            words.removeAt(words.lastIndex)
            if (words.isNotEmpty() && words.last().folded in appLinks) words.removeAt(words.lastIndex)
        } else if (words.size >= 2 && words[words.lastIndex - 1].folded in app && words.last().folded in appLinks) {
            words.removeAt(words.lastIndex)
            words.removeAt(words.lastIndex)
        }
        if (words.isNotEmpty() && words.first().folded in app) words.removeAt(0)
    }
}

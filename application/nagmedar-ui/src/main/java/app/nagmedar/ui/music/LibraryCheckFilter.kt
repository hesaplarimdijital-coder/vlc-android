package app.nagmedar.ui.music

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import app.nagmedar.audio.cue.CueUri
import app.nagmedar.tags.artwork.LibraryCoverState
import app.nagmedar.tags.quality.LibraryQualityState
import app.nagmedar.ui.R
import app.nagmedar.ui.quality.LibraryChecks
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate

/**
 * 1.4.0, the filter's "Denetim": Metamatik's lists of what to put right in the library, from what its checks found
 * ([LibraryChecks]): the songs lacking an essential tag, the lossless files made from lossy ones, the files without a
 * cover, the songs whose text looks garbled. Several chosen let through the songs on any of the lists.
 */
enum class LibraryCheck(@StringRes val label: Int) {
    NeedsFix(R.string.nag_check_needs_fix),
    Fake(R.string.nag_check_fake),
    NoCover(R.string.nag_check_no_cover),
    Garbled(R.string.nag_check_garbled),
}

/** What the library's checks found, as the filter reads it: by the files' URIs. */
class CheckFindings(val quality: LibraryQualityState = LibraryQualityState(), val tags: LibraryCoverState = LibraryCoverState()) {
    /** Whether [track] is on [check]'s list; a CUE sheet's track by its sheet's file. */
    fun has(check: LibraryCheck, track: Track): Boolean {
        val uri = CueUri.file(track.uri)
        return when (check) {
            LibraryCheck.NeedsFix -> uri in tags.needsAttention
            LibraryCheck.Fake -> quality.qualities[uri]?.isFake == true
            LibraryCheck.NoCover -> uri in tags.withoutCover
            LibraryCheck.Garbled -> uri in tags.garbled
        }
    }

    /** How many of [tracks] are on [check]'s list. */
    fun count(check: LibraryCheck, tracks: List<Track>): Int = tracks.count { has(check, it) }

    /** Whether the checks have files still to look at. */
    val running: Boolean get() = quality.isRunning || tags.isRunning
}

/** [checks]' lists of this library's tracks: the library itself when none is chosen. */
fun MusicLibrary.checked(checks: Set<LibraryCheck>, findings: CheckFindings): MusicLibrary =
    if (checks.isEmpty()) this else MusicLibrary(tracks.filter { track -> checks.any { findings.has(it, track) } }, locale)

/**
 * [library] through the filter's [checks], made again as the checks find more, every few seconds at most: a list being
 * read while they work is not made again for every file.
 */
@Composable
internal fun rememberCheckedLibrary(library: MusicLibrary, checks: Set<LibraryCheck>): MusicLibrary {
    if (checks.isEmpty()) return library
    val found = currentCheckFindings()
    return remember(library, checks, found) { library.checked(checks, found) }
}

/** The tests' findings (fakes); the app's are [LibraryChecks]'. */
val LocalCheckFindings = staticCompositionLocalOf<CheckFindings?> { null }

/** What the library's checks found, as the page reads it now. */
@Composable
internal fun currentCheckFindings(): CheckFindings = LocalCheckFindings.current ?: rememberCheckFindings().value

/** What the library's checks found, read again every few seconds at most while they work. */
@Composable
internal fun rememberCheckFindings(): State<CheckFindings> {
    val quality = LibraryChecks.quality
    val tags = LibraryChecks.tags
    return produceState(CheckFindings(quality.value, tags.value), quality, tags) {
        combine(quality, tags) { q, t -> CheckFindings(q, t) }.conflate().collect { found ->
            value = found
            delay(FINDINGS_EVERY_MS)
        }
    }
}

/** The filtered list is made again this often at most while the checks find more. */
private const val FINDINGS_EVERY_MS = 3_000L

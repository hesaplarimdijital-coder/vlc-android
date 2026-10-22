package app.nagmedar.ui.library.metadata

import android.content.Context
import android.widget.Toast
import app.nagmedar.data.FilmKind
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.videolan.tools.Settings

/** How the series are shown: today's cards, posters, or the dense list. */
enum class SeriesView { Grid, Poster, List }

/** The view each library section was last shown in ("Izgara", "Afiş", "Liste"), kept per section. */
object LibraryViews {
    const val FILMS = "nagmedar_films_view"
    const val SERIES = "nagmedar_series_view"

    /** The view saved under [key] among [entries]; null when none is (the section's own default then). */
    fun <T : Enum<T>> read(context: Context, key: String, entries: List<T>): T? {
        val saved = runCatching { Settings.getInstance(context).getString(key, null) }.getOrNull() ?: return null
        return entries.firstOrNull { it.name == saved }
    }

    fun save(context: Context, key: String, view: Enum<*>) {
        runCatching { Settings.getInstance(context).edit().putString(key, view.name).apply() }
    }
}

/**
 * The film information's entries of a film's or a series' menu: "Bilgileri yenile" and "Yanlış film
 * mi? Ara…" ([onFix] opens the search); none in a build without a TMDB key.
 */
fun filmMenuEntries(context: Context, query: FilmQuery?, scope: CoroutineScope, onFix: (FilmQuery) -> Unit): List<MenuEntry> {
    if (query == null || !FilmInfoCenter.tmdbAvailable) return emptyList()
    val res = context.resources
    return listOf(
        MenuEntry.Item(res.getString(R.string.nag_film_refresh), {
            scope.launch {
                val outcome = FilmInfoCenter.refresh(context, query)
                Toast.makeText(context, outcomeText(context, outcome), Toast.LENGTH_SHORT).show()
            }
        }, icon = NagIcons.Resume),
        MenuEntry.Item(
            res.getString(if (query.kind == FilmKind.TV) R.string.nag_series_fix else R.string.nag_film_fix),
            { onFix(query) },
            icon = NagIcons.Search,
        ),
    )
}

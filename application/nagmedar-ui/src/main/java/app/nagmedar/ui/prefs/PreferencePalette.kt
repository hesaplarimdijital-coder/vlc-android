package app.nagmedar.ui.prefs

import android.content.Context
import androidx.annotation.WorkerThread
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.search.PaletteItem
import app.nagmedar.ui.search.PaletteKind
import org.videolan.tools.Settings

/**
 * The command palette's lines for "All settings": each shown preference of every page, found by its
 * title, with its page under it; [open] takes its key and shows it on its page. It reads the XML, so
 * it runs away from the main thread.
 */
@WorkerThread
fun preferencePaletteItems(context: Context, open: (String) -> Unit): List<PaletteItem> {
    val pages = PreferenceCatalog.load(context.resources)
    val values = PreferenceValues(Settings.getInstance(context))
    val allSettings = context.getString(R.string.nag_prefs_title)
    return pages.values.flatMap { page ->
        page.sections.flatMap { section ->
            section.items
                .filter { it.key != null && it.title.isNotBlank() && !PreferenceCatalog.isPage(it.key) && PreferenceCatalog.visible(it, values) }
                .map { item ->
                    val key = item.key!!
                    PaletteItem(
                        id = "preference:$key",
                        kind = PaletteKind.Setting,
                        label = item.title,
                        detail = context.getString(R.string.nag_prefs_path, allSettings, section.title ?: page.title),
                        icon = NagIcons.Settings,
                        run = { open(key) },
                    )
                }
        }
    }.distinctBy { it.id }
}

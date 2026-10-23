package app.nagmedar.ui.screens

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.lab.ReportDialog
import app.nagmedar.ui.library.metadata.FilmAttribution
import app.nagmedar.ui.search.foldForSearch
import app.nagmedar.ui.shell.BrandMark
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.videolan.vlc.BuildConfig

/** A licence of the list S-005 keeps (res/raw/libraries.json). */
@Immutable
data class License(val id: String, val name: String, val text: String, val link: String)

/** A library the app is built with, and its licence. */
@Immutable
data class ThirdParty(val title: String, val copyright: String, val license: License?)

/** S-005's list: the libraries as listed, each with its licence. */
fun readThirdParties(json: String): List<ThirdParty> {
    val root = JSONObject(json)
    val licenseArray = root.getJSONArray("licenses")
    val licenses = (0 until licenseArray.length()).map { licenseArray.getJSONObject(it) }.associate { entry ->
        val id = entry.getString("id")
        id to License(id, entry.optString("name"), entry.optString("description"), entry.optString("link"))
    }
    val libraries = root.getJSONArray("libraries")
    return (0 until libraries.length()).map { libraries.getJSONObject(it) }.map { entry ->
        ThirdParty(entry.getString("title"), entry.optString("copyright"), licenses[entry.optString("license")])
    }
}

/**
 * Whether [query] names the library, its copyright or its licence, as listed or as its chip names
 * it ([licenseLabel]); folded the Turkish way, so "kamu mali" finds "Kamu malı".
 */
internal fun ThirdParty.matches(query: String, licenseLabel: String? = null): Boolean {
    val q = foldForSearch(query).trim()
    if (q.isEmpty()) return true
    return listOfNotNull(title, copyright, license?.name, license?.id, licenseLabel).any { foldForSearch(it).contains(q) }
}

/** "Apache 2.0 license" → "Apache 2.0", "LGPL v2.1 or later" → "LGPL v2.1+": a licence's name, short. */
internal fun compactLicenseName(name: String): String =
    name.trim().replace(Regex("""\s+licen[cs]e$""", RegexOption.IGNORE_CASE), "").replace(Regex("""\s+or later$"""), "+")

/**
 * How a licence's chip names it (S-1501): short, and in the interface's language where the name
 * has words of its own ("Kamu malı", "3 maddeli BSD"); the others are names, the same in both.
 */
@Composable
@ReadOnlyComposable
internal fun licenseLabel(license: License): String = when (license.id) {
    "bsd3" -> stringResource(R.string.nag_license_bsd3)
    // S-710: cpuinfo and darts-clone; the OPUS-MT model
    "bsd2" -> stringResource(R.string.nag_license_bsd2)
    "ccby4" -> "CC BY 4.0"
    "gsm" -> stringResource(R.string.nag_license_gsm)
    "harfbuzz" -> stringResource(R.string.nag_license_old_mit)
    "publicdomain" -> stringResource(R.string.nag_license_public_domain)
    "boost" -> "Boost 1.0"
    else -> compactLicenseName(license.name)
}

@Composable
fun rememberThirdParties(): List<ThirdParty>? {
    val resources = LocalContext.current.resources
    return produceState<List<ThirdParty>?>(null, resources) {
        value = withContext(Dispatchers.IO) { runCatching { readThirdParties(resources.readLibraries()) }.getOrDefault(emptyList()) }
    }.value
}

private fun Resources.readLibraries(): String = openRawResource(R.raw.libraries).bufferedReader().use { it.readText() }

/** What the about page shows of this build. */
@Immutable
data class AboutFacts(
    val version: String = BuildConfig.VLC_VERSION_NAME,
    val libvlc: String = BuildConfig.LIBVLC_VERSION,
    val medialibrary: String = BuildConfig.ML_VERSION,
)

/**
 * About and licences (S-209, the face of S-005): the version with libVLC's and the medialibrary's,
 * that it is free software under the GPL with the source code, the GPL itself and where to report
 * a problem; who made it; and every library it is built with and its licence, to be searched and
 * opened. Side by side on wide windows, one column on a phone.
 */
@Composable
fun AboutScreen(
    libraries: List<ThirdParty>?,
    onOpenLink: (String) -> Unit,
    modifier: Modifier = Modifier,
    facts: AboutFacts = AboutFacts(),
) {
    val dimens = NagTheme.dimens
    var query by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var gplShown by rememberSaveable { mutableStateOf(false) }
    val labels = libraries.orEmpty().mapNotNull { it.license }.distinctBy { it.id }.associate { it.id to licenseLabel(it) }
    val shown = remember(libraries, query, labels) { libraries.orEmpty().filter { it.matches(query, labels[it.license?.id]) } }
    val toggle: (String) -> Unit = { title -> expanded = if (title in expanded) expanded - title else expanded + title }
    val wide = currentWidthClass() >= WidthClass.Expanded
    val sourceUrl = stringResource(R.string.nagmedar_source_url)
    val about: @Composable () -> Unit = {
        AboutHero(facts)
        FreeSoftware(
            onSource = { onOpenLink(sourceUrl) },
            onGpl = { gplShown = true },
            onIssues = { onOpenLink("$sourceUrl/issues") },
        )
        People(onAll = { onOpenLink("$sourceUrl/graphs/contributors") })
        // film information: where the posters, overviews and ratings come from, as TMDB's terms ask
        AboutCard(stringResource(R.string.nag_film_settings)) { FilmAttribution() }
    }
    if (wide) {
        Row(modifier.fillMaxSize().padding(horizontal = dimens.pagePadding)) {
            Column(
                Modifier.widthIn(max = 420.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = dimens.gapL),
                verticalArrangement = Arrangement.spacedBy(dimens.gapL),
            ) { about() }
            Spacer(Modifier.width(dimens.gapXl))
            LazyColumn(Modifier.weight(1f).fillMaxHeight().testTag("about-libraries"), contentPadding = PaddingValues(vertical = dimens.gapL)) {
                libraryItems(libraries, shown, query, { query = it }, expanded, toggle, onOpenLink)
            }
        }
    } else {
        LazyColumn(
            modifier.fillMaxSize().testTag("about-libraries"),
            contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapL),
        ) {
            item(key = "about") {
                Column(verticalArrangement = Arrangement.spacedBy(dimens.gapL), modifier = Modifier.padding(bottom = dimens.gapXl)) { about() }
            }
            libraryItems(libraries, shown, query, { query = it }, expanded, toggle, onOpenLink)
        }
    }
    if (gplShown) {
        val gpl = libraries?.firstNotNullOfOrNull { it.license?.takeIf { license -> license.id == "gpl2" } }
        ReportDialog(stringResource(R.string.nag_about_gpl_title), gpl?.text.orEmpty()) { gplShown = false }
    }
}

/** The libraries' heading, the field that searches them, and the list. */
private fun LazyListScope.libraryItems(
    libraries: List<ThirdParty>?,
    shown: List<ThirdParty>,
    query: String,
    onQuery: (String) -> Unit,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    item(key = "libraries-heading") {
        Column(Modifier.padding(bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stringResource(R.string.nag_about_libraries_title),
                    style = NagTheme.type.title,
                    color = NagTheme.colors.text,
                    modifier = Modifier.semantics { heading() },
                )
                if (libraries != null) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        pluralStringResource(R.plurals.nag_about_libraries_count, libraries.size, libraries.size),
                        style = NagTheme.type.labelSmall,
                        color = NagTheme.colors.textTertiary,
                    )
                }
            }
            Text(stringResource(R.string.nag_about_libraries_hint), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
            Spacer(Modifier.height(8.dp))
            FilterField(query, onQuery)
        }
    }
    if (libraries != null && shown.isEmpty()) {
        item(key = "none") {
            Text(stringResource(R.string.nag_about_none_found), style = NagTheme.type.body, color = NagTheme.colors.textSecondary,
                modifier = Modifier.padding(vertical = 16.dp))
        }
    }
    items(shown, key = { it.title }) { library ->
        LibraryRow(library, library.title in expanded, { onToggle(library.title) }, onOpenLink)
    }
}

@Composable
private fun AboutHero(facts: AboutFacts) {
    val colors = NagTheme.colors
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BrandMark(Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.nagmedar_shell_title), style = NagTheme.type.display, color = colors.text)
                Text(
                    stringResource(R.string.nag_about_versions, facts.version, facts.libvlc, facts.medialibrary),
                    style = NagTheme.type.mono,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.nag_about_tagline), style = NagTheme.type.body, color = colors.textSecondary)
    }
}

/** A card of the page: a heading, its words, its buttons. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutCard(title: String, actions: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.surface, shape)
            .border(1.dp, colors.border, shape)
            .padding(dimens.gapL),
    ) {
        Text(title, style = NagTheme.type.title, color = colors.text, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(dimens.gapS))
        content()
        if (actions != null) {
            Spacer(Modifier.height(dimens.gapM))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) { actions() }
        }
    }
}

@Composable
private fun FreeSoftware(onSource: () -> Unit, onGpl: () -> Unit, onIssues: () -> Unit) {
    AboutCard(
        stringResource(R.string.nag_about_free_title),
        actions = {
            NagButton(stringResource(R.string.nag_about_source), onSource, primary = true)
            NagButton(stringResource(R.string.nag_about_gpl), onGpl)
            NagButton(stringResource(R.string.nag_about_issues), onIssues)
        },
    ) {
        Text(stringResource(R.string.nag_about_free_text), style = NagTheme.type.body, color = NagTheme.colors.textSecondary)
    }
}

@Composable
private fun People(onAll: () -> Unit) {
    val colors = NagTheme.colors
    AboutCard(
        stringResource(R.string.nag_about_people_title),
        actions = { NagButton(stringResource(R.string.nag_about_all_contributors), onAll) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Person(stringResource(R.string.nag_about_people_vlc), stringResource(R.string.nag_about_people_vlc_desc))
            Person(stringResource(R.string.nag_about_people_nagmedar), stringResource(R.string.nag_about_people_nagmedar_desc))
            HorizontalRule()
            Text(stringResource(R.string.nag_about_copyright), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        }
    }
}

@Composable
private fun Person(name: String, what: String) {
    Column {
        Text(name, style = NagTheme.type.bodyStrong, color = NagTheme.colors.text)
        Text(what, style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
    }
}

/** A one-line search field with its magnifier and a clear button; the libraries' filter, and the guide's and the shortcut card's (S-1504). */
@Composable
internal fun FilterField(
    query: String,
    onQuery: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = stringResource(R.string.nag_about_filter),
) {
    val focusManager = LocalFocusManager.current
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTarget.coerceAtLeast(40.dp))
            .background(colors.surface, shape)
            .border(1.dp, colors.borderStrong, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NagIcons.Search, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text(placeholder, style = NagTheme.type.body, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                textStyle = NagTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth().tabMovesFocus(focusManager).semantics { contentDescription = placeholder },
            )
        }
        if (query.isNotEmpty()) NagIconButton(NagIcons.Close, stringResource(R.string.nag_search_clear), { onQuery("") })
    }
}

/** A library: its name, its copyright and its licence; opened, the licence's text. */
@Composable
private fun LibraryRow(library: ThirdParty, open: Boolean, onToggle: () -> Unit, onOpenLink: (String) -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val license = library.license
    val opened = stringResource(R.string.nag_about_opened)
    val closed = stringResource(R.string.nag_about_closed)
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTarget.coerceAtLeast(48.dp))
                .focusRing(state, shape)
                .background(stateBackground(state, open), shape)
                .nagClickable(interaction, onToggle, onClickLabel = library.title)
                .semantics { stateDescription = if (open) opened else closed }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(library.title, style = NagTheme.type.bodyStrong, color = if (open) colors.onSelection else colors.text, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                if (library.copyright.isNotBlank()) {
                    Text(library.copyright, style = NagTheme.type.labelSmall, color = if (open) colors.onSelection else colors.textTertiary,
                        maxLines = if (open) 4 else 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (license != null) {
                Spacer(Modifier.width(8.dp))
                LicenseChip(licenseLabel(license))
            }
            Icon(
                if (open) NagIcons.ChevronUp else NagIcons.ChevronDown,
                contentDescription = null,
                tint = if (open) colors.onSelection else colors.textTertiary,
                modifier = Modifier.padding(start = 6.dp).size(dimens.icon),
            )
        }
        if (open && license != null) {
            Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 12.dp)) {
                if (license.text.isBlank()) {
                    Text(stringResource(R.string.nag_about_no_text), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .background(colors.surfaceRaised, shape)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    ) {
                        Text(license.text.trim(), style = NagTheme.type.mono, color = colors.textSecondary)
                    }
                }
                if (license.link.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    NagButton(stringResource(R.string.nag_about_full_license), { onOpenLink(license.link) }, icon = NagIcons.OpenWindow)
                }
            }
        }
        HorizontalRule(Modifier.padding(horizontal = 10.dp))
    }
}

@Composable
private fun LicenseChip(name: String) {
    val colors = NagTheme.colors
    Box(
        Modifier
            .border(1.dp, colors.borderStrong, RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(name, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

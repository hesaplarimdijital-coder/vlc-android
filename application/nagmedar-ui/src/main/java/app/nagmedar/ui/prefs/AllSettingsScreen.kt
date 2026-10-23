package app.nagmedar.ui.prefs

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.LoadingState
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSlider
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.NagTextField
import app.nagmedar.ui.components.NoResultsState
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.search.foldForSearch
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.tools.Settings
import kotlin.math.roundToInt

/**
 * "All settings": every page of VLC's preferences drawn as Nağmedar's settings, from the same XML
 * the classic screen read (S-1601, [PreferenceCatalog]), with its pages under one another (Back
 * goes up), a search across all of them on the first page, and VLC's behaviour behind each
 * change ([AllSettingsController]). The lines Nağmedar's own settings have open them ([onLink]).
 * [requestedKey] opens the page of that preference (the player's "metered connection" question,
 * the palette) and is cleared with [onRequestHandled]. With the settings behind the parental
 * control's PIN, the PIN is asked first and [onClose] leaves when it is not given.
 */
@Composable
fun AllSettingsScreen(
    onLink: (NagmedarLink) -> Unit,
    onClose: () -> Unit,
    requestedKey: String? = null,
    onRequestHandled: () -> Unit = {},
) {
    val context = LocalContext.current
    val values = remember(context) { PreferenceValues(Settings.getInstance(context)) }
    DisposableEffect(values) {
        values.attach()
        onDispose { values.detach() }
    }
    // read once in a language: opened again, the pages are there in the first frame
    val pages by produceState(PreferenceCatalog.loaded(context.resources), context) {
        if (value == null) value = withContext(Dispatchers.Default) { PreferenceCatalog.read(context.resources) }
    }
    val loaded = pages
    if (loaded == null) {
        LoadingState(stringResource(R.string.nag_prefs_loading), Modifier.fillMaxWidth(), posters = 0)
        return
    }
    val controller = remember(loaded) { AllSettingsController(context.applicationContext, values, loaded) }
    AllSettingsPages(controller, onLink, onClose, requestedKey, onRequestHandled)
}

@Composable
private fun AllSettingsPages(
    controller: AllSettingsController,
    onLink: (NagmedarLink) -> Unit,
    onClose: () -> Unit,
    requestedKey: String?,
    onRequestHandled: () -> Unit,
) {
    val context = LocalContext.current
    val dimens = NagTheme.dimens
    // the pages opened, "/" between their ids; the first page is not in it
    var path by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var unlocked by rememberSaveable { mutableStateOf(false) }
    val stack = path.split('/').filter { it.isNotEmpty() }
    val pageId = stack.lastOrNull() ?: PreferenceCatalog.ROOT
    val page = controller.pages[pageId] ?: controller.pages.getValue(PreferenceCatalog.ROOT)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val parents = remember(controller) { parentsOf(controller.pages) }
    val close by rememberUpdatedState(onClose)

    // PreferencesActivity asked for the PIN before showing anything when the settings are restricted
    LaunchedEffect(controller) {
        if (controller.restricted && !unlocked) {
            controller.dialog = PrefDialog.Pin(PinMode.Check, onDone = { unlocked = true }, onCancel = { close() })
        }
    }

    fun open(id: String) {
        path = (stack + id).joinToString("/")
        scope.launch { listState.scrollToItem(0) }
    }

    fun up() {
        path = stack.dropLast(1).joinToString("/")
    }

    BackHandler(enabled = stack.isNotEmpty()) { up() }
    // the subtitles' look is applied when their page is left, also by leaving the screen
    DisposableEffect(pageId) { onDispose { controller.left(pageId) } }

    LaunchedEffect(requestedKey) {
        val key = requestedKey ?: return@LaunchedEffect
        val target = controller.pages.values.firstOrNull { p -> p.items.any { it.key == key } }
        if (target != null) {
            val chain = generateSequence(target.id) { parents[it] }.takeWhile { it != PreferenceCatalog.ROOT }.toList().reversed()
            query = ""
            path = chain.joinToString("/")
            val shown = target.sections.filter { s -> s.items.any { PreferenceCatalog.visible(it, controller.values) } }
            val section = shown.indexOfFirst { s -> s.items.any { it.key == key } }
            listState.scrollToItem((section + 1).coerceAtLeast(0))
        } else {
            // a key of a page left out, which Nağmedar's own settings have (the remote access switch)
            PreferenceCatalog.linkFor(key)?.let(onLink)
        }
        onRequestHandled()
    }

    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> controller.picked(PrefFile.SoundFont, uri) }
    val mediaDb = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        controller.picked(PrefFile.MediaDatabase, uri)
    }
    val appDb = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        controller.picked(PrefFile.AppDatabase, uri)
    }
    LaunchedEffect(controller.fileRequest) {
        val file = controller.fileRequest ?: return@LaunchedEffect
        controller.fileRequest = null
        runCatching {
            when (file) {
                PrefFile.SoundFont -> files.launch(arrayOf("audio/sf2", "audio/x-soundfont", "application/octet-stream", "*/*"))
                PrefFile.MediaDatabase -> mediaDb.launch(controller.suggestedName(file))
                PrefFile.AppDatabase -> appDb.launch(controller.suggestedName(file))
            }
        }
    }

    val onItem: (PrefItem) -> Unit = { item ->
        val key = item.key
        val link = key?.let { PreferenceCatalog.links[it] }
        when {
            link != null -> onLink(link)
            // the parental control's page asks for a PIN to be made first (PreferencesFragment)
            key == PreferenceCatalog.PARENTAL -> if (controller.pinSet) {
                open(PreferenceCatalog.PARENTAL)
            } else {
                controller.dialog = PrefDialog.Pin(PinMode.Create, onDone = { open(PreferenceCatalog.PARENTAL) })
            }
            key != null && PreferenceCatalog.isPage(key) -> open(key)
            else -> controller.click(item)
        }
    }

    val searching = stack.isEmpty() && query.isNotBlank()
    val hits = if (searching) search(controller, query, context) else emptyList()
    val sections = if (searching) {
        emptyList()
    } else {
        page.sections.mapNotNull { section ->
            val shown = section.items.filter { PreferenceCatalog.visible(it, controller.values) }
            val categoryShown = section.key == null || PreferenceCatalog.visible(PrefItem(PrefKind.Info, section.key, ""), controller.values)
            if (shown.isEmpty() || !categoryShown) null else section to shown
        }
    }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("all-settings"),
            contentPadding = PaddingValues(bottom = dimens.gapXl),
        ) {
            if (stack.isEmpty()) {
                item(key = "header") {
                    Column {
                        PageHeader(title = stringResource(R.string.nag_prefs_title))
                        Box(Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs)) {
                            Box(Modifier.widthIn(max = 760.dp)) {
                                NagTextField(
                                    query,
                                    { query = it },
                                    stringResource(R.string.nag_prefs_search),
                                    placeholder = stringResource(R.string.nag_prefs_search_hint),
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "header") { SubPageHeader(page.title, onBack = ::up) }
            }
            if (searching) {
                if (hits.isEmpty()) {
                    item(key = "none") {
                        NoResultsState(
                            title = stringResource(R.string.nag_prefs_no_results, query.trim()),
                            modifier = Modifier.fillMaxWidth().padding(dimens.gapXl),
                        )
                    }
                } else {
                    items(hits, key = { "${it.page.id}/${it.item.key}" }) { hit ->
                        PrefCard(null) {
                            Text(
                                stringResource(R.string.nag_prefs_path, hit.page.title, hit.section ?: hit.page.title),
                                style = NagTheme.type.labelSmall,
                                color = NagTheme.colors.textTertiary,
                            )
                            PrefLine(hit.item, controller, onItem)
                        }
                    }
                }
            } else {
                items(sections) { (section, shown) ->
                    PrefCard(section.title) {
                        shown.forEach { item -> PrefLine(item, controller, onItem) }
                    }
                }
            }
        }
    }
    PreferenceDialogHost(controller)
}

/** A sub-page's title with the way up. */
@Composable
private fun SubPageHeader(title: String, onBack: () -> Unit) {
    val dimens = NagTheme.dimens
    Row(
        Modifier.fillMaxWidth().padding(start = dimens.pagePadding - dimens.gapS, end = dimens.pagePadding, top = dimens.gapS, bottom = dimens.gapXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_prefs_back), onBack)
        Spacer(Modifier.width(dimens.gapXs))
        Text(
            title,
            style = NagTheme.type.title,
            color = NagTheme.colors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** A category of a page: Settings' card, with the category's title when it has one. */
@Composable
private fun PrefCard(title: String?, content: @Composable ColumnScope.() -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    Box(Modifier.fillMaxWidth().padding(horizontal = dimens.pagePadding, vertical = dimens.gapS)) {
        Column(
            Modifier
                .widthIn(max = 760.dp)
                .fillMaxWidth()
                .background(colors.surface, shape)
                .border(1.dp, colors.border, shape)
                .padding(horizontal = dimens.gapL, vertical = dimens.gapM),
        ) {
            if (title != null) {
                Text(title, style = NagTheme.type.title, color = colors.text, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(dimens.gapS))
                HorizontalRule()
                Spacer(Modifier.height(dimens.gapXs))
            }
            content()
        }
    }
}

/** One preference: its title and summary, and the control its kind asks for. */
@Composable
private fun PrefLine(item: PrefItem, controller: AllSettingsController, onItem: (PrefItem) -> Unit) {
    val context = LocalContext.current
    val values = controller.values
    val enabled = PreferenceCatalog.enabled(item, values, controller.items, context)
    val summary = PreferenceCatalog.summary(item, values, context)
    val faded = Modifier.alpha(if (enabled) 1f else DISABLED_ALPHA)
    when (item.kind) {
        PrefKind.Toggle -> SettingRow(item.title, faded, subtitle = summary) {
            val on = values.checked(item)
            NagSwitch(on, { controller.toggle(item, it) }, stringResource(if (on) R.string.nag_prefs_on else R.string.nag_prefs_off), enabled = enabled)
        }
        PrefKind.Choice -> SettingRow(item.title, faded, subtitle = summary.takeIf { item.summary?.contains("%s") != true }) {
            val label = if (item.persistent) PreferenceCatalog.choiceLabel(item, values, context) else null
            NagButton(label ?: stringResource(R.string.nag_prefs_choose), { controller.dialog = PrefDialog.Choice(item) }, icon = NagIcons.ChevronDown, enabled = enabled)
        }
        PrefKind.MultiChoice -> SettingRow(item.title, faded, subtitle = summary) {
            val chosen = values.selection(item)
            val label = item.entryValues.zip(item.entries).filter { it.first in chosen }.joinToString(", ") { it.second }
            NagButton(label.ifEmpty { stringResource(R.string.nag_prefs_none) }, { controller.dialog = PrefDialog.MultiChoice(item) }, icon = NagIcons.ChevronDown, enabled = enabled)
        }
        PrefKind.Text -> SettingRow(item.title, faded, subtitle = summary.takeIf { !item.simpleSummary }) {
            val text = values.text(item)
            NagButton(text.ifEmpty { stringResource(R.string.nag_prefs_not_set) }, { controller.dialog = PrefDialog.Text(item) }, enabled = enabled)
        }
        PrefKind.Number -> SettingRow(item.title, faded) {
            NagButton(summary.orEmpty(), { controller.dialog = PrefDialog.Number(item) }, enabled = enabled)
        }
        PrefKind.Slider -> SliderLine(item, controller, summary, enabled, faded)
        PrefKind.Colour -> SettingRow(item.title, faded, subtitle = hexOf(values.number(item))) {
            Swatch(values.number(item), selected = false) { if (enabled) controller.dialog = PrefDialog.Colour(item) }
        }
        PrefKind.Link -> LinkLine(item, linkSummary(item) ?: summary, enabled) { onItem(item) }
        PrefKind.Info -> Text(
            summary.orEmpty(),
            style = NagTheme.type.labelSmall,
            color = NagTheme.colors.textTertiary,
            modifier = Modifier.padding(vertical = NagTheme.dimens.gapS),
        )
    }
}

/** Where a line leads in Nağmedar's own settings, said under its title. */
@Composable
private fun linkSummary(item: PrefItem): String? = when (PreferenceCatalog.links[item.key]) {
    NagmedarLink.Library -> stringResource(R.string.nag_prefs_in_settings, stringResource(R.string.nag_section_library))
    NagmedarLink.Appearance -> stringResource(R.string.nag_prefs_in_settings, stringResource(R.string.nag_settings_appearance))
    NagmedarLink.RemoteAccess -> stringResource(R.string.nag_prefs_in_settings, stringResource(R.string.nag_ra_settings))
    NagmedarLink.Backup -> stringResource(R.string.nag_prefs_in_settings, stringResource(R.string.nag_backup_settings))
    NagmedarLink.Equalizer -> stringResource(R.string.nag_prefs_opens_page, stringResource(R.string.nag_eq_title))
    NagmedarLink.About -> stringResource(R.string.nag_prefs_opens_page, stringResource(R.string.nag_settings_about))
    null -> null
}

@Composable
private fun SliderLine(item: PrefItem, controller: AllSettingsController, summary: String?, enabled: Boolean, modifier: Modifier) {
    val values = controller.values
    val span = (item.max - item.min).coerceAtLeast(1)
    val current = values.number(item).coerceIn(item.min, item.max)
    Column(modifier.padding(vertical = NagTheme.dimens.gapXs)) {
        SettingRow(item.title, subtitle = summary.takeIf { item.summary?.contains("%s") != true }) {
            Text(
                if (item.summary?.contains("%s") == true) summary.orEmpty() else current.toString(),
                style = NagTheme.type.label,
                color = NagTheme.colors.textSecondary,
            )
        }
        NagSlider(
            value = (current - item.min).toFloat() / span,
            onValueChange = { fraction ->
                val next = item.min + (fraction * span).roundToInt()
                if (next != current) controller.setNumber(item, next)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            wheelStep = 1f / span,
            keyStep = 1f / span,
        )
    }
}

@Composable
private fun LinkLine(item: PrefItem, summary: String?, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTarget.coerceAtLeast(dimens.rowHeight))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onClick, enabled = enabled)
            .alpha(if (enabled) 1f else DISABLED_ALPHA)
            .padding(vertical = dimens.gapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = NagTheme.type.bodyStrong, color = colors.text)
            if (summary != null) Text(summary, style = NagTheme.type.labelSmall, color = colors.textTertiary)
        }
        Icon(NagIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
    }
}

private const val DISABLED_ALPHA = 0.45f

/** A preference found by the search: its page, its category and itself. */
private class PrefHit(val page: PrefPage, val section: String?, val item: PrefItem)

/** Every shown preference of every page whose title or summary holds each word of [query], folded as the search page folds. */
private fun search(controller: AllSettingsController, query: String, context: Context): List<PrefHit> {
    val words = foldForSearch(query).split(' ').filter { it.isNotBlank() }
    return controller.pages.values.flatMap { page ->
        page.sections.flatMap { section ->
            section.items
                .filter { it.key != null && !PreferenceCatalog.isPage(it.key) && PreferenceCatalog.visible(it, controller.values) }
                .filter { item ->
                    val text = foldForSearch(listOfNotNull(item.title, PreferenceCatalog.summary(item, controller.values, context), section.title).joinToString(" "))
                    words.all { it in text }
                }
                .map { PrefHit(page, section.title, it) }
        }
    }.distinctBy { it.item.key }
}

/** Each page's parent: the page with the line that opens it. */
private fun parentsOf(pages: Map<String, PrefPage>): Map<String, String> = buildMap {
    pages.values.forEach { page ->
        page.items.forEach { item -> if (PreferenceCatalog.isPage(item.key)) put(item.key!!, page.id) }
    }
}

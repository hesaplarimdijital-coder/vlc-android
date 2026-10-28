package app.nagmedar.ui.podcast

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.nagmedar.data.PodcastFeedSummary
import app.nagmedar.ui.R
import app.nagmedar.ui.components.IconMenuButton
import app.nagmedar.ui.components.LoadingState
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NoResultsState
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.copyToClipboard
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.covers.CoverTile
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.openLink
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.DetailTransition
import app.nagmedar.ui.shell.ListDetailPanes
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.NagTheme
import org.videolan.tools.AppScope

/** The OPML file's media type, as the apps that write it name it. */
private const val OPML_MIME = "text/x-opml"

/** The name the export suggests; the person may change it in the system's picker. */
private const val OPML_FILE = "nagmedar-podcasts.opml"

/** The widest the lists get on a big window: rows that long are hard to follow with the eyes. */
private val ListWidth = 960.dp

/**
 * S-1101, S-1102: Podcasts: the subscriptions as covers or as a list, the search of Podcast Index and
 * iTunes with a Subscribe button on each result, a podcast's page with its episodes, "add by RSS
 * address" and the OPML import and export in the menu. Ctrl+Shift+P puts the keyboard in the search field.
 */
@Composable
fun PodcastsScreen(undo: UndoBarState?) {
    val context = LocalContext.current
    val app = context.applicationContext
    val scope = rememberCoroutineScope()
    val controller = remember(context, scope, undo) {
        PodcastController(
            PodcastCenter.repository(app), PodcastCenter.search(), scope, app, undo,
            background = AppScope, imports = PodcastCenter.imports, player = ServiceEpisodePlayer(context),
            services = EpisodeServices(
                downloads = PodcastCenter.downloads(app),
                extras = PodcastCenter.extras(app),
                control = ServiceEpisodeControl,
                progress = PodcastCenter.tracker.progress,
                settings = { PodcastCenter.settingsStore(app).read() },
                onSubscribed = { PodcastCenter.schedule(app) },
                transcriber = WhisperTranscriber(app),
            ),
        )
    }
    LaunchedEffect(controller) { controller.follow() }
    // a notification of new episodes was tapped: the show's page
    val requested by PodcastCenter.openRequest.collectAsState()
    LaunchedEffect(requested) {
        requested?.let { feedId ->
            controller.open(feedId)
            PodcastNotifier(app).clearNews(feedId)
            PodcastCenter.opened()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) controller.importFile(uri) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(OPML_MIME)) { uri -> if (uri != null) controller.export(uri) }
    val actions = remember(controller, importer, exporter) {
        controller.actions(
            onCopy = { text -> copyToClipboard(app, app.getString(R.string.nag_streams_address), text, R.string.nag_pod_copied) },
            onOpenLink = { url -> openLink(context, url) },
            onImport = { importer.launch(arrayOf("*/*")) },
            onExport = { exporter.launch(OPML_FILE) },
        )
    }
    val focus = remember { FocusRequester() }
    val pending by PodcastCenter.searchFocusPending.collectAsState()
    LaunchedEffect(pending) {
        if (pending) {
            PodcastCenter.searchFocused()
            runCatching { focus.requestFocus() }
        }
    }
    PodcastsContent(controller.model, actions, focus, controller::listening)
}

/**
 * The screen below the shell: [model] drawn, [actions] called. In tests it is given a model and no repository.
 * S-205: a podcast's page is the details of the subscriptions; an episode's page is the details of that page. With room for
 * two panes (DeX, tablet) the episodes take the list's place while an episode is open and its page is beside them; in a
 * phone window the episode's page fills the screen and Back returns to the podcast. [listening] is the player's place in
 * the open episode, read by the episode's page alone.
 */
@Composable
fun PodcastsContent(
    model: PodcastScreenModel,
    actions: PodcastActions,
    focus: FocusRequester = remember { FocusRequester() },
    listening: () -> Listening? = { model.episode?.listening },
) {
    val page = model.page
    var retained by remember { mutableStateOf(page) }
    LaunchedEffect(page) { if (page != null) retained = page }
    val side = currentWidthClass() >= WidthClass.Expanded
    val episodeOpen = model.episodeId != null
    ListDetailPanes(
        detail = page?.summary?.feed?.id,
        onCloseDetail = if (episodeOpen) actions.onCloseEpisode else actions.onClosePage,
        list = {
            val shown = page ?: retained
            if (side && episodeOpen && shown != null) {
                PodcastPageView(shown, fullScreen = false, actions = actions, downloads = model.downloads, selected = model.episodeId)
            } else {
                PodcastList(model, actions, focus)
            }
        },
        detailPane = { _, fullScreen ->
            val shownPage = page ?: retained
            if (fullScreen) {
                // a phone's window: the episode's page comes over the show's on the shared axis and Back
                // (the predictive gesture) takes it away again (Desktop Kit 0.2.0)
                val held = remember { EpisodeHold() }
                model.episode?.let { held.ui = it }
                DetailTransition(
                    detail = model.episodeId,
                    onBack = actions.onCloseEpisode,
                    list = { shownPage?.let { PodcastPageView(it, fullScreen = true, actions = actions, downloads = model.downloads) } },
                ) { _ ->
                    // closing, the page that was open stays until it has gone
                    EpisodeDetailPane(if (model.episodeId == null) model.copy(episode = held.ui) else model, fullScreen = true, actions, listening)
                }
            } else if (episodeOpen) {
                EpisodeDetailPane(model, fullScreen = false, actions, listening)
            } else {
                shownPage?.let { PodcastPageView(it, fullScreen = false, actions = actions, downloads = model.downloads) }
            }
        },
        widthKey = if (episodeOpen) "podcast-episode" else "podcast-page",
        defaultDetailWidth = if (episodeOpen) 600.dp else 420.dp,
        minDetailWidth = 340.dp,
        minListWidth = 420.dp,
    )
    model.add?.let { AddAddressDialog(it, actions.onAddSubmit, actions.onAddDismiss) }
    model.importing?.let { ImportDialog(it, actions.onImportDismiss) }
    model.showSettings?.let { ShowSettingsDialog(it, actions) }
}

/** The episode's page last shown on a phone, kept while it closes; plain, so keeping it never redraws anything. */
private class EpisodeHold {
    var ui: EpisodeDetailUi? = null
}

@Composable
private fun PodcastList(model: PodcastScreenModel, actions: PodcastActions, focus: FocusRequester) {
    val res = LocalContext.current.resources
    val subscriptions = model.subscriptions
    val wide = currentWidthClass() >= WidthClass.Medium
    var grid by rememberSaveable { mutableStateOf(wide) }
    val showingSubscriptions = model.search is SearchUi.Idle
    Column(Modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.nag_nav_podcasts),
            subtitle = subscriptions?.takeIf { it.isNotEmpty() }?.let { pluralStringResource(R.plurals.nag_pod_count, it.size, it.size) },
        ) {
            if (showingSubscriptions && !subscriptions.isNullOrEmpty()) {
                NagIconButton(
                    if (grid) NagIcons.ListView else NagIcons.Grid,
                    stringResource(if (grid) R.string.nag_pod_view_list else R.string.nag_pod_view_grid),
                    { grid = !grid },
                    modifier = Modifier.testTag("podcast-view-toggle"),
                )
            }
            if (wide) {
                NagButton(stringResource(R.string.nag_pod_add), actions.onAdd, icon = NagIcons.Plus, modifier = Modifier.testTag("podcast-add"))
            } else {
                NagIconButton(NagIcons.Plus, stringResource(R.string.nag_pod_add), actions.onAdd, modifier = Modifier.testTag("podcast-add"))
            }
            IconMenuButton(
                NagIcons.MoreVertical,
                stringResource(R.string.nag_pod_more),
                entries = { overflowMenu(res, subscriptions.orEmpty().isNotEmpty(), actions) },
                modifier = Modifier.testTag("podcast-more"),
            )
        }
        SearchRow(model.query, actions, focus)
        when (val search = model.search) {
            SearchUi.Idle -> Subscriptions(subscriptions, grid, actions, focus)
            SearchUi.Searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                LoadingState(stringResource(R.string.nag_pod_searching), Modifier.padding(top = NagTheme.dimens.gapL), posters = 0)
            }
            is SearchUi.Found -> Results(search, model, actions)
            is SearchUi.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StatePanel(
                    tone = if (search.reason == SearchFailure.Offline) StateTone.Neutral else StateTone.Warning,
                    icon = if (search.reason == SearchFailure.Offline) NagIcons.NetworkOff else NagIcons.Warning,
                    title = search.reason.title(res),
                    description = search.reason.description(res),
                    primary = StateAction(stringResource(R.string.nag_pod_retry), actions.onSearch),
                    secondary = StateAction(stringResource(R.string.nag_pod_clear_search), actions.onClearSearch),
                )
            }
        }
    }
}

private fun overflowMenu(res: android.content.res.Resources, hasSubscriptions: Boolean, actions: PodcastActions): List<MenuEntry> = listOf(
    MenuEntry.Item(res.getString(R.string.nag_pod_menu_import), actions.onImport, icon = NagIcons.Folder),
    MenuEntry.Item(res.getString(R.string.nag_pod_menu_export), actions.onExport, icon = NagIcons.Downloads, enabled = hasSubscriptions),
)

// ---------------------------------------------------------------- search

@Composable
private fun SearchRow(query: String, actions: PodcastActions, focus: FocusRequester) {
    val dimens = NagTheme.dimens
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.pagePadding, vertical = dimens.gapS),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchField(query, actions, focus, Modifier.weight(1f).widthIn(max = ListWidth))
        NagButton(
            stringResource(R.string.nag_pod_search),
            actions.onSearch,
            primary = true,
            enabled = query.isNotBlank(),
            modifier = Modifier.testTag("podcast-search-button"),
        )
    }
}

@Composable
private fun SearchField(query: String, actions: PodcastActions, focus: FocusRequester, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val placeholder = stringResource(R.string.nag_pod_search_placeholder)
    Row(
        modifier
            .heightIn(min = dimens.minTarget.coerceAtLeast(44.dp))
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
                onValueChange = actions.onQueryChange,
                singleLine = true,
                textStyle = NagTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { actions.onSearch() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    .tabMovesFocus(focusManager)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter, Key.NumPadEnter -> {
                                actions.onSearch()
                                true
                            }
                            // Escape empties the field; with it empty the key belongs to the shell
                            Key.Escape -> if (query.isNotEmpty()) {
                                actions.onClearSearch()
                                true
                            } else {
                                false
                            }
                            else -> false
                        }
                    }
                    .semantics { contentDescription = placeholder }
                    .testTag("podcast-search"),
            )
        }
        if (query.isNotEmpty()) NagIconButton(NagIcons.Close, stringResource(R.string.nag_search_clear), actions.onClearSearch)
    }
}

@Composable
private fun Results(search: SearchUi.Found, model: PodcastScreenModel, actions: PodcastActions) {
    val res = LocalContext.current.resources
    if (search.hits.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                NoResultsState(
                    title = stringResource(R.string.nag_pod_no_results, search.term),
                    detail = stringResource(R.string.nag_pod_no_results_detail),
                    suggestions = listOf(
                        StateAction(stringResource(R.string.nag_pod_clear_search), actions.onClearSearch),
                        StateAction(stringResource(R.string.nag_pod_add), actions.onAdd),
                    ),
                )
                search.notice?.let { NoticeLine(it.text(res), Modifier.padding(horizontal = NagTheme.dimens.gapL)) }
            }
        }
        return
    }
    LazyColumn(
        Modifier.fillMaxSize().testTag("podcast-results"),
        contentPadding = PaddingValues(horizontal = NagTheme.dimens.pagePadding, vertical = NagTheme.dimens.gapS),
        verticalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapXs),
    ) {
        item(key = "header") {
            Column(Modifier.widthIn(max = ListWidth).padding(bottom = NagTheme.dimens.gapXs), verticalArrangement = Arrangement.spacedBy(NagTheme.dimens.gapXs)) {
                Text(
                    pluralStringResource(R.plurals.nag_pod_results, search.hits.size, search.hits.size, search.term),
                    style = NagTheme.type.label,
                    color = NagTheme.colors.textSecondary,
                )
                search.notice?.let { NoticeLine(it.text(res)) }
            }
        }
        items(search.hits, key = { FeedUrl.key(it.feedUrl) }) { hit ->
            val key = FeedUrl.key(hit.feedUrl)
            ResultRow(
                hit = hit,
                subscribed = key in model.subscribedKeys,
                state = model.subscribing[key],
                actions = actions,
                modifier = Modifier.widthIn(max = ListWidth),
            )
        }
    }
}

/** A small word of what the results are made of: Podcast Index's keys missing, or Podcast Index not answering. */
@Composable
private fun NoticeLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier.testTag("podcast-notice"), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(NagIcons.Info, contentDescription = null, tint = NagTheme.colors.textTertiary, modifier = Modifier.size(16.dp).padding(top = 1.dp))
        Text(text, style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
    }
}

@Composable
private fun ResultRow(hit: PodcastHit, subscribed: Boolean, state: SubscribeUi?, actions: PodcastActions, modifier: Modifier = Modifier) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val focusState = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val density = LocalDensity.current
    val cover = rememberPodcastCover(hit.imageUrl, with(density) { COVER_ROW.roundToPx() })
    val open = { if (subscribed) actions.onOpenSubscribed(hit) else actions.onSubscribe(hit) }
    ContextMenuArea(
        entries = {
            listOf(
                if (subscribed) {
                    MenuEntry.Item(res.getString(R.string.nag_pod_open), { actions.onOpenSubscribed(hit) }, icon = NagIcons.Podcasts)
                } else {
                    MenuEntry.Item(res.getString(R.string.nag_pod_subscribe), { actions.onSubscribe(hit) }, icon = NagIcons.Plus)
                },
                MenuEntry.Item(res.getString(R.string.nag_pod_copy_address), { actions.onCopy(hit.feedUrl) }),
            )
        },
        accessibilityLabel = res.getString(R.string.nag_pod_actions),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTarget)
                .focusRing(focusState, shape)
                .background(stateBackground(focusState), shape)
                .nagClickable(interaction, open, onClickLabel = if (subscribed) res.getString(R.string.nag_pod_open) else res.getString(R.string.nag_pod_subscribe))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("podcast-result"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverTile(cover, COVER_ROW, placeholder = NagIcons.Podcasts)
            Column(Modifier.weight(1f)) {
                Text(hit.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(
                    hit.author,
                    hit.episodeCount?.let { res.getQuantityString(R.plurals.nag_pod_episodes, it, it) },
                ).joinToString(" · ")
                if (meta.isNotEmpty()) Text(meta, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (state is SubscribeUi.Failed) {
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(NagIcons.Warning, contentDescription = null, tint = toneColors(StateTone.Warning).icon, modifier = Modifier.size(14.dp).padding(top = 1.dp))
                        Text(state.failure.describe(res), style = NagTheme.type.labelSmall, color = toneColors(StateTone.Warning).content, modifier = Modifier.testTag("podcast-result-warning"))
                    }
                }
            }
            SubscribeButton(subscribed, state, onSubscribe = { actions.onSubscribe(hit) }, onOpen = { actions.onOpenSubscribed(hit) })
        }
    }
}

/** "Abone ol", then "Ekleniyor…", then "Abone" with a tick: the button of a result. */
@Composable
private fun SubscribeButton(subscribed: Boolean, state: SubscribeUi?, onSubscribe: () -> Unit, onOpen: () -> Unit) {
    when {
        subscribed -> NagButton(stringResource(R.string.nag_pod_subscribed), onOpen, icon = NagIcons.Check, modifier = Modifier.testTag("podcast-subscribed"))
        state == SubscribeUi.Working -> NagButton(stringResource(R.string.nag_pod_adding), {}, enabled = false, modifier = Modifier.testTag("podcast-subscribe-working"))
        state is SubscribeUi.Failed -> NagButton(stringResource(R.string.nag_pod_retry), onSubscribe, primary = true, modifier = Modifier.testTag("podcast-subscribe"))
        else -> NagButton(stringResource(R.string.nag_pod_subscribe), onSubscribe, primary = true, modifier = Modifier.testTag("podcast-subscribe"))
    }
}

// ---------------------------------------------------------------- subscriptions

@Composable
private fun Subscriptions(subscriptions: List<PodcastFeedSummary>?, grid: Boolean, actions: PodcastActions, focus: FocusRequester) {
    val res = LocalContext.current.resources
    when {
        subscriptions == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LoadingState(res.getString(R.string.nag_pod_loading), Modifier.padding(top = NagTheme.dimens.gapL))
        }
        subscriptions.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StatePanel(
                tone = StateTone.Neutral,
                icon = NagIcons.Podcasts,
                title = stringResource(R.string.nag_pod_empty_title),
                description = stringResource(R.string.nag_pod_empty_desc),
                primary = StateAction(stringResource(R.string.nag_pod_find)) { runCatching { focus.requestFocus() } },
                secondary = StateAction(stringResource(R.string.nag_pod_menu_import), actions.onImport),
            )
        }
        grid -> SubscriptionGrid(subscriptions, actions)
        else -> SubscriptionList(subscriptions, actions)
    }
}

private fun subscriptionMenu(res: android.content.res.Resources, summary: PodcastFeedSummary, actions: PodcastActions): List<MenuEntry> = listOf(
    MenuEntry.Item(res.getString(R.string.nag_pod_open), { actions.onOpen(summary.feed.id) }, icon = NagIcons.Podcasts),
    MenuEntry.Item(res.getString(R.string.nag_pod_copy_address), { actions.onCopy(summary.feed.feedUrl) }),
    MenuEntry.Separator,
    MenuEntry.Item(res.getString(R.string.nag_pod_unsubscribe), { actions.onUnsubscribe(summary.feed.id) }, icon = NagIcons.Delete, destructive = true),
)

@Composable
private fun SubscriptionGrid(subscriptions: List<PodcastFeedSummary>, actions: PodcastActions) {
    val dimens = NagTheme.dimens
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize().testTag("podcast-grid"),
        contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapS),
        horizontalArrangement = Arrangement.spacedBy(dimens.gapM),
        verticalArrangement = Arrangement.spacedBy(dimens.gapM),
    ) {
        items(subscriptions, key = { it.feed.id }) { summary -> SubscriptionTile(summary, actions) }
    }
}

@Composable
private fun SubscriptionTile(summary: PodcastFeedSummary, actions: PodcastActions) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    ContextMenuArea(
        entries = { subscriptionMenu(res, summary, actions) },
        accessibilityLabel = res.getString(R.string.nag_pod_actions),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .focusRing(state, shape)
                .background(stateBackground(state), shape)
                .nagClickable(interaction, { actions.onOpen(summary.feed.id) }, onClickLabel = res.getString(R.string.nag_pod_open))
                .padding(6.dp)
                .testTag("podcast-tile"),
        ) {
            val side = maxWidth
            val cover = rememberPodcastCover(summary.feed.imageUrl, with(LocalDensity.current) { side.roundToPx().coerceAtMost(COVER_MAX_PX) })
            Column {
                Box {
                    CoverTile(cover, side, corner = dimens.radiusControl, placeholder = NagIcons.Podcasts)
                    if (summary.newCount > 0) {
                        ToneBadge(
                            pluralStringResource(R.plurals.nag_pod_new, summary.newCount, summary.newCount),
                            StateTone.Info,
                            Modifier.align(Alignment.TopEnd).padding(6.dp).testTag("podcast-new"),
                        )
                    }
                }
                Spacer(Modifier.size(6.dp))
                Text(summary.feed.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(summary.feed.author, res.getQuantityString(R.plurals.nag_pod_episodes, summary.episodeCount, summary.episodeCount)).joinToString(" · ")
                Text(meta, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SubscriptionList(subscriptions: List<PodcastFeedSummary>, actions: PodcastActions) {
    val dimens = NagTheme.dimens
    LazyColumn(
        Modifier.fillMaxSize().testTag("podcast-list"),
        contentPadding = PaddingValues(horizontal = dimens.pagePadding, vertical = dimens.gapS),
        verticalArrangement = Arrangement.spacedBy(dimens.gapXs),
    ) {
        items(subscriptions, key = { it.feed.id }) { summary -> SubscriptionRow(summary, actions, Modifier.widthIn(max = ListWidth)) }
    }
}

@Composable
private fun SubscriptionRow(summary: PodcastFeedSummary, actions: PodcastActions, modifier: Modifier = Modifier) {
    val res = LocalContext.current.resources
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val cover = rememberPodcastCover(summary.feed.imageUrl, with(LocalDensity.current) { COVER_ROW.roundToPx() })
    ContextMenuArea(
        entries = { subscriptionMenu(res, summary, actions) },
        accessibilityLabel = res.getString(R.string.nag_pod_actions),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTarget)
                .focusRing(state, shape)
                .background(stateBackground(state), shape)
                .nagClickable(interaction, { actions.onOpen(summary.feed.id) }, onClickLabel = res.getString(R.string.nag_pod_open))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .testTag("podcast-row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CoverTile(cover, COVER_ROW, placeholder = NagIcons.Podcasts)
            Column(Modifier.weight(1f)) {
                Text(summary.feed.title, style = NagTheme.type.bodyStrong, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(summary.feed.author, res.getQuantityString(R.plurals.nag_pod_episodes, summary.episodeCount, summary.episodeCount)).joinToString(" · ")
                Text(meta, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (summary.newCount > 0) {
                ToneBadge(pluralStringResource(R.plurals.nag_pod_new, summary.newCount, summary.newCount), StateTone.Info, Modifier.testTag("podcast-new"))
            }
            Icon(NagIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(dimens.icon))
        }
    }
}

/** The cover in a row, in dp. */
internal val COVER_ROW: Dp = 56.dp

/** Covers are decoded no larger than this, however wide the tile. */
private const val COVER_MAX_PX = 480

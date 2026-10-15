package app.nagmedar.ui.network

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.BrowserCommands
import app.nagmedar.ui.components.ColumnWidth
import app.nagmedar.ui.components.DenseTable
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIconButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.SectionCaption
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StateIconTile
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.TableColumn
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.components.VerticalRule
import app.nagmedar.ui.components.alertBackground
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.stateBackground
import app.nagmedar.ui.components.toneColors
import app.nagmedar.ui.input.ContextMenuArea
import app.nagmedar.ui.input.KeyChord
import app.nagmedar.ui.input.ShortcutId
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.screens.PageHeader
import app.nagmedar.ui.shell.WidthClass
import app.nagmedar.ui.shell.currentWidthClass
import app.nagmedar.ui.theme.InputMode
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import org.videolan.tools.KEY_BROWSE_NETWORK
import org.videolan.tools.NAGMEDAR_NETWORK_SHARES
import org.videolan.tools.NetworkMonitor
import org.videolan.tools.Settings

/** A server being added or changed in the form. */
private sealed interface Editing {
    val draft: ServerDraft

    data class New(override val draft: ServerDraft) : Editing

    data class Change(val server: SavedServer, override val draft: ServerDraft) : Editing
}

/**
 * S-206, S-1301: Sources › Network in the app: libVLC reads the network, Nağmedar's database
 * keeps the saved servers with their sign-ins sealed by the Android Keystore, the playback
 * service plays. Looks for servers while the network allows it and shares are on.
 */
@Composable
fun NetworkScreen(undo: UndoBarState? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val locale = currentLocale()
    val model = remember(context, locale) {
        NetworkModel(
            LibVlcNetworkSource(context),
            DatabaseServerStore(context),
            scope,
            locale,
            onNotice = { notice -> Toast.makeText(context, noticeText(context, notice), Toast.LENGTH_SHORT).show() },
        )
    }
    val actions = remember(context) { ServiceNetworkActions(context) }
    LaunchedEffect(model) { model.followSaved() }
    var sharesOn by remember { mutableStateOf(sharesEnabled(context)) }
    val lanFlow = remember(context) { NetworkMonitor.getInstance(context).connectionFlow.drop(1).map { it.connected && (!it.mobile || it.vpn) } }
    val lan by lanFlow.collectAsState(initial = lanNow(context))
    val state = when {
        !sharesOn -> LanState.Disabled
        !lan -> LanState.NoLan
        else -> LanState.Available
    }
    LaunchedEffect(state) {
        if (state == LanState.Available) model.discover() else model.stopDiscovery()
    }
    NetworkBrowser(
        model = model,
        actions = actions,
        lan = state,
        undo = undo,
        onEnableShares = {
            Settings.getInstance(context).edit().putBoolean(NAGMEDAR_NETWORK_SHARES, true).putBoolean(KEY_BROWSE_NETWORK, true).apply()
            sharesOn = true
        },
        onOpenWifiSettings = {
            val panel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) AndroidSettings.Panel.ACTION_WIFI else AndroidSettings.ACTION_WIFI_SETTINGS
            runCatching { context.startActivity(Intent(panel)) }
        },
    )
    // the page goes: the network is no longer read for it
    DisposableEffect(model) {
        onDispose {
            model.stopDiscovery()
            model.close()
        }
    }
}

/** Whether network shares are on: the first run's choice and VLC's "Browse the network" setting. */
private fun sharesEnabled(context: Context): Boolean {
    val settings = Settings.getInstance(context)
    return settings.getBoolean(NAGMEDAR_NETWORK_SHARES, true) && settings.getBoolean(KEY_BROWSE_NETWORK, true)
}

/** Whether a local network is there now: Wi-Fi, Ethernet or a VPN; a mobile connection alone is not one. */
private fun lanNow(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
}

private fun noticeText(context: Context, notice: NetworkNotice): String {
    val res = context.resources
    return when (notice) {
        is NetworkNotice.Queued -> res.getQuantityString(R.plurals.nag_drop_queued, notice.count, notice.count)
        is NetworkNotice.NothingToPlay -> res.getString(R.string.nag_net_nothing_to_play, notice.name)
        is NetworkNotice.AddedToLibrary -> res.getString(
            if (notice.signInKept) R.string.nag_net_added_to_library else R.string.nag_net_added_to_library_session,
            notice.name,
        )
        NetworkNotice.AddressTaken -> res.getString(R.string.nag_net_address_taken)
        NetworkNotice.SignInNotKept -> res.getString(R.string.nag_net_sign_in_not_kept)
        is NetworkNotice.FoldersSkipped -> res.getQuantityString(R.plurals.nag_net_folders_skipped, notice.count, notice.count)
    }
}

/**
 * S-206: the network browser. On a wide window the servers (saved ones, then those found on this
 * network) beside the open folder; on a phone the servers, then the folder over them, Back going
 * up. A folder is a dense table (Enter or a double click opens, Backspace goes up, F5 reads it
 * again, the Menu key opens a file's menu); its states: connecting (after a few seconds the time
 * taken and a way to give up), empty, unreachable, and a sign-in asked for, turned down or lost.
 */
@Composable
fun NetworkBrowser(
    model: NetworkModel,
    actions: NetworkActions,
    lan: LanState,
    modifier: Modifier = Modifier,
    undo: UndoBarState? = null,
    onEnableShares: () -> Unit = {},
    onOpenWifiSettings: () -> Unit = {},
    /** The clock of the slow network's seconds; the screenshots fix it. */
    clock: () -> Long = SystemClock::elapsedRealtime,
    /** A form open from the start (the screenshots'). */
    initialDraft: ServerDraft? = null,
) {
    val res = LocalContext.current.resources
    var editing by remember { mutableStateOf<Editing?>(initialDraft?.let { if (it.editing != null) Editing.Change(it.editing, it) else Editing.New(it) }) }
    val wide = currentWidthClass() >= WidthClass.Expanded
    val folder = model.folder
    val refresh = {
        if (model.folder != null) model.refresh() else if (lan == LanState.Available) model.discover()
        true
    }
    BrowserCommands(onUp = { model.up() }, onRefresh = refresh)
    val addServer = { editing = Editing.New(ServerDraft()) }
    val editServer = { server: SavedServer -> editing = Editing.Change(server, ServerDraft.of(server)) }
    val saveFound = { server: DiscoveredServer -> editing = Editing.New(ServerDraft.of(server)) }
    val removeServer = { server: SavedServer ->
        model.remove(server) { restore ->
            undo?.show(res.getString(R.string.nag_net_removed, server.name), res.getString(R.string.nag_sc_undo), ShortcutRegistry.chordLabel(ShortcutId.Undo, res), restore)
        }
    }
    Column(modifier.fillMaxSize()) {
        PageHeader(
            title = stringResource(R.string.nag_nav_network),
            subtitle = summary(model, lan),
        ) {
            NagIconButton(NagIcons.Repeat, stringResource(R.string.nag_sc_refresh), { refresh() }, shortcut = ShortcutId.Refresh)
            if (wide) {
                NagButton(stringResource(R.string.nag_net_add_server), addServer, icon = NagIcons.Plus, primary = true, modifier = Modifier.testTag("network-add"))
            } else {
                NagIconButton(NagIcons.Plus, stringResource(R.string.nag_net_add_server), addServer, modifier = Modifier.testTag("network-add"))
            }
        }
        HorizontalRule()
        val servers = @Composable { paneModifier: Modifier ->
            ServerPane(model, lan, paneModifier, onAdd = addServer, onEdit = editServer, onRemove = removeServer, onSaveFound = saveFound,
                onEnableShares = onEnableShares, onOpenWifiSettings = onOpenWifiSettings)
        }
        val folderPane = @Composable { paneModifier: Modifier ->
            FolderPane(model, actions, paneModifier, clock = clock, onAdd = addServer, onEdit = editServer, showBack = !wide)
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                servers(Modifier.width(if (NagTheme.inputMode == InputMode.Pointer) 280.dp else 320.dp).fillMaxHeight())
                VerticalRule()
                folderPane(Modifier.weight(1f).fillMaxHeight())
            }
        } else if (folder == null) {
            servers(Modifier.fillMaxSize())
        } else {
            BackHandler { if (!model.up()) model.close() }
            folderPane(Modifier.fillMaxSize())
        }
    }
    when (val form = editing) {
        null -> Unit
        else -> ServerDialog(
            form.draft,
            onSave = {
                val draft = form.draft
                model.save((form as? Editing.Change)?.server?.id ?: 0L, draft.displayName, draft.url, draft.signInChange) { saved -> if (saved) editing = null }
            },
            onCancel = { editing = null },
            onRemove = {
                (form as? Editing.Change)?.server?.let(removeServer)
                editing = null
            },
        )
    }
}

/** "3 saved · 2 on this network", or what keeps the network from being looked through. */
@Composable
private fun summary(model: NetworkModel, lan: LanState): String? {
    val saved = model.saved?.size ?: return null
    val found = model.discovery.unsaved(model.saved.orEmpty()).size
    val savedText = pluralStringResource(R.plurals.nag_net_saved_count, saved, saved)
    return when (lan) {
        LanState.Available -> stringResource(R.string.nag_net_summary, savedText, pluralStringResource(R.plurals.nag_net_found_count, found, found))
        else -> savedText
    }
}

/** The servers: a word on the network when it cannot be looked through, the saved ones, those found on it. */
@Composable
private fun ServerPane(
    model: NetworkModel,
    lan: LanState,
    modifier: Modifier,
    onAdd: () -> Unit,
    onEdit: (SavedServer) -> Unit,
    onRemove: (SavedServer) -> Unit,
    onSaveFound: (DiscoveredServer) -> Unit,
    onEnableShares: () -> Unit,
    onOpenWifiSettings: () -> Unit,
) {
    val res = LocalContext.current.resources
    val dimens = NagTheme.dimens
    val saved = model.saved.orEmpty()
    val found = model.discovery.unsaved(saved)
    val openUrl = model.folder?.server?.url
    val menuLabel = stringResource(R.string.nag_more_actions)
    LazyColumn(modifier.testTag("network-servers"), contentPadding = PaddingValues(vertical = dimens.gapS)) {
        when (lan) {
            LanState.Disabled -> item(key = "disabled") {
                PaneNotice(
                    StateTone.Info,
                    NagIcons.Shield,
                    stringResource(R.string.nag_net_off_title),
                    stringResource(R.string.nag_net_off_desc),
                    StateAction(stringResource(R.string.nag_net_off_turn_on), onEnableShares),
                )
            }
            LanState.NoLan -> item(key = "no-lan") {
                PaneNotice(
                    StateTone.Warning,
                    NagIcons.NetworkOff,
                    stringResource(R.string.nag_net_no_lan_title),
                    stringResource(R.string.nag_net_no_lan_desc),
                    StateAction(stringResource(R.string.nag_net_wifi_settings), onOpenWifiSettings),
                )
            }
            LanState.Available -> Unit
        }
        item(key = "saved-caption") { PaneCaption(stringResource(R.string.nag_net_saved)) }
        if (model.saved != null && saved.isEmpty()) {
            item(key = "saved-none") { PaneHint(stringResource(R.string.nag_net_saved_none), StateAction(stringResource(R.string.nag_net_add_server), onAdd)) }
        }
        items(saved, key = { "saved-${it.id}" }) { server ->
            ContextMenuArea(
                entries = {
                    listOf(
                        MenuEntry.Item(res.getString(R.string.nag_net_open), { model.open(server) }),
                        MenuEntry.Item(res.getString(R.string.nag_net_edit), { onEdit(server) }),
                        MenuEntry.Separator,
                        MenuEntry.Item(res.getString(R.string.nag_net_remove_server), { onRemove(server) }, icon = NagIcons.Delete, destructive = true),
                    )
                },
                accessibilityLabel = menuLabel,
            ) {
                ServerRow(
                    icon = protocolIcon(server.protocol),
                    name = server.name,
                    detail = serverDetail(server.protocol, server.url),
                    selected = openUrl == server.url,
                    locked = server.signedIn,
                    onClick = { model.open(server) },
                )
            }
        }
        if (lan == LanState.Available) {
            item(key = "found-caption") { PaneCaption(stringResource(R.string.nag_net_on_this_network), searching = model.discovery.searching) }
            if (found.isEmpty() && !model.discovery.searching) {
                item(key = "found-none") { PaneHint(stringResource(R.string.nag_net_found_none), null) }
            }
            items(found, key = { "found-${it.url}" }) { server ->
                ContextMenuArea(
                    entries = {
                        listOf(
                            MenuEntry.Item(res.getString(R.string.nag_net_open), { model.open(server) }),
                            MenuEntry.Item(res.getString(R.string.nag_net_save_server), { onSaveFound(server) }),
                        )
                    },
                    accessibilityLabel = menuLabel,
                ) {
                    ServerRow(
                        icon = protocolIcon(server.protocol),
                        name = server.name,
                        detail = serverDetail(server.protocol, server.url),
                        selected = openUrl == server.url,
                        locked = false,
                        onClick = { model.open(server) },
                    )
                }
            }
        }
    }
}

/** "SMB · nas.local". */
private fun serverDetail(protocol: NetworkProtocol?, url: String): String =
    listOfNotNull(protocol?.label, displayHost(url)).joinToString(" · ")

private fun protocolIcon(protocol: NetworkProtocol?): ImageVector = when (protocol) {
    NetworkProtocol.Upnp -> NagIcons.Streams
    null -> NagIcons.Network
    else -> NagIcons.Network
}

@Composable
private fun PaneCaption(text: String, searching: Boolean = false) {
    val dimens = NagTheme.dimens
    Column(Modifier.fillMaxWidth().padding(start = dimens.gapM, end = dimens.gapM, top = dimens.gapM, bottom = dimens.gapXs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionCaption(text, Modifier.weight(1f).semantics { heading() })
            if (searching) Text(stringResource(R.string.nag_net_searching), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
        }
        if (searching) {
            Spacer(Modifier.padding(top = 4.dp))
            NagProgressBar(null, label = stringResource(R.string.nag_net_searching))
        }
    }
}

@Composable
private fun PaneHint(text: String, action: StateAction?) {
    val dimens = NagTheme.dimens
    Column(Modifier.fillMaxWidth().padding(horizontal = dimens.gapM, vertical = dimens.gapXs), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
        Text(text, style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
        if (action != null) NagButton(action.label, action.onClick, icon = NagIcons.Plus)
    }
}

/** What keeps the network from being looked through, in the servers' pane, with the way out. */
@Composable
private fun PaneNotice(tone: StateTone, icon: ImageVector, title: String, message: String, action: StateAction) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val c = toneColors(tone)
    val shape = RoundedCornerShape(dimens.radiusCard)
    Column(
        Modifier
            .padding(horizontal = dimens.gapS, vertical = dimens.gapXs)
            .fillMaxWidth()
            .background(colors.alertBackground(tone), shape)
            .padding(dimens.gapM)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }
            .testTag("network-notice"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = c.base, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, style = NagTheme.type.bodyStrong, color = c.content)
        }
        Text(message, style = NagTheme.type.labelSmall, color = colors.textSecondary)
        NagButton(action.label, action.onClick)
    }
}

/** A server in the list: its icon, its name, its protocol and host, a lock when its sign-in is kept. */
@Composable
private fun ServerRow(icon: ImageVector, name: String, detail: String, selected: Boolean, locked: Boolean, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    val ink = if (selected) colors.onSelection else colors.text
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.gapS)
            .heightIn(min = dimens.minTarget.coerceAtLeast(44.dp))
            .focusRing(state, shape)
            .background(stateBackground(state, selected), shape)
            .semantics { this.selected = selected }
            .nagClickable(interaction, onClick)
            .padding(horizontal = dimens.gapS, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) colors.onSelection else colors.textSecondary, modifier = Modifier.size(dimens.icon))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = NagTheme.type.bodyStrong, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = NagTheme.type.labelSmall, color = if (selected) colors.onSelection else colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (locked) {
            Icon(NagIcons.Lock, contentDescription = stringResource(R.string.nag_net_sign_in_kept_short), tint = if (selected) colors.onSelection else colors.textTertiary,
                modifier = Modifier.size(14.dp))
        }
    }
}

/** The open folder: where it is, what can be done with all of it, and what it holds or why it does not show. */
@Composable
private fun FolderPane(
    model: NetworkModel,
    actions: NetworkActions,
    modifier: Modifier,
    clock: () -> Long,
    onAdd: () -> Unit,
    onEdit: (SavedServer) -> Unit,
    showBack: Boolean,
) {
    val view = model.folder
    Column(modifier) {
        if (view == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                StatePanel(
                    tone = StateTone.Neutral,
                    icon = NagIcons.Network,
                    title = stringResource(R.string.nag_net_choose_title),
                    description = stringResource(R.string.nag_net_choose_desc),
                    primary = StateAction(stringResource(R.string.nag_net_add_server), onAdd),
                )
            }
            return@Column
        }
        FolderBar(view, model, actions, showBack)
        HorizontalRule()
        val savedServer = view.server.savedId?.let { id -> model.saved?.firstOrNull { it.id == id } }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val status = view.status) {
                is FolderStatus.Connecting -> Centered { Connecting(view, status.since, clock, onCancel = model::cancel) }
                FolderStatus.Unreachable -> Centered {
                    StatePanel(
                        tone = StateTone.Error,
                        icon = NagIcons.NetworkOff,
                        title = stringResource(R.string.nag_net_unreachable_title),
                        description = stringResource(R.string.nag_net_unreachable_desc, displayHost(view.url)),
                        primary = StateAction(stringResource(R.string.nag_net_try_again), model::refresh),
                        secondary = savedServer?.let { StateAction(stringResource(R.string.nag_net_edit), { onEdit(it) }) },
                    )
                }
                is FolderStatus.SignIn -> Centered {
                    SignInPanel(view.server, status.reason, onSignIn = model::signIn, onCancel = model::cancel, onRetry = model::reopen)
                }
                is FolderStatus.Question -> Centered {
                    PromptPanel(status.prompt, onAnswer = { action -> model.answer(status.prompt, action) }, modifier = Modifier.testTag("network-question"))
                }
                FolderStatus.Listed -> if (view.entries.isEmpty()) {
                    Centered {
                        StatePanel(
                            tone = StateTone.Neutral,
                            icon = NagIcons.Folder,
                            title = stringResource(R.string.nag_net_empty_title),
                            description = stringResource(R.string.nag_net_empty_desc, view.name),
                            primary = if (view.atRoot) null else StateAction(stringResource(R.string.nag_sc_folder_up)) { model.up() },
                            secondary = StateAction(stringResource(R.string.nag_sc_refresh), model::refresh),
                        )
                    }
                } else {
                    EntryTable(view, model, actions)
                }
            }
        }
        model.gathering?.let { name -> GatheringStrip(name, onCancel = model::stopGathering) }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) { content() }
}

/** Where the folder is (each folder on the way a button back to it) and what is done with all it holds. */
@Composable
private fun FolderBar(view: FolderView, model: NetworkModel, actions: NetworkActions, showBack: Boolean) {
    val dimens = NagTheme.dimens
    val colors = NagTheme.colors
    val listed = view.status == FolderStatus.Listed && view.entries.isNotEmpty()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = dimens.gapS, vertical = dimens.gapXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
            NagIconButton(NagIcons.ChevronLeft, stringResource(R.string.nag_sc_folder_up), { if (!model.up()) model.close() }, shortcut = ShortcutId.FolderUp)
        }
        Row(
            Modifier.weight(1f).horizontalScroll(rememberScrollState()).testTag("network-trail"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            view.trail.forEachIndexed { index, crumb ->
                if (index > 0) Icon(NagIcons.ChevronRight, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
                val last = index == view.trail.lastIndex
                CrumbButton(crumb.name, current = last, onClick = { model.goTo(index) })
            }
        }
        if (listed) {
            NagIconButton(NagIcons.Play, stringResource(R.string.nag_net_play_all), { model.playFolder(null, enqueue = false, actions) })
            NagIconButton(NagIcons.Queue, stringResource(R.string.nag_net_queue_all), { model.playFolder(null, enqueue = true, actions) })
            if (actions.canAddToLibrary(view.url)) {
                NagIconButton(NagIcons.FolderAdd, stringResource(R.string.nag_net_add_to_library), { model.addToLibrary(null, actions) })
            }
        }
    }
}

@Composable
private fun CrumbButton(name: String, current: Boolean, onClick: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    val shape = RoundedCornerShape(dimens.radiusControl)
    Box(
        Modifier
            .heightIn(min = dimens.minTarget.coerceAtLeast(dimens.controlHeight))
            .focusRing(state, shape)
            .background(stateBackground(state), shape)
            .nagClickable(interaction, onClick, enabled = !current)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name,
            style = if (current) NagTheme.type.bodyStrong else NagTheme.type.body,
            color = if (current) colors.text else colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 240.dp),
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** libVLC reading the folder: at first only that; after a few seconds, how long it has taken and a way to give up. */
@Composable
private fun Connecting(view: FolderView, since: Long, clock: () -> Long, onCancel: () -> Unit) {
    // the seconds change once a second; an infinite animation's frames, which tests stop
    val seconds by produceState(((clock() - since) / 1000).toInt(), since) {
        while (true) withInfiniteAnimationFrameMillis { value = ((clock() - since) / 1000).toInt() }
    }
    val slow = seconds * 1000L >= NetworkModel.SLOW_MS
    StatePanel(
        tone = if (slow) StateTone.Warning else StateTone.Info,
        icon = NagIcons.Network,
        title = stringResource(if (slow) R.string.nag_net_slow_title else R.string.nag_net_connecting),
        description = if (slow) stringResource(R.string.nag_net_slow_desc, displayHost(view.url), seconds) else view.name,
        primary = if (slow) StateAction(stringResource(R.string.nag_sc_cancel), onCancel) else null,
        modifier = Modifier.testTag("network-connecting"),
    ) {
        NagProgressBar(null, label = stringResource(R.string.nag_net_connecting), modifier = Modifier.width(240.dp))
    }
}

/**
 * The sign-in a folder asks for: needed, turned down (the user name stays) or lost on this device;
 * kept sealed when "remember" stays ticked (a server found on the network is saved for it).
 */
@Composable
private fun SignInPanel(server: OpenServer, reason: SignInReason, onSignIn: (Credentials, Boolean) -> Unit, onCancel: () -> Unit, onRetry: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    var user by rememberSaveable(server.url) { mutableStateOf("") }
    var password by remember(server.url, reason) { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var keep by rememberSaveable(server.url) { mutableStateOf(true) }
    val submit = { if (user.isNotBlank()) onSignIn(Credentials(user, password), keep) }
    val tone = when (reason) {
        SignInReason.Needed -> StateTone.Info
        SignInReason.Rejected -> StateTone.Error
        SignInReason.Forgotten, SignInReason.Unavailable -> StateTone.Warning
    }
    Column(
        Modifier
            .widthIn(max = 420.dp)
            .fillMaxWidth()
            .padding(dimens.gapL)
            .testTag("network-sign-in"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StateIconTile(NagIcons.Lock, tone)
            Text(
                stringResource(
                    when (reason) {
                        SignInReason.Needed -> R.string.nag_net_sign_in_needed
                        SignInReason.Rejected -> R.string.nag_net_sign_in_rejected
                        SignInReason.Forgotten -> R.string.nag_net_sign_in_lost
                        SignInReason.Unavailable -> R.string.nag_net_sign_in_unavailable
                    },
                ),
                style = NagTheme.type.title,
                color = colors.text,
            )
            Text(
                stringResource(
                    when (reason) {
                        SignInReason.Needed -> R.string.nag_net_sign_in_needed_desc
                        SignInReason.Rejected -> R.string.nag_net_sign_in_rejected_desc
                        SignInReason.Forgotten -> R.string.nag_net_sign_in_lost_desc
                        SignInReason.Unavailable -> R.string.nag_net_sign_in_unavailable_desc
                    },
                    server.name,
                ),
                style = NagTheme.type.body,
                color = colors.textSecondary,
            )
        }
        CredentialFields(
            user = user,
            onUser = { user = it },
            password = password,
            onPassword = { password = it },
            showPassword = show,
            onShowPassword = { show = it },
            onDone = submit,
        )
        NagCheckbox(
            keep,
            { keep = it },
            stringResource(if (server.savedId != null) R.string.nag_net_remember else R.string.nag_net_remember_and_save),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            NagButton(stringResource(R.string.nag_sc_cancel), onCancel)
            // the Keystore may answer now: the kept password, not a typed one
            if (reason == SignInReason.Unavailable) NagButton(stringResource(R.string.nag_net_try_again), onRetry)
            NagButton(stringResource(R.string.nag_net_sign_in_action), { submit() }, primary = true, enabled = user.isNotBlank(), modifier = Modifier.testTag("network-sign-in-go"))
        }
    }
}

/** The files of the folder, folders first: a click selects and a double click or Enter opens (a tap opens by touch). */
@Composable
private fun EntryTable(view: FolderView, model: NetworkModel, actions: NetworkActions) {
    val res = LocalContext.current.resources
    val pointer = NagTheme.inputMode == InputMode.Pointer
    val compact = currentWidthClass() == WidthClass.Compact
    var selected by remember(view.url) { mutableStateOf<String?>(null) }
    val name = TableColumn<NetworkEntry>(
        title = stringResource(R.string.nag_net_column_name),
        width = ColumnWidth.Weight(1f),
        sortable = false,
        content = { entry, isSelected -> EntryName(entry, isSelected) },
        value = { it.name },
    )
    val kind = TableColumn<NetworkEntry>(
        title = stringResource(R.string.nag_net_column_kind),
        width = ColumnWidth.Fixed(120.dp),
        sortable = false,
        value = { res.getString(kindLabel(it.kind)) },
    )
    val open = { entry: NetworkEntry -> model.open(entry, actions) }
    DenseTable(
        columns = if (compact) listOf(name) else listOf(name, kind),
        rows = view.entries,
        key = { it.url },
        selectedKey = selected,
        onRowClick = { entry, _ -> if (pointer) selected = entry.url else open(entry) },
        onRowDoubleClick = if (pointer) { entry, _ -> open(entry) } else null,
        rowMenu = { entry, _ -> entryMenu(res, entry, view, model, actions) },
        rowMenuLabel = stringResource(R.string.nag_more_actions),
        modifier = Modifier.testTag("network-table"),
    )
}

@Composable
private fun EntryName(entry: NetworkEntry, selected: Boolean) {
    val colors = NagTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when (entry.kind) {
                EntryKind.Folder -> NagIcons.Folder
                EntryKind.Video -> NagIcons.Movies
                EntryKind.Audio -> NagIcons.Music
                EntryKind.Playlist -> NagIcons.PlaylistAdd
                EntryKind.Subtitle -> NagIcons.Subtitle
                EntryKind.Other -> NagIcons.MoreHorizontal
            },
            contentDescription = null,
            tint = if (selected) colors.onSelection else if (entry.kind == EntryKind.Folder) colors.accentText else colors.textSecondary,
            modifier = Modifier.size(NagTheme.dimens.icon),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            entry.name,
            style = if (entry.kind == EntryKind.Folder) NagTheme.type.bodyStrong else NagTheme.type.body,
            color = if (selected) colors.onSelection else colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun kindLabel(kind: EntryKind): Int = when (kind) {
    EntryKind.Folder -> R.string.nag_net_kind_folder
    EntryKind.Video -> R.string.nag_net_kind_video
    EntryKind.Audio -> R.string.nag_net_kind_audio
    EntryKind.Playlist -> R.string.nag_net_kind_playlist
    EntryKind.Subtitle -> R.string.nag_net_kind_subtitle
    EntryKind.Other -> R.string.nag_net_kind_other
}

/** A file's menu: play, queue, play on a device (S-1302); a folder's: open, play, queue, add to the library. */
private fun entryMenu(res: Resources, entry: NetworkEntry, view: FolderView, model: NetworkModel, actions: NetworkActions): List<MenuEntry> = buildList {
    if (entry.kind == EntryKind.Folder) {
        add(MenuEntry.Item(res.getString(R.string.nag_net_open), { model.open(entry, actions) }, shortcut = KeyChord(Key.Enter).label(res)))
        add(MenuEntry.Item(res.getString(R.string.nag_play), { model.playFolder(entry, enqueue = false, actions) }, icon = NagIcons.Play))
        add(MenuEntry.Item(res.getString(R.string.nag_add_to_queue), { model.playFolder(entry, enqueue = true, actions) }, icon = NagIcons.Queue))
        if (actions.canAddToLibrary(entry.url)) {
            add(MenuEntry.Separator)
            add(MenuEntry.Item(res.getString(R.string.nag_net_add_to_library), { model.addToLibrary(entry, actions) }, icon = NagIcons.FolderAdd))
        }
    } else {
        add(MenuEntry.Item(res.getString(R.string.nag_play), { model.open(entry, actions) }, icon = NagIcons.Play, shortcut = KeyChord(Key.Enter).label(res)))
        add(MenuEntry.Item(res.getString(R.string.nag_add_to_queue), { actions.enqueue(listOf(entry)) }, icon = NagIcons.Queue))
        // S-1302: the devices found, each by name, and the picker for the others
        RemoteRendererHook.renderers?.takeIf { it.offered(entry) }?.let { renderers ->
            val files = view.playable
            val at = files.indexOf(entry).coerceAtLeast(0)
            renderers.available().forEach { renderer ->
                add(MenuEntry.Item(res.getString(R.string.nag_net_play_on, renderer.name), { renderers.play(renderer, files, at) }, icon = NagIcons.Cast))
            }
            add(MenuEntry.Item(res.getString(R.string.nag_cast_play_on), { renderers.choose(files, at) }, icon = NagIcons.Cast))
        }
    }
}

/** A folder's files being gathered to play: its name, and a way to give up. */
@Composable
private fun GatheringStrip(name: String, onCancel: () -> Unit) {
    val dimens = NagTheme.dimens
    HorizontalRule()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = dimens.pagePadding, vertical = dimens.gapXs)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val text = stringResource(R.string.nag_net_gathering, name)
        Text(text, style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.width(120.dp).padding(horizontal = dimens.gapS)) { NagProgressBar(null, label = text) }
        NagButton(stringResource(R.string.nag_sc_cancel), onCancel)
    }
}

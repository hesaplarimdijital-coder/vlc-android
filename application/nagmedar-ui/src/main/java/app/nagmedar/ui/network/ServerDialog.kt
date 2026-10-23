package app.nagmedar.ui.network

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.tabMovesFocus
import app.nagmedar.ui.theme.NagTheme

/** What the add-server form finds wrong: an address's faults, or a password without its user. */
enum class DraftError(@StringRes val message: Int) {
    HostMissing(R.string.nag_net_error_host_missing),
    HostInvalid(R.string.nag_net_error_host_invalid),
    PortInvalid(R.string.nag_net_error_port),
    PathInvalid(R.string.nag_net_error_path),
    UserMissing(R.string.nag_net_error_user),
    ;

    companion object {
        fun of(error: AddressError): DraftError = when (error) {
            AddressError.HostMissing -> HostMissing
            AddressError.HostInvalid -> HostInvalid
            AddressError.PortInvalid -> PortInvalid
            AddressError.PathInvalid -> PathInvalid
        }
    }
}

/**
 * S-206, S-1301: a server as the add-server form holds it. [editing] is the saved server being
 * changed, null for a new one; its kept sign-in ([signInKept]) is not shown: it stays unless the
 * user changes or forgets it. A whole address typed or pasted as the host ("smb://nas/Filmler")
 * fills the protocol, the host, the port and the folder.
 */
@Stable
class ServerDraft(
    val editing: SavedServer? = null,
    address: ServerAddress? = null,
    name: String = "",
    /** A UPnP server's address is libVLC's own: only its name can be changed. */
    val fixedUrl: String? = null,
    val signInKept: Boolean = false,
) {
    var protocol by mutableStateOf(address?.protocol ?: NetworkProtocol.Smb)
    var host by mutableStateOf(address?.host.orEmpty())
        private set
    var port by mutableStateOf(address?.port?.toString().orEmpty())
    var path by mutableStateOf(address?.path.orEmpty())
    var name by mutableStateOf(name)
    var user by mutableStateOf("")
    var password by mutableStateOf("")
    var showPassword by mutableStateOf(false)
    var remember by mutableStateOf(true)

    /** The kept sign-in replaced by what is typed (it is not shown); true at once without one. */
    var changeSignIn by mutableStateOf(!signInKept)

    /** The kept sign-in to be forgotten. */
    var forgetSignIn by mutableStateOf(false)

    /** Saving was tried: every fault shows, a missing host too. */
    var attempted by mutableStateOf(false)

    /** [family] chosen: its plain protocol, or the one of it already chosen. */
    fun choose(family: ProtocolFamily) {
        if (protocol.family != family) protocol = NetworkProtocol.entries.first { it.family == family }
    }

    /** The host typed; a whole address fills the form instead (its password, if any, is not taken). */
    fun typeHost(text: String) {
        if ("://" !in text) {
            host = text
            return
        }
        val parsed = ServerAddress.parse(text)
        if (parsed == null || !parsed.protocol.typed) {
            host = text
            return
        }
        protocol = parsed.protocol
        host = parsed.host
        port = parsed.port?.toString().orEmpty()
        path = parsed.path
        val authority = text.substringAfter("://").substringBefore('/')
        if ('@' in authority && parsed.protocol.signIn) {
            user = authority.substringBeforeLast('@').substringBefore(':')
            changeSignIn = true
        }
    }

    /** The port typed: null for the protocol's own, -1 when it is no number. */
    private val portNumber: Int?
        get() = port.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: -1 }

    val address: ServerAddress get() = ServerAddress(protocol, host.trim(), portNumber, path)

    /** The address libVLC is given: never a user or a password in it. */
    val url: String get() = fixedUrl ?: address.url

    /** Whether a user name and a password are asked for. */
    val asksSignIn: Boolean get() = fixedUrl == null && protocol.signIn

    val errors: Set<DraftError>
        get() = buildSet {
            if (fixedUrl == null) address.errors.forEach { add(DraftError.of(it)) }
            if (asksSignIn && changeSignIn && password.isNotEmpty() && user.isBlank()) add(DraftError.UserMissing)
        }

    /** The faults shown now: a missing host only once saving was tried. */
    val shownErrors: Set<DraftError>
        get() = if (attempted) errors else errors - DraftError.HostMissing

    val valid: Boolean get() = errors.isEmpty()

    /** The name it is saved under: the one typed, else the folder's or the host's. */
    val displayName: String
        get() = name.trim().ifEmpty { ServerAddress.segments(path).lastOrNull() ?: host.trim().ifEmpty { fixedUrl?.let(::displayHost).orEmpty() } }

    /** What saving does with the sign-in. */
    val signInChange: SignInChange
        get() = when {
            !asksSignIn -> SignInChange.Forget
            !changeSignIn -> if (forgetSignIn) SignInChange.Forget else SignInChange.Unchanged
            user.isBlank() && password.isEmpty() -> SignInChange.Forget
            else -> SignInChange.Use(Credentials(user, password), remember)
        }

    companion object {
        /** The form for [server], saved before. */
        fun of(server: SavedServer): ServerDraft {
            val address = ServerAddress.parse(server.url)
            val fixed = server.url.takeIf { address == null || !address.protocol.typed }
            return ServerDraft(server, address?.takeIf { fixed == null }, server.name, fixed, server.signedIn)
        }

        /** The form for [server], found on the network, to be saved. */
        fun of(server: DiscoveredServer): ServerDraft {
            val address = ServerAddress.parse(server.url)
            val fixed = server.url.takeIf { address == null || !address.protocol.typed }
            return ServerDraft(null, address?.takeIf { fixed == null }, server.name, fixed)
        }
    }
}

/** The add-server form in its dialog. */
@Composable
fun ServerDialog(draft: ServerDraft, onSave: () -> Unit, onCancel: () -> Unit, onRemove: () -> Unit = {}) {
    NagDialog(onDismissRequest = onCancel) {
        ServerDialogSurface(draft, onSave, onCancel, onRemove)
    }
}

/**
 * S-206: adding a server, or changing one: the protocol (SMB, FTP with its TLS, SFTP, NFS, WebDAV
 * over HTTP or HTTPS), the host, the port, the folder, the name shown, the user name and the
 * password, and whether the sign-in is kept on this device, sealed. The address being made shows
 * under the form, never with the password. A fault shows under its field; saving with one shows
 * them all and saves nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerDialogSurface(draft: ServerDraft, onSave: () -> Unit, onCancel: () -> Unit, onRemove: () -> Unit = {}) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    val errors = draft.shownErrors
    val save = {
        draft.attempted = true
        if (draft.valid) onSave()
    }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val narrow = maxWidth < 600.dp
        Column(
            Modifier
                .padding(if (narrow) 8.dp else dimens.gapL)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = maxHeight)
                .background(colors.surfaceRaised, shape)
                .border(1.dp, colors.borderStrong, shape)
                .padding(dimens.gapL)
                .testTag("server-dialog"),
        ) {
            Text(
                stringResource(if (draft.editing != null) R.string.nag_net_edit_server else R.string.nag_net_add_server_title),
                style = NagTheme.type.title,
                color = colors.text,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.padding(top = dimens.gapS))
            HorizontalRule()
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = dimens.gapM),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (draft.fixedUrl == null) {
                    FormGroup(stringResource(R.string.nag_net_protocol)) {
                        // a phone scrolls the protocols sideways
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            SegmentedChoice(
                                options = ProtocolFamily.typed.map { it to it.label },
                                selected = draft.protocol.family,
                                onSelect = draft::choose,
                                modifier = Modifier.testTag("server-protocol"),
                            )
                        }
                    }
                    when (draft.protocol.family) {
                        ProtocolFamily.Ftp -> FormGroup(stringResource(R.string.nag_net_ftp_tls)) {
                            SegmentedChoice(
                                options = listOf(
                                    NetworkProtocol.Ftp to stringResource(R.string.nag_net_tls_none),
                                    NetworkProtocol.Ftps to NetworkProtocol.Ftps.label,
                                    NetworkProtocol.Ftpes to NetworkProtocol.Ftpes.label,
                                ),
                                selected = draft.protocol,
                                onSelect = { draft.protocol = it },
                            )
                        }
                        ProtocolFamily.WebDav -> NagCheckbox(
                            draft.protocol == NetworkProtocol.WebDavs,
                            { draft.protocol = if (it) NetworkProtocol.WebDavs else NetworkProtocol.WebDav },
                            stringResource(R.string.nag_net_https),
                        )
                        else -> Unit
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        FormField(
                            stringResource(R.string.nag_net_host),
                            draft.host,
                            draft::typeHost,
                            hint = stringResource(R.string.nag_net_host_hint),
                            error = errors.firstOrNull { it == DraftError.HostMissing || it == DraftError.HostInvalid },
                            keyboardType = KeyboardType.Uri,
                            modifier = Modifier.widthIn(min = 200.dp).then(if (narrow) Modifier.fillMaxWidth() else Modifier.width(320.dp)),
                            tag = "server-host",
                        )
                        if (draft.protocol.port) {
                            FormField(
                                stringResource(R.string.nag_net_port),
                                draft.port,
                                { draft.port = it.filter { c -> !c.isWhitespace() } },
                                hint = draft.protocol.defaultPort?.toString().orEmpty(),
                                error = errors.firstOrNull { it == DraftError.PortInvalid },
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.width(if (narrow) 140.dp else 120.dp),
                                tag = "server-port",
                            )
                        }
                    }
                    FormField(
                        stringResource(R.string.nag_net_path),
                        draft.path,
                        { draft.path = it },
                        hint = stringResource(if (draft.protocol == NetworkProtocol.Smb) R.string.nag_net_path_hint_smb else R.string.nag_net_path_hint),
                        error = errors.firstOrNull { it == DraftError.PathInvalid },
                        tag = "server-path",
                    )
                }
                FormField(
                    stringResource(R.string.nag_net_name),
                    draft.name,
                    { draft.name = it },
                    hint = draft.displayName.ifEmpty { stringResource(R.string.nag_net_name_hint) },
                    tag = "server-name",
                )
                if (draft.asksSignIn) SignInFields(draft, errors, onDone = save)
                Text(
                    stringResource(R.string.nag_net_address_preview, draft.url),
                    style = NagTheme.type.mono,
                    color = colors.textSecondary,
                    modifier = Modifier.testTag("server-url"),
                )
            }
            HorizontalRule()
            Spacer(Modifier.padding(top = dimens.gapM))
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (draft.editing != null) NagButton(stringResource(R.string.nag_net_remove_server), onRemove)
                Spacer(Modifier.weight(1f))
                NagButton(stringResource(R.string.nag_sc_cancel), onCancel)
                NagButton(stringResource(R.string.nag_net_save), save, primary = true, modifier = Modifier.testTag("server-save"))
            }
        }
    }
}

/** The user name, the password and whether they are kept; a kept sign-in is only said to be there. */
@Composable
private fun SignInFields(draft: ServerDraft, errors: Set<DraftError>, onDone: () -> Unit) {
    val colors = NagTheme.colors
    FormGroup(stringResource(R.string.nag_net_sign_in)) {
        if (!draft.changeSignIn) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(if (draft.forgetSignIn) R.string.nag_net_sign_in_forgotten else R.string.nag_net_sign_in_kept),
                    style = NagTheme.type.body,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                NagButton(stringResource(R.string.nag_net_sign_in_change), { draft.changeSignIn = true })
                if (!draft.forgetSignIn) NagButton(stringResource(R.string.nag_net_sign_in_forget), { draft.forgetSignIn = true })
            }
            return@FormGroup
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CredentialFields(
                user = draft.user,
                onUser = { draft.user = it },
                password = draft.password,
                onPassword = { draft.password = it },
                showPassword = draft.showPassword,
                onShowPassword = { draft.showPassword = it },
                userError = errors.firstOrNull { it == DraftError.UserMissing },
                onDone = onDone,
            )
            NagCheckbox(draft.remember, { draft.remember = it }, stringResource(R.string.nag_net_remember))
        }
    }
}

/** User name and password fields, the password hidden unless shown; Enter in the password finishes. */
@Composable
internal fun CredentialFields(
    user: String,
    onUser: (String) -> Unit,
    password: String,
    onPassword: (String) -> Unit,
    showPassword: Boolean,
    onShowPassword: (Boolean) -> Unit,
    userError: DraftError? = null,
    onDone: () -> Unit,
) {
    FormField(
        stringResource(R.string.nag_net_user),
        user,
        onUser,
        hint = stringResource(R.string.nag_net_user_hint),
        error = userError,
        tag = "server-user",
    )
    FormField(
        stringResource(R.string.nag_net_password),
        password,
        onPassword,
        hint = "",
        password = !showPassword,
        keyboardType = KeyboardType.Password,
        imeAction = ImeAction.Done,
        onDone = onDone,
        tag = "server-password",
    )
    NagCheckbox(showPassword, onShowPassword, stringResource(R.string.nag_net_show_password))
}

/** A part of the form: its name over its control. */
@Composable
internal fun FormGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
        content()
    }
}

/**
 * A one-line text field with its name, and its fault under it (a red border, read out at once).
 * [password] hides what is typed.
 */
@Composable
internal fun FormField(
    title: String,
    value: String,
    onValue: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    error: DraftError? = null,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    tag: String? = null,
) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusControl)
    val focusManager = LocalFocusManager.current
    val message = error?.let { stringResource(it.message) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = NagTheme.type.labelSmall, color = colors.textSecondary)
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = dimens.minTarget.coerceAtLeast(40.dp))
                .background(colors.surface, shape)
                .border(if (error != null) 1.5.dp else 1.dp, if (error != null) colors.danger else colors.borderStrong, shape)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty() && hint.isNotEmpty()) Text(hint, style = NagTheme.type.body, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            BasicTextField(
                value = value,
                onValueChange = onValue,
                singleLine = true,
                textStyle = NagTheme.type.body.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .tabMovesFocus(focusManager)
                    .semantics {
                        contentDescription = title
                        if (message != null) error(message)
                    }
                    .then(if (tag != null) Modifier.testTag(tag) else Modifier),
            )
        }
        if (message != null) {
            Text(
                message,
                style = NagTheme.type.labelSmall,
                color = colors.danger,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
}

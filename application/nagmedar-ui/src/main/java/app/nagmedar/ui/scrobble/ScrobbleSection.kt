package app.nagmedar.ui.scrobble

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.NagTextField
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.openLink
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme

/** What Settings › Scrobble can be asked to do; the screenshot and UI tests give their own. */
class ScrobbleActions(
    val setEnabled: (ScrobbleServiceId, Boolean) -> Unit = { _, _ -> },
    val openSignIn: (ScrobbleServiceId) -> Unit = {},
    val closeSignIn: () -> Unit = {},
    val signIn: (ScrobbleServiceId, SignInInput) -> Unit = { _, _ -> },
    val forget: (ScrobbleServiceId) -> Unit = {},
    val sendNow: () -> Unit = {},
    val openLink: (String) -> Unit = {},
)

/**
 * S-1008: Settings › Music › Scrobble, on the app's scrobbling: for Last.fm and ListenBrainz a
 * switch, the account signed in, how many scrobbles wait to be sent, "send now" and "forget
 * account". [controller] is the screenshot and UI tests' own.
 */
@Composable
internal fun ScrobbleSection(controller: ScrobbleController? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val active = controller ?: remember {
        Scrobbling.get(context).let { ScrobbleController(it.settings, it.store, it.services, it.scheduler, scope) }
    }
    LaunchedEffect(active) { active.watch() }
    val actions = remember(active) {
        ScrobbleActions(active::setEnabled, active::openSignIn, active::closeSignIn, active::signIn, active::forget, active::sendNow) { openLink(context, it) }
    }
    ScrobbleContent(active.rows, active.form, actions)
}

/** The section as it looks for [rows] and the sign-in form's state [signIn]. */
@Composable
internal fun ScrobbleContent(rows: List<ScrobbleRow>, signIn: SignInState, actions: ScrobbleActions) {
    SettingsSection(stringResource(R.string.nag_scrobble_title)) {
        Text(stringResource(R.string.nag_scrobble_intro), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
        rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalRule()
            ServiceBlock(row, signIn, actions)
        }
    }
}

@Composable
private fun serviceName(id: ScrobbleServiceId): String = stringResource(
    when (id) {
        ScrobbleServiceId.LastFm -> R.string.nag_scrobble_lastfm
        ScrobbleServiceId.ListenBrainz -> R.string.nag_scrobble_listenbrainz
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceBlock(row: ScrobbleRow, signIn: SignInState, actions: ScrobbleActions) {
    val dimens = NagTheme.dimens
    val colors = NagTheme.colors
    val tag = "scrobble-${row.id.key}"
    val name = serviceName(row.id)
    Column(Modifier.fillMaxWidth()) {
        SettingRow(
            name,
            subtitle = when {
                !row.available -> null
                row.account != null -> stringResource(R.string.nag_scrobble_signed_in, row.account)
                else -> stringResource(R.string.nag_scrobble_signed_out)
            },
        ) {
            NagSwitch(
                checked = row.enabled,
                onCheckedChange = { actions.setEnabled(row.id, it) },
                label = stringResource(if (row.enabled) R.string.nag_scrobble_on else R.string.nag_scrobble_off),
                enabled = row.available && row.account != null,
                modifier = Modifier.testTag("$tag-switch"),
            )
        }
        if (!row.available) {
            Text(stringResource(R.string.nag_scrobble_no_key, name), style = NagTheme.type.labelSmall, color = colors.warning, modifier = Modifier.testTag("$tag-no-key"))
        }
        if (row.held) {
            Text(stringResource(R.string.nag_scrobble_held, name), style = NagTheme.type.labelSmall, color = colors.warning, modifier = Modifier.testTag("$tag-held"))
        }
        if (row.available && row.account != null) {
            Text(
                if (row.waiting == 0) stringResource(R.string.nag_scrobble_none_waiting) else pluralStringResource(R.plurals.nag_scrobble_waiting, row.waiting, row.waiting),
                style = NagTheme.type.labelSmall,
                color = colors.textTertiary,
                modifier = Modifier.testTag("$tag-waiting"),
            )
        }
        Spacer(Modifier.height(dimens.gapXs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            if (row.account == null) {
                NagButton(
                    stringResource(R.string.nag_scrobble_sign_in),
                    { actions.openSignIn(row.id) },
                    enabled = row.available,
                    modifier = Modifier.testTag("$tag-sign-in"),
                )
            } else {
                NagButton(stringResource(R.string.nag_scrobble_send_now), actions.sendNow, enabled = row.waiting > 0 && !row.held, modifier = Modifier.testTag("$tag-send-now"))
                if (row.held) {
                    NagButton(stringResource(R.string.nag_scrobble_sign_in_again), { actions.openSignIn(row.id) }, modifier = Modifier.testTag("$tag-sign-in"))
                }
                NagButton(stringResource(R.string.nag_scrobble_forget), { actions.forget(row.id) }, modifier = Modifier.testTag("$tag-forget"))
            }
        }
        val form = signIn
        if ((form is SignInState.Open && form.service == row.id) || (form is SignInState.Working && form.service == row.id)) {
            Spacer(Modifier.height(dimens.gapS))
            SignInForm(row.id, row.account.orEmpty(), form, actions)
        }
        Spacer(Modifier.height(dimens.gapXs))
    }
}

/** The sign-in: Last.fm's user name and password, which are sent once; ListenBrainz's token, pasted from its settings page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SignInForm(id: ScrobbleServiceId, account: String, state: SignInState, actions: ScrobbleActions) {
    val dimens = NagTheme.dimens
    val colors = NagTheme.colors
    val tag = "scrobble-${id.key}"
    val working = state is SignInState.Working
    var name by remember(id) { mutableStateOf(if (id == ScrobbleServiceId.LastFm) account else "") }
    var secret by remember(id) { mutableStateOf("") }
    val lastFm = id == ScrobbleServiceId.LastFm
    val ready = secret.isNotBlank() && (!lastFm || name.isNotBlank()) && !working
    val submit = { if (ready) actions.signIn(id, SignInInput(name, secret)) }
    Column(Modifier.fillMaxWidth().testTag("$tag-form"), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
        if (lastFm) {
            NagTextField(name, { name = it }, stringResource(R.string.nag_scrobble_user), enabled = !working, modifier = Modifier.testTag("$tag-user"))
            NagTextField(secret, { secret = it }, stringResource(R.string.nag_scrobble_password), secret = true, enabled = !working, onDone = { submit() }, modifier = Modifier.testTag("$tag-password"))
            Text(stringResource(R.string.nag_scrobble_password_hint), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        } else {
            NagTextField(secret, { secret = it }, stringResource(R.string.nag_scrobble_token), secret = true, enabled = !working, onDone = { submit() }, modifier = Modifier.testTag("$tag-token"))
            Text(stringResource(R.string.nag_scrobble_token_hint), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        }
        val open = state as? SignInState.Open
        if (open?.problem != null) {
            Text(problemText(id, open.problem, open.code), style = NagTheme.type.labelSmall, color = colors.warning, modifier = Modifier.testTag("$tag-problem"))
        }
        if (working) {
            Text(stringResource(R.string.nag_scrobble_signing_in), style = NagTheme.type.labelSmall, color = colors.textSecondary, modifier = Modifier.testTag("$tag-working"))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(dimens.gapS), verticalArrangement = Arrangement.spacedBy(dimens.gapS)) {
            NagButton(
                stringResource(if (lastFm) R.string.nag_scrobble_sign_in_confirm else R.string.nag_scrobble_check_token),
                submit,
                primary = true,
                enabled = ready,
                modifier = Modifier.testTag("$tag-submit"),
            )
            if (!lastFm) {
                NagButton(stringResource(R.string.nag_scrobble_open_listenbrainz), { actions.openLink(ListenBrainzService.SETTINGS_URL) }, modifier = Modifier.testTag("$tag-open-settings"))
            }
            NagButton(stringResource(R.string.nag_scrobble_cancel), actions.closeSignIn, modifier = Modifier.testTag("$tag-cancel"))
        }
    }
}

@Composable
private fun problemText(id: ScrobbleServiceId, problem: SignInProblem, code: Int): String = when (problem) {
    SignInProblem.Refused -> stringResource(if (id == ScrobbleServiceId.LastFm) R.string.nag_scrobble_refused_lastfm else R.string.nag_scrobble_refused_listenbrainz)
    SignInProblem.Offline -> stringResource(R.string.nag_scrobble_offline)
    SignInProblem.NoKey -> stringResource(R.string.nag_scrobble_no_key, serviceName(id))
    SignInProblem.Server -> stringResource(R.string.nag_scrobble_server, serviceName(id), code)
    SignInProblem.NotKept -> stringResource(R.string.nag_scrobble_not_kept)
}

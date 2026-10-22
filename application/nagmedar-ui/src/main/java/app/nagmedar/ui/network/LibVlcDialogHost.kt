package app.nagmedar.ui.network

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.nagmedar.ui.components.NagDialog
import androidx.lifecycle.compose.LifecycleStartEffect
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagCheckbox
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagProgressBar
import app.nagmedar.ui.components.StateIconTile
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.theme.NagTheme

/** The requests the app's windows show, in the order they came; main thread. */
class PromptQueue : PromptHost {
    // process-wide state: made in the global snapshot by ProcessState.warmUp, before any window composes
    val shown: SnapshotStateList<LibVlcPrompt> = mutableStateListOf()

    override fun show(prompt: LibVlcPrompt): Boolean {
        shown += prompt
        return true
    }

    override fun gone(prompt: LibVlcPrompt): Boolean = shown.remove(prompt)
}

/**
 * S-1301: libVLC's requests that no folder answers (a file played from a server that asks for a
 * sign-in, a certificate to accept, a long task's progress), shown over the new interface while
 * its window is in front, as VLC's windows show them in the classic one; libVLC waits for them.
 */
@Composable
fun LibVlcDialogHost() {
    val context = LocalContext.current
    // the process's: the requests outlive a window recreated or gone a moment
    val queue = NetworkDialogs.queue
    LifecycleStartEffect(queue) {
        NetworkDialogs.install(context.applicationContext)
        NetworkDialogs.windowStarted()
        onStopOrDispose { NetworkDialogs.windowStopped() }
    }
    val prompt = queue.shown.firstOrNull() ?: return
    // answered elsewhere, or taken back by libVLC
    LaunchedEffect(prompt) { if (prompt.settled) queue.shown.remove(prompt) }
    NagDialog(onDismissRequest = {
        if (prompt.kind != PromptKind.Progress || prompt.cancelText != null) {
            prompt.dismiss()
            queue.shown.remove(prompt)
        }
    }, properties = DialogProperties()) {
        PromptSurface(prompt) { queue.shown.remove(prompt) }
    }
}

/** A request in its window: a sign-in's fields, a question's actions or a progress line, then [onDone]. */
@Composable
fun PromptSurface(prompt: LibVlcPrompt, onDone: () -> Unit) {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusCard)
    val surface = Modifier
        .padding(dimens.gapM)
        .widthIn(max = 480.dp)
        .fillMaxWidth()
        .background(colors.surfaceRaised, shape)
        .border(1.dp, colors.borderStrong, shape)
    when (prompt.kind) {
        PromptKind.Login -> LoginPrompt(prompt, surface, onDone)
        PromptKind.Question -> PromptPanel(prompt, surface, focusFirst = true) { action ->
            if (action == null) prompt.dismiss() else prompt.action(action)
            onDone()
        }
        PromptKind.Progress -> Column(surface.padding(dimens.gapL).testTag("libvlc-progress"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PromptWords(prompt)
            NagProgressBar(if (prompt.indeterminate) null else prompt.position.coerceIn(0f, 1f), label = prompt.title)
            prompt.cancelText?.let { cancel ->
                NagButton(cancel, {
                    prompt.dismiss()
                    onDone()
                }, modifier = Modifier.align(Alignment.End))
            }
        }
    }
}

/**
 * libVLC's question in its own words (a server's certificate, a file to overwrite), on a folder's
 * page or in a window: its actions, the first one foremost, and its way out; [onAnswer] hears 1,
 * 2 or null (turned down). The words libVLC writes in English are said in the interface's
 * language where they are known ([LibVlcWords]: a certificate's "accept for 24 hours", "accept
 * permanently", "cancel"). In a window ([focusFirst]) the keyboard starts on the way out, the
 * safe answer; Tab goes through the actions, Enter answers, Esc turns it down.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PromptPanel(prompt: LibVlcPrompt, modifier: Modifier = Modifier, focusFirst: Boolean = false, onAnswer: (Int?) -> Unit) {
    val dimens = NagTheme.dimens
    val res = LocalContext.current.resources
    val cancel = remember { FocusRequester() }
    if (focusFirst) LaunchedEffect(prompt) { runCatching { cancel.requestFocus() } }
    Column(modifier.widthIn(max = 480.dp).padding(dimens.gapL), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StateIconTile(if (prompt.warning) NagIcons.Warning else NagIcons.Shield, if (prompt.warning) StateTone.Warning else StateTone.Info)
        PromptWords(prompt)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NagButton(
                prompt.cancelText?.let { LibVlcWords.localized(res, it) } ?: stringResource(R.string.nag_sc_cancel),
                { onAnswer(null) },
                modifier = Modifier.focusRequester(cancel).testTag("prompt-cancel"),
            )
            prompt.action2?.let { NagButton(LibVlcWords.localized(res, it), { onAnswer(2) }, modifier = Modifier.testTag("prompt-action-2")) }
            prompt.action1?.let { NagButton(LibVlcWords.localized(res, it), { onAnswer(1) }, primary = true, modifier = Modifier.testTag("prompt-action-1")) }
        }
    }
}

@Composable
private fun PromptWords(prompt: LibVlcPrompt) {
    val colors = NagTheme.colors
    val res = LocalContext.current.resources
    Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (prompt.title.isNotEmpty()) {
            Text(LibVlcWords.localized(res, prompt.title), style = NagTheme.type.title, color = colors.text, modifier = Modifier.semantics { heading() })
        }
        if (prompt.text.isNotEmpty()) Text(LibVlcWords.localized(res, prompt.text), style = NagTheme.type.body, color = colors.textSecondary)
    }
}

/** libVLC's sign-in: the user name it suggests, the password, and keeping them when it offers to. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LoginPrompt(prompt: LibVlcPrompt, modifier: Modifier, onDone: () -> Unit) {
    val dimens = NagTheme.dimens
    var user by remember(prompt) { mutableStateOf(prompt.defaultUser.orEmpty()) }
    var password by remember(prompt) { mutableStateOf("") }
    var show by remember(prompt) { mutableStateOf(false) }
    var keep by remember(prompt) { mutableStateOf(false) }
    val submit = {
        prompt.login(user, password, keep && prompt.asksStore)
        onDone()
    }
    Column(modifier.padding(dimens.gapL).testTag("libvlc-login"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StateIconTile(NagIcons.Lock, StateTone.Info)
        PromptWords(prompt)
        CredentialFields(user, { user = it }, password, { password = it }, show, { show = it }, onDone = submit)
        if (prompt.asksStore) NagCheckbox(keep, { keep = it }, stringResource(R.string.nag_net_remember))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            NagButton(stringResource(R.string.nag_sc_cancel), {
                prompt.dismiss()
                onDone()
            })
            NagButton(stringResource(R.string.nag_net_sign_in_action), submit, primary = true)
        }
    }
}

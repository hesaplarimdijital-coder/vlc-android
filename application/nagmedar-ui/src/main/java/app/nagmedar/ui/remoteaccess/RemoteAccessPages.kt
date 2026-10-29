package app.nagmedar.ui.remoteaccess

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.copyToClipboard
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.components.DialogCard
import app.nagmedar.ui.components.DialogFrame
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.videolan.resources.ACTION_START_SERVER
import org.videolan.resources.ACTION_STOP_SERVER
import org.videolan.tools.KEY_ENABLE_REMOTE_ACCESS
import org.videolan.tools.Settings
import org.videolan.vlc.util.RemoteAccessUtils
import org.videolan.vlc.util.UrlUtils

/** The remote access windows of the Nağmedar interface. */
enum class RemoteAccessPage { Addresses, HowItWorks }

/** Which remote access window is asked for: from Settings › Remote access, its first enable, or its notification. */
object RemoteAccessPages {
    var page by mutableStateOf<RemoteAccessPage?>(null)
        private set

    fun open(which: RemoteAccessPage) {
        page = which
    }

    fun close() {
        page = null
    }
}

/**
 * The remote access windows over the shell: the addresses (with their QR codes) and the
 * connections, how it works, and the one-time code a new browser asks for while this window is
 * in front (the notification shows it too).
 */
@Composable
fun RemoteAccessHost() {
    when (RemoteAccessPages.page) {
        RemoteAccessPage.Addresses -> AddressesDialog(onDismiss = RemoteAccessPages::close)
        RemoteAccessPage.HowItWorks -> HowItWorksDialog(onDismiss = RemoteAccessPages::close)
        null -> Unit
    }
    val code by RemoteAccessUtils.otpFlow.collectAsState()
    var put by remember { mutableStateOf<String?>(null) }
    code?.takeIf { it != put }?.let { shown ->
        OneTimeCodeDialog(
            code = shown,
            onNotMe = {
                RemoteAccessUtils.otpCodeRemoveFlow.value = shown
                RemoteAccessUtils.otpFlow.value = null
            },
            onDismiss = { put = shown },
        )
    }
}

/** The server's state as the page says it, and whether it can be started or stopped from there. */
private enum class ServerState(val label: Int, val canToggle: Boolean) {
    NotStarted(R.string.nag_ra_state_not_started, false),
    Connecting(R.string.nag_ra_state_connecting, false),
    Started(R.string.nag_ra_state_started, true),
    Stopping(R.string.nag_ra_state_stopping, false),
    Stopped(R.string.nag_ra_state_stopped, true),
    Error(R.string.nag_ra_state_error, true),
    ;

    companion object {
        fun of(name: String): ServerState = when (name) {
            "STARTED" -> Started
            "CONNECTING" -> Connecting
            "STOPPING" -> Stopping
            "STOPPED" -> Stopped
            "ERROR" -> Error
            else -> NotStarted
        }
    }
}

private fun control(context: Context, action: String) {
    context.sendBroadcast(Intent(action).setPackage(context.packageName))
}

/** The server's status with Start or Stop, the addresses it answers on (each with its QR code, Copy and Share), the browsers connected. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddressesDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = NagTheme.colors
    val enabled = remember(context) { Settings.getInstance(context).getBoolean(KEY_ENABLE_REMOTE_ACCESS, false) }
    val status by RemoteAccessUtils.statusFlow.collectAsState()
    val addresses by RemoteAccessUtils.addressesFlow.collectAsState()
    val connections by RemoteAccessUtils.connectionsFlow.collectAsState()
    val state = ServerState.of(status)
    var qrFor by rememberSaveable { mutableStateOf<String?>(null) }
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.nag_ra_addresses_title), Modifier.testTag("ra-addresses-page"), cardWidth = 520.dp) {
                Column(Modifier.heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(if (enabled) state.label else R.string.nag_ra_state_off),
                            style = NagTheme.type.bodyStrong,
                            color = if (state == ServerState.Error) colors.danger else colors.text,
                            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        if (enabled && state.canToggle) {
                            NagButton(
                                stringResource(if (state == ServerState.Started) R.string.nag_ra_stop else R.string.nag_ra_start),
                                { control(context, if (state == ServerState.Started) ACTION_STOP_SERVER else ACTION_START_SERVER) },
                                icon = if (state == ServerState.Started) NagIcons.Stop else NagIcons.Play,
                            )
                        }
                    }
                    if (!enabled) {
                        Text(stringResource(R.string.nag_ra_turn_on_first), style = NagTheme.type.body, color = colors.textSecondary)
                    } else if (state == ServerState.Started) {
                        Heading(stringResource(R.string.nag_ra_addresses_heading))
                        Text(stringResource(R.string.nag_ra_addresses_hint), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                        val shownQr = qrFor ?: addresses.firstOrNull()
                        addresses.forEach { address ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(address, style = NagTheme.type.mono, color = colors.text)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (address != shownQr) NagButton(stringResource(R.string.nag_ra_show_qr), { qrFor = address })
                                    NagButton(stringResource(R.string.nag_ra_copy), { copy(context, address) })
                                    NagButton(stringResource(R.string.nag_ra_share), { share(context, address) })
                                }
                                if (address == shownQr) QrCode(address)
                            }
                        }
                        Heading(stringResource(R.string.nag_ra_connections))
                        if (connections.isEmpty()) {
                            Text(stringResource(R.string.nag_ra_connections_none), style = NagTheme.type.labelSmall, color = colors.textSecondary)
                        } else {
                            connections.forEach { Text(it, style = NagTheme.type.mono, color = colors.text) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_ra_how), { RemoteAccessPages.open(RemoteAccessPage.HowItWorks) })
                    NagButton(stringResource(R.string.nag_info_close), onDismiss, primary = true)
                }
            }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text.uppercase(currentLocale()), style = NagTheme.type.caption, color = NagTheme.colors.textTertiary, modifier = Modifier.semantics { heading() })
}

/** The address as a QR code, dark on light whatever the theme, for a phone's camera. */
@Composable
private fun QrCode(address: String) {
    val qr by produceState<ImageBitmap?>(null, address) {
        value = withContext(Dispatchers.Default) { runCatching { UrlUtils.generateQRCode(address, QR_PIXELS).asImageBitmap() }.getOrNull() }
    }
    val description = stringResource(R.string.nag_ra_qr_of, address)
    Box(
        Modifier.size(212.dp).background(Color.White, RoundedCornerShape(8.dp)).padding(6.dp).semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        qr?.let { Image(it, contentDescription = null, modifier = Modifier.size(200.dp)) }
    }
}

private const val QR_PIXELS = 512

private fun copy(context: Context, address: String) {
    copyToClipboard(context, context.getString(R.string.nag_ra_settings), address, R.string.nag_ra_copied)
}

private fun share(context: Context, address: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, address)
    runCatching { context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/** One step of "How it works": its title and what it says. */
private class HowStep(val title: Int, val text: Int)

private val HOW_STEPS = listOf(
    HowStep(R.string.nag_ra_how_welcome_title, R.string.nag_ra_how_welcome),
    HowStep(R.string.nag_ra_how_control_title, R.string.nag_ra_how_control),
    HowStep(R.string.nag_ra_how_code_title, R.string.nag_ra_how_code),
    HowStep(R.string.nag_ra_how_encryption_title, R.string.nag_ra_how_encryption),
    HowStep(R.string.nag_ra_how_content_title, R.string.nag_ra_how_content),
)

/** What remote access is and how it is kept safe, a step at a time (Back, Next, Done). */
@Composable
private fun HowItWorksDialog(onDismiss: () -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val current = HOW_STEPS[step.coerceIn(0, HOW_STEPS.lastIndex)]
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.nag_ra_how_title), Modifier.testTag("ra-how-page"), cardWidth = 460.dp) {
                Text(stringResource(R.string.nag_ra_how_step, step + 1, HOW_STEPS.size), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
                Column(Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(current.title), style = NagTheme.type.title, color = NagTheme.colors.text)
                    Text(stringResource(current.text), style = NagTheme.type.body, color = NagTheme.colors.textSecondary)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (step > 0) NagButton(stringResource(R.string.nag_ra_how_back), { step-- })
                    if (step < HOW_STEPS.lastIndex) {
                        NagButton(stringResource(R.string.nag_ra_how_next), { step++ }, primary = true)
                    } else {
                        NagButton(stringResource(R.string.nag_lib_done), onDismiss, primary = true)
                    }
                }
            }
        }
    }
}

/** The six digits a new browser asks for, with "It is not me", which turns that browser away. */
@Composable
private fun OneTimeCodeDialog(code: String, onNotMe: () -> Unit, onDismiss: () -> Unit) {
    NagDialog(onDismissRequest = onDismiss) {
        DialogFrame(onDismiss) {
            DialogCard(stringResource(R.string.nag_ra_code_title), Modifier.testTag("ra-code"), cardWidth = 400.dp) {
                Text(stringResource(R.string.nag_ra_code_hint), style = NagTheme.type.body, color = NagTheme.colors.textSecondary)
                val spoken = code.toList().joinToString(" ")
                Text(
                    code.chunked(3).joinToString(" "),
                    style = NagTheme.type.display,
                    color = NagTheme.colors.text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = spoken },
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    NagButton(stringResource(R.string.nag_ra_code_not_me), {
                        onNotMe()
                        onDismiss()
                    })
                    NagButton(stringResource(R.string.nag_info_close), onDismiss, primary = true)
                }
            }
        }
    }
}

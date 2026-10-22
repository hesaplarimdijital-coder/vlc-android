package app.nagmedar.ui.podcast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.NagDialog
import app.nagmedar.data.PodcastFeed
import app.nagmedar.ui.R
import app.nagmedar.ui.audio.locale
import app.nagmedar.ui.components.MenuButton
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.player.navigate.DialogCard
import app.nagmedar.ui.player.navigate.DialogFrame
import app.nagmedar.ui.theme.NagTheme
import java.text.NumberFormat
import java.util.Locale

/** The speed as the buttons write it: "1×", "1,25×", "0,5×" (the decimal comma follows the language). */
internal fun speedLabel(speed: Float, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }.format(speed.toDouble()) + "×"

/**
 * S-1103, S-1104: one podcast's own settings in a small window: the speed its episodes play at, the seconds skipped at the
 * start (an intro) and before the end (an outro), whether new episodes are told in a notification, and the automatic
 * download of the newest and how many of its episodes are kept. Each choice is kept as it is made.
 */
@Composable
fun ShowSettingsDialog(feed: PodcastFeed, actions: PodcastActions) {
    NagDialog(onDismissRequest = actions.onDismissShowSettings) {
        DialogFrame(actions.onDismissShowSettings) { ShowSettingsSurface(feed, actions) }
    }
}

@Composable
internal fun ShowSettingsSurface(feed: PodcastFeed, actions: PodcastActions) {
    val res = LocalContext.current.resources
    val locale = res.locale()
    val colors = NagTheme.colors
    DialogCard(stringResource(R.string.nag_pod_show_settings), Modifier.testTag("podcast-show-settings"), cardWidth = 480.dp) {
        Text(feed.title, style = NagTheme.type.label, color = colors.textSecondary, maxLines = 2)
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            SettingRow(stringResource(R.string.nag_pod_set_speed), subtitle = stringResource(R.string.nag_pod_set_speed_hint)) {
                MenuButton(
                    speedLabel(feed.speed, locale),
                    entries = {
                        PlaybackRules.SPEEDS.map { speed ->
                            MenuEntry.Item(
                                speedLabel(speed, locale),
                                { actions.onSetPlayback(feed.id, speed, feed.skipIntroSeconds, feed.skipOutroSeconds) },
                                checked = PlaybackRules.sameSpeed(speed, feed.speed),
                            )
                        }
                    },
                    modifier = Modifier.testTag("podcast-set-speed"),
                )
            }
            SettingRow(stringResource(R.string.nag_pod_set_intro), subtitle = stringResource(R.string.nag_pod_set_intro_hint)) {
                SecondsMenu(feed.skipIntroSeconds, "podcast-set-intro") { actions.onSetPlayback(feed.id, feed.speed, it, feed.skipOutroSeconds) }
            }
            SettingRow(stringResource(R.string.nag_pod_set_outro), subtitle = stringResource(R.string.nag_pod_set_outro_hint)) {
                SecondsMenu(feed.skipOutroSeconds, "podcast-set-outro") { actions.onSetPlayback(feed.id, feed.speed, feed.skipIntroSeconds, it) }
            }
            SettingRow(stringResource(R.string.nag_pod_set_notify), subtitle = stringResource(R.string.nag_pod_set_notify_hint)) {
                NagSwitch(
                    checked = feed.notify,
                    onCheckedChange = { actions.onSetNotify(feed.id, it) },
                    label = stringResource(if (feed.notify) R.string.nag_pod_set_on else R.string.nag_pod_set_off),
                    modifier = Modifier.testTag("podcast-set-notify"),
                )
            }
            SettingRow(stringResource(R.string.nag_pod_set_auto), subtitle = stringResource(R.string.nag_pod_set_auto_hint)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (feed.autoDownload) {
                        MenuButton(
                            pluralStringResource(R.plurals.nag_pod_set_auto_latest, feed.autoDownloadCount, feed.autoDownloadCount),
                            entries = {
                                AUTO_COUNTS.map { count ->
                                    MenuEntry.Item(
                                        res.getQuantityString(R.plurals.nag_pod_set_auto_latest, count, count),
                                        { actions.onSetAutoDownload(feed.id, true, count) },
                                        checked = count == feed.autoDownloadCount,
                                    )
                                }
                            },
                            modifier = Modifier.testTag("podcast-set-auto-count"),
                        )
                    }
                    NagSwitch(
                        checked = feed.autoDownload,
                        onCheckedChange = { actions.onSetAutoDownload(feed.id, it, feed.autoDownloadCount) },
                        label = stringResource(if (feed.autoDownload) R.string.nag_pod_set_on else R.string.nag_pod_set_off),
                        modifier = Modifier.testTag("podcast-set-auto"),
                    )
                }
            }
            SettingRow(stringResource(R.string.nag_pod_set_keep), subtitle = stringResource(R.string.nag_pod_set_keep_hint)) {
                MenuButton(
                    if (feed.keepLatest == 0) stringResource(R.string.nag_pod_set_keep_all) else pluralStringResource(R.plurals.nag_pod_set_keep_latest, feed.keepLatest, feed.keepLatest),
                    entries = {
                        KEEP_COUNTS.map { count ->
                            MenuEntry.Item(
                                if (count == 0) res.getString(R.string.nag_pod_set_keep_all) else res.getQuantityString(R.plurals.nag_pod_set_keep_latest, count, count),
                                { actions.onSetKeepLatest(feed.id, count) },
                                checked = count == feed.keepLatest,
                            )
                        }
                    },
                    modifier = Modifier.testTag("podcast-set-keep"),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            NagButton(stringResource(R.string.nag_pod_ok), actions.onDismissShowSettings, primary = true, modifier = Modifier.testTag("podcast-show-settings-ok"))
        }
    }
}

/** A menu of the seconds an intro or an outro may be: [PlaybackRules.SKIP_SECONDS]; "Yok" for none. */
@Composable
private fun SecondsMenu(seconds: Int, tag: String, onSelect: (Int) -> Unit) {
    val res = LocalContext.current.resources
    MenuButton(
        secondsLabel(res, seconds),
        entries = { PlaybackRules.SKIP_SECONDS.map { value -> MenuEntry.Item(secondsLabel(res, value), { onSelect(value) }, checked = value == seconds) } },
        modifier = Modifier.testTag(tag),
    )
}

private fun secondsLabel(res: android.content.res.Resources, seconds: Int): String =
    if (seconds == 0) res.getString(R.string.nag_pod_set_none) else res.getString(R.string.nag_pod_set_seconds, seconds)

private val AUTO_COUNTS = listOf(1, 2, 3, 5, 10)
private val KEEP_COUNTS = listOf(0, 3, 5, 10, 20, 50)

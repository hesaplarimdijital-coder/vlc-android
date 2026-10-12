package app.nagmedar.ui.lyrics

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
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.screens.SettingsSection
import kotlinx.coroutines.launch
import org.videolan.tools.NAGMEDAR_LYRICS_ONLINE
import org.videolan.tools.Settings

/**
 * S-1003: Settings › Lyrics: whether LRCLIB is asked when the device has no synced lyrics (what
 * it is sent is said), and how many tracks' lyrics are kept from it for offline use, which can be
 * cleared. The screenshot tests give [online], [kept] and the callbacks; the app reads the setting
 * and the cache itself.
 */
@Composable
internal fun LyricsSection(
    online: Boolean? = null,
    kept: Int? = null,
    onOnlineChange: ((Boolean) -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lookUp by remember { mutableStateOf(online ?: LyricsCenter.onlineAllowed(context)) }
    var count by remember { mutableStateOf(kept) }
    if (kept == null) {
        LaunchedEffect(Unit) { count = runCatching { LyricsCenter.finder(context).downloadedCount() }.getOrNull() }
    }
    SettingsSection(stringResource(R.string.nag_lyrics_settings)) {
        SettingRow(stringResource(R.string.nag_lyrics_online), subtitle = stringResource(R.string.nag_lyrics_online_hint)) {
            NagSwitch(
                checked = lookUp,
                onCheckedChange = { on ->
                    lookUp = on
                    if (onOnlineChange != null) {
                        onOnlineChange(on)
                    } else {
                        Settings.getInstance(context).edit().putBoolean(NAGMEDAR_LYRICS_ONLINE, on).apply()
                    }
                },
                label = stringResource(if (lookUp) R.string.nag_lyrics_online_on else R.string.nag_lyrics_online_off),
                modifier = Modifier.testTag("lyrics-online"),
            )
        }
        val held = count
        SettingRow(
            stringResource(R.string.nag_lyrics_kept),
            subtitle = when {
                held == null -> null
                held == 0 -> stringResource(R.string.nag_lyrics_kept_none)
                else -> pluralStringResource(R.plurals.nag_lyrics_kept_count, held, held)
            },
        ) {
            NagButton(
                stringResource(R.string.nag_lyrics_clear),
                {
                    if (onClear != null) {
                        onClear()
                        count = 0
                    } else {
                        scope.launch { count = runCatching { LyricsCenter.finder(context).clearDownloaded() }.map { 0 }.getOrDefault(count) }
                    }
                },
                enabled = (held ?: 0) > 0,
                modifier = Modifier.testTag("lyrics-clear"),
            )
        }
    }
}

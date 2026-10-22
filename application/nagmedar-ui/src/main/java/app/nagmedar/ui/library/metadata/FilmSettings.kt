package app.nagmedar.ui.library.metadata

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.library.currentLocale
import app.nagmedar.ui.library.sizeLabel
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Settings › Film information: whether films and series are looked up on TMDB (what is sent is
 * said), only on Wi-Fi or on any network, the language of the overviews, what is kept and how to
 * clear it, and where it comes from. A build without a TMDB key says the feature is off, one
 * without an OMDb key that the IMDb and Rotten Tomatoes ratings are left out.
 */
@Composable
internal fun FilmInfoSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version by FilmInfoCenter.settingsChanges.collectAsState()
    val enabled = remember(version) { FilmInfoCenter.enabled(context) }
    val wifiOnly = remember(version) { FilmInfoCenter.wifiOnly(context) }
    val language = remember(version) { FilmInfoCenter.language(context) }
    val state by FilmInfoCenter.state.collectAsState()
    val foundFlow = remember(context) { runCatching { FilmInfoCenter.foundCount(context) }.getOrNull() ?: flowOf(0) }
    val found by foundFlow.collectAsState(initial = 0)
    var imageBytes by remember { mutableStateOf<Long?>(null) }
    var cleared by remember { mutableStateOf(0) }
    LaunchedEffect(found, cleared) { imageBytes = runCatching { FilmImages.diskBytes(context) }.getOrNull() }
    val available = FilmInfoCenter.tmdbAvailable
    SettingsSection(stringResource(R.string.nag_film_settings)) {
        if (!available) {
            Text(stringResource(R.string.nag_film_key_missing), style = NagTheme.type.labelSmall, color = NagTheme.colors.warning,
                modifier = Modifier.testTag("film-info-no-key"))
            Spacer(Modifier.height(NagTheme.dimens.gapS))
        } else if (!FilmInfoCenter.omdbAvailable) {
            Text(stringResource(R.string.nag_film_omdb_missing), style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary,
                modifier = Modifier.testTag("film-info-no-omdb"))
            Spacer(Modifier.height(NagTheme.dimens.gapS))
        }
        SettingRow(stringResource(R.string.nag_film_settings_on), subtitle = stringResource(R.string.nag_film_settings_on_hint)) {
            NagSwitch(
                checked = enabled && available,
                onCheckedChange = { FilmInfoCenter.setEnabled(context, it) },
                label = stringResource(if (enabled && available) R.string.nag_covers_online_on else R.string.nag_covers_online_off),
                enabled = available,
                modifier = Modifier.testTag("film-info-on"),
            )
        }
        SettingRow(stringResource(R.string.nag_film_settings_wifi), subtitle = stringResource(R.string.nag_film_settings_wifi_hint)) {
            NagSwitch(
                checked = wifiOnly,
                onCheckedChange = { FilmInfoCenter.setWifiOnly(context, it) },
                label = stringResource(if (wifiOnly) R.string.nag_covers_online_on else R.string.nag_covers_online_off),
                enabled = available && enabled,
            )
        }
        SettingRow(stringResource(R.string.nag_film_settings_language), subtitle = stringResource(R.string.nag_film_settings_language_hint)) {
            SegmentedChoice(
                options = listOf(
                    FilmLanguage.Turkish to stringResource(R.string.nag_film_lang_tr),
                    FilmLanguage.English to stringResource(R.string.nag_film_lang_en),
                ),
                selected = language,
                onSelect = { FilmInfoCenter.setLanguage(context, it) },
            )
        }
        val locale = currentLocale()
        val kept = if (found == 0) {
            stringResource(R.string.nag_film_kept_none)
        } else {
            pluralStringResource(R.plurals.nag_film_kept_count, found, found) + (imageBytes?.takeIf { it > 0 }?.let { " · " + sizeLabel(it, locale) } ?: "")
        }
        SettingRow(stringResource(R.string.nag_film_settings_kept), subtitle = kept) {
            NagButton(
                stringResource(R.string.nag_film_settings_clear),
                {
                    scope.launch {
                        runCatching { FilmInfoCenter.clear(context) }
                        cleared++
                    }
                },
                enabled = found > 0 || (imageBytes ?: 0) > 0,
                modifier = Modifier.testTag("film-info-clear"),
            )
        }
        stateText(state)?.let {
            Text(it, style = NagTheme.type.labelSmall, color = NagTheme.colors.textSecondary)
            Spacer(Modifier.height(NagTheme.dimens.gapS))
        }
        FilmAttribution()
    }
}

@Composable
private fun stateText(state: FilmFetchState): String? = when (state) {
    FilmFetchState.Idle -> null
    is FilmFetchState.Working -> pluralStringResource(R.plurals.nag_film_state_working, state.remaining, state.remaining)
    FilmFetchState.WaitingForWifi -> stringResource(R.string.nag_film_state_wifi)
    FilmFetchState.Offline -> stringResource(R.string.nag_film_state_offline)
    FilmFetchState.KeyRefused -> stringResource(R.string.nag_film_state_refused)
    FilmFetchState.Limited -> stringResource(R.string.nag_film_state_limited)
}

/** Where film information comes from, as TMDB's terms ask it to be said: in Settings and on the about page. */
@Composable
fun FilmAttribution() {
    val colors = NagTheme.colors
    Text(stringResource(R.string.nag_film_tmdb_notice), style = NagTheme.type.labelSmall, color = colors.textSecondary)
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.nag_film_omdb_notice), style = NagTheme.type.labelSmall, color = colors.textSecondary)
}

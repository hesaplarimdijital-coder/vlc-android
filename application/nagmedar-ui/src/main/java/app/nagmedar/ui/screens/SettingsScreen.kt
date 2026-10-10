package app.nagmedar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.NagmedarPreferences
import app.nagmedar.ui.R
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.SegmentedChoice
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.components.SwatchChoice
import app.nagmedar.ui.input.ShortcutGroup
import app.nagmedar.ui.input.ShortcutRegistry
import app.nagmedar.ui.input.ShortcutScope
import app.nagmedar.ui.lab.LabTool
import app.nagmedar.ui.lab.LabTools
import app.nagmedar.ui.lab.ReportDialog
import app.nagmedar.ui.lab.findActivity
import app.nagmedar.ui.shell.KeyCap
import app.nagmedar.ui.theme.Accent
import app.nagmedar.ui.theme.DensityPreference
import app.nagmedar.ui.theme.NagTheme
import app.nagmedar.ui.theme.ThemeMode
import kotlinx.coroutines.launch
import org.videolan.vlc.BuildConfig

/** Index of the keyboard shortcut section in [SettingsScreen]'s list, for "Keyboard shortcuts" (Ctrl+/). */
const val SETTINGS_SHORTCUTS_ITEM = 3

@Composable
fun SettingsScreen(
    prefs: NagmedarPreferences,
    onOpenClassicSettings: () -> Unit,
    onOpenLicences: () -> Unit,
    listState: LazyListState = rememberLazyListState(),
) {
    val dimens = NagTheme.dimens
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = dimens.gapXl),
        horizontalAlignment = Alignment.Start,
    ) {
        item { PageHeader(title = stringResource(R.string.nag_nav_settings)) }
        item {
            SettingsSection(stringResource(R.string.nag_settings_appearance)) {
                SettingRow(stringResource(R.string.nag_settings_theme)) {
                    SegmentedChoice(
                        options = listOf(
                            ThemeMode.System to stringResource(R.string.nag_theme_system),
                            ThemeMode.Light to stringResource(R.string.nag_theme_light),
                            ThemeMode.Dark to stringResource(R.string.nag_theme_dark),
                            ThemeMode.Oled to stringResource(R.string.nag_theme_oled),
                        ),
                        selected = prefs.themeMode,
                        onSelect = prefs::updateThemeMode,
                    )
                }
                SettingRow(stringResource(R.string.nag_settings_accent)) {
                    SwatchChoice(
                        options = listOf(
                            Triple(Accent.Blue, Accent.Blue.base, stringResource(R.string.nag_accent_blue)),
                            Triple(Accent.Amber, Accent.Amber.base, stringResource(R.string.nag_accent_amber)),
                            Triple(Accent.Green, Accent.Green.base, stringResource(R.string.nag_accent_green)),
                            Triple(Accent.Coral, Accent.Coral.base, stringResource(R.string.nag_accent_coral)),
                        ),
                        selected = prefs.accent,
                        onSelect = prefs::updateAccent,
                    )
                }
            }
        }
        item {
            SettingsSection(stringResource(R.string.nag_settings_input)) {
                SettingRow(stringResource(R.string.nag_settings_density)) {
                    SegmentedChoice(
                        options = listOf(
                            DensityPreference.Auto to stringResource(R.string.nag_theme_system),
                            DensityPreference.Touch to stringResource(R.string.nag_density_touch),
                            DensityPreference.Pointer to stringResource(R.string.nag_density_pointer),
                        ),
                        selected = prefs.density,
                        onSelect = prefs::updateDensity,
                    )
                }
                Text(stringResource(R.string.nag_density_auto), style = NagTheme.type.labelSmall, color = NagTheme.colors.textTertiary)
            }
        }
        item { ShortcutsSection() }
        item {
            SettingsSection(stringResource(R.string.nag_settings_about)) {
                Text(
                    stringResource(R.string.nag_settings_version, BuildConfig.VLC_VERSION_NAME, BuildConfig.LIBVLC_VERSION),
                    style = NagTheme.type.body,
                    color = NagTheme.colors.textSecondary,
                )
                Spacer(Modifier.height(dimens.gapM))
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.gapS)) {
                    NagButton(stringResource(R.string.nag_settings_classic_settings), onOpenClassicSettings)
                    NagButton(stringResource(R.string.nag_settings_licences), onOpenLicences)
                }
            }
        }
        item { LabSection() }
    }
}

/**
 * Settings > Lab: the MVP0 spike experiments that need the real device (USB DAC, TV over HDMI).
 * Each one produces a text report to copy or share.
 */
@Composable
private fun LabSection() {
    val colors = NagTheme.colors
    val dimens = NagTheme.dimens
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var running by remember { mutableStateOf<LabTool?>(null) }
    var result by remember { mutableStateOf<Pair<String, String>?>(null) }
    SettingsSection(stringResource(R.string.nag_settings_lab)) {
        Text(stringResource(R.string.nag_lab_intro), style = NagTheme.type.labelSmall, color = colors.textTertiary)
        Spacer(Modifier.height(dimens.gapS))
        LabTools.all.forEach { tool ->
            val title = stringResource(tool.title)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = dimens.rowHeight).padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = dimens.gapM)) {
                    Text("${tool.spike} · $title", style = NagTheme.type.bodyStrong, color = colors.text)
                    Text(stringResource(tool.description), style = NagTheme.type.labelSmall, color = colors.textTertiary)
                }
                NagButton(
                    text = stringResource(if (running === tool) R.string.nag_lab_running else R.string.nag_lab_run),
                    onClick = {
                        val activity = context.findActivity() ?: return@NagButton
                        running = tool
                        scope.launch {
                            val report = runCatching { tool.run(activity) }.getOrElse { "${it::class.java.simpleName}: ${it.message}" }
                            running = null
                            result = title to report
                        }
                    },
                    enabled = running == null,
                )
            }
        }
    }
    result?.let { (title, report) -> ReportDialog(title, report, onDismiss = { result = null }) }
}

@Composable
private fun ShortcutsSection() {
    val colors = NagTheme.colors
    val resources = LocalContext.current.resources
    SettingsSection(stringResource(R.string.nag_settings_shortcuts)) {
        ShortcutGroup.entries.forEach { group ->
            val shortcuts = ShortcutRegistry.defaults.filter { it.group == group }
            if (shortcuts.isEmpty()) return@forEach
            Text(
                stringResource(group.title),
                style = NagTheme.type.label,
                color = colors.textSecondary,
                modifier = Modifier.padding(top = NagTheme.dimens.gapM, bottom = 4.dp),
            )
            shortcuts.forEach { shortcut ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(shortcut.label), style = NagTheme.type.body, color = colors.text, modifier = Modifier.weight(1f))
                    if (shortcut.scope == ShortcutScope.Player) {
                        Text(stringResource(R.string.nag_shortcuts_player_only), style = NagTheme.type.caption, color = colors.textTertiary,
                            modifier = Modifier.padding(end = 8.dp))
                    }
                    KeyCap(shortcut.chord.label(resources))
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
                .padding(dimens.gapL),
        ) {
            Text(title, style = NagTheme.type.title, color = colors.text, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(dimens.gapS))
            HorizontalRule()
            Spacer(Modifier.height(dimens.gapS))
            content()
        }
    }
}

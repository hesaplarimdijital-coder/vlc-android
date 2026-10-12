package app.nagmedar.ui.covers

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.R
import app.nagmedar.ui.components.MenuEntry
import app.nagmedar.ui.components.MenuPopup
import app.nagmedar.ui.components.NagButton
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.SettingRow
import app.nagmedar.ui.components.collectState
import app.nagmedar.ui.components.focusRing
import app.nagmedar.ui.components.nagClickable
import app.nagmedar.ui.components.rememberMenuState
import app.nagmedar.ui.input.contextMenuGesture
import app.nagmedar.ui.screens.SettingsSection
import app.nagmedar.ui.theme.NagTheme
import kotlinx.coroutines.launch
import org.videolan.tools.NAGMEDAR_COVERS_ONLINE
import org.videolan.tools.Settings
import kotlin.math.roundToInt

/** S-1006: a cover, or the music note where there is none. */
@Composable
fun CoverTile(image: ImageBitmap?, size: Dp, modifier: Modifier = Modifier, corner: Dp = 6.dp) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(corner)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(colors.surfaceRaised)
            .then(if (image == null) Modifier.border(1.dp, colors.border, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Icon(NagIcons.Music, contentDescription = null, tint = colors.textTertiary, modifier = Modifier.size(size / 2.5f))
        }
    }
}

/** S-1006: a cover that opens its menu: a click, a right click or a long press. */
@Composable
fun CoverMenuButton(image: ImageBitmap?, size: Dp, label: String, entries: () -> List<MenuEntry>, modifier: Modifier = Modifier) {
    val menu = rememberMenuState()
    val interaction = remember { MutableInteractionSource() }
    val state = interaction.collectState()
    var height by remember { mutableIntStateOf(0) }
    val shape = RoundedCornerShape(6.dp)
    Box(modifier) {
        CoverTile(
            image,
            size,
            Modifier
                .onSizeChanged { height = it.height }
                .focusRing(state, shape)
                .contextMenuGesture { at -> menu.open(IntOffset(at.x.roundToInt(), at.y.roundToInt())) }
                .nagClickable(interaction, { menu.open(IntOffset(0, height + 4)) }, role = Role.Button, onClickLabel = label)
                .testTag("cover-menu"),
        )
        MenuPopup(visible = menu.isOpen, onDismiss = menu::close, entries = if (menu.isOpen) entries() else emptyList(), offset = menu.offset ?: IntOffset.Zero, minWidth = 200.dp)
    }
}

/**
 * S-1006: Settings › Covers: whether Cover Art Archive is asked for an album whose file and folder
 * have no cover (what is sent is said), and how many albums' covers found are kept, which can be
 * cleared (the ones chosen by hand stay). The screenshot tests give [online], [kept] and the
 * callbacks; the app reads the setting and the folder itself.
 */
@Composable
internal fun CoversSection(
    online: Boolean? = null,
    kept: Int? = null,
    onOnlineChange: ((Boolean) -> Unit)? = null,
    onClear: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lookUp by remember { mutableStateOf(online ?: CoverCenter.onlineAllowed(context)) }
    var count by remember { mutableStateOf(kept) }
    if (kept == null) {
        LaunchedEffect(Unit) { count = runCatching { CoverCenter.finder(context).foundCount() }.getOrNull() }
    }
    SettingsSection(stringResource(R.string.nag_covers_settings)) {
        SettingRow(stringResource(R.string.nag_covers_online), subtitle = stringResource(R.string.nag_covers_online_hint)) {
            NagSwitch(
                checked = lookUp,
                onCheckedChange = { on ->
                    lookUp = on
                    if (onOnlineChange != null) {
                        onOnlineChange(on)
                    } else {
                        Settings.getInstance(context).edit().putBoolean(NAGMEDAR_COVERS_ONLINE, on).apply()
                    }
                },
                label = stringResource(if (lookUp) R.string.nag_covers_online_on else R.string.nag_covers_online_off),
                modifier = Modifier.testTag("covers-online"),
            )
        }
        val held = count
        SettingRow(
            stringResource(R.string.nag_covers_kept),
            subtitle = when {
                held == null -> null
                held == 0 -> stringResource(R.string.nag_covers_kept_none)
                else -> pluralStringResource(R.plurals.nag_covers_kept_count, held, held)
            },
        ) {
            NagButton(
                stringResource(R.string.nag_covers_clear),
                {
                    if (onClear != null) {
                        onClear()
                        count = 0
                    } else {
                        scope.launch {
                            count = runCatching { CoverCenter.finder(context).clearFound() }.map { 0 }.getOrDefault(count)
                            CoverCenter.changed()
                        }
                    }
                },
                enabled = (held ?: 0) > 0,
                modifier = Modifier.testTag("covers-clear"),
            )
        }
    }
}

package app.nagmedar.ui

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.LoadingState
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.theme.LocalNagMotion
import app.nagmedar.ui.theme.NagMotion
import app.nagmedar.ui.theme.NagTheme

/** A motion token as the board lists it: name, duration, curve (null: none) and what uses it. */
class MotionToken(val name: String, val duration: String, val easing: Easing?, val curve: String, val use: String)

val motionTokens = listOf(
    MotionToken("Anında", "0 ms", null, "—", "Fareyle üzerine gelme, odak, basılı tutma ve seçim"),
    MotionToken("Kısa", "${NagMotion.SHORT} ms", NagMotion.Exit, "çıkış · 0,3 0 1 1", "Ekrandan çıkan her şey: menü, ipucu, eski ekran, eski adım"),
    MotionToken("Orta", "${NagMotion.MEDIUM} ms", NagMotion.Enter, "giriş · 0 0 0 1",
        "Gelen ekran; ilk açılışta yeni adım ${NagMotion.STEP_SLIDE_DP} dp kayarak gelir"),
    MotionToken("Uzun", "${NagMotion.LONG} ms", NagMotion.Standard, "standart · 0,2 0 0 1", "Ekranda kalıp yer ya da boyut değiştiren büyük yüzeyler"),
    MotionToken("Döngü", "${NagMotion.SWEEP} – ${NagMotion.SHIMMER} ms", LinearEasing, "doğrusal",
        "Yalnızca süren iş: iskelet parıltısı, belirsiz ilerleme"),
)

/** "PictureInPicture" → "Picture​In​Picture": names may break between their words. */
private fun breakable(name: String): String = name.replace(Regex("(?<=[a-z])(?=[A-Z])"), "​")

/** S-203: the icon set (16 and 22 dp, every icon by name) and the motion tokens (1480 x 925). */
@Composable
fun IconsMotionBoard(modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(start = 32.dp, end = 32.dp, top = 26.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column {
            Text("İkonlar ve hareket", style = NagTheme.type.display, color = colors.text)
            Text(
                "${NagIcons.all.size} ikon · 16 × 16 ızgara, 1,6 çizgi, yuvarlak uç · az ve hızlı hareket; animasyonlar kaldırılınca hiçbir şey kıpırdamaz",
                style = NagTheme.type.body,
                color = colors.textSecondary,
            )
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BoardCard("İKONLAR", "işaretçide 16 dp · dokunmatikte 22 dp · yön okları sağdan sola dillerde aynalanır", Modifier.weight(1.9f)) {
                IconGrid(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp))
            }
            BoardCard("HAREKET", "süreler, eğriler ve kullanıldıkları yer", Modifier.weight(1f)) {
                MotionList(Modifier.fillMaxSize().padding(14.dp))
            }
        }
    }
}

@Composable
private fun BoardCard(kind: String, where: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxHeight()
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.borderStrong, shape)
            .testTag("board-$kind"),
    ) {
        Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ToneBadge(kind, StateTone.Neutral)
            Spacer(Modifier.width(8.dp))
            Text(where, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1)
        }
        HorizontalRule()
        Column(Modifier.weight(1f).fillMaxWidth(), content = content)
    }
}

@Composable
private fun IconGrid(modifier: Modifier) {
    Column(modifier) {
        NagIcons.all.chunked(10).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth()) {
                row.forEach { icon -> IconTile(icon, Modifier.weight(1f).fillMaxHeight()) }
                repeat(10 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, modifier: Modifier) {
    val colors = NagTheme.colors
    Column(modifier.padding(4.dp).testTag("icon-${icon.name}"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(16.dp))
            Icon(icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(breakable(icon.name), style = NagTheme.type.caption, color = colors.textSecondary, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@Composable
private fun MotionList(modifier: Modifier) {
    val colors = NagTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        motionTokens.forEach { token ->
            Row(Modifier.fillMaxWidth().testTag("motion-${token.name}"), verticalAlignment = Alignment.CenterVertically) {
                EasingCurve(token.easing, Modifier.size(width = 56.dp, height = 40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(token.name, style = NagTheme.type.bodyStrong, color = colors.text)
                        Spacer(Modifier.width(8.dp))
                        Text(token.duration, style = NagTheme.type.mono, color = colors.textSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text(token.curve, style = NagTheme.type.caption, color = colors.textTertiary)
                    }
                    Text(token.use, style = NagTheme.type.labelSmall, color = colors.textSecondary)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        HorizontalRule()
        Text("Animasyonları kaldır açıkken", style = NagTheme.type.bodyStrong, color = colors.text)
        Text(
            "Geçişler anında olur. Döngüler durur: iskelet düz kalır, belirsiz ilerleme kesikli bir çizgi olarak bekler.",
            style = NagTheme.type.labelSmall,
            color = colors.textSecondary,
        )
        CompositionLocalProvider(LocalNagMotion provides NagMotion.Reduced) {
            Box(Modifier.fillMaxWidth().testTag("reduced-sample")) {
                LoadingState(title = "Kütüphane taranıyor", posters = 5, dense = true)
            }
        }
    }
}

/** The easing curve from (0, 0) to (1, 1) in a small frame; a dot when there is no motion. */
@Composable
private fun EasingCurve(easing: Easing?, modifier: Modifier) {
    val colors = NagTheme.colors
    val frame = colors.border
    val line = colors.accent
    Canvas(modifier.background(colors.background, RoundedCornerShape(6.dp)).border(1.dp, frame, RoundedCornerShape(6.dp))) {
        val pad = 6.dp.toPx()
        val w = size.width - 2 * pad
        val h = size.height - 2 * pad
        if (easing == null) {
            drawCircle(line, radius = 3.dp.toPx(), center = Offset(pad + w, pad))
            return@Canvas
        }
        val path = Path()
        for (i in 0..40) {
            val t = i / 40f
            val x = pad + t * w
            val y = pad + h - easing.transform(t) * h
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

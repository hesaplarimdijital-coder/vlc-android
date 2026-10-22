package app.nagmedar.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.nagmedar.ui.components.AlertBanner
import app.nagmedar.ui.components.HorizontalRule
import app.nagmedar.ui.components.LoadingState
import app.nagmedar.ui.components.NagIcons
import app.nagmedar.ui.components.NagSwitch
import app.nagmedar.ui.components.NoResultsState
import app.nagmedar.ui.components.SkeletonBlock
import app.nagmedar.ui.components.StateAction
import app.nagmedar.ui.components.StatePanel
import app.nagmedar.ui.components.StateTone
import app.nagmedar.ui.components.ToneBadge
import app.nagmedar.ui.components.UndoBar
import app.nagmedar.ui.components.UndoBarState
import app.nagmedar.ui.theme.NagTheme

/** One card of the states board: its badge, where it appears, and the state's composable. */
class StateCard(val kind: String, val tone: StateTone, val where: String, val content: @Composable ColumnScope.() -> Unit)

private val noop = {}

private fun panel(kind: String, tone: StateTone, where: String, icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String,
                  primary: String?, secondary: String?, extra: (@Composable ColumnScope.() -> Unit)? = null) =
    StateCard(kind, tone, where) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            StatePanel(
                tone = tone,
                icon = icon,
                title = title,
                description = description,
                primary = primary?.let { StateAction(it, noop) },
                secondary = secondary?.let { StateAction(it, noop) },
                dense = true,
                content = extra,
            )
        }
    }

/** The twelve states of the "Durumlar" mockup, with its copy. */
val stateCards: List<StateCard> = listOf(
    panel("BOŞ", StateTone.Neutral, "Kütüphane · ilk açılış", NagIcons.FolderAdd, "Henüz klasör eklenmedi",
        "Film ve müzik klasörlerinizi seçin ya da bağlı diski tarayın.", "Klasör ekle", "SAMSUNG T7 diskini tara"),
    panel("İZİN", StateTone.Info, "Android medya erişimi", NagIcons.Shield, "Medya dosyalarına erişim",
        "Tarama yalnızca cihazda yapılır; hiçbir dosya yüklenmez.", "İzin ver", "Yalnızca klasör seç"),
    StateCard("YÜKLENİYOR", StateTone.Info, "Kütüphane taraması") {
        LoadingState(
            title = "Kütüphane taranıyor",
            progress = 1248f / 3900f,
            progressText = "1 248 / 3 900",
            detail = "Arka planda sürer · bulunanlar hemen oynatılabilir",
            dense = true,
        )
    },
    StateCard("SONUÇ YOK", StateTone.Neutral, "Arama") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            NoResultsState(
                title = "“kuzey ışıkları 1080p” için sonuç yok",
                detail = "2 filtre etkin: 4K, Dolby Vision",
                suggestions = listOf(
                    StateAction("Filtreleri temizle", noop),
                    StateAction("Ağ kaynaklarında ara", noop),
                    StateAction("“Kuzey Işığı” 2160p", noop),
                ),
                dense = true,
            )
        }
    },
    panel("HATA", StateTone.Error, "Video oynatıcı · ses izi", NagIcons.ErrorCircle, "TrueHD 7.1 donanımla çözülemiyor",
        "Görüntü sürüyor; ses için yazılım çözücü ya da diğer iz seçilebilir.", "Yazılımla çöz", "DTS-HD MA 5.1'e geç"),
    panel("BAĞLANTI", StateTone.Warning, "Harici disk", NagIcons.DriveRemoved, "SAMSUNG T7 çıkarıldı",
        "Oynatma 1:11:45'te durdu. Disk geri takılınca kaldığı yerden sürer.", null, "Kapat") {
        var on by remember { mutableStateOf(true) }
        NagSwitch(checked = on, onCheckedChange = { on = it }, label = "Otomatik devam et")
    },
    panel("AĞ", StateTone.Error, "Radyo akışı", NagIcons.NetworkOff, "Caz Kıyısı'na ulaşılamıyor",
        "3 deneme başarısız · zaman aşımı (10 sn). Son 6 sn tampondan çaldı.", "Yeniden dene", "Yedek akış · AAC 128"),
    panel("KOTA", StateTone.Warning, "OpenSubtitles", NagIcons.Subtitle, "Günlük indirme kotası doldu",
        "20/20 indirme · yarın 03:00'te yenilenir.", "Cihazda oluştur", "Hesap bağla"),
    StateCard("UYARI", StateTone.Warning, "Ses çıkışı değişti") {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Bottom) {
            Column(Modifier.padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SkeletonBlock(Modifier.fillMaxWidth(0.6f).height(9.dp))
                SkeletonBlock(Modifier.fillMaxWidth(0.4f).height(9.dp))
            }
            AlertBanner(
                tone = StateTone.Warning,
                title = "Bit-perfect devre dışı",
                message = "Bluetooth kulaklık bağlandı: LDAC 990 kbps, 96 → 48 kHz yeniden örnekleme.",
                actions = listOf(StateAction("Tamam", noop), StateAction("USB DAC'a dön", noop)),
            )
        }
    },
    panel("DİSK DOLU", StateTone.Error, "TV Lab · remux", NagIcons.DiskFull, "Hedefte yer yetersiz",
        "52,7 GB gerekiyor, 31,2 GB boş. İş kuyrukta bekletildi.", "Başka konum seç", "Kuyrukta beklet"),
    panel("BAŞARI", StateTone.Success, "TV Lab · remux tamamlandı", NagIcons.SuccessCircle, "Kuzey_Isigi_B4.mkv hazır",
        "Video paketleri birebir: 198 761 / 198 761 hash eşleşti · 6 dk 12 sn.", "Klasörde göster", "TV'de oynat (DLNA)"),
    StateCard("GERİ AL", StateTone.Neutral, "Bildirim çubuğu") {
        val undo = remember {
            UndoBarState().apply {
                show("Gecikme −350 ms olarak kaydedildi", "Geri al") {}
                show("3 parça sıradan kaldırıldı", "Geri al", shortcut = "Ctrl+Z") {}
            }
        }
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom)) {
            undo.messages.forEachIndexed { index, message ->
                UndoBar(message, onUndo = {}, onTimeout = {}, older = index < undo.messages.lastIndex)
            }
            Text("8 sn görünür · üzerine gelince durur · ekran okuyucuya duyurulur", style = NagTheme.type.labelSmall,
                color = NagTheme.colors.textSecondary)
        }
    },
)

/** The "Durumlar" board: twelve cards in a 4 x 3 grid, as in the mockup (1480 x 925). */
@Composable
fun StatesBoard(modifier: Modifier = Modifier) {
    val colors = NagTheme.colors
    Column(
        modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(start = 32.dp, end = 32.dp, top = 26.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Durumlar", style = NagTheme.type.display, color = colors.text)
                Text("Boş, izin, yükleniyor, hata, uyarı ve başarı · her durumda bir sonraki adım açık", style = NagTheme.type.body,
                    color = colors.textSecondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(StateTone.Neutral to "Nötr", StateTone.Info to "Bilgi", StateTone.Warning to "Uyarı", StateTone.Error to "Hata",
                    StateTone.Success to "Başarı").forEach { (tone, label) -> ToneBadge(label, tone) }
            }
        }
        stateCards.chunked(4).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                row.forEach { card -> StateCardFrame(card, Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun StateCardFrame(card: StateCard, modifier: Modifier) {
    val colors = NagTheme.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .clip(shape)
            .background(colors.surface, shape)
            .border(1.dp, colors.borderStrong, shape)
            .testTag("state-${card.kind}"),
    ) {
        Row(Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ToneBadge(card.kind, card.tone)
            Spacer(Modifier.width(8.dp))
            Text(card.where, style = NagTheme.type.labelSmall, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HorizontalRule()
        Column(Modifier.weight(1f).fillMaxWidth(), content = card.content)
    }
}

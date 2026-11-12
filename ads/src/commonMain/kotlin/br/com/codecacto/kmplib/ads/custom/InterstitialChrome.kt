package br.com.codecacto.kmplib.ads.custom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

/**
 * O **cromo** do intersticial em tela cheia (2.355.0) — o desenho da família cristã (D12): fundo escuro,
 * selo "Anúncio" no canto, "Fechar em N" que vira "✕ Fechar", a arte no meio (raio 26, margem 18) e o
 * rodapé "Sem anúncios com o Premium." + "Conhecer o Premium". Sem cromo (default), o intersticial é o
 * de sempre (arte em tela cheia + "X").
 *
 * Os TEXTOS vêm de quem chama, já no idioma do app (o kit da família passa os dele).
 *
 * @param closeAfterSeconds contagem antes de poder fechar, contada a partir da arte pintada (0..30).
 *   Durante a contagem nem o "voltar" fecha.
 * @param closeCountdown "Fechar em 3" para o número que falta.
 * @param footerText linha do rodapé (`null` = sem rodapé).
 * @param ctaLabel/onCtaClick ação do rodapé — fecha o anúncio e chama [onCtaClick] (abrir o Premium).
 *   Id `ads-interstitial-cta`.
 */
@Immutable
data class InterstitialChrome(
    val label: String,
    val closeLabel: String,
    val closeCountdown: (secondsLeft: Int) -> String,
    val closeAfterSeconds: Int = 3,
    val footerText: String? = null,
    val ctaLabel: String? = null,
    val onCtaClick: (() -> Unit)? = null,
    val background: Color = Color(0xFF111111),
    val contentColor: Color = Color(0xFFFFFFFF),
    val mutedColor: Color = Color(0xFFCFCFCF),
    val pillBorder: Color = Color(0xFF555555),
    val pillBackground: Color = Color(0xFF1D1D1D),
)

/** Id do CTA do rodapé do cromo. */
const val INTERSTITIAL_CTA_TEST_TAG: String = "ads-interstitial-cta"

@Composable
internal fun ChromedInterstitial(
    ad: CustomAd,
    chrome: InterstitialChrome,
    secondsLeft: Int,
    onDismiss: () -> Unit,
    onAdClick: () -> Unit,
    onLoad: (AdCreativeLoad) -> Unit,
    loadedTag: String?,
) {
    val text = TextStyle(color = chrome.contentColor, fontSize = 13.sp)
    Column(
        Modifier.fillMaxSize().background(chrome.background).windowInsetsPadding(WindowInsets.safeDrawing).testTag(AdsTestTags.INTERSTITIAL),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 26.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                chrome.label,
                style = text.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = chrome.mutedColor),
                modifier = Modifier.border(1.dp, chrome.mutedColor, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
            )
            val pill = Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(999.dp)).background(chrome.pillBackground)
                .border(1.dp, chrome.pillBorder, RoundedCornerShape(999.dp))
            if (secondsLeft > 0) {
                Box(pill.alpha(0.6f).padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite }, contentAlignment = Alignment.Center) {
                    Text(chrome.closeCountdown(secondsLeft), style = text.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(vertical = 12.dp))
                }
            } else {
                Row(
                    pill.clickable(role = Role.Button, onClick = onDismiss).testTag(AdsTestTags.BTN_FECHAR_INTERSTITIAL).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Filled.Close, null, tint = chrome.contentColor, modifier = Modifier.size(18.dp))
                    Text(chrome.closeLabel, style = text.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(vertical = 12.dp))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp).clip(RoundedCornerShape(26.dp))) {
            AsyncImage(
                model = ad.imageUrl,
                contentDescription = ad.title.ifBlank { chrome.label },
                contentScale = ContentScale.Crop,
                onState = { onLoad(it.toAdCreativeLoad()) },
                modifier = Modifier.fillMaxSize().then(if (loadedTag != null) Modifier.testTag(loadedTag) else Modifier).clickable(onClick = onAdClick),
            )
        }
        if (chrome.footerText != null || chrome.ctaLabel != null) {
            Column(
                Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                chrome.footerText?.let { Text(it, style = text.copy(fontSize = 14.sp, color = chrome.mutedColor)) }
                if (chrome.ctaLabel != null && chrome.onCtaClick != null) {
                    Text(
                        chrome.ctaLabel,
                        style = text.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline),
                        modifier = Modifier
                            .heightIn(min = 44.dp)
                            .clickable(role = Role.Button) { onDismiss(); chrome.onCtaClick.invoke() }
                            .testTag(INTERSTITIAL_CTA_TEST_TAG)
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

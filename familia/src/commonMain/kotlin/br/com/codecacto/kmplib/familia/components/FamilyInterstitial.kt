package br.com.codecacto.kmplib.familia.components

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.ads.custom.CustomAd
import br.com.codecacto.kmplib.ads.custom.InterstitialChrome
import br.com.codecacto.kmplib.ads.router.ManagedInterstitialAd
import br.com.codecacto.kmplib.familia.familiaString
import br.com.codecacto.kmplib.familia.generated.resources.*
import br.com.codecacto.kmplib.familia.theme.FamilyFixedColors

/**
 * O **intersticial da família** (02 §11): o `ManagedInterstitialAd` da kmplib (espera a arte; sem arte,
 * pula; Premium nunca vê) com o cromo do protótipo — "Anúncio", "Fechar em 3" → "✕ Fechar", rodapé
 * "Sem anúncios com o Premium." + "Conhecer o Premium" (fecha e chama [onOpenPremium]).
 *
 * Mostre SÓ depois de uma ação concluída (salvar imagem, fim do terço…), nunca durante oração, leitura,
 * contagem ou digitação; a frequência (≥ 4 min, nunca na 1ª sessão) é do app, contada em [onShown].
 */
@Composable
fun FamilyInterstitialAd(
    show: Boolean,
    onDismiss: () -> Unit,
    onOpenPremium: () -> Unit,
    onShown: ((CustomAd) -> Unit)? = null,
    closeAfterSeconds: Int = 3,
) {
    val label = familiaString(Res.string.familia_ad_label)
    val close = familiaString(Res.string.familia_ad_close)
    val closeIn = familiaString(Res.string.familia_ad_close_in, "%COUNT%")
    val footer = familiaString(Res.string.familia_ad_footer)
    val cta = familiaString(Res.string.familia_ad_cta)
    ManagedInterstitialAd(
        show = show,
        onDismiss = onDismiss,
        onShown = onShown,
        chrome = InterstitialChrome(
            label = label,
            closeLabel = close,
            closeCountdown = { n -> closeIn.replace("%COUNT%", n.toString()) },
            closeAfterSeconds = closeAfterSeconds,
            footerText = footer,
            ctaLabel = cta,
            onCtaClick = onOpenPremium,
            background = FamilyFixedColors.interstitialBackground,
            contentColor = FamilyFixedColors.interstitialContent,
            mutedColor = FamilyFixedColors.interstitialMuted,
        ),
    )
}

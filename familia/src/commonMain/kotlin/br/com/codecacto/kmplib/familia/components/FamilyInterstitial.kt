package br.com.codecacto.kmplib.familia.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import br.com.codecacto.kmplib.ads.custom.CustomAd
import br.com.codecacto.kmplib.ads.custom.InterstitialChrome
import br.com.codecacto.kmplib.ads.router.ManagedInterstitialAd
import br.com.codecacto.kmplib.familia.familiaString
import br.com.codecacto.kmplib.familia.generated.resources.*
import br.com.codecacto.kmplib.familia.theme.FamilyFixedColors
import br.com.codecacto.kmplib.familia.theme.FamilyTheme

/**
 * O **intersticial da família** (02 §11): o `ManagedInterstitialAd` da kmplib (espera a arte; sem arte,
 * pula; Premium nunca vê) com o cromo do protótipo — "Anúncio", "Fechar em 3" → "✕ Fechar", rodapé
 * "Sem anúncios com o Premium." + "Conhecer o Premium" (fecha e chama [onOpenPremium]).
 *
 * Mostre SÓ depois de uma ação concluída (salvar imagem, fim do terço…), nunca durante oração, leitura,
 * contagem ou digitação; a frequência (≥ 4 min, nunca na 1ª sessão) é do app, contada em [onShown].
 *
 * App sem anúncio (`FamilyAppIdentity(showsAds = false)`, 2.359.0): nada abre — com [show] `true` o
 * [onDismiss] é chamado na hora, para o fluxo do app seguir como se o anúncio tivesse sido pulado.
 */
@Composable
fun FamilyInterstitialAd(
    show: Boolean,
    onDismiss: () -> Unit,
    onOpenPremium: () -> Unit,
    onShown: ((CustomAd) -> Unit)? = null,
    closeAfterSeconds: Int = 3,
) {
    if (!FamilyTheme.identity.showsAds) {
        val dismiss by rememberUpdatedState(onDismiss)
        LaunchedEffect(show) { if (show) dismiss() }
        return
    }
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

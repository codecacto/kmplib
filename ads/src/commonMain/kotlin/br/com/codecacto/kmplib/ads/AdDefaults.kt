package br.com.codecacto.kmplib.ads

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ads.custom.CustomAd

/** Medidas padrão dos house ads, num lugar só. */
object AdDefaults {
    /**
     * Altura do banner de rodapé quando o app **fixa** uma (`height`/`customHeight`).
     *
     * ⚠️ **O default NÃO é este** desde a 2.170.0 — é a proporção da arte
     * ([BannerSize.aspectRatio]). Altura fixa e arte de proporção fixa brigam: numa tela de 360 dp,
     * a faixa 6:1 mede 60 dp de altura naturalmente, e forçá-la a 90 dp faz o `ContentScale.Crop`
     * **cortar um terço da largura** — some justamente o que fica nas bordas do criativo. Este
     * valor continua aqui para quem precisa de uma altura fixa por decisão de layout.
     */
    val BANNER_HEIGHT: Dp = 90.dp

    /** Idem, para o banner grande. */
    val BANNER_LARGE_HEIGHT: Dp = 180.dp
}

/**
 * Tamanho do banner de rodapé. A escolha é **do app**: uma tela de leitura contínua pede o padrão;
 * um app cuja única receita é o house ad ganha visibilidade com o grande.
 *
 * Cada tamanho pede um formato diferente ao backend — e um anúncio sem a arte daquele formato
 * simplesmente não entra no sorteio.
 *
 * **A altura vem da PROPORÇÃO da arte, não de um número em dp.** É o que garante a peça inteira em
 * qualquer largura de tela: num aparelho de 360 dp o padrão ocupa 60 dp e o grande 120 dp; num de
 * 480 dp, 80 e 160. O "dobro" que separa os dois é a proporção (6:1 → 3:1), e ele se mantém em
 * telas que não previmos.
 */
enum class BannerSize(
    internal val format: String,
    internal val aspectRatio: Float,
    internal val height: Dp,
) {
    /** Faixa 6:1 (arte 1440×240). */
    STANDARD(CustomAd.FORMAT_BANNER, 6f, AdDefaults.BANNER_HEIGHT),

    /** Faixa 3:1 (arte 1440×480) — o dobro da altura relativa. */
    LARGE(CustomAd.FORMAT_BANNER_LARGE, 3f, AdDefaults.BANNER_LARGE_HEIGHT);

    companion object {
        /**
         * Proporção da arte que de fato veio — o que a caixa deve seguir.
         *
         * O app pede um tamanho, mas pode receber outro (o pedido cai para o banner comum quando
         * não há arte grande). Desenhar na proporção do PEDIDO cortaria a arte recebida; desenhar
         * na proporção DELA mantém a peça inteira nos dois casos.
         *
         * Formato desconhecido ou em branco (o backend pode omitir) cai no [fallback], que é o
         * tamanho que o app pediu.
         */
        fun aspectRatioOf(format: String, fallback: BannerSize): Float =
            entries.firstOrNull { it.format == format }?.aspectRatio ?: fallback.aspectRatio
    }
}

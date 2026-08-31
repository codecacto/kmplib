package br.com.codecacto.kmplib.ads

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ads.custom.CustomAd

/** Medidas padrão dos house ads, num lugar só. */
object AdDefaults {
    /**
     * Altura do banner de rodapé.
     *
     * Eram **60 dp** até a 2.168.0 — o tamanho do banner clássico de rede de anúncio (50-60 dp), que
     * numa arte própria fica apertado: a imagem chega inteira mas em miniatura, e o que deveria ser
     * a chamada de um app da casa vira uma tarja. Como o criativo é NOSSO (house ad, sem formato
     * imposto por rede), a altura é escolha de produto — e o fundador pediu mais.
     *
     * **90 dp** é o meio-termo: 50% mais alto, ainda menor que o "large banner" de 100 dp das redes,
     * e sem comer a tela num aparelho pequeno (num Android de 640 dp de altura, ocupa ~14%).
     * Quem precisar de outra medida passa `customHeight` — o default é só o ponto de partida.
     */
    val BANNER_HEIGHT: Dp = 90.dp

    /**
     * Altura do banner **grande** ([BannerSize.LARGE]) — o dobro do padrão.
     *
     * A arte dele é uma faixa 3:1 (1440×480), contra a 6:1 do banner comum. **Dobrar a altura sem
     * trocar a arte não funciona**: a mesma imagem 6:1 num espaço 3:1 sai deformada ou com metade
     * cortada. Por isso o grande é um formato próprio, com arte própria, e não um parâmetro de
     * layout.
     */
    val BANNER_LARGE_HEIGHT: Dp = 180.dp
}

/**
 * Tamanho do banner de rodapé. A escolha é **do app**: uma tela de leitura contínua pede o padrão;
 * um app cuja única receita é o house ad ganha visibilidade com o grande.
 *
 * Cada tamanho pede um formato diferente ao backend — e um anúncio sem a arte daquele formato
 * simplesmente não entra no sorteio.
 */
enum class BannerSize(internal val format: String, internal val height: Dp) {
    /** Faixa 6:1 (arte 1440×240), [AdDefaults.BANNER_HEIGHT]. */
    STANDARD(CustomAd.FORMAT_BANNER, AdDefaults.BANNER_HEIGHT),

    /** Faixa 3:1 (arte 1440×480), [AdDefaults.BANNER_LARGE_HEIGHT]. */
    LARGE(CustomAd.FORMAT_BANNER_LARGE, AdDefaults.BANNER_LARGE_HEIGHT),
}

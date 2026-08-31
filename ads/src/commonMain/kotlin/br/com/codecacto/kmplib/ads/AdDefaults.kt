package br.com.codecacto.kmplib.ads

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
}

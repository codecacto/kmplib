package br.com.codecacto.kmplib.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A conta que mantém o conteúdo das telas da lib ACIMA do rodapé fixo (o banner de house ad).
 *
 * O slot em si é composição — e teste de UI automatizado está desligado no KMP por decisão da
 * fábrica —, então o que se prova aqui é a regra que decide a folga: ela vem do `innerPadding` do
 * `Scaffold` (que já contém a altura do `bottomBar`) e **soma** com o padding próprio da tela.
 */
class ScreenBottomBarTest {

    @Test
    fun `sem rodape o comportamento e o de sempre`() {
        // Sem `bottomBar`, o Scaffold devolve 0 no rodapé (ou só o inset do sistema): a tela fica
        // com exatamente a folga que ela mesma pede. É o caso de TODO consumidor atual.
        assertEquals(0.dp, espacoAcimaDoRodape(PaddingValues(0.dp)))
        assertEquals(16.dp, espacoAcimaDoRodape(PaddingValues(0.dp), folgaPropria = 16.dp))
    }

    @Test
    fun `com rodape o conteudo termina colado no topo dele`() {
        // 90dp = a altura típica do banner. Sem folga própria, o último item encosta no topo do
        // banner — nunca passa por baixo, que é o defeito que a constituição proíbe.
        val comBanner = PaddingValues(bottom = 90.dp)

        assertEquals(90.dp, espacoAcimaDoRodape(comBanner))
    }

    @Test
    fun `a folga da tela SOMA com a altura do rodape, nao substitui`() {
        // Trocar uma pela outra colaria o botão de enviar no topo do banner: o padding de leitura
        // da tela continua valendo ACIMA da barra.
        val comBanner = PaddingValues(bottom = 90.dp)

        assertEquals(106.dp, espacoAcimaDoRodape(comBanner, folgaPropria = 16.dp))
    }

    @Test
    fun `rodape desligado volta a valer so o inset do sistema`() {
        // `ManagedBannerAd` com o roteamento em OFF vira um Spacer de altura zero: o Scaffold
        // devolve só o inset da barra de navegação, e a tela fica idêntica à de antes do slot.
        val soInsetDoSistema = PaddingValues(bottom = 24.dp)

        assertEquals(40.dp, espacoAcimaDoRodape(soInsetDoSistema, folgaPropria = 16.dp))
    }
}

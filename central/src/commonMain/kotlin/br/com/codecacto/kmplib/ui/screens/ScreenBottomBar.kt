package br.com.codecacto.kmplib.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Folga inferior do CONTEÚDO nas telas da lib que aceitam rodapé fixo (`bottomBar`) —
 * [br.com.codecacto.kmplib.ui.screens.feedback.FeedbackScreen],
 * [br.com.codecacto.kmplib.ui.screens.developer.DeveloperScreen] e
 * [br.com.codecacto.kmplib.ui.screens.developer.ContactScreen].
 *
 * ## Por que existe
 *
 * O `Scaffold` desenha o `bottomBar` POR CIMA da área de conteúdo: quem chama `fillMaxSize()` sem
 * consumir o `innerPadding` termina com o último item metade escondido atrás do rodapé — e, num app
 * cujo rodapé é o banner de house ad, isso é exatamente o defeito que a constituição proíbe
 * ("lista com banner: o último item aparece INTEIRO"). A altura do `bottomBar` **já vem** dentro do
 * `innerPadding`; a regra é só não jogá-lo fora.
 *
 * Sem `bottomBar`, o `innerPadding` inferior é o inset da barra de navegação do sistema — o mesmo
 * valor de sempre. Por isso a conta é a mesma nos dois casos, e o comportamento de quem NÃO passa
 * rodapé não muda em nada.
 *
 * @param innerPadding o `PaddingValues` que o `Scaffold` entrega ao conteúdo
 * @param folgaPropria folga que a tela quer ALÉM do rodapé (o padding de leitura dela)
 * @return a folga inferior total — a soma, nunca o maior dos dois: substituir o padding próprio pela
 *   altura da barra colaria o botão de enviar no topo do banner.
 */
internal fun espacoAcimaDoRodape(
    innerPadding: PaddingValues,
    folgaPropria: Dp = 0.dp,
): Dp = innerPadding.calculateBottomPadding() + folgaPropria

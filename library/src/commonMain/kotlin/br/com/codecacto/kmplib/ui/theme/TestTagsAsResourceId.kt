package br.com.codecacto.kmplib.ui.theme

import androidx.compose.runtime.Composable

/**
 * Expõe as `Modifier.testTag()` da árvore como **`resource-id` da plataforma**, para que automação
 * de UI de caixa-preta (Maestro, Appium, UiAutomator) consiga selecionar por id.
 *
 * ## Por que isto existe na lib, e não em cada app
 *
 * `Modifier.testTag("paywall-btn-assinar")` **não vira `resource-id` sozinho**. No Android, a tag só
 * aparece na árvore de acessibilidade — que é o que o `uiautomator` lê — quando algum nó ancestral
 * declara `testTagsAsResourceId = true` na sua `semantics`. Sem essa linha, a tag existe para o
 * `ComposeTestRule` (teste instrumentado, que fala com o Compose direto) e é **invisível** para
 * qualquer ferramenta externa: `maestro studio` mostra o nó sem id, e o flow não tem como se ancorar
 * a nada além de texto de tela — que muda no dia em que alguém melhora o copy.
 *
 * A declaração precisa acontecer **uma vez, na raiz da hierarquia**. O lugar por onde todo app da
 * fábrica passa é o [AppTheme], então é aqui que uma linha chega em todos eles — em vez de 28 cópias
 * da mesma correção, cada uma esquecida num app diferente.
 *
 * ## Ligada SEMPRE, não só em debug
 *
 * Condicionar a `BuildInfo.isDebug` faria o seletor por id funcionar no emulador e **falhar no build
 * da faixa alpha** — que é justamente o build que a suíte de pagamento é obrigada a usar, porque o
 * Play Billing não inicializa em app instalado de lado. Um recurso de teste que só existe onde o
 * teste não pode rodar não é recurso, é armadilha.
 *
 * O que se expõe são **nomes de elemento de UI** (`paywall-plano-mensal`), não segredo: a árvore de
 * acessibilidade do Compose já é legível por qualquer serviço de acessibilidade instalado, com ou
 * sem esta flag. Ela muda o *rótulo* dos nós, não o que é acessível.
 *
 * ## iOS não precisa
 *
 * No iOS a `testTag` já é publicada como `accessibilityIdentifier` pelo próprio Compose
 * Multiplatform, que é exatamente o que o Maestro/XCUITest leem. O `actual` de lá é um no-op de
 * propósito — embrulhar em `Box` só para não ficar vazio custaria um nó de layout por app sem
 * comprar nada.
 */
@Composable
internal expect fun WithTestTagsAsResourceId(content: @Composable () -> Unit)

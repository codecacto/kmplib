package br.com.codecacto.kmplib.ui.theme

import androidx.compose.runtime.Composable

/**
 * No-op de propósito — ver [WithTestTagsAsResourceId] (commonMain).
 *
 * No iOS o Compose Multiplatform já publica a `testTag` como `accessibilityIdentifier` do
 * `UIAccessibility`, que é o que o Maestro e o XCUITest leem. Não há flag para ligar, e embrulhar o
 * conteúdo num `Box` só para o `actual` não ficar vazio adicionaria um nó de layout na raiz de todo
 * app iOS da fábrica sem comprar nada.
 */
@Composable
internal actual fun WithTestTagsAsResourceId(content: @Composable () -> Unit) {
    content()
}

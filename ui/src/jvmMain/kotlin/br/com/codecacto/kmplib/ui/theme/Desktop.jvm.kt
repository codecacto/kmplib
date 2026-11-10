package br.com.codecacto.kmplib.ui.theme

import androidx.compose.runtime.Composable

/** Desktop: não há `resource-id`; o `testTag` já é o que o teste do Compose Desktop enxerga. */
@Composable
actual fun WithTestTagsAsResourceId(content: @Composable () -> Unit) {
    content()
}

/** Desktop: a janela não tem barra de status/navegação do sistema para colorir. */
@Composable
internal actual fun ApplySystemBarsAppearance(background: SystemBarsBackground) = Unit

package br.com.codecacto.kmplib.platform.automation

import androidx.compose.ui.Modifier

/**
 * No-op de propósito — ver [exposeTestTagsAsResourceId] (commonMain). No iOS o Compose
 * Multiplatform publica a `testTag` como `accessibilityIdentifier` em qualquer janela, sem flag.
 */
actual fun Modifier.exposeTestTagsAsResourceId(): Modifier = this

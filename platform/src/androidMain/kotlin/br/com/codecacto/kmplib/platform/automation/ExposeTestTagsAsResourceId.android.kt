package br.com.codecacto.kmplib.platform.automation

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Ver [exposeTestTagsAsResourceId] (commonMain). O `uiautomator` sobe pelos ancestrais de cada nó
 * com `testTag` até achar a flag — por isso basta ligá-la no nó-raiz da janela.
 *
 * `testTagsAsResourceId` é API **só de Android** (`androidx.compose.ui.semantics`): daí o
 * `expect/actual`.
 */
@OptIn(ExperimentalComposeUiApi::class)
actual fun Modifier.exposeTestTagsAsResourceId(): Modifier =
    this.semantics { testTagsAsResourceId = true }

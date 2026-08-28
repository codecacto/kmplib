package br.com.codecacto.kmplib.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId

/**
 * Ver [WithTestTagsAsResourceId] (commonMain) para o porquê.
 *
 * O `Box` **sem modificador de tamanho** é o padrão do próprio Compose para esta flag: ele mede o
 * conteúdo (`wrapContent`) e repassa as restrições que recebeu, então um `Scaffold`/`fillMaxSize`
 * dentro dele continua ocupando a tela inteira, inclusive com `WindowInsets`. É um nó de layout a
 * mais na raiz — não uma mudança de layout.
 *
 * `testTagsAsResourceId` é **API só de Android** (`androidx.compose.ui.semantics`), e é por isso que
 * a declaração é `expect/actual` em vez de morar direto no [AppTheme], que é `commonMain` e compila
 * para iOS também.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun WithTestTagsAsResourceId(content: @Composable () -> Unit) {
    Box(Modifier.semantics { testTagsAsResourceId = true }) {
        content()
    }
}

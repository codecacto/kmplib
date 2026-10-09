package br.com.codecacto.kmplib.platform.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag

/**
 * **Conteúdo que não pode sair do aparelho por captura de tela** — senha temporária mostrada a quem
 * cadastrou, código de acesso, dado de saúde, documento.
 *
 * ```kotlin
 * SecureContent {
 *     SenhaTemporariaCard(senha)
 * }
 * ```
 *
 * O que cada plataforma faz, pela API oficial — e o que **não** faz:
 * - **Android:** `FLAG_SECURE` na janela enquanto o bloco estiver composto (por [HideFromRecents],
 *   com a mesma contagem de pedidos aninhados). O sistema **bloqueia print, gravação e
 *   espelhamento** e tira a tela da miniatura de recentes. Vale para a JANELA inteira enquanto o
 *   bloco existir — não só para o pedaço embrulhado: é assim que o flag funciona.
 * - **iOS:** não existe API pública para **bloquear o print** (o truque do `UITextField` seguro é
 *   detalhe interno do UIKit — fora do padrão-ouro). O que a Apple oferece, e esta função usa:
 *   (1) `UIScreen.captured` + `UIScreenCapturedDidChangeNotification` — enquanto a tela está sendo
 *   **gravada, espelhada ou transmitida por AirPlay**, o bloco fica coberto por [cover]; (2) o
 *   desfoque do seletor de apps do [HideFromRecents]. **O print (botões laterais) continua
 *   possível no iOS** — se isso for inaceitável para o dado, ele não deve ser exibido no iOS.
 *
 * O conteúdo **continua composto** debaixo da cobertura (texto digitado e rolagem não se perdem
 * quando a gravação começa e para), e a cobertura engole o toque.
 *
 * @param enabled `false` desliga tudo (útil para ligar só quando o dado está visível).
 * @param cover o que aparece no lugar do bloco durante a gravação no iOS. Default: preto opaco —
 *   o mesmo que o Android grava no lugar de uma janela com `FLAG_SECURE`.
 */
@Composable
fun SecureContent(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    cover: @Composable BoxScope.() -> Unit = { DefaultSecureCover() },
    content: @Composable BoxScope.() -> Unit,
) {
    HideFromRecents(enabled)
    val captured by rememberScreenCaptured()
    Box(modifier) {
        content()
        if (enabled && captured) {
            Box(
                Modifier
                    .matchParentSize()
                    .testTag(SECURE_CONTENT_COVER_TAG)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                            }
                        }
                    },
            ) { cover() }
        }
    }
}

@Composable
private fun BoxScope.DefaultSecureCover() {
    Box(Modifier.matchParentSize().background(Color.Black))
}

/** `testTag` da cobertura do [SecureContent]. */
const val SECURE_CONTENT_COVER_TAG: String = "conteudo-seguro-cobertura"

/**
 * `true` enquanto a tela está sendo gravada/espelhada (iOS, `UIScreen.captured`). No Android é
 * sempre `false`: lá o `FLAG_SECURE` já faz o sistema gravar preto, sem precisar cobrir.
 */
@Composable
internal expect fun rememberScreenCaptured(): State<Boolean>

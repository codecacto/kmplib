package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Fecha o teclado quando a pessoa toca **fora** de um campo — em qualquer ponto da área coberta.
 *
 * Aplicado **uma vez, na raiz** do app (o `Box` que envolve a navegação), vale para todas as telas:
 * é o comportamento que o iOS tem em todo app nativo e que o Compose não traz de fábrica. No
 * Android o teclado ainda tem a seta de "voltar" para ser fechado; no iPhone **não tem** — sem isto,
 * o teclado que subiu para um comentário só desce quando a pessoa sai da tela.
 *
 * ## O que conta como "tocar fora"
 *
 * Um toque (sem arrastar além do *touch slop*, com um dedo só) que **nenhum filho consumiu**. É a
 * régua que separa, sem lista de exceções:
 * - **o próprio campo** — o `BasicTextField` consome o toque (Android: `detectTapAndPress`;
 *   iOS: `detectRepeatingTapGestures` do `cupertinoTextFieldPointer`), então tocar nele, ou passar
 *   de um campo para outro, **não** fecha nada;
 * - **botões e itens clicáveis** — `clickable` também consome; "Enviar" não derruba o teclado de
 *   quem vai continuar escrevendo;
 * - **rolagem** — passa do slop, não é toque; rolar a lista para reler o post mantém o teclado.
 *
 * Fundo, texto, foto, espaço entre os cartões: nada disso consome, e o toque fecha o teclado.
 *
 * ## Por que NÃO é `clickable { clearFocus() }` (o que o `FormContainer` fazia até a 2.194.0)
 *
 * - `clickable` **consome** o toque e publica um nó de semântica "clicável" do tamanho da tela: o
 *   TalkBack/VoiceOver passa a anunciar o formulário inteiro como um botão.
 * - Só enxerga o toque que chega **até ele** — cada tela precisava lembrar de pôr o seu (foi assim
 *   no Super 8, tela a tela), e a que esquecia ficava sem.
 *
 * Aqui o toque é **observado** no passe `Final`, depois de todos os filhos, e **nunca consumido**:
 * nada abaixo muda de comportamento.
 *
 * ## Onde a raiz não alcança
 *
 * `Dialog`, `ModalBottomSheet` e `Popup` são outra janela, com árvore de toque própria: o modifier
 * da raiz não os vê. `AppDialog` (e por ele `AppInputDialog`) e `AppBottomSheet` já trazem este
 * modifier; um `ModalBottomSheet`/`Dialog` montado à mão no app precisa recebê-lo no próprio
 * conteúdo.
 */
fun Modifier.dismissKeyboardOnTapOutside(): Modifier = this then DismissKeyboardOnTapOutsideElement

private data object DismissKeyboardOnTapOutsideElement :
    ModifierNodeElement<DismissKeyboardOnTapOutsideNode>() {
    override fun create() = DismissKeyboardOnTapOutsideNode()
    override fun update(node: DismissKeyboardOnTapOutsideNode) = Unit
    override fun InspectorInfo.inspectableProperties() {
        name = "dismissKeyboardOnTapOutside"
    }
}

private class DismissKeyboardOnTapOutsideNode : DelegatingNode(), CompositionLocalConsumerModifierNode {

    init {
        delegate(SuspendingPointerInputModifierNode { awaitUnconsumedTaps(::dismissKeyboard) })
    }

    private fun dismissKeyboard() {
        // `clearFocus` encerra a sessão de digitação — é o que baixa o teclado nas duas
        // plataformas e tira o cursor do campo. O `hide()` vem junto porque, sem foco em campo
        // nenhum, ele é no-op; com foco, garante o teclado fora mesmo se algo retomar o foco.
        currentValueOf(LocalFocusManager).clearFocus()
        currentValueOf(LocalSoftwareKeyboardController)?.hide()
    }
}

/** Observa (sem consumir) cada gesto e chama [onTap] quando ele termina como toque livre. */
private suspend fun PointerInputScope.awaitUnconsumedTaps(onTap: () -> Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
        var tracker = KeyboardDismissTap.start(downConsumed = down.isConsumed)
        while (tracker == KeyboardDismissTap.Verdict.Pending) {
            val event = awaitPointerEvent(PointerEventPass.Final)
            val change = event.changes.firstOrNull { it.id == down.id }
            tracker = KeyboardDismissTap.next(
                pointerCount = event.changes.size,
                trackedPointerPresent = change != null,
                anyConsumed = event.changes.any { it.isConsumed },
                distanceFromDown = change?.let { (it.position - down.position).getDistance() } ?: 0f,
                touchSlop = viewConfiguration.touchSlop,
                released = change?.changedToUpIgnoreConsumed() == true,
            )
        }
        if (tracker == KeyboardDismissTap.Verdict.Tap) onTap()
    }
}

/**
 * A decisão "isto foi um toque livre?" como regra pura — fora do `pointerInput` para ser testável
 * sem árvore de composição (mesmo motivo de `FormDefaults`).
 */
internal object KeyboardDismissTap {

    enum class Verdict { Pending, Tap, NotATap }

    /** O dedo desceu. Se um filho já consumiu o `down` (o campo de texto faz isso), não é conosco. */
    fun start(downConsumed: Boolean): Verdict = if (downConsumed) Verdict.NotATap else Verdict.Pending

    /** Um evento depois do `down`, já no passe `Final` (depois de todos os filhos). */
    fun next(
        pointerCount: Int,
        trackedPointerPresent: Boolean,
        anyConsumed: Boolean,
        distanceFromDown: Float,
        touchSlop: Float,
        released: Boolean,
    ): Verdict = when {
        // Segundo dedo = pinça/zoom, nunca um toque para fechar o teclado.
        pointerCount > 1 || !trackedPointerPresent -> Verdict.NotATap
        // Alguém tratou o gesto (clique, rolagem, arrasto de folha): o toque era dele.
        anyConsumed -> Verdict.NotATap
        // Arrastou: é rolagem, e rolar para reler não pode derrubar o teclado.
        distanceFromDown > touchSlop -> Verdict.NotATap
        released -> Verdict.Tap
        else -> Verdict.Pending
    }
}

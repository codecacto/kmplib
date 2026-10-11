package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextRange

/**
 * `Saver` de [TextFieldState] que guarda **só o texto e a seleção** — sem o histórico de desfazer
 * (2.290.0, GAP-TEXTSTATE-SAVER-01).
 *
 * ## Por que existe
 *
 * O `rememberTextFieldState` usa o `TextFieldState.Saver` do Compose, que grava também o
 * `TextUndoManager` (a pilha de desfazer). No **Kotlin/Native (iOS)**, com Compose Multiplatform até a
 * linha 1.10.x, restaurar esse estado com o desfazer NÃO vazio lança
 * `ClassCastException: kotlin.collections.asList$1 cannot be cast to kotlin.Int` em
 * `TextUndoManager.Companion.Saver.restore` — o app **fecha** ao voltar para a tela depois de alguém
 * digitar e apagar no campo (Vitalis, 10/out/2026). Android não reproduz. É defeito do próprio
 * Compose, registrado como **CMP-9960** (corrigido na linha 1.11; CMP-10373 é o duplicado com o
 * reprodutor de 1.10.3) e AOSP CL 4000634.
 *
 * O caminho oficial para estado salvável com formato próprio é um `Saver` passado ao
 * `rememberSaveable` — é o que este objeto é. Ele também é estritamente melhor para a fábrica mesmo
 * depois do conserto do Compose: o histórico de desfazer não tem utilidade depois de sair da tela
 * (nenhum campo da lib oferece "desfazer") e só aumenta o `Bundle`.
 *
 * Formato salvo: `listOf(texto: String, inícioDaSeleção: Int, fimDaSeleção: Int)` — três tipos que o
 * `Bundle` do Android e o registro de estado do iOS aceitam. A restauração é defensiva: formato
 * inesperado (inclusive o do `TextFieldState.Saver` de uma versão anterior, salvo antes de o app
 * subir de versão) devolve `null`, e o `rememberSaveable` cai no valor inicial em vez de derrubar o app.
 * Seleção fora do texto é recortada para dentro dele.
 */
object TextOnlyTextFieldStateSaver : Saver<TextFieldState, Any> {

    override fun SaverScope.save(value: TextFieldState): Any {
        val texto = value.text.toString()
        val selecao = value.selection
        return listOf(texto, selecao.start, selecao.end)
    }

    override fun restore(value: Any): TextFieldState? {
        val partes = value as? List<*> ?: return null
        if (partes.size < 3) return null
        val texto = partes[0] as? String ?: return null
        val inicio = partes[1] as? Int ?: return null
        val fim = partes[2] as? Int ?: return null
        return TextFieldState(texto, coerceSelection(texto, inicio, fim))
    }

    /** Seleção salva recortada para caber no texto (o texto pode ter mudado de tamanho por fora). */
    internal fun coerceSelection(texto: String, inicio: Int, fim: Int): TextRange =
        TextRange(inicio.coerceIn(0, texto.length), fim.coerceIn(0, texto.length))
}

/**
 * O `rememberTextFieldState` **sem** o histórico de desfazer no estado salvo (2.290.0) — use no lugar
 * dele em todo campo de texto do app.
 *
 * Sobrevive a rotação, à volta para a tela pela navegação e à morte do processo como o original, com
 * o texto e a seleção; o que não volta é o "desfazer" do que foi digitado antes de sair — e é
 * exatamente essa parte que derruba o app no iOS (ver [TextOnlyTextFieldStateSaver]).
 *
 * Dado sensível (saúde, documento) não vai a estado salvo nenhum: aí é
 * `remember { TextFieldState(texto) }`, ou `rememberSyncedTextFieldState(…, saveable = false)`.
 *
 * ```kotlin
 * val nome = rememberTextOnlyTextFieldState()            // em vez de rememberTextFieldState()
 * BasicTextField(state = nome)
 * ```
 */
@Composable
fun rememberTextOnlyTextFieldState(
    initialText: String = "",
    initialSelection: TextRange = TextRange(initialText.length),
): TextFieldState = rememberSaveable(saver = TextOnlyTextFieldStateSaver) {
    TextFieldState(initialText, initialSelection)
}

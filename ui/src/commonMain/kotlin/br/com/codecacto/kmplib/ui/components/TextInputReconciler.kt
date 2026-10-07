package br.com.codecacto.kmplib.ui.components

/**
 * Decide quando o texto de um campo deve ser **reescrito de fora** — e quando o valor que chega de
 * fora é só o ECO atrasado do que a própria pessoa digitou (2.258.0).
 *
 * **O defeito que isto mata.** Campo com `value = state.query` vindo do `StateFlow` do ViewModel e
 * `onValueChange = { onAction(QueryChanged(it)) }`: cada letra faz a ida e volta pelo ViewModel, e o
 * valor só volta ao campo um quadro depois. Digitando rápido (iOS, Maestro em rajada), o campo é
 * recomposto com o valor VELHO entre duas teclas, o `BasicTextField` aceita o velho como verdade e a
 * letra seguinte é aplicada sobre ele — "teste qa" vira "tete a" (MinhaOS, 06/out/2026, docs/42).
 *
 * **A regra oficial** (Google, "Effective state management for TextField"): o texto mora na UI e é
 * atualizado no mesmo quadro; o ViewModel só RECEBE. Esta classe é a peça que falta para o campo
 * continuar aceitando um valor de fora (limpar, restaurar, preencher pela seleção de um item) sem
 * voltar a depender da ida e volta:
 *
 * - cada edição local entra numa fila de "emitidos e ainda não ecoados" ([onLocalText]);
 * - quando o valor externo muda ([onExternal]) e é um desses emitidos, é eco: a fila anda até ele e o
 *   campo **não** é tocado (o eco de "t" chegando quando o campo já tem "tes" não apaga o "es");
 * - quando muda para um valor que ninguém digitou, foi a TELA que mudou o texto (limpar, restaurar,
 *   transformar): esse valor vence e a fila zera.
 *
 * Valor externo que NÃO muda não reescreve nada — por isso este caminho é para campo que aceita
 * qualquer texto (busca, filtro). Campo que RECUSA a tecla (`if (ok) set(it)`) precisa do caminho
 * controlado de sempre do `AppTextField`.
 *
 * Classe pura, sem Compose: o comportamento é testado em `TextInputReconcilerTest` simulando o
 * ViewModel atrasado.
 */
internal class TextInputReconciler(initialExternal: String) {

    /** Último valor externo visto — a mudança dele é o gatilho, não o valor em si. */
    var lastExternal: String = initialExternal
        private set

    private val pending = ArrayDeque<String>()

    /** Quantos valores emitidos ainda esperam o eco (exposto para teste). */
    val pendingCount: Int get() = pending.size

    /**
     * O texto local mudou (tecla, colar, limpar pelo "x"). Devolve `true` se o valor deve ser
     * emitido para fora (`onValueChange`/`onQueryChange`).
     *
     * Não emite quando nada está em voo e o texto já é o externo — é o caso do valor inicial e do
     * próprio texto que [onExternal] acabou de escrever no campo, que voltariam ao ViewModel como um
     * "mudou" que não mudou.
     */
    fun onLocalText(text: String): Boolean {
        if (pending.isEmpty() && text == lastExternal) return false
        if (pending.lastOrNull() == text) return false
        pending.addLast(text)
        while (pending.size > MAX_PENDING) pending.removeFirst()
        return true
    }

    /**
     * O valor externo atual (a cada composição). Devolve o texto que o campo deve ASSUMIR, ou `null`
     * para deixar o campo como está.
     */
    fun onExternal(value: String, localText: String): String? {
        if (value == lastExternal) return null
        lastExternal = value
        val echo = pending.indexOf(value)
        if (echo >= 0) {
            // Eco (talvez de uma tecla já superada): a fila anda até ele, o campo fica.
            repeat(echo + 1) { pending.removeFirst() }
            return null
        }
        pending.clear()
        // A tela mudou o texto por outro motivo (limpar, restaurar, selecionar um item).
        return if (value == localText) null else value
    }

    private companion object {
        /** Teto da fila: quem nunca ecoa (valor fixo de fora) não a faz crescer sem limite. */
        const val MAX_PENDING = 64
    }
}

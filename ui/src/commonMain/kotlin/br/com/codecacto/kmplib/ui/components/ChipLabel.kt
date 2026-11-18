package br.com.codecacto.kmplib.ui.components

/**
 * Régua do texto dentro de um chip de fileira rolável (≥ 2.373.0) — usada pelo [FilterChipRow] e pelos chips de
 * profissional/grupo do `AppTimeGridScheduler` no celular.
 *
 * Numa fileira que ROLA não falta largura: o chip mede pelo conteúdo e mostra o nome inteiro ("Bianca Toledo", nunca
 * "Bianca Toled…"). O corte só existe para o caso fora da curva — um rótulo tão longo que um chip sozinho ocuparia a
 * tela — e aí acontece no fim de uma PALAVRA, com reticências, a partir de [MAX_CHARS]. O texto completo continua na
 * descrição de acessibilidade do chip.
 */
object ChipLabel {
    /** Acima disto (≈ "Maria Aparecida dos Santos Silva") o rótulo é encurtado no fim de uma palavra. */
    const val MAX_CHARS: Int = 32

    private val SPACES = Regex("\\s+")

    /**
     * O texto que o chip desenha: sem espaço sobrando nas pontas nem repetido no meio; inteiro até [maxChars]
     * caracteres; acima disso, as palavras que cabem + "…" (palavra única longa demais é cortada nela mesma).
     */
    fun display(label: String, maxChars: Int = MAX_CHARS): String {
        require(maxChars >= 2) { "maxChars precisa ser ≥ 2" }
        val clean = label.trim().replace(SPACES, " ")
        if (clean.length <= maxChars) return clean
        val room = maxChars - 1 // espaço da reticência
        val cut = clean.lastIndexOf(' ', startIndex = room)
        val head = if (cut > 0) clean.substring(0, cut) else clean.substring(0, room)
        return head.trimEnd() + "…"
    }
}

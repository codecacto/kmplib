package br.com.codecacto.kmplib.tournament

/**
 * **O que o resto do motor pergunta a um confronto — e é tudo.**
 *
 * Avanço de fase, classificação e pódio consomem só isto (mais o [Score]). Nenhum deles sabe se o
 * confronto foi decidido em cobranças, em gols ou em sets: o "como" (sequência de pênaltis, placar
 * de raquete) é do app ou do [MatchFormat], e um esporte novo entra sem tocar em chave nem tabela.
 *
 * O "por quê" de um vencedor (morte súbita, decisão antecipada…) é conversa da tela do app, não do
 * motor — por isso [Decided] não carrega motivo.
 */
sealed interface MatchOutcome {

    /** Ainda há o que registrar. Não pontua. */
    data object InProgress : MatchOutcome {
        override val winner: Side? get() = null
    }

    /** Jogado e decidido. */
    data class Decided(override val winner: Side) : MatchOutcome

    /** Empate — só onde a fase admite (grupos/pontos corridos do futebol). Raquete não empata. */
    data object Draw : MatchOutcome {
        override val winner: Side? get() = null
    }

    /**
     * Vitória por **W.O.**: o adversário não compareceu àquele jogo. Pontua como vitória, e o placar
     * que acompanha o resultado é o **convencionado** pelo formato
     * ([MatchFormat.walkoverScore] — `6-0` no beach tennis).
     *
     * Não confundir com **desistência do torneio** (a dupla sai do grupo): essa é do
     * participante, e a regra dela é a [WithdrawalPolicy] da classificação.
     */
    data class Walkover(override val winner: Side) : MatchOutcome

    val isFinished: Boolean get() = this !is InProgress

    /** Quem venceu ([Decided] ou [Walkover]); `null` em andamento ou empate. */
    val winner: Side?
}

/** Os 3 formatos de disputa. */
enum class TournamentFormat {
    /** Chave em potência de 2, BYE para quem sobra, quem perde está fora. */
    KNOCKOUT,

    /** Todos contra todos (método do círculo); campeão é quem lidera a tabela no fim. */
    ROUND_ROBIN,

    /** Todos contra todos dentro de cada grupo; os N primeiros de cada um vão ao mata-mata. */
    GROUPS_THEN_KNOCKOUT,
}

/**
 * Que papel a fase cumpre. **O rótulo exibido não mora aqui nem em lugar nenhum da lib** — ver
 * [PhaseIdentity].
 */
enum class PhaseType {
    /** Round-robin dentro de um grupo (uma fase por grupo). */
    GROUP,

    /** Uma rodada do formato todos contra todos. */
    ROUND,

    /** Uma fase eliminatória (oitavas, quartas, semi, final…). */
    KNOCKOUT,

    /**
     * A disputa de 3º lugar, entre os perdedores das semifinais. Fase própria, de um confronto,
     * numerada **depois** da final (a ordem em que se JOGA é decisão do organizador, não do motor).
     */
    THIRD_PLACE,
}

/**
 * **O dado de que a palavra "Semifinais" é feita.**
 *
 * Nenhum texto sai da lib: num app em 4 idiomas, um rótulo gravado ("Grupo A", "Semifinais")
 * congela o idioma do dia da criação para sempre. O banco guarda o dado; quem monta a palavra é a
 * UI. A eliminatória se nomeia pelo [matchCount] (1 = final, 2 = semi, 4 = quartas…), o grupo pelo
 * [groupIndex] (0-based: 0 = "A") e a rodada pelo [roundNumber] (1-based).
 */
data class PhaseIdentity(
    val type: PhaseType,
    val matchCount: Int,
    val groupIndex: Int? = null,
    val roundNumber: Int? = null,
)

/**
 * Um participante já sorteado — jogador, dupla ou equipe: para o motor é um id.
 *
 * [drawOrder] é a posição que o sorteio deu na geração. **Persista-a**: ela é o último critério de
 * desempate da classificação. O sorteio aconteceu uma vez, na frente do organizador; re-sortear a
 * cada abertura da tabela faria o 3º e o 4º trocarem sozinhos entre um olhar e outro.
 */
data class TournamentParticipant(
    val id: Long,
    val drawOrder: Int,
    val groupIndex: Int? = null,
)

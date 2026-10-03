package br.com.codecacto.kmplib.tournament

/**
 * Chaveamento eliminatório. Invariantes: chave em potência de 2, cada participante uma única vez
 * na estreia e **nenhum confronto com dois BYEs**.
 *
 * A ordem de entrada é **semeada** ([seedOrder]), não sequencial: a lista que chega aqui é lida
 * como "cabeça 1, cabeça 2, cabeça 3…" e cada uma é posicionada onde o regulamento manda.
 */
object Knockout {

    /** Potência de 2 imediatamente acima (ou igual) a [participants], com piso em 2. */
    fun bracketSize(participants: Int): Int {
        var size = 2
        while (size < participants) size *= 2
        return size
    }

    /**
     * A ordem de semeadura de uma chave de [size] vagas — as **cabeças** (1-based) na posição que
     * cada uma ocupa, lidas em pares: as posições `2j` e `2j+1` formam o confronto `j`.
     *
     * Construção recursiva, que é a definição do chaveamento semeado:
     * ```
     * seedOrder(1)  = [1]
     * seedOrder(2n) = para cada p de seedOrder(n), emitir p e (2n+1−p)
     * ```
     * Assim `2 → [1,2]`, `4 → [1,4,2,3]` e `8 → [1,8,4,5,2,7,3,6]`, isto é `1×8, 4×5, 2×7, 3×6`.
     *
     * Três propriedades saem de graça daqui:
     * 1. **A 1ª e a 2ª cabeças caem em metades opostas** (e 1–4 em quartos distintos…). Como o
     *    [Advancement] cruza vizinhos (`2j` e `2j+1` → `j`), a metade da chave é o bloco contíguo
     *    de confrontos: com a ordem sequencial a cabeça 2 nascia colada na 1 e as duas se
     *    encontravam na semifinal.
     * 2. **A cabeça melhor de cada par está sempre no lado A** — em todo par `(p, 2n+1−p)` vale
     *    `p ≤ n < 2n+1−p`.
     * 3. **O BYE cai sozinho nas melhores cabeças.** Numa chave de `T` vagas com `n` reais, as
     *    cabeças virtuais são `n+1..T`; a cabeça virtual `T+1−p` é adversária de `p`, logo quem
     *    passa direto é exatamente `1..T−n`. Não é preciso mover par nenhum de lugar.
     */
    fun seedOrder(size: Int): List<Int> {
        require(size >= 1 && (size and (size - 1)) == 0) {
            "a ordem de semeadura só existe para chave em potência de 2 (recebeu $size)"
        }
        var order = listOf(1)
        while (order.size < size) {
            val doubled = order.size * 2
            order = buildList(doubled) {
                order.forEach { seed ->
                    add(seed)
                    add(doubled + 1 - seed)
                }
            }
        }
        return order
    }

    /**
     * Os confrontos de estreia em termos de **cabeça** (1-based): o índice da lista é o índice do
     * confronto, e o par é `(cabeça do lado A, cabeça do lado B)`, sempre com a melhor no lado A.
     */
    fun firstRoundPairs(size: Int): List<Pair<Int, Int>> {
        require(size >= 2) { "uma chave precisa de ao menos 2 vagas (recebeu $size)" }
        return seedOrder(size).chunked(2).map { (a, b) -> a to b }
    }

    /**
     * Monta a chave inteira a partir dos participantes **já ordenados por semeadura**: o primeiro é a
     * cabeça 1, o segundo a cabeça 2, e assim por diante. No mata-mata puro essa ordem é a do
     * sorteio; depois dos grupos é a de [Qualifiers.forKnockout].
     *
     * As vagas que faltam para fechar a potência de 2 são **cabeças virtuais**, e por construção só
     * aparecem no lado B de uma das melhores cabeças ([seedOrder]): daí sai o BYE sem remanejamento,
     * e a garantia de que **nenhum confronto nasce com dois BYEs** (o lado A é sempre real).
     *
     * @param firstPhaseOrder deslocamento da numeração das fases (a chave que vem depois dos grupos).
     * @param thirdPlaceMatch acrescenta a fase [PhaseType.THIRD_PLACE] (um confronto, vagas a definir,
     *   alimentado por [Advancement.thirdPlaceDestination]). Só existe com **4 ou mais**
     *   participantes: com 3, uma semifinal é BYE e só há um semifinalista derrotado — ele é o 3º
     *   sem jogar ([TournamentConclusion.podium] já sabe disso). Default `false`: sem 3º lugar.
     */
    fun buildBracket(
        participantIds: List<Long>,
        firstPhaseOrder: Int = 0,
        thirdPlaceMatch: Boolean = false,
    ): KnockoutBracket {
        require(participantIds.size >= 2) {
            "uma chave eliminatória precisa de ao menos 2 participantes (recebeu ${participantIds.size})"
        }
        require(participantIds.toSet().size == participantIds.size) {
            "participante repetido na chave — cada um entra uma única vez"
        }

        val size = bracketSize(participantIds.size)

        val firstRound = firstRoundPairs(size).mapIndexed { index, (seedA, seedB) ->
            // O lado A é sempre real: `seedA <= size / 2 < participantIds.size`.
            val sideA = participantIds[seedA - 1]
            val sideB = participantIds.getOrNull(seedB - 1)
            PlannedMatch(index, sideA, sideB, isBye = sideB == null)
        }

        val phases = buildList {
            var matchesInPhase = size / 2
            var order = firstPhaseOrder
            add(PlannedPhase(order = order, type = PhaseType.KNOCKOUT, matches = firstRound))
            while (matchesInPhase > 1) {
                matchesInPhase /= 2
                order += 1
                add(
                    PlannedPhase(
                        order = order,
                        type = PhaseType.KNOCKOUT,
                        // Vagas a definir: quem entra aqui é o vencedor da fase anterior.
                        matches = List(matchesInPhase) { PlannedMatch(it, null, null) },
                    ),
                )
            }
            if (thirdPlaceMatch && participantIds.size >= 4) {
                add(
                    PlannedPhase(
                        order = order + 1,
                        type = PhaseType.THIRD_PLACE,
                        matches = listOf(PlannedMatch(0, null, null)),
                    ),
                )
            }
        }

        return KnockoutBracket(size = size, phases = phases)
    }
}

/**
 * Para onde vai quem vence (ou, na semifinal, quem perde) uma eliminatória.
 *
 * Numa chave em potência de 2 isso é aritmética: os confrontos `2j` e `2j+1` de uma fase alimentam
 * o confronto `j` da seguinte, o primeiro pelo lado A e o segundo pelo lado B. É por cruzar
 * **vizinhos** que a metade da chave é um bloco contíguo — e por isso a ordem de entrada precisa
 * ser semeada ([Knockout.seedOrder]).
 *
 * **Nada aqui sabe como o confronto foi vencido** — só recebe o índice.
 */
object Advancement {

    data class Destination(val matchIndex: Int, val side: Side)

    /** Vencedor do confronto [indexInPhase] → confronto e lado na fase seguinte. */
    fun winnerDestination(indexInPhase: Int): Destination = Destination(
        matchIndex = indexInPhase / 2,
        side = if (indexInPhase % 2 == 0) Side.A else Side.B,
    )

    /**
     * Perdedor da semifinal [semifinalIndex] (0 ou 1) → lado no confronto único de
     * [PhaseType.THIRD_PLACE]: o da semi 0 é o lado A, o da semi 1 o lado B.
     */
    fun thirdPlaceDestination(semifinalIndex: Int): Destination {
        require(semifinalIndex in 0..1) { "a semifinal tem 2 confrontos (recebeu o índice $semifinalIndex)" }
        return Destination(matchIndex = 0, side = if (semifinalIndex == 0) Side.A else Side.B)
    }
}

package br.com.codecacto.kmplib.tournament

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TournamentConclusionTest {

    private fun match(a: Long?, b: Long?, winner: Long?, finished: Boolean = true) =
        TournamentConclusion.MatchSummary(a, b, winner, finished)

    private fun phase(order: Int, type: PhaseType, vararg matches: TournamentConclusion.MatchSummary) =
        TournamentConclusion.PhaseSummary(order, type, matches.toList())

    private val semis = phase(0, PhaseType.KNOCKOUT, match(1, 2, 1), match(3, 4, 3))
    private val decidedFinal = phase(1, PhaseType.KNOCKOUT, match(1, 3, 3))
    private val openFinal = phase(1, PhaseType.KNOCKOUT, match(1, 3, null, finished = false))

    /** Portado do `VerificacaoDoMotorTest`: o comportamento do TorneioDePenalti (sem 3º lugar). */
    @Test
    fun podiumWithoutThirdPlace() {
        assertEquals(listOf(3L, 1L), TournamentConclusion.podium(TournamentFormat.KNOCKOUT, listOf(semis, decidedFinal)))
        assertEquals(
            emptyList(),
            TournamentConclusion.podium(TournamentFormat.KNOCKOUT, listOf(semis, openFinal)),
            "final reaberta pelo desfazer NÃO mantém campeão",
        )

        val groupA = phase(0, PhaseType.GROUP, match(1, 2, 1), match(3, 4, 3))
        assertTrue(TournamentConclusion.groupsComplete(listOf(groupA)))
        assertEquals(emptyList(), TournamentConclusion.podium(TournamentFormat.GROUPS_THEN_KNOCKOUT, listOf(groupA)))
        assertEquals(
            listOf(1L, 3L, 4L),
            TournamentConclusion.podium(TournamentFormat.GROUPS_THEN_KNOCKOUT, listOf(groupA), listOf(1, 3, 4, 2)),
            "grupo sem chave conclui pela tabela",
        )
        assertEquals(
            listOf(3L, 1L),
            TournamentConclusion.podium(TournamentFormat.GROUPS_THEN_KNOCKOUT, listOf(groupA, decidedFinal.copy(order = 2))),
        )

        val rounds = listOf(phase(0, PhaseType.ROUND, match(1, 2, 1)), phase(1, PhaseType.ROUND, match(3, 4, 3)))
        assertEquals(
            listOf(3L, 1L, 4L),
            TournamentConclusion.podium(TournamentFormat.ROUND_ROBIN, rounds, listOf(3, 1, 4, 2)),
        )
        assertEquals(emptyList(), TournamentConclusion.podium(TournamentFormat.ROUND_ROBIN, emptyList(), listOf(1, 2)))
    }

    @Test
    fun podiumWithThirdPlaceMatch() {
        val third = phase(2, PhaseType.THIRD_PLACE, match(2, 4, 4))
        assertEquals(
            listOf(3L, 1L, 4L),
            TournamentConclusion.podium(TournamentFormat.KNOCKOUT, listOf(semis, decidedFinal, third), thirdPlaceMatch = true),
        )
        // 3º lugar ainda não jogado: o torneio não acabou.
        val pending = phase(2, PhaseType.THIRD_PLACE, match(2, 4, null, finished = false))
        assertFalse(TournamentConclusion.isComplete(listOf(semis, decidedFinal, pending)))
        assertEquals(
            emptyList(),
            TournamentConclusion.podium(TournamentFormat.KNOCKOUT, listOf(semis, decidedFinal, pending), thirdPlaceMatch = true),
        )
        // Grupos + chave com 3º lugar.
        val group = phase(0, PhaseType.GROUP, match(1, 2, 1))
        assertEquals(
            listOf(3L, 1L, 4L),
            TournamentConclusion.podium(
                TournamentFormat.GROUPS_THEN_KNOCKOUT,
                listOf(group, semis.copy(order = 1), decidedFinal.copy(order = 2), third.copy(order = 3)),
                thirdPlaceMatch = true,
            ),
        )
    }

    /** Com 3 participantes e 3º lugar ligado: uma semi é BYE, e o perdedor da outra é o 3º. */
    @Test
    fun threeParticipantsThirdPlaceIsTheLoneSemifinalLoser() {
        val bracket = Knockout.buildBracket(listOf(1L, 2L, 3L), thirdPlaceMatch = true)
        assertEquals(2, bracket.phases.size, "sem fase de 3º lugar")
        val semisWithBye = phase(0, PhaseType.KNOCKOUT, match(1, null, 1), match(2, 3, 2))
        val final = phase(1, PhaseType.KNOCKOUT, match(1, 2, 2))
        assertEquals(
            listOf(2L, 1L, 3L),
            TournamentConclusion.podium(TournamentFormat.KNOCKOUT, listOf(semisWithBye, final), thirdPlaceMatch = true),
        )
        // Sem a opção, o pódio da chave tem dois nomes (comportamento do TorneioDePenalti).
        assertEquals(listOf(2L, 1L), TournamentConclusion.podium(TournamentFormat.KNOCKOUT, listOf(semisWithBye, final)))
        // Sem BYE na semi e sem fase de 3º, não se inventa terceiro.
        assertEquals(
            listOf(3L, 1L),
            TournamentConclusion.podium(TournamentFormat.KNOCKOUT, listOf(semis, decidedFinal), thirdPlaceMatch = true),
        )
        // Só a final (2 participantes): não há semi de onde tirar terceiro.
        assertEquals(
            listOf(3L, 1L),
            TournamentConclusion.podium(TournamentFormat.KNOCKOUT, listOf(decidedFinal), thirdPlaceMatch = true),
        )
    }

    @Test
    fun completeness() {
        assertFalse(TournamentConclusion.isComplete(emptyList()))
        assertFalse(TournamentConclusion.groupsComplete(listOf(semis)), "sem grupo não há gatilho")
        assertFalse(phase(0, PhaseType.GROUP).isFinished, "fase vazia não está concluída")
        assertFalse(
            TournamentConclusion.groupsComplete(listOf(phase(0, PhaseType.GROUP, match(1, 2, null, finished = false)))),
        )
        // Final com mais de um confronto não é final: sem pódio.
        assertEquals(emptyList(), TournamentConclusion.podium(TournamentFormat.KNOCKOUT, listOf(semis)))
    }
}

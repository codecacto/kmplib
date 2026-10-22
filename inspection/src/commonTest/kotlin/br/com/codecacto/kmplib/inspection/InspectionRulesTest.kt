package br.com.codecacto.kmplib.inspection

import br.com.codecacto.kmplib.inspection.model.ExitResult
import br.com.codecacto.kmplib.inspection.model.InspectionPhase
import br.com.codecacto.kmplib.inspection.model.InspectionRules
import br.com.codecacto.kmplib.inspection.model.ItemCondition
import br.com.codecacto.kmplib.inspection.model.ItemFilter
import br.com.codecacto.kmplib.inspection.model.ItemRowState
import br.com.codecacto.kmplib.inspection.model.MediaQueueBannerKind
import br.com.codecacto.kmplib.inspection.model.MediaQueueSummary
import br.com.codecacto.kmplib.inspection.preview.InspectionSamples
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InspectionRulesTest {

    private val itens = InspectionSamples.items

    @Test
    fun filtros_da_entrada_e_da_saida() {
        assertEquals(listOf(ItemFilter.ALL, ItemFilter.WITH_PROBLEM, ItemFilter.WITHOUT_PHOTO), InspectionRules.filtersFor(InspectionPhase.ENTRY))
        assertEquals(listOf(ItemFilter.ALL, ItemFilter.CHANGED, ItemFilter.PENDING), InspectionRules.filtersFor(InspectionPhase.EXIT))
    }

    @Test
    fun contagens_das_pilulas_do_banheiro() {
        val c = InspectionRules.filterCounts(itens, InspectionPhase.ENTRY)
        assertEquals(6, c[ItemFilter.ALL])
        assertEquals(2, c[ItemFilter.WITH_PROBLEM]) // torneira (Ruim) + chuveiro (Regular)
        assertEquals(4, c[ItemFilter.WITHOUT_PHOTO]) // chuveiro, box, paredes, teto
        assertEquals(2, InspectionRules.problemCount(itens, InspectionPhase.ENTRY))
    }

    @Test
    fun tarefa_guiada_vem_primeiro_e_respeita_o_filtro() {
        val tarefa = ItemRowState("hidro", "Foto do hidrômetro", condition = null, isGuidedTask = true, guidedTaskRequiredPhotos = 1)
        val visiveis = InspectionRules.visibleItems(itens + tarefa, ItemFilter.ALL, InspectionPhase.ENTRY)
        assertEquals("hidro", visiveis.first().id)
        val semFoto = InspectionRules.visibleItems(itens + tarefa, ItemFilter.WITHOUT_PHOTO, InspectionPhase.ENTRY)
        assertEquals("hidro", semFoto.first().id)
        assertEquals(5, semFoto.size)
    }

    @Test
    fun problema_e_pendencia_na_saida() {
        val a = ItemRowState("a", "A", exitResult = ExitResult.CONFORMING)
        val b = ItemRowState("b", "B", exitResult = ExitResult.RESERVATION, changedSinceEntry = true)
        val c = ItemRowState("c", "C", exitResult = null)
        val saida = listOf(a, b, c)
        assertEquals(1, InspectionRules.problemCount(saida, InspectionPhase.EXIT))
        val conta = InspectionRules.filterCounts(saida, InspectionPhase.EXIT)
        assertEquals(1, conta[ItemFilter.CHANGED])
        assertEquals(1, conta[ItemFilter.PENDING])
        // Regular na ENTRADA não conta como problema da saída.
        assertTrue(!ItemRowState("x", "X", ItemCondition.BAD, exitResult = ExitResult.CONFORMING).isProblem(InspectionPhase.EXIT))
    }

    @Test
    fun marcar_todos_como_conforme_nao_sobrescreve_o_ja_avaliado_nem_o_que_mudou() {
        val itensSaida = listOf(
            ItemRowState("a", "A", exitResult = null),
            ItemRowState("b", "B", exitResult = ExitResult.NON_CONFORMING),
            ItemRowState("c", "C", exitResult = null, changedSinceEntry = true),
            ItemRowState("t", "T", exitResult = null, isGuidedTask = true),
        )
        assertEquals(listOf("a"), InspectionRules.markAllConformingTargets(itensSaida).map { it.id })
    }

    @Test
    fun contagem_por_estado_inclui_os_zeros() {
        val c = InspectionRules.conditionCounts(listOf(ItemCondition.GOOD, ItemCondition.GOOD, ItemCondition.BAD, null))
        assertEquals(5, c.size)
        assertEquals(0, c[ItemCondition.NOT_APPLICABLE])
        assertEquals(2, c[ItemCondition.GOOD])
        assertEquals(3, InspectionRules.resultCounts(emptyList()).size)
    }

    @Test
    fun faixa_da_fila_por_prioridade() {
        assertEquals(MediaQueueBannerKind.HIDDEN, InspectionRules.queueBannerKind(MediaQueueSummary(0, 0)))
        assertEquals(MediaQueueBannerKind.UPLOADING, InspectionRules.queueBannerKind(MediaQueueSummary(300, 212)))
        assertEquals(MediaQueueBannerKind.FAILED, InspectionRules.queueBannerKind(MediaQueueSummary(300, 212, failed = 5, offline = true)))
        assertEquals(MediaQueueBannerKind.OFFLINE, InspectionRules.queueBannerKind(MediaQueueSummary(300, 212, offline = true)))
        assertEquals(MediaQueueBannerKind.WAITING_WIFI, InspectionRules.queueBannerKind(MediaQueueSummary(10, 8, waitingWifi = 2)))
        assertEquals(MediaQueueBannerKind.UPLOADING, InspectionRules.queueBannerKind(MediaQueueSummary(10, 5, waitingWifi = 2)))
        assertEquals(MediaQueueBannerKind.DONE, InspectionRules.queueBannerKind(MediaQueueSummary(10, 10)))
        assertEquals(0.7066667f, InspectionRules.queueFraction(MediaQueueSummary(300, 212)), 0.0001f)
    }

    @Test
    fun mover_item_da_lista() {
        assertEquals(listOf("b", "c", "a"), InspectionRules.move(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("a", "b"), InspectionRules.move(listOf("a", "b"), 0, 5))
    }
}

package br.com.codecacto.kmplib.ui.components

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Testes das regras puras do [ChecklistItem] — o que define se o item comunica o estado certo:
 * tom efetivo, tingimento de fundo e descrição anunciada pelo leitor de tela.
 */
class ChecklistItemTest {

    @Test
    fun `tom efetivo segue o estado`() {
        assertEquals(
            StatusTone.SUCCESS,
            checklistItemTone(checked = true, StatusTone.SUCCESS, StatusTone.NEUTRAL),
        )
        assertEquals(
            StatusTone.NEUTRAL,
            checklistItemTone(checked = false, StatusTone.SUCCESS, StatusTone.NEUTRAL),
        )
    }

    @Test
    fun `pendencia critica usa tom de perigo enquanto nao marcada`() {
        // Caso "ainda a bordo" da conferência: o item nasce vermelho e só perde o destaque ao marcar.
        val tone = checklistItemTone(
            checked = false,
            checkedTone = StatusTone.SUCCESS,
            uncheckedTone = StatusTone.DANGER,
        )
        assertEquals(StatusTone.DANGER, tone)
        assertTrue(checklistItemUsesToneContainer(tone))
    }

    @Test
    fun `NEUTRAL nao tinge o fundo`() {
        assertFalse(checklistItemUsesToneContainer(StatusTone.NEUTRAL))
    }

    @Test
    fun `todos os tons com carga semantica tingem o fundo`() {
        StatusTone.entries
            .filter { it != StatusTone.NEUTRAL }
            .forEach { assertTrue(checklistItemUsesToneContainer(it), "$it deveria tingir") }
    }

    @Test
    fun `descricao de estado usa o vocabulario do dominio`() {
        val texts = ChecklistItemTexts(checkedState = "Embarcado", uncheckedState = "Aguardando")
        assertEquals("Embarcado", checklistItemStateDescription(checked = true, texts))
        assertEquals("Aguardando", checklistItemStateDescription(checked = false, texts))
    }

    @Test
    fun `descricao de estado tem default pt-BR`() {
        val texts = ChecklistItemTexts()
        assertEquals("Marcado", checklistItemStateDescription(true, texts))
        assertEquals("Não marcado", checklistItemStateDescription(false, texts))
    }

    @Test
    fun `alvo de toque e maior que o minimo do Material`() {
        // 48dp é o mínimo do Material; este componente é de campo (luva/direção) e exige mais.
        assertEquals(64.dp, ChecklistItemDefaults.MinHeight)
        assertTrue(ChecklistItemDefaults.MinHeight > 48.dp)
    }

    @Test
    fun `estado nunca depende so da cor`() {
        // WCAG 1.4.1: os dois estados têm ícones DIFERENTES, não apenas tons diferentes.
        assertTrue(ChecklistItemDefaults.CheckedIcon.name != ChecklistItemDefaults.UncheckedIcon.name)
    }
}

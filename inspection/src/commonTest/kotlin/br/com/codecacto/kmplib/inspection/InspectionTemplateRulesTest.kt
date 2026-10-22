package br.com.codecacto.kmplib.inspection

import br.com.codecacto.kmplib.inspection.model.CustomQuestionDraft
import br.com.codecacto.kmplib.inspection.model.EditItemDraft
import br.com.codecacto.kmplib.inspection.model.TemplateItem
import br.com.codecacto.kmplib.inspection.model.TemplateQuestion
import br.com.codecacto.kmplib.inspection.model.TemplateQuestionType
import br.com.codecacto.kmplib.inspection.ui.template.TemplateStandardQuestions
import br.com.codecacto.kmplib.inspection.ui.template.isValid
import br.com.codecacto.kmplib.inspection.ui.template.templateItemSummary
import br.com.codecacto.kmplib.inspection.ui.template.validateCustomQuestion
import br.com.codecacto.kmplib.inspection.ui.template.validateItemDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InspectionTemplateRulesTest {
    private val texts = InspectionTexts()

    @Test
    fun resumo_do_item_como_no_editor() {
        val item = TemplateItem(
            "t", "Torneira", minPhotos = 1,
            questions = listOf(TemplateQuestion("m", "Material"), TemplateQuestion("c", "Cor")),
        )
        assertEquals("Material, Cor · 1 foto", templateItemSummary(item, texts))
        assertEquals("Funciona?, Material, Cor · 1 foto", templateItemSummary(item.copy(asksFunctioning = true), texts))
        assertEquals("", templateItemSummary(TemplateItem("x", "X"), texts))
        assertEquals("2 fotos", templateItemSummary(TemplateItem("x", "X", minPhotos = 2), texts))
    }

    @Test
    fun item_sem_nome_tem_erro_no_campo() {
        assertNotNull(validateItemDraft(EditItemDraft(null, name = "  "), texts.template).nameError)
        assertNull(validateItemDraft(EditItemDraft(null, name = "Pia"), texts.template).nameError)
    }

    @Test
    fun pergunta_de_escolha_exige_duas_opcoes_distintas() {
        val t = texts.template
        val so1 = validateCustomQuestion(CustomQuestionDraft("Acabamento", TemplateQuestionType.CHOICE, optionsText = "Fosco\nfosco\n "), t)
        assertFalse(so1.isValid())
        // "Fosco" e "fosco" são a mesma opção; linha vazia não conta.
        assertEquals(listOf("Fosco"), so1.options)
        val repetida = validateCustomQuestion(CustomQuestionDraft("Acabamento", TemplateQuestionType.CHOICE, optionsText = "Fosco\nFosco"), t)
        assertFalse(repetida.isValid())
        val ok = validateCustomQuestion(CustomQuestionDraft("Acabamento", TemplateQuestionType.CHOICE, optionsText = "Fosco\nBrilho"), t)
        assertTrue(ok.isValid())
        assertTrue(validateCustomQuestion(CustomQuestionDraft("Voltagem", TemplateQuestionType.NUMBER), t).isValid())
        assertFalse(validateCustomQuestion(CustomQuestionDraft("", TemplateQuestionType.TEXT), t).isValid())
    }

    @Test
    fun perguntas_padrao_e_tipos_do_formschema() {
        assertEquals(listOf("material", "cor", "marca", "quantidade"), TemplateStandardQuestions.ALL)
        assertEquals(TemplateQuestionType.NUMBER, TemplateStandardQuestions.typeOf(TemplateStandardQuestions.QUANTITY))
        assertEquals(TemplateQuestionType.TEXT, TemplateStandardQuestions.typeOf(TemplateStandardQuestions.COLOR))
        assertEquals("yesNo", TemplateQuestionType.YES_NO.formType)
        assertEquals("Cor", TemplateStandardQuestions.label("cor", texts.template))
    }
}

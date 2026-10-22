package br.com.codecacto.kmplib.inspection.mapping

import br.com.codecacto.kmplib.core.text.foldForSearch
import br.com.codecacto.kmplib.inspection.data.ItemFunctioning
import br.com.codecacto.kmplib.inspection.data.ItemPatch
import br.com.codecacto.kmplib.inspection.data.TemplateDto
import br.com.codecacto.kmplib.inspection.data.TemplateItemDto
import br.com.codecacto.kmplib.inspection.data.TemplateItemInput
import br.com.codecacto.kmplib.inspection.data.TemplateOrigin
import br.com.codecacto.kmplib.inspection.data.TemplateSummaryDto
import br.com.codecacto.kmplib.inspection.data.VoiceSuggestionsDto
import br.com.codecacto.kmplib.inspection.model.EditItemDraft
import br.com.codecacto.kmplib.inspection.model.TemplateCard
import br.com.codecacto.kmplib.inspection.model.TemplateEditorState
import br.com.codecacto.kmplib.inspection.model.TemplateItem
import br.com.codecacto.kmplib.inspection.model.TemplateListState
import br.com.codecacto.kmplib.inspection.model.TemplateListTab
import br.com.codecacto.kmplib.inspection.model.TemplateQuestion
import br.com.codecacto.kmplib.inspection.model.TemplateQuestionType
import br.com.codecacto.kmplib.inspection.model.TemplateRoom
import br.com.codecacto.kmplib.inspection.model.VoiceCaptureState
import br.com.codecacto.kmplib.inspection.ui.template.TemplateStandardQuestions
import br.com.codecacto.kmplib.ui.form.FormJson
import br.com.codecacto.kmplib.ui.form.FormQuestionType
import br.com.codecacto.kmplib.ui.form.FormSchemaV1
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Modelos (editor) — domínio ↔ estado de tela. A pergunta do item mora no `fieldSchema` (FormSchema v1),
 * escrito **igual ao da weblib** (`templateItemFormSchema`): uma seção `campos`, pergunta própria de escolha
 * = `singleChoice` com `display: chips` e o valor da opção em slug do rótulo. As perguntas-padrão são
 * reconhecidas pelo id da weblib (`material`, `color`, `brand`, `quantity`) e pelo do kit
 * ([TemplateStandardQuestions]); na escrita vai o da weblib — o mesmo modelo editado nas duas pontas não
 * duplica a pergunta.
 */
object InspectionTemplateMapping {

    /** Id da pergunta-padrão no FIO (o da weblib) para a chave do kit. */
    fun wireIdOfStandard(key: String): String = when (key) {
        TemplateStandardQuestions.COLOR -> "color"
        TemplateStandardQuestions.BRAND -> "brand"
        TemplateStandardQuestions.QUANTITY -> "quantity"
        else -> key
    }

    /** Chave do kit para um id de pergunta vindo do fio (`null` = pergunta própria). */
    fun standardKeyOf(questionId: String): String? = when (questionId) {
        "material", TemplateStandardQuestions.MATERIAL -> TemplateStandardQuestions.MATERIAL
        "color", TemplateStandardQuestions.COLOR -> TemplateStandardQuestions.COLOR
        "brand", TemplateStandardQuestions.BRAND -> TemplateStandardQuestions.BRAND
        "quantity", TemplateStandardQuestions.QUANTITY -> TemplateStandardQuestions.QUANTITY
        else -> null
    }

    fun templateCard(summary: TemplateSummaryDto, usedInCount: Int = 0, editedAtLabel: String? = null): TemplateCard = TemplateCard(
        id = summary.id,
        name = summary.name,
        roomCount = summary.groupCount,
        itemCount = summary.itemCount,
        usedInCount = usedInCount,
        editedAtLabel = editedAtLabel,
        isReadyMade = summary.origin == TemplateOrigin.SEED,
    )

    /** "Meus" = criados pelo dono; "Prontos" = sementes da lib. */
    fun templateListState(
        templates: List<TemplateSummaryDto>,
        tab: TemplateListTab = TemplateListTab.MINE,
        editedAtLabel: (TemplateSummaryDto) -> String? = { null },
    ): TemplateListState = TemplateListState(
        mine = templates.filter { it.origin != TemplateOrigin.SEED }.map { templateCard(it, editedAtLabel = editedAtLabel(it)) },
        readyMade = templates.filter { it.origin == TemplateOrigin.SEED }.map { templateCard(it, editedAtLabel = editedAtLabel(it)) },
        tab = tab,
    )

    /** O editor com o cômodo [selectedRoomId] (default: o primeiro) aberto. */
    fun templateEditorState(template: TemplateDto, selectedRoomId: String? = null): TemplateEditorState {
        val salas = template.groups.sortedBy { it.position }
        val aberta = salas.firstOrNull { it.id == selectedRoomId } ?: salas.firstOrNull()
        return TemplateEditorState(
            name = template.name,
            rooms = salas.map { TemplateRoom(it.id, it.name) },
            selectedRoomId = aberta?.id,
            items = aberta?.items.orEmpty().sortedBy { it.position }.map(::templateItem),
            isReadyMade = template.origin == TemplateOrigin.SEED,
        )
    }

    /**
     * Item do modelo para o kit. `asksFunctioning` = `false`: o contrato v1 não tem esse sinal no modelo
     * (lacuna registrada no backlog da backlib).
     */
    fun templateItem(dto: TemplateItemDto): TemplateItem = TemplateItem(
        id = dto.id,
        name = dto.name,
        required = dto.required,
        minPhotos = dto.minPhotos,
        defaultCondition = dto.defaultState.toCondition() ?: br.com.codecacto.kmplib.inspection.model.ItemCondition.GOOD,
        questions = questionsOf(dto.fieldSchema),
    )

    /** As perguntas de um `fieldSchema` (só as de topo das seções de campos; tipo fora do kit é ignorado). */
    fun questionsOf(fieldSchema: JsonElement?): List<TemplateQuestion> {
        val schema = decodeSchema(fieldSchema) ?: return emptyList()
        return schema.sections.flatMap { it.questions }.mapNotNull { q ->
            val tipo = when (q.type) {
                FormQuestionType.TEXT, FormQuestionType.LONG_TEXT -> TemplateQuestionType.TEXT
                FormQuestionType.NUMBER -> TemplateQuestionType.NUMBER
                FormQuestionType.DATE -> TemplateQuestionType.DATE
                FormQuestionType.YES_NO -> TemplateQuestionType.YES_NO
                FormQuestionType.SINGLE_CHOICE -> TemplateQuestionType.CHOICE
                else -> return@mapNotNull null
            }
            TemplateQuestion(
                id = q.id,
                label = q.text,
                type = tipo,
                required = q.required,
                options = q.options.map { it.label },
                standardKey = standardKeyOf(q.id),
            )
        }
    }

    /** O `fieldSchema` do item (FormSchema v1). Sem pergunta = `null` (o motor grava ausência). */
    fun itemFormSchema(itemId: String, itemName: String, questions: List<TemplateQuestion>): JsonObject? {
        if (questions.isEmpty()) return null
        return buildJsonObject {
            put("schemaVersion", 1)
            put("id", "item.$itemId")
            put("title", itemName)
            put(
                "sections",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("id", "campos")
                            put("title", itemName)
                            put("kind", "fields")
                            put("questions", JsonArray(questions.map(::questionToForm)))
                        },
                    )
                },
            )
        }
    }

    /** Uma pergunta do kit como `FormQuestion` v1 (escolha → `singleChoice` com valores em slug únicos). */
    fun questionToForm(q: TemplateQuestion): JsonObject = buildJsonObject {
        put("id", q.standardKey?.let(::wireIdOfStandard) ?: q.id)
        put("text", q.label)
        put("required", q.required)
        when (q.type) {
            TemplateQuestionType.CHOICE -> {
                put("type", "singleChoice")
                put("display", "chips")
                val usados = mutableSetOf<String>()
                put(
                    "options",
                    JsonArray(
                        q.options.map { rotulo ->
                            val base = optionValue(rotulo)
                            var valor = base
                            var n = 2
                            while (valor in usados) valor = "$base-${n++}"
                            usados += valor
                            buildJsonObject { put("value", valor); put("label", rotulo) }
                        },
                    ),
                )
            }
            else -> put("type", q.type.formType)
        }
    }

    /** Slug do rótulo (sem acento, minúsculo, `-` entre palavras) — o `optionValue` da weblib. */
    fun optionValue(label: String): String =
        foldForSearch(label).replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "opcao" }

    /**
     * O rascunho do diálogo "Editar item" → corpo de `POST/PUT …/items`. As perguntas-padrão ligadas entram
     * primeiro, na ordem dos chips; depois as próprias. [labelOfStandard] = o rótulo da pergunta-padrão no
     * idioma da tela (`TemplateStandardQuestions.label(key, texts.template)`).
     */
    fun itemInput(
        draft: EditItemDraft,
        labelOfStandard: (String) -> String,
        base: TemplateItemDto? = null,
    ): TemplateItemInput {
        val padrao = TemplateStandardQuestions.ALL.filter { it in draft.standardQuestions }.map { key ->
            TemplateQuestion(id = key, label = labelOfStandard(key), type = TemplateStandardQuestions.typeOf(key), standardKey = key)
        }
        val perguntas = padrao + draft.customQuestions
        return TemplateItemInput(
            name = draft.name.trim(),
            required = draft.required,
            minPhotos = draft.minPhotos.coerceAtLeast(0),
            fieldSchema = itemFormSchema(draft.itemId ?: base?.id ?: "novo", draft.name.trim(), perguntas),
            defaultState = draft.defaultCondition.toItemState(),
            referenceValue = base?.referenceValue,
            mediaTasks = base?.mediaTasks.orEmpty(),
        )
    }

    /** As propostas ACEITAS da voz como mudanças de item (a pessoa confirmou cada uma na tela). */
    fun voicePatches(suggestions: VoiceSuggestionsDto, state: VoiceCaptureState): List<ItemPatch> =
        state.proposals.mapNotNull { row ->
            if (!row.matched || !row.accepted) return@mapNotNull null
            val i = row.id.substringBefore('-').toIntOrNull() ?: return@mapNotNull null
            val s = suggestions.suggestions.getOrNull(i) ?: return@mapNotNull null
            ItemPatch(
                id = s.itemId,
                state = row.condition?.toItemState() ?: s.state,
                functioning = s.functioning?.takeIf { it != ItemFunctioning.UNKNOWN },
                observation = row.note.takeIf { it.isNotBlank() },
            )
        }

    private fun decodeSchema(el: JsonElement?): FormSchemaV1? {
        if (el == null || el !is JsonObject) return null
        return runCatching { FormJson.format.decodeFromJsonElement(FormSchemaV1.serializer(), el) }.getOrNull()
    }
}

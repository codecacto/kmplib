package br.com.codecacto.kmplib.inspection.ui.template

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.inspection.InspectionTemplateTexts
import br.com.codecacto.kmplib.inspection.InspectionTexts
import br.com.codecacto.kmplib.inspection.fmt
import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIconView
import br.com.codecacto.kmplib.inspection.icons.InspectionIcons
import br.com.codecacto.kmplib.inspection.label
import br.com.codecacto.kmplib.inspection.model.CustomQuestionDraft
import br.com.codecacto.kmplib.inspection.model.EditItemDraft
import br.com.codecacto.kmplib.inspection.model.ItemCondition
import br.com.codecacto.kmplib.inspection.model.TemplateCard
import br.com.codecacto.kmplib.inspection.model.TemplateEditorEvent
import br.com.codecacto.kmplib.inspection.model.TemplateEditorState
import br.com.codecacto.kmplib.inspection.model.TemplateItem
import br.com.codecacto.kmplib.inspection.model.TemplateListEvent
import br.com.codecacto.kmplib.inspection.model.TemplateListState
import br.com.codecacto.kmplib.inspection.model.TemplateListTab
import br.com.codecacto.kmplib.inspection.model.TemplateQuestionType
import br.com.codecacto.kmplib.inspection.plural
import br.com.codecacto.kmplib.inspection.rememberInspectionTexts
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.ui.InspectionDashedAddButton
import br.com.codecacto.kmplib.inspection.ui.InspectionDialog
import br.com.codecacto.kmplib.inspection.ui.InspectionEyebrow
import br.com.codecacto.kmplib.inspection.ui.InspectionFooter
import br.com.codecacto.kmplib.inspection.ui.InspectionNoteField
import br.com.codecacto.kmplib.inspection.ui.InspectionPrimaryButton
import br.com.codecacto.kmplib.inspection.ui.InspectionSecondaryButton
import br.com.codecacto.kmplib.inspection.ui.InspectionTestTags
import br.com.codecacto.kmplib.inspection.ui.InspectionTextField
import br.com.codecacto.kmplib.inspection.ui.InspectionTopBar
import br.com.codecacto.kmplib.inspection.ui.InspectionTopBarTextAction
import br.com.codecacto.kmplib.inspection.ui.KitIconButton
import br.com.codecacto.kmplib.inspection.ui.inspectionTextStyle
import br.com.codecacto.kmplib.inspection.ui.items.InlineErrorBanner
import br.com.codecacto.kmplib.inspection.ui.rooms.MenuRow
import br.com.codecacto.kmplib.ui.components.AppDropdownMenu
import br.com.codecacto.kmplib.ui.components.AppStepper
import br.com.codecacto.kmplib.ui.components.AppTabRow
import br.com.codecacto.kmplib.ui.components.EmptyState
import br.com.codecacto.kmplib.ui.components.ErrorState
import br.com.codecacto.kmplib.ui.components.ReorderableList

/** As perguntas-padrão do item, na ordem dos chips (D14/D15). "Funciona?" é eixo, não pergunta. */
object TemplateStandardQuestions {
    const val MATERIAL = "material"
    const val COLOR = "cor"
    const val BRAND = "marca"
    const val QUANTITY = "quantidade"
    val ALL: List<String> = listOf(MATERIAL, COLOR, BRAND, QUANTITY)

    /** O tipo FormSchema de cada padrão: Quantidade = número; o resto, texto. */
    fun typeOf(key: String): TemplateQuestionType =
        if (key == QUANTITY) TemplateQuestionType.NUMBER else TemplateQuestionType.TEXT

    fun label(key: String, texts: InspectionTemplateTexts): String = when (key) {
        MATERIAL -> texts.questionMaterial
        COLOR -> texts.questionColor
        BRAND -> texts.questionBrand
        QUANTITY -> texts.questionQuantity
        else -> key
    }
}

/**
 * **Lista de modelos** (§2.14 app, LK-M14): abas "Meus modelos · N" | "Modelos prontos · N", cartões
 * (nome 17/700, "11 cômodos · 142 itens", "Usado em 2 vistorias · editado 14/10", ⋮), a seção
 * "COMEÇAR DE UM MODELO PRONTO" com "Copiar e editar" (modelo pronto é somente leitura) e o rodapé
 * "+ Novo modelo em branco".
 */
@Composable
fun InspectionTemplateList(
    state: TemplateListState,
    onEvent: (TemplateListEvent) -> Unit,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    val tt = texts.template
    Column(modifier.fillMaxSize().background(t.bg)) {
        InspectionTopBar(
            title = tt.templatesTitle,
            titleSize = 22.sp,
            titleWeight = FontWeight.Bold,
            onNavigate = { onEvent(TemplateListEvent.Back) },
            navigationDescription = texts.common.back,
        )
        AppTabRow(
            tabs = listOf(tt.myTemplates.fmt(state.mine.size), tt.readyTemplates.fmt(state.readyMade.size)),
            selectedIndex = state.tab.ordinal,
            onSelect = { onEvent(TemplateListEvent.SelectTab(TemplateListTab.entries[it])) },
            modifier = Modifier.padding(horizontal = 12.dp),
            height = 46.dp,
            selectedColor = t.brand,
            unselectedColor = t.muted,
            dividerColor = t.line,
            textStyle = inspectionTextStyle(15.sp),
            testTag = InspectionTestTags.TEMPLATES_TABS,
            optionTestKeys = listOf("meus", "prontos"),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> Unit
                state.errorMessage != null -> ErrorState(state.errorMessage, onRetry = { onEvent(TemplateListEvent.Retry) })
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.tab == TemplateListTab.MINE) {
                        if (state.mine.isEmpty()) {
                            item(key = "vazio") {
                                EmptyState(icon = InspectionIcons.vector(InspectionIcon.FactCheck), title = tt.newBlankTemplate)
                            }
                        }
                        items(state.mine, key = { "m-" + it.id }) { card -> MyTemplateCard(card, texts, onEvent) }
                        if (state.readyMade.isNotEmpty()) {
                            item(key = "h-prontos") { InspectionEyebrow(tt.startFromReady, Modifier.padding(top = 12.dp)) }
                            items(state.readyMade, key = { "r-" + it.id }) { card -> ReadyTemplateCard(card, texts, onEvent) }
                        }
                    } else {
                        items(state.readyMade, key = { "r2-" + it.id }) { card -> ReadyTemplateCard(card, texts, onEvent) }
                    }
                }
            }
        }
        InspectionFooter {
            InspectionPrimaryButton(
                text = "+ " + tt.newBlankTemplate,
                onClick = { onEvent(TemplateListEvent.NewBlank) },
                height = 56.dp,
                testTag = InspectionTestTags.TEMPLATES_NEW,
            )
        }
    }
}

@Composable
private fun MyTemplateCard(card: TemplateCard, texts: InspectionTexts, onEvent: (TemplateListEvent) -> Unit) {
    val t = InspectionTheme.tokens
    val forma = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(t.surface)
            .border(1.dp, t.line, forma)
            .clickable(role = Role.Button) { onEvent(TemplateListEvent.Open(card.id)) }
            .testTag(InspectionTestTags.template(card.id))
            .padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(card.name, style = inspectionTextStyle(17.sp, FontWeight.Bold))
            Text(texts.template.templateSummary.fmt(card.roomCount, card.itemCount), style = inspectionTextStyle(14.sp, color = t.muted, tabular = true))
            if (card.editedAtLabel != null) {
                Text(texts.template.templateUsage.fmt(card.usedInCount, card.editedAtLabel), style = inspectionTextStyle(14.sp, color = t.muted, tabular = true))
            }
        }
        KitIconButton(InspectionIcon.MoreVert, texts.common.moreOptions, { onEvent(TemplateListEvent.Menu(card.id)) }, size = 22.dp)
    }
}

@Composable
private fun ReadyTemplateCard(card: TemplateCard, texts: InspectionTexts, onEvent: (TemplateListEvent) -> Unit) {
    val t = InspectionTheme.tokens
    val forma = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(forma)
            .background(t.surface)
            .border(1.dp, t.line, forma)
            .clickable(role = Role.Button) { onEvent(TemplateListEvent.Open(card.id)) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(card.name, style = inspectionTextStyle(16.sp, FontWeight.SemiBold))
            Text(texts.template.templateSummary.fmt(card.roomCount, card.itemCount), style = inspectionTextStyle(14.sp, color = t.muted, tabular = true))
        }
        InspectionSecondaryButton(
            text = texts.template.copyAndEdit,
            onClick = { onEvent(TemplateListEvent.CopyAndEdit(card.id)) },
            height = 40.dp,
            contentColor = t.brand,
            textSize = 15.sp,
            testTag = InspectionTestTags.templateCopy(card.id),
        )
    }
}

/**
 * **Editor de modelo no app** (§2.14, LK-M14): barra 64 (nome · "Suíte master · 9 itens" · Prévia ·
 * Salvar), chips de cômodos (selecionado invertido; último "+ cômodo"; toque longo abre o menu do
 * cômodo), linhas de item REORDENÁVEIS por arrasto (alça · nome 16/600 + "Material, Cor · 1 foto" ·
 * etiqueta "Obrigatório" · editar), "+ Adicionar item" e o aviso "Mudanças valem para vistorias
 * novas". O diálogo "Editar item" ([EditItemDialog]) inclui o segundo passo da pergunta própria (D14).
 */
@Composable
fun InspectionTemplateEditor(
    state: TemplateEditorState,
    onEvent: (TemplateEditorEvent) -> Unit,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    val tt = texts.template
    val sala = state.selectedRoom
    Column(modifier.fillMaxSize().background(t.bg)) {
        InspectionTopBar(
            title = state.name,
            titleSize = 20.sp,
            titleWeight = FontWeight.Bold,
            subtitle = sala?.let { tt.editorSubtitle.fmt(it.name, state.items.size) },
            onNavigate = { onEvent(TemplateEditorEvent.Back) },
            navigationDescription = texts.common.back,
            actions = {
                InspectionTopBarTextAction(tt.preview, { onEvent(TemplateEditorEvent.Preview) }, weight = FontWeight.Normal)
                InspectionTopBarTextAction(
                    texts.common.save,
                    { onEvent(TemplateEditorEvent.Save) },
                    enabled = state.canSave,
                    size = 16.sp,
                    weight = FontWeight.Bold,
                    modifier = Modifier.testTag(InspectionTestTags.EDITOR_SAVE),
                )
            },
        )
        if (state.versionConflict) {
            InlineErrorBanner(tt.templateConflict, tt.reload, { onEvent(TemplateEditorEvent.Reload) }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        } else if (state.isReadyMade) {
            Text(tt.readOnlyTemplate, style = inspectionTextStyle(14.sp, color = t.muted), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        } else if (state.errorMessage != null) {
            InlineErrorBanner(state.errorMessage, texts.common.retry, { onEvent(TemplateEditorEvent.Save) }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        RoomChips(state, texts, onEvent)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (state.rooms.isEmpty()) {
                EmptyState(
                    icon = InspectionIcons.vector(InspectionIcon.LibraryAdd),
                    title = texts.rooms.emptyRooms,
                    action = { InspectionDashedAddButton(texts.rooms.addRoom, { onEvent(TemplateEditorEvent.AddRoom) }, Modifier.padding(horizontal = 16.dp)) },
                )
            } else {
                ReorderableList(
                    items = state.items,
                    key = { it.id },
                    onMove = { a, b -> onEvent(TemplateEditorEvent.MoveItem(a, b)) },
                    onReorderFinished = { onEvent(TemplateEditorEvent.MoveItemFinished) },
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Top,
                    enabled = !state.isReadyMade,
                    header = { item(key = "topo") { HorizontalDivider(thickness = 1.dp, color = t.line) } },
                    footer = {
                        item(key = "rodape") {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (!state.isReadyMade) {
                                    InspectionDashedAddButton(
                                        tt.addItem,
                                        { onEvent(TemplateEditorEvent.AddItem) },
                                        radius = 8.dp,
                                        textSize = 16.sp,
                                        weight = FontWeight.Bold,
                                        testTag = InspectionTestTags.EDITOR_ADD_ITEM,
                                    )
                                }
                                Text(
                                    tt.changesNote,
                                    style = inspectionTextStyle(14.sp, color = t.muted),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    },
                ) { item, _, arrastando ->
                    TemplateItemRow(
                        item = item,
                        texts = texts,
                        selected = state.editing?.itemId == item.id,
                        dragging = arrastando,
                        readOnly = state.isReadyMade,
                        handle = Modifier.dragHandle(),
                        onEdit = { onEvent(TemplateEditorEvent.EditItem(item.id)) },
                    )
                }
            }
        }
    }
    state.editing?.let { EditItemDialog(it, texts, onEvent) }
    if (state.confirmLeave) {
        InspectionDialog(
            title = tt.leaveEditorTitle,
            message = tt.leaveEditorMessage,
            confirmText = tt.leave,
            dismissText = texts.common.cancel,
            destructive = true,
            onConfirm = { onEvent(TemplateEditorEvent.ConfirmLeave) },
            onDismiss = { onEvent(TemplateEditorEvent.DismissLeave) },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RoomChips(state: TemplateEditorState, texts: InspectionTexts, onEvent: (TemplateEditorEvent) -> Unit) {
    val t = InspectionTheme.tokens
    val selecionado = state.selectedRoom?.id
    LazyRow(
        Modifier.fillMaxWidth().testTag(InspectionTestTags.EDITOR_ROOMS),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.rooms, key = { it.id }) { sala ->
            val ativo = sala.id == selecionado
            val forma = RoundedCornerShape(19.dp)
            Box(
                Modifier
                    .height(38.dp)
                    .clip(forma)
                    .background(if (ativo) t.ink else t.surface)
                    .then(if (ativo) Modifier else Modifier.border(1.dp, t.line, forma))
                    .combinedClickable(
                        role = Role.Tab,
                        onLongClick = { onEvent(TemplateEditorEvent.RoomMenu(sala.id)) },
                        onLongClickLabel = texts.common.moreOptions,
                        onClick = { onEvent(TemplateEditorEvent.SelectRoom(sala.id)) },
                    )
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(sala.name, style = inspectionTextStyle(15.sp, if (ativo) FontWeight.Bold else FontWeight.Normal, if (ativo) t.bg else t.ink))
            }
        }
        if (!state.isReadyMade) {
            item(key = "adicionar") {
                val forma = RoundedCornerShape(19.dp)
                Row(
                    Modifier
                        .height(38.dp)
                        .clip(forma)
                        .border(1.dp, t.line, forma)
                        .clickable(role = Role.Button) { onEvent(TemplateEditorEvent.AddRoom) }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    InspectionIconView(InspectionIcon.Add, size = 18.dp, tint = t.brand)
                    Text(texts.rooms.addRoomShort, style = inspectionTextStyle(15.sp, FontWeight.SemiBold, t.brand))
                }
            }
        }
    }
}

/** "Material, Cor · 1 foto" — perguntas ligadas + mínimo de fotos. Pura. */
fun templateItemSummary(item: TemplateItem, texts: InspectionTexts): String {
    val perguntas = buildList {
        if (item.asksFunctioning) add(texts.item.functioningLabel)
        item.questions.filter { it.enabled }.forEach { add(it.label) }
    }.joinToString(", ")
    val fotos = if (item.minPhotos > 0) plural(item.minPhotos, texts.items.photoCountOne, texts.items.photoCountOther) else ""
    return listOf(perguntas, fotos).filter { it.isNotEmpty() }.joinToString(" · ")
}

@Composable
private fun TemplateItemRow(
    item: TemplateItem,
    texts: InspectionTexts,
    selected: Boolean,
    dragging: Boolean,
    readOnly: Boolean,
    handle: Modifier,
    onEdit: () -> Unit,
) {
    val t = InspectionTheme.tokens
    Column(Modifier.fillMaxWidth().background(if (selected || dragging) t.brandSoft else t.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(enabled = !readOnly, role = Role.Button, onClick = onEdit)
                .testTag(InspectionTestTags.editorItem(item.id))
                .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!readOnly) {
                Box(handle.size(width = 32.dp, height = 44.dp), contentAlignment = Alignment.Center) {
                    InspectionIconView(InspectionIcon.DragIndicator, size = 22.dp, tint = t.muted)
                }
            } else {
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(item.name, style = inspectionTextStyle(16.sp, FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis)
                val resumo = templateItemSummary(item, texts)
                if (resumo.isNotEmpty()) Text(resumo, style = inspectionTextStyle(13.sp, color = t.muted))
            }
            if (item.required) RequiredTag(texts.template.requiredTag)
            if (!readOnly) KitIconButton(InspectionIcon.Edit, texts.template.editItemTitle, onEdit, size = 22.dp)
        }
        HorizontalDivider(thickness = 1.dp, color = t.line)
    }
}

/** Etiqueta "Obrigatório": 24 dp, raio 4, `brandSoft`/`brand` 12/700. */
@Composable
fun RequiredTag(text: String, modifier: Modifier = Modifier) {
    val t = InspectionTheme.tokens
    Box(
        modifier.height(24.dp).clip(RoundedCornerShape(4.dp)).background(t.brandSoft).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = inspectionTextStyle(12.sp, FontWeight.Bold, t.brand))
    }
}

/**
 * **Diálogo "Editar item"** (centralizado, D-j): nome (campo 50, 17 sp), Obrigatório (interruptor),
 * Mínimo de fotos ([AppStepper]) e Estado padrão (seletor), e os chips de **Perguntas do item**
 * (Funciona? · Material · Cor · Marca · Quantidade · as próprias · "+ Pergunta própria"). A pergunta
 * própria abre o SEGUNDO PASSO no mesmo diálogo (nome, tipo, obrigatória, opções) — D14.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditItemDialog(draft: EditItemDraft, texts: InspectionTexts, onEvent: (TemplateEditorEvent) -> Unit) {
    val tt = texts.template
    val pergunta = draft.composingQuestion
    if (pergunta != null) {
        CustomQuestionStep(pergunta, draft, texts, onEvent)
        return
    }
    InspectionDialog(
        title = if (draft.itemId == null) tt.newItemTitle else tt.editItemTitle,
        confirmText = texts.common.save,
        dismissText = texts.common.cancel,
        onConfirm = { onEvent(TemplateEditorEvent.SaveItem) },
        onDismiss = { onEvent(TemplateEditorEvent.CancelItem) },
        confirmTestTag = InspectionTestTags.EDIT_ITEM_SAVE,
        maxWidth = 560.dp,
    ) {
        Column(
            Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InspectionTextField(
                value = draft.name,
                onValueChange = { onEvent(TemplateEditorEvent.DraftChanged(draft.copy(name = it, nameError = null))) },
                label = tt.itemNameLabel,
                errorMessage = draft.nameError,
                height = 50.dp,
                textSize = 17.sp,
                testTag = InspectionTestTags.EDIT_ITEM_NAME,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .toggleable(draft.required, role = Role.Switch) {
                        onEvent(TemplateEditorEvent.DraftChanged(draft.copy(required = it)))
                    }
                    .testTag(InspectionTestTags.EDIT_ITEM_REQUIRED),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tt.requiredTag, style = inspectionTextStyle(16.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Switch(checked = draft.required, onCheckedChange = null)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AppStepper(
                    value = draft.minPhotos,
                    onValueChange = { onEvent(TemplateEditorEvent.DraftChanged(draft.copy(minPhotos = it))) },
                    min = 0,
                    max = 20,
                    label = tt.minPhotosLabel,
                    modifier = Modifier.weight(1f),
                )
                ConditionPicker(
                    selected = draft.defaultCondition,
                    onSelect = { onEvent(TemplateEditorEvent.DraftChanged(draft.copy(defaultCondition = it))) },
                    label = tt.defaultConditionLabel,
                    texts = texts,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(tt.itemQuestions, style = inspectionTextStyle(14.sp, FontWeight.SemiBold), modifier = Modifier.semantics { heading() })
            FlowRow(
                Modifier.testTag(InspectionTestTags.EDIT_ITEM_QUESTIONS),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                QuestionChip(texts.item.functioningLabel, draft.asksFunctioning) {
                    onEvent(TemplateEditorEvent.DraftChanged(draft.copy(asksFunctioning = !draft.asksFunctioning)))
                }
                TemplateStandardQuestions.ALL.forEach { chave ->
                    val ligada = chave in draft.standardQuestions
                    QuestionChip(TemplateStandardQuestions.label(chave, tt), ligada) {
                        val novas = if (ligada) draft.standardQuestions - chave else draft.standardQuestions + chave
                        onEvent(TemplateEditorEvent.DraftChanged(draft.copy(standardQuestions = novas)))
                    }
                }
                draft.customQuestions.forEach { q ->
                    QuestionChip(q.label, q.enabled) {
                        val atualizadas = draft.customQuestions.map { if (it.id == q.id) it.copy(enabled = !it.enabled) else it }
                        onEvent(TemplateEditorEvent.DraftChanged(draft.copy(customQuestions = atualizadas)))
                    }
                }
                AddQuestionChip(tt.ownQuestion) { onEvent(TemplateEditorEvent.StartCustomQuestion) }
            }
        }
    }
}

/** Chip de pergunta: 36 dp, raio 18, 15 sp; ligado = borda 1,5 marca + `brandSoft` + ✓ + texto marca 700. */
@Composable
private fun QuestionChip(label: String, on: Boolean, onToggle: () -> Unit) {
    val t = InspectionTheme.tokens
    val forma = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .height(36.dp)
            .clip(forma)
            .background(if (on) t.brandSoft else t.surface)
            .border(1.5.dp, if (on) t.brand else t.line, forma)
            .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (on) InspectionIconView(InspectionIcon.Check, size = 17.dp, tint = t.brand)
        Text(label, style = inspectionTextStyle(15.sp, if (on) FontWeight.Bold else FontWeight.Normal, if (on) t.brand else t.ink))
    }
}

@Composable
private fun AddQuestionChip(label: String, onClick: () -> Unit) {
    val t = InspectionTheme.tokens
    val forma = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .height(36.dp)
            .clip(forma)
            .border(1.5.dp, t.line, forma)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        InspectionIconView(InspectionIcon.Add, size = 17.dp, tint = t.brand)
        Text(label, style = inspectionTextStyle(15.sp, FontWeight.SemiBold, t.brand))
    }
}

/** Seletor "Bom ⌄" do Estado padrão (campo 48 do kit + menu). */
@Composable
private fun ConditionPicker(
    selected: ItemCondition,
    onSelect: (ItemCondition) -> Unit,
    label: String,
    texts: InspectionTexts,
    modifier: Modifier = Modifier,
) {
    val t = InspectionTheme.tokens
    var aberto by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = inspectionTextStyle(14.sp, FontWeight.SemiBold))
        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(t.surface)
                    .border(1.dp, t.line, RoundedCornerShape(6.dp))
                    .clickable(role = Role.DropdownList) { aberto = true }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InspectionIconView(selected.icon, filled = true, size = 18.dp, tint = t.tone(selected.tone).fg)
                Spacer(Modifier.width(8.dp))
                Text(texts.values.label(selected), style = inspectionTextStyle(15.sp), modifier = Modifier.weight(1f))
                InspectionIconView(InspectionIcon.ExpandMore, size = 20.dp, tint = t.muted)
            }
            AppDropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }, containerColor = t.surface) {
                ItemCondition.entries.forEach { c ->
                    MenuRow(texts.values.label(c), c.icon, t.ink, "vistoria-editor-estado-${c.key}") {
                        aberto = false
                        onSelect(c)
                    }
                }
            }
        }
    }
}

/** O segundo passo: nome, tipo (Texto · Escolha · Número · Sim/Não · Data), obrigatória e opções. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomQuestionStep(
    q: CustomQuestionDraft,
    draft: EditItemDraft,
    texts: InspectionTexts,
    onEvent: (TemplateEditorEvent) -> Unit,
) {
    val tt = texts.template
    fun muda(nova: CustomQuestionDraft) = onEvent(TemplateEditorEvent.DraftChanged(draft.copy(composingQuestion = nova)))
    InspectionDialog(
        title = tt.ownQuestion,
        confirmText = tt.addQuestion,
        dismissText = texts.common.cancel,
        onConfirm = { onEvent(TemplateEditorEvent.AddCustomQuestion) },
        onDismiss = { onEvent(TemplateEditorEvent.CancelCustomQuestion) },
        maxWidth = 560.dp,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InspectionTextField(
                value = q.label,
                onValueChange = { muda(q.copy(label = it, labelError = null)) },
                label = tt.questionNameLabel,
                errorMessage = q.labelError,
            )
            Text(tt.questionTypeLabel, style = inspectionTextStyle(14.sp, FontWeight.SemiBold))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TemplateQuestionType.entries.forEach { tipo ->
                    QuestionChip(questionTypeLabel(tipo, tt), q.type == tipo) { muda(q.copy(type = tipo)) }
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(q.required, role = Role.Switch) { muda(q.copy(required = it)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tt.questionRequired, style = inspectionTextStyle(16.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
                Switch(checked = q.required, onCheckedChange = null)
            }
            if (q.type == TemplateQuestionType.CHOICE) {
                InspectionNoteField(
                    value = q.optionsText,
                    onValueChange = { muda(q.copy(optionsText = it, optionsError = null)) },
                    label = tt.choiceOptionsLabel,
                    minHeight = 96.dp,
                )
                q.optionsError?.let { Text(it, style = inspectionTextStyle(13.sp, color = InspectionTheme.tokens.bad.fg)) }
            }
        }
    }
}

fun questionTypeLabel(type: TemplateQuestionType, tt: InspectionTemplateTexts): String = when (type) {
    TemplateQuestionType.TEXT -> tt.typeText
    TemplateQuestionType.CHOICE -> tt.typeChoice
    TemplateQuestionType.NUMBER -> tt.typeNumber
    TemplateQuestionType.YES_NO -> tt.typeYesNo
    TemplateQuestionType.DATE -> tt.typeDate
}

/**
 * Confere o rascunho do item antes de salvar: nome obrigatório. Devolve o rascunho com o erro no
 * CAMPO (ou `null` em [EditItemDraft.nameError] se está tudo certo). Pura.
 */
fun validateItemDraft(draft: EditItemDraft, texts: InspectionTemplateTexts): EditItemDraft =
    draft.copy(nameError = if (draft.name.isBlank()) texts.nameRequired else null)

/**
 * Confere a pergunta própria: nome obrigatório; Escolha exige ao menos DUAS opções distintas. Erro no
 * campo. Pura.
 */
fun validateCustomQuestion(q: CustomQuestionDraft, texts: InspectionTemplateTexts): CustomQuestionDraft = q.copy(
    labelError = if (q.label.isBlank()) texts.nameRequired else null,
    optionsError = if (q.type == TemplateQuestionType.CHOICE && q.options.size < 2) texts.choiceOptionsRequired else null,
)

/** `true` quando a pergunta própria pode entrar. */
fun CustomQuestionDraft.isValid(): Boolean = labelError == null && optionsError == null

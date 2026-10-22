package br.com.codecacto.kmplib.inspection.ui.item

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.inspection.InspectionTexts
import br.com.codecacto.kmplib.inspection.fmt
import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIconView
import br.com.codecacto.kmplib.inspection.model.ItemSheetEvent
import br.com.codecacto.kmplib.inspection.model.ItemSheetState
import br.com.codecacto.kmplib.inspection.plural
import br.com.codecacto.kmplib.inspection.rememberInspectionTexts
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.ui.InspectionConditionTiles
import br.com.codecacto.kmplib.inspection.ui.InspectionDialog
import br.com.codecacto.kmplib.inspection.ui.InspectionFooter
import br.com.codecacto.kmplib.inspection.ui.InspectionFunctioningTiles
import br.com.codecacto.kmplib.inspection.ui.InspectionNoteField
import br.com.codecacto.kmplib.inspection.ui.InspectionPrimaryButton
import br.com.codecacto.kmplib.inspection.ui.InspectionSectionLabel
import br.com.codecacto.kmplib.inspection.ui.InspectionTestTags
import br.com.codecacto.kmplib.inspection.ui.InspectionTopBar
import br.com.codecacto.kmplib.inspection.ui.inspectionTextStyle
import br.com.codecacto.kmplib.inspection.ui.items.InlineErrorBanner
import br.com.codecacto.kmplib.inspection.ui.media.InspectionMediaStrip
import br.com.codecacto.kmplib.ui.theme.LocalWindowSizeClass
import br.com.codecacto.kmplib.ui.theme.WindowSizeClass

/**
 * **Folha do item na entrada** (§2.7, LK-M09) — TELA CHEIA (D3), não bottom sheet; em janela
 * EXPANDIDA vira um cartão largo centralizado (D4). Barra 64 com "X", título 20/600 e
 * "Banheiro · item 4 de 14". Corpo, nesta ordem: Estado (5 tiles) · Funciona? (se o item pergunta) ·
 * campos do item ([fields] — o `FormRunner` do `field_schema`, ou campos do tema) · Observação · os
 * três botões de mídia (Foto · Vídeo · Voz) · a faixa de mídia + "Anexar da galeria". Rodapé
 * "Salvar" 52.
 *
 * **Tarefa guiada** (D5): sem Estado/Funciona?; "Salvar" desligado até o mínimo de fotos da câmera,
 * com a frase do que falta. **Prévia** do editor de modelo = [ItemSheetState.readOnly] (tudo
 * desenhado, nada editável — D16). Fechar com mudança não salva → o produto liga
 * [ItemSheetState.confirmDiscard] e o kit pergunta "Descartar alterações?".
 *
 * @param fields os campos do item (`field_schema`), em grade de 2 colunas pelo próprio conteúdo.
 *   O kit não renderiza o FormSchema por conta própria: quem tem o schema é o produto, e o
 *   `FormRunner` da lib (`ui/form`) é o renderizador oficial dele.
 */
@Composable
fun InspectionItemSheet(
    state: ItemSheetState,
    onEvent: (ItemSheetEvent) -> Unit,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
    fields: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val t = InspectionTheme.tokens
    val expandida = LocalWindowSizeClass.current == WindowSizeClass.EXPANDIDA
    if (expandida) {
        Box(modifier.fillMaxSize().background(t.scrim), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.92f)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                ItemSheetContent(state, onEvent, texts, fields)
            }
        }
    } else {
        Box(modifier.fillMaxSize()) { ItemSheetContent(state, onEvent, texts, fields) }
    }
    if (state.confirmDiscard) {
        InspectionDialog(
            title = texts.item.discardTitle,
            message = texts.item.discardMessage,
            confirmText = texts.item.discardConfirm,
            dismissText = texts.item.keepEditing,
            destructive = true,
            onConfirm = { onEvent(ItemSheetEvent.ConfirmDiscard) },
            onDismiss = { onEvent(ItemSheetEvent.DismissDiscard) },
        )
    }
}

@Composable
private fun ItemSheetContent(
    state: ItemSheetState,
    onEvent: (ItemSheetEvent) -> Unit,
    texts: InspectionTexts,
    fields: (@Composable ColumnScope.() -> Unit)?,
) {
    val t = InspectionTheme.tokens
    val editavel = !state.readOnly
    Column(Modifier.fillMaxSize().background(t.bg)) {
        InspectionTopBar(
            title = state.title,
            subtitle = texts.item.itemPosition.fmt(state.roomName, state.position, state.total),
            navigationIcon = InspectionIcon.Close,
            navigationDescription = texts.common.close,
            onNavigate = { onEvent(ItemSheetEvent.Close) },
            titleSize = 20.sp,
            showDivider = true,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!state.isGuidedTask) {
                InspectionConditionTiles(
                    selected = state.condition,
                    onSelect = { onEvent(ItemSheetEvent.ConditionChanged(it)) },
                    enabled = editavel,
                    title = texts.item.conditionLabel,
                    texts = texts,
                )
                if (state.asksFunctioning) {
                    InspectionFunctioningTiles(
                        selected = state.functioning,
                        onSelect = { onEvent(ItemSheetEvent.FunctioningChanged(it)) },
                        enabled = editavel,
                        title = texts.item.functioningLabel,
                        texts = texts,
                    )
                }
                if (fields != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = fields)
                }
            }
            InspectionNoteField(
                value = state.note,
                onValueChange = { onEvent(ItemSheetEvent.NoteChanged(it)) },
                label = texts.item.noteLabel,
                enabled = editavel,
                testTag = InspectionTestTags.ITEM_NOTE,
            )
            if (editavel) {
                MediaButtonsRow(
                    texts = texts,
                    showVideoAndVoice = !state.isGuidedTask,
                    onPhoto = { onEvent(ItemSheetEvent.TakePhoto) },
                    onVideo = { onEvent(ItemSheetEvent.RecordVideo) },
                    onVoice = { onEvent(ItemSheetEvent.RecordVoice) },
                )
            }
            InspectionMediaStrip(
                media = state.media,
                onAdd = if (editavel) {
                    { onEvent(ItemSheetEvent.TakePhoto) }
                } else {
                    null
                },
                onOpen = { onEvent(ItemSheetEvent.OpenMedia(it.id)) },
                onRetry = { onEvent(ItemSheetEvent.RetryMedia(it.id)) },
                onRemove = if (editavel) {
                    { onEvent(ItemSheetEvent.RemoveMedia(it.id)) }
                } else {
                    null
                },
                texts = texts,
            )
            if (editavel) {
                Row(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button) { onEvent(ItemSheetEvent.AttachFromGallery) }
                        .testTag(InspectionTestTags.ITEM_GALLERY)
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    InspectionIconView(InspectionIcon.PhotoLibrary, size = 20.dp, tint = t.brand)
                    Text(texts.item.attachFromGallery, style = inspectionTextStyle(15.sp, FontWeight.SemiBold, t.brand))
                }
            }
        }
        if (editavel) {
            InspectionFooter {
                if (state.errorMessage != null) {
                    InlineErrorBanner(state.errorMessage, texts.common.retry, { onEvent(ItemSheetEvent.Save) })
                } else if (state.isGuidedTask && state.missingPhotos > 0) {
                    Text(
                        plural(state.missingPhotos, texts.item.guidedMinimumOne, texts.item.guidedMinimumOther),
                        style = inspectionTextStyle(13.sp, color = t.muted, tabular = true),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                InspectionPrimaryButton(
                    text = texts.common.save,
                    onClick = { onEvent(ItemSheetEvent.Save) },
                    enabled = state.canSave || state.isSaving,
                    loading = state.isSaving,
                    testTag = InspectionTestTags.ITEM_SAVE,
                )
            }
        }
    }
}

/** Os três botões de mídia: grade 3, gap 8, 56 dp, borda `line`, ícone 22 + rótulo 15/600 na marca. */
@Composable
internal fun MediaButtonsRow(
    texts: InspectionTexts,
    showVideoAndVoice: Boolean,
    onPhoto: () -> Unit,
    onVideo: () -> Unit,
    onVoice: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MediaButton(InspectionIcon.PhotoCamera, texts.item.photo, onPhoto, InspectionTestTags.ITEM_PHOTO, Modifier.weight(1f))
        if (showVideoAndVoice) {
            MediaButton(InspectionIcon.Videocam, texts.item.video, onVideo, InspectionTestTags.ITEM_VIDEO, Modifier.weight(1f))
            MediaButton(InspectionIcon.Mic, texts.item.voice, onVoice, InspectionTestTags.ITEM_VOICE, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MediaButton(icon: InspectionIcon, label: String, onClick: () -> Unit, testTag: String, modifier: Modifier) {
    val t = InspectionTheme.tokens
    val forma = RoundedCornerShape(6.dp)
    Row(
        modifier
            .height(56.dp)
            .clip(forma)
            .background(t.surface)
            .border(1.dp, t.line, forma)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InspectionIconView(icon, size = 22.dp, tint = t.brand)
        Text(label, style = inspectionTextStyle(15.sp, FontWeight.SemiBold, t.brand), maxLines = 1)
    }
}

/**
 * A grade de 2 colunas dos campos do item (§2.7 "Material · Cor"), para o produto que não usa o
 * `FormRunner`: cada par de [content] cai lado a lado.
 */
@Composable
fun InspectionFieldGrid(modifier: Modifier = Modifier, content: List<@Composable () -> Unit>) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        content.chunked(2).forEach { par ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                par.forEach { campo -> Box(Modifier.weight(1f)) { campo() } }
                if (par.size == 1) Box(Modifier.weight(1f))
            }
        }
    }
}

/** Rótulo de seção reexportado para os campos do produto ficarem com a mesma tipografia. */
@Composable
fun InspectionFieldLabel(text: String) = InspectionSectionLabel(text)

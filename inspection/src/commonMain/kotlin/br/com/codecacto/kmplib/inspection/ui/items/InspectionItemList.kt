package br.com.codecacto.kmplib.inspection.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.inspection.InspectionTexts
import br.com.codecacto.kmplib.inspection.fmt
import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIconView
import br.com.codecacto.kmplib.inspection.icons.InspectionIcons
import br.com.codecacto.kmplib.inspection.model.InspectionMediaThumb
import br.com.codecacto.kmplib.inspection.model.InspectionPhase
import br.com.codecacto.kmplib.inspection.model.InspectionRules
import br.com.codecacto.kmplib.inspection.model.ItemFilter
import br.com.codecacto.kmplib.inspection.model.ItemListEvent
import br.com.codecacto.kmplib.inspection.model.ItemListState
import br.com.codecacto.kmplib.inspection.model.ItemRowState
import br.com.codecacto.kmplib.inspection.plural
import br.com.codecacto.kmplib.inspection.rememberInspectionTexts
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.ui.InspectionDialog
import br.com.codecacto.kmplib.inspection.ui.InspectionFooter
import br.com.codecacto.kmplib.inspection.ui.InspectionPrimaryButton
import br.com.codecacto.kmplib.inspection.ui.InspectionSkeletonBlock
import br.com.codecacto.kmplib.inspection.ui.InspectionStateChip
import br.com.codecacto.kmplib.inspection.ui.InspectionTaskChip
import br.com.codecacto.kmplib.inspection.ui.InspectionTestTags
import br.com.codecacto.kmplib.inspection.ui.InspectionTopBar
import br.com.codecacto.kmplib.inspection.ui.InspectionTopBarTextAction
import br.com.codecacto.kmplib.inspection.ui.KitIconButton
import br.com.codecacto.kmplib.inspection.ui.inspectionTextStyle
import br.com.codecacto.kmplib.ui.components.EmptyState
import br.com.codecacto.kmplib.ui.components.ErrorState
import br.com.codecacto.kmplib.ui.components.rememberPhotoSourceRequest
import coil3.compose.AsyncImage

/**
 * **Itens do cômodo** (§2.6, LK-M07): barra 64 com o nome do cômodo e o resumo de problema em âmbar,
 * cartão de mídia do cômodo (fotos + vídeo + voz), pílulas de filtro, as linhas e o rodapé
 * "Cômodo pronto" no espaço próprio. Na SAÍDA as pílulas viram "Todos · Só o que mudou · Pendentes" e
 * aparece "Marcar todos como Conforme" (com diálogo e contagem). Tarefas guiadas vêm primeiro.
 */
@Composable
fun InspectionItemList(
    state: ItemListState,
    onEvent: (ItemListEvent) -> Unit,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    val problemas = InspectionRules.problemCount(state.items, state.phase)
    val visiveis = InspectionRules.visibleItems(state.items, state.filter, state.phase)
    val contagens = InspectionRules.filterCounts(state.items, state.phase)
    var confirmarTodos by remember { mutableStateOf(false) }
    val alvosConforme = InspectionRules.markAllConformingTargets(state.items)

    Column(modifier.fillMaxSize().background(t.bg)) {
        InspectionTopBar(
            title = state.roomName,
            subtitle = if (problemas > 0) plural(problemas, texts.items.itemsWithProblemOne, texts.items.itemsWithProblemOther) else null,
            subtitleIsWarning = true,
            onNavigate = { onEvent(ItemListEvent.Back) },
            navigationDescription = texts.common.back,
            actions = {
                KitIconButton(InspectionIcon.MoreVert, texts.common.moreOptions, { onEvent(ItemListEvent.Menu) }, size = 22.dp)
            },
        )
        if (state.phase == InspectionPhase.ENTRY) {
            RoomMediaCard(
                media = state.roomMedia,
                texts = texts,
                enabled = !state.readOnly,
                onAddPhoto = { onEvent(ItemListEvent.AddRoomPhoto) },
                onVideo = { onEvent(ItemListEvent.AddRoomVideo) },
                onVoice = { onEvent(ItemListEvent.AddRoomVoice) },
                onOpen = { onEvent(ItemListEvent.OpenRoomMedia(it.id)) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
            )
        }
        ItemFilterPills(
            filters = InspectionRules.filtersFor(state.phase),
            counts = contagens,
            selected = state.filter,
            onSelect = { onEvent(ItemListEvent.Filter(it)) },
            texts = texts,
        )
        if (state.phase == InspectionPhase.EXIT && !state.readOnly && alvosConforme.isNotEmpty()) {
            InspectionTopBarTextAction(
                text = texts.items.markAllConforming,
                onClick = { confirmarTodos = true },
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp).testTag(InspectionTestTags.ITEMS_MARK_ALL),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> ItemsSkeleton()
                state.errorMessage != null && state.items.isEmpty() ->
                    ErrorState(message = state.errorMessage, onRetry = { onEvent(ItemListEvent.Retry) })
                visiveis.isEmpty() -> EmptyState(icon = InspectionIcons.vector(InspectionIcon.FactCheck), title = texts.items.emptyFilter)
                else -> LazyColumn(
                    Modifier.fillMaxSize().testTag(InspectionTestTags.ITEMS_LIST),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    item(key = "topo") { HorizontalDivider(thickness = 1.dp, color = t.line) }
                    items(visiveis, key = { it.id }) { item ->
                        ItemRow(item, state.phase, texts, onClick = { onEvent(ItemListEvent.OpenItem(item.id)) })
                    }
                }
            }
            if (state.errorMessage != null && state.items.isNotEmpty()) {
                InlineErrorBanner(
                    message = state.errorMessage,
                    actionLabel = texts.common.retry,
                    onAction = { onEvent(ItemListEvent.Retry) },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                )
            }
        }
        if (!state.readOnly && state.phase == InspectionPhase.ENTRY) {
            InspectionFooter {
                InspectionPrimaryButton(
                    text = texts.items.roomDone,
                    onClick = { onEvent(ItemListEvent.RoomReady) },
                    loading = state.isMarkingRoomReady,
                    testTag = InspectionTestTags.ITEMS_ROOM_DONE,
                )
            }
        }
    }
    if (confirmarTodos) {
        InspectionDialog(
            title = texts.items.markAllConformingTitle.fmt(alvosConforme.size),
            message = texts.items.markAllConformingMessage,
            confirmText = texts.items.markAction,
            dismissText = texts.common.cancel,
            onConfirm = {
                confirmarTodos = false
                onEvent(ItemListEvent.MarkAllConforming)
            },
            onDismiss = { confirmarTodos = false },
        )
    }
}

/**
 * **Cartão de mídia do cômodo**: "Fotos do cômodo (3)", até 3 miniaturas 64×48 (+N), e os botões
 * 48×48 de vídeo e voz. A foto do cômodo entra pela própria faixa (tocar nas miniaturas vazias abre a
 * câmera).
 */
@Composable
fun RoomMediaCard(
    media: List<InspectionMediaThumb>,
    onAddPhoto: () -> Unit,
    onVideo: () -> Unit,
    onVoice: () -> Unit,
    onOpen: (InspectionMediaThumb) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    val forma = RoundedCornerShape(6.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(forma)
            .background(t.surface)
            .border(1.dp, t.line, forma)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(texts.items.roomPhotos.fmt(media.size), style = inspectionTextStyle(14.sp, FontWeight.SemiBold))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val mostradas = media.take(3)
            mostradas.forEachIndexed { i, m ->
                val extra = if (i == 2 && media.size > 3) media.size - 3 else 0
                Box(
                    Modifier
                        .size(width = 64.dp, height = 48.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(t.sunken)
                        .clickable(role = Role.Image) { onOpen(m) },
                    contentAlignment = Alignment.Center,
                ) {
                    m.source?.let {
                        AsyncImage(rememberPhotoSourceRequest(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    if (extra > 0) {
                        Box(Modifier.fillMaxSize().background(t.scrim), contentAlignment = Alignment.Center) {
                            Text(texts.item.moreMedia.fmt(extra), style = inspectionTextStyle(14.sp, FontWeight.SemiBold, t.surface))
                        }
                    }
                }
            }
            if (enabled) {
                repeat((3 - mostradas.size).coerceAtLeast(0).coerceAtMost(1)) {
                    Box(
                        Modifier
                            .size(width = 64.dp, height = 48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, t.line, RoundedCornerShape(4.dp))
                            .clickable(role = Role.Button, onClick = onAddPhoto),
                        contentAlignment = Alignment.Center,
                    ) {
                        InspectionIconView(InspectionIcon.PhotoCamera, size = 22.dp, tint = t.brand, contentDescription = texts.item.addPhoto)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (enabled) {
                MediaSquareButton(InspectionIcon.Videocam, texts.items.recordVideo, onVideo, InspectionTestTags.ITEMS_ROOM_VIDEO)
                MediaSquareButton(InspectionIcon.Mic, texts.items.recordVoice, onVoice, InspectionTestTags.ITEMS_ROOM_VOICE)
            }
        }
    }
}

@Composable
private fun MediaSquareButton(icon: InspectionIcon, description: String, onClick: () -> Unit, testTag: String) {
    val t = InspectionTheme.tokens
    Box(
        Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(1.dp, t.line, RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        InspectionIconView(icon, size = 22.dp, tint = t.brand, contentDescription = description)
    }
}

/**
 * **Pílulas de filtro** (≠ `FilterChipRow`: outra forma): 36 dp, raio 18, 14 sp; ativa invertida
 * (fundo `ink`, texto `bg`, 600), inativa com borda `line`. Rolam na horizontal. "Todos · 10".
 */
@Composable
fun ItemFilterPills(
    filters: List<ItemFilter>,
    counts: Map<ItemFilter, Int>,
    selected: ItemFilter,
    onSelect: (ItemFilter) -> Unit,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    LazyRow(
        modifier = modifier.fillMaxWidth().selectableGroup().testTag(InspectionTestTags.ITEMS_FILTER),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(filters, key = { it.name }) { f ->
            val ativo = f == selected
            val rotulo = filterLabel(f, texts).fmt(counts[f] ?: 0)
            val forma = RoundedCornerShape(18.dp)
            Box(
                Modifier
                    .height(36.dp)
                    .clip(forma)
                    .background(if (ativo) t.ink else t.surface)
                    .then(if (ativo) Modifier else Modifier.border(1.dp, t.line, forma))
                    .selectable(selected = ativo, role = Role.Tab, onClick = { onSelect(f) })
                    .testTag("${InspectionTestTags.ITEMS_FILTER}-${f.name.lowercase()}")
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    rotulo,
                    style = inspectionTextStyle(14.sp, if (ativo) FontWeight.SemiBold else FontWeight.Normal, if (ativo) t.bg else t.ink, tabular = true),
                    maxLines = 1,
                )
            }
        }
    }
}

/** O modelo de rótulo de cada filtro (formatar com a contagem). */
fun filterLabel(filter: ItemFilter, texts: InspectionTexts): String = when (filter) {
    ItemFilter.ALL -> texts.items.filterAll
    ItemFilter.WITH_PROBLEM -> texts.items.filterProblem
    ItemFilter.WITHOUT_PHOTO -> texts.items.filterNoPhoto
    ItemFilter.CHANGED -> texts.items.filterChanged
    ItemFilter.PENDING -> texts.items.filterPending
}

/**
 * Uma linha de item: nome 16/500 + meta 13 (ícone 15 + "1 foto" / "Nota de voz" / "Sem foto"; o
 * obrigatório incompleto em âmbar 600 com `warning`) e, à direita, o chip de estado (ou de
 * resultado, na saída). A tarefa guiada tem fundo `brandSoft` e o chip "Tarefa".
 */
@Composable
fun ItemRow(
    item: ItemRowState,
    phase: InspectionPhase,
    texts: InspectionTexts,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = InspectionTheme.tokens
    Column(modifier.fillMaxWidth().background(if (item.isGuidedTask) t.brandSoft else t.surface)) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics(mergeDescendants = true) { }
                .testTag(InspectionTestTags.item(item.id))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.name, style = inspectionTextStyle(16.sp, FontWeight.Medium), maxLines = 2, overflow = TextOverflow.Ellipsis)
                ItemMeta(item, texts)
            }
            Spacer(Modifier.width(12.dp))
            when {
                item.isGuidedTask -> InspectionTaskChip(texts = texts)
                phase == InspectionPhase.EXIT && item.exitResult != null -> InspectionStateChip(item.exitResult, texts = texts)
                item.condition != null -> InspectionStateChip(item.condition, texts = texts)
            }
        }
        HorizontalDivider(thickness = 1.dp, color = t.line)
    }
}

@Composable
private fun ItemMeta(item: ItemRowState, texts: InspectionTexts) {
    val t = InspectionTheme.tokens
    val (icone, texto, aviso) = when {
        item.isGuidedTask -> Triple(
            null,
            plural(item.guidedTaskRequiredPhotos, texts.items.guidedTaskMetaOne, texts.items.guidedTaskMetaOther),
            true,
        )
        item.missingRequiredPhotos > 0 -> Triple(
            InspectionIcon.Warning,
            plural(item.missingRequiredPhotos, texts.items.requiredMissingOne, texts.items.requiredMissingOther),
            true,
        )
        item.photoCount > 0 -> Triple(
            InspectionIcon.PhotoCamera,
            plural(item.photoCount, texts.items.photoCountOne, texts.items.photoCountOther),
            false,
        )
        item.hasVoiceNote -> Triple(InspectionIcon.Mic, texts.items.voiceNote, false)
        else -> Triple(null, texts.items.noPhoto, false)
    }
    val cor = if (aviso) t.warn.fg else t.muted
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (icone != null) InspectionIconView(icone, filled = aviso, size = 15.dp, tint = cor)
        Text(texto, style = inspectionTextStyle(13.sp, if (aviso) FontWeight.SemiBold else FontWeight.Normal, cor))
    }
}

/** Banner de erro colado ao que falhou (gravação local), com a ação. */
@Composable
internal fun InlineErrorBanner(message: String, actionLabel: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val t = InspectionTheme.tokens
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(t.bad.soft)
            .border(1.dp, t.bad.fg, RoundedCornerShape(6.dp))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InspectionIconView(InspectionIcon.Error, filled = true, size = 18.dp, tint = t.bad.fg)
        Text(message, style = inspectionTextStyle(14.sp, color = t.ink), modifier = Modifier.weight(1f).padding(8.dp))
        Box(
            Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onAction).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(actionLabel, style = inspectionTextStyle(14.sp, FontWeight.SemiBold, t.bad.fg))
        }
    }
}

@Composable
private fun ItemsSkeleton() {
    val t = InspectionTheme.tokens
    Column(Modifier.fillMaxWidth()) {
        repeat(6) {
            Row(
                Modifier.fillMaxWidth().height(64.dp).background(t.surface).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InspectionSkeletonBlock(Modifier.width(150.dp).height(14.dp))
                    InspectionSkeletonBlock(Modifier.width(70.dp).height(10.dp))
                }
                InspectionSkeletonBlock(Modifier.width(64.dp).height(26.dp))
            }
            HorizontalDivider(thickness = 1.dp, color = t.line)
        }
    }
}

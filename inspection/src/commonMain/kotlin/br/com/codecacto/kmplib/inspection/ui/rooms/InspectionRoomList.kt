package br.com.codecacto.kmplib.inspection.ui.rooms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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
import br.com.codecacto.kmplib.inspection.model.RoomListEvent
import br.com.codecacto.kmplib.inspection.model.RoomListState
import br.com.codecacto.kmplib.inspection.model.RoomProgress
import br.com.codecacto.kmplib.inspection.plural
import br.com.codecacto.kmplib.inspection.rememberInspectionTexts
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.ui.InspectionDashedAddButton
import br.com.codecacto.kmplib.inspection.ui.InspectionDialog
import br.com.codecacto.kmplib.inspection.ui.InspectionSkeletonBlock
import br.com.codecacto.kmplib.inspection.ui.InspectionTestTags
import br.com.codecacto.kmplib.inspection.ui.InspectionTextField
import br.com.codecacto.kmplib.inspection.ui.InspectionTopBar
import br.com.codecacto.kmplib.inspection.ui.InspectionTopBarTextAction
import br.com.codecacto.kmplib.inspection.ui.KitIconButton
import br.com.codecacto.kmplib.inspection.ui.inspectionTextStyle
import br.com.codecacto.kmplib.ui.components.AppDropdownMenu
import br.com.codecacto.kmplib.ui.components.AppProgressBar
import br.com.codecacto.kmplib.ui.components.EmptyState
import br.com.codecacto.kmplib.ui.components.ErrorState
import br.com.codecacto.kmplib.ui.components.ProgressRing
import br.com.codecacto.kmplib.ui.components.ProgressTone
import br.com.codecacto.kmplib.ui.components.ReorderableList

/**
 * **Lista de cômodos** da vistoria (§2.5, LK-M07): barra "← Cômodos · Revisão", cabeçalho de
 * progresso ("Apto 302 · Entrada" ⟷ "5 de 8 prontos" + barra de 6 dp), lista reordenável por ARRASTO
 * (alça à esquerda, ações de acessibilidade mover/posição) com anel de 34 dp e menu ⋮ (Duplicar ·
 * Renomear · Remover), e o botão tracejado "+ Adicionar cômodo" no fim da rolagem.
 *
 * Título, barra e cabeçalho desenham no 1º quadro; carregando troca SÓ a lista por esqueleto.
 * Remover pede confirmação (dizendo que as fotos saem junto, quando há); renomear abre o diálogo.
 */
@Composable
fun InspectionRoomList(
    state: RoomListState,
    onEvent: (RoomListEvent) -> Unit,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    var renomeando by remember { mutableStateOf<RoomProgress?>(null) }
    var removendo by remember { mutableStateOf<RoomProgress?>(null) }

    Column(modifier.fillMaxSize().background(t.bg)) {
        InspectionTopBar(
            title = texts.rooms.roomsTitle,
            onNavigate = { onEvent(RoomListEvent.Back) },
            navigationDescription = texts.common.back,
            actions = {
                InspectionTopBarTextAction(
                    text = texts.rooms.review,
                    onClick = { onEvent(RoomListEvent.Review) },
                    enabled = !state.isLoading,
                    modifier = Modifier.testTag(InspectionTestTags.ROOMS_REVIEW),
                )
            },
        )
        RoomsProgressHeader(state, texts)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.isLoading -> RoomsSkeleton()
                state.errorMessage != null && state.rooms.isEmpty() ->
                    ErrorState(message = state.errorMessage, onRetry = { onEvent(RoomListEvent.Retry) })
                state.rooms.isEmpty() -> EmptyState(
                    icon = InspectionIcons.vector(InspectionIcon.LibraryAdd),
                    title = texts.rooms.emptyRooms,
                    action = if (state.readOnly) null else {
                        { InspectionDashedAddButton(texts.rooms.addRoom, { onEvent(RoomListEvent.Add) }, Modifier.padding(horizontal = 16.dp)) }
                    },
                )
                else -> ReorderableList(
                    items = state.rooms,
                    key = { it.id },
                    onMove = { from, to -> onEvent(RoomListEvent.Move(from, to)) },
                    onReorderFinished = { onEvent(RoomListEvent.MoveFinished) },
                    modifier = Modifier.fillMaxSize().testTag(InspectionTestTags.ROOMS_LIST),
                    verticalArrangement = Arrangement.Top,
                    contentPadding = PaddingValues(bottom = 24.dp),
                    enabled = !state.readOnly,
                    dragOnLongPress = !state.readOnly,
                    header = { item(key = "topo") { HorizontalDivider(thickness = 1.dp, color = t.line) } },
                    footer = {
                        if (!state.readOnly) {
                            item(key = "adicionar") {
                                InspectionDashedAddButton(
                                    text = texts.rooms.addRoom,
                                    onClick = { onEvent(RoomListEvent.Add) },
                                    modifier = Modifier.padding(16.dp),
                                    testTag = InspectionTestTags.ROOMS_ADD,
                                )
                            }
                        }
                    },
                ) { room, _, isDragging ->
                    RoomRow(
                        room = room,
                        texts = texts,
                        readOnly = state.readOnly,
                        isDragging = isDragging,
                        handleModifier = Modifier.dragHandle(),
                        onOpen = { onEvent(RoomListEvent.Open(room.id)) },
                        onDuplicate = { onEvent(RoomListEvent.Duplicate(room.id)) },
                        onRename = { renomeando = room },
                        onRemove = { removendo = room },
                    )
                }
            }
        }
    }

    renomeando?.let { room ->
        var nome by remember(room.id) { mutableStateOf(room.name) }
        InspectionDialog(
            title = texts.rooms.renameRoomTitle,
            confirmText = texts.common.save,
            dismissText = texts.common.cancel,
            confirmEnabled = nome.isNotBlank(),
            onConfirm = {
                onEvent(RoomListEvent.Rename(room.id, nome.trim()))
                renomeando = null
            },
            onDismiss = { renomeando = null },
        ) {
            InspectionTextField(value = nome, onValueChange = { nome = it }, label = texts.rooms.roomNameLabel)
        }
    }
    removendo?.let { room ->
        InspectionDialog(
            title = texts.rooms.removeRoomTitle,
            message = (if (room.hasMedia) texts.rooms.removeRoomWithMedia else texts.rooms.removeRoomNoMedia).fmt(room.name),
            confirmText = texts.rooms.remove,
            dismissText = texts.common.cancel,
            destructive = true,
            onConfirm = {
                onEvent(RoomListEvent.Remove(room.id))
                removendo = null
            },
            onDismiss = { removendo = null },
        )
    }
}

/** "Apto 302 · Entrada" ⟷ "5 de 8 prontos" + barra de 6 dp (`sunken`/`brand`, raio 3). */
@Composable
private fun RoomsProgressHeader(state: RoomListState, texts: InspectionTexts) {
    val t = InspectionTheme.tokens
    Column(
        Modifier
            .fillMaxWidth()
            .background(t.bg)
            .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)
            .testTag(InspectionTestTags.ROOMS_PROGRESS),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.contextLabel,
                style = inspectionTextStyle(14.sp, color = t.muted),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!state.isLoading) {
                Text(
                    texts.rooms.roomsReady.fmt(state.readyCount, state.rooms.size),
                    style = inspectionTextStyle(14.sp, FontWeight.SemiBold, tabular = true),
                )
            }
        }
        AppProgressBar(
            progress = state.overallFraction,
            height = 6.dp,
            color = t.brand,
            trackColor = t.sunken,
        )
    }
}

/**
 * Uma linha de cômodo: alça (28×44) · anel 34 · nome 16/500 + situação 13 · contador mono "4/10" ·
 * ⋮ 44. O anel começa às 12 h; concluído vira disco cheio na marca com ✓ em `onBrand`.
 */
@Composable
fun RoomRow(
    room: RoomProgress,
    texts: InspectionTexts,
    onOpen: () -> Unit,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    isDragging: Boolean = false,
    handleModifier: Modifier = Modifier,
) {
    val t = InspectionTheme.tokens
    var menu by remember { mutableStateOf(false) }
    val situacao = when {
        room.isComplete -> texts.rooms.roomReady
        !room.isStarted -> texts.rooms.roomNotStarted
        else -> plural(room.remaining, texts.rooms.roomToReviewOne, texts.rooms.roomToReviewOther)
    }
    Column(modifier.fillMaxWidth().background(if (isDragging) t.sunken else t.surface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .clickableRow(onOpen)
                .padding(start = 6.dp, end = 8.dp)
                .testTag(InspectionTestTags.room(room.id)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!readOnly) {
                Box(
                    handleModifier.size(width = 28.dp, height = 44.dp).clearAndSetSemantics { },
                    contentAlignment = Alignment.Center,
                ) {
                    InspectionIconView(InspectionIcon.DragIndicator, size = 22.dp, tint = t.muted)
                }
            } else {
                Spacer(Modifier.width(10.dp))
            }
            Spacer(Modifier.width(8.dp))
            ProgressRing(
                progress = room.fraction,
                size = 34.dp,
                strokeWidth = 4.dp,
                tone = ProgressTone.Primary,
                completeTone = ProgressTone.Primary,
                trackColor = t.line,
                completeFill = true,
                contentDescription = texts.rooms.roomProgressDescription.fmt(room.done, room.total),
            ) {
                if (isComplete) InspectionIconView(InspectionIcon.Check, size = 18.dp, tint = t.onBrand)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    room.name,
                    style = inspectionTextStyle(16.sp, FontWeight.Medium),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    situacao,
                    style = inspectionTextStyle(13.sp, color = if (room.isComplete) t.good.fg else t.muted),
                )
            }
            Text(
                "${room.done}/${room.total}",
                style = inspectionTextStyle(14.sp, color = t.muted, mono = true),
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            if (!readOnly) {
                Box {
                    KitIconButton(
                        icon = InspectionIcon.MoreVert,
                        description = texts.common.moreOptions,
                        onClick = { menu = true },
                        size = 22.dp,
                        testTag = InspectionTestTags.roomMenu(room.id),
                    )
                    RoomMenu(
                        expanded = menu,
                        onDismiss = { menu = false },
                        texts = texts,
                        onDuplicate = { menu = false; onDuplicate() },
                        onRename = { menu = false; onRename() },
                        onRemove = { menu = false; onRemove() },
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = t.line)
    }
}

/** Menu ⋮ do cômodo: largura 190, raio 6, linhas de 44; "Remover" em `bad`. */
@Composable
private fun RoomMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    texts: InspectionTexts,
    onDuplicate: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    val t = InspectionTheme.tokens
    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 190.dp),
        shape = RoundedCornerShape(6.dp),
        containerColor = t.surface,
        shadowElevation = 8.dp,
    ) {
        MenuRow(texts.rooms.duplicate, InspectionIcon.ContentCopy, t.ink, InspectionTestTags.ROOM_MENU_DUPLICATE, onDuplicate)
        MenuRow(texts.rooms.rename, InspectionIcon.Edit, t.ink, InspectionTestTags.ROOM_MENU_RENAME, onRename)
        MenuRow(texts.rooms.remove, InspectionIcon.Delete, t.bad.fg, InspectionTestTags.ROOM_MENU_REMOVE, onRemove)
    }
}

@Composable
internal fun MenuRow(
    label: String,
    icon: InspectionIcon,
    color: androidx.compose.ui.graphics.Color,
    testTag: String,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label, style = inspectionTextStyle(15.sp, color = color)) },
        leadingIcon = { InspectionIconView(icon, size = 20.dp, tint = color) },
        onClick = onClick,
        modifier = Modifier.height(44.dp).testTag(testTag),
        contentPadding = MenuDefaults.DropdownMenuItemContentPadding,
    )
}

/** Esqueleto da lista: 5 linhas com a moldura da linha real (título e barra já estão no ar). */
@Composable
private fun RoomsSkeleton() {
    val t = InspectionTheme.tokens
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = t.line)
        repeat(5) {
            Row(
                Modifier.fillMaxWidth().height(68.dp).background(t.surface).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(34.dp).clip(CircleShape).background(t.sunken))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    InspectionSkeletonBlock(Modifier.width(140.dp).height(14.dp))
                    InspectionSkeletonBlock(Modifier.width(90.dp).height(10.dp))
                }
                InspectionSkeletonBlock(Modifier.width(36.dp).height(12.dp))
            }
            HorizontalDivider(thickness = 1.dp, color = t.line)
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(role = Role.Button, onClick = onClick)

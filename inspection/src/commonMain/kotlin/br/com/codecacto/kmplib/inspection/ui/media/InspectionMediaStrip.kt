package br.com.codecacto.kmplib.inspection.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.inspection.InspectionTexts
import br.com.codecacto.kmplib.inspection.fmt
import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIconView
import br.com.codecacto.kmplib.inspection.label
import br.com.codecacto.kmplib.inspection.model.InspectionMediaKind
import br.com.codecacto.kmplib.inspection.model.InspectionMediaSource
import br.com.codecacto.kmplib.inspection.model.InspectionMediaThumb
import br.com.codecacto.kmplib.inspection.model.MediaUploadStatus
import br.com.codecacto.kmplib.inspection.rememberInspectionTexts
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.theme.InspectionTokens
import br.com.codecacto.kmplib.inspection.ui.InspectionDialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import br.com.codecacto.kmplib.inspection.ui.InspectionTestTags
import br.com.codecacto.kmplib.inspection.ui.dashedBorder
import br.com.codecacto.kmplib.inspection.ui.inspectionTextStyle
import br.com.codecacto.kmplib.ui.components.ProgressRing
import br.com.codecacto.kmplib.ui.components.rememberPhotoSourceRequest
import coil3.compose.AsyncImage

/** Medidas da faixa de mídia (§2.9). */
object InspectionMediaStripDefaults {
    val TileSize: Dp = 76.dp
    val FailedTileWidth: Dp = 110.dp
    val Gap: Dp = 8.dp
}

/**
 * **Faixa de mídia do item** (§2.9, LK-M08): rolagem horizontal, gap 8, o tile de **adicionar
 * SEMPRE PRIMEIRO** (tracejado, "+" na marca). Cada mídia é um [MediaTile] 76×76: confirmada ganha o
 * ✓ verde, anexo da galeria a etiqueta "Anexo", na fila/enviando o selo de envio, e a que falhou vira
 * o tile LARGO vermelho com "Tentar de novo" — a captura nunca some.
 *
 * Só aparece miniatura de mídia já gravada no disco (o produto só a coloca no estado depois disso).
 */
@Composable
fun InspectionMediaStrip(
    media: List<InspectionMediaThumb>,
    onAdd: (() -> Unit)?,
    onOpen: (InspectionMediaThumb) -> Unit,
    onRetry: (InspectionMediaThumb) -> Unit,
    modifier: Modifier = Modifier,
    onRemove: ((InspectionMediaThumb) -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    var removendo by remember { mutableStateOf<InspectionMediaThumb?>(null) }
    LazyRow(
        modifier = modifier.testTag(InspectionTestTags.MEDIA_STRIP),
        horizontalArrangement = Arrangement.spacedBy(InspectionMediaStripDefaults.Gap),
        contentPadding = contentPadding,
    ) {
        if (onAdd != null) {
            item(key = "adicionar") { AddMediaTile(onAdd, texts) }
        }
        items(media, key = { it.id }) { thumb ->
            MediaTile(
                thumb = thumb,
                index = media.indexOf(thumb) + 1,
                onOpen = { onOpen(thumb) },
                onRetry = { onRetry(thumb) },
                onLongClick = if (onRemove != null && thumb.removable) {
                    { removendo = thumb }
                } else {
                    null
                },
                texts = texts,
            )
        }
    }
    removendo?.let { alvo ->
        InspectionDialog(
            title = texts.item.removeMediaTitle,
            confirmText = texts.rooms.remove,
            dismissText = texts.common.cancel,
            destructive = true,
            onConfirm = {
                removendo = null
                onRemove?.invoke(alvo)
            },
            onDismiss = { removendo = null },
        )
    }
}

/** O tile de adicionar: tracejado 1,5 `line`, "+" 28 na marca. */
@Composable
fun AddMediaTile(onClick: () -> Unit, texts: InspectionTexts = rememberInspectionTexts(), size: Dp = InspectionMediaStripDefaults.TileSize) {
    val t = InspectionTheme.tokens
    Box(
        Modifier
            .size(size)
            .dashedBorder(t.line)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = texts.item.addPhoto }
            .testTag(InspectionTestTags.MEDIA_ADD),
        contentAlignment = Alignment.Center,
    ) {
        InspectionIconView(InspectionIcon.Add, size = 28.dp, tint = t.brand)
    }
}

/**
 * Um tile de mídia (76×76, raio 6) — ou 110×76 quando falhou. A descrição para leitor de tela diz o
 * tipo, a posição e o estado ("Foto 2, Confirmada"), e o anexo diz que é anexo.
 */
@Composable
fun MediaTile(
    thumb: InspectionMediaThumb,
    index: Int,
    onOpen: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    size: Dp = InspectionMediaStripDefaults.TileSize,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    val forma = RoundedCornerShape(6.dp)
    val descricao = buildString {
        append(
            (if (thumb.kind == InspectionMediaKind.VIDEO) texts.item.videoDescription else texts.item.mediaDescription)
                .fmt(index, texts.values.label(thumb.status)),
        )
        if (thumb.origin == InspectionMediaSource.GALLERY) append(", ").append(texts.item.attachmentBadge)
    }

    if (thumb.status == MediaUploadStatus.FAILED) {
        Column(
            modifier
                .size(width = InspectionMediaStripDefaults.FailedTileWidth, height = size)
                .clip(forma)
                .background(t.bad.soft)
                .border(1.5.dp, t.bad.fg, forma)
                .clickable(role = Role.Button, onClick = onRetry)
                .clearAndSetSemantics {
                    contentDescription = "$descricao. ${texts.common.retry}"
                    role = Role.Button
                    onClick(texts.common.retry) { onRetry(); true }
                }
                .testTag(InspectionTestTags.mediaRetry(thumb.id))
                .padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                InspectionIconView(InspectionIcon.Error, size = 15.dp, tint = t.bad.fg)
                Text(texts.values.mediaFailed, style = inspectionTextStyle(12.sp, FontWeight.SemiBold, t.bad.fg))
            }
            Text(
                texts.common.retry,
                style = inspectionTextStyle(12.sp, FontWeight.SemiBold, t.bad.fg).copy(textDecoration = TextDecoration.Underline),
                maxLines = 2,
            )
        }
        return
    }

    Box(
        modifier
            .size(size)
            .clip(forma)
            .background(t.sunken)
            .combinedClickable(role = Role.Button, onLongClick = onLongClick, onClick = onOpen)
            .clearAndSetSemantics {
                contentDescription = descricao
                role = Role.Button
                onClick { onOpen(); true }
                if (onLongClick != null) onLongClick(texts.rooms.remove) { onLongClick(); true }
            }
            .testTag(InspectionTestTags.media(thumb.id)),
    ) {
        val fonte = thumb.source
        if (fonte != null && thumb.kind != InspectionMediaKind.VOICE) {
            AsyncImage(
                model = rememberPhotoSourceRequest(fonte),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        when (thumb.kind) {
            InspectionMediaKind.VIDEO -> InspectionIconView(
                InspectionIcon.PlayCircle,
                filled = true,
                size = 28.dp,
                tint = t.surface,
                modifier = Modifier.align(Alignment.Center),
            )
            InspectionMediaKind.VOICE -> InspectionIconView(
                InspectionIcon.Mic,
                size = 26.dp,
                tint = t.brand,
                modifier = Modifier.align(Alignment.Center),
            )
            InspectionMediaKind.PHOTO -> Unit
        }
        val duracao = thumb.durationMillis
        if (duracao != null && thumb.kind != InspectionMediaKind.PHOTO) {
            Text(
                formatMediaDuration(duracao),
                style = inspectionTextStyle(11.sp, color = InspectionTokens.OnPhoto, mono = true),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(InspectionTokens.PhotoStampBackground, RoundedCornerShape(3.dp))
                    .padding(horizontal = 4.dp),
            )
        }
        if (thumb.origin == InspectionMediaSource.GALLERY) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(t.ink)
                    .padding(horizontal = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(texts.item.attachmentBadge, style = inspectionTextStyle(11.sp, FontWeight.SemiBold, t.bg))
            }
        }
        // Vídeo/voz: a duração ocupa o canto de baixo, e o selo sobe para o de cima.
        val temDuracao = thumb.durationMillis != null && thumb.kind != InspectionMediaKind.PHOTO
        StatusBadge(thumb, Modifier.align(if (temDuracao) Alignment.TopEnd else Alignment.BottomEnd).padding(4.dp))
    }
}

/** O selo de estado no canto inferior direito (D8): ✓ verde, relógio cinza, anel de envio na marca. */
@Composable
private fun StatusBadge(thumb: InspectionMediaThumb, modifier: Modifier) {
    val t = InspectionTheme.tokens
    Box(modifier.size(20.dp).clip(CircleShape).background(t.surface), contentAlignment = Alignment.Center) {
        when (thumb.status) {
            MediaUploadStatus.CONFIRMED ->
                InspectionIconView(InspectionIcon.CheckCircle, filled = true, size = 16.dp, tint = t.good.fg)
            MediaUploadStatus.QUEUED ->
                InspectionIconView(InspectionIcon.Schedule, size = 14.dp, tint = t.neutral.fg)
            MediaUploadStatus.UPLOADING -> ProgressRing(
                progress = thumb.uploadProgress,
                size = 16.dp,
                strokeWidth = 2.dp,
                trackColor = t.line,
                completeTone = null,
            ) {}
            MediaUploadStatus.FAILED -> Unit
        }
    }
}

/** "0:42", "12:05", "1:02:03" — duração de vídeo/voz. */
fun formatMediaDuration(millis: Long): String {
    val total = (millis.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val ss = s.toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$ss" else "$m:$ss"
}

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.inspection.InspectionTexts
import br.com.codecacto.kmplib.inspection.fmt
import br.com.codecacto.kmplib.inspection.icons.InspectionIcon
import br.com.codecacto.kmplib.inspection.icons.InspectionIconView
import br.com.codecacto.kmplib.inspection.icons.InspectionIcons
import br.com.codecacto.kmplib.inspection.model.InspectionRules
import br.com.codecacto.kmplib.inspection.model.MediaQueueBannerKind
import br.com.codecacto.kmplib.inspection.model.MediaQueueEntry
import br.com.codecacto.kmplib.inspection.model.MediaQueueEvent
import br.com.codecacto.kmplib.inspection.model.MediaQueueScreenState
import br.com.codecacto.kmplib.inspection.model.MediaQueueSummary
import br.com.codecacto.kmplib.inspection.model.MediaUploadStatus
import br.com.codecacto.kmplib.inspection.plural
import br.com.codecacto.kmplib.inspection.rememberInspectionTexts
import br.com.codecacto.kmplib.inspection.theme.InspectionTheme
import br.com.codecacto.kmplib.inspection.theme.InspectionTone
import br.com.codecacto.kmplib.inspection.ui.InspectionDialog
import br.com.codecacto.kmplib.inspection.ui.InspectionSecondaryButton
import br.com.codecacto.kmplib.inspection.ui.InspectionStateChip
import br.com.codecacto.kmplib.inspection.ui.InspectionTestTags
import br.com.codecacto.kmplib.inspection.ui.InspectionTopBar
import br.com.codecacto.kmplib.inspection.ui.inspectionTextStyle
import br.com.codecacto.kmplib.ui.components.AppProgressBar
import br.com.codecacto.kmplib.ui.components.EmptyState
import br.com.codecacto.kmplib.ui.components.RefreshableBox
import kotlinx.coroutines.delay

/**
 * **Faixa compacta da fila de envio** (§2.16 `MediaQueueBanner`, D8): mín. 48 dp, `padding 8 16`,
 * fundo `surface`, borda inferior. Ícone + frase tabular + barra 6 dp + `chevron_right` (abre a fila).
 * Variantes por [InspectionRules.queueBannerKind]: enviando (marca) · aguardando Wi-Fi (neutro, com
 * "Enviar agora com dados móveis" — confirmado em diálogo) · sem conexão (neutro) · falhas (`bad`,
 * "Tentar de novo") · concluída (verde por 2 s, depois some).
 */
@Composable
fun MediaQueueBanner(
    summary: MediaQueueSummary,
    onEvent: (MediaQueueEvent) -> Unit,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    val tipo = InspectionRules.queueBannerKind(summary)
    var concluidaVisivel by remember { mutableStateOf(false) }
    LaunchedEffect(tipo) {
        if (tipo == MediaQueueBannerKind.DONE) {
            concluidaVisivel = true
            delay(DONE_VISIBLE_MILLIS)
            concluidaVisivel = false
        }
    }
    if (tipo == MediaQueueBannerKind.HIDDEN || (tipo == MediaQueueBannerKind.DONE && !concluidaVisivel)) return
    var confirmarDados by remember { mutableStateOf(false) }

    val (tom, icone, frase) = when (tipo) {
        MediaQueueBannerKind.UPLOADING ->
            Triple(InspectionTone.BRAND, InspectionIcon.Upload, texts.queue.queueProgress.fmt(summary.confirmed, summary.total))
        MediaQueueBannerKind.WAITING_WIFI -> Triple(
            InspectionTone.NEUTRAL,
            InspectionIcon.Schedule,
            plural(summary.waitingWifi, texts.queue.queueWaitingWifiOne, texts.queue.queueWaitingWifiOther),
        )
        MediaQueueBannerKind.OFFLINE -> Triple(InspectionTone.NEUTRAL, InspectionIcon.WifiOff, texts.queue.queueOffline)
        MediaQueueBannerKind.FAILED -> Triple(InspectionTone.BAD, InspectionIcon.Error, texts.queue.queueFailed.fmt(summary.failed))
        MediaQueueBannerKind.DONE -> Triple(InspectionTone.GOOD, InspectionIcon.CloudDone, texts.queue.queueDone)
        MediaQueueBannerKind.HIDDEN -> return
    }
    val cor = t.tone(tom).fg
    Column(modifier.fillMaxWidth().background(t.surface).testTag(InspectionTestTags.QUEUE_BANNER)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { onEvent(MediaQueueEvent.Open) }
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            InspectionIconView(icone, filled = tipo != MediaQueueBannerKind.UPLOADING, size = 20.dp, tint = cor)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(frase, style = inspectionTextStyle(14.sp, tabular = true))
                if (tipo == MediaQueueBannerKind.UPLOADING || tipo == MediaQueueBannerKind.FAILED) {
                    AppProgressBar(
                        progress = InspectionRules.queueFraction(summary),
                        height = 6.dp,
                        color = t.brand,
                        trackColor = t.sunken,
                    )
                }
            }
            when (tipo) {
                MediaQueueBannerKind.FAILED -> BannerTextAction(texts.common.retry) { onEvent(MediaQueueEvent.RetryAll) }
                MediaQueueBannerKind.WAITING_WIFI -> BannerTextAction(texts.queue.send) { confirmarDados = true }
                else -> Unit
            }
            InspectionIconView(InspectionIcon.ChevronRight, size = 22.dp, tint = t.muted, contentDescription = texts.queue.openQueue)
        }
        HorizontalDivider(thickness = 1.dp, color = t.line)
    }
    if (confirmarDados) {
        InspectionDialog(
            title = texts.queue.mobileDataTitle,
            message = texts.queue.mobileDataMessage,
            confirmText = texts.queue.send,
            dismissText = texts.common.cancel,
            onConfirm = {
                confirmarDados = false
                onEvent(MediaQueueEvent.SendWithMobileData)
            },
            onDismiss = { confirmarDados = false },
        )
    }
}

private const val DONE_VISIBLE_MILLIS = 2_000L

@Composable
private fun BannerTextAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = inspectionTextStyle(14.sp, FontWeight.SemiBold, InspectionTheme.tokens.brand))
    }
}

/**
 * **Variante bloco** (tela "Gerando o laudo…"): barra de 8 dp raio 4 + "Faltam N mídias · X de Y
 * enviadas", centralizada.
 */
@Composable
fun MediaUploadProgressBlock(
    uploaded: Int,
    total: Int,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    val restantes = (total - uploaded).coerceAtLeast(0)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppProgressBar(
            progress = if (total <= 0) 0f else uploaded.toFloat() / total,
            height = 8.dp,
            color = t.brand,
            trackColor = t.sunken,
        )
        Text(
            texts.report.pendingMedia.fmt(restantes, uploaded, total),
            style = inspectionTextStyle(14.sp, color = t.muted, tabular = true),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/**
 * **Fila completa** (§2.16 `MediaQueueScreen`, T16): faixa de progresso no topo, a seção "N com
 * falha" aberta (miniatura · "Quarto 1 · Foto 4" · chip Falhou · "Tentar de novo"), as pendentes, as
 * "Confirmadas (207)" recolhidas, o cartão de espaço e puxar-para-atualizar. Vazia: "Nada na fila".
 */
@Composable
fun MediaQueueScreen(
    state: MediaQueueScreenState,
    onEvent: (MediaQueueEvent) -> Unit,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    val t = InspectionTheme.tokens
    var confirmadasAbertas by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().background(t.bg)) {
        InspectionTopBar(
            title = texts.queue.queueTitle,
            onNavigate = { onEvent(MediaQueueEvent.Back) },
            navigationDescription = texts.common.back,
        )
        MediaQueueBanner(state.summary, onEvent, texts = texts)
        RefreshableBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onEvent(MediaQueueEvent.Refresh) },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            val vazia = state.failed.isEmpty() && state.pending.isEmpty() && state.confirmed.isEmpty()
            if (vazia) {
                EmptyState(icon = InspectionIcons.vector(InspectionIcon.CloudDone), title = texts.queue.queueEmpty)
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                    if (state.failed.isNotEmpty()) {
                        item(key = "h-falhas") { QueueSectionHeader(texts.queue.queueFailed.fmt(state.failed.size)) }
                        items(state.failed, key = { "f-" + it.id }) { entry ->
                            QueueRow(entry, texts, onRetry = { onEvent(MediaQueueEvent.Retry(entry.id)) })
                        }
                    }
                    if (state.pending.isNotEmpty()) {
                        items(state.pending, key = { "p-" + it.id }) { entry -> QueueRow(entry, texts, onRetry = null) }
                    }
                    if (state.confirmed.isNotEmpty()) {
                        item(key = "h-confirmadas") {
                            QueueSectionHeader(
                                texts.queue.confirmedSection.fmt(state.confirmed.size),
                                expandable = true,
                                expanded = confirmadasAbertas,
                                onToggle = { confirmadasAbertas = !confirmadasAbertas },
                            )
                        }
                        if (confirmadasAbertas) {
                            items(state.confirmed, key = { "c-" + it.id }) { entry -> QueueRow(entry, texts, onRetry = null) }
                        }
                    }
                    item(key = "espaco") {
                        StorageReclaimCard(
                            reclaimableBytes = state.reclaimableBytes,
                            onReclaim = { onEvent(MediaQueueEvent.ReclaimStorage) },
                            modifier = Modifier.padding(16.dp),
                            texts = texts,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSectionHeader(
    title: String,
    expandable: Boolean = false,
    expanded: Boolean = true,
    onToggle: () -> Unit = {},
) {
    val t = InspectionTheme.tokens
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(if (expandable) Modifier.clickable(role = Role.Button, onClick = onToggle) else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = inspectionTextStyle(14.sp, FontWeight.SemiBold), modifier = Modifier.weight(1f))
        if (expandable) {
            InspectionIconView(
                if (expanded) InspectionIcon.ExpandMore else InspectionIcon.ChevronRight,
                size = 22.dp,
                tint = t.muted,
            )
        }
    }
}

@Composable
private fun QueueRow(entry: MediaQueueEntry, texts: InspectionTexts, onRetry: (() -> Unit)?) {
    val t = InspectionTheme.tokens
    Column(Modifier.fillMaxWidth().background(t.surface)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)).background(t.sunken)) {
                val fonte = entry.thumb.source
                if (fonte != null) {
                    coil3.compose.AsyncImage(
                        model = br.com.codecacto.kmplib.ui.components.rememberPhotoSourceRequest(fonte),
                        contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.label, style = inspectionTextStyle(15.sp))
                InspectionStateChip(entry.thumb.status, texts = texts)
            }
            if (onRetry != null && entry.thumb.status == MediaUploadStatus.FAILED) {
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(role = Role.Button, onClick = onRetry)
                        .testTag(InspectionTestTags.queueRetry(entry.id))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(texts.common.retry, style = inspectionTextStyle(14.sp, FontWeight.SemiBold, t.brand))
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = t.line)
    }
}

/**
 * **Cartão "Liberar espaço"** (`StorageReclaimCard`): quanto do que o servidor JÁ confirmou pode sair
 * do aparelho, e o botão. Nada sai antes da confirmação do hash — a regra é do produto/fila; o cartão
 * só oferece. `null` = ainda calculando (o cartão não aparece).
 */
@Composable
fun StorageReclaimCard(
    reclaimableBytes: Long?,
    onReclaim: () -> Unit,
    modifier: Modifier = Modifier,
    texts: InspectionTexts = rememberInspectionTexts(),
) {
    if (reclaimableBytes == null) return
    val t = InspectionTheme.tokens
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(t.surface)
            .border(1.dp, t.line, RoundedCornerShape(6.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(texts.queue.storageTitle, style = inspectionTextStyle(15.sp, FontWeight.SemiBold))
        if (reclaimableBytes > 0) {
            Text(
                texts.queue.storageReclaimable.fmt(formatBytes(reclaimableBytes)),
                style = inspectionTextStyle(14.sp, color = t.muted, tabular = true),
            )
            InspectionSecondaryButton(
                text = texts.camera.freeSpace,
                onClick = onReclaim,
                contentColor = t.brand,
                height = 44.dp,
                textSize = 15.sp,
            )
        } else {
            Text(texts.queue.storageNothing, style = inspectionTextStyle(14.sp, color = t.muted))
        }
    }
}

/** "12 MB", "1,4 GB" — vírgula decimal, uma casa acima de 1 GB. */
fun formatBytes(bytes: Long): String {
    val kb = 1024.0
    val mb = kb * 1024
    val gb = mb * 1024
    return when {
        bytes >= gb -> {
            val dez = kotlin.math.round(bytes / gb * 10).toLong()
            "${dez / 10},${dez % 10} GB"
        }
        bytes >= mb -> "${kotlin.math.round(bytes / mb).toLong()} MB"
        bytes >= kb -> "${kotlin.math.round(bytes / kb).toLong()} KB"
        else -> "$bytes B"
    }
}

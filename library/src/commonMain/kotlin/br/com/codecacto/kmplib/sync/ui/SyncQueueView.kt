package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.sync.SyncItemStatus
import br.com.codecacto.kmplib.sync.SyncQueueItem
import br.com.codecacto.kmplib.sync.SyncState
import br.com.codecacto.kmplib.ui.theme.AppColors

/**
 * Visão da fila de sincronização offline-first — par do
 * [UploadQueueView] para o módulo `sync/`.
 *
 * Renderiza uma linha por item da outbox com [StatusBadge] (pendente/sincronizando/
 * conflito/erro) e ações por item ([onRetry]/[onDiscard]) + ação global [onRetryAll].
 * Cores 100% via tokens do tema (sem hardcode). Stateless — o app passa o estado e
 * coleta `engine.state`/`engine.queue`.
 *
 * ```kotlin
 * val state by engine.state.collectAsState()
 * val items by engine.queue.collectAsState()
 * SyncQueueView(state, items,
 *     onRetry = { scope.launch { (engine as DefaultSyncEngine).retry(it.entity, it.localId) } },
 *     onRetryAll = { scope.launch { (engine as DefaultSyncEngine).retryAll() } },
 *     onDiscard = { scope.launch { (engine as DefaultSyncEngine).discard(it.entity, it.localId) } })
 * ```
 */
@Composable
fun SyncQueueView(
    state: SyncState,
    items: List<SyncQueueItem>,
    modifier: Modifier = Modifier,
    onRetry: ((SyncQueueItem) -> Unit)? = null,
    onRetryAll: (() -> Unit)? = null,
    onDiscard: ((SyncQueueItem) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val hasError = items.any { it.status == SyncItemStatus.Error || it.status == SyncItemStatus.Conflict }
        if (hasError && onRetryAll != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onRetryAll) { Text("Tentar tudo de novo") }
            }
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items, key = { "${it.entity}:${it.localId}" }) { item ->
                SyncQueueItemRow(item = item, onRetry = onRetry, onDiscard = onDiscard)
            }
        }
    }
}

/** Linha de um item da fila de sync. */
@Composable
fun SyncQueueItemRow(
    item: SyncQueueItem,
    modifier: Modifier = Modifier,
    onRetry: ((SyncQueueItem) -> Unit)? = null,
    onDiscard: ((SyncQueueItem) -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            item.error?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        SyncStatusBadge(item.status)

        when (item.status) {
            SyncItemStatus.Syncing -> {
                CircularProgressIndicator(
                    modifier = Modifier.padding(start = 4.dp),
                    strokeWidth = 2.dp,
                )
            }
            SyncItemStatus.Error, SyncItemStatus.Conflict -> {
                if (onRetry != null) {
                    IconButton(onClick = { onRetry(item) }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Tentar novamente",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                if (onDiscard != null) {
                    IconButton(onClick = { onDiscard(item) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Descartar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            SyncItemStatus.Pending -> {
                if (onDiscard != null) {
                    IconButton(onClick = { onDiscard(item) }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Descartar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** [StatusBadge] tematizado para um [SyncItemStatus]. */
@Composable
fun SyncStatusBadge(status: SyncItemStatus) {
    val colors = AppColors.current
    val (label, bg, fg) = when (status) {
        SyncItemStatus.Pending -> Triple("Pendente", colors.info.copy(alpha = 0.15f), colors.info)
        SyncItemStatus.Syncing -> Triple("Sincronizando", colors.warning.copy(alpha = 0.15f), colors.warning)
        SyncItemStatus.Conflict -> Triple("Conflito", colors.warning.copy(alpha = 0.18f), colors.warning)
        SyncItemStatus.Error -> Triple("Erro", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error)
    }
    StatusBadge(text = label, textColor = fg, backgroundColor = bg)
}

package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.firebase.storage.UploadItem
import br.com.codecacto.kmplib.firebase.storage.UploadStatus
import br.com.codecacto.kmplib.ui.theme.AppColors

/**
 * Linha de progresso de **um** upload da fila ([br.com.codecacto.kmplib.firebase.storage.UploadQueue]).
 *
 * Renderiza nome do arquivo + estado:
 *  - PENDING/UPLOADING: barra de progresso ([AppProgressBar]) + "%".
 *  - COMPLETED: check verde.
 *  - FAILED: mensagem de erro + botão de retry (dispara [onRetry]).
 *
 * Cores 100% via tema (sem hardcode). Stateless — o app passa o [item] e o callback de retry.
 */
@Composable
fun UploadProgressItem(
    item: UploadItem,
    modifier: Modifier = Modifier,
    onRetry: ((String) -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.fileName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface,
            )
            when (item.status) {
                UploadStatus.PENDING, UploadStatus.UPLOADING -> {
                    AppProgressBar(
                        progress = item.fraction,
                        tone = ProgressTone.Primary,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
                UploadStatus.COMPLETED -> {
                    Text(
                        text = "Enviado",
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.current.success,
                    )
                }
                UploadStatus.FAILED -> {
                    Text(
                        text = item.errorMessage ?: "Falha no envio",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        when (item.status) {
            UploadStatus.UPLOADING, UploadStatus.PENDING -> {
                Text(
                    text = "${item.percent}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            UploadStatus.COMPLETED -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Enviado",
                    tint = AppColors.current.success,
                )
            }
            UploadStatus.FAILED -> {
                if (onRetry != null) {
                    IconButton(onClick = { onRetry(item.id) }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Tentar novamente",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Lista vertical de progresso de toda a fila de upload. Atalho sobre [UploadProgressItem].
 *
 * Uso: `val items by queue.items.collectAsState()` → `UploadQueueView(items, onRetry = queue::retry)`
 * (o retry deve ser disparado numa coroutine pelo app/ViewModel).
 */
@Composable
fun UploadQueueView(
    items: List<UploadItem>,
    modifier: Modifier = Modifier,
    onRetry: ((String) -> Unit)? = null,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(items, key = { it.id }) { item ->
            UploadProgressItem(item = item, onRetry = onRetry)
        }
    }
}

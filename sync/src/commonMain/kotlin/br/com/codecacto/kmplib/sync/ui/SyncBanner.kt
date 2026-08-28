package br.com.codecacto.kmplib.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.sync.SyncState

/**
 * Banner de estado de sincronização (par do [OfflineBanner], dedicado ao módulo `sync/`).
 *
 * Mostra:
 *  - [SyncState.Syncing]: "Sincronizando N itens…" + spinner (cor de aviso/primária).
 *  - [SyncState.Offline]: "Sem conexão — alterações serão enviadas depois" (cor de erro).
 *  - [SyncState.Error]: a mensagem de erro (cor de erro).
 *  - [SyncState.Idle]: nada (oculto).
 *
 * Cores 100% via tema. Stateless — colete `engine.state` e passe aqui.
 */
@Composable
fun SyncBanner(
    state: SyncState,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = state !is SyncState.Idle,
        enter = expandVertically(),
        exit = shrinkVertically(),
    ) {
        when (state) {
            is SyncState.Syncing -> SyncBannerSurface(
                modifier = modifier,
                background = MaterialTheme.colorScheme.secondaryContainer,
                content = MaterialTheme.colorScheme.onSecondaryContainer,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = "Sincronizando ${state.pending} ${if (state.pending == 1) "item" else "itens"}…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is SyncState.Offline -> SyncBannerSurface(
                modifier = modifier,
                background = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Icon(Icons.Default.WifiOff, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = "Sem conexão — alterações serão enviadas depois",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            is SyncState.Error -> SyncBannerSurface(
                modifier = modifier,
                background = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
            }

            SyncState.Idle -> Unit
        }
    }
}

@Composable
private fun SyncBannerSurface(
    modifier: Modifier,
    background: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
    body: @Composable () -> Unit,
) {
    Surface(color = background, contentColor = content, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) { body() }
    }
}

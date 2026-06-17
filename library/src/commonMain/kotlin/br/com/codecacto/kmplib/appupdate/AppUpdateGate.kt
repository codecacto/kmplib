package br.com.codecacto.kmplib.appupdate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.platform.getUrlLauncher

/**
 * Wrapper de entry point que aplica a política de Force Update sobre o [content] do app.
 *
 * Faz o [AppUpdateService.check] uma vez (via [LaunchedEffect] na chave de identidade da config) e:
 * - **Hard** → renderiza uma tela bloqueante full-screen ([HardUpdateScreen]); o [content] NÃO é
 *   composto e não há como dispensar (sem botão fechar/voltar).
 * - **Soft** → renderiza o [content] normalmente + um diálogo dispensável ([SoftUpdateDialog])
 *   ("Atualizar" / "Agora não"); ao dispensar, segue usando o app.
 * - **None** ou ainda checando → renderiza apenas o [content].
 *
 * Best-effort: como [AppUpdateService.check] nunca lança nem bloqueia em falha, qualquer erro de
 * rede simplesmente deixa o [content] passar.
 *
 * Deve envolver a raiz da navegação, tipicamente dentro do `AppTheme`:
 * ```kotlin
 * AppTheme(colorPalette = ...) {
 *     ProvideIsCompact {
 *         AppUpdateGate(config = appUpdateConfig) {
 *             AppNavHost()
 *         }
 *     }
 * }
 * ```
 *
 * @param config Configuração da checagem. Mudanças de identidade re-disparam o `check()`.
 * @param texts Textos da UI (defaults pt-BR).
 * @param content Conteúdo real do app.
 */
@Composable
fun AppUpdateGate(
    config: AppUpdateConfig,
    texts: AppUpdateTexts = AppUpdateTexts(),
    content: @Composable () -> Unit,
) {
    val service = remember(config) { AppUpdateService(config) }
    var status by remember(config) { mutableStateOf<AppUpdateStatus>(AppUpdateStatus.None) }
    var softDismissed by remember(config) { mutableStateOf(false) }

    LaunchedEffect(config) {
        status = service.check()
    }

    when (val current = status) {
        is AppUpdateStatus.Hard -> {
            HardUpdateScreen(
                texts = texts,
                serverMessage = current.message,
                onUpdate = { openStore(current.storeUrl) },
            )
        }

        is AppUpdateStatus.Soft -> {
            content()
            if (!softDismissed) {
                SoftUpdateDialog(
                    texts = texts,
                    serverMessage = current.message,
                    latestVersionName = current.latestVersionName,
                    onUpdate = {
                        softDismissed = true
                        openStore(current.storeUrl)
                    },
                    onDismiss = { softDismissed = true },
                )
            }
        }

        AppUpdateStatus.None -> content()
    }
}

private fun openStore(storeUrl: String?) {
    val url = storeUrl?.trim().orEmpty()
    if (url.isNotEmpty()) {
        getUrlLauncher().openUrl(url)
    } else {
        // Sem storeUrl explícita: abre a página do app na loja da plataforma atual.
        getUrlLauncher().openStorePage()
    }
}

/**
 * Tela bloqueante de atualização obrigatória. Sem botão de fechar/voltar — só "Atualizar".
 * Ocupa toda a tela com um [Surface] opaco, impedindo interação com o conteúdo por baixo.
 */
@Composable
fun HardUpdateScreen(
    texts: AppUpdateTexts = AppUpdateTexts(),
    serverMessage: String? = null,
    onUpdate: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = texts.updateIconContentDescription,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
                Text(
                    text = texts.hardTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = serverMessage?.takeIf { it.isNotBlank() } ?: texts.hardMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Button(
                    onClick = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(texts.hardUpdateButton)
                }
            }
        }
    }
}

/**
 * Diálogo dispensável de atualização opcional ("Atualizar" / "Agora não").
 */
@Composable
fun SoftUpdateDialog(
    texts: AppUpdateTexts = AppUpdateTexts(),
    serverMessage: String? = null,
    latestVersionName: String? = null,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val baseMessage = serverMessage?.takeIf { it.isNotBlank() } ?: texts.softMessage
    val message = latestVersionName?.takeIf { it.isNotBlank() }
        ?.let { "$baseMessage (v$it)" }
        ?: baseMessage

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = texts.updateIconContentDescription,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(texts.softTitle) },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onUpdate) { Text(texts.softUpdateButton) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(texts.softDismissButton) }
        },
    )
}

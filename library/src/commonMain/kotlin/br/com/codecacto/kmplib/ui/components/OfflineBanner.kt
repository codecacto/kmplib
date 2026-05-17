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
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.network.ConnectivityObserver

/**
 * Banner que aparece automaticamente quando o app fica offline.
 *
 * Inicia o [ConnectivityObserver] no `LaunchedEffect` e libera no `DisposableEffect`.
 *
 * ```kotlin
 * Column {
 *     OfflineBanner(observer)
 *     // ... resto da UI
 * }
 * ```
 *
 * @param observer observador de conectividade. Pode ser injetado via DI.
 * @param text texto exibido quando offline.
 * @param backgroundColor cor de fundo do banner.
 * @param contentColor cor do texto e ícone.
 */
@Composable
fun OfflineBanner(
    observer: ConnectivityObserver,
    modifier: Modifier = Modifier,
    text: String = "Sem conexão",
    backgroundColor: Color = MaterialTheme.colorScheme.errorContainer,
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer
) {
    LaunchedEffect(observer) { observer.start() }
    DisposableEffect(observer) {
        onDispose { observer.stop() }
    }

    val online by observer.isOnline.collectAsState()

    AnimatedVisibility(
        visible = !online,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Surface(
            color = backgroundColor,
            contentColor = contentColor,
            modifier = modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

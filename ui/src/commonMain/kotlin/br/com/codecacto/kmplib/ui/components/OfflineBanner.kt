package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_offline_short
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
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
 * Banner que aparece quando o app está offline.
 *
 * Overload "puro" — recebe diretamente o estado [isOnline]. Útil para:
 *
 * **`windowInsets` (2.235.0):** o inset que o banner aplica DENTRO do próprio fundo. Default = nenhum
 * (no meio do conteúdo, sob uma `TopAppBar`, ele não deve somar status bar). Quando o banner é a
 * PRIMEIRA coisa da tela — no topo da janela, acima de tudo, como no `ConnectivityStyle.Banner` —
 * passe `WindowInsets.statusBars`: o fundo pinta atrás da status bar e o texto fica abaixo dela.
 * - testes (passar valores fixos)
 * - apps que já têm seu próprio observador de conectividade
 *
 * Para uso com [ConnectivityObserver] da lib, use o overload que recebe
 * o observer.
 */
@Composable
fun OfflineBanner(
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    text: String = kmpStringResource(Res.string.kmplib_offline_short),
    backgroundColor: Color = MaterialTheme.colorScheme.errorContainer,
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0)
) {
    AnimatedVisibility(
        visible = !isOnline,
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
                    // Dentro do Surface: o fundo do banner pinta atrás da barra do sistema e o texto
                    // começa depois dela. No `modifier` (fora do Surface) a faixa ficaria sem cor.
                    .windowInsetsPadding(windowInsets)
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
    text: String = kmpStringResource(Res.string.kmplib_offline_short),
    backgroundColor: Color = MaterialTheme.colorScheme.errorContainer,
    contentColor: Color = MaterialTheme.colorScheme.onErrorContainer,
    windowInsets: WindowInsets = WindowInsets(0, 0, 0, 0)
) {
    LaunchedEffect(observer) { observer.start() }
    DisposableEffect(observer) {
        onDispose { observer.stop() }
    }

    val online by observer.isOnline.collectAsState()

    OfflineBanner(
        isOnline = online,
        modifier = modifier,
        text = text,
        backgroundColor = backgroundColor,
        contentColor = contentColor,
        windowInsets = windowInsets
    )
}

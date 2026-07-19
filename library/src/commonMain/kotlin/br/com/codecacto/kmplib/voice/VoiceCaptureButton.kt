package br.com.codecacto.kmplib.voice

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Botão de **captura por voz** (ícone de microfone) para colocar ao lado de um campo numérico.
 *
 * Ao ser tocado (`onClick`), o app deve abrir o [DictationOverlay] — este botão apenas dispara a
 * ação; ele não faz reconhecimento. Alvo de toque generoso (default 48dp) para uso com luvas no
 * curral (caso Arroba Certa). Cores via tokens do tema (sem hardcode).
 *
 * ```kotlin
 * var showDictation by remember { mutableStateOf(false) }
 * VoiceCaptureButton(onClick = { showDictation = true })
 * if (showDictation) {
 *     DictationOverlay(
 *         recognizer = rememberSpeechRecognizer(),
 *         onConfirm = { peso -> field = peso; showDictation = false },
 *         onDismiss = { showDictation = false },
 *     )
 * }
 * ```
 *
 * @param onClick abre o overlay de ditado.
 * @param modifier modificador externo.
 * @param enabled quando `false`, o botão fica desabilitado (esmaecido).
 * @param size diâmetro do alvo de toque (default 48dp).
 * @param tint cor do ícone/realce (default `colorScheme.primary`).
 * @param contentDescription rótulo de acessibilidade (leitor de tela).
 */
@Composable
fun VoiceCaptureButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 48.dp,
    tint: Color = MaterialTheme.colorScheme.primary,
    contentDescription: String = "Ditar por voz",
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        colors = IconButtonDefaults.iconButtonColors(
            contentColor = tint,
        ),
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = contentDescription,
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

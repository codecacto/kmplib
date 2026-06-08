package br.com.codecacto.kmplib.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.AppTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Barra de reprodução de áudio: botão play/pause (ou "replay" quando concluído),
 * slider de progresso com seek e os tempos atual/total.
 *
 * Stateless: recebe os valores já coletados dos [StateFlow]s do [AudioPlayer] e devolve
 * callbacks. Mantém a separação Screen/Content do padrão MVI da kmplib — a tela coleta
 * `player.currentPosition`, `player.duration`, `player.isPlaying`, `player.state` e repassa.
 *
 * Cores via [MaterialTheme.colorScheme] (tema da kmplib). Acessível: o botão e o slider
 * expõem `contentDescription`.
 *
 * Exemplo:
 * ```kotlin
 * val pos by vm.player.currentPosition.collectAsState()
 * val dur by vm.player.duration.collectAsState()
 * val playing by vm.player.isPlaying.collectAsState()
 * val state by vm.player.state.collectAsState()
 *
 * AudioPlayerBar(
 *     positionMs = pos,
 *     durationMs = dur,
 *     isPlaying = playing,
 *     state = state,
 *     onPlayPause = { if (playing) vm.player.pause() else vm.player.play(filePath) },
 *     onSeek = { vm.player.seekTo(it) }
 * )
 * ```
 *
 * @param positionMs Posição atual em milissegundos.
 * @param durationMs Duração total em milissegundos.
 * @param isPlaying Se está reproduzindo agora.
 * @param state Estado de alto nível do player (controla o ícone e o estado de loading).
 * @param onPlayPause Disparado ao tocar no botão central (alterna play/pause ou reinicia se concluído).
 * @param onSeek Disparado ao soltar o slider, com a posição-alvo em ms.
 * @param modifier Modificador externo.
 * @param enabled Habilita/desabilita os controles (ex.: enquanto não há arquivo).
 */
@Composable
fun AudioPlayerBar(
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    state: AudioPlayerState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val isCompleted = state == AudioPlayerState.COMPLETED
    val isLoading = state == AudioPlayerState.LOADING

    val sliderMax = if (durationMs > 0) durationMs.toFloat() else 1f
    val sliderValue = positionMs.coerceIn(0L, maxOf(durationMs, 0L)).toFloat()

    val playPauseDescription = when {
        isCompleted -> "Reproduzir novamente"
        isPlaying -> "Pausar"
        else -> "Reproduzir"
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onPlayPause,
                enabled = enabled && !isLoading,
                modifier = Modifier
                    .size(48.dp)
                    .semantics { contentDescription = playPauseDescription }
            ) {
                val icon = when {
                    isCompleted -> Icons.Filled.Replay
                    isPlaying -> Icons.Filled.Pause
                    else -> Icons.Filled.PlayArrow
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Slider(
                value = sliderValue,
                onValueChange = { onSeek(it.toLong()) },
                valueRange = 0f..sliderMax,
                enabled = enabled && durationMs > 0,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Progresso da reprodução" },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatPlayerTime(positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatPlayerTime(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Formata um tempo em milissegundos como `m:ss` (ou `h:mm:ss` se >= 1h).
 * Exposto para reuso em telas que exibem duração de áudio fora da barra.
 */
fun formatPlayerTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    fun pad(n: Long) = if (n < 10) "0$n" else "$n"
    return if (hours > 0) "$hours:${pad(minutes)}:${pad(seconds)}" else "$minutes:${pad(seconds)}"
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun AudioPlayerBarPlayingPreview() {
    AppTheme {
        AudioPlayerBar(
            positionMs = 42_000,
            durationMs = 185_000,
            isPlaying = true,
            state = AudioPlayerState.PLAYING,
            onPlayPause = {},
            onSeek = {}
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun AudioPlayerBarCompletedPreview() {
    AppTheme {
        AudioPlayerBar(
            positionMs = 185_000,
            durationMs = 185_000,
            isPlaying = false,
            state = AudioPlayerState.COMPLETED,
            onPlayPause = {},
            onSeek = {}
        )
    }
}

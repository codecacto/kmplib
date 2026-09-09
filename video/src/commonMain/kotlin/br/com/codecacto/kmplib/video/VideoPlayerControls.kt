package br.com.codecacto.kmplib.video

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Os controles sobre o vídeo — **Compose, não a barra nativa de cada plataforma**.
 *
 * O motivo é a paridade: os controles do `PlayerView` (Android) e os da AVKit (iOS) não têm os
 * mesmos botões, não têm o mesmo menu de velocidade e não deixam a marca d'água ficar por baixo
 * deles. Um curso que parece um app diferente em cada aparelho é o que isto evita.
 *
 * O que é nativo continua nativo: a superfície, o decodificador, a legenda embutida, a sessão de
 * mídia. Só o desenho dos botões é nosso.
 */
@Composable
internal fun VideoPlayerControls(
    state: VideoPlayerState,
    texts: VideoPlayerTexts,
    colors: VideoPlayerColors,
    isFullscreen: Boolean,
    onFullscreenChange: ((Boolean) -> Unit)?,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Enquanto o dedo arrasta, a barra obedece ao DEDO e não ao player: sem isto o relógio de
    // progresso sobrescreve a posição arrastada 4 vezes por segundo e o polegar "escorrega" de volta.
    var arrastando by remember { mutableStateOf(false) }
    var posicaoArrastada by remember { mutableStateOf(0f) }

    val duracao = state.durationMillis
    val posicao = if (arrastando) posicaoArrastada.toLong() else state.positionMillis

    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to colors.scrim,
                0.35f to Color.Transparent,
                0.65f to Color.Transparent,
                1f to colors.scrim,
            ),
        ),
    ) {
        // ------------------------------------------------------------------ transporte, no centro
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ControlIcon(
                icon = Icons.Filled.Replay10,
                description = texts.rewind,
                tint = colors.chrome,
            ) {
                onInteraction()
                state.seekBy(-state.config.seekStepMillis)
            }

            ControlIcon(
                icon = when {
                    state.status == VideoStatus.Ended -> Icons.Filled.Replay
                    state.isPlaying -> Icons.Filled.Pause
                    else -> Icons.Filled.PlayArrow
                },
                description = when {
                    state.status == VideoStatus.Ended -> texts.replay
                    state.isPlaying -> texts.pause
                    else -> texts.play
                },
                tint = colors.chrome,
                size = 44.dp,
            ) {
                onInteraction()
                state.playPause()
            }

            ControlIcon(
                icon = Icons.Filled.Forward10,
                description = texts.forward,
                tint = colors.chrome,
            ) {
                onInteraction()
                state.seekBy(state.config.seekStepMillis)
            }
        }

        // ------------------------------------------------------------------ rodapé
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatVideoTime(posicao),
                    color = colors.chrome,
                    fontSize = 12.sp,
                )
                Slider(
                    value = videoProgressOf(posicao, duracao),
                    onValueChange = { fracao ->
                        onInteraction()
                        arrastando = true
                        posicaoArrastada = fracao * duracao.coerceAtLeast(0L).toFloat()
                    },
                    onValueChangeFinished = {
                        arrastando = false
                        state.seekTo(posicaoArrastada.toLong())
                    },
                    // Sem duração não há para onde arrastar — e um slider habilitado sobre um
                    // vídeo que ainda não abriu manda o player buscar a posição 0 de um manifesto
                    // inexistente.
                    enabled = duracao > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.accent,
                        activeTrackColor = colors.accent,
                        inactiveTrackColor = colors.track,
                        disabledThumbColor = colors.track,
                        disabledActiveTrackColor = colors.track,
                        disabledInactiveTrackColor = colors.track,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .semantics { contentDescription = texts.progress },
                )
                Text(
                    text = formatVideoTime(duracao),
                    color = colors.chrome,
                    fontSize = 12.sp,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SpeedControl(state = state, texts = texts, colors = colors, onInteraction = onInteraction)

                if (state.subtitleOptions.isNotEmpty()) {
                    SubtitleControl(state = state, texts = texts, colors = colors, onInteraction = onInteraction)
                }

                Box(Modifier.weight(1f))

                if (onFullscreenChange != null) {
                    ControlIcon(
                        icon = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        description = if (isFullscreen) texts.exitFullscreen else texts.enterFullscreen,
                        tint = colors.chrome,
                    ) {
                        onInteraction()
                        onFullscreenChange(!isFullscreen)
                    }
                }
            }
        }
    }
}

/** O menu das seis velocidades. O botão mostra a atual — `1×`, `1,5×`. */
@Composable
private fun SpeedControl(
    state: VideoPlayerState,
    texts: VideoPlayerTexts,
    colors: VideoPlayerColors,
    onInteraction: () -> Unit,
) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = {
                onInteraction()
                aberto = true
            },
            modifier = Modifier.semantics { contentDescription = texts.speed },
        ) {
            Text(
                text = formatVideoSpeed(state.speed),
                color = colors.chrome,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            VIDEO_SPEEDS.forEach { velocidade ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatVideoSpeed(velocidade),
                            fontWeight = if (velocidade == state.speed) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        aberto = false
                        onInteraction()
                        state.setSpeed(velocidade)
                    },
                )
            }
        }
    }
}

/** Ligar/desligar legenda e escolher a faixa. O ícone mostra se há alguma ligada. */
@Composable
private fun SubtitleControl(
    state: VideoPlayerState,
    texts: VideoPlayerTexts,
    colors: VideoPlayerColors,
    onInteraction: () -> Unit,
) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        ControlIcon(
            icon = if (state.selectedSubtitle == null) {
                Icons.Outlined.ClosedCaptionOff
            } else {
                Icons.Outlined.ClosedCaption
            },
            description = texts.subtitles,
            tint = colors.chrome,
        ) {
            onInteraction()
            aberto = true
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = texts.subtitlesOff,
                        fontWeight = if (state.selectedSubtitle == null) FontWeight.Bold else FontWeight.Normal,
                    )
                },
                onClick = {
                    aberto = false
                    onInteraction()
                    state.selectSubtitle(null)
                },
            )
            state.subtitleOptions.forEach { opcao ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = opcao.label,
                            fontWeight = if (opcao.id == state.selectedSubtitle?.id) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            },
                        )
                    },
                    onClick = {
                        aberto = false
                        onInteraction()
                        state.selectSubtitle(opcao)
                    },
                )
            }
        }
    }
}

/**
 * Um botão de controle. O `IconButton` já garante os 48dp de alvo de toque; o [size] é o do
 * DESENHO — a distinção que a 2.189.0 documentou para o `AppCheckbox`.
 */
@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: Color,
    size: androidx.compose.ui.unit.Dp = 28.dp,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.width(48.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(size),
        )
    }
}

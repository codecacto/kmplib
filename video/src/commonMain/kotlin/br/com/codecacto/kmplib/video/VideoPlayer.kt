package br.com.codecacto.kmplib.video

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * O player de vídeo da fábrica: **quadro + controles + a sua sobreposição**.
 *
 * ```kotlin
 * val player = rememberVideoPlayerState(
 *     media = VideoMedia(url = aula.hlsUrl, title = aula.titulo, startPositionMillis = aula.retomarEm),
 *     onPosition = { pos, _ -> vm.salvarProgresso(pos) },
 * )
 *
 * VideoPlayer(
 *     state = player,
 *     modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
 *     isFullscreen = cheio,
 *     onFullscreenChange = { cheio = it },
 * ) {
 *     // A marca d'água é do APP: nome, e-mail, matrícula — o que o produto decidir.
 *     Text(
 *         aluno.email,
 *         color = Color.White.copy(alpha = 0.35f),
 *         modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
 *     )
 * }
 * ```
 *
 * ### As camadas, de baixo para cima
 * 1. **[VideoSurface]** — os quadros e a legenda **embutida** (desenhada pela plataforma).
 * 2. **Legenda externa** — a fala do arquivo `.vtt`/`.srt`, quando é essa a escolhida.
 * 3. **[overlay]** — o seu slot. Fica **acima** do vídeo e da legenda e **abaixo** dos controles,
 *    que é a única ordem em que uma marca d'água antipirataria funciona sem engolir o botão de
 *    pausa. O slot **não recebe toque**: os gestos passam por ele para os controles.
 * 4. **Estados** — roda de carregamento, mensagem de erro com "tentar de novo".
 * 5. **Controles** — somem sozinhos depois de [CONTROLS_HIDE_DELAY_MILLIS] tocando, e voltam ao
 *    toque na tela.
 *
 * ### Tela cheia é decisão do APP
 * [onFullscreenChange] existe, mas quem gira o aparelho, esconde as barras do sistema ou troca de
 * rota é o app: um player de lib que force `SCREEN_ORIENTATION_LANDSCAPE` sequestra a Activity de
 * quem o embutiu. Passando `null`, o botão de tela cheia some.
 *
 * @param controls `false` desenha só o quadro (e a sua sobreposição), sem nenhum controle — para
 *   uma vitrine em autoplay, por exemplo.
 */
@Composable
fun VideoPlayer(
    state: VideoPlayerState,
    modifier: Modifier = Modifier,
    texts: VideoPlayerTexts = state.texts,
    colors: VideoPlayerColors = defaultVideoPlayerColors(),
    controls: Boolean = true,
    isFullscreen: Boolean = false,
    onFullscreenChange: ((Boolean) -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    var controlesVisiveis by remember { mutableStateOf(true) }

    // Some sozinho enquanto toca. Pausado, os controles FICAM: quem pausou está olhando para eles.
    LaunchedEffect(controlesVisiveis, state.isPlaying) {
        if (controlesVisiveis && state.isPlaying) {
            delay(CONTROLS_HIDE_DELAY_MILLIS)
            controlesVisiveis = false
        }
    }
    // Um erro traz os controles de volta: é onde está o "tentar de novo".
    LaunchedEffect(state.status) {
        if (state.status is VideoStatus.Error || state.status == VideoStatus.Ended) {
            controlesVisiveis = true
        }
    }

    Box(
        modifier = modifier
            .background(colors.background)
            .pointerInput(controls) {
                if (!controls) return@pointerInput
                detectTapGestures(onTap = { controlesVisiveis = !controlesVisiveis })
            },
    ) {
        VideoSurface(state, Modifier.fillMaxSize())

        // Legenda externa. Fica acima do rodapé de controles quando eles estão visíveis, para a
        // fala não ficar embaixo da barra de progresso.
        val cue = state.currentCue
        if (cue != null) {
            ExternalSubtitle(
                text = cue.text,
                colors = colors,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = 24.dp,
                        end = 24.dp,
                        bottom = if (controls && controlesVisiveis) 88.dp else 20.dp,
                    ),
            )
        }

        // O slot do app. `Box` sem `pointerInput`: o toque atravessa para os controles.
        overlay()

        when (val status = state.status) {
            VideoStatus.Loading -> CircularProgressIndicator(
                color = colors.chrome,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )

            VideoStatus.Buffering -> CircularProgressIndicator(
                color = colors.chrome,
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )

            is VideoStatus.Error -> VideoErrorPanel(
                message = status.message,
                retryLabel = texts.retry,
                colors = colors,
                onRetry = state::retry,
                modifier = Modifier.align(Alignment.Center),
            )

            else -> Unit
        }

        if (controls && controlesVisiveis && state.status !is VideoStatus.Error) {
            VideoPlayerControls(
                state = state,
                texts = texts,
                colors = colors,
                isFullscreen = isFullscreen,
                onFullscreenChange = onFullscreenChange,
                onInteraction = { controlesVisiveis = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Quanto tempo os controles ficam na tela antes de sumirem, com o vídeo tocando. 3,5 s é o
 * intervalo em que dá para ler o relógio e alcançar um botão sem que a barra atrapalhe a aula.
 */
const val CONTROLS_HIDE_DELAY_MILLIS: Long = 3_500L

/**
 * A fala da legenda externa.
 *
 * Fundo escuro atrás do texto, e não sombra: sombra some sobre um quadro claro (uma quadra ao sol),
 * e legenda que só se lê em cena escura não é legenda.
 */
@Composable
private fun ExternalSubtitle(
    text: String,
    colors: VideoPlayerColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(colors.scrim, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            color = colors.chrome,
            fontSize = 15.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun VideoErrorPanel(
    message: String,
    retryLabel: String,
    colors: VideoPlayerColors,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            color = colors.chrome,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) {
            Text(retryLabel, color = colors.accent)
        }
    }
}

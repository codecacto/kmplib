package br.com.codecacto.kmplib.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import br.com.codecacto.kmplib.core.network.createHttpClient
import br.com.codecacto.kmplib.platform.KeepScreenOn
import io.ktor.client.HttpClient
import kotlinx.coroutines.delay

/**
 * Cria o player da plataforma. Chame [rememberVideoPlayerState] em vez desta — é ela que amarra o
 * ciclo de vida, o relógio de progresso e a liberação.
 */
expect fun createVideoPlayerState(
    config: VideoPlayerConfig,
    texts: VideoPlayerTexts,
): VideoPlayerState

/**
 * O [VideoPlayerState] preso a esta composição — **com o ciclo de vida resolvido**.
 *
 * O que ele faz por você, e que é a origem dos três defeitos clássicos de player embutido:
 *
 * 1. **Libera ao sair da tela.** `onDispose { release() }`. Sem isso o áudio segue tocando por
 *    cima da tela seguinte — o defeito que derrubou as duas tentativas anteriores de player na
 *    kmplib (ver `ui.components.video.VideoPlayerDialog`).
 * 2. **Pausa ao sair do primeiro plano**, se [VideoPlayerConfig.backgroundBehavior] for
 *    [VideoBackgroundBehavior.Pause]. O gatilho é `ON_STOP`, e não `ON_PAUSE`: um diálogo por cima
 *    (o menu de velocidade, uma permissão) já dispara `ON_PAUSE`, e a aula pararia sozinha.
 * 3. **Mantém a tela acesa** enquanto toca, e só enquanto toca — o `KeepScreenOn` do
 *    `kmplib-platform` solta o pedido no descarte.
 *
 * Além disso mantém o relógio de progresso (~4×/s, **só quando está tocando**) e chama [onPosition]
 * a cada [VideoPlayerConfig.positionReportIntervalMillis] para o app persistir "onde parou".
 *
 * @param media o vídeo. Trocar de [media] recarrega o player. `null` = nada carregado.
 * @param onPosition o gancho de "salvar onde parou". Recebe posição e duração em milissegundos.
 *   É chamado **também na pausa e ao sair da tela**, com a posição exata — o intervalo é o piso da
 *   frequência, não a única oportunidade: quem fecha o app dez segundos depois do último tique
 *   perderia dez segundos de progresso.
 * @param httpClient usado só para baixar legenda externa. Default: um cliente próprio do player,
 *   liberado junto com ele.
 * @param onRenewUrl **a renovação silenciosa da URL assinada**. Chamado quando a reprodução falha
 *   com [VideoErrorKind.Expired] — o app pede uma URL nova ao servidor e devolve; o player recarrega
 *   **na posição em que estava**, e o aluno vê no máximo um instante de espera em vez de uma tela de
 *   erro. Devolver `null` (ou lançar) deixa o erro na tela, com o botão de tentar de novo.
 *   ⚠️ Vale [MAX_RENOVACOES_DE_URL] tentativas por mídia: sem o teto, um servidor que devolve
 *   sempre a mesma URL vencida põe o player num laço de recarga infinito — que não trava a tela, e
 *   por isso ninguém percebe até a conta do CDN chegar.
 */
@Composable
fun rememberVideoPlayerState(
    media: VideoMedia?,
    config: VideoPlayerConfig = VideoPlayerConfig(),
    texts: VideoPlayerTexts = VideoPlayerTexts(),
    httpClient: HttpClient? = null,
    onPosition: ((positionMillis: Long, durationMillis: Long) -> Unit)? = null,
    onRenewUrl: (suspend (VideoMedia) -> String?)? = null,
): VideoPlayerState {
    val state = remember(config, texts) { createVideoPlayerState(config, texts) }

    // Cliente próprio quando o app não passa o dele: um player de curso baixa, no máximo, um
    // arquivo de legenda por aula. Fechado junto com o player.
    val clienteProprio = remember(httpClient) { if (httpClient == null) createHttpClient() else null }
    val cliente = httpClient ?: clienteProprio!!

    DisposableEffect(state) {
        onDispose {
            // A posição final ANTES de soltar o player: depois do release não há mais o que ler, e
            // é exatamente aqui que "voltar da aula" precisa gravar onde parou.
            state.refreshProgress()
            onPosition?.invoke(state.positionMillis, state.durationMillis)
            state.release()
            clienteProprio?.close()
        }
    }

    LaunchedEffect(state, media) {
        if (media != null) state.load(media)
    }

    // Renovação da URL assinada. A contagem é por MÍDIA: trocar de aula devolve as tentativas.
    var renovacoes by remember(media) { mutableIntStateOf(0) }
    LaunchedEffect(state, state.status) {
        val atual = state.status
        if (onRenewUrl == null || atual !is VideoStatus.Error) return@LaunchedEffect
        if (atual.kind != VideoErrorKind.Expired) return@LaunchedEffect
        if (renovacoes >= MAX_RENOVACOES_DE_URL) return@LaunchedEffect
        val carregada = state.media ?: return@LaunchedEffect
        val posicao = state.positionMillis
        renovacoes++
        val nova = runCatching { onRenewUrl(carregada) }.getOrNull()
        if (!nova.isNullOrBlank()) {
            state.load(carregada.copy(url = nova, startPositionMillis = posicao))
        }
    }

    // Legenda externa: baixa e interpreta quando a escolha muda.
    LaunchedEffect(state, state.selectedSubtitle) {
        state.loadSelectedExternalSubtitle(cliente)
    }

    // Relógio de progresso. Roda SÓ enquanto toca: um player pausado numa tela aberta não tem por
    // que recompor quatro vezes por segundo.
    LaunchedEffect(state, state.needsProgressTicker) {
        if (!state.needsProgressTicker) {
            state.refreshProgress()
            return@LaunchedEffect
        }
        var ultimoReporte = state.positionMillis
        while (true) {
            state.refreshProgress()
            val intervalo = state.config.positionReportIntervalMillis
            if (onPosition != null && intervalo > 0) {
                val andou = state.positionMillis - ultimoReporte
                if (andou >= intervalo || andou <= -intervalo) {
                    ultimoReporte = state.positionMillis
                    onPosition(state.positionMillis, state.durationMillis)
                }
            }
            delay(PROGRESS_TICK_MILLIS)
        }
    }

    // Pausa ao sair do primeiro plano. ON_STOP, não ON_PAUSE — ver o KDoc acima.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        state.onEnterBackground()
        onPosition?.invoke(state.positionMillis, state.durationMillis)
    }

    KeepScreenOn(enabled = config.keepScreenOn && state.isPlaying)

    return state
}

/**
 * 250 ms. É o passo do relógio de progresso, e ele governa duas coisas ao mesmo tempo: a barra que
 * anda e a **troca de fala da legenda externa** (ver [SubtitleCue]). Mais lento que isto e a
 * legenda atrasa de forma visível; mais rápido é recomposição à toa.
 */
internal const val PROGRESS_TICK_MILLIS = 250L

/**
 * Quantas vezes a URL assinada é renovada automaticamente por mídia, antes de o erro ir para a
 * tela. Duas: a primeira cobre o token que venceu na pausa, a segunda cobre o relógio do aparelho
 * adiantado. A terceira já é servidor devolvendo lixo, e insistir vira laço.
 */
const val MAX_RENOVACOES_DE_URL: Int = 2

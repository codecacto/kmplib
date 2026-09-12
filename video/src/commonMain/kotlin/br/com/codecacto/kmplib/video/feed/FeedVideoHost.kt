package br.com.codecacto.kmplib.video.feed

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layoutBounds
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.codecacto.kmplib.platform.KeepScreenOn
import kotlinx.coroutines.delay

/**
 * O controller do feed em volta. O [FeedVideoHost] o provê; o [FeedVideo] o lê.
 *
 * `null` fora de um host — e o [FeedVideo] **falha alto** nesse caso, em vez de criar um controller
 * próprio: dois vídeos cada um com o seu coordenador tocariam juntos, com o som de um por cima do
 * outro, e o build ficaria verde.
 */
val LocalFeedVideoController: ProvidableCompositionLocal<FeedVideoController?> =
    staticCompositionLocalOf { null }

/**
 * O [FeedVideoController] preso a esta composição — **com o ciclo de vida resolvido**:
 *
 * - **`ON_STOP` destrói os players** (e `ON_START` os recria). `ON_STOP`, e não `ON_PAUSE`: um
 *   diálogo por cima dispara `ON_PAUSE`, e o vídeo pararia por causa de um menu. É também o gatilho
 *   de navegação: a tela que ficou para trás na pilha recebe `ON_STOP`.
 * - **Sair da composição destrói tudo** — inclusive o pré-carregador.
 * - **Tela acesa só com som.** Vídeo de feed é laço: com a tela presa acesa ele tocaria para sempre
 *   num celular largado na mesa. Mudo, vale o tempo de tela do sistema (a pessoa que rola o feed já
 *   o mantém aceso tocando nele); com som, a pessoa está assistindo, e a tela não apaga no meio.
 *   No iOS isso significa desligar o `preventsDisplaySleepDuringVideoPlayback` do AVPlayer, cujo
 *   default seguraria a tela acesa até com o vídeo mudo.
 *
 * @param sound o som do feed. Default: [FeedVideoSoundState.Shared] — o mesmo para o processo todo.
 */
@Composable
fun rememberFeedVideoController(
    config: FeedVideoConfig = FeedVideoConfig(),
    sound: FeedVideoSoundState = FeedVideoSoundState.Shared,
): FeedVideoController {
    val ciclo = LocalLifecycleOwner.current.lifecycle

    // O pré-carregador vive junto do controller: no Android ele é dono do `DefaultPreloadManager`,
    // e é o mesmo builder dele que constrói os players do pool (ver `createFeedVideoEngine`).
    val preloader = remember(config) { createFeedPreloader(config) }

    val controller = remember(config, sound, preloader) {
        FeedVideoController(
            config = config,
            sound = sound,
            preloader = preloader,
            engineFactory = { createFeedVideoEngine(config, preloader) },
        ).also {
            // Composto com a tela já fora do primeiro plano: nasce parado, e o `ON_START` o liga.
            if (!ciclo.currentState.isAtLeast(Lifecycle.State.STARTED)) it.onStop()
        }
    }

    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) { controller.onStart() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { controller.onStop() }

    // O som pode mudar fora deste feed (o detalhe da publicação divide o mesmo estado): quem
    // estiver vivo acompanha.
    LaunchedEffect(controller) {
        snapshotFlow { sound.isMuted }.collect { controller.applySound() }
    }

    // Leitura periódica do estado nativo. No Android é no-op; no iOS é assim que se descobre que o
    // item ficou pronto ou falhou (o AVPlayer não empurra estado). É também o tique que reavalia se
    // o vídeo da vez está passando fome — e, se estiver, cancela o pré-carregamento dos vizinhos.
    LaunchedEffect(controller) {
        while (true) {
            controller.poll()
            delay(FEED_VIDEO_POLL_MILLIS)
        }
    }

    TelaAcesaComSom(controller)

    return controller
}

/**
 * Em composable próprio de propósito: `isPlaying` muda a cada troca de estado do vídeo, e lida no
 * corpo do [rememberFeedVideoController] ela recomporia quem o chamou — o feed inteiro.
 */
@Composable
private fun TelaAcesaComSom(controller: FeedVideoController) {
    KeepScreenOn(enabled = controller.isPlaying && !controller.sound.isMuted)
}

/**
 * A **área do feed**: provê o controller aos [FeedVideo] de dentro e marca o retângulo contra o qual
 * a visibilidade deles é medida.
 *
 * Ponha em volta da `LazyColumn` (ou de qualquer lista rolável). O tamanho vai no [modifier] do host;
 * o filho recebe as restrições mínimas dele, então uma `LazyColumn` com `fillMaxSize()` ocupa
 * exatamente a área medida.
 *
 * ```kotlin
 * FeedVideoHost(Modifier.fillMaxSize()) {
 *     LazyColumn(Modifier.fillMaxSize()) { … FeedVideo(…) … }
 * }
 * ```
 *
 * A área é a do host, e não a da janela, por causa do que fica por cima do feed: com um top bar ou
 * uma barra inferior fixos **fora** do host, o vídeo que está atrás da barra não conta como visível.
 */
@Composable
fun FeedVideoHost(
    modifier: Modifier = Modifier,
    controller: FeedVideoController = rememberFeedVideoController(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalFeedVideoController provides controller) {
        Box(modifier = modifier.layoutBounds(controller.viewport), propagateMinConstraints = true) {
            content()
        }
    }
}

/** De quanto em quanto tempo o estado nativo é lido (iOS) e a fome do vídeo da vez é reavaliada. */
internal const val FEED_VIDEO_POLL_MILLIS: Long = 150L

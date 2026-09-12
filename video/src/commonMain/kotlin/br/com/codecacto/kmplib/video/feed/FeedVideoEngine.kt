package br.com.codecacto.kmplib.video.feed

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import br.com.codecacto.kmplib.video.VideoStatus
import br.com.codecacto.kmplib.video.VideoStreamKind

/**
 * Um player nativo **do pool** do feed — a única parte do vídeo de feed que é código de plataforma.
 *
 * Não é o [br.com.codecacto.kmplib.video.VideoPlayerState] do player de aula, de propósito: aquele
 * nasce com `MediaSession`, legenda, velocidade e — no iOS — com a sessão de áudio em `.playback`,
 * que **interrompe a música** que a pessoa estava ouvindo. Um vídeo mudo de feed não pode fazer isso.
 *
 * - **Android:** um `ExoPlayer` (Media3) em laço (`REPEAT_MODE_ONE`), com buffer curto e origem de
 *   dados **com cache de disco** (ver `FeedVideoCache`).
 * - **iOS:** um `AVPlayer` com a camada já anexada e laço manual (ver `FeedVideoEngine.ios.kt`).
 *
 * Quem cria, recicla e libera é o [FeedVideoController]; nenhuma tela segura um destes.
 */
internal abstract class FeedVideoEngine {

    /** Em que ponto o player está. Estado do Compose: a caixa do vídeo recompõe sozinha. */
    var status: VideoStatus by mutableStateOf(VideoStatus.Idle)
        protected set

    /** A fonte carregada agora, ou `null` = vazio. É o que o pool usa para não recarregar à toa. */
    var loadedUrl: String? by mutableStateOf(null)
        protected set

    /**
     * Chamado quando **outro app tomou o áudio** (Android: perda definitiva do foco de áudio ou fone
     * desconectado; iOS: interrupção da sessão ou rota perdida) com o vídeo tocando COM som.
     *
     * O controller responde voltando o feed a **mudo** e seguindo a reprodução — é o que um feed faz:
     * o vídeo não para por causa da música de outro app, ele só se cala.
     */
    var onAudioLost: (() -> Unit)? = null

    /** Troca a fonte. Começa parado; quem manda tocar é o controller. */
    abstract fun load(url: String, kind: VideoStreamKind)

    /** Toca (idempotente). */
    abstract fun play()

    /** Pausa (idempotente). */
    abstract fun pause()

    /**
     * Com som ou sem. **Sem som não pede foco de áudio** (Android) e deixa a sessão em `.ambient`
     * (iOS) — a música de outro app continua tocando por baixo do vídeo.
     */
    abstract fun setMuted(muted: Boolean)

    /** Esvazia o player **sem destruí-lo**: solta decodificador e buffer, e fica pronto para reuso. */
    abstract fun clear()

    /** Destrói o player. Depois disto ele não é mais usado. */
    abstract fun release()

    /**
     * Este player está **adiantando** um vídeo que ainda não tem a vez (`true`), ou é o que está
     * tocando (`false`)? O controller avisa a cada troca de vez.
     *
     * O que cada plataforma faz com a informação — e nos dois casos é a diferença entre "o feed
     * escorrega" e "o vídeo da vez engasga por causa do de baixo":
     * - **Android:** `ExoPlayer.setPriority` (`PRIORITY_PLAYBACK` × `PRIORITY_PLAYBACK_PRELOAD`). Em
     *   aparelho apertado é **isto** que decide quem perde o decodificador de vídeo — o CDD do
     *   Android 16 garante só 6 decodificadores SDR simultâneos, e há aparelho relatando **1**. Sem
     *   prioridade, quem o sistema derruba pode ser justamente o vídeo que está na tela.
     * - **iOS:** `preferredForwardBufferDuration` — 1 s em quem está fora da vez, automático em quem
     *   toca. É o equivalente possível do pré-carregamento medido (a AVFoundation não tem
     *   `PreloadManager`).
     */
    open fun setPreloadMode(preloading: Boolean) = Unit

    /**
     * Quanto há de buffer **à frente** da posição atual, em milissegundos, ou `null` se a plataforma
     * não souber dizer.
     *
     * Serve a uma decisão só: [shouldCancelFeedPreload] — o vídeo da vez passando fome cancela todo
     * o pré-carregamento dos vizinhos. `null` **não** é fome (ver lá).
     */
    open fun bufferedAheadMillis(): Long? = null

    /**
     * Lê o estado do player nativo. No Android é no-op (o ExoPlayer empurra eventos); no iOS é a
     * leitura periódica — o AVPlayer não tem listener, o `status` do item se lê.
     */
    open fun refresh() = Unit
}

/**
 * Cria o player nativo do pool. Só o [FeedVideoController] chama.
 *
 * @param preloader o pré-carregador do feed. No Android o player é construído **pelo mesmo builder**
 *   do `DefaultPreloadManager`, para dividir com ele a thread de reprodução e o cache; sem isso os
 *   dois disputariam recursos sem se enxergar.
 */
internal expect fun createFeedVideoEngine(
    config: FeedVideoConfig,
    preloader: FeedPreloader,
): FeedVideoEngine

/**
 * A superfície de um player do pool dentro da caixa do post.
 *
 * @param onFrameVisibleChange `true` quando a superfície **já mostra um quadro** deste player — é
 *   o momento em que a capa pode sair sem deixar a tela preta.
 */
@Composable
internal expect fun FeedVideoSurface(
    engine: FeedVideoEngine,
    scale: FeedVideoScale,
    modifier: Modifier,
    onFrameVisibleChange: (Boolean) -> Unit,
)

package br.com.codecacto.kmplib.video.feed

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.video.VideoPlayerHolder
import br.com.codecacto.kmplib.video.VideoStatus
import br.com.codecacto.kmplib.video.VideoStreamKind
import br.com.codecacto.kmplib.video.paraVideoErrorKind
import br.com.codecacto.kmplib.video.videoStatusOf
import br.com.codecacto.kmplib.video.videoStreamKindOf

/**
 * Um `ExoPlayer` (Media3) do pool do feed.
 *
 * ### Som e foco de áudio — a decisão que importa aqui
 * **Mudo não pede foco de áudio.** O `handleAudioFocus` do `setAudioAttributes` fica `false` e o
 * volume em `0`: a música que a pessoa está ouvindo em outro app **continua**, com o vídeo andando
 * por cima calado. É o comportamento de todo feed (Instagram, X), e é o que o Android pede — foco de
 * áudio é para quem vai **emitir** som.
 *
 * **Com som, o player pede foco** (`handleAudioFocus = true`, `USAGE_MEDIA`): a música de outro app
 * pausa, uma notificação abaixa o volume do vídeo (*ducking*, que a Media3 faz sozinha), e uma
 * ligação o pausa e retoma depois. Se outro app **tomar** o foco de vez, ou o fone for desconectado,
 * o ExoPlayer para — e aí o feed não fica parado: [onAudioLost] devolve o feed a mudo e o vídeo segue.
 *
 * ### Cache de disco (2.197.0)
 * A origem de dados é a do [FeedVideoCache], que **lê e escreve** em disco. Sem ela, o laço
 * (`REPEAT_MODE_ONE`) rebaixava da rede, a cada volta, tudo o que não coubesse nos 15 s de buffer —
 * um vídeo de 60 s em laço baixava 60 s de vídeo por minuto, para sempre.
 *
 * A chave é [feedVideoCacheKey] (a URL **sem a query**), nunca a URL crua: a URL de feed é assinada
 * e muda a cada abertura da tela.
 */
@OptIn(UnstableApi::class)
internal class ExoFeedVideoEngine(
    context: Context,
    config: FeedVideoConfig,
    private val preloader: Media3FeedPreloader?,
) : FeedVideoEngine() {

    val player: ExoPlayer = run {
        val builder = ExoPlayer.Builder(context)
            .setLoadControl(feedLoadControl())
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    FeedVideoCache.dataSourceFactory(context, config.diskCacheBytes),
                ),
            )
        // Construído pelo builder do `DefaultPreloadManager` quando ele existe: é assim que o player
        // e o pré-carregamento dividem a thread de reprodução e o cache (ver `Media3FeedPreloader`).
        preloader?.buildPlayer(builder) ?: builder.build()
    }

    private val atributos = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    /** `null` até o primeiro [setMuted] — força a primeira aplicação. */
    private var mudo: Boolean? = null

    /** `null` até o primeiro [setPreloadMode]. */
    private var preparando: Boolean? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = sincronizar()
        override fun onIsPlayingChanged(isPlaying: Boolean) = sincronizar()

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            sincronizar()
            val perdeuOAudio = reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS ||
                reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
            if (!playWhenReady && perdeuOAudio && mudo == false) onAudioLost?.invoke()
        }

        override fun onPlayerError(error: PlaybackException) {
            val kind = error.paraVideoErrorKind()
            AppLogger.w(FEED_TAG, "Vídeo de feed falhou (${error.errorCodeName}): ${error.message}")
            status = VideoStatus.Error(
                kind = kind,
                message = FeedVideoTexts().playbackError,
                cause = "${error.errorCodeName}: ${error.message}",
            )
        }
    }

    init {
        // Laço: o vídeo de feed recomeça sozinho no fim, sem passar por `STATE_ENDED`.
        player.repeatMode = Player.REPEAT_MODE_ONE
        player.addListener(listener)
        setMuted(true)
        setPreloadMode(true)
    }

    override fun load(url: String, kind: VideoStreamKind) {
        loadedUrl = url
        status = VideoStatus.Loading

        // A fonte que o pré-carregamento já preparou, quando existe: o player a adota no estado em
        // que está, sem reabrir o manifesto. É o que faz a troca de vídeo ser instantânea.
        val preparada = preloader?.mediaSourceFor(url)
        if (preparada != null) {
            player.setMediaSource(preparada)
        } else {
            player.setMediaItem(mediaItemDe(url, kind))
        }
        player.prepare()
    }

    private fun mediaItemDe(url: String, kind: VideoStreamKind): MediaItem {
        val forma = if (kind == VideoStreamKind.Auto) videoStreamKindOf(url) else kind
        return MediaItem.Builder()
            .setUri(url)
            // Sem a chave estável, cada renovação de token gravaria o MESMO vídeo de novo no cache.
            .setCustomCacheKey(feedVideoCacheKey(url))
            .apply {
                // URL assinada esconde o `.m3u8` do sniffer da Media3 (a extensão vem antes do
                // `?token=`): sem o MIME explícito o HLS cairia no extrator progressivo e falharia
                // com o build verde. Mesma regra do player de aula.
                if (forma == VideoStreamKind.Hls) setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            .build()
    }

    override fun play() {
        if (!player.playWhenReady) player.play()
    }

    override fun pause() {
        if (player.playWhenReady) player.pause()
    }

    override fun setMuted(muted: Boolean) {
        if (mudo == muted) return
        mudo = muted
        player.volume = if (muted) 0f else 1f
        // Pede o foco só com som; ao voltar a mudo, DEVOLVE o foco (a música de outro app pode voltar).
        player.setAudioAttributes(atributos, /* handleAudioFocus = */ !muted)
        // Fone desconectado só importa com som: mudo, não há o que sair no alto-falante.
        player.setHandleAudioBecomingNoisy(!muted)
    }

    /**
     * Quem perde o decodificador quando o aparelho aperta.
     *
     * `setPriority` alimenta o `KEY_IMPORTANCE` do `MediaCodec` (API 35+; abaixo disso é inócuo, não
     * é erro). Com dois players vivos e um aparelho que só garante um decodificador de vídeo, é esta
     * linha que faz o sistema derrubar o que está **adiantando** em vez do que está na tela.
     */
    override fun setPreloadMode(preloading: Boolean) {
        if (preparando == preloading) return
        preparando = preloading
        player.setPriority(if (preloading) C.PRIORITY_PLAYBACK_PRELOAD else C.PRIORITY_PLAYBACK)
    }

    override fun bufferedAheadMillis(): Long? {
        val bufferizado = player.bufferedPosition
        if (bufferizado == C.TIME_UNSET || bufferizado < 0L) return null
        return (bufferizado - player.currentPosition).coerceAtLeast(0L)
    }

    override fun clear() {
        player.stop()
        player.clearMediaItems()
        loadedUrl = null
        status = VideoStatus.Idle
    }

    override fun release() {
        player.removeListener(listener)
        player.release()
        loadedUrl = null
        status = VideoStatus.Idle
    }

    private fun sincronizar() {
        if (player.playerError != null) return
        if (loadedUrl == null) {
            status = VideoStatus.Idle
            return
        }
        val estado = player.playbackState
        status = videoStatusOf(
            preparado = estado == Player.STATE_READY ||
                estado == Player.STATE_BUFFERING && player.duration != C.TIME_UNSET,
            querTocar = player.playWhenReady,
            semDados = estado == Player.STATE_BUFFERING,
        )
    }
}

/**
 * O `LoadControl` do feed — **o mesmo para os players do pool e para o pré-carregamento**.
 *
 * O `DefaultLoadControl` padrão guarda até 50 s à frente, pensado para um player por tela. Com dois
 * players vivos no feed, cada um guardaria um vídeo de 60 s quase inteiro na memória e no plano de
 * dados, por um post que talvez a pessoa pule em dois segundos.
 *
 * **`bufferForPlaybackMs` = 500 ms**, e não 1 s: é o teto direto do tempo até o primeiro quadro — o
 * player não começa antes de ter esse tanto em mãos. É o valor do demo oficial de vídeo curto do
 * Google, e a diferença é sentida em toda troca de post. (29% das sessões de vídeo curto são
 * abandonadas nos 3 primeiros segundos; meio segundo aqui não é detalhe.)
 */
@OptIn(UnstableApi::class)
internal fun feedLoadControl(): LoadControl = DefaultLoadControl.Builder()
    .setBufferDurationsMs(
        /* minBufferMs = */ 5_000,
        /* maxBufferMs = */ 15_000,
        /* bufferForPlaybackMs = */ 500,
        /* bufferForPlaybackAfterRebufferMs = */ 2_000,
    )
    .build()

internal actual fun createFeedVideoEngine(
    config: FeedVideoConfig,
    preloader: FeedPreloader,
): FeedVideoEngine {
    val context = VideoPlayerHolder.getContext()
        ?: error(
            "kmplib-video: chame initKmpLibVideo(context) no Application.onCreate() " +
                "(ou KmpLib.init(context), se usa o artefato umbrella).",
        )
    return ExoFeedVideoEngine(context, config, preloader as? Media3FeedPreloader)
}

internal const val FEED_TAG = "KmpLibFeedVideo"

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
 * ### Buffer curto
 * O `DefaultLoadControl` padrão guarda até 50 s à frente — pensado para um player por tela. Com dois
 * players vivos no feed, cada um guardaria um vídeo de 60 s quase inteiro na memória e no plano de
 * dados, por um post que talvez a pessoa pule em dois segundos. Aqui: 15 s no máximo, e a
 * reprodução começa com 1 s em mãos.
 */
@OptIn(UnstableApi::class)
internal class ExoFeedVideoEngine(context: Context) : FeedVideoEngine() {

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 5_000,
                    /* maxBufferMs = */ 15_000,
                    /* bufferForPlaybackMs = */ 1_000,
                    /* bufferForPlaybackAfterRebufferMs = */ 2_000,
                )
                .build(),
        )
        .build()

    private val atributos = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        .build()

    /** `null` até o primeiro [setMuted] — força a primeira aplicação. */
    private var mudo: Boolean? = null

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
    }

    override fun load(url: String, kind: VideoStreamKind) {
        val forma = if (kind == VideoStreamKind.Auto) videoStreamKindOf(url) else kind
        val item = MediaItem.Builder()
            .setUri(url)
            .apply {
                // URL assinada esconde o `.m3u8` do sniffer da Media3 (a extensão vem antes do
                // `?token=`): sem o MIME explícito o HLS cairia no extrator progressivo e falharia
                // com o build verde. Mesma regra do player de aula.
                if (forma == VideoStreamKind.Hls) setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            .build()
        loadedUrl = url
        status = VideoStatus.Loading
        player.setMediaItem(item)
        player.prepare()
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

internal actual fun createFeedVideoEngine(): FeedVideoEngine {
    val context = VideoPlayerHolder.getContext()
        ?: error(
            "kmplib-video: chame initKmpLibVideo(context) no Application.onCreate() " +
                "(ou KmpLib.init(context), se usa o artefato umbrella).",
        )
    return ExoFeedVideoEngine(context)
}

internal const val FEED_TAG = "KmpLibFeedVideo"

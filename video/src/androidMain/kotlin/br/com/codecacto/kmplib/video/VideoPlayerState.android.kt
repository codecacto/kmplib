package br.com.codecacto.kmplib.video

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import br.com.codecacto.kmplib.video.download.Media3Downloads
import br.com.codecacto.kmplib.core.util.AppLogger
import java.lang.ref.WeakReference

/**
 * Holder do `Context` da aplicação. Inicializado por `initKmpLibVideo(context)` (ou por
 * `KmpLib.init(context)`, no umbrella).
 */
object VideoPlayerHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}

/**
 * **Media3/ExoPlayer** — o player recomendado pelo Android, e o único caminho para o que o produto
 * pede.
 *
 * O que o `MediaPlayer`/`VideoView` da plataforma **não** faz, e por isso não serve aqui: HLS
 * adaptativo confiável, `setPlaybackSpeed`, seleção de faixa de legenda e `MediaSession`. Não é
 * "o ExoPlayer é mais fácil": é que o outro não tem as funcionalidades.
 */
@OptIn(UnstableApi::class)
private class ExoPlayerVideoPlayerState(
    private val context: Context,
    config: VideoPlayerConfig,
    texts: VideoPlayerTexts,
) : VideoPlayerState(config, texts) {

    /** Exposto ao [VideoSurface] — é o `PlayerView` que precisa dele. */
    val player: ExoPlayer = ExoPlayer.Builder(context)
        // Foco de áudio pelo próprio player: sem isto, uma ligação ou outro app tocando música
        // ficam por cima da aula, e a aula não pausa. `C.USAGE_MEDIA` é o que classifica a
        // reprodução como mídia (volume de mídia, não de toque).
        .setAudioAttributes(
            androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true,
        )
        // Fone desconectado = pausa. É o comportamento que o usuário espera e que o Android pede
        // (senão a aula sai no alto-falante no meio do ônibus).
        .setHandleAudioBecomingNoisy(true)
        .build()

    /**
     * A sessão de mídia do sistema: título/artista na central de mídia e comandos de transporte de
     * fone, Bluetooth e tela de bloqueio.
     *
     * ⚠️ **O que ela ainda NÃO dá** — e está registrado no CHANGELOG e no backlog, não escondido:
     * a **notificação persistente** e a reprodução com o app **fechado** exigem um
     * `MediaSessionService` em primeiro plano, com o player morando no serviço e a tela falando com
     * ele por `MediaController`. É outra arquitetura, não um parâmetro; a API pública daqui já está
     * desenhada para recebê-la (ver [VideoBackgroundBehavior.ContinueAudio]) sem quebrar ninguém.
     */
    private var session: MediaSession? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = sincronizar()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = sincronizar()
        override fun onIsPlayingChanged(isPlaying: Boolean) = sincronizar()

        override fun onTracksChanged(tracks: Tracks) {
            updateEmbeddedSubtitles(tracks.legendasEmbutidas())
            aplicarPreferenciaInicialDeLegenda()
        }

        override fun onPlayerError(error: PlaybackException) {
            val kind = error.paraVideoErrorKind()
            AppLogger.e(VIDEO_TAG, "Falha na reprodução (${error.errorCodeName}): ${error.message}")
            status = VideoStatus.Error(
                kind = kind,
                message = texts.messageFor(kind),
                cause = "${error.errorCodeName}: ${error.message}",
            )
        }
    }

    /** `true` depois do primeiro [load] — evita aplicar a preferência de legenda a cada faixa nova. */
    private var preferenciaAplicada = false

    init {
        player.addListener(listener)
        player.setPlaybackSpeed(config.initialSpeed)
        if (config.mediaSession) {
            session = runCatching { MediaSession.Builder(context, player).build() }
                .onFailure { AppLogger.w(VIDEO_TAG, "MediaSession indisponível: ${it.message}") }
                .getOrNull()
        }
    }

    override fun load(media: VideoMedia) {
        this.media = media
        preferenciaAplicada = false
        externalCues = emptyList()
        applySelectedSubtitle(null)
        status = VideoStatus.Loading

        val item = MediaItem.Builder()
            .setUri(media.url)
            // `setMimeType` explícito quando a URL é assinada: `…/playlist.m3u8?token=…` esconde a
            // extensão do sniffer do `DefaultMediaSourceFactory`, e o HLS cairia no extrator
            // progressivo — o vídeo falha com `UnrecognizedInputFormatException` e o build está verde.
            .apply {
                if (media.resolvedKind() == VideoStreamKind.Hls) {
                    setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                }
                // A chave do cache de download é o `offlineId`, NUNCA a URL: a URL assinada muda a
                // cada abertura da aula, e endereçar por ela faria o player não achar nada do que
                // já está no disco. Ver `Media3Downloads`.
                media.offlineId?.let { setCustomCacheKey(it) }
            }
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(media.title)
                    .setArtist(media.artist)
                    .build(),
            )
            .build()

        val inicio = media.startPositionMillis.coerceAtLeast(0L)
        if (media.offlineId != null) {
            // **Tocar o baixado é o MESMO player, mudando só a fonte.** A origem passa a ser o
            // cache do `kmplib-video.download`, em modo somente-leitura: o vídeo abre sem rede, e o
            // que porventura falte cai para a URL (`FLAG_IGNORE_CACHE_ON_ERROR`).
            //
            // O custo — abrir o `SimpleCache`, que indexa o diretório — é pago só aqui, quando o
            // app pediu explicitamente a cópia baixada. Quem só faz streaming nunca passa por esta
            // linha.
            val fonte = DefaultMediaSourceFactory(Media3Downloads.playbackDataSourceFactory(context))
                .createMediaSource(item)
            player.setMediaSource(fonte, inicio)
        } else {
            player.setMediaItem(item, inicio)
        }
        player.playWhenReady = config.autoPlay
        player.prepare()
        sincronizar()
    }

    override fun play() {
        if (status == VideoStatus.Ended) player.seekTo(0)
        player.play()
    }

    override fun pause() = player.pause()

    override fun seekTo(millis: Long) {
        player.seekTo(seekTargetOf(0L, millis, durationMillis))
        refreshProgress()
    }

    override fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
        updateSpeed(speed)
    }

    override fun selectSubtitle(option: VideoSubtitleOption?) {
        applySelectedSubtitle(option)
        val base = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT)
        if (option == null || !option.embedded) {
            // Externa (ou nenhuma): a plataforma não desenha nada — quem desenha é o overlay do
            // `VideoPlayer`, com as falas do arquivo. Deixar a embutida ligada daria legenda dupla.
            player.trackSelectionParameters = base.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            return
        }
        val grupo = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT }
            .getOrNull(option.id.removePrefix(TEXT_TRACK_ID_PREFIX).toIntOrNull() ?: -1)
        player.trackSelectionParameters = base
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .apply {
                if (grupo != null) {
                    setOverrideForType(TrackSelectionOverride(grupo.mediaTrackGroup, 0))
                }
            }
            .build()
    }

    override fun release() {
        session?.release()
        session = null
        player.removeListener(listener)
        player.release()
        status = VideoStatus.Idle
    }

    override fun refreshProgress() {
        positionMillis = player.currentPosition.coerceAtLeast(0L)
        bufferedMillis = player.bufferedPosition.coerceAtLeast(0L)
        val duracao = player.duration
        // `C.TIME_UNSET` é Long.MIN_VALUE: publicado cru, a barra recebe uma fração absurda e some.
        durationMillis = if (duracao == C.TIME_UNSET || duracao < 0) 0L else duracao
    }

    private fun sincronizar() {
        refreshProgress()
        updateSpeed(player.playbackParameters.speed)
        if (player.playbackState == Player.STATE_ENDED) {
            status = VideoStatus.Ended
            return
        }
        if (status is VideoStatus.Error && player.playerError != null) return
        status = videoStatusOf(
            preparado = player.playbackState == Player.STATE_READY ||
                player.playbackState == Player.STATE_BUFFERING && durationMillis > 0,
            querTocar = player.playWhenReady,
            semDados = player.playbackState == Player.STATE_BUFFERING,
        )
    }

    private fun aplicarPreferenciaInicialDeLegenda() {
        if (preferenciaAplicada) return
        if (subtitleOptions.isEmpty()) return
        preferenciaAplicada = true
        preferredSubtitleOf(subtitleOptions, config.preferredSubtitleLanguage)
            ?.let { selectSubtitle(it) }
            ?: selectSubtitle(null)
    }
}

/**
 * As faixas de texto que o manifesto trouxe, no vocabulário neutro da lib.
 *
 * O **id é a posição** (`text-0`, `text-1`), e não o `TrackGroup.id`: em HLS esse campo vem vazio
 * com frequência, e duas faixas com id `""` seriam a mesma opção no menu — escolher "English"
 * ligaria "Português". Ver o `selectSubtitle`, que resolve pela mesma posição.
 *
 * Faixa que o aparelho não consegue decodificar fica **de fora**: oferecê-la é prometer uma legenda
 * que não vai aparecer.
 */
@OptIn(UnstableApi::class)
private fun Tracks.legendasEmbutidas(): List<VideoSubtitleOption> =
    groups.filter { it.type == C.TRACK_TYPE_TEXT }
        .mapIndexedNotNull { indice, grupo ->
            if (!grupo.isSupported) return@mapIndexedNotNull null
            val formato = grupo.mediaTrackGroup.getFormat(0)
            val idioma = formato.language ?: return@mapIndexedNotNull null
            VideoSubtitleOption(
                id = "$TEXT_TRACK_ID_PREFIX$indice",
                label = formato.label ?: idioma,
                language = idioma,
                embedded = true,
            )
        }

/** Prefixo do id das faixas embutidas — ver [legendasEmbutidas]. */
private const val TEXT_TRACK_ID_PREFIX = "text-"

@OptIn(UnstableApi::class)
private fun PlaybackException.paraVideoErrorKind(): VideoErrorKind {
    (cause as? HttpDataSource.InvalidResponseCodeException)?.let {
        return videoErrorKindForHttpStatus(it.responseCode)
    }
    return when (errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        -> VideoErrorKind.Network

        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> VideoErrorKind.NotFound

        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        -> VideoErrorKind.Unsupported

        else -> VideoErrorKind.Unknown
    }
}

@OptIn(UnstableApi::class)
actual fun createVideoPlayerState(
    config: VideoPlayerConfig,
    texts: VideoPlayerTexts,
): VideoPlayerState {
    val context = VideoPlayerHolder.getContext()
        ?: error(
            "kmplib-video: chame initKmpLibVideo(context) no Application.onCreate() " +
                "(ou KmpLib.init(context), se usa o artefato umbrella).",
        )
    return ExoPlayerVideoPlayerState(context, config, texts)
}

/** Acesso interno ao ExoPlayer, para a [VideoSurface] ligar o `PlayerView`. */
@OptIn(UnstableApi::class)
internal fun VideoPlayerState.exoPlayerOrNull(): ExoPlayer? =
    (this as? ExoPlayerVideoPlayerState)?.player

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:no-wildcard-imports")

package br.com.codecacto.kmplib.video

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.CValue
// Interop ObjC: os membros de uma @interface viram MEMBROS e os de uma categoria viram EXTENSÕES
// de nível superior. `AVPlayer.play()` e `AVPlayerItem.status` estão em lados diferentes dessa
// linha, e errar qual é qual só aparece no Mac. Importar o pacote resolve os dois casos.
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.Foundation.*
import platform.MediaPlayer.*
import platform.darwin.NSObjectProtocol

/**
 * **AVPlayer (AVFoundation)** — o player da Apple, e o único caminho oficial para HLS no iOS.
 *
 * Três decisões que valem registro:
 *
 * - **`AVPlayerLayer`, não `AVPlayerViewController`.** A AVKit traria os controles dela por cima de
 *   tudo — inclusive da marca d'água do app. A camada é a forma que a Apple indica para embutir
 *   vídeo numa hierarquia de views própria. Ver [VideoSurface].
 * - **`AVAudioSession` em `.playback`.** Sem isto o vídeo fica **mudo com o interruptor de
 *   silencioso ligado** (o default do iOS é `ambient`), e o aluno conclui que a aula não tem áudio.
 * - **O estado se LÊ, não é empurrado.** O AVPlayer não tem `Player.Listener`: `status`,
 *   `timeControlStatus` e a duração são propriedades. Por isso [refreshProgress] carrega o que no
 *   Android é callback, e por isso o relógio da composição gira também em [VideoStatus.Loading]
 *   (ver `VideoPlayerState.needsProgressTicker`).
 */
private class AvPlayerVideoPlayerState(
    config: VideoPlayerConfig,
    texts: VideoPlayerTexts,
) : VideoPlayerState(config, texts) {

    val player: AVPlayer = AVPlayer()

    /** A velocidade pedida. O AVPlayer não a guarda parado: `rate = 0` **é** a pausa. */
    private var rateDesejado: Float = config.initialSpeed

    private var querTocar: Boolean = false
    private var observadorDeFim: NSObjectProtocol? = null
    private var grupoDeLegendas: AVMediaSelectionGroup? = null
    private var preferenciaAplicada = false

    init {
        configurarSessaoDeAudio()
        if (config.mediaSession) registrarComandosRemotos()
    }

    override fun load(media: VideoMedia) {
        this.media = media
        preferenciaAplicada = false
        grupoDeLegendas = null
        externalCues = emptyList()
        applySelectedSubtitle(null)
        status = VideoStatus.Loading

        val url = NSURL.URLWithString(media.url)
        if (url == null) {
            AppLogger.e(VIDEO_TAG, "URL de vídeo inválida: ${media.url}")
            status = VideoStatus.Error(
                kind = VideoErrorKind.Unknown,
                message = texts.messageFor(VideoErrorKind.Unknown),
                cause = "URL inválida",
            )
            return
        }

        removerObservadorDeFim()
        val item = AVPlayerItem(uRL = url)
        player.replaceCurrentItemWithPlayerItem(item)

        if (media.startPositionMillis > 0) {
            player.seekToTime(cmTime(media.startPositionMillis))
        }

        observadorDeFim = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            querTocar = false
            status = VideoStatus.Ended
            publicarNaCentralDeMidia()
        }

        querTocar = config.autoPlay
        if (config.autoPlay) player.setRate(rateDesejado)
        refreshProgress()
    }

    override fun play() {
        if (status == VideoStatus.Ended) player.seekToTime(cmTime(0))
        querTocar = true
        // `setRate` toca E aplica a velocidade. `play()` puro forçaria 1×, perdendo a escolha do aluno.
        player.setRate(rateDesejado)
        refreshProgress()
    }

    override fun pause() {
        querTocar = false
        player.pause()
        refreshProgress()
    }

    override fun seekTo(millis: Long) {
        val alvo = seekTargetOf(0L, millis, durationMillis)
        player.seekToTime(cmTime(alvo))
        positionMillis = alvo
        publicarNaCentralDeMidia()
    }

    override fun setSpeed(speed: Float) {
        rateDesejado = speed
        updateSpeed(speed)
        // Só mexe no `rate` se já está andando: mudar a velocidade de um vídeo pausado o faria tocar.
        if (querTocar) player.setRate(speed)
        publicarNaCentralDeMidia()
    }

    override fun selectSubtitle(option: VideoSubtitleOption?) {
        applySelectedSubtitle(option)
        val item = player.currentItem ?: return
        val grupo = grupoDeLegendas ?: return
        val escolhida: AVMediaSelectionOption? = if (option == null || !option.embedded) {
            // Externa (ou nenhuma): a plataforma não desenha nada — quem desenha é o overlay do
            // `VideoPlayer`, com as falas do arquivo. Senão sairia legenda dobrada.
            null
        } else {
            val indice = option.id.removePrefix(TEXT_TRACK_ID_PREFIX).toIntOrNull() ?: -1
            grupo.options.getOrNull(indice) as? AVMediaSelectionOption
        }
        item.selectMediaOption(escolhida, grupo)
    }

    override fun release() {
        removerObservadorDeFim()
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        limparCentralDeMidia()
        status = VideoStatus.Idle
    }

    override fun refreshProgress() {
        val item = player.currentItem
        if (item == null) {
            positionMillis = 0
            durationMillis = 0
            return
        }

        positionMillis = player.currentTime().paraMillis()
        durationMillis = item.duration.paraMillis()
        bufferedMillis = positionMillis

        if (item.status == AVPlayerItemStatusFailed) {
            publicarFalha(item)
            return
        }
        if (status == VideoStatus.Ended) return

        val preparado = item.status == AVPlayerItemStatusReadyToPlay
        if (preparado) descobrirLegendasEmbutidas(item)

        status = videoStatusOf(
            preparado = preparado,
            querTocar = querTocar,
            // `WaitingToPlayAtSpecifiedRate` é exatamente "quer andar e está sem dado" — o
            // equivalente do `STATE_BUFFERING` do ExoPlayer. Não se deduz do `rate`, que é 0 tanto
            // no buffering quanto na pausa.
            semDados = player.timeControlStatus ==
                AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate,
        )
        updateSpeed(if (querTocar && player.rate > 0f) player.rate else rateDesejado)
        publicarNaCentralDeMidia()
    }

    // -------------------------------------------------------------------------------- legendas

    /**
     * Lê as faixas de texto do manifesto, **uma vez por mídia**.
     *
     * É a variante síncrona de `mediaSelectionGroupForMediaCharacteristic:`, e de propósito: a
     * Apple a depreciou por causa do I/O bloqueante em asset ainda não carregado, e aqui ela só é
     * chamada **depois** de `AVPlayerItemStatusReadyToPlay` — quando o grupo já está em memória e
     * não há nada a buscar.
     */
    @Suppress("DEPRECATION")
    private fun descobrirLegendasEmbutidas(item: AVPlayerItem) {
        if (preferenciaAplicada) return

        val grupo = runCatching {
            item.asset.mediaSelectionGroupForMediaCharacteristic(AVMediaCharacteristicLegible)
        }.getOrNull()

        val opcoes = grupo?.options.orEmpty().mapIndexedNotNull { indice, bruta ->
            val opcao = bruta as? AVMediaSelectionOption ?: return@mapIndexedNotNull null
            val idioma = opcao.extendedLanguageTag ?: return@mapIndexedNotNull null
            VideoSubtitleOption(
                id = "$TEXT_TRACK_ID_PREFIX$indice",
                label = opcao.displayName,
                language = idioma,
                embedded = true,
            )
        }

        grupoDeLegendas = grupo
        // Mesmo sem faixa embutida o menu tem de existir: as externas do app entram aqui.
        updateEmbeddedSubtitles(opcoes)
        preferenciaAplicada = true
        selectSubtitle(preferredSubtitleOf(subtitleOptions, config.preferredSubtitleLanguage))
    }

    // ----------------------------------------------------------------------------------- erros

    /**
     * Traduz a falha do item.
     *
     * O `errorLog` é o caminho oficial para o **status HTTP**: o `NSError` do item diz só que a
     * operação falhou (`AVFoundationErrorDomain -11800`), e é no último evento do log que está o
     * `403` da URL assinada vencida — o erro mais comum de um curso, e o único cuja saída é
     * diferente ("abra a aula de novo", não "verifique a conexão").
     */
    private fun publicarFalha(item: AVPlayerItem) {
        if (status is VideoStatus.Error) return
        val http = runCatching {
            (item.errorLog()?.events?.lastOrNull() as? AVPlayerItemErrorLogEvent)
                ?.errorStatusCode
                ?.toInt()
        }.getOrNull() ?: 0
        val kind = if (http > 0) videoErrorKindForHttpStatus(http) else VideoErrorKind.Network
        val detalhe = item.error?.localizedDescription ?: "AVPlayerItemStatusFailed"
        AppLogger.e(VIDEO_TAG, "Falha na reprodução (HTTP $http): $detalhe")
        status = VideoStatus.Error(kind = kind, message = texts.messageFor(kind), cause = detalhe)
    }

    // -------------------------------------------------------------------------- sessão de mídia

    /**
     * `.playback` é o que faz o áudio sair com o **interruptor de silencioso ligado** e o que
     * permite continuar em segundo plano — nesse caso o APP ainda precisa da capability
     * *Background Modes → Audio*, que a lib não tem como declarar por ele.
     */
    private fun configurarSessaoDeAudio() {
        try {
            val sessao = AVAudioSession.sharedInstance()
            sessao.setCategory(AVAudioSessionCategoryPlayback, error = null)
            sessao.setActive(true, error = null)
        } catch (e: Exception) {
            AppLogger.w(VIDEO_TAG, "AVAudioSession não configurada: ${e.message}")
        }
    }

    /**
     * Os comandos da tela de bloqueio, do fone, do relógio e do CarPlay.
     *
     * ⚠️ `MPRemoteCommandHandlerStatusSuccess` é **constante de topo** — `MPRemoteCommandHandlerStatus`
     * é só um `typealias` de `NSInteger`, não um enum class, então não se qualifica por ele.
     * Ver `references/ios-cinterop.md` na skill `kmplib-catalog`.
     */
    private fun registrarComandosRemotos() {
        val centro = MPRemoteCommandCenter.sharedCommandCenter()
        centro.playCommand.addTargetWithHandler {
            play()
            MPRemoteCommandHandlerStatusSuccess
        }
        centro.pauseCommand.addTargetWithHandler {
            pause()
            MPRemoteCommandHandlerStatusSuccess
        }
        centro.togglePlayPauseCommand.addTargetWithHandler {
            playPause()
            MPRemoteCommandHandlerStatusSuccess
        }
        centro.skipForwardCommand.addTargetWithHandler {
            seekBy(config.seekStepMillis)
            MPRemoteCommandHandlerStatusSuccess
        }
        centro.skipBackwardCommand.addTargetWithHandler {
            seekBy(-config.seekStepMillis)
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    private fun publicarNaCentralDeMidia() {
        if (!config.mediaSession) return
        val atual = media ?: return
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = mapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to (atual.title ?: ""),
            MPMediaItemPropertyArtist to (atual.artist ?: ""),
            MPMediaItemPropertyPlaybackDuration to (durationMillis / 1000.0),
            MPNowPlayingInfoPropertyElapsedPlaybackTime to (positionMillis / 1000.0),
            MPNowPlayingInfoPropertyPlaybackRate to (if (isPlaying) speed.toDouble() else 0.0),
        )
    }

    private fun limparCentralDeMidia() {
        if (!config.mediaSession) return
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    private fun removerObservadorDeFim() {
        observadorDeFim?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observadorDeFim = null
    }
}

/** Prefixo do id das faixas embutidas — a posição no grupo de seleção, ver `selectSubtitle`. */
private const val TEXT_TRACK_ID_PREFIX = "text-"

/** Escala de tempo dos `CMTime` construídos aqui: milissegundo, a unidade da API pública. */
private const val TIMESCALE = 1000

private fun cmTime(millis: Long): CValue<CMTime> =
    CMTimeMakeWithSeconds(millis / 1000.0, TIMESCALE)

/**
 * `CMTime` → milissegundos. `NaN`/negativo — a duração indefinida de um HLS que ainda não abriu, ou
 * de uma transmissão ao vivo — vira `0`, que é o que a barra de progresso sabe desenhar.
 */
private fun CValue<CMTime>.paraMillis(): Long {
    val segundos = CMTimeGetSeconds(this)
    if (segundos.isNaN() || segundos < 0) return 0
    return (segundos * 1000).toLong()
}

actual fun createVideoPlayerState(
    config: VideoPlayerConfig,
    texts: VideoPlayerTexts,
): VideoPlayerState = AvPlayerVideoPlayerState(config, texts)

/** Acesso interno ao AVPlayer, para a [VideoSurface] ligar a `AVPlayerLayer`. */
internal fun VideoPlayerState.avPlayerOrNull(): AVPlayer? =
    (this as? AvPlayerVideoPlayerState)?.player

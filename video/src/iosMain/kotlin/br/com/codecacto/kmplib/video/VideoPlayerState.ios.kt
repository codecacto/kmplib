@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:no-wildcard-imports")

package br.com.codecacto.kmplib.video

import br.com.codecacto.kmplib.core.util.redactMediaUrlsIn
import br.com.codecacto.kmplib.core.util.redactMediaUrl
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.CValue
import kotlinx.cinterop.readValue
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
 *   **Mudo, `.ambient`** (2.284.0): mistura com os outros apps, então a música da pessoa não para
 *   por uma demonstração calada. Ver [VideoAudioSession].
 * - **Laço = `AVPlayerLooper` sobre `AVQueuePlayer`** (2.284.0). É a forma que a Apple indica para
 *   repetir sem emenda: o looper mantém réplicas do item na fila, e a próxima volta já está
 *   carregada quando a atual termina. "Voltar ao zero no fim" deixaria um quadro preto e um soluço
 *   de áudio entre as voltas. (O vídeo de FEED faz laço manual por outro motivo — o looper ignora
 *   `preferredForwardBufferDuration`, que o feed precisa; aqui não há pré-carregamento a controlar.)
 * - **O estado se LÊ, não é empurrado.** O AVPlayer não tem `Player.Listener`: `status`,
 *   `timeControlStatus` e a duração são propriedades. Por isso [refreshProgress] carrega o que no
 *   Android é callback, e por isso o relógio da composição gira também em [VideoStatus.Loading]
 *   (ver `VideoPlayerState.needsProgressTicker`).
 */
private class AvPlayerVideoPlayerState(
    config: VideoPlayerConfig,
    texts: VideoPlayerTexts,
) : VideoPlayerState(config, texts) {

    /** `AVQueuePlayer` (subclasse de `AVPlayer`) só no laço — é a fila que o `AVPlayerLooper` gere. */
    val player: AVPlayer = if (config.loop) AVQueuePlayer() else AVPlayer()

    /** O laço em vigor, ou `null` fora de [VideoPlayerConfig.loop]. Precisa ser retido: solto, o laço para. */
    private var looper: AVPlayerLooper? = null

    /** A réplica do laço em que a legenda foi aplicada — cada volta é um `AVPlayerItem` novo. */
    private var itemDaLegenda: AVPlayerItem? = null

    /** A velocidade pedida. O AVPlayer não a guarda parado: `rate = 0` **é** a pausa. */
    private var rateDesejado: Float = config.initialSpeed

    private var querTocar: Boolean = false
    private var observadorDeFim: NSObjectProtocol? = null
    private var grupoDeLegendas: AVMediaSelectionGroup? = null
    private var preferenciaAplicada = false

    init {
        player.muted = config.startMuted
        VideoAudioSession.attach(this)
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
            AppLogger.e(VIDEO_TAG, "URL de vídeo inválida: ${redactMediaUrl(media.url)}")
            status = VideoStatus.Error(
                kind = VideoErrorKind.Unknown,
                message = texts.messageFor(VideoErrorKind.Unknown),
                cause = "URL inválida",
            )
            return
        }

        removerObservadorDeFim()
        val item = AVPlayerItem(uRL = url)
        val fila = player as? AVQueuePlayer
        if (fila != null) {
            // Laço: o item é só o MODELO; quem toca são as réplicas que o looper põe na fila.
            // Nunca chega ao fim, então não há observador de fim.
            pararLaco()
            itemDaLegenda = null
            // `kCMTimeRangeInvalid` = o item inteiro (é o que o `playerLooperWithPlayer:templateItem:`
            // da Apple passa por baixo; o K/N só expõe o inicializador completo).
            looper = AVPlayerLooper(player = fila, templateItem = item, timeRange = kCMTimeRangeInvalid.readValue())
        } else {
            player.replaceCurrentItemWithPlayerItem(item)
            observadorDeFim = NSNotificationCenter.defaultCenter.addObserverForName(
                name = AVPlayerItemDidPlayToEndTimeNotification,
                `object` = item,
                queue = NSOperationQueue.mainQueue,
            ) { _ ->
                querTocar = false
                status = VideoStatus.Ended
                publicarNaCentralDeMidia()
            }
        }

        if (media.startPositionMillis > 0) {
            player.seekToTime(cmTime(media.startPositionMillis))
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

    override fun setMuted(muted: Boolean) {
        if (muted == isMuted) return
        player.muted = muted
        updateMuted(muted)
        // Ligar o som troca a sessão para `.playback` (a música de fundo para); desligar devolve
        // `.ambient` quando nenhum outro player do processo tem som.
        VideoAudioSession.update()
    }

    override fun selectSubtitle(option: VideoSubtitleOption?) {
        applySelectedSubtitle(option)
        val item = player.currentItem ?: return
        itemDaLegenda = item
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
        pararLaco()
        player.replaceCurrentItemWithPlayerItem(null)
        limparCentralDeMidia()
        VideoAudioSession.detach(this)
        status = VideoStatus.Idle
    }

    /** Desliga o laço e esvazia a fila — antes de trocar de mídia e ao soltar o player. */
    private fun pararLaco() {
        looper?.disableLooping()
        looper = null
        (player as? AVQueuePlayer)?.removeAllItems()
    }

    override fun refreshProgress() {
        // O looper falha antes de haver réplica na fila (asset ilegível): sem isto o status
        // ficaria em "carregando" para sempre, porque não há `currentItem` para ler.
        looper?.let { laco ->
            if (laco.status == AVPlayerLooperStatusFailed && status !is VideoStatus.Error) {
                val detalhe = laco.error?.localizedDescription ?: "AVPlayerLooperStatusFailed"
                AppLogger.e(VIDEO_TAG, "Falha no laço de reprodução: ${redactMediaUrlsIn(detalhe)}")
                status = VideoStatus.Error(
                    kind = VideoErrorKind.Unknown,
                    message = texts.messageFor(VideoErrorKind.Unknown),
                    cause = redactMediaUrlsIn(detalhe),
                )
                return
            }
        }
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
        // Cada volta do laço é uma réplica nova: a legenda escolhida é reaplicada nela.
        if (preparado && looper != null && preferenciaAplicada && itemDaLegenda !== item) {
            selectSubtitle(selectedSubtitle)
        }

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
        AppLogger.e(VIDEO_TAG, "Falha na reprodução (HTTP $http): ${redactMediaUrlsIn(detalhe)}")
        status = VideoStatus.Error(kind = kind, message = texts.messageFor(kind), cause = redactMediaUrlsIn(detalhe))
    }

    // -------------------------------------------------------------------------- sessão de mídia

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

/**
 * A **sessão de áudio** dos players de aula — uma só por processo, porque a `AVAudioSession` é uma só.
 *
 * - Algum player **com som** → `.playback`, ativa. É o que faz o áudio sair com o **interruptor de
 *   silencioso ligado** e o que permite continuar em segundo plano ([VideoBackgroundBehavior.ContinueAudio]
 *   — o APP ainda precisa da capability *Background Modes → Audio*, que a lib não declara por ele).
 * - Todos **mudos** → `.ambient`: mistura com os outros apps, e a música que a pessoa ouve
 *   **continua** enquanto o vídeo de demonstração passa calado. O `.soloAmbient` (default do iOS)
 *   e o `.playback` a interromperiam no primeiro quadro. Não se ativa a sessão à mão: o AVPlayer a
 *   ativa ao tocar, e ativar `.ambient` não muda nada para quem está ouvindo.
 * - Ao sair o **último** player, a categoria que o app tinha antes é restaurada (até a 2.283.0 o
 *   player deixava `.playback` para sempre).
 *
 * Mesma decisão do vídeo de feed (`FeedAudioSession`), que tem a sua própria: cada um restaura a
 * categoria que encontrou.
 */
private object VideoAudioSession {

    private val players = mutableListOf<AvPlayerVideoPlayerState>()
    private var categoriaAnterior: String? = null

    /** `true` = `.playback` aplicado, `false` = `.ambient`, `null` = nada aplicado ainda. */
    private var comSomAplicado: Boolean? = null

    fun attach(player: AvPlayerVideoPlayerState) {
        if (players.isEmpty()) categoriaAnterior = AVAudioSession.sharedInstance().category
        players += player
        update()
    }

    fun detach(player: AvPlayerVideoPlayerState) {
        if (!players.remove(player)) return
        if (players.isNotEmpty()) {
            update()
            return
        }
        val anterior = categoriaAnterior ?: AVAudioSessionCategorySoloAmbient
        runCatching { AVAudioSession.sharedInstance().setCategory(anterior, error = null) }
        categoriaAnterior = null
        comSomAplicado = null
    }

    fun update() {
        val comSom = players.any { !it.isMuted }
        if (comSomAplicado == comSom) return
        comSomAplicado = comSom
        try {
            val sessao = AVAudioSession.sharedInstance()
            if (comSom) {
                sessao.setCategory(AVAudioSessionCategoryPlayback, error = null)
                sessao.setActive(true, error = null)
            } else {
                sessao.setCategory(AVAudioSessionCategoryAmbient, error = null)
            }
        } catch (e: Exception) {
            AppLogger.w(VIDEO_TAG, "AVAudioSession não configurada: ${redactMediaUrlsIn(e.message)}")
        }
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

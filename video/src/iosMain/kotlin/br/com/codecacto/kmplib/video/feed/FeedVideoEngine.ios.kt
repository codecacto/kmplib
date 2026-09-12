@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:no-wildcard-imports")

package br.com.codecacto.kmplib.video.feed

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.video.VideoErrorKind
import br.com.codecacto.kmplib.video.VideoStatus
import br.com.codecacto.kmplib.video.VideoStreamKind
import br.com.codecacto.kmplib.video.videoErrorKindForHttpStatus
import br.com.codecacto.kmplib.video.videoStatusOf
// Interop ObjC: membros de @interface viram membros, os de categoria viram extensões de topo — e
// `AVPlayer.muted`/`preventsDisplaySleepDuringVideoPlayback` estão do lado das extensões. Importar
// o pacote cobre os dois casos (ver `references/ios-cinterop.md`).
import platform.AVFAudio.*
import platform.AVFoundation.*
import platform.CoreMedia.*
import platform.Foundation.*
import platform.darwin.NSObjectProtocol

/**
 * Um `AVPlayer` do pool do feed.
 *
 * ### A camada nasce ANTES do item — e isto não é detalhe de organização
 * O [playerLayer] é criado **aqui**, no construtor, já apontando para o player. Motivo, da própria
 * Apple (WWDC 2016, *Advances in AVFoundation Playback*): quando um `AVPlayerItem` vira
 * `currentItem` de um player que **não tem camada anexada**, a AVFoundation monta o pipeline
 * **só de áudio** — e depois tem de reconfigurá-lo quando a camada aparece. No feed isso acontecia
 * em todo item: o controller manda `load()` num `SideEffect`, que roda **antes** de a superfície
 * compor. Custo de fazer certo: zero. Ganho: o primeiro quadro sai na primeira tentativa.
 *
 * A superfície (`FeedVideoSurface.ios.kt`) apenas **adota** esta camada; ela não cria nenhuma.
 *
 * ### Laço manual, e não `AVPlayerLooper` (2.197.0)
 * O `AVPlayerLooper` faz o laço sem emenda, mas **ignora `preferredForwardBufferDuration` e
 * `preferredMaximumResolution`** — ele gerencia a fila por dentro, com réplicas do item que não
 * recebem a nossa configuração. Como o pré-carregamento do iOS é justamente
 * `preferredForwardBufferDuration` (não existe `PreloadManager` na AVFoundation), manter o looper
 * seria manter um parâmetro público que não faz nada. Há ainda o bug conhecido de `seek` em HLS
 * com looper — e o feed vai para HLS quando migrarmos para o Bunny Stream.
 *
 * O laço passa a ser `AVPlayerItemDidPlayToEndTime` + `seek(.zero)`, com
 * `actionAtItemEnd = .none` para o player não se pausar no fim.
 *
 * ### O resto
 * - **`muted` do próprio AVPlayer** para o mudo — não volume zero —, e a sessão de áudio decidida
 *   pelo [FeedAudioSession] (ver lá a escolha de categoria).
 * - **`preventsDisplaySleepDuringVideoPlayback = false`.** O default do AVPlayer é `true`: qualquer
 *   vídeo tocando, mesmo mudo, prende a tela acesa. Num feed em laço isso seria a tela acesa para
 *   sempre num celular largado. Quem segura a tela, e **só com som**, é o `KeepScreenOn` do
 *   [rememberFeedVideoController] — a mesma regra nas duas plataformas.
 * - **O estado se lê** ([refresh]): o AVPlayer não tem listener; `status` e `timeControlStatus` são
 *   propriedades, lidas pela varredura periódica do controller.
 */
internal class AvFeedVideoEngine(private val config: FeedVideoConfig) : FeedVideoEngine() {

    val player: AVPlayer = AVPlayer()

    /** A camada deste player. Criada junto com ele — ver o KDoc da classe. */
    val playerLayer: AVPlayerLayer = AVPlayerLayer()

    private var observadorDeFim: NSObjectProtocol? = null
    private var querTocar = false
    private var mudo: Boolean? = null
    private var kindEmVigor: VideoStreamKind = VideoStreamKind.Auto

    /** Este player está adiantando um vizinho (`true`) ou é o da vez? Nasce adiantando. */
    private var preparando = true

    /** O asset atual nasceu barrado em rede cara/limitada? Ver [opcoesDeRede]. */
    private var criadoRestrito = false

    /** `true` quando este player está com som — o [FeedAudioSession] decide a categoria por isso. */
    internal val comSom: Boolean get() = mudo == false

    init {
        // A camada ANTES de qualquer item virar `currentItem`. Ver o KDoc da classe.
        playerLayer.player = player
        // Sem isto o player se pausa ao chegar ao fim, e o laço ficaria com um solavanco.
        player.actionAtItemEnd = AVPlayerActionAtItemEndNone
        player.muted = true
        player.preventsDisplaySleepDuringVideoPlayback = false
        FeedAudioSession.attach(this)
        setMuted(true)
    }

    override fun load(url: String, kind: VideoStreamKind) {
        // `kind` não muda a construção aqui: o AVFoundation reconhece o HLS pelo conteúdo, não pela
        // extensão. Guardamos só para poder recarregar do jeito certo em [setPreloadMode].
        kindEmVigor = kind
        esvaziar()
        loadedUrl = url

        val endereco = NSURL.URLWithString(url)
        if (endereco == null) {
            AppLogger.w(FEED_TAG, "URL de vídeo de feed inválida: $url")
            status = VideoStatus.Error(VideoErrorKind.Unknown, FeedVideoTexts().playbackError, "URL inválida")
            return
        }

        criadoRestrito = restringirRede()
        val asset = AVURLAsset(uRL = endereco, options = opcoesDeRede())
        val item = AVPlayerItem(asset = asset)
        // O "pré-carregamento" possível no iOS: quem não tem a vez pede pouco à frente, e assim não
        // disputa banda com o vídeo que está na tela.
        item.preferredForwardBufferDuration = if (preparando) BUFFER_DE_PRELOAD_SEGUNDOS else BUFFER_AUTOMATICO

        player.replaceCurrentItemWithPlayerItem(item)
        observarFim(item)
        status = VideoStatus.Loading
        if (querTocar) player.play()
    }

    override fun play() {
        if (querTocar && player.rate > 0f) return
        querTocar = true
        player.play()
    }

    override fun pause() {
        if (!querTocar && player.rate == 0f) return
        querTocar = false
        player.pause()
    }

    override fun setMuted(muted: Boolean) {
        if (mudo == muted) return
        mudo = muted
        player.muted = muted
        FeedAudioSession.update()
    }

    /**
     * Quanto este player pode pedir à frente.
     *
     * Ao **ganhar a vez**, um item que nasceu barrado na rede cara é refeito: sem isso ele ficaria
     * esperando para sempre um Wi-Fi que talvez não venha, e o post apareceria travado na capa.
     */
    override fun setPreloadMode(preloading: Boolean) {
        if (preparando == preloading) return
        preparando = preloading
        player.currentItem?.preferredForwardBufferDuration =
            if (preloading) BUFFER_DE_PRELOAD_SEGUNDOS else BUFFER_AUTOMATICO
        if (!preloading && criadoRestrito) {
            loadedUrl?.let { load(it, kindEmVigor) }
        }
    }

    override fun clear() {
        querTocar = false
        player.pause()
        esvaziar()
        loadedUrl = null
        status = VideoStatus.Idle
    }

    override fun release() {
        clear()
        playerLayer.player = null
        playerLayer.removeFromSuperlayer()
        FeedAudioSession.detach(this)
    }

    override fun refresh() {
        if (loadedUrl == null) {
            status = VideoStatus.Idle
            return
        }
        if (status is VideoStatus.Error) return

        val item = player.currentItem
        if (item?.status == AVPlayerItemStatusFailed) {
            publicarFalha(item)
            return
        }
        status = videoStatusOf(
            preparado = item?.status == AVPlayerItemStatusReadyToPlay,
            // Pausado pelo SISTEMA (ligação, fone desconectado) não conta como "quer tocar": o
            // `querTocar` é nosso, o `timeControlStatus` é a verdade.
            querTocar = querTocar && player.timeControlStatus != AVPlayerTimeControlStatusPaused,
            semDados = player.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate,
        )
    }

    /** O sistema tomou o áudio. Só interessa se este player estava tocando COM som. */
    internal fun onSystemAudioLost() {
        if (comSom && querTocar) onAudioLost?.invoke()
    }

    /**
     * O laço: no fim do item, volta ao zero e segue.
     *
     * O observador é por **item** (`object = item`), e não global: com dois players vivos, um
     * observador de `object = null` faria o fim do vídeo de um reiniciar o outro.
     */
    private fun observarFim(item: AVPlayerItem) {
        observadorDeFim = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = item,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            player.seekToTime(CMTimeMake(value = 0, timescale = 1))
            if (querTocar) player.play()
        }
    }

    /**
     * **Rede cara/limitada barra o PRÉ-CARREGAMENTO** — nunca o vídeo que a pessoa está vendo.
     *
     * No iOS quem decide não somos nós: são as chaves `AVURLAssetAllowsExpensiveNetworkAccessKey`
     * (dados móveis) e `AVURLAssetAllowsConstrainedNetworkAccessKey` (Modo Dados Reduzidos, o
     * "Data Saver" da Apple), que o próprio sistema avalia. É o caminho recomendado — perguntar o
     * tipo de rede e decidir por conta própria erra em VPN, hotspot e roaming.
     */
    private fun restringirRede(): Boolean =
        preparando && config.preloadEnabled && !config.preloadOnMeteredNetwork

    private fun opcoesDeRede(): Map<Any?, Any?> = if (restringirRede()) {
        mapOf(
            AVURLAssetAllowsExpensiveNetworkAccessKey to false,
            AVURLAssetAllowsConstrainedNetworkAccessKey to false,
        )
    } else {
        emptyMap()
    }

    private fun esvaziar() {
        removerObservador()
        player.replaceCurrentItemWithPlayerItem(null)
    }

    private fun removerObservador() {
        observadorDeFim?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observadorDeFim = null
    }

    /**
     * O status HTTP sai do `errorLog` do item (o `NSError` diz só `-11800`) — é o que separa a URL
     * assinada vencida ([VideoErrorKind.Expired]) da falta de rede. Mesma leitura do player de aula.
     */
    private fun publicarFalha(item: AVPlayerItem?) {
        val http = item?.errorLog()?.events?.lastOrNull()
            ?.let { it as? AVPlayerItemErrorLogEvent }
            ?.errorStatusCode
            ?.toInt() ?: 0
        val kind = if (http > 0) videoErrorKindForHttpStatus(http) else VideoErrorKind.Network
        val detalhe = item?.error?.localizedDescription ?: "falha"
        AppLogger.w(FEED_TAG, "Vídeo de feed falhou (HTTP $http): $detalhe")
        status = VideoStatus.Error(kind, FeedVideoTexts().playbackError, detalhe)
    }

    private companion object {
        /** 1 s à frente em quem não tem a vez: o bastante para o primeiro quadro. */
        const val BUFFER_DE_PRELOAD_SEGUNDOS = 1.0

        /** `0` devolve a decisão ao AVPlayer — é o default da Apple, e o certo para quem toca. */
        const val BUFFER_AUTOMATICO = 0.0
    }
}

/**
 * A **sessão de áudio** do app enquanto há feed com vídeo — uma só por processo, porque a
 * `AVAudioSession` é uma só.
 *
 * ### A escolha de categoria (registrada, porque é a decisão que o usuário sente)
 * - **Mudo → `.ambient`.** É a categoria que a Apple descreve para som que "não é essencial" e que
 *   **mistura** com o áudio de outros apps: a música que a pessoa está ouvindo **continua** enquanto o
 *   feed passa vídeos calados. É o que Instagram e X fazem. O `.soloAmbient` (default do iOS)
 *   interromperia a música no primeiro vídeo que tocasse — mesmo mudo.
 * - **Com som → `.playback`, modo `moviePlayback`.** A pessoa **tocou** no alto-falante: o áudio
 *   passa a ser o conteúdo, e é o `.playback` que o faz sair **mesmo com o interruptor de silencioso
 *   ligado** e que interrompe a música de fundo (a Apple indica `.playback` para reprodução de vídeo
 *   iniciada pelo usuário; o modo `moviePlayback` é o do conteúdo audiovisual). Com o silencioso
 *   ligado o feed continua **nascendo mudo** — o som só sai por um toque explícito, que é a forma
 *   de respeitar o interruptor sem esconder o botão.
 * - **Ao sair o último feed**, a categoria que o app tinha antes é **restaurada**.
 *
 * Também escuta a **interrupção** (ligação, Siri) e a **perda de rota** (fone desconectado): com o
 * vídeo tocando com som, o sistema o pausa, e o feed responde voltando a mudo e seguindo — ver
 * [FeedVideoEngine.onAudioLost].
 */
private object FeedAudioSession {

    private val engines = mutableListOf<AvFeedVideoEngine>()
    private val observadores = mutableListOf<NSObjectProtocol>()
    private var categoriaAnterior: String? = null

    /** A última categoria aplicada: `true` = com som (`.playback`), `false` = `.ambient`, `null` = nenhuma. */
    private var comSomAplicado: Boolean? = null

    fun attach(engine: AvFeedVideoEngine) {
        if (engines.isEmpty()) {
            categoriaAnterior = AVAudioSession.sharedInstance().category
            observar()
        }
        engines += engine
    }

    fun detach(engine: AvFeedVideoEngine) {
        engines.remove(engine)
        if (engines.isNotEmpty()) {
            update()
            return
        }
        observadores.forEach { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observadores.clear()
        val anterior = categoriaAnterior ?: AVAudioSessionCategorySoloAmbient
        runCatching { AVAudioSession.sharedInstance().setCategory(anterior, error = null) }
        categoriaAnterior = null
        comSomAplicado = null
    }

    fun update() {
        val comSom = engines.any { it.comSom }
        if (comSomAplicado == comSom) return
        comSomAplicado = comSom
        val sessao = AVAudioSession.sharedInstance()
        try {
            if (comSom) {
                sessao.setCategory(
                    AVAudioSessionCategoryPlayback,
                    mode = AVAudioSessionModeMoviePlayback,
                    options = 0uL,
                    error = null,
                )
                sessao.setActive(true, error = null)
            } else {
                sessao.setCategory(AVAudioSessionCategoryAmbient, error = null)
            }
        } catch (e: Exception) {
            AppLogger.w(FEED_TAG, "AVAudioSession não configurada para o feed: ${e.message}")
        }
    }

    private fun observar() {
        val centro = NSNotificationCenter.defaultCenter
        observadores += centro.addObserverForName(
            name = AVAudioSessionInterruptionNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { nota ->
            val tipo = (nota?.userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedLongValue
            if (tipo == AVAudioSessionInterruptionTypeBegan) avisarPerda()
        }
        // A notificação de rota chega numa thread secundária; a fila principal a traz para cá.
        observadores += centro.addObserverForName(
            name = AVAudioSessionRouteChangeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { nota ->
            val motivo = (nota?.userInfo?.get(AVAudioSessionRouteChangeReasonKey) as? NSNumber)?.unsignedLongValue
            if (motivo == AVAudioSessionRouteChangeReasonOldDeviceUnavailable) avisarPerda()
        }
    }

    private fun avisarPerda() {
        engines.toList().forEach { it.onSystemAudioLost() }
    }
}

/**
 * No iOS não há `DefaultPreloadManager`: o adiantamento é feito pelos próprios players do pool, com
 * `preferredForwardBufferDuration` curto em quem não tem a vez (ver [AvFeedVideoEngine]).
 */
internal actual fun createFeedPreloader(config: FeedVideoConfig): FeedPreloader = NoFeedPreloader

internal actual fun createFeedVideoEngine(
    config: FeedVideoConfig,
    preloader: FeedPreloader,
): FeedVideoEngine = AvFeedVideoEngine(config)

internal const val FEED_TAG = "KmpLibFeedVideo"

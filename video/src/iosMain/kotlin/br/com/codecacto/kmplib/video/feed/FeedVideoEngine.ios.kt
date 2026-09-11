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
import platform.Foundation.*
import platform.darwin.NSObjectProtocol

/**
 * Um `AVQueuePlayer` + `AVPlayerLooper` do pool do feed.
 *
 * - **Laço pelo `AVPlayerLooper`**, que é o que a Apple indica para repetir sem emenda: ele mantém
 *   réplicas do item na fila e as troca antes do fim. O "voltar para o zero ao terminar"
 *   (`AVPlayerItemDidPlayToEndTime` + `seek`) deixa um soluço visível a cada volta.
 * - **`muted` do próprio AVPlayer** para o mudo — não volume zero —, e a sessão de áudio decidida pelo
 *   [FeedAudioSession] (ver lá a escolha de categoria).
 * - **`preventsDisplaySleepDuringVideoPlayback = false`.** O default do AVPlayer é `true`: qualquer
 *   vídeo tocando, mesmo mudo, prende a tela acesa. Num feed em laço isso seria a tela acesa para
 *   sempre num celular largado. Quem segura a tela, e **só com som**, é o `KeepScreenOn` do
 *   [rememberFeedVideoController] — a mesma regra nas duas plataformas.
 * - **O estado se lê** ([refresh]): o AVPlayer não tem listener; `status` e `timeControlStatus` são
 *   propriedades, lidas pela varredura periódica do controller.
 */
internal class AvFeedVideoEngine : FeedVideoEngine() {

    val player: AVQueuePlayer = AVQueuePlayer()

    private var looper: AVPlayerLooper? = null
    private var querTocar = false
    private var mudo: Boolean? = null

    /** `true` quando este player está com som — o [FeedAudioSession] decide a categoria por isso. */
    internal val comSom: Boolean get() = mudo == false

    init {
        player.muted = true
        player.preventsDisplaySleepDuringVideoPlayback = false
        FeedAudioSession.attach(this)
        setMuted(true)
    }

    override fun load(url: String, kind: VideoStreamKind) {
        // `kind` não se aplica aqui: o AVFoundation reconhece o HLS pelo conteúdo, não pela extensão.
        esvaziar()
        loadedUrl = url
        val endereco = NSURL.URLWithString(url)
        if (endereco == null) {
            AppLogger.w(FEED_TAG, "URL de vídeo de feed inválida: $url")
            status = VideoStatus.Error(VideoErrorKind.Unknown, FeedVideoTexts().playbackError, "URL inválida")
            return
        }
        val item = AVPlayerItem(uRL = endereco)
        looper = AVPlayerLooper.playerLooperWithPlayer(player = player, templateItem = item)
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

    override fun clear() {
        querTocar = false
        player.pause()
        esvaziar()
        loadedUrl = null
        status = VideoStatus.Idle
    }

    override fun release() {
        clear()
        FeedAudioSession.detach(this)
    }

    override fun refresh() {
        if (loadedUrl == null) {
            status = VideoStatus.Idle
            return
        }
        if (status is VideoStatus.Error) return

        val item = player.currentItem
        if (looper?.status == AVPlayerLooperStatusFailed || item?.status == AVPlayerItemStatusFailed) {
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

    private fun esvaziar() {
        looper?.disableLooping()
        looper = null
        player.removeAllItems()
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
        val detalhe = item?.error?.localizedDescription ?: looper?.error?.localizedDescription ?: "falha"
        AppLogger.w(FEED_TAG, "Vídeo de feed falhou (HTTP $http): $detalhe")
        status = VideoStatus.Error(kind, FeedVideoTexts().playbackError, detalhe)
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

internal actual fun createFeedVideoEngine(): FeedVideoEngine = AvFeedVideoEngine()

internal const val FEED_TAG = "KmpLibFeedVideo"

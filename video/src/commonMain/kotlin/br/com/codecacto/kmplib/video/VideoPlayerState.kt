package br.com.codecacto.kmplib.video

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.codecacto.kmplib.core.util.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText

/**
 * O player, do ponto de vista de quem desenha a tela — **sem uma linha de ExoPlayer ou AVPlayer**.
 *
 * Obtenha um com [rememberVideoPlayerState] e entregue-o ao [VideoPlayer]. Os campos são estado do
 * Compose: ler `positionMillis` dentro de um composable recompõe sozinho, sem `collectAsState`.
 *
 * ```kotlin
 * val player = rememberVideoPlayerState(
 *     media = VideoMedia(url = aula.hlsUrl, title = aula.titulo, startPositionMillis = aula.retomarEm),
 *     onPosition = { posicao, _ -> viewModel.salvarProgresso(posicao) },
 * )
 *
 * VideoPlayer(player, Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
 *     MarcaDagua(aluno.email)   // o slot é do app: a lib não decide o que vai escrito
 * }
 * ```
 *
 * ### Quem é dono do quê
 * A lib é dona do **player** e do **estado**; o app é dono do **conteúdo** — a marca d'água, a
 * decisão de retomar, o que fazer quando a URL assinada expira. É por isso que [VideoPlayer]
 * recebe um slot de sobreposição em vez de um parâmetro `watermarkText`: antipirataria é regra de
 * produto, e produto nenhum quer a moldura que a lib escolheria.
 */
@Stable
abstract class VideoPlayerState internal constructor(
    /** A configuração com que este player foi criado. */
    val config: VideoPlayerConfig,
    /** Os textos, para os `actual` traduzirem a falha do player numa frase de tela. */
    val texts: VideoPlayerTexts,
) {

    /** Em que ponto a reprodução está. Ver [VideoStatus]. */
    var status: VideoStatus by mutableStateOf(VideoStatus.Idle)
        protected set

    /** Posição atual, em milissegundos. */
    var positionMillis: Long by mutableStateOf(0L)
        protected set

    /** Duração total, ou `0` enquanto o manifesto não a informa. */
    var durationMillis: Long by mutableStateOf(0L)
        protected set

    /** Até onde o buffer já baixou — a barra clara atrás do progresso. */
    var bufferedMillis: Long by mutableStateOf(0L)
        protected set

    /**
     * A velocidade em vigor. Ver [VIDEO_SPEEDS].
     *
     * É `val` com apoio privado, e não `var ... protected set`, por uma razão da JVM: o setter de
     * uma propriedade `speed` compila como `setSpeed(F)V` e colidiria com a ação [setSpeed] — o
     * erro é *"platform declaration clash"* e só aparece no alvo Android. Quem escreve é
     * [updateSpeed].
     */
    val speed: Float get() = speedInterno

    private var speedInterno: Float by mutableStateOf(config.initialSpeed)

    /** Registra a velocidade que o player nativo confirmou. */
    protected fun updateSpeed(speed: Float) {
        speedInterno = speed
    }

    /**
     * As legendas que dá para escolher: as **embutidas** que a plataforma achou no manifesto, mais
     * as **externas** que o app declarou em [VideoMedia.subtitles]. Ver [mergeSubtitleOptions].
     */
    var subtitleOptions: List<VideoSubtitleOption> by mutableStateOf(emptyList())
        protected set

    /** A legenda ligada, ou `null` para nenhuma. */
    var selectedSubtitle: VideoSubtitleOption? by mutableStateOf(null)
        protected set

    /** A mídia carregada, ou `null` antes do primeiro [load]. */
    var media: VideoMedia? by mutableStateOf(null)
        protected set

    /**
     * A fala a desenhar **agora**, quando a legenda escolhida é um arquivo externo.
     *
     * Legenda embutida no HLS não passa por aqui: quem a desenha é a plataforma. Ver
     * [parseSubtitles] para o porquê de a externa ser interpretada pela lib.
     */
    val currentCue: SubtitleCue?
        get() = if (externalCues.isEmpty()) null else cueAt(externalCues, positionMillis)

    /** `true` quando o relógio anda ou quer andar — é o que decide o ícone do botão central. */
    val isPlaying: Boolean
        get() = status == VideoStatus.Playing || status == VideoStatus.Buffering

    /** As falas do arquivo externo em vigor. Vazia quando a legenda é embutida ou está desligada. */
    protected var externalCues: List<SubtitleCue> by mutableStateOf(emptyList())

    /** Cache das legendas externas já baixadas, por URL — trocar de faixa não rebaixa. */
    private val cuesPorFaixa = mutableMapOf<String, List<SubtitleCue>>()

    // ---------------------------------------------------------------------------------------
    // Ações
    // ---------------------------------------------------------------------------------------

    /**
     * Troca a mídia. Libera a anterior, respeita [VideoMedia.startPositionMillis] e dá play se
     * [VideoPlayerConfig.autoPlay].
     */
    abstract fun load(media: VideoMedia)

    /** Toca. Em [VideoStatus.Ended], recomeça do zero. */
    abstract fun play()

    /** Pausa. */
    abstract fun pause()

    /** Pausa se estiver tocando, toca se não. É o que o botão central faz. */
    fun playPause() {
        if (isPlaying) pause() else play()
    }

    /** Vai para [millis], grampeado entre `0` e a duração. */
    abstract fun seekTo(millis: Long)

    /**
     * Anda [deltaMillis] a partir de onde está — negativo volta.
     *
     * Os botões de ±10 s chamam `seekBy(config.seekStepMillis)` e o negativo dele; a conta e os
     * limites são do [seekTargetOf], que é comum às duas plataformas.
     */
    fun seekBy(deltaMillis: Long) {
        seekTo(seekTargetOf(positionMillis, deltaMillis, durationMillis))
    }

    /** Muda a velocidade. Valor fora de [VIDEO_SPEEDS] é aceito, mas o menu só oferece os seis. */
    abstract fun setSpeed(speed: Float)

    /** Liga a legenda [option], ou desliga com `null`. */
    abstract fun selectSubtitle(option: VideoSubtitleOption?)

    /**
     * Recarrega a mídia atual **na posição atual** — é o botão "tentar de novo" da tela de erro.
     *
     * Para URL assinada vencida ([VideoErrorKind.Expired]) isto não basta e não deveria bastar: o
     * app precisa pedir uma URL nova ao servidor e chamar [load] com ela. É por isso que o tipo do
     * erro é público.
     */
    open fun retry() {
        val atual = media ?: return
        load(atual.copy(startPositionMillis = positionMillis))
    }

    /** Solta o player e os recursos nativos. Chamado sozinho ao sair da tela. */
    abstract fun release()

    // ---------------------------------------------------------------------------------------
    // Ganchos internos — usados pelos `actual` e pelo relógio do `rememberVideoPlayerState`
    // ---------------------------------------------------------------------------------------

    /** Lê posição e buffer do player nativo. O relógio da composição chama isto ~4×/s. */
    internal abstract fun refreshProgress()

    /**
     * Se o relógio de progresso precisa girar agora.
     *
     * Inclui [VideoStatus.Loading] de propósito, e não é detalhe: no iOS **é a leitura periódica
     * que descobre que a mídia ficou pronta** (o AVPlayer não empurra o estado — o `status` do
     * `AVPlayerItem` se lê). Sem isto, um player com `autoPlay = false` ficaria em "carregando"
     * para sempre, porque `isPlaying` é `false` e ninguém mais olharia.
     */
    internal val needsProgressTicker: Boolean
        get() = isPlaying || status == VideoStatus.Loading

    /** O que o ciclo de vida manda fazer ao sair do primeiro plano. */
    internal open fun onEnterBackground() {
        if (config.backgroundBehavior == VideoBackgroundBehavior.Pause) pause()
    }

    /** Recalcula [subtitleOptions] a partir do que a plataforma achou e do que o app declarou. */
    protected fun updateEmbeddedSubtitles(embedded: List<VideoSubtitleOption>) {
        val externas = media?.subtitles.orEmpty().map { it.toOption() }
        subtitleOptions = mergeSubtitleOptions(embedded, externas)
    }

    /**
     * Aplica o estado de legenda sem tocar no player — para os `actual` reusarem.
     *
     * O nome não é `setSelectedSubtitle` de propósito: colidiria com o setter da propriedade
     * [selectedSubtitle] na JVM (`setSelectedSubtitle(…)V`).
     */
    protected fun applySelectedSubtitle(option: VideoSubtitleOption?) {
        selectedSubtitle = option
        if (option == null || option.embedded) externalCues = emptyList()
    }

    /**
     * Baixa e interpreta o arquivo da legenda externa escolhida.
     *
     * Fica fora de [selectSubtitle] porque é I/O: quem a chama é o `LaunchedEffect` do
     * [rememberVideoPlayerState], que tem escopo e é cancelado ao sair da tela. Falha de download
     * **não vira erro de reprodução** — o vídeo continua, só sem legenda, com o motivo no log.
     */
    internal suspend fun loadSelectedExternalSubtitle(client: HttpClient) {
        val opcao = selectedSubtitle
        if (opcao == null || opcao.embedded) {
            externalCues = emptyList()
            return
        }
        val faixa = media?.subtitles?.firstOrNull { it.optionId() == opcao.id }
        if (faixa == null) {
            externalCues = emptyList()
            return
        }

        cuesPorFaixa[opcao.id]?.let {
            externalCues = it
            return
        }

        val conteudo = faixa.content ?: faixa.url?.let { url ->
            runCatching { client.get(url).bodyAsText() }
                .onFailure { AppLogger.e(VIDEO_TAG, "Falha ao baixar a legenda $url: ${it.message}") }
                .getOrNull()
        }
        if (conteudo == null) {
            externalCues = emptyList()
            return
        }

        val cues = parseSubtitles(conteudo)
        if (cues.isEmpty()) {
            AppLogger.w(VIDEO_TAG, "Arquivo de legenda sem nenhuma fala reconhecida: ${opcao.label}")
        }
        cuesPorFaixa[opcao.id] = cues
        externalCues = cues
    }
}

/** A etiqueta de log do módulo. */
internal const val VIDEO_TAG = "KmpLibVideo"

/** A identidade da faixa externa dentro da sessão: a URL, ou o rótulo quando o conteúdo veio junto. */
internal fun VideoSubtitleTrack.optionId(): String = url ?: "inline:$label"

internal fun VideoSubtitleTrack.toOption(): VideoSubtitleOption =
    VideoSubtitleOption(id = optionId(), label = label, language = language, embedded = false)

/**
 * Junta as legendas das duas origens numa lista só, **embutida na frente**.
 *
 * Quando as duas trazem a mesma língua, fica a **embutida** e a externa some. Não é preferência
 * estética: a embutida é sincronizada pela própria plataforma junto do vídeo, enquanto a externa
 * depende do nosso relógio de progresso — oferecer as duas com o mesmo rótulo ("Português",
 * "Português") faria o aluno escolher no escuro entre uma boa e uma pior.
 *
 * A comparação de língua ignora caixa e região (`pt-BR` casa com `pt`), que é como os manifestos
 * de verdade etiquetam.
 */
fun mergeSubtitleOptions(
    embedded: List<VideoSubtitleOption>,
    external: List<VideoSubtitleOption>,
): List<VideoSubtitleOption> {
    val linguasEmbutidas = embedded.map { it.language.baseLanguage() }.toSet()
    return embedded + external.filter { it.language.baseLanguage() !in linguasEmbutidas }
}

/**
 * A faixa que deve nascer ligada dado o idioma preferido, ou `null` para nenhuma.
 *
 * Casa primeiro exato (`pt-BR` = `pt-BR`) e depois só a língua (`pt-BR` serve para quem pediu
 * `pt`) — nessa ordem, senão "português de Portugal" ganharia de "português do Brasil" por acaso
 * da ordem do manifesto.
 */
fun preferredSubtitleOf(
    options: List<VideoSubtitleOption>,
    preferredLanguage: String?,
): VideoSubtitleOption? {
    if (preferredLanguage.isNullOrBlank()) return null
    val alvo = preferredLanguage.lowercase()
    return options.firstOrNull { it.language.lowercase() == alvo }
        ?: options.firstOrNull { it.language.baseLanguage() == alvo.baseLanguage() }
}

private fun String.baseLanguage(): String = lowercase().substringBefore('-').substringBefore('_')

package br.com.codecacto.kmplib.video.transcode

/**
 * **Compressão de vídeo no aparelho** (2.286.0) — prepara o vídeo gravado ou escolhido para subir:
 * H.264 em MP4, lado menor até 720 px, ~2 Mbps, 30 fps, com corte e sem áudio opcionais, e a
 * miniatura junto.
 *
 * Nasceu do pedido de vídeo do App do Personal: 60 s de câmera de celular em 1080p/4K passam de
 * 100 MB; preparado no perfil [VideoTranscodeProfile.H264_720P], fica em ~15 MB — o que sobe numa
 * rede móvel e cabe na cota do servidor. O servidor normaliza de novo (garante perfil, tira metadados
 * e áudio); a compressão aqui é para **não mandar 100 MB pela rede**.
 *
 * ```kotlin
 * val transcoder = remember { createVideoTranscoder() }
 * when (val r = transcoder.prepare(
 *     source = VideoTranscodeSource.fromPath(gravado.path),
 *     mute = true,
 *     onProgress = { p -> viewModel.onAction(Action.Progresso(p)) },
 * )) {
 *     is VideoTranscodeResult.Success -> enviar(r.video)          // r.video.thumbnailJpeg = a capa
 *     is VideoTranscodeResult.Failure -> mostrarErro(r.error)
 * }
 * ```
 *
 * ## Plataforma (SDK oficial, nada de ffmpeg embutido)
 * - **Android:** Media3 **`Transformer`** — codificador de hardware (MediaCodec), `Presentation`
 *   para o lado menor, `FrameDropEffect` para 30 fps, `ClippingConfiguration` para o corte,
 *   `setRemoveAudio` para o mudo; HDR convertido para SDR (H.264 não carrega HDR10/HLG direito).
 * - **iOS:** AVFoundation **`AVAssetReader` + `AVAssetWriter`** — o caminho da Apple quando a taxa
 *   de bits importa (o `AVAssetExportSession` só aceita presets, e o de 720p sai com 5–8 Mbps: 60 s
 *   passariam de 40 MB). Orientação preservada pela `preferredTransform`; 30 fps por descarte de
 *   quadro; corte por `timeRange`.
 *
 * ## Saída: arquivo TEMPORÁRIO
 * O MP4 vai para a pasta `VIDEO_PREPARED_TEMP_DIRECTORY` (cache do app), que entra na varredura do
 * `clearKmpLibTemporaryFiles` (1 h no bootstrap, tudo no logout). Depois de subir, apague com
 * [PreparedVideo.deleteFile]; fila de envio que precise do arquivo por mais tempo o **move** para a
 * área dela.
 *
 * ## Cancelamento
 * É `suspend`: cancelar a corrotina (sair da tela) cancela a codificação e apaga o arquivo parcial.
 * Uma chamada por vez por instância — o hardware de codificação é um recurso escasso.
 */
interface VideoTranscoder {
    /**
     * Prepara [source] no [profile].
     *
     * @param trim o trecho a manter; `null` = o vídeo inteiro.
     * @param mute `true` = o arquivo sai **sem faixa de áudio** (não é áudio zerado: a faixa não existe).
     * @param onProgress fração 0..1, na main thread, algumas vezes por segundo.
     */
    suspend fun prepare(
        source: VideoTranscodeSource,
        trim: VideoTrim? = null,
        mute: Boolean = false,
        profile: VideoTranscodeProfile = VideoTranscodeProfile.H264_720P,
        onProgress: (Float) -> Unit = {},
    ): VideoTranscodeResult
}

/**
 * O compressor da plataforma. **Android:** exige `initKmpLibVideo(context)` (ou `KmpLib.init`) — sem
 * ele, o `prepare` devolve [VideoTranscodeError.UNKNOWN] com o motivo no log, em vez de derrubar o app.
 */
expect fun createVideoTranscoder(): VideoTranscoder

/**
 * De onde vem o vídeo: um **caminho absoluto** ou uma **URI** (`file://`, e no Android `content://`
 * — a do seletor de vídeo da lib, `PickedVideo.reference`).
 */
class VideoTranscodeSource private constructor(
    /** O que a plataforma abre: caminho absoluto ou URI. */
    val value: String,
    /** `true` = [value] é URI; `false` = caminho. */
    val isUri: Boolean,
) {
    /** Sem o caminho/URI — é arquivo da pessoa. */
    override fun toString(): String = "VideoTranscodeSource(isUri=$isUri)"

    companion object {
        /** Caminho absoluto de um arquivo local (`RecordedVideo.path`). */
        fun fromPath(path: String): VideoTranscodeSource = VideoTranscodeSource(path, isUri = false)

        /** URI: `file://…`, ou `content://…` no Android (`PickedVideo.reference`). */
        fun fromUri(uri: String): VideoTranscodeSource = VideoTranscodeSource(uri, isUri = true)

        /**
         * Aceita os dois: com esquema (`file:`, `content:`) vira URI; senão, caminho. É o que se usa
         * com o `PickedVideo.reference`, que é URI no Android e caminho no iOS.
         */
        fun of(reference: String): VideoTranscodeSource =
            if (reference.substringBefore(':', missingDelimiterValue = "").let { it.length > 1 && it.all(Char::isLetter) }) {
                fromUri(reference)
            } else {
                fromPath(reference)
            }
    }
}

/**
 * O trecho a manter, em milissegundos. [endMillis] `null` = até o fim. Validado contra a duração
 * real por [resolveVideoTrim].
 */
data class VideoTrim(val startMillis: Long, val endMillis: Long? = null)

/**
 * O perfil de saída. Os números são **tetos**, nunca alvos que aumentam o vídeo: fonte menor que
 * [maxShortSidePx] não é ampliada, fonte a 24 fps não ganha quadros.
 *
 * @property maxShortSidePx teto do lado MENOR (720 = "720p" em pé ou deitado).
 * @property videoBitrate taxa média de vídeo, em bits/s.
 * @property frameRate teto de quadros por segundo.
 * @property keyFrameIntervalSeconds intervalo entre quadros-chave — 1 s deixa a busca exata do
 *   player e o quadro a quadro baratos na revisão.
 * @property audioBitrate taxa de áudio AAC, quando há áudio.
 * @property thumbnailMaxDimension maior lado da miniatura JPEG; `0` = sem miniatura.
 */
data class VideoTranscodeProfile(
    val maxShortSidePx: Int = 720,
    val videoBitrate: Int = 2_000_000,
    val frameRate: Int = 30,
    val keyFrameIntervalSeconds: Float = 1f,
    val audioBitrate: Int = 96_000,
    val thumbnailMaxDimension: Int = 480,
) {
    init {
        require(maxShortSidePx in 144..4320) { "maxShortSidePx fora de 144..4320: $maxShortSidePx" }
        require(videoBitrate in 100_000..50_000_000) { "videoBitrate fora de 100 kbps..50 Mbps: $videoBitrate" }
        require(frameRate in 1..120) { "frameRate fora de 1..120: $frameRate" }
        require(keyFrameIntervalSeconds > 0f) { "keyFrameIntervalSeconds precisa ser > 0" }
        require(audioBitrate in 16_000..512_000) { "audioBitrate fora de 16..512 kbps: $audioBitrate" }
        require(thumbnailMaxDimension == 0 || thumbnailMaxDimension in 64..2048) {
            "thumbnailMaxDimension precisa ser 0 ou 64..2048: $thumbnailMaxDimension"
        }
    }

    companion object {
        /**
         * H.264 720p, ~2 Mbps, 30 fps, quadro-chave a cada 1 s — **~15 MB por minuto** sem áudio
         * ([estimatedTranscodeBytes]). O perfil do pedido de vídeo do App do Personal.
         */
        val H264_720P: VideoTranscodeProfile = VideoTranscodeProfile()
    }
}

/**
 * O vídeo preparado.
 *
 * @property path caminho absoluto do MP4 (temporário — ver [VideoTranscoder]).
 * @property uri o mesmo arquivo como `file://` — o que o `VideoMedia(url = …)` toca na prévia.
 * @property widthPx/heightPx medida **na orientação de exibição** (em pé = mais alto que largo).
 * @property thumbnailJpeg a capa (quadro do início, maior lado ≤ `thumbnailMaxDimension`), ou
 *   `null` se o perfil não pediu ou a plataforma não conseguiu tirar.
 */
class PreparedVideo(
    val path: String,
    val uri: String,
    val sizeBytes: Long,
    val durationMillis: Long,
    val widthPx: Int,
    val heightPx: Int,
    val hasAudio: Boolean,
    val thumbnailJpeg: ByteArray?,
    val thumbnailWidthPx: Int,
    val thumbnailHeightPx: Int,
) {
    /** Sempre `video/mp4`. */
    val mimeType: String get() = VIDEO_MP4_MIME_TYPE

    /** A duração em segundos inteiros, arredondada para cima — o `durationSeconds` dos contratos. */
    val durationSeconds: Int get() = ((durationMillis + 999) / 1000).toInt()

    /** Apaga o arquivo. `true` se apagou ou já não existia. */
    fun deleteFile(): Boolean = deleteLocalVideoFile(path)

    /** Sem o caminho — é vídeo da pessoa. */
    override fun toString(): String =
        "PreparedVideo(sizeBytes=$sizeBytes, durationMillis=$durationMillis, widthPx=$widthPx, " +
            "heightPx=$heightPx, hasAudio=$hasAudio, thumbnail=${thumbnailJpeg != null})"
}

/** Resultado do [VideoTranscoder.prepare]. */
sealed interface VideoTranscodeResult {
    data class Success(val video: PreparedVideo) : VideoTranscodeResult

    /** [detail] é diagnóstico para log (sem caminho de arquivo), nunca texto de tela. */
    data class Failure(val error: VideoTranscodeError, val detail: String? = null) : VideoTranscodeResult
}

/** Por que o vídeo não saiu — cada caso com uma saída diferente na tela. */
enum class VideoTranscodeError {
    /** O arquivo não abre (sumiu, permissão da URI expirou, não é vídeo). Saída: escolher/gravar de novo. */
    SOURCE_UNREADABLE,

    /** Abre, mas o aparelho não decodifica (codec raro, HDR que o hardware não converte). */
    UNSUPPORTED_FORMAT,

    /** O trecho pedido não existe no vídeo (começo depois do fim, ou depois da duração). */
    INVALID_TRIM,

    /** O codificador falhou (recurso de hardware ocupado, erro do sistema). Saída: tentar de novo. */
    ENCODER_FAILED,

    /** Sem espaço para gravar a saída. Saída: liberar espaço. */
    NO_SPACE,

    /** Qualquer outra falha — o motivo vai no `detail`, para o log. */
    UNKNOWN,
}

/** O trecho validado, em milissegundos: [startMillis] inclusivo, [endMillis] exclusivo. */
data class ResolvedVideoTrim(val startMillis: Long, val endMillis: Long) {
    val durationMillis: Long get() = endMillis - startMillis
}

/**
 * Valida [trim] contra a duração da fonte. `null` (sem corte) devolve o vídeo inteiro; o fim é
 * grampeado na duração (um corte "até 61 s" num vídeo de 60,4 s não é erro). Devolve `null` quando
 * o trecho não existe: começo negativo, começo ≥ fim, começo ≥ duração, ou menos de
 * [MIN_TRIM_DURATION_MILLIS] de vídeo.
 *
 * Duração desconhecida (≤ 0): só confere o próprio trecho; sem corte devolve `0..0`, e o chamador
 * transcodifica tudo.
 */
fun resolveVideoTrim(trim: VideoTrim?, sourceDurationMillis: Long): ResolvedVideoTrim? {
    if (trim == null) return ResolvedVideoTrim(0L, sourceDurationMillis.coerceAtLeast(0L))
    if (trim.startMillis < 0L) return null
    val fimPedido = trim.endMillis
    if (fimPedido != null && fimPedido <= trim.startMillis) return null
    if (sourceDurationMillis <= 0L) {
        return ResolvedVideoTrim(trim.startMillis, fimPedido ?: 0L)
    }
    if (trim.startMillis >= sourceDurationMillis) return null
    val fim = (fimPedido ?: sourceDurationMillis).coerceAtMost(sourceDurationMillis)
    if (fim - trim.startMillis < MIN_TRIM_DURATION_MILLIS) return null
    return ResolvedVideoTrim(trim.startMillis, fim)
}

/** A medida de saída em pixels (largura × altura). */
data class VideoDimensions(val widthPx: Int, val heightPx: Int)

/**
 * A medida de saída para uma fonte de [sourceWidthPx] × [sourceHeightPx] (já na orientação de
 * exibição): o lado menor reduzido a [maxShortSidePx] mantendo a proporção, os dois lados **pares**
 * (exigência do H.264 4:2:0) e **nunca ampliado** — fonte menor sai na própria medida (arredondada
 * para par). Medida inválida (≤ 0) devolve `null`.
 */
fun targetVideoDimensions(sourceWidthPx: Int, sourceHeightPx: Int, maxShortSidePx: Int): VideoDimensions? {
    if (sourceWidthPx <= 0 || sourceHeightPx <= 0 || maxShortSidePx <= 0) return null
    val menor = minOf(sourceWidthPx, sourceHeightPx)
    val escala = if (menor > maxShortSidePx) maxShortSidePx.toDouble() / menor else 1.0
    fun par(v: Double): Int = (kotlin.math.round(v / 2.0) * 2).toInt().coerceAtLeast(2)
    return VideoDimensions(par(sourceWidthPx * escala), par(sourceHeightPx * escala))
}

/**
 * O tamanho esperado do arquivo, em bytes, para [durationMillis] no [profile]: (vídeo + áudio, se
 * houver) × duração ÷ 8, mais ~2 % de contêiner. É a conta do "~15 MB por minuto" — e o que o app
 * mostra antes de subir, se quiser.
 */
fun estimatedTranscodeBytes(durationMillis: Long, profile: VideoTranscodeProfile, withAudio: Boolean): Long {
    if (durationMillis <= 0L) return 0L
    val bitsPorSegundo = profile.videoBitrate.toLong() + if (withAudio) profile.audioBitrate.toLong() else 0L
    val bytes = bitsPorSegundo * durationMillis / 8_000L
    return bytes + bytes / 50
}

/**
 * Se o quadro em [timeMicros] entra na saída limitada a [targetFrameRate] fps, dado o último quadro
 * mantido em [lastKeptMicros] (`null` = nenhum ainda): entra se estiver a pelo menos um intervalo
 * (com 5 % de folga, para 30 fps nominais que chegam a 29,97 não perderem quadro à toa) do último.
 * É o descarte de quadro do iOS (o Android usa o `FrameDropEffect` da Media3).
 */
fun shouldKeepVideoFrame(timeMicros: Long, lastKeptMicros: Long?, targetFrameRate: Int): Boolean {
    if (lastKeptMicros == null || targetFrameRate <= 0) return true
    val intervalo = 1_000_000.0 / targetFrameRate
    return timeMicros - lastKeptMicros >= intervalo * 0.95
}

/** O menor trecho que vale a pena preparar: 500 ms. */
const val MIN_TRIM_DURATION_MILLIS: Long = 500L

/** O tipo do arquivo preparado. */
const val VIDEO_MP4_MIME_TYPE: String = "video/mp4"

/** Apaga um arquivo local de vídeo. `true` se apagou ou já não existia. Nunca lança. */
internal expect fun deleteLocalVideoFile(path: String): Boolean

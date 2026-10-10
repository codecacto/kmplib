package br.com.codecacto.kmplib.camera.video

import androidx.compose.runtime.Immutable
import br.com.codecacto.kmplib.camera.guided.CameraLens
import br.com.codecacto.kmplib.camera.guided.GuidedCameraStatus
import br.com.codecacto.kmplib.camera.guided.resolveCameraLens
import br.com.codecacto.kmplib.platform.permission.PermissionStatus

/**
 * A resolução da gravação. O padrão é [HD_720P]: é o que o perfil de compressão da lib entrega
 * (`VideoTranscodeProfile.H264_720P`, `kmplib-video`) — gravar acima disso só gasta disco e tempo
 * de compressão. Sem a resolução no aparelho, a plataforma cai para a mais próxima.
 */
enum class VideoRecordingQuality {
    SD_480P,
    HD_720P,
    FHD_1080P,
}

/** Qual permissão está faltando para gravar. */
enum class VideoRecorderPermission {
    CAMERA,

    /** Só com `recordAudio = true`. */
    MICROPHONE,
}

/**
 * O vídeo gravado — um **arquivo temporário** no aparelho, nunca bytes em memória.
 *
 * @property path caminho absoluto. Pasta `VIDEO_CAPTURE_TEMP_DIRECTORY` (cache do app): entra na
 *   varredura do `clearKmpLibTemporaryFiles` (1 h no bootstrap, tudo no logout). Apague com
 *   [deleteFile] assim que não precisar mais (depois de comprimir/enviar).
 * @property uri o mesmo arquivo como `file://` — o que o `VideoMedia(url = …)` toca na prévia.
 * @property durationMillis duração real do arquivo.
 * @property widthPx/heightPx medida **na orientação de exibição**; `0` se não deu para ler.
 * @property mimeType `video/mp4` (Android) ou `video/quicktime` (iOS) — os dois que o upload aceita.
 * @property lens a câmera usada.
 * @property hasAudio se o arquivo tem faixa de áudio.
 * @property reachedMaxDuration a gravação parou sozinha no teto (`maxDurationMillis`).
 */
class RecordedVideo(
    val path: String,
    val uri: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val widthPx: Int,
    val heightPx: Int,
    val mimeType: String,
    val lens: CameraLens,
    val hasAudio: Boolean,
    val reachedMaxDuration: Boolean,
) {
    /** A duração em segundos inteiros, arredondada para cima — o `durationSeconds` dos contratos. */
    val durationSeconds: Int get() = ((durationMillis + 999) / 1000).toInt()

    /** Apaga o arquivo. `true` se apagou ou já não existia. Nunca lança. */
    fun deleteFile(): Boolean = deleteRecordedVideoFile(path)

    /** Sem o caminho — é vídeo da pessoa. */
    override fun toString(): String =
        "RecordedVideo(durationMillis=$durationMillis, sizeBytes=$sizeBytes, widthPx=$widthPx, heightPx=$heightPx, " +
            "mimeType=$mimeType, lens=$lens, hasAudio=$hasAudio, reachedMaxDuration=$reachedMaxDuration)"
}

/**
 * Por que o gravador não entregou vídeo — para o app registrar ou oferecer outro caminho (a
 * galeria). A própria tela já mostra a mensagem e a saída de cada caso.
 */
enum class VideoRecorderError {
    /** Câmera negada (ainda dá para pedir de novo). */
    PERMISSION_DENIED,

    /** Câmera negada em definitivo: só pelas Configurações. */
    PERMISSION_PERMANENTLY_DENIED,

    /** Microfone negado (com `recordAudio = true`). */
    MICROPHONE_DENIED,

    /** Microfone negado em definitivo. */
    MICROPHONE_PERMANENTLY_DENIED,

    /** Sem câmera utilizável (emulador sem câmera, simulador do iOS). */
    CAMERA_UNAVAILABLE,

    /** A sessão de câmera não subiu. */
    INITIALIZATION_FAILED,

    /** A gravação começou mas o arquivo não saiu. */
    RECORDING_FAILED,

    /** Sem espaço no aparelho para gravar. */
    NO_SPACE,
}

/** O estado da tela do gravador — cada caso com a sua saída, nunca um preview preto. */
sealed interface VideoRecorderState {
    data object Starting : VideoRecorderState

    /** Câmera ao vivo. [canSwitchLens] = mostrar o botão de trocar (com as duas câmeras). */
    data class Live(val canSwitchLens: Boolean) : VideoRecorderState

    /** Falta [permission], mas dá para pedir. */
    data class PermissionRequired(val permission: VideoRecorderPermission) : VideoRecorderState

    /** [permission] negada em definitivo — o caminho é abrir as Configurações. */
    data class PermissionPermanentlyDenied(val permission: VideoRecorderPermission) : VideoRecorderState

    data object CameraUnavailable : VideoRecorderState

    /** [message] é diagnóstico (log), não texto de tela. */
    data class InitializationFailed(val message: String?) : VideoRecorderState
}

/**
 * Deriva o [VideoRecorderState]. Pura. A câmera tem precedência; o microfone só conta com
 * [recordAudio] (e é pedido depois da câmera, nunca os dois diálogos juntos).
 */
internal fun videoRecorderStateOf(
    cameraPermission: PermissionStatus,
    microphonePermission: PermissionStatus,
    recordAudio: Boolean,
    camera: GuidedCameraStatus?,
    allowLensSwitch: Boolean,
): VideoRecorderState {
    when (cameraPermission) {
        PermissionStatus.PERMANENTLY_DENIED ->
            return VideoRecorderState.PermissionPermanentlyDenied(VideoRecorderPermission.CAMERA)
        PermissionStatus.DENIED, PermissionStatus.NOT_REQUESTED ->
            return VideoRecorderState.PermissionRequired(VideoRecorderPermission.CAMERA)
        PermissionStatus.GRANTED -> Unit
    }
    if (recordAudio) {
        when (microphonePermission) {
            PermissionStatus.PERMANENTLY_DENIED ->
                return VideoRecorderState.PermissionPermanentlyDenied(VideoRecorderPermission.MICROPHONE)
            PermissionStatus.DENIED, PermissionStatus.NOT_REQUESTED ->
                return VideoRecorderState.PermissionRequired(VideoRecorderPermission.MICROPHONE)
            PermissionStatus.GRANTED -> Unit
        }
    }
    return when (camera) {
        null -> VideoRecorderState.Starting
        is GuidedCameraStatus.Ready -> VideoRecorderState.Live(
            canSwitchLens = allowLensSwitch && camera.availableLenses.containsAll(CameraLens.entries),
        )
        GuidedCameraStatus.Unavailable -> VideoRecorderState.CameraUnavailable
        is GuidedCameraStatus.Failed -> VideoRecorderState.InitializationFailed(camera.message)
    }
}

/** O erro a relatar ao app num estado — ou `null` ("ainda não pediu" não é erro). */
internal fun videoRecorderErrorOf(
    cameraPermission: PermissionStatus,
    microphonePermission: PermissionStatus,
    state: VideoRecorderState,
): VideoRecorderError? = when (state) {
    is VideoRecorderState.PermissionRequired -> when (state.permission) {
        VideoRecorderPermission.CAMERA ->
            if (cameraPermission == PermissionStatus.DENIED) VideoRecorderError.PERMISSION_DENIED else null
        VideoRecorderPermission.MICROPHONE ->
            if (microphonePermission == PermissionStatus.DENIED) VideoRecorderError.MICROPHONE_DENIED else null
    }
    is VideoRecorderState.PermissionPermanentlyDenied -> when (state.permission) {
        VideoRecorderPermission.CAMERA -> VideoRecorderError.PERMISSION_PERMANENTLY_DENIED
        VideoRecorderPermission.MICROPHONE -> VideoRecorderError.MICROPHONE_PERMANENTLY_DENIED
    }
    VideoRecorderState.CameraUnavailable -> VideoRecorderError.CAMERA_UNAVAILABLE
    is VideoRecorderState.InitializationFailed -> VideoRecorderError.INITIALIZATION_FAILED
    VideoRecorderState.Starting, is VideoRecorderState.Live -> null
}

/**
 * O relógio da gravação em curso — o que o contador e o anel do botão desenham.
 *
 * @property elapsedMillis tempo gravado (grampeado em `0..max`).
 * @property remainingMillis quanto falta para o teto.
 * @property progress fração do teto (0..1) — o anel em volta do botão.
 * @property canStop já passou do mínimo: o botão de parar responde. Antes disso o arquivo sairia
 *   curto demais para o servidor (que recusa abaixo de 1 s).
 * @property isFinalStretch nos últimos [FINAL_STRETCH_MILLIS] — o contador muda de cor.
 */
@Immutable
data class RecordingClock(
    val elapsedMillis: Long,
    val remainingMillis: Long,
    val maxMillis: Long,
    val progress: Float,
    val canStop: Boolean,
    val isFinalStretch: Boolean,
)

/** Monta o [RecordingClock] de uma gravação com [elapsedMillis] de [maxMillis] (mínimo [minMillis]). */
fun recordingClockOf(elapsedMillis: Long, maxMillis: Long, minMillis: Long): RecordingClock {
    val teto = maxMillis.coerceAtLeast(1L)
    val decorrido = elapsedMillis.coerceIn(0L, teto)
    val falta = teto - decorrido
    return RecordingClock(
        elapsedMillis = decorrido,
        remainingMillis = falta,
        maxMillis = teto,
        progress = (decorrido.toFloat() / teto.toFloat()).coerceIn(0f, 1f),
        canStop = decorrido >= minMillis,
        isFinalStretch = falta <= FINAL_STRETCH_MILLIS && falta < teto,
    )
}

/**
 * Milissegundos → `"0:07"`/`"1:00"`. Contador de gravação conta segundos **inteiros já gravados**
 * (para baixo), como a câmera do sistema: "0:59" só vira "1:00" no teto.
 */
fun formatRecordingTime(millis: Long): String {
    val total = (millis / 1000).coerceAtLeast(0)
    val segundos = total % 60
    val minutos = total / 60
    return "$minutos:${if (segundos < 10) "0" else ""}$segundos"
}

/** Teto de gravação válido: 1 s..10 min. */
internal fun coerceMaxRecordingMillis(value: Long): Long = value.coerceIn(MIN_RECORDING_CEILING_MILLIS, MAX_RECORDING_CEILING_MILLIS)

/** Mínimo válido: 0..teto. */
internal fun coerceMinRecordingMillis(value: Long, max: Long): Long = value.coerceIn(0L, max)

/** Contagem regressiva antes de gravar: 0..10 s. */
internal fun coerceStartDelaySeconds(value: Int): Int = value.coerceIn(0, MAX_START_DELAY_SECONDS)

/**
 * Proporção do preview (largura/altura): a do VÍDEO, 16:9 — 9:16 em retrato. Como na câmera guiada,
 * o preview é desenhado inteiro, sem corte: o que está dentro do guia na tela está no vídeo.
 */
internal fun videoRecorderPreviewAspectRatio(containerWidth: Float, containerHeight: Float): Float =
    if (containerWidth > containerHeight) 16f / 9f else 9f / 16f

/** A câmera a usar — a mesma regra da câmera guiada. */
internal fun resolveRecorderLens(requested: CameraLens, available: Set<CameraLens>): CameraLens? =
    resolveCameraLens(requested, available)

/**
 * Ids estáveis para o Maestro — nunca o texto. Flow: `tapOn id camera-video-btn-gravar`, esperar,
 * `tapOn` de novo (é o mesmo botão para parar).
 */
object VideoRecorderTestTags {
    const val ROOT: String = "camera-video"
    const val PREVIEW: String = "camera-video-preview"
    const val GUIDE: String = "camera-video-guia"
    const val RECORD: String = "camera-video-btn-gravar"
    const val SWITCH_LENS: String = "camera-video-btn-trocar"
    const val CLOSE: String = "camera-video-btn-fechar"
    const val TIMER: String = "camera-video-tempo"
    const val COUNTDOWN: String = "camera-video-contagem"
    const val PRIMARY_ACTION: String = "camera-video-btn-acao"
    const val MESSAGE: String = "camera-video-mensagem"
}

/** Os últimos 10 s: o contador avisa que vai parar. */
const val FINAL_STRETCH_MILLIS: Long = 10_000L

/** O teto padrão: 60 s. */
const val DEFAULT_MAX_RECORDING_MILLIS: Long = 60_000L

/** O mínimo padrão: 1 s (o servidor recusa vídeo de 0 s). */
const val DEFAULT_MIN_RECORDING_MILLIS: Long = 1_000L

private const val MIN_RECORDING_CEILING_MILLIS = 1_000L
private const val MAX_RECORDING_CEILING_MILLIS = 10L * 60_000L
private const val MAX_START_DELAY_SECONDS = 10

/** Apaga o arquivo gravado. `true` se apagou ou já não existia. Nunca lança. */
internal expect fun deleteRecordedVideoFile(path: String): Boolean

package br.com.codecacto.kmplib.camera.video

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import br.com.codecacto.kmplib.camera.guided.CameraLens
import br.com.codecacto.kmplib.camera.guided.GuidedCameraStatus

/** Por que a gravação não saiu. */
internal enum class VideoRecordFailure {
    NO_SPACE,

    /** Parou antes de haver um quadro válido (toque duplo, sessão interrompida). */
    NO_DATA,
    OTHER,
}

/** O que a plataforma relata de uma gravação, já na main thread. */
internal sealed interface VideoRecordEvent {
    /** O arquivo começou a receber quadros — é daqui que o contador anda. */
    data object Started : VideoRecordEvent

    data class Finished(val video: RecordedVideo) : VideoRecordEvent

    /** [message] é diagnóstico (sem caminho de arquivo), nunca texto de tela. */
    data class Failed(val failure: VideoRecordFailure, val message: String?) : VideoRecordEvent
}

/**
 * A ponte entre os botões (código comum) e a sessão (plataforma): a plataforma preenche [start] e
 * [stop] quando a sessão está no ar e os esvazia ao sair. Gravar com a sessão fora do ar vira falha,
 * não silêncio.
 */
internal class VideoRecordHandle {
    var start: ((onEvent: (VideoRecordEvent) -> Unit) -> Unit)? = null
    var stop: (() -> Unit)? = null

    fun startRecording(onEvent: (VideoRecordEvent) -> Unit) {
        val iniciar = start
        if (iniciar == null) {
            onEvent(VideoRecordEvent.Failed(VideoRecordFailure.OTHER, "sessão de câmera fora do ar"))
        } else {
            iniciar(onEvent)
        }
    }

    fun stopRecording() {
        stop?.invoke()
    }
}

/**
 * A sessão de câmera de VÍDEO da plataforma, desenhada **inteira** (sem corte) na caixa 9:16/16:9
 * que o código comum dimensionou.
 *
 * - **Android:** CameraX — `Preview` + `VideoCapture<Recorder>` (16:9, [quality] com *fallback*),
 *   `FileOutputOptions.setDurationLimitMillis` = o teto que para sozinho; `PreviewView` em
 *   `FIT_CENTER`; tudo solto no `onDispose`.
 * - **iOS:** AVFoundation — `AVCaptureSession` (preset da [quality]) + `AVCaptureMovieFileOutput`
 *   com `maxRecordedDuration`; microfone como segunda entrada com [recordAudio].
 *
 * Sair da tela no meio de uma gravação para a gravação e **apaga** o arquivo: ninguém o pediu.
 */
@Composable
internal expect fun VideoRecorderPreview(
    modifier: Modifier,
    lens: CameraLens,
    recordAudio: Boolean,
    quality: VideoRecordingQuality,
    maxDurationMillis: Long,
    handle: VideoRecordHandle,
    onStatus: (GuidedCameraStatus) -> Unit,
)

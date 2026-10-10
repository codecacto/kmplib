package br.com.codecacto.kmplib.camera.video

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.camera.guided.CameraLens
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_close
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_init_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_open_settings
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_permission_allow
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_permission_title
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_retry
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_starting
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_unavailable
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_use_back
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_use_front
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_cancel_countdown
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_countdown
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_finishing
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_limit_reached
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_microphone_allow
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_microphone_denied_message
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_microphone_message
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_microphone_title
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_no_space
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_permission_denied_message
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_permission_message
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_record
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_recording_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_recording_time
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_stop
import br.com.codecacto.kmplib.generated.resources.kmplib_video_recorder_stop_disabled
import org.jetbrains.compose.resources.stringResource

/**
 * Textos visíveis (e lidos pelo leitor de tela) do [VideoRecorderCamera]. O default
 * ([rememberVideoRecorderTexts]) vem dos recursos da lib nos 4 idiomas e segue o idioma do
 * aparelho; para trocar uma frase, `rememberVideoRecorderTexts().copy(…)`. Os literais abaixo são
 * pt-BR, para quem constrói o objeto fora de composição (teste, preview).
 */
data class VideoRecorderTexts(
    val record: String = "Gravar vídeo",
    val stop: String = "Parar gravação",
    /** O botão antes do mínimo (não para ainda). */
    val stopDisabled: String = "Gravando — aguarde para poder parar",
    /** O tempo lido pelo leitor de tela: recebe "0:12" e "1:00". */
    val recordingTime: (elapsed: String, max: String) -> String = { a, b -> "$a de $b" },
    /** A contagem antes de gravar: recebe os segundos que faltam. */
    val countdown: (seconds: Int) -> String = { s -> "Gravação começa em $s" },
    val cancelCountdown: String = "Cancelar contagem",
    val finishing: String = "Salvando o vídeo…",
    val useFrontLens: String = "Usar câmera frontal",
    val useBackLens: String = "Usar câmera traseira",
    val close: String = "Fechar câmera",
    val starting: String = "Preparando a câmera…",
    val permissionTitle: String = "Precisamos da câmera",
    val permissionMessage: String = "A câmera é usada para gravar o vídeo.",
    val permissionAllow: String = "Permitir câmera",
    val permissionDeniedMessage: String = "O acesso à câmera está bloqueado. Libere nas Configurações do aparelho para gravar o vídeo.",
    val microphoneTitle: String = "Precisamos do microfone",
    val microphoneMessage: String = "O microfone grava o som do vídeo.",
    val microphoneAllow: String = "Permitir microfone",
    val microphoneDeniedMessage: String = "O acesso ao microfone está bloqueado. Libere nas Configurações do aparelho para gravar com som.",
    val openSettings: String = "Abrir Configurações",
    val cameraUnavailable: String = "Câmera indisponível neste aparelho.",
    val initializationFailed: String = "Não foi possível iniciar a câmera.",
    val retry: String = "Tentar novamente",
    val recordingFailed: String = "O vídeo não foi gravado. Tente de novo.",
    val noSpace: String = "Sem espaço no aparelho para gravar o vídeo.",
    val limitReached: String = "Tempo máximo atingido",
) {
    /** Para onde o botão de trocar leva — diz o destino, não onde está. */
    fun switchLensLabel(current: CameraLens): String =
        if (current == CameraLens.BACK) useFrontLens else useBackLens
}

/** [VideoRecorderTexts] no idioma do aparelho (pt-BR / pt-PT / en / es). */
@Composable
fun rememberVideoRecorderTexts(): VideoRecorderTexts {
    val tempo = stringResource(Res.string.kmplib_video_recorder_recording_time, "\u0001", "\u0002")
    val contagem = stringResource(Res.string.kmplib_video_recorder_countdown, "\u0001")
    return VideoRecorderTexts(
        record = stringResource(Res.string.kmplib_video_recorder_record),
        stop = stringResource(Res.string.kmplib_video_recorder_stop),
        stopDisabled = stringResource(Res.string.kmplib_video_recorder_stop_disabled),
        recordingTime = { a, b -> tempo.replace("\u0001", a).replace("\u0002", b) },
        countdown = { s -> contagem.replace("\u0001", s.toString()) },
        cancelCountdown = stringResource(Res.string.kmplib_video_recorder_cancel_countdown),
        finishing = stringResource(Res.string.kmplib_video_recorder_finishing),
        useFrontLens = stringResource(Res.string.kmplib_guided_camera_use_front),
        useBackLens = stringResource(Res.string.kmplib_guided_camera_use_back),
        close = stringResource(Res.string.kmplib_guided_camera_close),
        starting = stringResource(Res.string.kmplib_guided_camera_starting),
        permissionTitle = stringResource(Res.string.kmplib_guided_camera_permission_title),
        permissionMessage = stringResource(Res.string.kmplib_video_recorder_permission_message),
        permissionAllow = stringResource(Res.string.kmplib_guided_camera_permission_allow),
        permissionDeniedMessage = stringResource(Res.string.kmplib_video_recorder_permission_denied_message),
        microphoneTitle = stringResource(Res.string.kmplib_video_recorder_microphone_title),
        microphoneMessage = stringResource(Res.string.kmplib_video_recorder_microphone_message),
        microphoneAllow = stringResource(Res.string.kmplib_video_recorder_microphone_allow),
        microphoneDeniedMessage = stringResource(Res.string.kmplib_video_recorder_microphone_denied_message),
        openSettings = stringResource(Res.string.kmplib_guided_camera_open_settings),
        cameraUnavailable = stringResource(Res.string.kmplib_guided_camera_unavailable),
        initializationFailed = stringResource(Res.string.kmplib_guided_camera_init_failed),
        retry = stringResource(Res.string.kmplib_guided_camera_retry),
        recordingFailed = stringResource(Res.string.kmplib_video_recorder_recording_failed),
        noSpace = stringResource(Res.string.kmplib_video_recorder_no_space),
        limitReached = stringResource(Res.string.kmplib_video_recorder_limit_reached),
    )
}

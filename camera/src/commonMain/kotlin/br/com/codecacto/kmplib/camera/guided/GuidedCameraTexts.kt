package br.com.codecacto.kmplib.camera.guided

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_capture
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_capture_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_capturing
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_close
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_flash_auto
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_flash_off
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_flash_on
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_init_failed
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_open_settings
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_permission_allow
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_permission_denied_message
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_permission_message
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_permission_title
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_retry
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_starting
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_unavailable
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_use_back
import br.com.codecacto.kmplib.generated.resources.kmplib_guided_camera_use_front
import org.jetbrains.compose.resources.stringResource

/**
 * Textos visíveis (e lidos pelo leitor de tela) da [GuidedCamera].
 *
 * Mesma convenção dos demais componentes da lib: o default ([rememberGuidedCameraTexts]) vem dos
 * recursos da lib nos 4 idiomas (pt-BR, pt-PT, en, es) e segue o idioma do aparelho; para trocar
 * uma frase, `rememberGuidedCameraTexts().copy(…)`. Os literais abaixo são pt-BR e existem para
 * quem constrói o objeto fora de composição (teste, preview).
 */
data class GuidedCameraTexts(
    val capture: String = "Tirar foto",
    val useFrontLens: String = "Usar câmera frontal",
    val useBackLens: String = "Usar câmera traseira",
    val flashOff: String = "Flash desligado",
    val flashAuto: String = "Flash automático",
    val flashOn: String = "Flash ligado",
    val close: String = "Fechar câmera",
    val starting: String = "Preparando a câmera…",
    val permissionTitle: String = "Precisamos da câmera",
    val permissionMessage: String = "A câmera é usada para tirar a foto.",
    val permissionAllow: String = "Permitir câmera",
    val permissionDeniedMessage: String = "O acesso à câmera está bloqueado. Libere nas Configurações do aparelho para tirar a foto.",
    val openSettings: String = "Abrir Configurações",
    val cameraUnavailable: String = "Câmera indisponível neste aparelho.",
    val initializationFailed: String = "Não foi possível iniciar a câmera.",
    val retry: String = "Tentar novamente",
    val captureFailed: String = "A foto não saiu. Tente de novo.",
    val capturing: String = "Tirando a foto…",
) {
    /** A descrição do botão de flash para o modo atual (o leitor de tela lê o estado). */
    fun flashLabel(mode: CameraFlashMode): String = when (mode) {
        CameraFlashMode.OFF -> flashOff
        CameraFlashMode.AUTO -> flashAuto
        CameraFlashMode.ON -> flashOn
    }

    /** A descrição do botão de trocar de câmera — diz para ONDE vai, não onde está. */
    fun switchLensLabel(current: CameraLens): String =
        if (current == CameraLens.BACK) useFrontLens else useBackLens
}

/** [GuidedCameraTexts] no idioma do aparelho (pt-BR / pt-PT / en / es). */
@Composable
fun rememberGuidedCameraTexts(): GuidedCameraTexts = GuidedCameraTexts(
    capture = stringResource(Res.string.kmplib_guided_camera_capture),
    useFrontLens = stringResource(Res.string.kmplib_guided_camera_use_front),
    useBackLens = stringResource(Res.string.kmplib_guided_camera_use_back),
    flashOff = stringResource(Res.string.kmplib_guided_camera_flash_off),
    flashAuto = stringResource(Res.string.kmplib_guided_camera_flash_auto),
    flashOn = stringResource(Res.string.kmplib_guided_camera_flash_on),
    close = stringResource(Res.string.kmplib_guided_camera_close),
    starting = stringResource(Res.string.kmplib_guided_camera_starting),
    permissionTitle = stringResource(Res.string.kmplib_guided_camera_permission_title),
    permissionMessage = stringResource(Res.string.kmplib_guided_camera_permission_message),
    permissionAllow = stringResource(Res.string.kmplib_guided_camera_permission_allow),
    permissionDeniedMessage = stringResource(Res.string.kmplib_guided_camera_permission_denied_message),
    openSettings = stringResource(Res.string.kmplib_guided_camera_open_settings),
    cameraUnavailable = stringResource(Res.string.kmplib_guided_camera_unavailable),
    initializationFailed = stringResource(Res.string.kmplib_guided_camera_init_failed),
    retry = stringResource(Res.string.kmplib_guided_camera_retry),
    captureFailed = stringResource(Res.string.kmplib_guided_camera_capture_failed),
    capturing = stringResource(Res.string.kmplib_guided_camera_capturing),
)

package br.com.codecacto.kmplib.platform

import android.content.Context
import androidx.fragment.app.FragmentActivity
import br.com.codecacto.kmplib.platform.audio.AudioCaptureHolder
import br.com.codecacto.kmplib.platform.permission.PermissionHostHolder
import br.com.codecacto.kmplib.platform.privacy.AndroidPrivacyScreen
import br.com.codecacto.kmplib.platform.tts.TtsControllerHolder
import br.com.codecacto.kmplib.torch.TorchControllerHolder

/**
 * Registra o `Context` nos serviços de sistema do `kmplib-platform`: compartilhamento,
 * notificação agendada, TTS, lanterna, bateria, sacudida, captura de áudio e idioma do aparelho.
 *
 * Chame no `Application.onCreate()`. Ver [br.com.codecacto.kmplib.core.initKmpLibCore] para o
 * porquê de cada módulo ter o seu.
 */
fun initKmpLibPlatform(context: Context) {
    ShareHandlerHolder.init(context)
    NotificationSchedulerHolder.init(context)
    TtsControllerHolder.init(context)
    TorchControllerHolder.init(context)
    BatteryMonitorHolder.init(context)
    ShakeDetectorHolder.init(context)
    AudioCaptureHolder.init(context)
    DeviceLocaleHolder.init(context)
}

/**
 * Entrega a `Activity` em foco ao que precisa dela: biometria, brilho de tela, o agendador de
 * notificação e — desde a 2.154.0 — o host de permissão de runtime.
 *
 * Chame no `Activity.onResume()`.
 *
 * Entrega também a janela ao `PrivacyScreen` (modo discreto): o `FLAG_SECURE` vive na janela da
 * instância atual da `Activity`, então **sem esta chamada girar o aparelho tira a proteção** — a
 * janela nova nasce sem o flag, e a lista de membros volta a aparecer na multitarefa.
 *
 * A ausência do host de permissão é MUDA: sem ele, `PermissionManager.requestPermission` não abre
 * diálogo nenhum, registra um aviso e devolve o status que já tinha. O botão "Permitir" existe, é
 * tocável, e não acontece nada — com build verde. Chamar duas vezes é inofensivo (os holders só
 * guardam a referência).
 */
fun kmpLibPlatformOnResume(activity: FragmentActivity) {
    BiometricAuthHolder.setActivity(activity)
    ScreenBrightnessHolder.setActivity(activity)
    NotificationSchedulerHolder.setActivity(activity)
    PermissionHostHolder.setActivity(activity)
    AndroidPrivacyScreen.setActivity(activity)
}

/** Solta a referência à `Activity`. Chame no `Activity.onPause()`. */
fun kmpLibPlatformOnPause() {
    BiometricAuthHolder.clearActivity()
    ScreenBrightnessHolder.clearActivity()
    NotificationSchedulerHolder.clearActivity()
    PermissionHostHolder.clearActivity()
    AndroidPrivacyScreen.clearActivity()
}

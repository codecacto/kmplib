package br.com.codecacto.kmplib.torch

/**
 * **Por que a lanterna não acendeu** — tipado, nunca exceção crua vazando para a UI.
 *
 * A lib não traz texto de usuário para estes casos de propósito: a frase é do produto (e o idioma
 * vem do aparelho, padrão da fábrica). O que a lib garante é que cada motivo é **distinguível** —
 * "seu aparelho não tem flash" e "outro app está usando a câmera" pedem telas diferentes.
 */
sealed interface TorchError {

    /** O aparelho não tem unidade de flash (ou nenhuma câmera com LED utilizável). */
    data object NoTorch : TorchError

    /**
     * Acesso à câmera negado — permissão recusada pelo usuário ou câmera desligada por política
     * do dispositivo (MDM corporativo).
     */
    data object PermissionDenied : TorchError

    /**
     * A câmera está **em uso por outro app** (ou pelo limite de câmeras abertas do aparelho).
     * Some sozinho quando o outro app solta o recurso.
     */
    data object InUse : TorchError

    /**
     * Lanterna temporariamente indisponível: superaquecimento, câmera desconectada, falha do
     * dispositivo. Tentar de novo mais tarde é razoável.
     */
    data object Unavailable : TorchError

    /** Falha não classificada; [message] é diagnóstico (log), não texto de tela. */
    data class Unknown(val message: String?) : TorchError
}

/**
 * Resultado de um comando de lanterna. Sucesso ou [TorchError] — sem `throw`, porque acender a luz
 * é a ação mais frequente do app e ninguém envolve um toggle em `try/catch`.
 */
sealed interface TorchOutcome {

    /** O comando chegou ao hardware. */
    data object Success : TorchOutcome

    /** O comando não foi aplicado; [error] diz por quê. */
    data class Failure(val error: TorchError) : TorchOutcome

    /** Atalho para `is Success`. */
    val isSuccess: Boolean get() = this is Success

    /** O erro, quando houve; `null` no sucesso. */
    val errorOrNull: TorchError? get() = (this as? Failure)?.error
}

/**
 * Motivo de recusa de acesso à câmera, normalizado a partir do código do Android
 * (`CameraAccessException.reason`).
 *
 * A conversão mora em `commonMain` para ser **coberta por teste** sem aparelho: é ela que decide se
 * a tela diz "outro app está usando a câmera" ou "acesso negado", e errar aqui manda o usuário para
 * o botão errado.
 */
enum class TorchAccessDenialReason {

    /** Outra aplicação está com a câmera. (`CAMERA_IN_USE`) */
    InUse,

    /** Limite de câmeras abertas simultaneamente atingido. (`MAX_CAMERAS_IN_USE`) */
    MaxCamerasInUse,

    /** Câmera desabilitada por política do dispositivo. (`CAMERA_DISABLED`) */
    DisabledByPolicy,

    /** Câmera desconectada (removida/indisponível). (`CAMERA_DISCONNECTED`) */
    Disconnected,

    /** Falha do próprio dispositivo de câmera. (`CAMERA_ERROR`) */
    DeviceError;

    /** O [TorchError] correspondente, que é o que a UI enxerga. */
    val asTorchError: TorchError
        get() = when (this) {
            InUse, MaxCamerasInUse -> TorchError.InUse
            DisabledByPolicy -> TorchError.PermissionDenied
            Disconnected, DeviceError -> TorchError.Unavailable
        }

    companion object {
        // Valores públicos e estáveis de android.hardware.camera2.CameraAccessException.
        internal const val ANDROID_CAMERA_DISABLED = 1
        internal const val ANDROID_CAMERA_DISCONNECTED = 2
        internal const val ANDROID_CAMERA_ERROR = 3
        internal const val ANDROID_CAMERA_IN_USE = 4
        internal const val ANDROID_MAX_CAMERAS_IN_USE = 5

        /**
         * Converte `CameraAccessException.reason` no motivo normalizado. Código desconhecido vira
         * [DeviceError] (⇒ [TorchError.Unavailable]) — degradar para "tente de novo" é melhor do
         * que acusar permissão que ninguém negou.
         */
        fun fromAndroidReason(reason: Int): TorchAccessDenialReason = when (reason) {
            ANDROID_CAMERA_IN_USE -> InUse
            ANDROID_MAX_CAMERAS_IN_USE -> MaxCamerasInUse
            ANDROID_CAMERA_DISABLED -> DisabledByPolicy
            ANDROID_CAMERA_DISCONNECTED -> Disconnected
            ANDROID_CAMERA_ERROR -> DeviceError
            else -> DeviceError
        }
    }
}

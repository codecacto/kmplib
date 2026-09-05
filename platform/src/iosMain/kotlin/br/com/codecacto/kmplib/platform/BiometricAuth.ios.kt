package br.com.codecacto.kmplib.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthenticationWithBiometrics
import platform.LocalAuthentication.LABiometryTypeFaceID
import platform.LocalAuthentication.LABiometryTypeTouchID
import platform.LocalAuthentication.LABiometryTypeNone
import br.com.codecacto.kmplib.core.util.AppLogger

class IosBiometricAuth : BiometricAuth {

    companion object {
        private const val TAG = "BiometricAuth"
        private const val ERROR_USER_CANCEL = -2L
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun isAvailable(): Boolean {
        val context = LAContext()
        return context.canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            error = null
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun getBiometricType(): BiometricType {
        val context = LAContext()
        val canEvaluate = context.canEvaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
            error = null
        )

        if (!canEvaluate) return BiometricType.NONE

        return when (context.biometryType) {
            LABiometryTypeFaceID -> BiometricType.FACE_ID
            LABiometryTypeTouchID -> BiometricType.TOUCH_ID
            LABiometryTypeNone -> BiometricType.NONE
            else -> BiometricType.UNKNOWN
        }
    }

    /**
     * `LAPolicyDeviceOwnerAuthentication` — biometria **ou** o código do aparelho. É a política que
     * a própria Apple indica para trava de app: `...WithBiometrics` recusa quem não tem Face ID /
     * Touch ID cadastrado, e num iPhone só com código isso tranca o dono para fora.
     */
    @OptIn(ExperimentalForeignApi::class)
    override fun isDeviceSecured(): Boolean =
        LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = null)

    @OptIn(ExperimentalForeignApi::class)
    override fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    ) = authenticate(title, subtitle, false, onSuccess, onError, onCancel)

    @OptIn(ExperimentalForeignApi::class)
    override fun authenticate(
        title: String,
        subtitle: String,
        allowDeviceCredential: Boolean,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        val secured = if (allowDeviceCredential) isDeviceSecured() else isAvailable()
        if (!secured) {
            onError(
                if (allowDeviceCredential) "Aparelho sem biometria e sem código de bloqueio"
                else "Biometria não disponível"
            )
            return
        }

        val context = LAContext()
        // Com o código do aparelho permitido, a alternativa é do PRÓPRIO sistema ("Digite o
        // código") e apagar o título do fallback esconderia a única saída de quem falhou no Face ID.
        if (!allowDeviceCredential) context.localizedFallbackTitle = ""

        val policy =
            if (allowDeviceCredential) LAPolicyDeviceOwnerAuthentication
            else LAPolicyDeviceOwnerAuthenticationWithBiometrics

        val reason = if (subtitle.isNotEmpty()) "$title\n$subtitle" else title

        context.evaluatePolicy(
            policy,
            localizedReason = reason
        ) { success, error ->
            when {
                success -> {
                    AppLogger.d(TAG, "Autenticação bem-sucedida")
                    onSuccess()
                }
                error != null -> {
                    val nsError = error as NSError
                    if (nsError.code == ERROR_USER_CANCEL) {
                        AppLogger.d(TAG, "Autenticação cancelada pelo usuário")
                        onCancel()
                    } else {
                        val errorMessage = nsError.localizedDescription ?: "Erro desconhecido"
                        AppLogger.e(TAG, "Erro na autenticação: $errorMessage")
                        onError(errorMessage)
                    }
                }
                else -> {
                    onError("Erro desconhecido")
                }
            }
        }
    }
}

actual fun getBiometricAuth(): BiometricAuth = IosBiometricAuth()

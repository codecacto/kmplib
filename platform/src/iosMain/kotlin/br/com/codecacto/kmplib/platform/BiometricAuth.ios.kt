package br.com.codecacto.kmplib.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
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

    @OptIn(ExperimentalForeignApi::class)
    override fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        if (!isAvailable()) {
            onError("Biometria não disponível")
            return
        }

        val context = LAContext()
        context.localizedFallbackTitle = "" // Remove opção de fallback

        val reason = if (subtitle.isNotEmpty()) "$title\n$subtitle" else title

        context.evaluatePolicy(
            LAPolicyDeviceOwnerAuthenticationWithBiometrics,
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

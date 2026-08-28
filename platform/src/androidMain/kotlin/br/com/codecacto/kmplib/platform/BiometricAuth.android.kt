package br.com.codecacto.kmplib.platform

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import br.com.codecacto.kmplib.core.util.AppLogger
import java.lang.ref.WeakReference

/**
 * Holder para Activity necessária para BiometricPrompt.
 * Deve ser atualizado em Activity.onResume().
 */
object BiometricAuthHolder {
    private var activityRef: WeakReference<FragmentActivity>? = null

    fun setActivity(activity: FragmentActivity) {
        activityRef = WeakReference(activity)
    }

    fun clearActivity() {
        activityRef = null
    }

    internal fun getActivity(): FragmentActivity? = activityRef?.get()
    internal fun getContext(): Context? = activityRef?.get()
}

class AndroidBiometricAuth : BiometricAuth {

    companion object {
        private const val TAG = "BiometricAuth"
    }

    private val context: Context?
        get() = BiometricAuthHolder.getContext()

    private val activity: FragmentActivity?
        get() = BiometricAuthHolder.getActivity()

    override fun isAvailable(): Boolean {
        val ctx = context ?: return false
        val biometricManager = BiometricManager.from(ctx)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
                BiometricManager.BIOMETRIC_SUCCESS
    }

    override fun getBiometricType(): BiometricType {
        val ctx = context ?: return BiometricType.NONE

        if (!isAvailable()) return BiometricType.NONE

        val packageManager = ctx.packageManager
        return when {
            packageManager.hasSystemFeature("android.hardware.fingerprint") -> BiometricType.FINGERPRINT
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    packageManager.hasSystemFeature("android.hardware.biometrics.face") -> BiometricType.FACE
            else -> BiometricType.UNKNOWN
        }
    }

    override fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit
    ) {
        val act = activity
        if (act == null) {
            onError("Activity não disponível. Chame BiometricAuthHolder.setActivity() em onResume()")
            return
        }

        if (!isAvailable()) {
            onError("Biometria não disponível")
            return
        }

        val executor = ContextCompat.getMainExecutor(act)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                AppLogger.d(TAG, "Autenticação bem-sucedida")
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                AppLogger.e(TAG, "Erro na autenticação: $errorCode - $errString")
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onCancel()
                    else -> onError(errString.toString())
                }
            }

            override fun onAuthenticationFailed() {
                AppLogger.w(TAG, "Autenticação falhou (biometria não reconhecida)")
                // Não chamamos callback aqui, o sistema permite novas tentativas
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancelar")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val biometricPrompt = BiometricPrompt(act, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}

actual fun getBiometricAuth(): BiometricAuth = AndroidBiometricAuth()

package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.util.Locale

/** Desktop: a região do usuário no SO, como a JVM a leu na partida (`user.country`). */
actual fun platformRegionCode(): String? = Locale.getDefault().country.takeIf { it.isNotBlank() }

/**
 * Desktop: câmera do sistema e geração de PDF da lib NÃO existem no alvo `jvm` (os módulos
 * `kmplib-camera` e `kmplib-pdf` não têm alvo desktop) — o app não oferece essas features no PC.
 */
actual object PlatformCapabilities {
    actual val cameraCapture: Boolean = false
    actual val pdfGeneration: Boolean = false
}

/** Desktop: sem biometria acessível à JVM (Windows Hello é WinRT). `isDeviceSecured` = `false`. */
internal object DesktopBiometricAuth : BiometricAuth {
    override fun isAvailable(): Boolean = false
    override fun getBiometricType(): BiometricType = BiometricType.NONE
    override fun isDeviceSecured(): Boolean = false
    override fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
        onCancel: () -> Unit,
    ) = onError("Biometria indisponível neste computador.")
}

actual fun getBiometricAuth(): BiometricAuth = DesktopBiometricAuth

/** Desktop: a JVM não lê bateria. Monitor inerte (`isAvailable = false`). */
actual fun createBatteryMonitor(): BatteryMonitor = UnavailableBatteryMonitor

/** Desktop: a JVM não controla o brilho do monitor. */
actual fun createScreenBrightnessController(): ScreenBrightnessController = UnavailableScreenBrightnessController

@Composable
actual fun rememberScreenBrightnessController(): ScreenBrightnessController =
    remember { UnavailableScreenBrightnessController }

/** Desktop: sem acelerômetro. */
actual fun createShakeDetector(sensitivity: ShakeSensitivity): ShakeDetector = UnavailableShakeDetector(sensitivity)

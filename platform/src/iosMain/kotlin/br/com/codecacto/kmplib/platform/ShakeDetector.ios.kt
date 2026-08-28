@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.useContents
import platform.CoreMotion.CMMotionManager
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

private const val TAG = "ShakeDetector"
private const val UPDATE_INTERVAL_SECONDS = 1.0 / 50.0

/** Cria o detector de chacoalhar do iOS. */
actual fun createShakeDetector(sensitivity: ShakeSensitivity): ShakeDetector =
    IosShakeDetector(sensitivity)

/**
 * **Padrão-ouro do iOS: Core Motion (`CMMotionManager`).**
 *
 * O gesto de *shake* que o UIKit oferece de graça (`motionEnded:`) não serve aqui: ele não tem
 * sensibilidade ajustável e depende da cadeia de resposta da tela. Core Motion entrega a aceleração
 * bruta, **já em g** — a mesma unidade que o Android usa depois de dividir por `GRAVITY_EARTH`.
 *
 * A 50 Hz, o mesmo ritmo do `SENSOR_DELAY_GAME` do Android. O sistema **suspende** as atualizações
 * quando o app sai da tela: é limite do SO, não do módulo.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** o build Kotlin/Native iOS não roda no servidor Linux.
 */
internal class IosShakeDetector(sensitivity: ShakeSensitivity) : ShakeDetector {

    private val motionManager = CMMotionManager()
    private val analyzer = ShakeAnalyzer(sensitivity)

    override val isAvailable: Boolean get() = motionManager.accelerometerAvailable

    override var isRunning: Boolean = false
        private set

    override var sensitivity: ShakeSensitivity
        get() = analyzer.sensitivity
        set(value) {
            analyzer.sensitivity = value
        }

    override fun start(onShake: () -> Unit): Boolean {
        if (!motionManager.accelerometerAvailable) return false
        if (isRunning) return true
        analyzer.reset()
        motionManager.accelerometerUpdateInterval = UPDATE_INTERVAL_SECONDS
        motionManager.startAccelerometerUpdatesToQueue(NSOperationQueue.mainQueue) { data, error ->
            if (error != null) {
                AppLogger.w(TAG, "Acelerômetro reportou erro: ${error.localizedDescription}")
                return@startAccelerometerUpdatesToQueue
            }
            val acceleration = data?.acceleration ?: return@startAccelerometerUpdatesToQueue
            val now = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
            val shook = acceleration.useContents {
                analyzer.onAcceleration(
                    x = x.toFloat(),
                    y = y.toFloat(),
                    z = z.toFloat(),
                    nowMillis = now,
                )
            }
            if (shook) onShake()
        }
        isRunning = true
        return true
    }

    override fun stop() {
        if (!isRunning) return
        motionManager.stopAccelerometerUpdates()
        isRunning = false
    }
}

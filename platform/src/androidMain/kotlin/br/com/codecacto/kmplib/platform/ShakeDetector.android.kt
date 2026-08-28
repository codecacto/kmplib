package br.com.codecacto.kmplib.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import br.com.codecacto.kmplib.core.util.AppLogger
import java.lang.ref.WeakReference

private const val TAG = "ShakeDetector"

/**
 * Holder do [Context] da aplicação para o [ShakeDetector]. Inicializado por `KmpLib.init(context)`.
 */
object ShakeDetectorHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}

/** Cria o detector de chacoalhar do Android. */
actual fun createShakeDetector(sensitivity: ShakeSensitivity): ShakeDetector {
    val context = ShakeDetectorHolder.getContext() ?: run {
        AppLogger.e(TAG, "KmpLib.init(context) não foi chamado — gesto de chacoalhar indisponível")
        return UnavailableShakeDetector(sensitivity)
    }
    return AndroidShakeDetector(context, sensitivity)
}

/**
 * **Padrão-ouro do Android: `SensorManager` + `TYPE_ACCELEROMETER`.**
 *
 * `SENSOR_DELAY_GAME` (~50 Hz) é a taxa que a documentação indica para detecção de gesto: rápida o
 * bastante para pegar um chacoalhão curto, sem o custo de `SENSOR_DELAY_FASTEST`.
 *
 * As amostras chegam em **m/s²** e são divididas por `GRAVITY_EARTH` antes de entrar no
 * [ShakeAnalyzer] — é o que faz o mesmo gesto disparar igual no Android e no iOS.
 */
internal class AndroidShakeDetector(
    context: Context,
    sensitivity: ShakeSensitivity,
) : ShakeDetector {

    private val sensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val accelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val analyzer = ShakeAnalyzer(sensitivity)

    private var callback: (() -> Unit)? = null

    override val isAvailable: Boolean get() = accelerometer != null

    override var isRunning: Boolean = false
        private set

    override var sensitivity: ShakeSensitivity
        get() = analyzer.sensitivity
        set(value) {
            analyzer.sensitivity = value
        }

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val values = event.values
            if (values.size < 3) return
            val shook = analyzer.onAcceleration(
                x = values[0] / SensorManager.GRAVITY_EARTH,
                y = values[1] / SensorManager.GRAVITY_EARTH,
                z = values[2] / SensorManager.GRAVITY_EARTH,
                nowMillis = event.timestamp / 1_000_000L,
            )
            if (shook) callback?.invoke()
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    override fun start(onShake: () -> Unit): Boolean {
        val sensor = accelerometer ?: return false
        if (isRunning) return true
        callback = onShake
        analyzer.reset()
        val registered = runCatching {
            sensorManager?.registerListener(
                listener,
                sensor,
                SensorManager.SENSOR_DELAY_GAME,
                Handler(Looper.getMainLooper()),
            )
        }.getOrDefault(false) == true
        isRunning = registered
        if (!registered) AppLogger.w(TAG, "Não foi possível registrar o acelerômetro")
        return registered
    }

    override fun stop() {
        if (!isRunning) return
        runCatching { sensorManager?.unregisterListener(listener) }
        isRunning = false
        callback = null
    }
}

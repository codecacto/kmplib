package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlin.math.sqrt

/**
 * **Quão forte é preciso sacudir** para o gesto contar.
 *
 * [thresholdG] é a aceleração total em **g** (com a gravidade inclusa: parado no bolso, o aparelho
 * mede ~1g). Quanto MENOR o limiar, mais sensível — e mais fácil disparar sem querer ao andar.
 *
 * [cooldownMillis] evita que um único chacoalhão vire cinco eventos: o acelerômetro reporta dezenas
 * de amostras por segundo, e todas passam do limiar durante o mesmo movimento.
 */
data class ShakeSensitivity(
    val thresholdG: Float,
    val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
) {
    init {
        require(thresholdG > 1f) { "thresholdG deve superar 1g (o aparelho parado já mede 1g)" }
        require(cooldownMillis >= 0) { "cooldownMillis não pode ser negativo" }
    }

    companion object {
        /** Janela mínima entre dois gestos reconhecidos. */
        const val DEFAULT_COOLDOWN_MILLIS: Long = 600L

        /** Limiar do extremo "mais sensível" do slider. */
        const val MOST_SENSITIVE_G: Float = 1.6f

        /** Limiar do extremo "menos sensível" do slider. */
        const val LEAST_SENSITIVE_G: Float = 3.4f

        /** Precisa de um chacoalhão firme — quase não dispara sozinho. */
        val LOW: ShakeSensitivity = ShakeSensitivity(3.0f)

        /** Equilíbrio recomendado (default). */
        val MEDIUM: ShakeSensitivity = ShakeSensitivity(2.3f)

        /** Dispara com um movimento curto — cuidado com o falso positivo ao andar. */
        val HIGH: ShakeSensitivity = ShakeSensitivity(1.8f)

        /**
         * Converte a posição do slider em limiar. `0f` = menos sensível, `1f` = mais sensível —
         * a direção que a pessoa espera ("arrastar para a direita deixa mais fácil disparar"), e
         * que é o inverso do limiar em g.
         */
        fun fromFraction(fraction: Float): ShakeSensitivity {
            val clamped = fraction.coerceIn(0f, 1f)
            val threshold = LEAST_SENSITIVE_G - (LEAST_SENSITIVE_G - MOST_SENSITIVE_G) * clamped
            return ShakeSensitivity(threshold)
        }
    }
}

/**
 * **A decisão de "isto foi um chacoalhão", em código comum e testável.**
 *
 * Os `actual` só leem o acelerômetro e entregam as amostras **já normalizadas em g** (o Android
 * divide por `SensorManager.GRAVITY_EARTH`; o iOS já reporta em g). Assim as duas plataformas
 * disparam com o mesmo gesto, e o comportamento é testado sem aparelho.
 *
 * Regra: aceleração total acima de [ShakeSensitivity.thresholdG] dispara **uma** vez, e o
 * [ShakeSensitivity.cooldownMillis] segurando o resto da rajada.
 */
class ShakeAnalyzer(
    /** Sensibilidade corrente — pode ser trocada em runtime pelo slider de ajustes. */
    var sensitivity: ShakeSensitivity = ShakeSensitivity.MEDIUM,
) {

    private var lastShakeAt: Long = Long.MIN_VALUE

    /**
     * Alimenta uma amostra do acelerômetro (eixos em **g**, gravidade inclusa).
     *
     * @return `true` quando a amostra fecha um gesto de chacoalhar.
     */
    fun onAcceleration(x: Float, y: Float, z: Float, nowMillis: Long): Boolean {
        val gForce = sqrt(x * x + y * y + z * z)
        if (gForce < sensitivity.thresholdG) return false
        if (lastShakeAt != Long.MIN_VALUE && nowMillis - lastShakeAt < sensitivity.cooldownMillis) {
            return false
        }
        lastShakeAt = nowMillis
        return true
    }

    /** Esquece o último gesto (ex.: ao religar a detecção). */
    fun reset() {
        lastShakeAt = Long.MIN_VALUE
    }
}

/**
 * Detecção do gesto de **chacoalhar o aparelho**, com sensibilidade ajustável.
 *
 * A capacidade é **consultável**: [isAvailable] é `false` no aparelho sem acelerômetro, e aí o app
 * some com a opção em vez de oferecer um ajuste que nunca dispara.
 *
 * - **Android:** `SensorManager` + `Sensor.TYPE_ACCELEROMETER` (`SENSOR_DELAY_GAME`).
 * - **iOS:** `CMMotionManager.startAccelerometerUpdates` (Core Motion). A leitura é suspensa pelo
 *   sistema quando o app sai da tela — é o limite do SO, não do módulo.
 *
 * Quem inicia, para: [stop] cancela o registro no sensor (bateria).
 */
interface ShakeDetector {

    /** `false` quando o aparelho não tem acelerômetro utilizável. */
    val isAvailable: Boolean

    /** `true` entre um [start] bem-sucedido e o [stop]. */
    val isRunning: Boolean

    /** Sensibilidade corrente; pode ser trocada com a detecção ligada. */
    var sensitivity: ShakeSensitivity

    /**
     * Liga a detecção. [onShake] é chamado na main thread, uma vez por gesto.
     *
     * @return `false` se o aparelho não tem o sensor (nada foi registrado).
     */
    fun start(onShake: () -> Unit): Boolean

    /** Desliga a detecção e solta o sensor. */
    fun stop()
}

/** Cria o detector de chacoalhar da plataforma atual. */
expect fun createShakeDetector(sensitivity: ShakeSensitivity = ShakeSensitivity.MEDIUM): ShakeDetector

/**
 * Detector com ciclo de vida atrelado à composição: liga com [enabled], **para no `onDispose`**.
 *
 * @return o detector, para consultar [ShakeDetector.isAvailable] e decidir se mostra o ajuste.
 */
@Composable
fun rememberShakeDetector(
    enabled: Boolean,
    sensitivity: ShakeSensitivity = ShakeSensitivity.MEDIUM,
    onShake: () -> Unit,
): ShakeDetector {
    val detector = remember { createShakeDetector(sensitivity) }
    // O callback mais recente sem religar o sensor a cada recomposição.
    val currentOnShake by rememberUpdatedState(onShake)
    DisposableEffect(detector, enabled, sensitivity) {
        detector.sensitivity = sensitivity
        if (enabled) detector.start { currentOnShake() }
        onDispose { detector.stop() }
    }
    return detector
}

/** Detector inerte: o aparelho não tem acelerômetro (ou o Context não foi registrado). */
internal class UnavailableShakeDetector(
    override var sensitivity: ShakeSensitivity = ShakeSensitivity.MEDIUM,
) : ShakeDetector {
    override val isAvailable: Boolean = false
    override val isRunning: Boolean = false
    override fun start(onShake: () -> Unit): Boolean = false
    override fun stop() = Unit
}

package br.com.codecacto.kmplib.platform.haptics

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.CoreHaptics.CHHapticEngine
import platform.CoreHaptics.CHHapticEvent
import platform.CoreHaptics.CHHapticEventParameter
import platform.CoreHaptics.CHHapticEventParameterIDHapticIntensity
import platform.CoreHaptics.CHHapticEventParameterIDHapticSharpness
import platform.CoreHaptics.CHHapticEventTypeHapticContinuous
import platform.CoreHaptics.CHHapticPattern
import platform.CoreHaptics.CHHapticPatternPlayerProtocol
import platform.CoreHaptics.CHHapticTimeImmediate
import platform.Foundation.NSError
import platform.Foundation.NSThread
import platform.UIKit.UIDevice
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UIUserInterfaceIdiomPhone
import platform.darwin.DISPATCH_TIME_NOW
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.darwin.NSEC_PER_MSEC

private const val TAG = "Haptics"

internal actual fun createPlatformHapticPlayer(): HapticPlayer = IosHapticPlayer()

/**
 * **Padrão-ouro do iOS: Core Haptics** (`CHHapticEngine`), com um evento `hapticContinuous` por
 * trecho de vibração (intensidade pelo parâmetro `hapticIntensity`). A engine é criada uma vez, com
 * `playsHapticsOnly` e desligamento automático quando ociosa; o `resetHandler` (servidor háptico
 * reiniciado) e o `stoppedHandler` (app em segundo plano, interrupção) só soltam o player — o
 * próximo `vibrate` religa a engine.
 *
 * Sem Core Haptics (iPhone 7, aparelhos antigos) cai para `UIImpactFeedbackGenerator`: um impacto no
 * início de cada trecho e a cada [IMPACT_REPEAT_MILLIS] dentro dele — aproximação, o gerador não tem
 * duração. Os dois respeitam "Tátil do Sistema" (o iOS não informa se está desligado).
 *
 * Em segundo plano o iOS suspende a engine do app: aviso com o app fechado vai pela notificação.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal class IosHapticPlayer : HapticPlayer {

    private val supportsCoreHaptics: Boolean =
        runCatching { CHHapticEngine.capabilitiesForHardware().supportsHaptics }.getOrDefault(false)

    private var engine: CHHapticEngine? = null
    private var player: CHHapticPatternPlayerProtocol? = null

    /** Cancela os impactos agendados do fallback: cada `vibrate`/`cancel` invalida os anteriores. */
    private var generation: Long = 0

    override val isSupported: Boolean
        get() = supportsCoreHaptics || UIDevice.currentDevice.userInterfaceIdiom == UIUserInterfaceIdiomPhone

    override fun vibrate(pattern: VibrationPattern, usage: HapticUsage): HapticOutcome {
        if (!isSupported) return HapticOutcome.UNSUPPORTED
        if (!NSThread.isMainThread) {
            // Engine e gerador de impacto são de uso na thread principal.
            dispatch_async(dispatch_get_main_queue()) { vibrateOnMain(pattern) }
            return HapticOutcome.PLAYED
        }
        return vibrateOnMain(pattern)
    }

    override fun cancel() {
        val parar = {
            generation++
            stopPlayer()
        }
        if (NSThread.isMainThread) parar() else dispatch_async(dispatch_get_main_queue()) { parar() }
    }

    private fun vibrateOnMain(pattern: VibrationPattern): HapticOutcome {
        generation++
        stopPlayer()
        return if (supportsCoreHaptics) playCoreHaptics(pattern) else playImpacts(pattern)
    }

    private fun stopPlayer() {
        player?.let { runCatching { it.stopAtTime(CHHapticTimeImmediate, null) } }
        player = null
    }

    private fun startedEngine(err: ObjCObjectVar<NSError?>): CHHapticEngine? {
        val eng = engine ?: runCatching { CHHapticEngine(andReturnError = err.ptr) }.getOrNull()?.also { e ->
            e.playsHapticsOnly = true
            e.autoShutdownEnabled = true
            e.resetHandler = { player = null }
            e.stoppedHandler = { _ -> player = null }
            engine = e
        } ?: return null
        return if (eng.startAndReturnError(err.ptr)) eng else null
    }

    private fun playCoreHaptics(pattern: VibrationPattern): HapticOutcome = memScoped {
        val err = alloc<ObjCObjectVar<NSError?>>()
        val eng = startedEngine(err) ?: return@memScoped falhou("engine", err.value)
        val events = pattern.timedVibrations().map { v ->
            CHHapticEvent(
                eventType = CHHapticEventTypeHapticContinuous,
                parameters = listOf(
                    CHHapticEventParameter(CHHapticEventParameterIDHapticIntensity, v.intensity),
                    CHHapticEventParameter(CHHapticEventParameterIDHapticSharpness, SHARPNESS),
                ),
                relativeTime = v.startMillis / 1000.0,
                duration = v.durationMillis / 1000.0,
            )
        }
        val hapticPattern = CHHapticPattern(events = events, parameters = emptyList<Any>(), error = err.ptr)
        val novo = eng.createPlayerWithPattern(hapticPattern, err.ptr) ?: return@memScoped falhou("player", err.value)
        if (!novo.startAtTime(CHHapticTimeImmediate, err.ptr)) return@memScoped falhou("start", err.value)
        player = novo
        HapticOutcome.PLAYED
    }

    private fun playImpacts(pattern: VibrationPattern): HapticOutcome {
        val minha = generation
        val generator = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
        generator.prepare()
        pattern.impactSchedule().forEach { (atMillis, intensity) ->
            val tocar = {
                if (generation == minha) generator.impactOccurredWithIntensity(intensity.toDouble())
            }
            if (atMillis == 0L) {
                tocar()
            } else {
                dispatch_after(
                    dispatch_time(DISPATCH_TIME_NOW, atMillis * NSEC_PER_MSEC.toLong()),
                    dispatch_get_main_queue(),
                ) { tocar() }
            }
        }
        return HapticOutcome.PLAYED
    }

    private fun falhou(etapa: String, error: NSError?): HapticOutcome {
        AppLogger.e(TAG, "Core Haptics recusou ($etapa): ${error?.code ?: "sem código"}")
        return HapticOutcome.FAILED
    }

    private companion object {
        /** Nitidez média: vibração "cheia", nem pancada seca nem zumbido. */
        const val SHARPNESS = 0.5f
    }
}

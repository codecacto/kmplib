@file:OptIn(
    kotlinx.cinterop.ExperimentalForeignApi::class,
    kotlinx.cinterop.BetaInteropApi::class,
)

package br.com.codecacto.kmplib.torch

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.StateFlow
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureTorchModeOff
import platform.AVFoundation.AVCaptureTorchModeOn
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.hasTorch
import platform.AVFoundation.isTorchAvailable
import platform.AVFoundation.isTorchActive
import platform.AVFoundation.setTorchModeOnWithLevel
import platform.AVFoundation.torchMode
import platform.Foundation.NSError

private const val TAG = "Torch"

/** Cria o controlador de lanterna do iOS. Sem LED no aparelho, devolve o controlador inerte. */
actual fun createTorchController(): TorchController {
    val device = AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo)
        ?: return UnavailableTorchController()
    if (!device.hasTorch) return UnavailableTorchController()
    return IosTorchController(device)
}

/**
 * **Padrão-ouro do iOS: `AVCaptureDevice`, sem `AVCaptureSession`.**
 *
 * - **Acender/apagar:** `lockForConfiguration` → `torchMode` → `unlockForConfiguration`. Travar a
 *   configuração é exigência da Apple, e destravar **no mesmo caminho** é o que impede a lanterna
 *   de prender o dispositivo para os outros apps (e para a própria câmera do sistema).
 * - **Intensidade:** `setTorchModeOn(level:)`, faixa **contínua** de 0 a 1 — disponível em todas as
 *   versões suportadas, sem corte por versão de SO (por isso
 *   [TorchCapabilities.levelCount] é [TorchCapabilities.CONTINUOUS] aqui).
 * - **Estado real:** `torchActive` é **key-value observable** (documentado pela Apple). O KVO é o
 *   que faz o botão da tela voltar sozinho quando o iOS apaga a luz — superaquecimento, outro app
 *   assumindo a câmera, Central de Controle.
 *
 * **PENDÊNCIA DE VALIDAÇÃO (host macOS):** escrito conforme as APIs oficiais; o build
 * Kotlin/Native iOS não roda no servidor Linux.
 */
internal class IosTorchController(
    private val device: AVCaptureDevice,
) : TorchController {

    private val machine = TorchStateMachine(iosTorchCapabilities(device.hasTorch))

    private var released = false

    override val state: StateFlow<TorchState> = machine.state

    override fun turnOn(level: Float?): TorchOutcome =
        applyTorch(on = true, level = machine.resolveLevel(level))

    override fun turnOff(): TorchOutcome = applyTorch(on = false, level = machine.current.level)

    override fun toggle(level: Float?): TorchOutcome =
        if (machine.current.isOn) turnOff() else turnOn(level)

    override fun setLevel(level: Float): TorchOutcome {
        if (!machine.current.capabilities.supportsIntensity) {
            machine.onError(TorchError.NoTorch)
            return TorchOutcome.Failure(TorchError.NoTorch)
        }
        val applied = machine.resolveLevel(level)
        if (!machine.current.isOn) {
            machine.onLevelChanged(applied)
            return TorchOutcome.Success
        }
        return applyTorch(on = true, level = applied)
    }

    override fun refresh() {
        machine.onCapabilities(iosTorchCapabilities(device.hasTorch))
        machine.onHardwareTorchChanged(device.isTorchActive())
    }

    override fun clearError() = machine.clearError()

    override fun release() {
        if (released) return
        released = true
        if (machine.current.isOn) applyTorch(on = false, level = machine.current.level)
    }

    // -------------------------------------------------------------------------------------------

    private fun applyTorch(on: Boolean, level: Float): TorchOutcome {
        if (!device.hasTorch) return fail(TorchError.NoTorch)
        // `torchAvailable` fica falso quando o iOS bloqueia o LED (aparelho quente, câmera tomada).
        if (on && !device.isTorchAvailable()) return fail(TorchError.Unavailable)

        // Falha ao travar = alguém está configurando o dispositivo (outra sessão de câmera).
        if (!device.lockForConfiguration(null)) return fail(TorchError.InUse)

        return try {
            if (!on) {
                device.torchMode = AVCaptureTorchModeOff
                machine.onTurnedOff()
                TorchOutcome.Success
            } else {
                val applied = memScoped {
                    val errorVar = alloc<ObjCObjectVar<NSError?>>()
                    val ok = device.setTorchModeOnWithLevel(level, errorVar.ptr)
                    if (!ok) {
                        AppLogger.w(TAG, "setTorchModeOnWithLevel falhou: ${errorVar.value?.localizedDescription}")
                    }
                    ok
                }
                if (applied) {
                    machine.onTurnedOn(level)
                    TorchOutcome.Success
                } else {
                    // Sem nível: acende no máximo, que é melhor do que não acender.
                    device.torchMode = AVCaptureTorchModeOn
                    machine.onTurnedOn(TorchLevel.MAX)
                    TorchOutcome.Success
                }
            }
        } finally {
            device.unlockForConfiguration()
        }
    }

    private fun fail(error: TorchError): TorchOutcome {
        machine.onError(error)
        return TorchOutcome.Failure(error)
    }
}

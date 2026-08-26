package br.com.codecacto.kmplib.torch

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.flow.StateFlow
import java.lang.ref.WeakReference

private const val TAG = "Torch"

/**
 * Holder do [Context] da aplicação para a lanterna no Android. Inicializado por
 * `KmpLib.init(context)` — nenhum app precisa chamar isto à mão.
 */
object TorchControllerHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}

/**
 * Cria o controlador de lanterna do Android.
 *
 * Sem `KmpLib.init(context)` no `Application.onCreate()`, devolve um controlador inerte que reporta
 * [TorchError.NoTorch] — em vez de estourar no primeiro toque.
 */
actual fun createTorchController(): TorchController {
    val context = TorchControllerHolder.getContext() ?: run {
        AppLogger.e(TAG, "KmpLib.init(context) não foi chamado — lanterna indisponível")
        return UnavailableTorchController()
    }
    return AndroidTorchController(context)
}

/**
 * **Padrão-ouro do Android: `CameraManager`, sem sessão de câmera.**
 *
 * - **Acender/apagar:** `CameraManager.setTorchMode(cameraId, Boolean)` — não abre a câmera, não
 *   pede permissão `CAMERA` e não impede outros apps de usá-la.
 * - **Estado real:** `CameraManager.registerTorchCallback(...)`. É o SO quem informa quando a
 *   lanterna acende ou apaga, **inclusive quando não fomos nós** — outro app pegou a câmera, o
 *   aparelho esquentou, a pessoa usou o atalho das Configurações Rápidas.
 * - **Intensidade:** `turnOnTorchWithStrengthLevel(cameraId, level)` a partir do **Android 13
 *   (API 33)**, com o teto lido de `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL`. Teto `1` ou SO anterior ao
 *   13 ⇒ a capacidade é reportada **ausente** ([TorchCapabilities.supportsIntensity] `false`), e
 *   não se simula nada piscando o LED.
 *
 * A câmera escolhida é a **primeira com unidade de flash**, preferindo a traseira.
 */
internal class AndroidTorchController(context: Context) : TorchController {

    private val cameraManager =
        context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

    private val cameraId: String? = cameraManager?.let(::findTorchCameraId)

    private val machine = TorchStateMachine(readCapabilities())

    private var released = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id == cameraId) machine.onHardwareTorchChanged(enabled)
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id != cameraId) return
            // Câmera tomada por outro app / aparelho superaquecido: a luz apagou e não é nossa.
            machine.onHardwareTorchChanged(false)
        }
    }

    init {
        runCatching {
            cameraManager?.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
        }.onFailure { AppLogger.w(TAG, "Falha ao registrar o TorchCallback: ${it.message}") }
    }

    override val state: StateFlow<TorchState> = machine.state

    override fun turnOn(level: Float?): TorchOutcome {
        val applied = machine.resolveLevel(level)
        return applyTorch(on = true, level = applied)
    }

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
        machine.onCapabilities(readCapabilities())
    }

    override fun clearError() = machine.clearError()

    override fun release() {
        if (released) return
        released = true
        runCatching { if (machine.current.isOn) setTorchMode(false, machine.current.level) }
        runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
    }

    // -------------------------------------------------------------------------------------------

    private fun applyTorch(on: Boolean, level: Float): TorchOutcome {
        val capabilities = machine.current.capabilities
        if (!capabilities.hasTorch || cameraId == null || cameraManager == null) {
            machine.onError(TorchError.NoTorch)
            return TorchOutcome.Failure(TorchError.NoTorch)
        }
        if (released) {
            machine.onError(TorchError.Unavailable)
            return TorchOutcome.Failure(TorchError.Unavailable)
        }
        return try {
            setTorchMode(on, level)
            // O callback do SO confirma o estado real logo em seguida; publicar aqui é o que faz o
            // botão responder no mesmo frame do toque.
            if (on) machine.onTurnedOn(level) else machine.onTurnedOff()
            TorchOutcome.Success
        } catch (e: CameraAccessException) {
            val error = TorchAccessDenialReason.fromAndroidReason(e.reason).asTorchError
            AppLogger.w(TAG, "CameraAccessException(reason=${e.reason}) ao alternar a lanterna")
            machine.onError(error)
            TorchOutcome.Failure(error)
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "Acesso à câmera negado: ${e.message}")
            machine.onError(TorchError.PermissionDenied)
            TorchOutcome.Failure(TorchError.PermissionDenied)
        } catch (e: IllegalArgumentException) {
            AppLogger.w(TAG, "Nível de lanterna inválido: ${e.message}")
            machine.onError(TorchError.Unavailable)
            TorchOutcome.Failure(TorchError.Unavailable)
        } catch (e: RuntimeException) {
            AppLogger.e(TAG, "Falha ao alternar a lanterna", e)
            val error = TorchError.Unknown(e.message)
            machine.onError(error)
            TorchOutcome.Failure(error)
        }
    }

    private fun setTorchMode(on: Boolean, level: Float) {
        val manager = cameraManager ?: return
        val id = cameraId ?: return
        val capabilities = machine.current.capabilities
        if (on &&
            capabilities.supportsIntensity &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            manager.turnOnTorchWithStrengthLevel(
                id,
                TorchLevel.toStep(level, capabilities.levelCount),
            )
        } else {
            manager.setTorchMode(id, on)
        }
    }

    private fun readCapabilities(): TorchCapabilities {
        val manager = cameraManager ?: return TorchCapabilities.NONE
        val id = cameraId ?: return TorchCapabilities.NONE
        val maxLevel = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) 1
            else manager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
        }.getOrDefault(1)

        return androidTorchCapabilities(
            hasFlashUnit = true,
            sdkInt = Build.VERSION.SDK_INT,
            maxLevel = maxLevel,
        )
    }
}

/** Primeira câmera com unidade de flash, preferindo a traseira (é onde mora o LED útil). */
private fun findTorchCameraId(manager: CameraManager): String? = runCatching {
    val withFlash = manager.cameraIdList.filter { id ->
        manager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }
    withFlash.firstOrNull { id ->
        manager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
    } ?: withFlash.firstOrNull()
}.getOrNull()

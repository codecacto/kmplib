package br.com.codecacto.kmplib.torch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * **Lanterna do aparelho — o hardware, sem sessão de câmera.**
 *
 * Acender o flash não exige (e não deve exigir) montar um preview de câmera: no Android o caminho
 * oficial é `CameraManager.setTorchMode`, no iOS é `AVCaptureDevice.torchMode`. Antes deste módulo,
 * a única lanterna da kmplib vivia **dentro** do `CameraView`/`BarcodeScannerView` — um app de
 * lanterna teria de subir uma sessão de leitura de código de barras para acender uma luz.
 *
 * Três garantias:
 * - **[state] reflete o hardware.** O SO apagou sozinho (outro app pegou a câmera, aparelho
 *   esquentou, Central de Controle) ⇒ o estado muda sem o app pedir nada.
 * - **Intensidade real ou nada.** [TorchCapabilities.supportsIntensity] diz se o slider deve
 *   existir. Onde não há suporte nativo, **não** se simula piscando o LED (PWM aquece e desgasta o
 *   componente) — a capacidade é reportada ausente e ponto.
 * - **Erro tipado.** Comandos devolvem [TorchOutcome]; nada de exceção crua chegando à tela.
 *
 * ## Ciclo de vida
 *
 * Quem cria, libera. [release] apaga a luz e solta o recurso da câmera — sem isso a lanterna pode
 * ficar acesa depois de a tela morrer, e o callback do SO segue registrado.
 *
 * ```kotlin
 * class LanternaViewModel : ViewModel() {
 *     private val torch = createTorchController()
 *     val state = torch.state
 *
 *     fun onToggle() = torch.toggle()
 *     fun onLevel(v: Float) = torch.setLevel(v)
 *
 *     override fun onCleared() { torch.release(); super.onCleared() }
 * }
 * ```
 *
 * Para uma lanterna que vive numa tela só, [rememberTorchController] faz o `release` sozinho.
 *
 * ## Threading
 *
 * Os comandos são **síncronos e rápidos** de propósito — o tempo entre o toque e a luz é a métrica
 * do produto. Chame da main thread; o estado é publicado por [StateFlow].
 */
interface TorchController {

    /** Estado observável: aceso/apagado, intensidade, capacidades e último erro. */
    val state: StateFlow<TorchState>

    /**
     * O que este aparelho suporta — **consultável antes de desenhar a tela** (sem acender nada).
     * Atalho para `state.value.capabilities`.
     */
    val capabilities: TorchCapabilities get() = state.value.capabilities

    /**
     * Acende a lanterna.
     *
     * @param level intensidade de `0f` a `1f`; `null` mantém a atual. Ignorado (tratado como
     *   máximo) quando o aparelho não tem intensidade variável.
     */
    fun turnOn(level: Float? = null): TorchOutcome

    /** Apaga a lanterna. Sem efeito se já estiver apagada. */
    fun turnOff(): TorchOutcome

    /** Alterna: apaga se acesa, acende (com [level], ou a intensidade atual) se apagada. */
    fun toggle(level: Float? = null): TorchOutcome

    /**
     * Ajusta a intensidade. Com a luz **acesa**, aplica na hora; **apagada**, guarda o valor para o
     * próximo [turnOn]. Devolve [TorchError.NoTorch] quando o aparelho não tem intensidade
     * variável — o app não deveria ter mostrado o controle (ver [TorchCapabilities]).
     */
    fun setLevel(level: Float): TorchOutcome

    /**
     * Reconsulta o hardware e publica o estado real. Útil ao voltar do segundo plano; o callback do
     * SO já cobre o caso normal.
     */
    fun refresh()

    /** Limpa [TorchState.error] (o app fechou o aviso). */
    fun clearError()

    /**
     * Apaga a luz, cancela a observação do SO e solta o recurso. **Obrigatório** ao descartar o
     * dono (`onCleared`, `onDispose`). Depois disto a instância não deve ser reutilizada.
     */
    fun release()
}

/** Cria o controlador de lanterna da plataforma atual. */
expect fun createTorchController(): TorchController

/**
 * Controlador com ciclo de vida atrelado à composição: criado uma vez, **liberado no `onDispose`**
 * (o que apaga a luz). Use quando a lanterna pertence a esta tela; se ela deve sobreviver à
 * navegação, guarde um [createTorchController] no ViewModel.
 */
@Composable
fun rememberTorchController(): TorchController {
    val controller = remember { createTorchController() }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }
    return controller
}

/**
 * Controlador inerte, para quando a plataforma não entrega uma lanterna utilizável (aparelho sem
 * flash, ou `KmpLib.init` não chamado no Android). Existe para que o app receba
 * [TorchError.NoTorch] tipado em vez de um crash ou de um controlador nulo que cada tela teria de
 * checar.
 */
internal class UnavailableTorchController(
    private val error: TorchError = TorchError.NoTorch,
) : TorchController {

    private val _state = MutableStateFlow(TorchState(capabilities = TorchCapabilities.NONE))
    override val state: StateFlow<TorchState> = _state.asStateFlow()

    private fun fail(): TorchOutcome {
        _state.value = _state.value.copy(isOn = false, error = error)
        return TorchOutcome.Failure(error)
    }

    override fun turnOn(level: Float?): TorchOutcome = fail()
    override fun turnOff(): TorchOutcome = TorchOutcome.Success
    override fun toggle(level: Float?): TorchOutcome = fail()
    override fun setLevel(level: Float): TorchOutcome = fail()
    override fun refresh() = Unit
    override fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun release() = Unit
}

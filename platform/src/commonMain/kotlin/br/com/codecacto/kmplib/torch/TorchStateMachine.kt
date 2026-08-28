package br.com.codecacto.kmplib.torch

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * **Máquina de estado da lanterna, em código comum.**
 *
 * Os dois `actual` (Android/iOS) só falam com o hardware; toda a decisão de "o que o estado vira
 * depois disto" acontece aqui — o que faz Android e iOS se comportarem igual e permite testar o
 * comportamento sem aparelho.
 *
 * Distinção que ela existe para manter: **comando do app** ([onTurnedOn]/[onTurnedOff]) e
 * **fato do hardware** ([onHardwareTorchChanged]) são entradas diferentes. A segunda é a que chega
 * quando o SO apaga a luz por conta própria, e ela não pode ser confundida com um pedido do
 * usuário.
 */
internal class TorchStateMachine(capabilities: TorchCapabilities = TorchCapabilities.NONE) {

    private val _state = MutableStateFlow(
        TorchState(
            isOn = false,
            level = TorchLevel.MAX,
            capabilities = capabilities,
        )
    )

    val state: StateFlow<TorchState> = _state.asStateFlow()

    val current: TorchState get() = _state.value

    /** Capacidades descobertas (ou redescobertas) na plataforma. Re-alinha o nível guardado. */
    fun onCapabilities(capabilities: TorchCapabilities) {
        _state.value = _state.value.copy(
            capabilities = capabilities,
            level = TorchLevel.align(_state.value.level, capabilities),
        )
    }

    /**
     * A intensidade que o hardware consegue aplicar para o pedido [requested]; `null` mantém a
     * atual. É o valor que o `actual` deve mandar ao dispositivo.
     */
    fun resolveLevel(requested: Float?): Float =
        TorchLevel.align(requested ?: _state.value.level, _state.value.capabilities)

    /** O comando de acender chegou ao hardware, com a intensidade [level] efetivamente aplicada. */
    fun onTurnedOn(level: Float) {
        _state.value = _state.value.copy(isOn = true, level = level, error = null)
    }

    /** O comando de apagar chegou ao hardware. */
    fun onTurnedOff() {
        _state.value = _state.value.copy(isOn = false, error = null)
    }

    /** Nova intensidade guardada (com a luz apagada) ou aplicada (com a luz acesa). */
    fun onLevelChanged(level: Float) {
        _state.value = _state.value.copy(level = level, error = null)
    }

    /**
     * **O hardware mudou por fora** — callback do SO (`registerTorchCallback` no Android, KVO de
     * `torchActive` no iOS). Só o `isOn` muda: um erro anterior continua visível até o próximo
     * comando, e a intensidade escolhida é preservada para quando a luz voltar.
     */
    fun onHardwareTorchChanged(isOn: Boolean) {
        if (_state.value.isOn == isOn) return
        _state.value = _state.value.copy(isOn = isOn)
    }

    /**
     * O comando falhou. A luz é dada como **apagada** quando o motivo impede acender — é o que
     * mantém o botão coerente com o LED.
     */
    fun onError(error: TorchError) {
        val stillOn = _state.value.isOn && error is TorchError.Unknown
        _state.value = _state.value.copy(isOn = stillOn, error = error)
    }

    /** Limpa o erro exibido (o app fechou o aviso). */
    fun clearError() {
        if (_state.value.error == null) return
        _state.value = _state.value.copy(error = null)
    }
}

package br.com.codecacto.kmplib.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Contador de referências para ativação/desativação de um recurso **compartilhado** de plataforma.
 *
 * Política pura (sem I/O, sem `expect/actual`), extraída para ser testável em `commonTest`:
 *
 * - [acquire] só chama [onActivate] na transição **0 → 1** (o recurso é ligado uma única vez, por
 *   mais consumidores que existam);
 * - [release] só chama [onDeactivate] na transição **1 → 0** (um consumidor sair não derruba os
 *   demais);
 * - [release] **sem** [acquire] correspondente é no-op (o contador nunca fica negativo, e um
 *   `stop()` extra jamais desliga o recurso de outro consumidor).
 *
 * A contagem usa `MutableStateFlow.getAndUpdate` (CAS atômico), então `acquire`/`release`
 * concorrentes não perdem incremento nem ativam duas vezes.
 */
internal class ActivationRefCounter(
    private val onActivate: () -> Unit,
    private val onDeactivate: () -> Unit,
) {
    private val count = MutableStateFlow(0)

    /** Quantos consumidores mantêm o recurso ligado agora. */
    val active: Int get() = count.value

    /** `true` enquanto ao menos um consumidor mantiver o recurso ligado. */
    val isActive: Boolean get() = count.value > 0

    /** Registra um consumidor. Liga o recurso apenas na primeira aquisição. */
    fun acquire() {
        val previous = count.getAndUpdate { it + 1 }
        if (previous == 0) onActivate()
    }

    /** Libera um consumidor. Desliga o recurso apenas quando o último sair. */
    fun release() {
        val previous = count.getAndUpdate { current -> if (current > 0) current - 1 else 0 }
        if (previous == 1) onDeactivate()
    }
}

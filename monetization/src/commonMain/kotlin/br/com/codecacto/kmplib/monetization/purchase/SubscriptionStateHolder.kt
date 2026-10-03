package br.com.codecacto.kmplib.monetization.purchase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * O estado da assinatura de um adaptador de loja **e as regras de quando ele muda** (2.250.0) — sem
 * SDK nenhum, para ser testado. O `RevenueCatPurchaseRepository` só traduz `CustomerInfo` e chama
 * isto.
 *
 * Duas regras moram aqui:
 *
 * 1. **Transição da leitura** ([nextReadState]): leitura boa → [SubscriptionReadState.READ]; leitura
 *    que falha → [SubscriptionReadState.FAILED] só se nada foi lido ainda; falha depois de `READ`
 *    **preserva** o estado (não rebaixa o assinante).
 * 2. **Geração de identidade**: toda troca de sujeito ([identityChanged] — `logIn`/`logOut`) avança a
 *    geração, e uma leitura **iniciada antes** da troca ([beginRead]) é **descartada** ao chegar
 *    ([completeRead]). Sem isto, a leitura de boot que respondesse depois de um `logIn` publicaria o
 *    `CustomerInfo` do sujeito ANTERIOR — o premium de quem saiu na conta de quem entrou.
 *
 * Tudo num único `MutableStateFlow` atualizado por `update {}`: conferir a geração e publicar é UMA
 * operação atômica, então uma troca de identidade em outra thread não entra no meio.
 */
internal class SubscriptionStateHolder {

    internal data class Snapshot(
        val info: SubscriptionInfo = SubscriptionInfo(isActive = false),
        val readState: SubscriptionReadState = SubscriptionReadState.PENDING,
        val generation: Long = 0,
    )

    private val snapshot = MutableStateFlow(Snapshot())

    /** O estado corrente (`isActive = false` inicial é marcador — ver [readState]). */
    val info: SubscriptionInfo get() = snapshot.value.info

    val readStateValue: SubscriptionReadState get() = snapshot.value.readState

    val subscriptionState: Flow<SubscriptionInfo> = snapshot.map { it.info }.distinctUntilChanged()

    val readState: Flow<SubscriptionReadState> = snapshot.map { it.readState }.distinctUntilChanged()

    /**
     * Publica um estado que vale para o sujeito **corrente** — resposta de compra, restauração,
     * listener de atualização do SDK. Não confere geração: essas respostas são do sujeito atual.
     */
    fun publish(info: SubscriptionInfo) {
        snapshot.update { it.copy(info = info, readState = nextReadState(it.readState, succeeded = true)) }
    }

    /** O sujeito mudou (`logIn`/`logOut`): leituras em voo ficam velhas, e [info] é o do novo sujeito. */
    fun identityChanged(info: SubscriptionInfo) {
        snapshot.update {
            it.copy(
                info = info,
                readState = nextReadState(it.readState, succeeded = true),
                generation = it.generation + 1,
            )
        }
    }

    /** Marca o início de uma leitura; devolva o token a [completeRead]. */
    fun beginRead(): Long = snapshot.value.generation

    /**
     * Conclui a leitura iniciada em [token]: [info] `null` = a leitura falhou.
     *
     * @return `true` se a leitura foi aplicada; `false` se foi descartada por ser de antes da última
     *   troca de identidade, ou se falhou.
     */
    fun completeRead(token: Long, info: SubscriptionInfo?): Boolean {
        var applied = false
        snapshot.update { current ->
            applied = false
            when {
                current.generation != token -> current
                info == null -> current.copy(readState = nextReadState(current.readState, succeeded = false))
                else -> {
                    applied = true
                    current.copy(info = info, readState = nextReadState(current.readState, succeeded = true))
                }
            }
        }
        return applied
    }
}

/**
 * Transição do sinal de leitura (regra pura): sucesso é sempre [SubscriptionReadState.READ]; falha só
 * move [SubscriptionReadState.PENDING] para [SubscriptionReadState.FAILED] — depois de lido, uma falha
 * não desfaz a leitura.
 */
internal fun nextReadState(current: SubscriptionReadState, succeeded: Boolean): SubscriptionReadState = when {
    succeeded -> SubscriptionReadState.READ
    current == SubscriptionReadState.PENDING -> SubscriptionReadState.FAILED
    else -> current
}

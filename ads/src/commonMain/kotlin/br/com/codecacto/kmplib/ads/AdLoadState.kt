package br.com.codecacto.kmplib.ads

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Situação da **primeira** carga de uma fonte remota de publicidade (lista de anúncios do
 * `CustomAdManager`, roteamento do `AdRouter`). Existe desde a 2.236.0.
 *
 * O motivo é a abertura do app: o intersticial "ao abrir" é pedido no primeiro frame, quando a
 * lista vinda do apps-api ainda está vazia. Lista vazia **não diz** se não há anúncio ou se a
 * resposta ainda não voltou — e tratar as duas coisas como "sem anúncio" fazia a sessão perder a
 * abertura (Piadaria, teste do agente de 02/out/2026). Este estado separa os dois casos.
 */
enum class AdLoadState {
    /** A fonte ainda não foi inicializada. */
    IDLE,

    /** Inicializada; a primeira resposta ainda não chegou. */
    LOADING,

    /** A primeira resposta chegou (pode ser uma lista vazia — aí não há mesmo anúncio). */
    READY,

    /** A fonte falhou ou terminou sem responder. Não há o que esperar. */
    FAILED;

    /** `true` quando não adianta mais esperar: chegou resposta ou a fonte desistiu. */
    val isSettled: Boolean get() = this == READY || this == FAILED
}

/**
 * Suspende até a primeira carga de [state] se resolver ([AdLoadState.isSettled]) ou até [timeout].
 * Devolve o estado final — `LOADING`/`IDLE` significa que o tempo acabou antes da resposta.
 */
internal suspend fun awaitSettled(state: StateFlow<AdLoadState>, timeout: Duration): AdLoadState =
    withTimeoutOrNull(timeout) { state.first { it.isSettled } } ?: state.value

package br.com.codecacto.kmplib.testing.vm

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.TurbineContext
import app.cash.turbine.turbineScope
import br.com.codecacto.kmplib.ui.mvi.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * **Teste de ViewModel no padrão da fábrica** (2.381.0, `kmplib-testing`) — a recomendação da Google para
 * corrotinas: `runTest` + **um** `StandardTestDispatcher` como `Dispatchers.Main` (o `viewModelScope`) e o mesmo
 * relógio virtual para o resto. Restaura o `Main` no fim, passe ou falhe.
 *
 * ```kotlin
 * @Test
 * fun `fluxo locacao_cancelar tela conexao-caiu - confere no servidor e mostra sucesso`() = runViewModelTest {
 *     val servidor = ServidorDeTeste().apply { … }
 *     val vm = LocacoesViewModel(LocacaoRepository(LocAkiApi(servidor.domainApiClient(dispatcher = dispatcher))))
 *     vm.dispatch(Action.ConfirmarCancelamento)
 *     vm.state.ateQue { it.cancelamento == null }
 * }
 * ```
 *
 * **Por que `Standard` e não `Unconfined`.** O `UnconfinedTestDispatcher` executa cada `launch` na hora, até a
 * primeira suspensão — e esconde a condição de corrida em que mora o duplo toque. Aqui nada anda sem o teste
 * deixar ([ViewModelTestScope.ocioso], [ateQue], ou o próprio `runTest` quando o corpo suspende).
 *
 * @param timeout teto do teste inteiro (tempo REAL), o mesmo parâmetro do `runTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun runViewModelTest(
    timeout: Duration = 10.seconds,
    block: suspend ViewModelTestScope.() -> Unit,
): TestResult {
    val dispatcher = StandardTestDispatcher()
    Dispatchers.setMain(dispatcher)
    try {
        return runTest(dispatcher, timeout) {
            val teste = this
            turbineScope { ViewModelTestScope(teste, dispatcher, this).block() }
        }
    } finally {
        Dispatchers.resetMain()
    }
}

/**
 * O que o corpo do [runViewModelTest] enxerga.
 *
 * @property test o `TestScope` do `runTest` (para `backgroundScope`, `testScheduler`, `advanceTimeBy`…).
 * @property dispatcher o dispatcher que é o `Main` do teste — passe-o ao `ServidorDeTeste`
 *   (`servidor.domainApiClient(dispatcher = dispatcher)`) e a qualquer `RestConfig(requestDispatcher = …)`, para
 *   tudo andar no mesmo relógio virtual.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelTestScope internal constructor(
    val test: TestScope,
    val dispatcher: TestDispatcher,
    private val turbines: TurbineContext,
) {
    /**
     * Mantém o [state] QUENTE: um coletor vazio no `backgroundScope` (recomendação da Google para `StateFlow`
     * montado com `stateIn(WhileSubscribed)` — sem assinante, ele não recalcula e o `.value` fica velho).
     * O coletor morre sozinho no fim do teste.
     */
    fun <S> manterQuente(state: StateFlow<S>) {
        test.backgroundScope.launch(UnconfinedTestDispatcher(test.testScheduler)) { state.collect {} }
    }

    /**
     * Os efeitos one-shot (navegação, toast) de um fluxo, por Turbine: `efeitos(vm.effect).awaitItem()`.
     * Assine ANTES da ação. No fim do teste, o Turbine reprova efeito que chegou e ninguém conferiu — consuma
     * (`awaitItem`) ou declare (`cancelAndIgnoreRemainingEvents()`): efeito esquecido é navegação que ninguém testou.
     */
    fun <E> efeitos(fluxo: Flow<E>): ReceiveTurbine<E> = with(turbines) { fluxo.testIn(test.backgroundScope) }

    /** [efeitos] do `effect` do `BaseViewModel`. */
    fun <E> efeitos(vm: BaseViewModel<*, *, E>): ReceiveTurbine<E> = efeitos(vm.effect)

    /**
     * Roda tudo o que está agendado, avançando o relógio virtual até não sobrar nada (`advanceUntilIdle`).
     * ⚠️ Parte do trabalho do cliente Ktor pode sair do dispatcher do teste (canal do corpo, plugins): para o
     * estado que depende da RESPOSTA, confira com [ateQue] — `ocioso()` seguido de `.value` fica intermitente.
     */
    fun ocioso() {
        test.advanceUntilIdle()
    }

    /** Roda só o que já está pronto AGORA, sem avançar o relógio (`runCurrent`). */
    fun agora() {
        test.runCurrent()
    }
}

/**
 * Espera o estado satisfazer [predicado] e o devolve. Confere o `.value` (o `StateFlow` é conflado: estados
 * intermediários podem não ser vistos — não teste a SEQUÊNCIA, teste o estado a que se chega).
 *
 * O [timeout] é em tempo REAL, de propósito: em tempo virtual ele venceria na hora, enquanto o engine (fora do
 * dispatcher do teste) ainda está respondendo. Enquanto espera, o `runTest` segue rodando o agendado.
 */
suspend fun <S> StateFlow<S>.ateQue(timeout: Duration = 5.seconds, predicado: (S) -> Boolean): S {
    if (predicado(value)) return value
    return try {
        withContext(Dispatchers.Default) { withTimeout(timeout) { first(predicado) } }
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        throw AssertionError("ateQue: o estado não chegou à condição em $timeout. Último estado: $value", e)
    }
}

/**
 * **Duplo toque**: a ação disparada 2× SEM deixar o scheduler andar entre os toques, e depois tudo roda até
 * ocioso. Prova o padrão da fábrica para ação que grava — a guarda é síncrona, no `onAction`:
 *
 * ```kotlin
 * if (currentState.salvando) return
 * setState { copy(salvando = true) }   // ANTES do launch
 * viewModelScope.launch { … }
 * ```
 *
 * Guarda que só liga o `salvando` DENTRO do `launch` (ou depois de uma suspensão) deixa o 2º toque passar — e o
 * teste mostra: o servidor recebe 2 chamadas. Espere o estado final com [ateQue] e confira
 * `servidor.chamadas(metodo, caminho).size == 1`.
 *
 * É mais estrito que o aparelho de propósito: lá o `viewModelScope` usa `Main.immediate`, que roda o `launch` na hora
 * até a 1ª suspensão — e a guarda dentro do `launch` às vezes se salva. Às vezes: basta uma suspensão antes dela
 * (validação, leitura local) ou um `launch(Dispatchers.Default)`. A régua da fábrica é a guarda síncrona.
 */
fun <A> ViewModelTestScope.duploToque(dispatch: (A) -> Unit, acao: A) {
    dispatch(acao)
    dispatch(acao)
    ocioso()
}

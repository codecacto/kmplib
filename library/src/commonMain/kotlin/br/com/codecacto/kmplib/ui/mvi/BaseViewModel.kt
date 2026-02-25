package br.com.codecacto.kmplib.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel base com suporte ao padrão MVI (Model-View-Intent)
 *
 * Fornece gerenciamento de State, Action e Effect de forma type-safe.
 *
 * @param State Tipo do estado da tela (data class com todos os dados)
 * @param Action Tipo das ações do usuário (sealed interface)
 * @param Effect Tipo dos efeitos colaterais (sealed interface, ex: navegação)
 */
abstract class BaseViewModel<State, Action, Effect>(
    initialState: State
) : ViewModel() {

    // Estado interno mutável
    private val _state = MutableStateFlow(initialState)

    /**
     * Estado atual da tela
     *
     * Collect este Flow para observar mudanças no estado
     */
    val state: StateFlow<State> = _state.asStateFlow()

    // Canal de efeitos (one-shot events)
    private val _effect = Channel<Effect>(Channel.BUFFERED)

    /**
     * Flow de efeitos colaterais (navegação, toasts, etc)
     *
     * Collect este Flow para reagir a eventos one-shot
     */
    val effect: Flow<Effect> = _effect.receiveAsFlow()

    /**
     * Processa uma ação do usuário
     *
     * Sobrescreva este método para implementar a lógica de negócio
     */
    abstract fun onAction(action: Action)

    /**
     * Atualiza o estado atual
     *
     * @param update Lambda que recebe o estado atual e retorna o novo estado
     */
    protected fun updateState(update: (State) -> State) {
        _state.update(update)
    }

    /**
     * Obtém o estado atual de forma síncrona
     */
    protected val currentState: State
        get() = _state.value

    /**
     * Emite um efeito colateral (one-shot event)
     *
     * @param effect Efeito a ser emitido
     */
    protected fun emitEffect(effect: Effect) {
        viewModelScope.launch {
            _effect.send(effect)
        }
    }

    /**
     * Executa uma operação assíncrona no viewModelScope
     *
     * @param block Bloco de código suspendível
     */
    protected fun launch(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
        }
    }
}

/**
 * Extension para facilitar collect de estado em Composables
 */
@Suppress("unused")
inline fun <State, Action, Effect> BaseViewModel<State, Action, Effect>.collectState(
    crossinline onStateChanged: (State) -> Unit
): Flow<State> = state.onEach { onStateChanged(it) }

/**
 * Extension para facilitar collect de efeitos em Composables
 */
@Suppress("unused")
inline fun <State, Action, Effect> BaseViewModel<State, Action, Effect>.collectEffect(
    crossinline onEffect: (Effect) -> Unit
): Flow<Effect> = effect.onEach { onEffect(it) }

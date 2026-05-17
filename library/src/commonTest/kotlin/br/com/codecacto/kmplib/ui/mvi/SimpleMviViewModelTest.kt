package br.com.codecacto.kmplib.ui.mvi

import kotlin.test.Test
import kotlin.test.assertEquals

class SimpleMviViewModelTest {

    @Test
    fun dispatchCallsOnActionAndSetStateUpdatesState() {
        val viewModel = CounterViewModel()

        viewModel.dispatch(CounterAction.Increment)
        viewModel.dispatch(CounterAction.Increment)

        assertEquals(CounterState(count = 2), viewModel.state.value)
    }

    private data class CounterState(val count: Int = 0) : UiState

    private sealed interface CounterAction : UiAction {
        data object Increment : CounterAction
    }

    private sealed interface CounterEffect : UiEffect

    private class CounterViewModel :
        SimpleMviViewModel<CounterState, CounterEffect, CounterAction>(CounterState()) {

        override fun onAction(action: CounterAction) {
            when (action) {
                CounterAction.Increment -> setState { copy(count = count + 1) }
            }
        }
    }
}

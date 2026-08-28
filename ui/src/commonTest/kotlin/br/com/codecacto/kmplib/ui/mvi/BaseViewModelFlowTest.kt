package br.com.codecacto.kmplib.ui.mvi

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private data class CounterState(val count: Int = 0) : UiState
private sealed interface CounterAction : UiAction {
    data object Increment : CounterAction
    data object Reset : CounterAction
}
private sealed interface CounterEffect : UiEffect {
    data class Notify(val message: String) : CounterEffect
}

private class CounterViewModel : SimpleMviViewModel<CounterState, CounterEffect, CounterAction>(CounterState()) {
    override fun onAction(action: CounterAction) {
        when (action) {
            CounterAction.Increment -> {
                setState { copy(count = count + 1) }
                sendEffect(CounterEffect.Notify("incremented to ${currentState.count}"))
            }
            CounterAction.Reset -> setState { copy(count = 0) }
        }
    }
}

class BaseViewModelFlowTest {

    @Test
    fun dispatch_updates_state() = runTest {
        val vm = CounterViewModel()
        vm.dispatch(CounterAction.Increment)
        vm.dispatch(CounterAction.Increment)
        assertEquals(2, vm.state.value.count)
    }

    @Test
    fun reset_returns_to_initial_state() = runTest {
        val vm = CounterViewModel()
        vm.dispatch(CounterAction.Increment)
        vm.dispatch(CounterAction.Reset)
        assertEquals(0, vm.state.value.count)
    }

    @Test
    fun effects_are_emitted_on_dispatch() = runTest {
        val vm = CounterViewModel()
        vm.dispatch(CounterAction.Increment)
        val emitted = vm.effect.first()
        assertTrue(emitted is CounterEffect.Notify)
        assertEquals("incremented to 1", emitted.message)
    }
}

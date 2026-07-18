package com.a02.draw.core.ui.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel<S : UiState, E : UiEffect>(initialState: S) : ViewModel() {
    private val mutableState = MutableStateFlow(initialState)
    val state: StateFlow<S> = mutableState.asStateFlow()

    private val effectChannel = Channel<E>(capacity = Channel.BUFFERED)
    val effects: Flow<E> = effectChannel.receiveAsFlow()

    protected fun updateState(reducer: S.() -> S) {
        mutableState.value = mutableState.value.reducer()
    }

    protected suspend fun sendEffect(effect: E) {
        effectChannel.send(effect)
    }

    protected fun launchCatching(
        onError: suspend (Throwable) -> Unit,
        block: suspend CoroutineScope.() -> Unit,
    ): Job = viewModelScope.launch {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            onError(throwable)
        }
    }
}

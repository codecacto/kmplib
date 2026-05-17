package br.com.codecacto.kmplib.core.network

import kotlinx.coroutines.flow.StateFlow

expect class ConnectivityObserver() {
    val isOnline: StateFlow<Boolean>
    fun start()
    fun stop()
}

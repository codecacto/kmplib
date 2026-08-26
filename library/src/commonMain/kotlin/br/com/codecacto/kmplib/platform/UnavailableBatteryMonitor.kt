package br.com.codecacto.kmplib.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Monitor inerte: a plataforma não informa a bateria. Reporta [BatteryStatus.UNAVAILABLE] —
 * e nunca `0%`, que levaria o app a cortar a feature achando que a carga acabou.
 */
internal object UnavailableBatteryMonitor : BatteryMonitor {
    private val _status = MutableStateFlow(BatteryStatus.UNAVAILABLE)
    override val status: StateFlow<BatteryStatus> = _status.asStateFlow()
    override fun refresh() = Unit
    override fun release() = Unit
}

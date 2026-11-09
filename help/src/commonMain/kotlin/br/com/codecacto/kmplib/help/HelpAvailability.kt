package br.com.codecacto.kmplib.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import br.com.codecacto.kmplib.core.network.ApiResult
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Lembra, por processo, se a ajuda está ligada para cada combinação de conteúdo: o ícone aparece em várias
 * telas e cada uma consultar o servidor seria desperdício. Falha de rede mantém a resposta anterior até
 * [maxStaleness] depois da última resposta boa; passado isso, a resposta é esquecida e vale `false`.
 */
internal class HelpAvailabilityCache(
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val minInterval: Duration = 5.minutes,
    private val maxStaleness: Duration = 1.hours,
) {
    private val mutex = Mutex()
    private val marks = mutableMapOf<String, TimeMark>()
    private val _values = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    val values: StateFlow<Map<String, Boolean>> = _values.asStateFlow()

    /** Consulta o servidor se a última resposta boa passou de [minInterval]. Devolve o valor conhecido. */
    suspend fun refresh(key: String, source: HelpSource): Boolean = mutex.withLock {
        val last = marks[key]
        if (last == null || last.elapsedNow() >= minInterval) {
            val result = source.loadCatalog()
            if (result is ApiResult.Success) {
                _values.update { it + (key to result.data.active) }
                marks[key] = timeSource.markNow()
            } else if (last != null && last.elapsedNow() >= maxStaleness) {
                _values.update { it - key }
                marks.remove(key)
            }
        }
        _values.value[key] == true
    }
}

private val processAvailability = HelpAvailabilityCache()

/**
 * `true` quando o projeto tem a ajuda ligada. Sem resposta do servidor, `false`, inclusive quando as falhas
 * se prolongam por mais de uma hora depois da última resposta boa: quem esconde o ícone não mostra um botão
 * que levaria a uma tela sem conteúdo.
 */
suspend fun isHelpEnabled(config: HelpConfig): Boolean =
    processAvailability.refresh(config.contentKey(), RestHelpSource(config))

/**
 * [isHelpEnabled] como estado de tela, para esconder o [HelpTopBarAction]. Reconsulta ao voltar ao primeiro
 * plano, respeitando o intervalo mínimo do cache.
 */
@Composable
fun rememberIsHelpEnabled(config: HelpConfig): Boolean {
    val key = config.contentKey()
    val scope = rememberCoroutineScope()
    val values by processAvailability.values.collectAsState()
    LifecycleResumeEffect(key) {
        val job = scope.launch { processAvailability.refresh(key, RestHelpSource(config)) }
        onPauseOrDispose { job.cancel() }
    }
    return values[key] == true
}

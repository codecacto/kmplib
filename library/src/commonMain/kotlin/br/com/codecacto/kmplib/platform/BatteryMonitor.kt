package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * **Quanta bateria resta e se está carregando** — o suficiente para um app decidir cortar um
 * consumo pesado antes de deixar a pessoa sem celular.
 *
 * [level] é fração (`0f..1f`), não porcentagem inteira: é o formato dos dois SOs
 * (`level/scale` no Android, `batteryLevel` no iOS) e evita arredondar duas vezes.
 */
data class BatteryStatus(
    /** Carga atual, de `0f` a `1f`. Vale `-1f` quando o nível ainda não é conhecido. */
    val level: Float = UNKNOWN_LEVEL,
    /** `true` enquanto o aparelho está no carregador (ou já cheio). */
    val isCharging: Boolean = false,
    /** `false` quando a plataforma não informa a bateria (leitura indisponível). */
    val isAvailable: Boolean = false,
) {

    /** Carga em porcentagem inteira, para exibir. `-1` quando desconhecida. */
    val percent: Int get() = if (level < 0f) -1 else (level * 100).roundToInt()

    /**
     * **A regra do corte por bateria crítica.**
     *
     * Só é crítica se a carga está no limiar **e o aparelho não está carregando** — no carregador,
     * 4% não é emergência nenhuma, e cortar a lanterna ali seria um defeito. Leitura indisponível
     * nunca é crítica: desligar a luz por causa de um dado que não temos é pior do que não desligar.
     *
     * @param threshold fração de corte (ex.: `0.05f` para 5%).
     */
    fun isCritical(threshold: Float): Boolean =
        isAvailable && !isCharging && level >= 0f && level <= threshold

    companion object {
        /** Nível ainda não conhecido / não informado pela plataforma. */
        const val UNKNOWN_LEVEL: Float = -1f

        /** Limiar de bateria crítica sugerido (5%). O produto pode escolher outro. */
        const val DEFAULT_CRITICAL_THRESHOLD: Float = 0.05f

        /** Leitura indisponível — o estado inicial e o de aparelho que não informa bateria. */
        val UNAVAILABLE: BatteryStatus = BatteryStatus()

        /**
         * Monta o status a partir do par `level`/`scale` do Android
         * (`BatteryManager.EXTRA_LEVEL` / `EXTRA_SCALE`), que é a leitura crua do broadcast.
         *
         * `scale` costuma ser 100, mas não é garantido — dividir por 100 fixo é um erro que só
         * aparece no aparelho errado. Valores inválidos viram [UNAVAILABLE].
         */
        fun fromLevelAndScale(level: Int, scale: Int, isCharging: Boolean): BatteryStatus {
            if (level < 0 || scale <= 0) return UNAVAILABLE
            return BatteryStatus(
                level = (level.toFloat() / scale.toFloat()).coerceIn(0f, 1f),
                isCharging = isCharging,
                isAvailable = true,
            )
        }

        /**
         * Monta o status a partir do `UIDevice.batteryLevel` do iOS, que devolve **-1** quando o
         * monitoramento está desligado ou o nível é desconhecido.
         */
        fun fromIosLevel(level: Float, isCharging: Boolean): BatteryStatus {
            if (level < 0f) return BatteryStatus(isCharging = isCharging, isAvailable = false)
            return BatteryStatus(
                level = level.coerceIn(0f, 1f),
                isCharging = isCharging,
                isAvailable = true,
            )
        }
    }
}

/**
 * Leitura observável da bateria.
 *
 * - **Android:** broadcast `ACTION_BATTERY_CHANGED` (o caminho documentado; é um *sticky broadcast*,
 *   por isso só pode ser registrado em runtime, nunca no manifesto).
 * - **iOS:** `UIDevice.batteryLevel`/`batteryState` com `isBatteryMonitoringEnabled`, atualizados
 *   pelas notificações `UIDeviceBatteryLevelDidChange`/`UIDeviceBatteryStateDidChange`.
 *
 * Quem cria, libera: [release] cancela o registro. No iOS ele também **desliga** o monitoramento de
 * bateria, que é uma flag global do processo.
 *
 * ```kotlin
 * private val battery = createBatteryMonitor()
 * init {
 *     battery.status
 *         .onEach { if (it.isCritical(BatteryStatus.DEFAULT_CRITICAL_THRESHOLD)) torch.turnOff() }
 *         .launchIn(viewModelScope)
 * }
 * override fun onCleared() { battery.release(); super.onCleared() }
 * ```
 */
interface BatteryMonitor {

    /** Carga e estado de carregamento, atualizados pelo SO. */
    val status: StateFlow<BatteryStatus>

    /** Relê o estado agora (ex.: ao voltar do segundo plano). */
    fun refresh()

    /** Cancela o registro no SO. Obrigatório ao descartar o dono. */
    fun release()
}

/** Cria o monitor de bateria da plataforma atual. */
expect fun createBatteryMonitor(): BatteryMonitor

/** Monitor de bateria com ciclo de vida atrelado à composição (liberado no `onDispose`). */
@Composable
fun rememberBatteryMonitor(): BatteryMonitor {
    val monitor = remember { createBatteryMonitor() }
    DisposableEffect(monitor) { onDispose { monitor.release() } }
    return monitor
}

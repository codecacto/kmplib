package br.com.codecacto.kmplib.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

private const val TAG = "BatteryMonitor"

/**
 * Holder do [Context] da aplicação para o [BatteryMonitor]. Inicializado por `KmpLib.init(context)`.
 */
object BatteryMonitorHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun getContext(): Context? = contextRef?.get()
}

/**
 * Cria o monitor de bateria do Android. Sem `KmpLib.init(context)`, devolve um monitor inerte que
 * reporta [BatteryStatus.UNAVAILABLE] — nunca "0% de bateria", que faria o app cortar a feature.
 */
actual fun createBatteryMonitor(): BatteryMonitor {
    val context = BatteryMonitorHolder.getContext() ?: run {
        AppLogger.e(TAG, "KmpLib.init(context) não foi chamado — leitura de bateria indisponível")
        return UnavailableBatteryMonitor
    }
    return AndroidBatteryMonitor(context)
}

/**
 * **Padrão-ouro do Android: `ACTION_BATTERY_CHANGED`.**
 *
 * É um *sticky broadcast*: `registerReceiver` devolve na hora o último valor publicado pelo sistema
 * (por isso a leitura inicial não precisa esperar evento nenhum) e segue entregando as mudanças.
 * Ele **não pode** ser declarado no manifesto — o Android o entrega apenas a receivers registrados
 * em runtime, de propósito, para não acordar todo app a cada 1% de carga.
 */
internal class AndroidBatteryMonitor(context: Context) : BatteryMonitor {

    private val appContext = context.applicationContext
    private val _status = MutableStateFlow(BatteryStatus.UNAVAILABLE)
    override val status: StateFlow<BatteryStatus> = _status.asStateFlow()

    private var released = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { _status.value = it.toBatteryStatus() }
        }
    }

    init {
        val sticky = runCatching {
            appContext.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.onFailure {
            AppLogger.w(TAG, "Falha ao registrar o receiver de bateria: ${it.message}")
        }.getOrNull()
        sticky?.let { _status.value = it.toBatteryStatus() }
    }

    override fun refresh() {
        if (released) return
        val sticky = runCatching {
            appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        }.getOrNull()
        sticky?.let { _status.value = it.toBatteryStatus() }
    }

    override fun release() {
        if (released) return
        released = true
        runCatching { appContext.unregisterReceiver(receiver) }
            .onFailure { AppLogger.w(TAG, "Receiver de bateria já estava solto: ${it.message}") }
    }
}

private fun Intent.toBatteryStatus(): BatteryStatus {
    val plugState = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val isCharging = plugState == BatteryManager.BATTERY_STATUS_CHARGING ||
        plugState == BatteryManager.BATTERY_STATUS_FULL
    return BatteryStatus.fromLevelAndScale(
        level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
        scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1),
        isCharging = isCharging,
    )
}

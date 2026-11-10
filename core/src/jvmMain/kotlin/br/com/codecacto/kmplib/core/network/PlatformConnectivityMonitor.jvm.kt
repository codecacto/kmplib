package br.com.codecacto.kmplib.core.network

import br.com.codecacto.kmplib.core.util.AppLogger
import java.net.NetworkInterface
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

private const val TAG = "ConnectivityObserver"

/**
 * Desktop (2.353.0): a JVM não tem callback de mudança de rede do SO. O monitor CONSULTA as
 * interfaces (`NetworkInterface`: alguma ativa, não-loopback, não-virtual, com endereço) a cada
 * [pollMillis], numa thread daemon, e só avisa quando o estado muda.
 *
 * Tarifada × não tarifada: a JVM não expõe a "conexão limitada" do Windows, então rede presente é
 * [NetworkType.UNMETERED] — o caso real de um PC (cabo/Wi-Fi). Interface ativa não prova internet:
 * quem decide "o servidor respondeu" continua sendo a requisição (mesma ressalva do Android).
 */
internal actual class PlatformConnectivityMonitor actual constructor() : NetworkTypeSource {

    private var executor: ScheduledExecutorService? = null
    private val pollMillis: Long = 5_000

    @Volatile
    private var last: NetworkType? = null

    actual override fun start(onChange: (NetworkType) -> Unit) {
        if (executor != null) return
        val ex = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "kmplib-connectivity").apply { isDaemon = true }
        }
        executor = ex
        ex.scheduleWithFixedDelay({
            val atual = currentType() ?: return@scheduleWithFixedDelay
            if (atual != last) {
                last = atual
                runCatching { onChange(atual) }.onFailure { AppLogger.w(TAG, "ouvinte de rede falhou", it) }
            }
        }, 0, pollMillis, TimeUnit.MILLISECONDS)
    }

    actual override fun stop() {
        executor?.shutdownNow()
        executor = null
        last = null
    }

    actual override fun currentType(): NetworkType? = try {
        val ativa = NetworkInterface.getNetworkInterfaces()?.asSequence().orEmpty().any { nif ->
            nif.isUp && !nif.isLoopback && !nif.isVirtual && nif.inetAddresses.hasMoreElements()
        }
        if (ativa) NetworkType.UNMETERED else NetworkType.NONE
    } catch (e: Exception) {
        AppLogger.w(TAG, "estado de rede indisponível: ${e::class.simpleName}")
        null
    }
}

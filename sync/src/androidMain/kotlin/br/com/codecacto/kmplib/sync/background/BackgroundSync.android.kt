package br.com.codecacto.kmplib.sync.background

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.sync.SyncDatabaseHolder
import br.com.codecacto.kmplib.sync.SyncNetworkPolicy
import java.util.concurrent.TimeUnit

actual fun createBackgroundSyncScheduler(config: BackgroundSyncConfig): BackgroundSyncScheduler =
    WorkManagerBackgroundSyncScheduler(config)

/**
 * [BackgroundSyncScheduler] pelo **WorkManager** (2.328.0). Os trabalhos são únicos por nome
 * (`<uniqueWorkName>-periodic` e `<uniqueWorkName>-soon`) e exigem `NetworkType.CONNECTED`.
 */
class WorkManagerBackgroundSyncScheduler(
    private val config: BackgroundSyncConfig = BackgroundSyncConfig(),
    private val contextProvider: () -> Context? = { SyncDatabaseHolder.contextOrNull() },
) : BackgroundSyncScheduler {

    private val periodicName get() = "${config.uniqueWorkName}-periodic"
    private val soonName get() = "${config.uniqueWorkName}-soon"

    private fun workManager(): WorkManager? {
        val context = contextProvider() ?: run {
            AppLogger.w(TAG, "initKmpLibSync não foi chamado — sincronização em 2º plano não agendada")
            return null
        }
        return runCatching { WorkManager.getInstance(context) }
            .onFailure { AppLogger.e(TAG, "WorkManager indisponível", it) }
            .getOrNull()
    }

    // Dado de texto sobe em qualquer rede nos modos automáticos (estudo §7.1): a restrição é só
    // "conectado". O "só no Wi-Fi" é da mídia, aplicado pelas filas de envio.
    private fun constraints() = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun apply(policy: SyncNetworkPolicy) {
        val wm = workManager() ?: return
        if (!policy.isAutomatic) {
            wm.cancelUniqueWork(periodicName)
            wm.cancelUniqueWork(soonName)
            return
        }
        val pedido = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(config.periodMinutes, TimeUnit.MINUTES)
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG_ALL)
            .build()
        runCatching { wm.enqueueUniquePeriodicWork(periodicName, ExistingPeriodicWorkPolicy.UPDATE, pedido) }
            .onFailure { AppLogger.e(TAG, "WorkManager recusou o periódico", it) }
    }

    override fun requestSoon(policy: SyncNetworkPolicy) {
        if (!policy.isAutomatic) return
        val wm = workManager() ?: return
        val pedido = OneTimeWorkRequestBuilder<BackgroundSyncWorker>()
            .setConstraints(constraints())
            .setInitialDelay(config.soonDelaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .addTag(TAG_ALL)
            .build()
        // REPLACE: gravações seguidas empurram a rodada para depois da última (uma rodada só).
        runCatching { wm.enqueueUniqueWork(soonName, ExistingWorkPolicy.REPLACE, pedido) }
            .onFailure { AppLogger.e(TAG, "WorkManager recusou o pedido de sincronização", it) }
    }

    override fun cancel() {
        val wm = workManager() ?: return
        wm.cancelUniqueWork(periodicName)
        wm.cancelUniqueWork(soonName)
    }

    companion object {
        private const val TAG = "BackgroundSync"
        private const val BACKOFF_SECONDS = 30L

        /** Tag de todos os trabalhos de sincronização em 2º plano (diagnóstico por `getWorkInfosByTag`). */
        const val TAG_ALL: String = "kmplib-background-sync"
    }
}

/**
 * O trabalho do WorkManager: roda o [BackgroundSyncWork] registrado pelo app. Sem registro (o app não
 * o fez no `Application.onCreate`) → `retry`, com aviso: nada se perde, mas nada anda.
 */
class BackgroundSyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = when (BackgroundSyncWork.run()) {
        BackgroundSyncResult.SUCCESS -> Result.success()
        BackgroundSyncResult.RETRY, null -> Result.retry()
    }
}

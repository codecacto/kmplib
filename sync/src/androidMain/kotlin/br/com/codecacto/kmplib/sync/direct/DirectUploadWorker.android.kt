package br.com.codecacto.kmplib.sync.direct

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.sync.SyncDatabaseHolder
import java.util.concurrent.TimeUnit

actual fun createPlatformDirectUploadTransport(outboxName: String): DirectUploadPartTransport =
    KtorDirectUploadPartTransport()

actual fun createPlatformDirectUploadScheduler(): DirectUploadScheduler = WorkManagerDirectUploadScheduler()

/**
 * [DirectUploadScheduler] pelo **WorkManager**: um trabalho único por fila e por necessidade de rede,
 * com a restrição de rede declarada (o sistema só o roda com `CONNECTED`/`UNMETERED`) e recuo
 * exponencial do próprio WorkManager. Precisa do `initKmpLibSync(context)`.
 */
class WorkManagerDirectUploadScheduler(private val contextProvider: () -> Context? = { SyncDatabaseHolder.contextOrNull() }) :
    DirectUploadScheduler {

    override fun schedule(outbox: DirectUploadOutbox, need: DirectUploadNetworkNeed, urgent: Boolean) {
        val context = contextProvider() ?: run {
            AppLogger.w(TAG, "initKmpLibSync não foi chamado — fila de envio não agendada")
            return
        }
        val pedido = OneTimeWorkRequestBuilder<DirectUploadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(if (need == DirectUploadNetworkNeed.UNMETERED) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(DirectUploadWorker.KEY_OUTBOX to outbox.name))
            .addTag(TAG_ALL)
            .build()
        // Pessoa agiu agora → REPLACE: passa na frente de um recuo longo (a parte em voo, se houver, é
        // retomada — o progresso é gravado por parte). Abertura/reagendamento → KEEP: não atropela.
        val politica = if (urgent) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueWorkName(outbox.name, need), politica, pedido)
        }.onFailure { AppLogger.e(TAG, "WorkManager recusou o agendamento da fila de envio", it) }
    }

    override fun cancel(outbox: DirectUploadOutbox) {
        val context = contextProvider() ?: return
        DirectUploadNetworkNeed.entries.forEach {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueWorkName(outbox.name, it))
        }
    }

    companion object {
        private const val TAG = "DirectUploadScheduler"
        private const val BACKOFF_SECONDS = 30L

        /** Tag de todos os trabalhos de envio direto (diagnóstico com `WorkManager.getWorkInfosByTag`). */
        const val TAG_ALL: String = "kmplib-direct-upload"

        /** Nome do trabalho único da fila [outboxName] para a rede [need]. */
        fun uniqueWorkName(outboxName: String, need: DirectUploadNetworkNeed): String =
            "kmplib-direct-upload-$outboxName-${need.name.lowercase()}"
    }
}

/**
 * O trabalho que drena a fila. Criado pelo WorkManager (também com o app fechado — o sistema sobe o
 * processo, o `Application.onCreate` constrói a fila e ela se registra no [DirectUploadOutboxRegistry]).
 *
 * Fila não encontrada (o app não a criou na abertura) → `retry`, com aviso: o envio não se perde,
 * mas também não anda até o app construir a fila no `onCreate`.
 */
class DirectUploadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val nome = inputData.getString(KEY_OUTBOX) ?: return Result.failure()
        val fila = DirectUploadOutboxRegistry.get(nome) ?: run {
            AppLogger.w(TAG, "fila '$nome' não foi construída na abertura do app; tentando depois")
            return Result.retry()
        }
        val resumo = fila.drainNow()
        return if (resumo.needsRetry) Result.retry() else Result.success()
    }

    companion object {
        private const val TAG = "DirectUploadWorker"
        internal const val KEY_OUTBOX = "outbox"
    }
}

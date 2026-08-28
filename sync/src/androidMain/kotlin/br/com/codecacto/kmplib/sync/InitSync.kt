package br.com.codecacto.kmplib.sync

import android.content.Context

/**
 * Registra o `Context` no banco local do offline-first (SQLDelight).
 *
 * Chame no `Application.onCreate()`. O `BlobStore`, que guarda os binários da fila de upload, é
 * inicializado pelo [br.com.codecacto.kmplib.core.initKmpLibCore] — ele mora no core porque não
 * depende de sincronização nenhuma.
 */
fun initKmpLibSync(context: Context) {
    SyncDatabaseHolder.init(context)
}

package br.com.codecacto.kmplib.sync

import br.com.codecacto.kmplib.sync.db.SyncDatabase

/**
 * Cria a instância do banco de sync ([SyncDatabase]) já com o driver e o schema da
 * plataforma aplicados (cria as tabelas no 1º uso, migra em versões futuras).
 *
 * - **Android:** `AndroidSqliteDriver` (precisa do Context — registrado via
 *   `KmpLib.initSync(context)` ou `KmpLib.init(context)`).
 * - **iOS:** `NativeSqliteDriver` (só valida em host macOS — dívida conhecida).
 *
 * Chame uma vez (no bootstrap/Koin do app) e injete a instância no
 * [DefaultSyncEngine] e nos repositórios [SyncableRepository].
 *
 * @param name nome do arquivo do banco (default [DEFAULT_SYNC_DB_NAME]).
 */
expect fun createSyncDatabase(name: String = DEFAULT_SYNC_DB_NAME): SyncDatabase

/** Nome default do arquivo do banco de sync. */
const val DEFAULT_SYNC_DB_NAME: String = "kmplib_sync.db"

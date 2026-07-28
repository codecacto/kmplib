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
 * ### Schema v2 (kmplib 2.91.0) — migração automática, sem perda
 * O espelho ganhou `account_id` (escopo de conta) e `failed`/`fail_code`/`attempts` (estado de
 * escrita por linha). As bases já instaladas **migram sozinhas** no primeiro `createSyncDatabase`
 * após o update (o driver aplica `Schema.migrate`): nada é dropado — as linhas existentes vão para
 * o bucket "sem escopo", que a primeira chamada a [SyncStore.setAccountScope] reivindica conforme a
 * [LegacyRowsPolicy]. Ver `1.sqm`.
 *
 * @param name nome do arquivo do banco (default [DEFAULT_SYNC_DB_NAME]).
 */
expect fun createSyncDatabase(name: String = DEFAULT_SYNC_DB_NAME): SyncDatabase

/** Nome default do arquivo do banco de sync. */
const val DEFAULT_SYNC_DB_NAME: String = "kmplib_sync.db"

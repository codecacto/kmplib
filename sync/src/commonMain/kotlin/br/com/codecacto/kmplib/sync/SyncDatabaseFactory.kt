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
 * ### `excludeFromBackup` — quem quer o dado na nuvem e quem NÃO quer (kmplib 2.105.0)
 *
 * O banco local é, para o app 100% offline, **o dado do usuário inteiro**. Duas posturas legítimas e
 * opostas, e por isso isto é **decisão de produto do app**, nunca default da fundação:
 *
 * - **`false` (DEFAULT) — o dado VOLTA no aparelho novo.** É o que a maioria dos apps quer: o Super 8
 *   restaurar o progresso, o Lua Certa o histórico, o Hora do Remédio os lembretes. Trocar de celular
 *   e perder tudo seria percebido como defeito. Nesse modo o arquivo entra no backup do iCloud (iOS) e
 *   no Auto Backup do Google (Android) — de propósito.
 * - **`true` — o dado NUNCA sai do aparelho.** Para app cujo argumento é exatamente esse. Caso
 *   motivador: **Confere QR** (`confere-qr`), cujo cofre guarda **chaves Pix** (CPF, e-mail ou
 *   telefone — PII, inclusive de terceiros) e cuja landing/Política de Privacidade afirmam por
 *   escrito que "o cofre nunca sai do seu aparelho". Sem esta marcação a frase seria factualmente
 *   falsa: no iOS **todo** o container do app, exceto `tmp/` e `Library/Caches/`, entra no backup do
 *   iCloud e do Finder a menos que o arquivo (ou o diretório que o contém) esteja marcado com
 *   `NSURLIsExcludedFromBackupKey`.
 *
 * **iOS:** o banco vive em `Library/Application Support/[SYNC_DATABASE_DIRECTORY]/<name>/` — um
 * diretório **por banco**, e é ele que recebe a marcação. Diretório (e não arquivo) porque o SQLite
 * em modo WAL mantém `-wal` e `-shm` ao lado do `.db`: marcar só o `.db` deixaria as escritas mais
 * recentes viajando para a nuvem, que é a forma clássica de "excluí do backup" que não exclui nada.
 * Como o caminho **não** depende do flag, ligar/desligar `excludeFromBackup` numa versão futura do
 * app **não** perde o banco: muda só o atributo.
 *
 * **Android:** o arquivo já nasce em `/data/data/<pkg>/databases/` (privado — nenhum outro app lê) e
 * o flag **não muda o caminho**. Mas privado ≠ fora da nuvem: o **Auto Backup** do Android inclui
 * `databases/` por padrão, e isso só se desliga **no manifesto do app** — a lib não pode (nem deve)
 * impor isso a todo consumidor. Com `excludeFromBackup = true` a lib **confere e avisa alto** no log
 * quando o manifesto ainda permite backup. O app que quer paridade com o iOS declara:
 *
 * ```xml
 * <!-- AndroidManifest.xml — API 31+ -->
 * <application android:dataExtractionRules="@xml/data_extraction_rules" ...>
 * <!-- res/xml/data_extraction_rules.xml -->
 * <data-extraction-rules>
 *   <cloud-backup><exclude domain="database" path="." /></cloud-backup>
 *   <device-transfer><exclude domain="database" path="." /></device-transfer>
 * </data-extraction-rules>
 * ```
 *
 * (ou `android:allowBackup="false"`, quando nada do app deve ir para a nuvem — o caso do Confere QR).
 *
 * @param name nome do arquivo do banco (default [DEFAULT_SYNC_DB_NAME]).
 * @param excludeFromBackup `true` mantém o banco **fora** do backup em nuvem. **Opt-in por app** —
 *   ver acima; o default `false` preserva a restauração no aparelho novo.
 */
expect fun createSyncDatabase(
    name: String = DEFAULT_SYNC_DB_NAME,
    excludeFromBackup: Boolean = false,
): SyncDatabase

/** Nome default do arquivo do banco de sync. */
const val DEFAULT_SYNC_DB_NAME: String = "kmplib_sync.db"

/**
 * Diretório onde a lib guarda os bancos de sync no iOS, dentro de
 * `Library/Application Support` — o lugar que a Apple define para arquivo de apoio criado pelo app
 * (não `Documents`, que é do usuário e aparece no app Arquivos; não `Caches`, que o sistema purga).
 *
 * Cada banco fica num **subdiretório próprio** ([syncDatabaseDirectoryName]), para que a marcação de
 * "fora do backup" possa ser aplicada por banco, cobrindo `-wal`/`-shm` junto.
 */
const val SYNC_DATABASE_DIRECTORY: String = "kmplib_databases"

/**
 * Nome do subdiretório de um banco, derivado do nome do arquivo.
 *
 * Sanitiza o que não pode virar caminho (separadores, `..`, controle) porque o nome vem do app e
 * um `../` transformaria o "diretório do banco" em escrita fora do container. Nome que sobra vazio
 * cai em [DEFAULT_SYNC_DB_NAME] — a lib não abre banco em caminho que não entende.
 */
internal fun syncDatabaseDirectoryName(name: String): String {
    val limpo = name.trim().map { c ->
        when {
            c == '/' || c == '\\' || c == ':' || c.code < 0x20 -> '_'
            else -> c
        }
    }.joinToString("")
    return if (limpo.isBlank() || limpo == "." || limpo == "..") DEFAULT_SYNC_DB_NAME else limpo
}

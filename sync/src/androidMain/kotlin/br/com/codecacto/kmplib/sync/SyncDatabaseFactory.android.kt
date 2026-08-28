package br.com.codecacto.kmplib.sync

import android.content.Context
import android.content.pm.ApplicationInfo
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.sync.db.SyncDatabase
import java.lang.ref.WeakReference

/**
 * Holder do Context para o banco de sync (mesmo padrão dos demais holders Android).
 * Inicializado via `KmpLib.init(context)` / `KmpLib.initSync(context)`.
 */
object SyncDatabaseHolder {
    private var contextRef: WeakReference<Context>? = null

    fun init(context: Context) {
        contextRef = WeakReference(context.applicationContext)
    }

    internal fun requireContext(): Context = contextRef?.get()
        ?: error(
            "SyncDatabase: Context não inicializado. Chame KmpLib.init(context) " +
                "(ou KmpLib.initSync(context)) no Application.onCreate()."
        )
}

/**
 * Banco de sync no Android: `/data/data/<pkg>/databases/<name>` — armazenamento **interno privado**,
 * que nenhum outro app lê. O caminho **não** depende de `excludeFromBackup` (não há por que mudá-lo).
 *
 * **`excludeFromBackup` no Android é declaração de manifesto, não chamada de runtime.** Privado não
 * significa fora da nuvem: o **Auto Backup** inclui o diretório `databases/` por padrão, e sair dele
 * exige `android:dataExtractionRules` (API 31+), `android:fullBackupContent` (23–30) ou
 * `android:allowBackup="false"` — decisão que a lib **não pode** impor a todo consumidor (a maioria
 * quer o dado de volta no aparelho novo). O que a lib faz aqui é **não deixar a promessa falhar
 * calada**: com `excludeFromBackup = true`, confere o que consegue conferir em runtime
 * (`FLAG_ALLOW_BACKUP`) e registra um aviso alto quando o manifesto ainda permite backup.
 *
 * O snippet pronto de `res/xml/data_extraction_rules.xml` está no KDoc do `expect` (commonMain).
 */
actual fun createSyncDatabase(name: String, excludeFromBackup: Boolean): SyncDatabase {
    val context = SyncDatabaseHolder.requireContext()
    if (excludeFromBackup) warnIfBackupStillAllowed(context, name)
    val driver = AndroidSqliteDriver(
        schema = SyncDatabase.Schema,
        context = context,
        name = name,
    )
    return SyncDatabase(driver)
}

private const val TAG = "SyncDatabase"

/**
 * O único sinal de backup legível em runtime. **Não** dá para inspecionar o conteúdo de
 * `dataExtractionRules`, então o aviso diz o que é verdade: "permite backup — confira se você excluiu
 * o domínio `database`". Prometer certeza que a plataforma não oferece seria pior que avisar de mais.
 */
private fun warnIfBackupStillAllowed(context: Context, name: String) {
    val allowsBackup = (context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0
    if (!allowsBackup) return
    AppLogger.w(
        TAG,
        "excludeFromBackup=true, mas o manifesto deste app permite backup (android:allowBackup). " +
            "O banco '$name' pode ir para a nuvem via Auto Backup. Exclua o domínio 'database' em " +
            "android:dataExtractionRules (API 31+) / android:fullBackupContent (23-30), ou use " +
            "android:allowBackup=\"false\".",
    )
}

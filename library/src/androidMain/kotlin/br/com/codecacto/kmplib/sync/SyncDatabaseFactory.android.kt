package br.com.codecacto.kmplib.sync

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
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

actual fun createSyncDatabase(name: String): SyncDatabase {
    val driver = AndroidSqliteDriver(
        schema = SyncDatabase.Schema,
        context = SyncDatabaseHolder.requireContext(),
        name = name,
    )
    return SyncDatabase(driver)
}

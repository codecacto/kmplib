package br.com.codecacto.kmplib.sync

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import br.com.codecacto.kmplib.sync.db.SyncDatabase

actual fun createSyncDatabase(name: String): SyncDatabase {
    val driver = NativeSqliteDriver(
        schema = SyncDatabase.Schema,
        name = name,
    )
    return SyncDatabase(driver)
}

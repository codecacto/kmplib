package br.com.codecacto.kmplib.bible

import co.touchlab.sqliter.DatabaseConfiguration
import co.touchlab.sqliter.DatabaseConnection
import co.touchlab.sqliter.DatabaseManager
import co.touchlab.sqliter.FieldType
import co.touchlab.sqliter.JournalMode
import co.touchlab.sqliter.NO_VERSION_CHECK
import co.touchlab.sqliter.createDatabaseManager
import co.touchlab.sqliter.withStatement
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

/**
 * SQLite do sistema pelo SQLiter (o motor do driver nativo do SQLDelight). `NO_VERSION_CHECK`: o
 * arquivo do gerador não tem `user_version` e nada deve ser gravado nele; `JournalMode.DELETE` é o
 * modo em que o arquivo já vem (nenhum PRAGMA o altera).
 */
private class IosBibleSqlConnection(
    private val manager: DatabaseManager,
    private val connection: DatabaseConnection,
) : BibleSqlConnection {
    override fun query(sql: String, args: List<Any>): List<List<Any?>> = connection.withStatement(sql) {
        args.forEachIndexed { i, a ->
            when (a) {
                is Long -> bindLong(i + 1, a)
                is Int -> bindLong(i + 1, a.toLong())
                else -> bindString(i + 1, a.toString())
            }
        }
        val cursor = query()
        val out = ArrayList<List<Any?>>()
        while (cursor.next()) {
            out += (0 until cursor.columnCount).map { i ->
                when (cursor.getType(i)) {
                    FieldType.TYPE_NULL -> null
                    FieldType.TYPE_INTEGER -> cursor.getLong(i)
                    FieldType.TYPE_FLOAT -> cursor.getDouble(i)
                    else -> cursor.getString(i)
                }
            }
        }
        out
    }

    override fun close() {
        connection.close()
    }
}

internal actual fun openBibleSqlConnection(path: String): BibleSqlConnection {
    val dir = path.substringBeforeLast('/')
    val name = path.substringAfterLast('/')
    val manager = createDatabaseManager(
        DatabaseConfiguration(
            name = name,
            version = NO_VERSION_CHECK,
            create = {},
            journalMode = JournalMode.DELETE,
            extendedConfig = DatabaseConfiguration.Extended(basePath = dir),
        ),
    )
    return IosBibleSqlConnection(manager, manager.createMultiThreadedConnection())
}

@OptIn(ExperimentalForeignApi::class)
actual fun bibleDefaultDirectory(): String {
    val root = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .firstOrNull() as? String ?: ""
    return "$root/kmplib-bible"
}

@OptIn(ExperimentalForeignApi::class)
internal actual fun bibleEnsureDirectory(path: String) {
    val fm = NSFileManager.defaultManager
    if (!fm.fileExistsAtPath(path)) fm.createDirectoryAtPath(path, true, null, null)
    // Conteúdo reproduzível (vem no pacote do app): fora do iCloud.
    NSURL.fileURLWithPath(path).setResourceValue(NSNumber(bool = true), NSURLIsExcludedFromBackupKey, null)
}

internal actual fun bibleFileExists(path: String): Boolean = NSFileManager.defaultManager.fileExistsAtPath(path)

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun bibleReadText(path: String): String? =
    if (!bibleFileExists(path)) null else NSString.stringWithContentsOfFile(path, NSUTF8StringEncoding, null)

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal actual fun bibleWriteAtomically(path: String, bytes: ByteArray) {
    val data = if (bytes.isEmpty()) NSData() else bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    // `atomically = true`: o Foundation grava num auxiliar e troca — nunca fica arquivo pela metade.
    check(data.writeToFile(path, atomically = true)) { "não foi possível instalar ${path.substringAfterLast('/')}" }
}

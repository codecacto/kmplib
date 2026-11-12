package br.com.codecacto.kmplib.bible

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import br.com.codecacto.kmplib.core.context.AndroidAppContext
import java.io.File
import java.io.FileOutputStream

/** `SQLiteDatabase` do framework, aberto OPEN_READONLY — a API oficial, sem cópia de esquema. */
private class AndroidBibleSqlConnection(private val db: SQLiteDatabase) : BibleSqlConnection {
    override fun query(sql: String, args: List<Any>): List<List<Any?>> {
        // O SQLite compara o texto ligado com coluna INTEGER por afinidade numérica (cap = '3' casa).
        db.rawQuery(sql, args.map { it.toString() }.toTypedArray()).use { c ->
            val out = ArrayList<List<Any?>>(c.count.coerceAtLeast(0))
            while (c.moveToNext()) {
                out += (0 until c.columnCount).map { i ->
                    when (c.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> null
                        Cursor.FIELD_TYPE_INTEGER -> c.getLong(i)
                        Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                        else -> c.getString(i)
                    }
                }
            }
            return out
        }
    }

    override fun close() = db.close()
}

internal actual fun openBibleSqlConnection(path: String): BibleSqlConnection =
    AndroidBibleSqlConnection(
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS),
    )

actual fun bibleDefaultDirectory(): String =
    File(AndroidAppContext.require().noBackupFilesDir, "kmplib-bible").absolutePath

internal actual fun bibleEnsureDirectory(path: String) {
    File(path).mkdirs()
}

internal actual fun bibleFileExists(path: String): Boolean = File(path).isFile

internal actual fun bibleReadText(path: String): String? =
    File(path).takeIf { it.isFile }?.readText(Charsets.UTF_8)

internal actual fun bibleWriteAtomically(path: String, bytes: ByteArray) {
    val target = File(path)
    val tmp = File(target.parentFile, target.name + ".tmp")
    FileOutputStream(tmp).use { out ->
        out.write(bytes)
        out.fd.sync()
    }
    if (!tmp.renameTo(target)) {
        target.delete()
        check(tmp.renameTo(target)) { "não foi possível instalar ${target.name}" }
    }
}

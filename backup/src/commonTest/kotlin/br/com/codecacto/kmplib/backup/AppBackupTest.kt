@file:OptIn(kotlin.time.ExperimentalTime::class)

package br.com.codecacto.kmplib.backup

import kotlinx.coroutines.test.runTest
import kotlin.time.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppBackupTest {

    private class Source(override val appSlug: String = "meu-dizimo", override val schemaVersion: Int = 3) : BackupSource {
        override val appVersion = "1.2.0"
        var stored = """{"lancamentos":[1,2]}"""
        var files = mapOf("comprovante-1.jpg" to byteArrayOf(1, 2, 3))
        var migratedFrom: Int? = null
        var failReplace = false
        override suspend fun exportData() = stored
        override suspend fun exportAttachments() = files
        override suspend fun migrate(data: String, fromSchema: Int): String { migratedFrom = fromSchema; return data.replace("lancamentos", "lancamentos_v3") }
        override suspend fun replaceAll(data: String, attachments: Map<String, ByteArray>) {
            if (failReplace) error("transação desfeita")
            stored = data; files = attachments
        }
    }

    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-11T15:00:00Z") }

    @Test
    fun `zip e padrao e ida e volta`() {
        val entries = listOf("manifesto.json" to "{}".encodeToByteArray(), "anexos/ç.jpg" to ByteArray(1000) { it.toByte() })
        val zip = ZipStore.write(entries)
        // Assinaturas PK: local no início, fim do diretório no fim.
        assertEquals(0x50, zip[0].toInt()); assertEquals(0x4B, zip[1].toInt())
        val back = ZipStore.read(zip)
        assertEquals(entries.map { it.first }, back.keys.toList())
        assertContentEquals(entries[1].second, back["anexos/ç.jpg"])
        assertEquals(0xCBF43926.toInt(), ZipStore.crc32("123456789".encodeToByteArray()))
    }

    @Test
    fun `nomes inseguros sao recusados`() {
        assertFalse(ZipStore.isSafeName("../x"))
        assertFalse(ZipStore.isSafeName("/etc/x"))
        assertFalse(ZipStore.isSafeName("a\\b"))
        assertTrue(ZipStore.isSafeName("anexos/foto.jpg"))
        assertFailsWith<IllegalArgumentException> { ZipStore.write(listOf("../x" to byteArrayOf())) }
    }

    @Test
    fun `nome do arquivo`() {
        assertEquals("meu-dizimo-03-11-2026.copia", BackupArchive.fileName("meu-dizimo", LocalDate(2026, 11, 3)))
    }

    @Test
    fun `gera e restaura a copia`() = runTest {
        val src = Source()
        val manager = AppBackupManager(src, clock) { TimeZone.of("America/Cuiaba") }
        val file = manager.create()
        assertEquals("meu-dizimo-11-11-2026.copia", file.fileName)
        assertEquals(3, file.manifest.schema)
        src.stored = "{}"; src.files = emptyMap()
        val r = manager.restore(file.bytes)
        assertIs<BackupRestoreResult.Restored>(r)
        assertEquals("""{"lancamentos":[1,2]}""", src.stored)
        assertContentEquals(byteArrayOf(1, 2, 3), src.files["comprovante-1.jpg"])
    }

    @Test
    fun `copia de outro app e recusada sem tocar em nada`() = runTest {
        val other = AppBackupManager(Source(appSlug = "jejuar"), clock).create()
        val src = Source()
        val r = AppBackupManager(src, clock).restore(other.bytes)
        assertEquals(BackupRestoreResult.Failed(BackupRestoreError.WRONG_APP), r)
        assertEquals("""{"lancamentos":[1,2]}""", src.stored)
    }

    @Test
    fun `esquema antigo migra e mais novo e recusado`() = runTest {
        val old = AppBackupManager(Source(schemaVersion = 1), clock).create()
        val src = Source(schemaVersion = 3)
        assertIs<BackupRestoreResult.Restored>(AppBackupManager(src, clock).restore(old.bytes))
        assertEquals(1, src.migratedFrom)
        assertTrue("lancamentos_v3" in src.stored)
        val newer = AppBackupManager(Source(schemaVersion = 9), clock).create()
        assertEquals(BackupRestoreResult.Failed(BackupRestoreError.NEWER_SCHEMA), AppBackupManager(Source(), clock).restore(newer.bytes))
    }

    @Test
    fun `arquivo corrompido ou truncado vira erro claro`() = runTest {
        val good = AppBackupManager(Source(), clock).create().bytes
        val src = Source()
        val m = AppBackupManager(src, clock)
        assertEquals(BackupRestoreResult.Failed(BackupRestoreError.CORRUPT), m.restore(good.copyOf(good.size / 2)))
        val flipped = good.copyOf().also { it[60] = (it[60] + 1).toByte() }
        assertEquals(BackupRestoreResult.Failed(BackupRestoreError.CORRUPT), m.restore(flipped))
        assertEquals(BackupRestoreResult.Failed(BackupRestoreError.CORRUPT), m.restore("não é zip".encodeToByteArray()))
        assertEquals("""{"lancamentos":[1,2]}""", src.stored)
    }

    @Test
    fun `falha na transacao do app nao conta como restaurado`() = runTest {
        val file = AppBackupManager(Source(), clock).create()
        val src = Source().apply { failReplace = true }
        assertEquals(BackupRestoreResult.Failed(BackupRestoreError.FAILED), AppBackupManager(src, clock).restore(file.bytes))
    }
}

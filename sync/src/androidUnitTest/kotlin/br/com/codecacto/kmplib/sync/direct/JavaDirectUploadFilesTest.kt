package br.com.codecacto.kmplib.sync.direct

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** O `java.io` de verdade (o que roda no aparelho), num diretório temporário da JVM. */
class JavaDirectUploadFilesTest {

    private val raiz: File = Files.createTempDirectory("direct-upload").toFile()
    private val fs = JavaDirectUploadFiles

    @AfterTest
    fun limpar() {
        raiz.deleteRecursively()
    }

    private fun arquivo(nome: String, bytes: ByteArray): String {
        val f = File(raiz, nome)
        f.parentFile.mkdirs()
        f.writeBytes(bytes)
        return f.absolutePath
    }

    @Test
    fun moverTiraDaOrigemEMantemOConteudo() {
        val origem = arquivo("tmp/v.mp4", ByteArray(1000) { it.toByte() })
        val destino = File(raiz, "fila/job/media").absolutePath
        assertTrue(fs.move(origem, destino))
        assertFalse(File(origem).exists())
        assertEquals(1000L, fs.size(destino))
        assertFalse(fs.move(origem, destino), "origem inexistente")
    }

    @Test
    fun escritaAtomicaSubstituiSemSobraDeTemporario() {
        val p = File(raiz, "fila/job/job.json").absolutePath
        assertTrue(fs.writeTextAtomic(p, "{\"a\":1}"))
        assertTrue(fs.writeTextAtomic(p, "{\"a\":2}"))
        assertEquals("{\"a\":2}", fs.readText(p))
        assertFalse(File("$p.tmp").exists())
        assertNull(fs.readText(File(raiz, "nao-existe").absolutePath))
    }

    @Test
    fun lerTrechoPorPosicionamento() {
        val p = arquivo("m", "0123456789AB".encodeToByteArray())
        assertContentEquals("4567".encodeToByteArray(), fs.readRange(p, 4, 4))
        assertContentEquals("89AB".encodeToByteArray(), fs.readRange(p, 8, 4))
        assertNull(fs.readRange(p, 10, 4), "passa do fim")
        assertNull(fs.readRange(p, -1, 2))
    }

    @Test
    fun gravarTrechoEmArquivoProprio() {
        val p = arquivo("m", "0123456789AB".encodeToByteArray())
        val parte = File(raiz, "part-2").absolutePath
        assertTrue(fs.writeRange(p, 4, 4, parte))
        assertContentEquals("4567".encodeToByteArray(), File(parte).readBytes())
        assertFalse(fs.writeRange(p, 10, 4, parte))
    }

    @Test
    fun listarCopiarEApagarRecursivo() {
        arquivo("fila/a/media", byteArrayOf(1))
        arquivo("fila/b/job.json", byteArrayOf(2))
        val fila = File(raiz, "fila").absolutePath
        assertEquals(setOf("a", "b"), fs.list(fila).toSet())
        assertTrue(fs.copy(File(raiz, "fila/a/media").absolutePath, File(raiz, "c").absolutePath))
        assertTrue(fs.deleteRecursively(File(raiz, "fila/a").absolutePath))
        assertEquals(listOf("b"), fs.list(fila))
        assertTrue(fs.deleteRecursively(File(raiz, "fila/inexistente").absolutePath))
        assertEquals(-1L, fs.size(fila), "diretório não é arquivo")
        assertTrue(fs.mkdirs(File(raiz, "x/y/z").absolutePath))
    }

    @Test
    fun armazenamentoDeVerdadeIdaEVolta() = kotlinx.coroutines.test.runTest {
        val storage = FileDirectUploadStorage(File(raiz, "fila").absolutePath, fs)
        val origem = arquivo("tmp/v.mp4", ByteArray(12) { 7 })
        assertEquals(12L, storage.importMedia("job-1", origem, move = true))
        val job = DirectUploadJob(
            id = "job-1", accountId = "a", kind = "k", targetId = "t", contentType = "video/mp4", sizeBytes = 12,
            fileName = "m", createdAtMillis = 1, session = DirectUploadSession("u", partSizeBytes = 4, partCount = 3, partUrls = mapOf(1 to "x")),
            completedParts = mapOf(1 to "\"e\""),
        )
        assertTrue(storage.save(job))
        assertEquals(listOf(job), storage.loadAll())
        assertTrue(storage.hasMedia("job-1", 12))
        File(raiz, "fila/orfa").mkdirs()
        assertEquals(listOf("orfa"), storage.orphanIds())
        assertTrue(storage.delete("job-1"))
        assertTrue(storage.loadAll().isEmpty())
    }
}

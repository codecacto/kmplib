package br.com.codecacto.kmplib.analytics

import br.com.codecacto.kmplib.core.storage.BlobStore
import br.com.codecacto.kmplib.core.storage.InMemoryBlobStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

class AnalyticsQueueTest {

    private fun evento(ms: Long, url: String) = QueuedAnalyticsEvent(
        id = newAnalyticsEventId(ms),
        url = url,
        name = "e",
        data = buildJsonObject { put("k", JsonPrimitive(1)) },
        timestamp = ms / 1000,
        language = "pt-BR",
    )

    @Test
    fun idOrdenaPelaHoraEPelaChamada_eServeDeNomeDeArquivo() {
        val a = newAnalyticsEventId(1_000)
        val b = newAnalyticsEventId(1_000)
        val c = newAnalyticsEventId(999)
        assertTrue(a < b)
        assertTrue(c < a)
        assertTrue(br.com.codecacto.kmplib.core.storage.isValidBlobId(BlobAnalyticsQueue.blobId(a)))
    }

    @Test
    fun filaEmDisco_guardaLeEmOrdemEApaga() = runTest {
        val store = InMemoryBlobStore()
        val q = BlobAnalyticsQueue(store)
        val e2 = evento(2_000, "/app/b")
        val e1 = evento(1_000, "/app/a")
        assertTrue(q.append(e2))
        assertTrue(q.append(e1))
        store.write("outro-arquivo.bin", byteArrayOf(1))
        assertEquals(2, q.size())
        assertEquals(listOf(e1, e2), q.oldest(10))
        assertEquals(listOf(e1), q.oldest(1))

        // Sobrevive a uma instância nova sobre o mesmo disco (app reaberto).
        val reaberta = BlobAnalyticsQueue(store)
        assertEquals(listOf(e1, e2), reaberta.oldest(10))

        reaberta.remove(listOf(e1.id))
        assertEquals(listOf(e2), q.oldest(10))
        assertTrue(store.exists("outro-arquivo.bin"), "não toca no que não é da fila")
    }

    @Test
    fun arquivoIlegivel_eApagadoSemTravarAFila() = runTest {
        val store = InMemoryBlobStore()
        val q = BlobAnalyticsQueue(store)
        store.write(BlobAnalyticsQueue.blobId("0000000000001-0000000000-00000000"), "{lixo".encodeToByteArray())
        val bom = evento(5_000, "/app/ok")
        q.append(bom)
        assertEquals(listOf(bom), q.oldest(10))
        assertEquals(1, q.size())
    }

    @Test
    fun discoQueFalha_naoLanca() = runTest {
        val quebrado = object : BlobStore {
            override suspend fun write(id: String, bytes: ByteArray): Boolean = error("disco")
            override suspend fun read(id: String): ByteArray? = error("disco")
            override suspend fun exists(id: String): Boolean = error("disco")
            override suspend fun sizeOf(id: String): Long = error("disco")
            override suspend fun delete(id: String): Boolean = error("disco")
            override suspend fun ids(): List<String> = error("disco")
        }
        val q = BlobAnalyticsQueue(quebrado)
        assertFalse(q.append(evento(1, "/app")))
        assertEquals(emptyList(), q.oldest(5))
        assertEquals(0, q.size())
        q.remove(listOf("x"))
    }

    @Test
    fun filaEmMemoria() = runTest {
        val q = InMemoryAnalyticsQueue()
        val b = evento(2, "/app/b")
        val a = evento(1, "/app/a")
        q.append(b)
        q.append(a)
        assertEquals(listOf(a), q.oldest(1))
        q.remove(listOf(a.id))
        assertEquals(1, q.size())
    }
}

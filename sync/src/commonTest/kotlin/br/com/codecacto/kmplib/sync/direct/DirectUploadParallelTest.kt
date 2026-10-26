package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.sync.rest.UploadRetryPolicy
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2.327.0 — partes em paralelo, arquivos em paralelo, cabeçalhos por parte e pausa persistente. */
class DirectUploadParallelTest {

    private val root = "/data/queue"
    private val conteudo = "0123456789ABCDEF".encodeToByteArray() // 16 bytes = 4 partes de 4

    private class Cena(
        val files: MemoryDirectUploadFiles = MemoryDirectUploadFiles(),
        val backend: FakeDirectUploadBackend = FakeDirectUploadBackend(partSize = 4, partCount = 4),
        var conta: String? = "conta-a",
        var ids: Int = 0,
    ) {
        val transport = FakePartTransport(files)
        val scheduler = RecordingScheduler()
        val storage = FileDirectUploadStorage("/data/queue", files)

        fun outbox(concurrency: DirectUploadConcurrency = DirectUploadConcurrency.Sequential) = DirectUploadOutbox(
            name = "teste",
            backend = backend,
            accountId = { conta },
            storage = storage,
            transport = transport,
            scheduler = scheduler,
            retry = UploadRetryPolicy(baseDelayMillis = 1_000, maxDelayMillis = 8_000, maxAttempts = 3),
            isMetered = { false },
            nowMillis = { 1_000_000L },
            idFactory = { "job-${++ids}" },
            concurrency = concurrency,
        )
    }

    private suspend fun DirectUploadOutbox.enfileirar(c: Cena, nome: String = "f1.jpg", bytes: ByteArray = conteudo): DirectUploadJob {
        val caminho = "/cache/fotos/$nome"
        c.files.mkdirs("/cache/fotos")
        c.files.files[caminho] = bytes.copyOf()
        return assertIs<DirectUploadEnqueueResult.Queued>(
            enqueue(DirectUploadRequest(sourcePath = caminho, kind = "event-photo", targetId = "ev-1", contentType = "image/jpeg")),
        ).job
    }

    /** Transporte que segura cada parte até o teste soltar, e mede quantas estão em voo ao mesmo tempo. */
    private class Portao(c: Cena) {
        var emVoo = 0
        var picoEmVoo = 0
        val chegaram = mutableListOf<String>()
        val portas = mutableMapOf<String, CompletableDeferred<Unit>>()

        init {
            c.transport.onUpload = { pedido ->
                emVoo++
                picoEmVoo = maxOf(picoEmVoo, emVoo)
                chegaram += pedido.key
                portas.getOrPut(pedido.key) { CompletableDeferred() }.await()
                emVoo--
            }
        }

        fun soltarTudo() {
            portas.values.forEach { it.complete(Unit) }
        }
    }

    @Test
    fun partesDoMesmoArquivoSobemEmParaleloAteOTetoEFechamComTodasAsEtagsEmOrdem() = runTest {
        val c = Cena()
        val fila = c.outbox(DirectUploadConcurrency(files = 1, partsPerFile = 3))
        fila.enfileirar(c)
        val portao = Portao(c)

        val dreno = async { fila.drainNow() }
        runCurrent()
        assertEquals(3, portao.emVoo, "três partes em voo juntas")
        assertEquals(listOf("job-1.1", "job-1.2", "job-1.3"), portao.chegaram)
        // Soltar a parte 2 abre a vaga para a 4 (janela deslizante), antes de a 1 terminar.
        portao.portas["job-1.2"]!!.complete(Unit)
        runCurrent()
        assertEquals(listOf("job-1.1", "job-1.2", "job-1.3", "job-1.4"), portao.chegaram)
        portao.soltarTudo()
        while (!dreno.isCompleted) {
            portao.soltarTudo()
            runCurrent()
            yield()
        }
        val resumo = dreno.await()

        assertEquals(3, portao.picoEmVoo, "nunca mais que partsPerFile")
        assertEquals(1, resumo.completed)
        assertEquals(4, resumo.partsUploaded)
        assertEquals((1..4).map { DirectUploadCompletedPart(it, "\"e$it\"") }, c.backend.completedWith)
        assertTrue(fila.jobs.value.isEmpty())
    }

    @Test
    fun etagDeCadaParteEGravadaNaHoraERetomadaSobeSoOQueFalta() = runTest {
        val c = Cena()
        val fila = c.outbox(DirectUploadConcurrency(files = 1, partsPerFile = 2))
        fila.enfileirar(c)
        // Parte 3 cai sem rede: a 1 e a 2 (em voo juntas) já foram aceitas e ficam gravadas.
        c.transport.script.addAll(listOf({ null }, { null }, { DirectUploadPartResult.Offline }))
        val primeira = fila.drainNow()
        assertTrue(primeira.paused)
        val gravado = c.storage.loadAll().single()
        assertEquals(setOf(1, 2), gravado.completedParts.keys.minus(4))

        // "Matar o app": fila nova sobre o mesmo disco.
        val nova = c.outbox(DirectUploadConcurrency(files = 1, partsPerFile = 2))
        c.transport.uploaded.clear()
        val segunda = nova.drainNow()
        assertEquals(1, segunda.completed)
        assertFalse(1 in c.transport.uploaded || 2 in c.transport.uploaded, "não reenvia o que já foi aceito")
        assertEquals(4, c.backend.completedWith!!.size)
    }

    @Test
    fun variosArquivosSobemJuntosNumaFilaSo() = runTest {
        val c = Cena(backend = FakeDirectUploadBackend(partSize = 16, partCount = 1))
        val fila = c.outbox(DirectUploadConcurrency(files = 3, partsPerFile = 1))
        repeat(4) { fila.enfileirar(c, nome = "f$it.jpg") }
        val portao = Portao(c)

        val dreno = async { fila.drainNow() }
        runCurrent()
        assertEquals(3, portao.emVoo, "três arquivos em voo")
        assertEquals(listOf("job-1.1", "job-2.1", "job-3.1"), portao.chegaram, "os mais antigos primeiro")
        portao.portas["job-1.1"]!!.complete(Unit)
        runCurrent()
        assertTrue("job-4.1" in portao.chegaram, "o quarto entra quando um termina")
        while (!dreno.isCompleted) {
            portao.soltarTudo()
            runCurrent()
            yield()
        }
        val resumo = dreno.await()
        assertEquals(4, resumo.completed)
        assertEquals(3, portao.picoEmVoo)
        assertTrue(fila.jobs.value.isEmpty())
    }

    @Test
    fun parteVencidaNoParaleloEReassinadaSoElaEAsOutrasNaoSobemDeNovo() = runTest {
        val c = Cena()
        val fila = c.outbox(DirectUploadConcurrency(files = 1, partsPerFile = 4))
        fila.enfileirar(c)
        c.transport.script.addAll(listOf({ null }, { DirectUploadPartResult.Expired }, { null }, { null }))
        val resumo = fila.drainNow()

        assertEquals(1, resumo.completed)
        assertTrue("presign:2" in c.backend.calls, "reassina só a parte vencida: ${c.backend.calls}")
        assertEquals(listOf(1, 3, 4, 2), c.transport.uploaded)
    }

    @Test
    fun falhaTerminalDeUmaParteNaoLancaNovasEMarcaFalhou() = runTest {
        val c = Cena()
        val fila = c.outbox(DirectUploadConcurrency(files = 1, partsPerFile = 2))
        fila.enfileirar(c)
        c.transport.script.addAll(listOf({ DirectUploadPartResult.Failed(400 + 22) }, { null }))
        val resumo = fila.drainNow()

        assertEquals(1, resumo.failed)
        assertEquals(listOf(2), c.transport.uploaded, "a parte já em voo terminou; nenhuma nova saiu")
        val job = fila.jobs.value.single()
        assertEquals(DirectUploadStatus.FAILED, job.status)
        assertEquals(setOf(2), job.completedParts.keys, "a ETag da parte aceita ficou")
    }

    @Test
    fun cabecalhoDaParteVaiNoPutDelaEVenceODaSessaoSemDiferencaDeCaixa() = runTest {
        val c = Cena()
        c.backend.startResults.add(
            DomainResult.Success(
                DirectUploadSession(
                    uploadId = "up-1",
                    partSizeBytes = 4,
                    partCount = 4,
                    partUrls = (1..4).associateWith { "https://storage.example.com/o?part=$it" },
                    signedHeaders = mapOf("x-amz-meta-evento" to "ev-1", "X-Amz-Checksum-Sha256" to "SESSAO"),
                    partHeaders = mapOf(
                        1 to mapOf("x-amz-checksum-sha256" to "P1", "Content-Type" to "application/octet-stream"),
                        3 to mapOf("x-amz-checksum-sha256" to "P3"),
                    ),
                ),
            ),
        )
        val pedidos = mutableListOf<DirectUploadPartRequest>()
        c.transport.onUpload = { pedidos += it }
        val fila = c.outbox(DirectUploadConcurrency(files = 1, partsPerFile = 2))
        fila.enfileirar(c)
        assertEquals(1, fila.drainNow().completed)

        val porParte = pedidos.associateBy { it.partNumber }
        assertEquals(mapOf("x-amz-meta-evento" to "ev-1", "x-amz-checksum-sha256" to "P1"), porParte.getValue(1).headers)
        assertEquals("application/octet-stream", porParte.getValue(1).contentType)
        assertEquals(mapOf("x-amz-meta-evento" to "ev-1", "X-Amz-Checksum-Sha256" to "SESSAO"), porParte.getValue(2).headers)
        assertNull(porParte.getValue(2).contentType)
        assertEquals("P3", porParte.getValue(3).headers["x-amz-checksum-sha256"])
        assertFalse("P1" in porParte.getValue(1).toString(), "valor de cabeçalho assinado nunca no toString")
    }

    @Test
    fun pausaDaFilaSobreviveAMatarOAppENaoPedeNovaTentativa() = runTest {
        val c = Cena()
        val fila = c.outbox()
        fila.enfileirar(c)
        assertTrue(fila.pauseAll())
        assertTrue(fila.isPaused.value)

        val nova = c.outbox()
        nova.resume()
        assertTrue(nova.isPaused.value, "a pausa foi lida do disco")
        val resumo = nova.drainNow()
        assertTrue(resumo.queuePaused)
        assertFalse(resumo.needsRetry, "pausa da pessoa não faz o WorkManager girar")
        assertTrue(c.transport.uploaded.isEmpty())

        c.scheduler.requests.clear()
        nova.enfileirar(c, nome = "f2.jpg")
        assertTrue(c.scheduler.requests.isEmpty(), "enfileirar com a fila pausada não agenda")

        assertTrue(nova.resumeAll())
        assertFalse(nova.isPaused.value)
        assertTrue(c.scheduler.requests.isNotEmpty(), "retomar agenda")
        assertEquals(2, nova.drainNow().completed)
        assertFalse(c.outbox().also { it.resume() }.isPaused.value, "a retomada também foi gravada")
    }

    @Test
    fun pausaDeUmEnvioSobreviveEOsOutrosSobem() = runTest {
        val c = Cena(backend = FakeDirectUploadBackend(partSize = 16, partCount = 1))
        val fila = c.outbox()
        val a = fila.enfileirar(c, nome = "a.jpg")
        fila.enfileirar(c, nome = "b.jpg")
        assertTrue(fila.pauseJob(a.id))
        assertTrue(a.id in c.transport.cancelled, "a parte em voo dele para")

        val nova = c.outbox()
        val resumo = nova.drainNow()
        assertEquals(1, resumo.completed)
        assertEquals(1, resumo.pausedByUser)
        assertFalse(resumo.needsRetry)
        val parado = nova.jobs.value.single()
        assertEquals(a.id, parado.id)
        assertTrue(parado.pausedByUser)

        assertTrue(nova.resumeJob(a.id))
        assertEquals(1, nova.drainNow().completed)
        assertTrue(nova.jobs.value.isEmpty())
    }

    @Test
    fun pausarNoMeioDoEnvioParaNoProximoPassoSemPedirNovaTentativa() = runTest {
        val c = Cena()
        val fila = c.outbox()
        val job = fila.enfileirar(c)
        c.transport.onUpload = { pedido -> if (pedido.partNumber == 2) fila.pauseJob(job.id) }
        val resumo = fila.drainNow()
        assertEquals(1, resumo.pausedByUser)
        assertFalse(resumo.paused)
        assertFalse(resumo.needsRetry)
        assertEquals(setOf(1, 2), fila.jobs.value.single().completedParts.keys, "a parte em voo terminou e ficou")
    }

    @Test
    fun concorrenciaForaDosLimitesERecusada() {
        assertFailsWith<IllegalArgumentException> { DirectUploadConcurrency(files = 0) }
        assertFailsWith<IllegalArgumentException> { DirectUploadConcurrency(partsPerFile = DirectUploadConcurrency.MAX_PARTS_PER_FILE + 1) }
        assertEquals(6, DirectUploadConcurrency(files = 3, partsPerFile = 2).maxInFlightParts)
    }

    @Test
    fun cabecalhosDaParteSaoLidosDoContratoEReassinaturaOsTroca() {
        val inicio = Json.parseToJsonElement(
            """{"uploadId":"u","photoId":"p","partSizeBytes":4,"partCount":2,"expiresAt":"2026-10-25T12:00:00Z",
               "parts":[{"partNumber":1,"url":"https://s/1","headers":{"x-amz-checksum-sha256":"HASH-PARTE-UM","n":1}},
                        {"partNumber":2,"url":"https://s/2","headers":null}]}""",
        ).jsonObject
        val sessao = parseDirectUploadSession(inicio, "photoId")!!
        assertEquals(mapOf(1 to mapOf("x-amz-checksum-sha256" to "HASH-PARTE-UM")), sessao.partHeaders, "valor não texto é descartado")
        assertFalse("HASH-PARTE-UM" in sessao.toString())

        val reassinadas = parseDirectUploadPartUrls(
            Json.parseToJsonElement("""{"parts":[{"partNumber":1,"url":"https://s/1b"},{"partNumber":2,"url":"https://s/2b","headers":{"x-amz-checksum-sha256":"B"}}]}""").jsonObject,
        )!!
        val junta = directUploadMergePresigned(sessao, reassinadas)
        assertEquals(mapOf(2 to mapOf("x-amz-checksum-sha256" to "B")), junta.partHeaders, "parte 1 reassinada sem cabeçalho perde o antigo")
        assertEquals("https://s/1b", junta.partUrls[1])
    }

    @Test
    fun estadoDeFilaNaoApareceComoEnvioNemComoOrfao() = runTest {
        val c = Cena()
        assertTrue(c.storage.saveQueueState(DirectUploadQueueState(pausedByUser = true)))
        assertTrue(c.storage.loadAll().isEmpty())
        assertTrue(c.storage.orphanIds().isEmpty())
        assertEquals(true, c.storage.loadQueueState()?.pausedByUser)
        assertTrue(c.files.files.containsKey("$root/.queue.json"))
    }
}

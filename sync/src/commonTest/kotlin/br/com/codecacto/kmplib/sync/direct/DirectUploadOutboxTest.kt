package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.monetization.entitlement.QuotaExceeded
import br.com.codecacto.kmplib.sync.rest.UploadRetryPolicy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectUploadOutboxTest {

    private val root = "/data/queue"
    private val tmp = "/cache/kmplib_video_prepared/v1.mp4"
    private val conteudo = "0123456789AB".encodeToByteArray() // 12 bytes = 3 partes de 4

    private class Cena(
        val files: MemoryDirectUploadFiles = MemoryDirectUploadFiles(),
        val backend: FakeDirectUploadBackend = FakeDirectUploadBackend(),
        var conta: String? = "conta-a",
        var agora: Long = 1_000_000L,
        var metered: Boolean? = false,
        var ids: Int = 0,
        val completos: MutableList<Pair<DirectUploadJob, String>> = mutableListOf(),
    ) {
        val transport = FakePartTransport(files)
        val scheduler = RecordingScheduler()
        val storage = FileDirectUploadStorage("/data/queue", files)

        fun outbox(retry: UploadRetryPolicy = UploadRetryPolicy(baseDelayMillis = 1_000, maxDelayMillis = 8_000, maxAttempts = 3)) =
            DirectUploadOutbox(
                name = "teste",
                backend = backend,
                accountId = { conta },
                storage = storage,
                transport = transport,
                scheduler = scheduler,
                retry = retry,
                isMetered = { metered },
                nowMillis = { agora },
                idFactory = { "job-${++ids}" },
                onCompleted = { job, corpo -> completos += job to corpo },
            )
    }

    private fun Cena.preparar() {
        files.mkdirs("/cache/kmplib_video_prepared")
        files.files[tmp] = conteudo.copyOf()
    }

    private fun pedido(wifiOnly: Boolean = false) = DirectUploadRequest(
        sourcePath = tmp,
        kind = "student-video",
        targetId = "req-1",
        contentType = "video/mp4",
        durationSeconds = 12,
        wifiOnly = wifiOnly,
    )

    private suspend fun DirectUploadOutbox.enfileirar(cena: Cena, wifiOnly: Boolean = false): DirectUploadJob {
        cena.preparar()
        return assertIs<DirectUploadEnqueueResult.Queued>(enqueue(pedido(wifiOnly))).job
    }

    @Test
    fun enfileirarMoveOArquivoDaPastaTemporariaEAgendaComUrgencia() = runTest {
        val c = Cena()
        val fila = c.outbox()
        val job = fila.enfileirar(c)

        assertNull(c.files.files[tmp], "o arquivo saiu da pasta temporária")
        assertContentEquals(conteudo, c.files.files["$root/job-1/media"])
        assertEquals(12L, job.sizeBytes)
        assertEquals("conta-a", job.accountId)
        assertEquals(DirectUploadStatus.QUEUED, job.status)
        assertNotNull(c.files.files["$root/job-1/job.json"], "estado persistido")
        assertEquals(listOf(DirectUploadNetworkNeed.ANY to true), c.scheduler.requests)
        assertEquals(listOf(job.id), fila.jobs.value.map { it.id })
    }

    @Test
    fun copiarDeixaOOriginalComQuemChamou() = runTest {
        val c = Cena()
        c.preparar()
        val r = c.outbox().enqueue(pedido().copy(moveSource = false))
        assertIs<DirectUploadEnqueueResult.Queued>(r)
        assertContentEquals(conteudo, c.files.files[tmp])
    }

    @Test
    fun recusaSemContaSemArquivoEComDiscoFalhando() = runTest {
        val c = Cena(conta = null)
        c.preparar()
        assertEquals(
            DirectUploadRejectReason.NO_ACCOUNT,
            assertIs<DirectUploadEnqueueResult.Rejected>(c.outbox().enqueue(pedido())).reason,
        )
        val c2 = Cena()
        assertEquals(
            DirectUploadRejectReason.SOURCE_MISSING,
            assertIs<DirectUploadEnqueueResult.Rejected>(c2.outbox().enqueue(pedido())).reason,
        )
        val c3 = Cena()
        c3.preparar()
        c3.files.failMove = true
        val r3 = assertIs<DirectUploadEnqueueResult.Rejected>(c3.outbox().enqueue(pedido()))
        assertEquals(DirectUploadRejectReason.STORAGE_FAILED, r3.reason)
        assertContentEquals(conteudo, c3.files.files[tmp], "falhou: o original fica com quem chamou")
        val c4 = Cena()
        c4.preparar()
        c4.files.failWrite = true
        assertEquals(
            DirectUploadRejectReason.STORAGE_FAILED,
            assertIs<DirectUploadEnqueueResult.Rejected>(c4.outbox().enqueue(pedido())).reason,
        )
        assertTrue(c4.files.files.keys.none { it.startsWith("$root/") }, "nada pela metade na fila")
        assertEquals(
            DirectUploadRejectReason.INVALID_REQUEST,
            assertIs<DirectUploadEnqueueResult.Rejected>(Cena().outbox().enqueue(pedido().copy(kind = " "))).reason,
        )
    }

    @Test
    fun caminhoFelizSobeTodasAsPartesFechaEApaga() = runTest {
        val c = Cena()
        val fila = c.outbox()
        val job = fila.enfileirar(c)

        val resumo = fila.drainNow()

        assertEquals(1, resumo.completed)
        assertEquals(3, resumo.partsUploaded)
        assertFalse(resumo.needsRetry)
        assertEquals(listOf(1, 2, 3), c.transport.uploaded)
        assertContentEquals("0123".encodeToByteArray(), c.transport.bodies[1])
        assertContentEquals("89AB".encodeToByteArray(), c.transport.bodies[3])
        assertEquals(listOf("start", "complete"), c.backend.calls)
        assertEquals(
            listOf(DirectUploadCompletedPart(1, "\"e1\""), DirectUploadCompletedPart(2, "\"e2\""), DirectUploadCompletedPart(3, "\"e3\"")),
            c.backend.completedWith,
        )
        assertTrue(c.files.files.keys.none { it.startsWith("$root/") }, "pasta do envio apagada ao concluir")
        assertTrue(fila.jobs.value.isEmpty())
        assertEquals(job.id, c.completos.single().first.id)
        assertTrue(c.completos.single().second.contains("SUBMITTED"))
    }

    @Test
    fun retomaDepoisDoProcessoMortoSubindoSoOQueFalta() = runTest {
        val c = Cena()
        val fila = c.outbox()
        fila.enfileirar(c)
        // Processo "morre" depois da 1ª parte: a 2ª nunca volta.
        var vez = 0
        c.transport.onUpload = { if (++vez == 2) throw kotlinx.coroutines.CancellationException("processo morto") }
        runCatching { fila.drainNow() }
        assertEquals(listOf(1), c.transport.uploaded)

        // Processo novo: outra instância sobre o MESMO disco.
        c.transport.onUpload = null
        c.transport.uploaded.clear()
        val nova = c.outbox()
        val resumo = nova.drainNow()

        assertEquals(1, resumo.completed)
        assertEquals(listOf(2, 3), c.transport.uploaded, "a parte 1 não sobe de novo")
        assertEquals(1, c.backend.startCount, "a sessão do servidor é reaproveitada")
        assertEquals(3, c.backend.completedWith?.size)
    }

    @Test
    fun urlVencidaEReassinadaAntesDeEnviar() = runTest {
        val c = Cena(backend = FakeDirectUploadBackend(urlsExpireAt = 1_000_000L + 60_000L)) // vence dentro da margem
        val fila = c.outbox()
        fila.enfileirar(c)

        fila.drainNow()

        assertEquals(listOf("start", "presign:1,2,3", "complete"), c.backend.calls)
        assertEquals(listOf(1, 2, 3), c.transport.uploaded)
    }

    @Test
    fun storageRecusandoAssinaturaReassinaUmaVezEDepoisRecua() = runTest {
        val c = Cena()
        val fila = c.outbox()
        fila.enfileirar(c)
        c.transport.script += { DirectUploadPartResult.Expired }

        assertEquals(1, fila.drainNow().completed)
        assertEquals(listOf("start", "presign:1", "complete"), c.backend.calls)

        val c2 = Cena()
        val fila2 = c2.outbox()
        fila2.enfileirar(c2)
        c2.transport.script += { DirectUploadPartResult.Expired }
        c2.transport.script += { DirectUploadPartResult.Expired }
        val r = fila2.drainNow()
        assertEquals(1, r.deferred)
        assertEquals(1_000_000L + 1_000L, r.nextAttemptAtMillis)
        assertEquals(1, fila2.jobs.value.single().attempts)
    }

    @Test
    fun storageQueEsqueceuOEnvioRecomecaDoZeroComRecuo() = runTest {
        val c = Cena()
        val fila = c.outbox()
        fila.enfileirar(c)
        c.transport.script += { null } // parte 1 ok
        c.transport.script += { DirectUploadPartResult.UploadGone }

        val r = fila.drainNow()
        assertEquals(1, r.deferred)
        val job = fila.jobs.value.single()
        assertNull(job.session)
        assertTrue(job.completedParts.isEmpty())

        c.agora += 5_000
        c.transport.uploaded.clear()
        assertEquals(1, fila.drainNow().completed)
        assertEquals(2, c.backend.startCount)
        assertEquals(listOf(1, 2, 3), c.transport.uploaded)
    }

    @Test
    fun semRedePausaEPreservaOProgresso() = runTest {
        val c = Cena()
        val fila = c.outbox()
        fila.enfileirar(c)
        c.transport.script += { null }
        c.transport.script += { DirectUploadPartResult.Offline }

        val r = fila.drainNow()
        assertTrue(r.paused)
        assertTrue(r.needsRetry)
        val job = fila.jobs.value.single()
        assertEquals(setOf(1), job.completedParts.keys)
        assertEquals(0, job.attempts, "sem rede não gasta tentativa")
        assertEquals(DirectUploadStatus.UPLOADING, job.status)

        val servidorOff = Cena()
        val f2 = servidorOff.outbox()
        f2.enfileirar(servidorOff)
        servidorOff.backend.startResults += DomainResult.Error(DomainResult.OFFLINE_CODE, "sem rede")
        assertTrue(f2.drainNow().paused)
        assertEquals(DirectUploadStatus.QUEUED, f2.jobs.value.single().status)
    }

    @Test
    fun recusaDoServidorParaOEnvioERetryVolta() = runTest {
        val c = Cena()
        val fila = c.outbox()
        val job = fila.enfileirar(c)
        c.backend.startResults += DomainResult.Error(409, "x", serverCode = "VIDEO_REQUEST_CLOSED", serverMessage = "O pedido foi encerrado.")

        val r = fila.drainNow()
        assertEquals(1, r.failed)
        val falhou = fila.jobs.value.single()
        assertEquals(DirectUploadStatus.FAILED, falhou.status)
        assertEquals("VIDEO_REQUEST_CLOSED", falhou.failure?.serverCode)
        assertEquals("O pedido foi encerrado.", falhou.failure?.message)
        assertNotNull(c.files.files["$root/${job.id}/media"], "o arquivo fica até a pessoa decidir")

        // FAILED não é drenado de novo sozinho.
        assertEquals(DirectUploadDrainSummary(), fila.drainNow())

        assertTrue(fila.retry(job.id))
        assertEquals(DirectUploadStatus.QUEUED, fila.jobs.value.single().status)
        assertEquals(1, fila.drainNow().completed)
    }

    @Test
    fun cotaViraFalhaDePlano() = runTest {
        val c = Cena()
        val fila = c.outbox()
        fila.enfileirar(c)
        c.backend.startResults += DomainResult.Quota(QuotaExceeded(feature = "video", limite = 1, contagem = 1))
        fila.drainNow()
        val f = fila.jobs.value.single().failure
        assertNotNull(f)
        assertTrue(f.isQuota)
        assertEquals(402, f.code)
    }

    @Test
    fun falhasRetentaveisEsgotamAPolitica() = runTest {
        val c = Cena()
        val fila = c.outbox()
        fila.enfileirar(c)
        repeat(3) { c.backend.startResults += DomainResult.Error(503, "fora") }

        val r1 = fila.drainNow()
        assertEquals(1, r1.deferred)
        assertEquals(1_001_000L, r1.nextAttemptAtMillis)
        assertEquals(0, fila.drainNow().completed, "antes do recuo não tenta")
        assertEquals(1, c.backend.startCount)

        c.agora = 1_001_000L
        assertEquals(1_003_000L, fila.drainNow().nextAttemptAtMillis) // 2ª: 2 s
        c.agora = 1_003_000L
        val r3 = fila.drainNow()
        assertEquals(1, r3.failed)
        assertEquals(DirectUploadOutbox.RETRY_EXHAUSTED_CODE, fila.jobs.value.single().failure?.code)
    }

    @Test
    fun envioDeOutraContaNaoSobe() = runTest {
        val c = Cena()
        val fila = c.outbox()
        fila.enfileirar(c)
        c.conta = "conta-b"
        assertEquals(DirectUploadDrainSummary(), fila.drainNow())
        assertTrue(c.backend.calls.isEmpty())
        c.conta = "conta-a"
        assertEquals(1, fila.drainNow().completed)
    }

    @Test
    fun soNoWifiEsperaRedeNaoTarifada() = runTest {
        val c = Cena(metered = true)
        val fila = c.outbox()
        fila.enfileirar(c, wifiOnly = true)
        assertEquals(listOf(DirectUploadNetworkNeed.UNMETERED to true), c.scheduler.requests)

        val r = fila.drainNow()
        assertEquals(1, r.waitingUnmetered)
        assertFalse(r.needsRetry, "não fica repetindo no 4G")
        assertTrue(c.backend.calls.isEmpty())

        c.metered = false
        assertEquals(1, fila.drainNow().completed)
    }

    @Test
    fun ligarSoNoWifiNoMeioCancelaAParteEmVoo() = runTest {
        val c = Cena()
        val fila = c.outbox()
        val job = fila.enfileirar(c)
        assertTrue(fila.setWifiOnly(job.id, true))
        assertEquals(listOf(job.id), c.transport.cancelled)
        assertTrue(fila.jobs.value.single().wifiOnly)
        assertEquals(DirectUploadNetworkNeed.UNMETERED to true, c.scheduler.requests.last())
    }

    @Test
    fun descartarApagaTudoEAvisaOServidor() = runTest {
        val c = Cena()
        val fila = c.outbox()
        val job = fila.enfileirar(c)
        c.transport.script += { null }
        c.transport.script += { DirectUploadPartResult.Offline }
        fila.drainNow()

        assertTrue(fila.discard(job.id))
        assertEquals(listOf(job.id), c.transport.cancelled)
        assertTrue("abort" in c.backend.calls)
        assertTrue(c.files.files.keys.none { it.startsWith("$root/") })
        assertTrue(fila.jobs.value.isEmpty())
        assertFalse(fila.discard(job.id))
    }

    @Test
    fun descartadoNoMeioDaParteNaoRessuscita() = runTest {
        val c = Cena()
        val fila = c.outbox()
        val job = fila.enfileirar(c)
        c.transport.onUpload = { if (it.partNumber == 2) fila.discard(job.id) }

        val r = fila.drainNow()
        assertEquals(0, r.completed)
        assertTrue(fila.jobs.value.isEmpty())
        assertTrue(c.files.files.keys.none { it.startsWith("$root/") }, "a drenagem não regravou o estado")
    }

    @Test
    fun purgaSoAContaNomeada() = runTest {
        val c = Cena()
        val fila = c.outbox()
        fila.enfileirar(c)
        c.conta = "conta-b"
        val dela = fila.enfileirar(c)

        assertEquals(1, fila.pendingCount("conta-a"))
        assertEquals(1, fila.purgeAccount("conta-a"))
        assertEquals(listOf(dela.id), fila.jobs.value.map { it.id })
        assertEquals(listOf("job-1"), c.transport.cancelled)
        assertNull(c.files.files["$root/job-1/media"])
        assertNotNull(c.files.files["$root/${dela.id}/media"])
        assertTrue(c.backend.calls.isEmpty(), "limpeza de conta não chama o servidor")
    }

    @Test
    fun segurarAFilaPausaADrenagem() = runTest {
        val c = Cena()
        val fila = c.outbox()
        fila.enfileirar(c)
        val r = fila.withDrainPaused { fila.drainNow() }
        assertTrue(r.paused)
        assertTrue(c.backend.calls.isEmpty())
    }

    @Test
    fun sessaoQueNaoFechaComOArquivoFalhaEAborta() = runTest {
        val c = Cena(backend = FakeDirectUploadBackend(partSize = 4, partCount = 5))
        val fila = c.outbox()
        fila.enfileirar(c)
        fila.drainNow()
        assertEquals(DirectUploadOutbox.INVALID_SESSION_CODE, fila.jobs.value.single().failure?.code)
        assertEquals(listOf("start", "abort"), c.backend.calls)
    }

    @Test
    fun arquivoSumidoViraFalha() = runTest {
        val c = Cena()
        val fila = c.outbox()
        val job = fila.enfileirar(c)
        c.files.files.remove("$root/${job.id}/media")
        fila.drainNow()
        assertEquals(DirectUploadOutbox.MISSING_FILE_CODE, fila.jobs.value.single().failure?.code)
    }

    @Test
    fun aberturaRecolhePastaOrfaEAgendaSoOQueEDaConta() = runTest {
        val c = Cena()
        c.files.mkdirs("$root/orfa")
        c.files.files["$root/orfa/media"] = byteArrayOf(1, 2)
        val fila = c.outbox()
        fila.enfileirar(c, wifiOnly = true)
        c.scheduler.requests.clear()

        val nova = c.outbox()
        nova.resume()
        assertTrue(c.files.files.keys.none { it.startsWith("$root/orfa") })
        assertEquals(listOf(DirectUploadNetworkNeed.UNMETERED to false), c.scheduler.requests)
        assertEquals(1, nova.jobs.value.size)
    }

    @Test
    fun eventoDeConclusaoChegaPeloFlow() = runTest {
        val c = Cena()
        val fila = c.outbox()
        val job = fila.enfileirar(c)
        fila.drainNow()
        val ev = fila.events.replayCache
        assertTrue(ev.isEmpty(), "sem replay: quem não ouvia não recebe velho")
        assertNull(fila.observeJob(job.id).first())
    }

    @Test
    fun registroAchaAFilaPeloNome() {
        val c = Cena()
        val fila = c.outbox()
        assertEquals(fila, DirectUploadOutboxRegistry.get("teste"))
    }
}

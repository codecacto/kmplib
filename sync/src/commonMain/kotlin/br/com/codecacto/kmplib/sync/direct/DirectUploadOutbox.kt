package br.com.codecacto.kmplib.sync.direct

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.core.util.newUuidV7
import br.com.codecacto.kmplib.sync.rest.DomainResult
import br.com.codecacto.kmplib.sync.rest.ReentrantSyncLock
import br.com.codecacto.kmplib.sync.rest.RestFailureClass
import br.com.codecacto.kmplib.sync.rest.UploadRetryPolicy
import br.com.codecacto.kmplib.sync.rest.classifyRestFailure
import br.com.codecacto.kmplib.sync.rest.uploadRetryDelayMillis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * **Fila de envio DURÁVEL de arquivo grande, direto ao storage** (2.287.0 — GAP-PT-M05, App do
 * Personal L-K2): upload multipart com URLs pré-assinadas (R2/S3), retomável parte a parte, que
 * sobrevive ao processo morto, opcionalmente "só no Wi-Fi".
 *
 * Não é a [RestUploadOutbox][br.com.codecacto.kmplib.sync.rest.RestUploadOutbox]: aquela manda um
 * multipart **para o nosso servidor**, em memória, num único request — serve a foto de 1 MB. Vídeo de
 * 15–100 MB não passa pelo backend (o servidor só assina e fecha), não cabe em memória e não pode
 * recomeçar do zero a cada queda de rede no 4G.
 *
 * ### O caminho de um envio
 * 1. [enqueue] **move** o arquivo pronto (ex.: o `PreparedVideo` do `VideoTranscoder`, que mora numa
 *    pasta temporária varrida a cada hora) para a área da fila — privada, fora do backup — e grava o
 *    estado. Daqui em diante matar o processo não perde nada.
 * 2. O agendador da plataforma ([DirectUploadScheduler]: **WorkManager** no Android; no iOS, o
 *    processo + a sessão de **segundo plano** do `URLSession`) acorda a fila quando há rede.
 * 3. `start` no servidor abre o envio e devolve as URLs; cada parte vai por `PUT` direto ao storage
 *    ([DirectUploadPartTransport]); a ETag de cada parte aceita é **gravada na hora** — a retomada
 *    sobe só o que falta, reassinando as URLs vencidas (`presignParts`).
 * 4. `complete` fecha o envio; a pasta do envio (arquivo inclusive) é **apagada**, e o app recebe o
 *    corpo da resposta em [events]/`onCompleted`.
 *
 * ### Conta
 * Cada envio guarda a conta que o enfileirou, e **só sobe com essa conta logada** ([accountId]): no
 * aparelho compartilhado, o vídeo de um não sobe com o token de outro. Ao sair da conta, o app chama
 * [purgeAccount] (descarta) ou deixa a fila esperar a mesma conta voltar — o `SyncAccountDataPurger`
 * faz isso sozinho quando recebe a fila (`directUploadOutboxes`).
 *
 * ### Uso
 * ```kotlin
 * // Koin — criada NA ABERTURA (createdAtStart): é por ela que o Worker do WorkManager acha a fila.
 * single(createdAtStart = true) {
 *     DirectUploadOutbox(
 *         backend = RestDirectUploadBackend(api = get(), uploadBasePath = "/v1/aluno/videos/uploads",
 *             startPath = { "/v1/aluno/video-requests/${it.targetId}/uploads" }),
 *         accountId = { get<AuthRepository>().currentUser.value?.uid },
 *     ).also { it.resume() }
 * }
 *
 * // Depois de comprimir:
 * val r = outbox.enqueue(DirectUploadRequest(
 *     sourcePath = prepared.path, kind = "student-video", targetId = requestId,
 *     contentType = prepared.mimeType, durationSeconds = prepared.durationSeconds, wifiOnly = soNoWifi,
 * ))
 * ```
 * iOS: encaminhe `application(_:handleEventsForBackgroundURLSession:completionHandler:)` para
 * `DirectUploadBackgroundEvents.handle(identifier, completionHandler)` (ver o KDoc dele).
 *
 * @param name nome da fila (trabalho do WorkManager, sessão de fundo do iOS, pasta). Duas filas no
 *   mesmo app = dois nomes.
 * @param backend as conversas com o servidor (ver [RestDirectUploadBackend]).
 * @param accountId a conta logada agora (`null` = ninguém). Lido a cada passo.
 * @param storage onde ficam estado e arquivos (default: pasta privada fora do backup).
 * @param transport quem leva as partes ao storage (default: o da plataforma).
 * @param scheduler quem acorda a fila (default: o da plataforma).
 * @param retry recuo entre falhas retentáveis (esgotado, o envio vira [DirectUploadStatus.FAILED]).
 * @param isMetered rede tarifada agora? (`null` = não sei; o transporte do iOS aplica o "só Wi-Fi" sozinho).
 * @param onCompleted chamado após cada envio aceito, com o corpo do `complete` — onde o app grava o
 *   recurso no próprio cache. Exceção aqui não desfaz nada (é registrada).
 */
class DirectUploadOutbox(
    val name: String = DEFAULT_DIRECT_UPLOAD_OUTBOX_NAME,
    private val backend: DirectUploadBackend,
    private val accountId: () -> String?,
    private val storage: DirectUploadStorage = createDirectUploadStorage(directUploadDirectoryFor(name)),
    private val transport: DirectUploadPartTransport = createPlatformDirectUploadTransport(name),
    private val scheduler: DirectUploadScheduler = createPlatformDirectUploadScheduler(),
    private val retry: UploadRetryPolicy = UploadRetryPolicy(),
    private val isMetered: () -> Boolean? = ::platformIsActiveNetworkMetered,
    private val nowMillis: () -> Long = ::currentTimeMillis,
    private val idFactory: () -> String = { newUuidV7() },
    private val onCompleted: (suspend (job: DirectUploadJob, responseBody: String) -> Unit)? = null,
) {
    init {
        require(isValidDirectUploadId(name)) { "nome de fila inválido" }
        DirectUploadOutboxRegistry.register(this)
    }

    private val cache = MutableStateFlow<Map<String, DirectUploadJob>>(emptyMap())
    private var loaded = false

    /** Guarda cache e disco juntos (nunca um sem o outro). */
    private val stateMutex = Mutex()

    /** Uma drenagem por vez. Reentrante: [withDrainPaused] pode chamar a limpeza por dentro. */
    private val drainMutex = ReentrantSyncLock()

    /** > 0 enquanto alguém segura a fila ([withDrainPaused]) — a drenagem em curso para no próximo passo. */
    private val holds = MutableStateFlow(0)

    private val _events = MutableSharedFlow<DirectUploadEvent>(extraBufferCapacity = 32)

    /** Concluídos e recusados, para a tela reagir (a lista em si é [jobs]). */
    val events: SharedFlow<DirectUploadEvent> = _events.asSharedFlow()

    /** Todos os envios no aparelho (de todas as contas), na ordem de drenagem. */
    val jobs: StateFlow<List<DirectUploadJob>> get() = _jobs.asStateFlow()
    private val _jobs = MutableStateFlow<List<DirectUploadJob>>(emptyList())

    /** Os envios da conta [accountId] (vazio para `null`), na ordem de drenagem. */
    fun observeJobs(accountId: String?): Flow<List<DirectUploadJob>> =
        _jobs.map { lista -> if (accountId == null) emptyList() else lista.filter { it.accountId == accountId } }
            .distinctUntilChanged()

    /** Um envio (`null` = concluído, descartado ou inexistente). */
    fun observeJob(jobId: String): Flow<DirectUploadJob?> = _jobs.map { l -> l.firstOrNull { it.id == jobId } }.distinctUntilChanged()

    /** Os envios de um recurso do app (ex.: o pedido de vídeo [targetId]). */
    fun observeTarget(kind: String, targetId: String): Flow<List<DirectUploadJob>> =
        _jobs.map { l -> l.filter { it.kind == kind && it.targetId == targetId } }.distinctUntilChanged()

    // -- Carga ------------------------------------------------------------------

    private suspend fun ensureLoaded() {
        if (loaded) return
        stateMutex.withLock {
            if (loaded) return
            val lidos = storage.loadAll().associateBy { it.id }
            publish(lidos)
            loaded = true
        }
    }

    private fun publish(mapa: Map<String, DirectUploadJob>) {
        cache.value = mapa
        _jobs.value = directUploadDrainOrder(mapa.values)
    }

    /**
     * Grava [job] (cache + disco) **se ele ainda está na fila**. Descartado/purgado no meio de uma
     * parte em voo, a drenagem não o ressuscita. Devolve o estado efetivo, ou `null` se saiu.
     */
    private suspend fun persist(job: DirectUploadJob): DirectUploadJob? = stateMutex.withLock {
        if (job.id !in cache.value) return@withLock null
        if (!storage.save(job)) AppLogger.w(TAG, "estado do envio ${job.id} não gravou (disco?)")
        publish(cache.value + (job.id to job))
        job
    }

    private fun current(jobId: String): DirectUploadJob? = cache.value[jobId]

    // -- Enfileirar ---------------------------------------------------------------

    /**
     * Enfileira um arquivo pronto. Com [DirectUploadRequest.moveSource] (default), o arquivo **sai**
     * da pasta temporária e passa a ser da fila — não chame `deleteFile()` do `PreparedVideo` depois
     * de um `Queued`; chame se vier `Rejected` (o original ficou com você).
     */
    suspend fun enqueue(request: DirectUploadRequest): DirectUploadEnqueueResult {
        ensureLoaded()
        val conta = accountId()?.takeIf { it.isNotBlank() }
            ?: return DirectUploadEnqueueResult.Rejected(DirectUploadRejectReason.NO_ACCOUNT)
        if (request.kind.isBlank() || request.contentType.isBlank()) {
            return DirectUploadEnqueueResult.Rejected(DirectUploadRejectReason.INVALID_REQUEST)
        }
        val id = idFactory()
        if (!isValidDirectUploadId(id)) return DirectUploadEnqueueResult.Rejected(DirectUploadRejectReason.INVALID_REQUEST)
        val job = stateMutex.withLock {
            if (id in cache.value) return DirectUploadEnqueueResult.Rejected(DirectUploadRejectReason.INVALID_REQUEST)
            val tamanho = storage.importMedia(id, request.sourcePath, request.moveSource)
            if (tamanho == null) {
                storage.delete(id)
                return if (request.sourcePath.isBlank()) {
                    DirectUploadEnqueueResult.Rejected(DirectUploadRejectReason.SOURCE_MISSING)
                } else {
                    DirectUploadEnqueueResult.Rejected(sourceOrStorage(request))
                }
            }
            val novo = DirectUploadJob(
                id = id,
                accountId = conta,
                kind = request.kind,
                targetId = request.targetId,
                contentType = request.contentType,
                sizeBytes = tamanho,
                durationSeconds = request.durationSeconds,
                fileName = request.fileName.ifBlank { "media" },
                metadata = request.metadata,
                wifiOnly = request.wifiOnly,
                createdAtMillis = nowMillis(),
            )
            // Estado DEPOIS do arquivo: pasta sem job.json é órfã (a varredura recolhe); job.json sem
            // arquivo seria um envio impossível.
            if (!storage.save(novo)) {
                if (request.moveSource) AppLogger.w(TAG, "estado do envio não gravou; arquivo descartado com a pasta")
                storage.delete(id)
                return DirectUploadEnqueueResult.Rejected(DirectUploadRejectReason.STORAGE_FAILED)
            }
            publish(cache.value + (id to novo))
            novo
        }
        scheduler.schedule(this, needOf(listOf(job)), urgent = true)
        return DirectUploadEnqueueResult.Queued(job)
    }

    private suspend fun sourceOrStorage(request: DirectUploadRequest): DirectUploadRejectReason =
        if (storage.sizeOf(request.sourcePath) <= 0L) DirectUploadRejectReason.SOURCE_MISSING
        else DirectUploadRejectReason.STORAGE_FAILED

    // -- Ações da pessoa ----------------------------------------------------------

    /** "Tentar de novo" num envio [DirectUploadStatus.FAILED]: volta à fila, sem recuo. */
    suspend fun retry(jobId: String): Boolean {
        ensureLoaded()
        val job = current(jobId)?.takeIf { it.status == DirectUploadStatus.FAILED } ?: return false
        val volta = job.copy(
            status = if (job.session == null) DirectUploadStatus.QUEUED else DirectUploadStatus.UPLOADING,
            attempts = 0,
            nextAttemptAtMillis = 0L,
            failure = null,
        )
        persist(volta) ?: return false
        scheduler.schedule(this, needOf(listOf(volta)), urgent = true)
        return true
    }

    /** Liga/desliga o "só no Wi-Fi" de um envio. */
    suspend fun setWifiOnly(jobId: String, wifiOnly: Boolean): Boolean {
        ensureLoaded()
        val job = current(jobId) ?: return false
        if (job.wifiOnly == wifiOnly) return true
        val novo = persist(job.copy(wifiOnly = wifiOnly)) ?: return false
        if (wifiOnly) transport.cancelJob(jobId) // parte em voo no 4G para; volta no Wi-Fi
        scheduler.schedule(this, needOf(listOf(novo)), urgent = true)
        return true
    }

    /**
     * Desiste do envio: para a parte em voo, apaga a pasta (arquivo inclusive) e avisa o servidor
     * (`abort`, melhor esforço — o servidor também limpa envio abandonado sozinho).
     */
    suspend fun discard(jobId: String): Boolean {
        ensureLoaded()
        val job = current(jobId) ?: return false
        transport.cancelJob(jobId)
        removeLocal(jobId)
        val sessao = job.session
        if (sessao != null && accountId() == job.accountId) {
            val r = runCatchingNonCancel { backend.abort(job, sessao) }
            if (r !is DomainResult.Success) AppLogger.i(TAG, "abort do envio ${job.id} não confirmado; o servidor expira sozinho")
        }
        return true
    }

    private suspend fun removeLocal(jobId: String): Boolean = stateMutex.withLock {
        val existia = jobId in cache.value
        publish(cache.value - jobId)
        storage.delete(jobId)
        existia
    }

    // -- Conta --------------------------------------------------------------------

    /** Envios ainda no aparelho da conta [accountId] (pendentes e recusados) — para avisar antes do logout. */
    suspend fun pendingCount(accountId: String): Int {
        ensureLoaded()
        return cache.value.values.count { it.accountId == accountId }
    }

    /**
     * Apaga **todos** os envios da conta [accountId] — estado e arquivos —, parando as partes em voo.
     * Passo da fila na exclusão de conta e no logout que descarta pendências. Não chama o servidor
     * (a sessão pode já ter acabado; envio abandonado expira lá).
     *
     * @return quantos envios saíram.
     * @throws IllegalArgumentException conta em branco.
     */
    suspend fun purgeAccount(accountId: String): Int {
        require(accountId.isNotBlank()) { "fila de envio: limpeza sem conta nomeada recusada" }
        ensureLoaded()
        val alvos = cache.value.values.filter { it.accountId == accountId }.map { it.id }
        alvos.forEach { transport.cancelJob(it) }
        var removidos = 0
        alvos.forEach { if (removeLocal(it)) removidos++ }
        return removidos
    }

    /**
     * Roda [block] com a fila **segurada**: a drenagem em curso para no próximo passo (partes em voo
     * são canceladas e retomadas depois) e nenhuma começa até [block] acabar.
     */
    suspend fun <T> withDrainPaused(block: suspend () -> T): T {
        holds.update { it + 1 }
        try {
            cache.value.keys.forEach { transport.cancelJob(it) }
            return drainMutex.withLock { block() }
        } finally {
            if (holds.updateAndGet { it - 1 } == 0) scheduleCurrent(urgent = false)
        }
    }

    // -- Abertura -----------------------------------------------------------------

    /**
     * Chame na abertura do app (e, no iOS, ao voltar ao primeiro plano): carrega a fila, recolhe
     * pastas órfãs e agenda a drenagem do que é da conta logada. Não bloqueia: é só agendar.
     */
    suspend fun resume() {
        ensureLoaded()
        sweepOrphans()
        scheduleCurrent(urgent = false)
    }

    /** Pastas de envio sem estado legível (processo morto no meio do `enqueue`). Devolve quantas saíram. */
    suspend fun sweepOrphans(): Int = stateMutex.withLock {
        val orfas = storage.orphanIds().filter { it !in cache.value }
        orfas.forEach { storage.delete(it) }
        if (orfas.isNotEmpty()) AppLogger.i(TAG, "varredura removeu ${orfas.size} pasta(s) órfã(s)")
        orfas.size
    }

    /**
     * Pede uma drenagem ao agendador (sem esperar). Para quando algo de fora sabe que vale tentar:
     * a rede voltou, o app voltou ao primeiro plano, a sessão de fundo do iOS entregou uma parte.
     */
    fun requestDrain() {
        if (accountId() == null) return
        if (!loaded) {
            scheduler.schedule(this, DirectUploadNetworkNeed.ANY, urgent = false)
            return
        }
        scheduleCurrent(urgent = false)
    }

    private fun scheduleCurrent(urgent: Boolean) {
        val conta = accountId() ?: return
        val ativos = cache.value.values.filter { it.accountId == conta && it.status != DirectUploadStatus.FAILED }
        if (ativos.isEmpty()) return
        if (ativos.any { !it.wifiOnly }) scheduler.schedule(this, DirectUploadNetworkNeed.ANY, urgent)
        if (ativos.any { it.wifiOnly }) scheduler.schedule(this, DirectUploadNetworkNeed.UNMETERED, urgent)
    }

    private fun needOf(jobs: List<DirectUploadJob>): DirectUploadNetworkNeed =
        if (jobs.all { it.wifiOnly }) DirectUploadNetworkNeed.UNMETERED else DirectUploadNetworkNeed.ANY

    // -- Drenagem -----------------------------------------------------------------

    /**
     * Envia o que é da conta logada e está na vez — um envio por vez, mais antigo primeiro, parte a
     * parte. É o que o agendador chama; serve também a teste e a um "enviar agora" manual.
     */
    suspend fun drainNow(): DirectUploadDrainSummary = drainMutex.withLock {
        ensureLoaded()
        // Ninguém logado: nada a fazer (não é pausa — o login chama requestDrain/resume).
        val conta = accountId()?.takeIf { it.isNotBlank() } ?: return@withLock DirectUploadDrainSummary()
        var resumo = DirectUploadDrainSummary()
        val vistos = mutableSetOf<String>()
        while (true) {
            if (holds.value > 0) return@withLock resumo.copy(paused = true)
            val agora = nowMillis()
            val job = directUploadDrainOrder(cache.value.values)
                .firstOrNull { it.id !in vistos && it.accountId == conta && it.status != DirectUploadStatus.FAILED }
                ?: break
            vistos += job.id
            if (job.nextAttemptAtMillis > agora) {
                resumo = resumo.adiado(job.nextAttemptAtMillis)
                continue
            }
            if (job.wifiOnly && isMetered() == true) {
                resumo = resumo.copy(waitingUnmetered = resumo.waitingUnmetered + 1)
                continue
            }
            val desfecho = processar(job, conta)
            resumo = resumo.copy(partsUploaded = resumo.partsUploaded + desfecho.parts)
            when (desfecho) {
                is Outcome.Done -> resumo = resumo.copy(completed = resumo.completed + 1)
                is Outcome.Failed -> resumo = resumo.copy(failed = resumo.failed + 1)
                is Outcome.Deferred -> resumo = resumo.adiado(desfecho.until)
                is Outcome.Gone -> Unit
                is Outcome.Paused -> return@withLock resumo.copy(paused = true)
            }
        }
        resumo
    }

    private fun DirectUploadDrainSummary.adiado(until: Long): DirectUploadDrainSummary =
        copy(deferred = deferred + 1, nextAttemptAtMillis = minOf(nextAttemptAtMillis ?: until, until))

    private sealed interface Outcome {
        val parts: Int

        data class Done(override val parts: Int) : Outcome
        data class Failed(override val parts: Int) : Outcome
        data class Deferred(override val parts: Int, val until: Long) : Outcome
        data class Paused(override val parts: Int) : Outcome
        data class Gone(override val parts: Int) : Outcome
    }

    /** Leva um envio o mais longe que der nesta passada. */
    private suspend fun processar(inicial: DirectUploadJob, conta: String): Outcome {
        var job = inicial
        var partes = 0

        fun parou(): Boolean = holds.value > 0 || accountId() != conta

        if (!storage.hasMedia(job.id, job.sizeBytes)) return falhar(job, MISSING_FILE, partes)

        // 1. Sessão.
        if (job.session == null) {
            if (parou()) return Outcome.Paused(partes)
            when (val r = runCatchingNonCancel { backend.start(job) }) {
                is DomainResult.Success -> {
                    val sessao = r.data
                    val problema = directUploadSessionProblem(sessao, job.sizeBytes)
                    if (problema != null) {
                        AppLogger.e(TAG, "sessão de envio incompatível com o arquivo: $problema")
                        runCatchingNonCancel { backend.abort(job, sessao) }
                        return falhar(job, DirectUploadFailure(INVALID_SESSION_CODE, message = INVALID_SESSION_MESSAGE), partes)
                    }
                    job = persist(
                        job.copy(session = sessao, status = DirectUploadStatus.UPLOADING, completedParts = emptyMap(), attempts = 0, failure = null),
                    ) ?: return Outcome.Gone(partes)
                }
                else -> return erroDoServidor(job, r, partes)
            }
        }

        // 2. Partes.
        var reassinouPorVencimento = false
        while (true) {
            val faltam = directUploadMissingParts(job)
            if (faltam.isEmpty()) break
            if (parou()) return Outcome.Paused(partes)
            val sessao = job.session ?: return Outcome.Gone(partes)
            val parte = faltam.first()
            var url = directUploadUsableUrl(sessao, parte, nowMillis())
            if (url == null) {
                val pedir = faltam.filter { directUploadUsableUrl(sessao, it, nowMillis()) == null }.take(PRESIGN_BATCH)
                when (val r = runCatchingNonCancel { backend.presignParts(job, sessao, pedir) }) {
                    is DomainResult.Success -> {
                        val novas = sessao.copy(partUrls = sessao.partUrls + r.data.urls, urlsExpireAtMillis = r.data.expiresAtMillis)
                        job = persist(job.copy(session = novas)) ?: return Outcome.Gone(partes)
                        url = directUploadUsableUrl(novas, parte, nowMillis())
                            ?: return recuar(job, DirectUploadFailure(MISSING_URL_CODE, message = MISSING_URL_MESSAGE), partes)
                    }
                    else -> return erroDoServidor(job, r, partes)
                }
            }
            val faixa = directUploadPartRange(parte, sessao.partSizeBytes, job.sizeBytes)
                ?: return falhar(job, DirectUploadFailure(INVALID_SESSION_CODE, message = INVALID_SESSION_MESSAGE), partes)
            val pedido = DirectUploadPartRequest(
                jobId = job.id,
                partNumber = parte,
                url = url,
                filePath = storage.mediaPath(job.id),
                offset = faixa.first,
                length = faixa.last - faixa.first + 1,
                partFilePath = storage.partPath(job.id, parte),
                wifiOnly = job.wifiOnly,
            )
            val resultado = try {
                transport.upload(pedido)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.w(TAG, "transporte lançou na parte $parte: ${e::class.simpleName}")
                DirectUploadPartResult.Offline
            }
            val atual = current(job.id) ?: return Outcome.Gone(partes)
            job = atual
            when (resultado) {
                is DirectUploadPartResult.Uploaded -> {
                    job = persist(job.copy(completedParts = job.completedParts + (parte to resultado.etag), attempts = 0, failure = null))
                        ?: return Outcome.Gone(partes + 1)
                    partes++
                    reassinouPorVencimento = false
                }
                DirectUploadPartResult.Expired -> {
                    if (reassinouPorVencimento) {
                        return recuar(job, DirectUploadFailure(403, message = EXPIRED_MESSAGE), partes)
                    }
                    reassinouPorVencimento = true
                    val s = job.session ?: return Outcome.Gone(partes)
                    job = persist(job.copy(session = s.copy(partUrls = s.partUrls - parte))) ?: return Outcome.Gone(partes)
                }
                DirectUploadPartResult.UploadGone -> {
                    // O storage esqueceu o envio (abortado/expirado no servidor): recomeça do zero, com recuo
                    // para não girar em falso se o servidor insistir.
                    job = persist(job.copy(session = null, completedParts = emptyMap(), status = DirectUploadStatus.QUEUED))
                        ?: return Outcome.Gone(partes)
                    return recuar(job, DirectUploadFailure(404, message = GONE_MESSAGE), partes)
                }
                DirectUploadPartResult.Offline, DirectUploadPartResult.Cancelled -> return Outcome.Paused(partes)
                DirectUploadPartResult.SourceUnreadable -> return falhar(job, MISSING_FILE, partes)
                is DirectUploadPartResult.Failed ->
                    return if (isRetryableDirectUploadPartStatus(resultado.code)) {
                        recuar(job, DirectUploadFailure(resultado.code, message = STORAGE_MESSAGE), partes)
                    } else {
                        falhar(job, DirectUploadFailure(resultado.code, message = STORAGE_MESSAGE), partes)
                    }
            }
        }

        // 3. Fechar.
        if (parou()) return Outcome.Paused(partes)
        val sessao = job.session ?: return Outcome.Gone(partes)
        if (job.status != DirectUploadStatus.COMPLETING) {
            job = persist(job.copy(status = DirectUploadStatus.COMPLETING)) ?: return Outcome.Gone(partes)
        }
        return when (val r = runCatchingNonCancel { backend.complete(job, sessao, directUploadCompletedParts(job)) }) {
            is DomainResult.Success -> {
                concluir(job, r.data)
                Outcome.Done(partes)
            }
            else -> erroDoServidor(job, r, partes)
        }
    }

    private suspend fun concluir(job: DirectUploadJob, corpo: String) {
        removeLocal(job.id)
        _events.tryEmit(DirectUploadEvent.Completed(job.id, job.kind, job.targetId, corpo))
        onCompleted?.let { cb ->
            try {
                cb(job, corpo)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "onCompleted lançou após um envio aceito (o envio NÃO é refeito)", e)
            }
        }
    }

    private suspend fun erroDoServidor(job: DirectUploadJob, r: DomainResult<*>, partes: Int): Outcome = when (r) {
        is DomainResult.Quota -> falhar(job, DirectUploadFailure(402, message = QUOTA_MESSAGE, isQuota = true), partes)
        is DomainResult.Error -> when (classifyRestFailure(r.code)) {
            RestFailureClass.Offline -> Outcome.Paused(partes)
            RestFailureClass.Retryable -> recuar(job, DirectUploadFailure(r.code, r.serverCode, r.userMessage), partes)
            RestFailureClass.Terminal, RestFailureClass.Quota ->
                falhar(job, DirectUploadFailure(r.code, r.serverCode, r.userMessage, isQuota = r.code == 402), partes)
        }
        is DomainResult.Success -> Outcome.Paused(partes)
    }

    private suspend fun recuar(job: DirectUploadJob, falha: DirectUploadFailure, partes: Int): Outcome {
        val tentativas = job.attempts + 1
        if (retry.isExhausted(tentativas)) {
            return falhar(job, falha.copy(code = RETRY_EXHAUSTED_CODE, message = falha.message ?: RETRY_EXHAUSTED_MESSAGE), partes)
        }
        val ate = nowMillis() + uploadRetryDelayMillis(tentativas, retry)
        persist(job.copy(attempts = tentativas, nextAttemptAtMillis = ate, failure = falha)) ?: return Outcome.Gone(partes)
        return Outcome.Deferred(partes, ate)
    }

    private suspend fun falhar(job: DirectUploadJob, falha: DirectUploadFailure, partes: Int): Outcome {
        persist(job.copy(status = DirectUploadStatus.FAILED, failure = falha)) ?: return Outcome.Gone(partes)
        _events.tryEmit(DirectUploadEvent.Failed(job.id, falha))
        return Outcome.Failed(partes)
    }

    private suspend inline fun <T> runCatchingNonCancel(block: () -> DomainResult<T>): DomainResult<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.w(TAG, "chamada ao servidor lançou: ${e::class.simpleName}")
        DomainResult.Error(DomainResult.OFFLINE_CODE, "Falha de transporte.")
    }

    companion object {
        private const val TAG = "DirectUploadOutbox"

        /** Quantas URLs pedir por `presignParts`. */
        const val PRESIGN_BATCH: Int = 50

        /** Sentinela: o arquivo da fila sumiu ou mudou de tamanho. Não há o que enviar. */
        const val MISSING_FILE_CODE: Int = -10

        /** Sentinela: a sessão do servidor não fecha com o arquivo (partes × tamanho). */
        const val INVALID_SESSION_CODE: Int = -11

        /** Sentinela: o servidor não devolveu a URL da parte pedida. */
        const val MISSING_URL_CODE: Int = -12

        /** Sentinela: acabaram as tentativas da [UploadRetryPolicy]. */
        const val RETRY_EXHAUSTED_CODE: Int = -13

        private const val MISSING_FILE_MESSAGE = "O arquivo não está mais no aparelho."
        private const val INVALID_SESSION_MESSAGE = "O servidor abriu um envio que não corresponde ao arquivo."
        private const val MISSING_URL_MESSAGE = "O servidor não liberou o envio desta parte."
        private const val EXPIRED_MESSAGE = "A autorização de envio venceu."
        private const val GONE_MESSAGE = "O envio expirou no servidor e será refeito."
        private const val STORAGE_MESSAGE = "O armazenamento recusou uma parte do arquivo."
        private const val QUOTA_MESSAGE = "Limite do plano atingido."
        private const val RETRY_EXHAUSTED_MESSAGE = "Não foi possível enviar o arquivo."

        private val MISSING_FILE = DirectUploadFailure(MISSING_FILE_CODE, message = MISSING_FILE_MESSAGE)
    }
}

/** Pasta da fila [name]: a default para [DEFAULT_DIRECT_UPLOAD_OUTBOX_NAME], com sufixo para as demais. */
fun directUploadDirectoryFor(name: String): String =
    if (name == DEFAULT_DIRECT_UPLOAD_OUTBOX_NAME) DEFAULT_DIRECT_UPLOAD_DIRECTORY else "${DEFAULT_DIRECT_UPLOAD_DIRECTORY}_$name"

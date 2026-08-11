package br.com.codecacto.kmplib.sync.rest

import br.com.codecacto.kmplib.core.storage.BlobStore
import br.com.codecacto.kmplib.core.storage.createBlobStore
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.sync.SyncOpType
import br.com.codecacto.kmplib.sync.SyncStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

/** Diretório default dos binários da fila de upload (exclusivo dela — ver [RestUploadOutbox.sweepOrphanBlobs]). */
const val DEFAULT_UPLOAD_BLOB_DIRECTORY: String = "kmplib_uploads"

/** Nome default da entidade da fila de upload no espelho `synced_entity`. */
const val DEFAULT_UPLOAD_ENTITY: String = "kmplib_upload"

/**
 * **Fila de upload DURÁVEL, participante do ciclo de sync** (2.104.0 — GAP-AC-M-PHOTOOUTBOX-01).
 *
 * Substitui a [RestUploadQueue] (e resolve o mesmo defeito da
 * [UploadQueue][br.com.codecacto.kmplib.firebase.storage.UploadQueue]): as duas guardavam a fila num
 * `MutableStateFlow` e os bytes num `mutableMapOf`, isto é, **em memória pura**. O usuário que
 * cadastrava um item com foto **sem sinal** e fechava o app perdia a foto **em silêncio** — o
 * registro subia depois, sem ela. Num app de coleção (Acervo, `moedas`: anverso e reverso
 * obrigatórios, uso típico numa feira sem sinal) isso é um item sem valor; em qualquer app com
 * anexo offline, é perda de dado do usuário.
 *
 * ### Como isto NÃO é uma segunda fila ao lado da que já existe
 * A persistência é o **mesmo** espelho `synced_entity` do offline-first, sob um nome de entidade
 * próprio ([DEFAULT_UPLOAD_ENTITY]), pelo **mesmo** [RestEntityMirror]. Consequência prática: a fila
 * de fotos herda, sem código novo, o **escopo de conta** (2.91.0 — a foto de um usuário não sobe na
 * conta de outro no aparelho compartilhado), o **histórico de entrega** (2.94.0 — uma recusa não é
 * apagada pelo toque seguinte), a distinção **drenável × recusada** (2.91.0) e o vocabulário de
 * estado [RestRowState] que a UI já sabe ler. **Sem migração de schema:** nada foi acrescentado à
 * tabela.
 *
 * O que **não** cabia no espelho — os bytes — vai para o disco, no
 * [BlobStore][br.com.codecacto.kmplib.core.storage.BlobStore] (armazenamento interno privado no
 * Android, `Application Support` no iOS; nunca cache purgável).
 *
 * ### A foto só sobe depois que o dono existe no servidor (a lição do ADR-0006)
 * Um item criado offline nasce com id local e ganha o id do servidor quando sincroniza. Enviar a foto
 * antes disso significa montar `POST /v1/items/local-…/photos` — o backend recusa por
 * `FOREIGN KEY`/UUID, a recusa é **terminal**, e a foto se perde para sempre. Por isso o upload
 * declara o seu dono ([PendingUpload.ownerEntity]/[PendingUpload.ownerHandle]) e o caminho usa o
 * marcador [UPLOAD_OWNER_PLACEHOLDER]; a cada ciclo a lib resolve o id do servidor pelo **remap
 * durável** ([SyncStore.resolveServerId]) e pelo espelho do dono. Enquanto ele não migrou, o upload
 * **espera** ([UploadTarget.WaitingOwner]) — não conta tentativa, não vira erro na tela.
 *
 * ### Uso
 * ```kotlin
 * // Koin
 * single { RestUploadOutbox(api = get(), store = get()) }
 * single {
 *     RestCrudSyncEngine(
 *         // a fila de fotos entra DEPOIS do repositório dono: no mesmo ciclo em que o item é
 *         // aceito, o remap já está disponível e a foto sobe em seguida.
 *         participants = listOf(get<ItemRepository>().rest, get<RestUploadOutbox>()),
 *         connectivity = get(),
 *         store = get(),
 *     )
 * }
 *
 * // Ao salvar o item (com ou sem rede):
 * val item = repo.create(novoItem).getOrNull() ?: return
 * outbox.enqueue(
 *     bytes = jpegAnverso,
 *     fileName = "anverso.jpg",
 *     mimeType = "image/jpeg",
 *     path = "/v1/items/{owner}/photos",
 *     ownerEntity = ItemCrud.name,
 *     ownerHandle = item.id,      // handle: pode ser o id local
 *     label = "anverso",
 * )
 * ```
 *
 * @param api cliente REST de domínio (Bearer + 401-refresh + 402→quota).
 * @param store espelho local — **o mesmo** dos repositórios de domínio.
 * @param blobs onde os binários ficam. O default cria um diretório **exclusivo** desta fila; não
 *   compartilhe a instância com outros usos, porque [sweepOrphanBlobs] apaga o que não estiver na
 *   fila.
 * @param entityName nome da entidade no espelho (troque se o app precisar de mais de uma fila).
 * @param retry política de recuo entre tentativas.
 * @param json Json do payload (o mesmo tolerante do espelho).
 * @param nowMillis relógio (injetável para teste).
 * @param idFactory gerador do id do upload.
 * @param onUploaded chamado após cada envio aceito, com o corpo bruto da resposta — é onde o app
 *   decodifica a foto criada e a grava no seu próprio espelho. Exceção aqui **não** desfaz o upload
 *   (que o servidor já aceitou): é registrada em log.
 */
class RestUploadOutbox(
    private val api: DomainApiClient,
    private val store: SyncStore,
    private val blobs: BlobStore = createBlobStore(DEFAULT_UPLOAD_BLOB_DIRECTORY),
    private val entityName: String = DEFAULT_UPLOAD_ENTITY,
    private val retry: UploadRetryPolicy = UploadRetryPolicy(),
    private val json: Json = restMirrorJson,
    private val nowMillis: () -> Long = ::currentTimeMillis,
    private val idFactory: () -> String = ::newRestClientId,
    private val onUploaded: (suspend (upload: PendingUpload, responseBody: String) -> Unit)? = null,
) : RestCrudSyncParticipant {

    /** Espelho da fila — a MESMA peça (e a mesma tabela) do offline-first de domínio. */
    private val mirror: RestEntityMirror<PendingUpload> = RestEntityMirror(
        name = entityName,
        store = store,
        serializer = PendingUpload.serializer(),
        idOf = PendingUpload::id,
        json = json,
    )

    private val ids: RestIdResolver = RestIdResolver(store)

    /** Serializa gravação de blob e varredura de órfãos (uma não pode ver a outra pela metade). */
    private val writeMutex = Mutex()

    /** Serializa a drenagem (nunca dois envios concorrentes da mesma fila). */
    private val drainMutex = Mutex()

    // -- Leitura / estado para a UI ----------------------------------------

    /**
     * A fila inteira, com o estado de cada item ([RestRowState]) — pendente, recusado pelo servidor,
     * com histórico de recusa. Ordenada por chegada (FIFO), a mesma ordem em que será drenada.
     */
    fun observeAll(): Flow<List<RestRow<PendingUpload>>> =
        mirror.observeVisibleWithState().map { linhas -> linhas.sortedBy { it.model.createdAtMillis } }

    /**
     * Quantos binários **ainda não subiram** e seguem na fila (exclui os recusados, que a UI mostra
     * como erro). É o número da frase "3 fotos aguardando envio".
     */
    fun observePendingCount(): Flow<Int> =
        observeAll().map { linhas -> linhas.count { !it.isFailed } }.distinctUntilChanged()

    /**
     * Uploads de **um registro**, correlacionados por **conjunto de handles** — nunca por igualdade
     * de id.
     *
     * A regra é a mesma de [OfflineFirstRestRepository.observeChildren]: o dono pode ter migrado de
     * id depois de a foto ter sido enfileirada, e comparar `ownerHandle == id` derrubaria justamente
     * as fotos do registro recém-sincronizado. A resolução roda fora da thread do coletor.
     */
    fun observeForOwner(handle: String): Flow<List<RestRow<PendingUpload>>> =
        observeAll()
            .map { linhas ->
                val handles = ids.handlesOf(handle)
                linhas.filter { it.model.ownerHandle in handles }
            }
            .flowOn(ids.dispatcher)

    /** Snapshot síncrono da fila (FIFO). */
    fun pending(): List<RestRow<PendingUpload>> =
        mirror.getVisibleWithState().sortedBy { it.model.createdAtMillis }

    /** Uploads que o servidor **recusou** (4xx/402/tentativas esgotadas), com o erro preservado. */
    fun failed(): List<RestRow<PendingUpload>> = mirror.failedRows()

    /** Estado de um upload. */
    fun stateOf(uploadId: String): RestRowState = mirror.stateOf(uploadId)

    /** Espaço ocupado pelos binários ainda não enviados (diagnóstico). */
    suspend fun storedBytes(): Long = blobs.totalBytes()

    // -- Enfileirar --------------------------------------------------------

    /**
     * Enfileira **um** binário. Grava os bytes no disco e a linha na outbox — depois disso, matar o
     * processo não perde nada.
     *
     * @param path caminho do endpoint; use [UPLOAD_OWNER_PLACEHOLDER] quando ele depender do id do
     *   dono (`"/v1/items/{owner}/photos"`).
     * @param ownerEntity nome da entidade dona no espelho (`RestCrudEntity.name`). **Informe-o
     *   sempre que o dono for um registro sincronizável** — é o que garante que a foto não suba
     *   antes de o dono existir no servidor. Em branco, [ownerHandle] é usado como id final, sem
     *   espera (para dono que a lib não espelha).
     * @param ownerHandle id do dono como o app o conhece (local ou do servidor).
     * @param label rótulo semântico ("anverso"/"reverso") — o que a UI mostra.
     */
    suspend fun enqueue(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
        path: String,
        ownerEntity: String = "",
        ownerHandle: String = "",
        label: String = "",
        fieldName: String = DEFAULT_UPLOAD_FIELD_NAME,
        method: UploadMethod = UploadMethod.POST,
        id: String = idFactory(),
    ): UploadEnqueueResult = enqueueParts(
        parts = listOf(UploadContent(bytes, fileName, mimeType, fieldName)),
        path = path,
        ownerEntity = ownerEntity,
        ownerHandle = ownerHandle,
        label = label,
        method = method,
        id = id,
    )

    /**
     * Enfileira um upload de **várias partes nomeadas** num único request multipart (ex.: foto-prova
     * `full` + `thumb`). Ou todas as partes são gravadas, ou nada entra na fila.
     */
    suspend fun enqueueParts(
        parts: List<UploadContent>,
        path: String,
        ownerEntity: String = "",
        ownerHandle: String = "",
        label: String = "",
        method: UploadMethod = UploadMethod.POST,
        id: String = idFactory(),
    ): UploadEnqueueResult = writeMutex.withLock {
        val invalido = validate(parts, path, ownerHandle, id)
        if (invalido != null) return@withLock invalido

        val gravadas = mutableListOf<PendingUploadPart>()
        parts.forEachIndexed { index, content ->
            val blobId = uploadBlobId(id, index)
            if (!blobs.write(blobId, content.bytes)) {
                // Atômico: uma parte que não coube no disco invalida o upload inteiro — meio
                // multipart enviado seria pior que nenhum.
                gravadas.forEach { blobs.delete(it.blobId) }
                blobs.delete(blobId)
                return@withLock UploadEnqueueResult.Rejected(
                    UploadRejectReason.StorageFailed,
                    "Não foi possível guardar o arquivo no dispositivo.",
                )
            }
            gravadas += PendingUploadPart(
                blobId = blobId,
                fileName = content.fileName,
                mimeType = content.mimeType,
                fieldName = content.fieldName,
                sizeBytes = content.bytes.size.toLong(),
            )
        }

        val upload = PendingUpload(
            id = id,
            path = path,
            parts = gravadas,
            ownerEntity = ownerEntity.trim(),
            ownerHandle = ownerHandle.trim(),
            label = label,
            createdAtMillis = nowMillis(),
            nextAttemptAtMillis = 0L,
            method = method,
        )
        // Grava a linha DEPOIS dos bytes: uma linha sem arquivo seria uma promessa que o disco não
        // pode cumprir. O contrário (arquivo sem linha) é apenas um órfão, que a varredura recolhe.
        mirror.putDirty(upload, SyncOpType.CREATE)
        UploadEnqueueResult.Queued(upload)
    }

    private fun validate(
        parts: List<UploadContent>,
        path: String,
        ownerHandle: String,
        id: String,
    ): UploadEnqueueResult.Rejected? = when {
        parts.isEmpty() || parts.any { it.bytes.isEmpty() } ->
            reject("Conteúdo vazio: não há o que enviar.")
        !isValidUploadId(id) ->
            reject("Id de upload inválido (precisa servir de nome de arquivo).")
        path.isBlank() ->
            reject("Caminho do endpoint em branco.")
        uploadPathRequiresOwner(path) && ownerHandle.isBlank() ->
            reject("O caminho exige o id do dono ($UPLOAD_OWNER_PLACEHOLDER), mas nenhum handle foi informado.")
        mirror.rowFor(id) != null ->
            reject("Já existe um upload na fila com este id.")
        else -> null
    }

    private fun reject(message: String) =
        UploadEnqueueResult.Rejected(UploadRejectReason.InvalidRequest, message)

    // -- Resolver / descartar ----------------------------------------------

    /**
     * Retry **explícito** de um upload recusado: volta à fila drenável e **zera o recuo** (o usuário
     * pediu agora). Devolve `false` se não havia upload recusado com esse id.
     */
    suspend fun requeue(uploadId: String): Boolean {
        val linha = mirror.rowFor(uploadId) ?: return false
        if (linha.failed == 0L) return false
        mirror.clearFailure(uploadId)
        mirror.decode(linha.payload_json)?.let { mirror.patch(it.copy(nextAttemptAtMillis = 0L)) }
        return true
    }

    /** Retry explícito de **todos** os recusados. Devolve quantos voltaram à fila. */
    suspend fun requeueAll(): Int = failed().count { requeue(it.model.id) }

    /**
     * Descarta um upload (o usuário desistiu): apaga a linha **e** o binário. Vale tanto para
     * recusado quanto para pendente — é a única forma de a foto sair do disco sem subir.
     */
    suspend fun discard(uploadId: String): Boolean = writeMutex.withLock {
        val linha = mirror.rowFor(uploadId) ?: return@withLock false
        val upload = mirror.decode(linha.payload_json)
        mirror.removeHard(uploadId)
        if (upload != null) {
            upload.parts.forEach { blobs.delete(it.blobId) }
        } else {
            // Payload ilegível: os blobs derivam do id da linha, então ainda dá para limpá-los.
            blobs.deleteByPrefix(linha.local_id)
        }
        true
    }

    /** Descarta **toda** a fila da conta corrente (ex.: wipe de logout). Devolve quantos saíram. */
    suspend fun discardAll(): Int = pending().count { discard(it.model.id) }

    // -- Ciclo de sync (RestCrudSyncParticipant) ---------------------------

    /**
     * Drena a fila. Registre este participante **depois** do repositório dono: o [parentRemap] traz o
     * `clientId → serverId` dos registros aceitos neste mesmo ciclo, então a foto de um item criado
     * offline sobe **logo após** o item, sem esperar o ciclo seguinte.
     *
     * Uploads não geram ids novos para outros participantes — devolve sempre um mapa vazio.
     */
    override suspend fun drainOutbox(parentRemap: Map<String, String>): Map<String, String> {
        drainNow(parentRemap)
        return emptyMap()
    }

    /** Uploads não têm o que reconciliar por GET: a verdade da fila é local. */
    override suspend fun refresh(): Boolean = true

    /**
     * Envia o que estiver pronto **agora** (também serve ao botão "tentar enviar" da UI).
     *
     * Ordem FIFO, um por vez. Cada item pode: subir; **esperar** o dono migrar de id; ser adiado pelo
     * recuo; falhar de forma retentável (fica na fila, com recuo); ou ser recusado (fica visível, com
     * o binário preservado). Perder a rede **pausa** a fila inteira — não adianta insistir, e a
     * outbox fica intacta.
     */
    suspend fun drainNow(parentRemap: Map<String, String> = emptyMap()): UploadDrainSummary =
        drainMutex.withLock { executarDrenagem(parentRemap) }

    private suspend fun executarDrenagem(parentRemap: Map<String, String>): UploadDrainSummary {
        val titular = store.accountScope.value
        val agora = nowMillis()
        var enviados = 0
        var aguardando = 0
        var adiados = 0
        var recusados = 0

        val fila = mirror.drainableRows()
            .mapNotNull { linha -> mirror.decode(linha.payload_json)?.let { linha to it } }
            .sortedBy { (_, upload) -> upload.createdAtMillis }

        for ((linha, upload) in fila) {
            // O titular pode ter mudado (login/logout): a fila restante é de quem saiu e o Bearer já
            // é de quem entrou — enviar agora colocaria a foto na conta errada.
            if (store.accountScope.value != titular) {
                AppLogger.w(TAG, "Fila de upload interrompida: o titular do espelho mudou.")
                return UploadDrainSummary(enviados, aguardando, adiados, recusados, paused = true)
            }
            if (upload.nextAttemptAtMillis > agora) {
                adiados++
                continue
            }
            when (val alvo = resolveTarget(upload, parentRemap)) {
                is UploadTarget.WaitingOwner -> aguardando++
                is UploadTarget.MissingOwner -> {
                    mirror.markFailed(upload.id, MISSING_OWNER_CODE, MISSING_OWNER_MESSAGE)
                    recusados++
                }
                is UploadTarget.Ready -> {
                    val parts = lerPartes(upload)
                    if (parts == null) {
                        mirror.markFailed(upload.id, MISSING_BLOB_CODE, MISSING_BLOB_MESSAGE)
                        recusados++
                        continue
                    }
                    val resposta = when (upload.method) {
                        UploadMethod.POST -> api.postMultipartParts(alvo.path, parts)
                        UploadMethod.PUT -> api.putMultipartParts(alvo.path, parts)
                    }
                    if (store.accountScope.value != titular) {
                        AppLogger.w(TAG, "Resposta de upload descartada: o titular do espelho mudou.")
                        return UploadDrainSummary(enviados, aguardando, adiados, recusados, paused = true)
                    }
                    when (resposta) {
                        is DomainResult.Success -> {
                            concluir(upload, resposta.data)
                            enviados++
                        }
                        is DomainResult.Quota -> {
                            mirror.markFailed(upload.id, 402, QUOTA_MESSAGE)
                            recusados++
                        }
                        is DomainResult.Error -> when (classifyRestFailure(resposta.code)) {
                            RestFailureClass.Offline -> {
                                // Sem rede: o resto da fila também não vai. Preserva tudo e para.
                                return UploadDrainSummary(enviados, aguardando, adiados, recusados, paused = true)
                            }
                            RestFailureClass.Retryable -> {
                                val virouRecusa = agendarNovaTentativa(linha.attempts.toInt(), upload, resposta)
                                if (virouRecusa) recusados++
                            }
                            RestFailureClass.Terminal, RestFailureClass.Quota -> {
                                mirror.markFailed(upload.id, resposta.code, resposta.message)
                                recusados++
                            }
                        }
                    }
                }
            }
        }
        return UploadDrainSummary(enviados, aguardando, adiados, recusados, paused = false)
    }

    /**
     * Falha **retentável**: conta a tentativa e adia a próxima com recuo exponencial. Esgotada a
     * política, vira falha **visível** — retentar para sempre em silêncio esconde do usuário uma foto
     * que nunca vai subir. @return `true` se virou recusa.
     */
    private fun agendarNovaTentativa(
        tentativasAnteriores: Int,
        upload: PendingUpload,
        erro: DomainResult.Error,
    ): Boolean {
        val tentativas = tentativasAnteriores + 1
        mirror.bumpAttempt(upload.id, erro.message)
        if (retry.isExhausted(tentativas)) {
            mirror.markFailed(upload.id, RETRY_EXHAUSTED_CODE, erro.message ?: RETRY_EXHAUSTED_MESSAGE)
            return true
        }
        val proxima = nowMillis() + uploadRetryDelayMillis(tentativas, retry)
        mirror.patch(upload.copy(nextAttemptAtMillis = proxima))
        return false
    }

    /**
     * Upload aceito: **primeiro** some a linha, **depois** o binário. Nessa ordem porque o resíduo de
     * cada falha é diferente — arquivo sem linha é órfão (a varredura recolhe), linha sem arquivo
     * seria um upload impossível marcado como erro para o usuário, de uma foto que já subiu.
     */
    private suspend fun concluir(upload: PendingUpload, corpo: String) {
        mirror.removeHard(upload.id)
        upload.parts.forEach { blobs.delete(it.blobId) }
        onUploaded?.let { callback ->
            runCatching { callback(upload, corpo) }.onFailure {
                AppLogger.e(TAG, "onUploaded lançou após um envio aceito (o upload NÃO é refeito)", it)
            }
        }
    }

    /** Lê os bytes das partes. `null` se algum binário sumiu do disco (não há o que enviar). */
    private suspend fun lerPartes(upload: PendingUpload): List<MultipartPart>? {
        val partes = upload.parts.map { parte ->
            val bytes = blobs.read(parte.blobId) ?: return null
            MultipartPart(
                fieldName = parte.fieldName.ifBlank { DEFAULT_UPLOAD_FIELD_NAME },
                fileName = parte.fileName,
                bytes = bytes,
                mimeType = parte.mimeType,
            )
        }
        return partes.ifEmpty { null }
    }

    /**
     * Para onde o upload vai agora. Consulta, nesta ordem: o remap **deste ciclo**, o espelho do dono
     * e o remap **durável** — que é o que faz a foto ainda achar o item quando o app foi fechado
     * entre o `POST` do item e o da foto.
     */
    private fun resolveTarget(upload: PendingUpload, parentRemap: Map<String, String>): UploadTarget {
        if (!upload.hasOwner) {
            val path = resolveUploadPath(upload.path, upload.ownerHandle)
                ?: return UploadTarget.MissingOwner
            return UploadTarget.Ready(path)
        }
        val handle = upload.ownerHandle
        val doCiclo = parentRemap[handle]
        val serverId = when {
            doCiclo != null -> doCiclo
            else -> {
                val dono = store.getByHandle(upload.ownerEntity, handle)
                when {
                    // O dono existe no espelho mas ainda não subiu: espera. NÃO é falha.
                    dono != null -> dono.server_id ?: return UploadTarget.WaitingOwner
                    // O dono já não está no espelho: só vale se ele migrou de id em algum momento.
                    else -> store.resolveServerId(handle) ?: return UploadTarget.MissingOwner
                }
            }
        }
        return resolveUploadPath(upload.path, serverId)
            ?.let { UploadTarget.Ready(it) }
            ?: UploadTarget.MissingOwner
    }

    // -- Manutenção do disco -----------------------------------------------

    /**
     * Apaga binários **órfãos** — arquivos sem linha correspondente na fila, resíduo de um processo
     * morto entre a gravação do arquivo e a da linha. Chame no **bootstrap**; sem isso o diretório
     * cresce para sempre.
     *
     * ### Duas salvaguardas, porque apagar foto do usuário é irreversível
     * 1. A varredura considera as linhas de **todas as contas** ([SyncStore.getRowsAcrossAccounts]).
     *    Olhar só a conta corrente apagaria as fotos ainda não enviadas de quem trocou de usuário no
     *    aparelho. Store que não sabe responder isso ⇒ **não varre nada** (devolve `0`).
     * 2. Um arquivo é considerado referenciado tanto por constar no payload de uma linha quanto por
     *    **derivar do id** dela ([uploadBlobId]) — assim, um payload ilegível não transforma as
     *    fotos daquela linha em órfãs.
     *
     * O [blobs] precisa ser exclusivo desta fila: o que não for referenciado por ela é apagado.
     *
     * @return quantos arquivos foram removidos.
     */
    suspend fun sweepOrphanBlobs(): Int = writeMutex.withLock {
        val linhas = store.getRowsAcrossAccounts(entityName)
        if (linhas == null) {
            AppLogger.w(TAG, "Varredura de órfãos ignorada: o espelho não lê linhas de todas as contas.")
            return@withLock 0
        }
        val referenciados = buildSet {
            linhas.forEach { linha ->
                mirror.decode(linha.payload_json)?.parts?.forEach { add(it.blobId) }
            }
        }
        val prefixos = linhas.map { "${it.local_id}-" }
        var removidos = 0
        blobs.ids().forEach { blobId ->
            val referenciado = blobId in referenciados || prefixos.any { blobId.startsWith(it) }
            if (!referenciado && blobs.delete(blobId)) removidos++
        }
        if (removidos > 0) AppLogger.i(TAG, "Varredura removeu $removidos binário(s) órfão(s).")
        removidos
    }

    private suspend fun BlobStore.deleteByPrefix(uploadId: String) {
        ids().filter { it.startsWith("$uploadId-") }.forEach { delete(it) }
    }

    companion object {
        private const val TAG = "RestUploadOutbox"

        /**
         * Sentinela: o registro dono não existe mais no espelho e nunca migrou — o upload perdeu o
         * destino. O binário é preservado; quem descarta é o usuário.
         */
        const val MISSING_OWNER_CODE: Int = -5
        const val MISSING_OWNER_MESSAGE: String = "O registro dono deste arquivo não existe mais."

        /** Sentinela: o binário sumiu do disco (apagado por fora). Não há o que enviar. */
        const val MISSING_BLOB_CODE: Int = -6
        const val MISSING_BLOB_MESSAGE: String = "O arquivo não está mais no dispositivo."

        /** Sentinela: acabaram as tentativas da [UploadRetryPolicy] — vira erro visível, com retry. */
        const val RETRY_EXHAUSTED_CODE: Int = -7
        const val RETRY_EXHAUSTED_MESSAGE: String = "Não foi possível enviar o arquivo."

        private const val QUOTA_MESSAGE: String = "Limite do plano atingido."
    }
}

/**
 * Conteúdo a enfileirar — os bytes **antes** de irem para o disco. Vive só até o [RestUploadOutbox]
 * gravá-los; a fila persistida guarda [PendingUploadPart] (metadado + referência ao arquivo).
 */
class UploadContent(
    val bytes: ByteArray,
    val fileName: String,
    val mimeType: String,
    val fieldName: String = DEFAULT_UPLOAD_FIELD_NAME,
)

/**
 * O que aconteceu numa drenagem.
 *
 * @param uploaded aceitos pelo servidor (saíram da fila e do disco).
 * @param waitingOwner esperando o registro dono migrar para o id do servidor — **não é falha**.
 * @param deferred adiados pelo recuo (falharam antes e ainda não é hora).
 * @param rejected recusados, agora visíveis na UI com o binário preservado.
 * @param paused a fila parou no meio (sem rede, ou troca de conta) e será retomada.
 */
data class UploadDrainSummary(
    val uploaded: Int = 0,
    val waitingOwner: Int = 0,
    val deferred: Int = 0,
    val rejected: Int = 0,
    val paused: Boolean = false,
) {
    /** `true` se nada ficou para trás nesta passada. */
    val isDrained: Boolean get() = !paused && waitingOwner == 0 && deferred == 0
}

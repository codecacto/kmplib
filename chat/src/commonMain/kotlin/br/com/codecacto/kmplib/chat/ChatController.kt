package br.com.codecacto.kmplib.chat

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import br.com.codecacto.kmplib.core.util.newUuidV7
import br.com.codecacto.kmplib.media.RecordedAudio
import br.com.codecacto.kmplib.sync.rest.DomainResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Ajustes do [ChatController].
 *
 * @param pollIntervalMillis consulta `after=` com a tela aberta e o app na frente (sem WebSocket).
 * @param maxPollIntervalMillis teto do recuo do polling quando a consulta falha seguidamente.
 * @param receiptRefreshEvery a cada N consultas, relê a página mais recente para atualizar o "lida"
 *   das minhas mensagens (o `after=` só traz mensagem nova, não recibo).
 * @param autoRetryLimit falhas retentáveis (5xx, 429…) antes de a mensagem virar "não enviada" e
 *   esperar a pessoa. Sem rede **não conta** — a mensagem fica "enviando" até a rede voltar.
 */
data class ChatConfig(
    val pageSize: Int = 30,
    val pollIntervalMillis: Long = 5_000L,
    val maxPollIntervalMillis: Long = 60_000L,
    val receiptRefreshEvery: Int = 4,
    val autoRetryLimit: Int = 5,
    val retryBaseMillis: Long = 2_000L,
    val retryMaxMillis: Long = 60_000L,
) {
    init {
        require(pageSize in 1..100) { "pageSize fora de 1..100" }
        require(pollIntervalMillis >= 1_000L) { "pollIntervalMillis abaixo de 1 s" }
        require(receiptRefreshEvery >= 1) { "receiptRefreshEvery >= 1" }
        require(autoRetryLimit >= 1) { "autoRetryLimit >= 1" }
    }
}

/**
 * **Uma conversa 1:1** (2.287.0 — GAP-PT-M12): carrega, rola para trás, envia com **id gerado no
 * cliente** (otimista — a mensagem aparece na hora, "enviando"), reenvia **sem duplicar** (mesmo id),
 * guarda o que não subiu no aparelho ([ChatPendingStore]: sobrevive ao app fechado), conta as não
 * lidas, marca como lida e se atualiza por **consulta periódica `after=`** — sem WebSocket.
 *
 * Um por tela (no ViewModel, com o `viewModelScope`). Chame [start] quando a tela aparece / o app
 * volta à frente e [stop] no `ON_STOP` — o [ChatThread] faz isso sozinho com o ciclo de vida.
 *
 * ```kotlin
 * class ConversaViewModel(api: DomainApiClient, conta: String) : ViewModel() {
 *     val chat = ChatController(
 *         transport = RestChatTransport(api, messagesPath = "/v1/aluno/messages", myRole = "ALUNO"),
 *         conversationKey = "aluno",
 *         accountId = conta,
 *         scope = viewModelScope,
 *     )
 * }
 * ```
 *
 * @param conversationKey identifica a conversa no aparelho (o `alunoId` para o personal; uma
 *   constante para o aluno) — separa as filas de pendências.
 * @param accountId a conta logada. Um controller é de uma conta; trocou de conta, crie outro.
 */
class ChatController(
    private val transport: ChatTransport,
    private val conversationKey: String,
    private val accountId: String,
    private val scope: CoroutineScope,
    private val store: ChatPendingStore = ChatPendingStore(),
    private val config: ChatConfig = ChatConfig(),
    private val nowMillis: () -> Long = ::currentTimeMillis,
    private val idFactory: () -> String = { newUuidV7() },
) {
    init {
        require(accountId.isNotBlank()) { "conversa sem conta" }
    }

    private val mutex = Mutex()
    private var confirmed: List<ChatMessage> = emptyList()
    private var pending: List<ChatOutgoing> = emptyList()
    private var pendingLoaded = false
    private var initialLoaded = false
    private var lastReadSent: String? = null
    private var polls = 0

    private val _state = MutableStateFlow(ChatThreadState())

    /** O que a tela desenha. */
    val state: StateFlow<ChatThreadState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var senderJob: Job? = null

    private fun publicar(alterar: ChatThreadState.() -> ChatThreadState = { this }) {
        _state.value = _state.value.alterar().copy(
            entries = buildChatEntries(confirmed, pending),
            unreadCount = chatUnreadCount(confirmed),
        )
    }

    // -- Ciclo de vida ----------------------------------------------------------------

    /** Começa (ou retoma) a carga e o polling. Idempotente. */
    fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            carregarPendentes()
            if (!initialLoaded) carregarInicial()
            dispararEnvio()
            var falhas = 0
            while (isActive) {
                delay(chatPollDelayMillis(falhas, config.pollIntervalMillis, config.maxPollIntervalMillis))
                falhas = if (consultar()) 0 else falhas + 1
            }
        }
    }

    /** Para o polling (o app foi para o segundo plano). O envio pendente continua. */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun carregarPendentes() {
        if (pendingLoaded) return
        val lidos = store.load(accountId, conversationKey)
        mutex.withLock {
            if (pendingLoaded) return
            // Pendente carregada do disco volta a "enviando" (o recuo da sessão anterior não vale mais).
            pending = (pending + lidos.map { if (it.failure == null) it.copy(nextAttemptAtMillis = 0L) else it }).distinctBy { it.id }
            pendingLoaded = true
            publicar()
        }
    }

    private suspend fun carregarInicial(): Boolean {
        val r = chamar { transport.latest(config.pageSize) }
        return mutex.withLock {
            when (r) {
                is DomainResult.Success -> {
                    confirmed = mergeChatMessages(confirmed, r.data.messages)
                    initialLoaded = true
                    publicar {
                        copy(
                            isLoadingInitial = false,
                            loadError = null,
                            hasMoreOlder = r.data.hasMore,
                            firstUnreadId = firstUnreadId ?: chatFirstUnreadId(confirmed),
                        )
                    }
                    true
                }
                is DomainResult.Error -> {
                    publicar { copy(isLoadingInitial = false, loadError = r.userMessage) }
                    false
                }
                is DomainResult.Quota -> {
                    publicar { copy(isLoadingInitial = false, loadError = "") }
                    false
                }
            }
        }
    }

    /** Uma consulta do polling. `true` se deu certo. */
    private suspend fun consultar(): Boolean {
        if (!initialLoaded) return carregarInicial()
        polls++
        var ok = buscarNovas()
        if (ok && polls % config.receiptRefreshEvery == 0) ok = relerRecentes()
        return ok
    }

    private suspend fun buscarNovas(): Boolean {
        repeat(MAX_AFTER_PAGES) {
            val ultima = mutex.withLock { confirmed.lastOrNull()?.id } ?: return relerRecentes()
            val r = chamar { transport.after(ultima, config.pageSize) }
            if (r !is DomainResult.Success) return false
            mutex.withLock {
                confirmed = mergeChatMessages(confirmed, r.data.messages)
                publicar { copy(loadError = null) }
            }
            if (!r.data.hasMore || r.data.messages.isEmpty()) return true
        }
        return true
    }

    private suspend fun relerRecentes(): Boolean {
        val r = chamar { transport.latest(config.pageSize) }
        if (r !is DomainResult.Success) return false
        mutex.withLock {
            confirmed = mergeChatMessages(confirmed, r.data.messages)
            publicar { copy(loadError = null) }
        }
        return true
    }

    /** Puxar para atualizar: busca as novas e os recibos, com o indicador ligado só pelo gesto. */
    fun refresh() {
        scope.launch {
            mutex.withLock { publicar { copy(isRefreshing = true) } }
            if (!initialLoaded) carregarInicial() else {
                buscarNovas()
                relerRecentes()
            }
            mutex.withLock { publicar { copy(isRefreshing = false) } }
        }
    }

    /** Carrega as anteriores à mais antiga da tela (rolou até o topo). */
    fun loadOlder() {
        scope.launch {
            val antiga = mutex.withLock {
                if (_state.value.isLoadingOlder || !_state.value.hasMoreOlder) return@launch
                publicar { copy(isLoadingOlder = true) }
                confirmed.firstOrNull()?.id
            }
            if (antiga == null) {
                mutex.withLock { publicar { copy(isLoadingOlder = false) } }
                return@launch
            }
            val r = chamar { transport.before(antiga, config.pageSize) }
            mutex.withLock {
                if (r is DomainResult.Success) {
                    confirmed = mergeChatMessages(confirmed, r.data.messages)
                    publicar { copy(isLoadingOlder = false, hasMoreOlder = r.data.hasMore) }
                } else {
                    publicar { copy(isLoadingOlder = false) }
                }
            }
        }
    }

    // -- Lidas ------------------------------------------------------------------------

    /**
     * A recebida mais nova está visível: marca como lidas até ela (uma chamada por mensagem nova —
     * repetir não chama o servidor). O [ChatThread] chama quando o fim da lista aparece.
     */
    fun markLatestAsRead() {
        scope.launch {
            val alvo = mutex.withLock {
                val id = chatLatestIncomingId(confirmed) ?: return@launch
                val msg = confirmed.first { it.id == id }
                if (id == lastReadSent || msg.readByOtherAtMillis != null) return@launch
                lastReadSent = id
                id
            }
            val r = chamar { transport.markRead(alvo) }
            mutex.withLock {
                if (r is DomainResult.Success) {
                    confirmed = markChatReadUpTo(confirmed, alvo, nowMillis())
                    publicar()
                } else if (lastReadSent == alvo) {
                    lastReadSent = null // tenta de novo na próxima vez que aparecer
                }
            }
        }
    }

    // -- Envio ------------------------------------------------------------------------

    /**
     * Envia um texto (aparado; vazio ou acima de [CHAT_TEXT_MAX_LENGTH] não vai — devolve `false`).
     * Aparece na hora como "enviando".
     */
    suspend fun sendText(text: String): Boolean {
        val t = normalizeChatText(text) ?: return false
        enfileirar(ChatOutgoing(id = idFactory(), kind = ChatMessageKind.TEXT, text = t, createdAtMillis = nowMillis()))
        return true
    }

    /**
     * Envia uma nota de voz gravada. Os bytes vão para a fila durável e o arquivo temporário é
     * **apagado** (a fila fica com a cópia). `false` se o arquivo sumiu ou não coube.
     */
    suspend fun sendAudio(audio: RecordedAudio): Boolean {
        val bytes = audio.readBytes() ?: return false
        val ok = sendAudio(bytes, audio.durationMillis, audio.levels)
        if (ok) audio.deleteFile()
        return ok
    }

    /** O mesmo, a partir dos bytes (m4a/AAC). */
    suspend fun sendAudio(bytes: ByteArray, durationMillis: Long, levels: List<Float> = emptyList()): Boolean {
        if (bytes.isEmpty()) return false
        val id = idFactory()
        val blob = store.putAudio(accountId, id, bytes) ?: return false
        enfileirar(
            ChatOutgoing(
                id = id,
                kind = ChatMessageKind.AUDIO,
                audioBlobId = blob,
                audioDurationMillis = durationMillis,
                audioLevels = levels,
                createdAtMillis = nowMillis(),
            ),
        )
        return true
    }

    /** "Tentar de novo" numa mensagem não enviada — o MESMO id vai de novo (o servidor não duplica). */
    suspend fun retry(messageId: String): Boolean {
        val ok = mutex.withLock {
            val p = pending.firstOrNull { it.id == messageId } ?: return false
            pending = pending.map { if (it.id == messageId) p.copy(failure = null, autoAttempts = 0, nextAttemptAtMillis = 0L) else it }
            persistir()
            publicar()
            true
        }
        dispararEnvio()
        return ok
    }

    /** Desiste de uma mensagem não confirmada (apaga da fila e o áudio guardado). */
    suspend fun discard(messageId: String): Boolean = mutex.withLock {
        val p = pending.firstOrNull { it.id == messageId } ?: return false
        pending = pending - p
        p.audioBlobId?.let { store.deleteAudio(it) }
        persistir()
        publicar()
        true
    }

    /** Os bytes do áudio de uma mensagem ainda na fila (para tocar o que acabou de gravar). */
    suspend fun pendingAudioBytes(messageId: String): ByteArray? {
        val blob = mutex.withLock { pending.firstOrNull { it.id == messageId }?.audioBlobId } ?: return null
        return store.readAudio(blob)
    }

    private suspend fun enfileirar(msg: ChatOutgoing) {
        carregarPendentes()
        mutex.withLock {
            pending = pending + msg
            persistir()
            publicar()
        }
        dispararEnvio()
    }

    private suspend fun persistir() {
        if (!store.save(accountId, conversationKey, pending)) AppLogger.w(TAG, "fila de mensagens não gravou (disco?)")
    }

    private fun dispararEnvio() {
        if (senderJob?.isActive == true) return
        senderJob = scope.launch { enviarFila() }
    }

    private suspend fun enviarFila() {
        while (true) {
            val agora = nowMillis()
            val (proxima, espera) = mutex.withLock {
                val prontas = pending.filter { it.failure == null }
                val vez = prontas.filter { it.nextAttemptAtMillis <= agora }.minWithOrNull(compareBy({ it.createdAtMillis }, { it.id }))
                vez to prontas.minOfOrNull { it.nextAttemptAtMillis }
            }
            if (proxima == null) {
                if (espera == null) return
                delay((espera - agora).coerceAtLeast(MIN_RETRY_WAIT_MILLIS))
                continue
            }
            enviar(proxima)
        }
    }

    private suspend fun enviar(msg: ChatOutgoing) {
        var atual = msg
        if (atual.kind == ChatMessageKind.AUDIO && atual.audioAssetId == null) {
            val bytes = atual.audioBlobId?.let { store.readAudio(it) }
            if (bytes == null) {
                falhar(atual, ChatSendFailure(MISSING_AUDIO_CODE, message = MISSING_AUDIO_MESSAGE))
                return
            }
            val segundos = (((atual.audioDurationMillis ?: 1_000L) + 999L) / 1000L).toInt().coerceAtLeast(1)
            when (val r = chamar { transport.uploadAudio(bytes, segundos) }) {
                is DomainResult.Success -> {
                    atual = atual.copy(audioAssetId = r.data)
                    // Grava o asset ANTES de mandar a mensagem: o reenvio não sobe o áudio de novo.
                    if (!atualizarPendente(atual)) return
                }
                else -> {
                    tratarErro(atual, r)
                    return
                }
            }
        }
        when (val r = chamar { transport.send(ChatSendRequest(atual.id, atual.kind, atual.text, atual.audioAssetId)) }) {
            is DomainResult.Success -> mutex.withLock {
                confirmed = mergeChatMessages(confirmed, listOf(r.data))
                pending = pending.filterNot { it.id == atual.id }
                atual.audioBlobId?.let { store.deleteAudio(it) }
                persistir()
                publicar()
            }
            else -> tratarErro(atual, r)
        }
    }

    private suspend fun atualizarPendente(msg: ChatOutgoing): Boolean = mutex.withLock {
        if (pending.none { it.id == msg.id }) return false // descartada no meio
        pending = pending.map { if (it.id == msg.id) msg else it }
        persistir()
        publicar()
        true
    }

    private suspend fun tratarErro(msg: ChatOutgoing, r: DomainResult<*>) {
        when (r) {
            is DomainResult.Quota -> falhar(msg, ChatSendFailure(402))
            is DomainResult.Error -> when {
                r.code == DomainResult.OFFLINE_CODE -> {
                    // Sem rede: continua "enviando", tenta de novo sozinha, sem gastar tentativa.
                    atualizarPendente(msg.copy(nextAttemptAtMillis = nowMillis() + config.retryMaxMillis / 4))
                }
                isRetryableChatError(r.code) -> {
                    val tentativas = msg.autoAttempts + 1
                    if (tentativas >= config.autoRetryLimit) {
                        falhar(msg, ChatSendFailure(r.code, r.serverCode, r.userMessage))
                    } else {
                        var espera = config.retryBaseMillis
                        repeat(tentativas - 1) { espera = (espera * 2).coerceAtMost(config.retryMaxMillis) }
                        atualizarPendente(msg.copy(autoAttempts = tentativas, nextAttemptAtMillis = nowMillis() + espera))
                    }
                }
                else -> falhar(msg, ChatSendFailure(r.code, r.serverCode, r.userMessage))
            }
            is DomainResult.Success -> Unit
        }
    }

    private suspend fun falhar(msg: ChatOutgoing, falha: ChatSendFailure) {
        atualizarPendente(msg.copy(failure = falha))
    }

    private suspend fun <T> chamar(block: suspend () -> DomainResult<T>): DomainResult<T> = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        AppLogger.w(TAG, "conversa: chamada lançou ${e::class.simpleName}")
        DomainResult.Error(DomainResult.OFFLINE_CODE, "Falha de transporte.")
    }

    companion object {
        private const val TAG = "ChatController"
        private const val MAX_AFTER_PAGES = 5
        private const val MIN_RETRY_WAIT_MILLIS = 250L

        /** Sentinela: o áudio da mensagem não está mais no aparelho. */
        const val MISSING_AUDIO_CODE: Int = -41
        private const val MISSING_AUDIO_MESSAGE = "O áudio não está mais no aparelho."
    }
}

/** 401 (pós-renovação), 408, 429 e 5xx: passam sozinhos. */
fun isRetryableChatError(code: Int): Boolean = code == 401 || code == 408 || code == 429 || code in 500..599

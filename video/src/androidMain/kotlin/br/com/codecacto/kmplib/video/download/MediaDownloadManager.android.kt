package br.com.codecacto.kmplib.video.download

import android.content.Context
import androidx.annotation.OptIn
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import br.com.codecacto.kmplib.core.prefs.appPreferences
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.video.VideoMedia
import br.com.codecacto.kmplib.video.VideoPlayerHolder
import br.com.codecacto.kmplib.video.VideoStreamKind
import br.com.codecacto.kmplib.video.resolvedKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * O gerenciador de downloads sobre o **Media3** — `DownloadManager` + `DownloadService`, que é o
 * caminho oficial do Android para mídia offline.
 *
 * Por que não um `HttpURLConnection` num `WorkManager`: HLS **não é um arquivo**. O `.m3u8` é um
 * índice que aponta para centenas de segmentos, e um deles pode ser uma variante de qualidade
 * diferente. Baixar "o arquivo" resulta num texto de 2 KB que não toca em lugar nenhum. Quem sabe
 * percorrer o manifesto, escolher a faixa (`DownloadHelper`), guardar os segmentos com a chave
 * certa e devolvê-los ao player é o subsistema da Media3 — e o mesmo cache é lido na reprodução.
 */
@OptIn(UnstableApi::class)
internal class Media3MediaDownloadManager(
    private val context: Context,
    initialConfig: MediaDownloadConfig,
    private val store: MediaDownloadStore,
    private var onRenewUrl: (suspend (MediaDownloadRequest) -> String?)?,
) : MediaDownloadManager {

    override var config: MediaDownloadConfig = initialConfig
        private set

    private val _downloads = MutableStateFlow<List<MediaDownload>>(emptyList())
    override val downloads: StateFlow<List<MediaDownload>> = _downloads.asStateFlow()

    /**
     * A Main é a thread do `DownloadManager`: ele é criado com o looper da aplicação e entrega os
     * callbacks nele. Sair dela para chamar `addDownload`/`setStopReason` é o caminho mais curto
     * para uma corrida que só aparece em aparelho lento.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** O que a Media3 sabe de cada item. Só se toca na Main. */
    private val nativos = LinkedHashMap<String, Download>()

    /** Quantas renovações de URL assinada já foram gastas, por item. Ver [shouldRenewDownloadUrl]. */
    private val renovacoes = mutableMapOf<String, Int>()

    /**
     * A última falha classificada de cada item.
     *
     * O Media3 guarda no índice só `FAILURE_REASON_UNKNOWN` — o motivo real vive na `Exception` do
     * callback e some com ele. Sem guardá-lo aqui, todo download falhado voltaria à tela como "não
     * foi possível baixar", inclusive o que só precisava de uma URL nova ou de espaço em disco.
     */
    private val falhas = mutableMapOf<String, MediaDownloadErrorKind>()

    private val temAtivos = MutableStateFlow(false)
    private val mutexDeEscrita = Mutex()

    private val downloadManager get() = Media3Downloads.downloadManager(context)

    private val listener = object : androidx.media3.exoplayer.offline.DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: androidx.media3.exoplayer.offline.DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            nativos[download.request.id] = download
            atualizarAtivos()
            scope.launch {
                if (download.state == Download.STATE_FAILED) tratarFalha(download, finalException)
                publicar()
            }
        }

        override fun onDownloadRemoved(
            downloadManager: androidx.media3.exoplayer.offline.DownloadManager,
            download: Download,
        ) {
            nativos.remove(download.request.id)
            atualizarAtivos()
            scope.launch { publicar() }
        }

        override fun onIdle(downloadManager: androidx.media3.exoplayer.offline.DownloadManager) {
            atualizarAtivos()
            scope.launch { publicar() }
        }

        override fun onWaitingForRequirementsChanged(
            downloadManager: androidx.media3.exoplayer.offline.DownloadManager,
            waitingForRequirements: Boolean,
        ) {
            scope.launch { publicar() }
        }
    }

    init {
        // ⚠️ TUDO o que toca o `DownloadManager` entra pela Main, inclusive a criação dele: a Media3
        // o amarra ao looper da thread que o construiu, e um app que crie este gerenciador dentro de
        // um módulo Koin em thread de fundo passaria a falar com ele de duas threads — o tipo de
        // corrida que compila, passa no teste e só aparece em aparelho lento.
        scope.launch {
            aplicarConfig(config)
            downloadManager.addListener(listener)
            // O índice é SQLite: lê-lo fora da Main. Depois disso, quem mantém o mapa atualizado é
            // o listener — o mesmo desenho do `DownloadTracker` da própria Media3.
            val doIndice = withContext(Dispatchers.IO) { lerIndice() }
            doIndice.forEach { nativos[it.request.id] = it }
            atualizarAtivos()
            publicar()
            retomarPendentes()
        }
        scope.launch {
            temAtivos.collectLatest { ativo ->
                if (!ativo) return@collectLatest
                while (true) {
                    downloadManager.currentDownloads.forEach { nativos[it.request.id] = it }
                    publicar()
                    delay(PROGRESS_POLL_MILLIS)
                }
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Leitura
    // -----------------------------------------------------------------------------------------

    override suspend fun get(id: String): MediaDownload? {
        _downloads.value.firstOrNull { it.id == id }?.let { return it }
        val registro = store.get(id) ?: return null
        return withContext(Dispatchers.Main.immediate) { paraMediaDownload(registro) }
    }

    override suspend fun storageUsage(): MediaStorageUsage {
        val registros = store.all()
        val ocupado = withContext(Dispatchers.IO) {
            runCatching { Media3Downloads.cache(context).cacheSpace }.getOrDefault(0L)
        }
        val livre = withContext(Dispatchers.IO) { availableStorageBytes() }
        return MediaStorageUsage(
            usedBytes = ocupado,
            itemCount = registros.size,
            availableBytes = (livre - config.reservedSpaceBytes).coerceAtLeast(0L),
        )
    }

    override suspend fun offlineMediaFor(
        id: String,
        title: String?,
        artist: String?,
        startPositionMillis: Long,
    ): VideoMedia? {
        val registro = store.get(id) ?: return null
        if (isMediaDownloadExpired(registro.expiresAtMillis, agora())) return null
        val nativo = withContext(Dispatchers.Main.immediate) { nativos[id] }
            ?: withContext(Dispatchers.IO) { lerIndice().firstOrNull { it.request.id == id } }
        if (nativo?.state != Download.STATE_COMPLETED) return null
        return VideoMedia(
            url = registro.url,
            kind = registro.kind,
            title = title ?: registro.title,
            artist = artist,
            startPositionMillis = startPositionMillis,
            offlineId = id,
        )
    }

    // -----------------------------------------------------------------------------------------
    // Escrita
    // -----------------------------------------------------------------------------------------

    override suspend fun enqueue(request: MediaDownloadRequest): MediaDownloadOutcome =
        mutexDeEscrita.withLock { enqueueInterno(request) }

    private suspend fun enqueueInterno(request: MediaDownloadRequest): MediaDownloadOutcome {
        if (!isValidMediaDownloadId(request.id)) {
            AppLogger.e(DOWNLOAD_TAG, "id de download inválido: '${request.id}'")
            return MediaDownloadOutcome.Rejected(MediaDownloadErrorKind.Unknown, config.texts.invalidId)
        }

        val agora = agora()
        val existente = store.get(request.id)
        if (existente?.completed == true && !isMediaDownloadExpired(existente.expiresAtMillis, agora)) {
            return MediaDownloadOutcome.AlreadyDownloaded
        }

        val livre = withContext(Dispatchers.IO) { availableStorageBytes() }
        val espaco = checkMediaDownloadSpace(request.estimatedBytes, livre, config.reservedSpaceBytes)
        if (espaco is MediaDownloadSpaceCheck.NotEnough) {
            return MediaDownloadOutcome.Rejected(
                kind = MediaDownloadErrorKind.NoSpace,
                message = config.texts.messageFor(MediaDownloadErrorKind.NoSpace),
                missingBytes = espaco.missingBytes,
            )
        }

        naMain {
            falhas.remove(request.id)
            renovacoes.remove(request.id)
        }
        val registro = (existente ?: request.toRecord(agora)).copy(
            url = request.url,
            title = request.title ?: existente?.title,
            groupId = request.groupId ?: existente?.groupId,
            quality = request.quality,
            estimatedBytes = request.estimatedBytes,
            expiresAtMillis = request.expiresAtMillis,
            pausedByUser = false,
        )
        store.put(registro)

        return try {
            adicionarNaMedia3(request)
            publicar()
            MediaDownloadOutcome.Enqueued
        } catch (e: IOException) {
            // Manifesto que não abre (rede fora, token já vencido) — a falha é do PREPARO, e por
            // isso não passa pelo listener: ela precisa voltar para quem tocou no botão.
            AppLogger.e(DOWNLOAD_TAG, "Falha ao preparar o download de ${request.id}: ${e.message}")
            val kind = paraMediaDownloadErrorKind(e)
            MediaDownloadOutcome.Rejected(kind, config.texts.messageFor(kind))
        }
    }

    override suspend fun pause(id: String) = mutexDeEscrita.withLock {
        store.get(id)?.let { store.put(it.copy(pausedByUser = true)) }
        naMain { downloadManager.setStopReason(id, Media3Downloads.STOP_REASON_USER_PAUSED) }
        publicar()
    }

    override suspend fun resume(id: String) = mutexDeEscrita.withLock {
        store.get(id)?.let { store.put(it.copy(pausedByUser = false)) }
        naMain {
            falhas.remove(id)
            downloadManager.setStopReason(id, Download.STOP_REASON_NONE)
            iniciarServico()
        }
        publicar()
    }

    override suspend fun pauseAll() {
        store.all().filterNot { it.completed }.forEach { pause(it.id) }
    }

    override suspend fun resumeAll() {
        store.all().filterNot { it.completed }.forEach { resume(it.id) }
    }

    override suspend fun remove(id: String) = mutexDeEscrita.withLock {
        store.remove(id)
        naMain {
            renovacoes.remove(id)
            falhas.remove(id)
            downloadManager.removeDownload(id)
            nativos.remove(id)
        }
        publicar()
    }

    override suspend fun removeGroup(groupId: String): Int {
        val alvos = store.all().filter { it.groupId == groupId }
        alvos.forEach { remove(it.id) }
        return alvos.size
    }

    override suspend fun removeAll() = mutexDeEscrita.withLock {
        store.clear()
        naMain {
            renovacoes.clear()
            falhas.clear()
            downloadManager.removeAllDownloads()
            nativos.clear()
        }
        publicar()
    }

    override suspend fun purgeExpired(nowMillis: Long): List<String> {
        val vencidos = expiredMediaDownloadIds(store.all(), nowMillis)
        vencidos.forEach { remove(it) }
        return vencidos
    }

    override fun updateConfig(config: MediaDownloadConfig) {
        this.config = config
        scope.launch {
            aplicarConfig(config)
            publicar()
        }
    }

    // -----------------------------------------------------------------------------------------
    // Interno
    // -----------------------------------------------------------------------------------------

    private fun aplicarConfig(config: MediaDownloadConfig) {
        downloadManager.maxParallelDownloads = config.maxParallelDownloads.coerceAtLeast(1)
        downloadManager.requirements = Media3Downloads.requirementsFor(config.wifiOnly)
    }

    private fun lerIndice(): List<Download> = runCatching {
        val lista = mutableListOf<Download>()
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) lista += cursor.download
        }
        lista.toList()
    }.getOrElse {
        AppLogger.w(DOWNLOAD_TAG, "Não foi possível ler o índice de downloads: ${it.message}")
        emptyList()
    }

    private fun atualizarAtivos() {
        temAtivos.value = downloadManager.currentDownloads.isNotEmpty()
    }

    /**
     * Devolve à fila o que estava baixando quando o app morreu.
     *
     * O Media3 já retoma sozinho o que está no índice, mas só quando o **serviço** sobe — e ele não
     * sobe sozinho depois de o processo ser morto pelo sistema. Sem este empurrão, o aluno reabre o
     * app e vê o download parado em 37%, sem nada acontecendo.
     */
    private suspend fun retomarPendentes() {
        val pendentes = mediaDownloadsToResume(store.all(), agora())
        if (pendentes.isEmpty()) return
        pendentes.forEach { downloadManager.setStopReason(it.id, Download.STOP_REASON_NONE) }
        iniciarServico()
    }

    private fun iniciarServico() {
        runCatching { DownloadService.startForeground(context, KmplibDownloadService::class.java) }
            .onFailure {
                // Android 12+ recusa iniciar serviço em primeiro plano com o app em segundo plano.
                // Não é erro do produto: a fila continua no `DownloadManager` enquanto o processo
                // vive, e o agendador a religa quando o app voltar.
                AppLogger.w(DOWNLOAD_TAG, "Serviço de download não pôde subir agora: ${it.message}")
            }
    }

    private suspend fun adicionarNaMedia3(request: MediaDownloadRequest) {
        val nativo = construirDownloadRequest(request)
        withContext(Dispatchers.Main.immediate) {
            downloadManager.addDownload(nativo)
            downloadManager.setStopReason(request.id, Download.STOP_REASON_NONE)
            iniciarServico()
        }
    }

    /**
     * Monta o pedido da Media3.
     *
     * **A chave de cache é o [MediaDownloadRequest.id], nunca a URL** — e isto é o que faz a
     * renovação do token funcionar. Sem `customCacheKey`, a Media3 endereça os pedaços pela URI: a
     * URL assinada muda a cada renovação, os pedaços já baixados ficam órfãos e o download recomeça
     * do zero **ocupando o dobro do espaço**. Com a chave fixa, trocar a URL é trocar por onde
     * buscar o que falta.
     */
    private suspend fun construirDownloadRequest(request: MediaDownloadRequest): DownloadRequest {
        val ehHls = request.resolvedKindForDownload() == VideoStreamKind.Hls
        val item = MediaItem.Builder()
            .setUri(request.url)
            .apply { if (ehHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()

        if (!ehHls) {
            // Progressivo não precisa de `DownloadHelper`: não há faixa a escolher, e preparar um
            // ExoPlayer só para descobrir isso seria uma volta de rede à toa.
            return DownloadRequest.Builder(request.id, Uri.parse(request.url))
                .setCustomCacheKey(request.id)
                .build()
        }

        val base = prepararHls(request, item)
        return DownloadRequest.Builder(request.id, base.uri)
            .setMimeType(base.mimeType)
            .setStreamKeys(base.streamKeys)
            .setCustomCacheKey(request.id)
            .build()
    }

    /** Abre o manifesto e escolhe a faixa. `prepare` é assíncrono e entrega no looper de quem chama. */
    private suspend fun prepararHls(
        request: MediaDownloadRequest,
        item: MediaItem,
    ): DownloadRequest = withContext(Dispatchers.Main.immediate) {
        suspendCancellableCoroutine { cont ->
            // `DEFAULT_TRACK_SELECTOR_PARAMETERS` é a base própria de DOWNLOAD da Media3 (ela
            // liga `forceHighestSupportedBitrate`, porque baixar não é escolher faixa a cada
            // segundo pela banda: escolhe-se uma, para sempre). O teto por qualidade entra em cima
            // dela. A variante `getDefaultTrackSelectorParameters(context)` está deprecada em 1.6.
            val parametros = DownloadHelper.DEFAULT_TRACK_SELECTOR_PARAMETERS
                .buildUpon()
                .aplicar(request.quality)
                .build()
            val helper = DownloadHelper.forMediaItem(
                item,
                parametros,
                DefaultRenderersFactory(context),
                Media3Downloads.httpDataSourceFactory(),
            )
            cont.invokeOnCancellation { runCatching { helper.release() } }
            helper.prepare(object : DownloadHelper.Callback {
                // `hasPreparedTracks` chegou na Media3 1.9 (a assinatura de 1.6 tinha só o helper).
                // Ele diz se houve seleção de faixa — `false` em conteúdo de faixa única. Não muda o
                // que fazemos: `getDownloadRequest` devolve o pedido nos dois casos, e o teto de
                // qualidade já foi aplicado nos parâmetros acima.
                override fun onPrepared(helper: DownloadHelper, hasPreparedTracks: Boolean) {
                    val resultado = runCatching { helper.getDownloadRequest(request.id, null) }
                    helper.release()
                    resultado.fold(
                        onSuccess = { if (cont.isActive) cont.resume(it) },
                        onFailure = { if (cont.isActive) cont.resumeWithException(IOException(it)) },
                    )
                }

                override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                    helper.release()
                    if (cont.isActive) cont.resumeWithException(e)
                }
            })
        }
    }

    /**
     * Trata a falha de um item — e, quando ela é URL vencida, **renova em silêncio**.
     *
     * O download de uma aula leva minutos; um token de 15 minutos vencer no meio é o caso normal,
     * não a exceção. Ver `MediaDownloadManager.onRenewUrl`.
     */
    private suspend fun tratarFalha(download: Download, exception: Exception?) {
        val id = download.request.id
        val kind = paraMediaDownloadErrorKind(exception)
        falhas[id] = kind
        val gastas = renovacoes[id] ?: 0
        val renovar = onRenewUrl
        if (renovar == null || !shouldRenewDownloadUrl(kind, gastas)) {
            AppLogger.e(DOWNLOAD_TAG, "Download $id falhou ($kind): ${exception?.message}")
            return
        }
        val registro = store.get(id) ?: return
        renovacoes[id] = gastas + 1
        val nova = runCatching { renovar(registro.toRequest()) }
            .onFailure { AppLogger.w(DOWNLOAD_TAG, "onRenewUrl falhou para $id: ${it.message}") }
            .getOrNull()
        if (nova.isNullOrBlank()) return

        store.put(registro.copy(url = nova))
        falhas.remove(id)
        // A chave de cache é a mesma (o id), então o que já veio ao disco continua valendo: isto é
        // uma retomada, não um recomeço.
        val renovado = DownloadRequest.Builder(id, Uri.parse(nova))
            .setMimeType(download.request.mimeType)
            .setStreamKeys(download.request.streamKeys)
            .setCustomCacheKey(id)
            .build()
        withContext(Dispatchers.Main.immediate) {
            downloadManager.addDownload(renovado)
            downloadManager.setStopReason(id, Download.STOP_REASON_NONE)
            iniciarServico()
        }
    }

    private suspend fun publicar() {
        val registros = store.all()
        val agora = agora()
        _downloads.value = withContext(Dispatchers.Main.immediate) {
            registros.map { paraMediaDownload(it, agora) }
        }
    }

    private fun paraMediaDownload(
        registro: MediaDownloadRecord,
        agora: Long = agora(),
    ): MediaDownload {
        val nativo = nativos[registro.id]
        val falha = if (nativo?.state == Download.STATE_FAILED) {
            config.texts.failure(falhas[registro.id] ?: MediaDownloadErrorKind.Unknown)
        } else {
            null
        }
        val baixado = nativo?.bytesDownloaded ?: 0L
        val total = nativo?.contentLength?.takeIf { it > 0 } ?: registro.estimatedBytes
        val concluido = nativo?.state == Download.STATE_COMPLETED
        val status = mediaDownloadStatusOf(
            completed = concluido,
            running = nativo?.state == Download.STATE_DOWNLOADING,
            pausedByUser = registro.pausedByUser,
            failure = falha,
            expired = isMediaDownloadExpired(registro.expiresAtMillis, agora),
        )
        if (concluido != registro.completed) {
            scope.launch { store.put(registro.copy(completed = concluido)) }
        }
        return MediaDownload(
            id = registro.id,
            url = registro.url,
            title = registro.title,
            groupId = registro.groupId,
            status = status,
            downloadedBytes = baixado,
            totalBytes = total,
            percent = if (concluido) 100 else mediaDownloadPercent(baixado, total),
            waitingForNetwork = status == MediaDownloadStatus.Queued &&
                downloadManager.isWaitingForRequirements,
            expiresAtMillis = registro.expiresAtMillis,
        )
    }

    private fun agora(): Long = System.currentTimeMillis()

    /**
     * Roda o bloco na thread da aplicação.
     *
     * Todo acesso ao `DownloadManager` **e** aos mapas em memória (`nativos`, `renovacoes`,
     * `falhas`) passa por aqui: quem mais os escreve é o listener da Media3, que chega sempre no
     * looper da aplicação. Duas threads mexendo num `LinkedHashMap` é corrida que não dá erro —
     * dá lista errada na tela, de vez em quando.
     */
    private suspend inline fun <T> naMain(crossinline bloco: () -> T): T =
        withContext(Dispatchers.Main.immediate) { bloco() }
}

/** A qualidade traduzida para os parâmetros de seleção de faixa da Media3. */
@OptIn(UnstableApi::class)
private fun DefaultTrackSelector.Parameters.Builder.aplicar(
    quality: MediaDownloadQuality,
): DefaultTrackSelector.Parameters.Builder = when (quality) {
    // Os dois juntos: a base de download da Media3 liga `forceHighestSupportedBitrate`, e deixá-lo
    // ligado faria "menor faixa" continuar baixando a maior.
    MediaDownloadQuality.Low -> setForceLowestBitrate(true).setForceHighestSupportedBitrate(false)
    MediaDownloadQuality.High -> this
    // `setExceedVideoConstraintsIfNecessary` já é `true` por default: quando TODAS as faixas passam
    // do teto, a Media3 escolhe a menor em vez de não escolher nenhuma. É o que faz um curso gravado
    // só em 1080p ainda baixar, em vez de falhar sem explicação.
    MediaDownloadQuality.Standard -> setMaxVideoBitrate(STANDARD_BITRATE_CEILING)
}

/** `Auto` resolvido pela extensão do caminho — a mesma regra da reprodução. */
private fun MediaDownloadRequest.resolvedKindForDownload(): VideoStreamKind =
    br.com.codecacto.kmplib.video.VideoMedia(url = url, kind = kind).resolvedKind()

/**
 * Traduz a exceção da Media3 no tipo de falha do produto.
 *
 * O `403` é o caso que importa: sem separá-lo, a URL assinada vencida vira "erro de rede" e a tela
 * manda o aluno conferir o wi-fi enquanto o que falta é um token novo.
 */
@OptIn(UnstableApi::class)
internal fun paraMediaDownloadErrorKind(exception: Throwable?): MediaDownloadErrorKind {
    var causa: Throwable? = exception
    var profundidade = 0
    while (causa != null && profundidade < MAX_PROFUNDIDADE_DE_CAUSA) {
        when (causa) {
            is HttpDataSource.InvalidResponseCodeException ->
                return mediaDownloadErrorKindForHttpStatus(causa.responseCode)
            is HttpDataSource.HttpDataSourceException -> return MediaDownloadErrorKind.Network
        }
        val mensagem = causa.message.orEmpty()
        if (mensagem.contains("ENOSPC") || mensagem.contains("No space left", ignoreCase = true)) {
            return MediaDownloadErrorKind.NoSpace
        }
        causa = causa.cause
        profundidade++
    }
    return if (exception is IOException) MediaDownloadErrorKind.Network else MediaDownloadErrorKind.Unknown
}

/** Cadeia de causas curta de propósito: exceção cíclica existe, e um `while` ingênuo trava o app. */
private const val MAX_PROFUNDIDADE_DE_CAUSA = 8

/**
 * A instância única do processo.
 *
 * Ver o KDoc de [MediaDownloadManager]: dois `SimpleCache` sobre a mesma pasta não coexistem — o
 * segundo lança. O singleton não é conveniência, é o que impede a segunda tela a chamar
 * [createMediaDownloadManager] de derrubar o app.
 */
private object InstanciaDeDownload {
    @Volatile
    var atual: Media3MediaDownloadManager? = null
}

@OptIn(UnstableApi::class)
actual fun createMediaDownloadManager(
    config: MediaDownloadConfig,
    store: MediaDownloadStore?,
    onRenewUrl: (suspend (MediaDownloadRequest) -> String?)?,
): MediaDownloadManager {
    InstanciaDeDownload.atual?.let {
        it.updateConfig(config)
        return it
    }
    return synchronized(InstanciaDeDownload) {
        InstanciaDeDownload.atual?.let {
            it.updateConfig(config)
            return@synchronized it
        }
        val context = VideoPlayerHolder.getContext()
            ?: error(
                "kmplib-video: chame initKmpLibVideo(context) no Application.onCreate() " +
                    "(ou KmpLib.init(context), se usa o artefato umbrella).",
            )
        Media3MediaDownloadManager(
            context = context,
            initialConfig = config,
            store = store ?: PreferencesMediaDownloadStore(appPreferences()),
            onRenewUrl = onRenewUrl,
        ).also { InstanciaDeDownload.atual = it }
    }
}

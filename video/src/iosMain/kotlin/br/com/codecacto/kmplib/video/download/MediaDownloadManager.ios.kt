@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:no-wildcard-imports")

package br.com.codecacto.kmplib.video.download

import br.com.codecacto.kmplib.core.prefs.appPreferences
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.video.VideoMedia
import br.com.codecacto.kmplib.video.VideoStreamKind
import br.com.codecacto.kmplib.video.resolvedKind
import kotlinx.cinterop.CValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeRange
import platform.CoreMedia.CMTimeRangeGetEnd
import platform.Foundation.*
import platform.darwin.NSObject

/**
 * O gerenciador de downloads no iOS — **`AVAssetDownloadTask` para HLS**, `NSURLSessionDownloadTask`
 * para arquivo progressivo, os dois em **sessão de segundo plano**.
 *
 * Três coisas que a Apple impõe e que explicam o desenho:
 *
 * 1. **HLS só se baixa por `AVAssetDownloadTask`.** Não existe "baixar o `.m3u8`": o resultado é um
 *    índice de texto que não toca. A `AVAssetDownloadURLSession` percorre o manifesto, escolhe a
 *    variante e monta um pacote `.movpkg` que o `AVURLAsset` sabe abrir sem rede. É o único caminho
 *    público — e o `AVAssetDownloadTask` **só existe em sessão de configuração de fundo**.
 * 2. **O caminho do baixado se guarda RELATIVO, nunca absoluto.** O diretório da aplicação (o
 *    `.../Containers/Data/Application/<UUID>/`) **muda a cada atualização do app**. Um caminho
 *    absoluto persistido volta apontando para uma pasta que não existe mais, e todas as aulas
 *    baixadas somem de uma vez, na atualização — sem erro, sem log, e sem os bytes terem saído do
 *    disco. Guardamos o pedaço depois de `NSHomeDirectory()` e recompomos na leitura.
 * 3. **A sessão de fundo é uma por identificador, para o processo inteiro.** Criar a segunda com o
 *    mesmo identificador é comportamento indefinido — daí o singleton, o mesmo do Android.
 */
private class IosMediaDownloadManager(
    initialConfig: MediaDownloadConfig,
    private val store: MediaDownloadStore,
    private val onRenewUrl: (suspend (MediaDownloadRequest) -> String?)?,
) : MediaDownloadManager {

    override var config: MediaDownloadConfig = initialConfig
        private set

    private val _downloads = MutableStateFlow<List<MediaDownload>>(emptyList())
    override val downloads: StateFlow<List<MediaDownload>> = _downloads.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutexDeEscrita = Mutex()

    /** Andamento por item, alimentado pelos callbacks da sessão. */
    private val progresso = mutableMapOf<String, ProgressoNativo>()
    private val renovacoes = mutableMapOf<String, Int>()

    private val delegado = Delegado(this)

    /** A sessão de HLS. `AVAssetDownloadTask` exige configuração de fundo. */
    private val sessaoHls: AVAssetDownloadURLSession = AVAssetDownloadURLSession.sessionWithConfiguration(
        configuration = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(SESSAO_HLS)
            .apply { setAllowsCellularAccess(!initialConfig.wifiOnly) },
        assetDownloadDelegate = delegado,
        delegateQueue = NSOperationQueue.mainQueue,
    )

    /** A sessão dos arquivos progressivos (mp4 e afins). */
    private val sessaoArquivo: NSURLSession = NSURLSession.sessionWithConfiguration(
        configuration = NSURLSessionConfiguration.backgroundSessionConfigurationWithIdentifier(SESSAO_ARQUIVO)
            .apply { setAllowsCellularAccess(!initialConfig.wifiOnly) },
        delegate = delegado,
        delegateQueue = NSOperationQueue.mainQueue,
    )

    init {
        scope.launch {
            reconectarTarefas()
            publicar()
            retomarPendentes()
        }
    }

    // -----------------------------------------------------------------------------------------
    // Leitura
    // -----------------------------------------------------------------------------------------

    override suspend fun get(id: String): MediaDownload? =
        store.get(id)?.let { paraMediaDownload(it) }

    override suspend fun storageUsage(): MediaStorageUsage {
        val registros = store.all()
        val ocupado = registros.sumOf { registro ->
            registro.localPath?.let { tamanhoEmDisco(caminhoAbsoluto(it)) } ?: 0L
        }
        return MediaStorageUsage(
            usedBytes = ocupado,
            itemCount = registros.size,
            availableBytes = (availableStorageBytes() - config.reservedSpaceBytes).coerceAtLeast(0L),
        )
    }

    override suspend fun offlineMediaFor(
        id: String,
        title: String?,
        artist: String?,
        startPositionMillis: Long,
    ): VideoMedia? {
        val registro = store.get(id) ?: return null
        if (!registro.completed) return null
        if (isMediaDownloadExpired(registro.expiresAtMillis, agora())) return null
        val caminho = registro.localPath?.let { caminhoAbsoluto(it) } ?: return null
        if (!NSFileManager.defaultManager.fileExistsAtPath(caminho)) {
            AppLogger.w(DOWNLOAD_TAG, "Baixado sumiu do disco: $id")
            return null
        }
        // A URL é a do ARQUIVO local — é isto que faz "tocar o baixado" ser o mesmo `VideoPlayer`:
        // o `AVURLAsset` abre um `.movpkg` exatamente como abriria o HLS remoto.
        return VideoMedia(
            url = NSURL.fileURLWithPath(caminho).absoluteString ?: return null,
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

        val espaco = checkMediaDownloadSpace(
            requiredBytes = request.estimatedBytes,
            availableBytes = availableStorageBytes(),
            reservedBytes = config.reservedSpaceBytes,
        )
        if (espaco is MediaDownloadSpaceCheck.NotEnough) {
            return MediaDownloadOutcome.Rejected(
                kind = MediaDownloadErrorKind.NoSpace,
                message = config.texts.messageFor(MediaDownloadErrorKind.NoSpace),
                missingBytes = espaco.missingBytes,
            )
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
        renovacoes.remove(request.id)
        iniciarTarefa(registro)
        publicar()
        return MediaDownloadOutcome.Enqueued
    }

    override suspend fun pause(id: String) = mutexDeEscrita.withLock {
        store.get(id)?.let { store.put(it.copy(pausedByUser = true)) }
        val tarefa = tarefaDe(id)
        if (tarefa is NSURLSessionDownloadTask) {
            // `cancel(byProducingResumeData:)` é a ÚNICA pausa durável de um download progressivo:
            // `suspend()` só sobrevive enquanto o processo vive. Os dados de retomada vão para o
            // disco e o download volta do byte em que parou, e não do zero.
            tarefa.cancelByProducingResumeData { dados ->
                dados?.writeToFile(caminhoDeRetomada(id), true)
            }
        } else {
            tarefa?.suspend()
        }
        progresso[id] = (progresso[id] ?: ProgressoNativo()).copy(rodando = false)
        publicar()
    }

    override suspend fun resume(id: String) = mutexDeEscrita.withLock {
        val registro = store.get(id) ?: return@withLock
        store.put(registro.copy(pausedByUser = false))
        iniciarTarefa(registro.copy(pausedByUser = false))
        publicar()
    }

    override suspend fun pauseAll() {
        store.all().filterNot { it.completed }.forEach { pause(it.id) }
    }

    override suspend fun resumeAll() {
        store.all().filterNot { it.completed }.forEach { resume(it.id) }
    }

    override suspend fun remove(id: String) = mutexDeEscrita.withLock {
        val registro = store.get(id)
        tarefaDe(id)?.cancel()
        tarefas.remove(id)
        progresso.remove(id)
        renovacoes.remove(id)
        registro?.localPath?.let { apagarDoDisco(caminhoAbsoluto(it)) }
        apagarDoDisco(caminhoDeRetomada(id))
        store.remove(id)
        publicar()
    }

    override suspend fun removeGroup(groupId: String): Int {
        val alvos = store.all().filter { it.groupId == groupId }
        alvos.forEach { remove(it.id) }
        return alvos.size
    }

    override suspend fun removeAll() {
        store.all().forEach { remove(it.id) }
    }

    override suspend fun purgeExpired(nowMillis: Long): List<String> {
        val vencidos = expiredMediaDownloadIds(store.all(), nowMillis)
        vencidos.forEach { remove(it) }
        return vencidos
    }

    override fun updateConfig(config: MediaDownloadConfig) {
        // Ver o KDoc de `MediaDownloadManager.updateConfig`: `allowsCellularAccess` é fixado na
        // criação da `NSURLSession`. Invalidar a sessão para recriá-la derrubaria o que está
        // baixando — perder o progresso para aplicar uma preferência é pior do que aplicá-la a
        // partir do próximo item.
        this.config = config
        scope.launch { publicar() }
    }

    // -----------------------------------------------------------------------------------------
    // Tarefas
    // -----------------------------------------------------------------------------------------

    private val tarefas = mutableMapOf<String, NSURLSessionTask>()

    private fun tarefaDe(id: String): NSURLSessionTask? = tarefas[id]

    /**
     * Reencontra as tarefas que a sessão de fundo continuou enquanto o app estava fechado.
     *
     * É esta reconexão que faz a retomada funcionar de verdade: a `NSURLSession` de fundo sobrevive
     * ao app ser encerrado, e ao voltar ela **já tem** as tarefas — recriá-las às cegas duplicaria
     * cada download. A identidade viaja no `taskDescription`.
     */
    private fun reconectarTarefas() {
        sessaoHls.getAllTasksWithCompletionHandler { lista ->
            lista?.filterIsInstance<NSURLSessionTask>()?.forEach { registrarTarefaExistente(it) }
        }
        sessaoArquivo.getAllTasksWithCompletionHandler { lista ->
            lista?.filterIsInstance<NSURLSessionTask>()?.forEach { registrarTarefaExistente(it) }
        }
    }

    private fun registrarTarefaExistente(tarefa: NSURLSessionTask) {
        val id = tarefa.taskDescription ?: return
        tarefas[id] = tarefa
        progresso[id] = (progresso[id] ?: ProgressoNativo())
            .copy(rodando = tarefa.state == NSURLSessionTaskStateRunning)
    }

    private fun iniciarTarefa(registro: MediaDownloadRecord) {
        if (tarefaDe(registro.id)?.state == NSURLSessionTaskStateRunning) return
        val url = NSURL.URLWithString(registro.url) ?: run {
            AppLogger.e(DOWNLOAD_TAG, "URL inválida para ${registro.id}")
            return
        }
        val ehHls = VideoMedia(url = registro.url, kind = registro.kind).resolvedKind() == VideoStreamKind.Hls
        val tarefa = if (ehHls) tarefaHls(registro, url) else tarefaDeArquivo(registro, url)
        if (tarefa == null) {
            AppLogger.e(DOWNLOAD_TAG, "Não foi possível criar a tarefa de download de ${registro.id}")
            return
        }
        tarefa.taskDescription = registro.id
        tarefas[registro.id] = tarefa
        progresso[registro.id] = (progresso[registro.id] ?: ProgressoNativo()).copy(rodando = true)
        tarefa.resume()
    }

    /**
     * A tarefa de HLS.
     *
     * Quando já existe pacote parcial no disco, o ativo é aberto **a partir dele** — é a retomada
     * documentada pela Apple para HLS: o `AVURLAsset` local sabe o que já tem e busca só o que
     * falta. Sem isso, um download de 800 MB interrompido em 90% recomeçaria do zero.
     */
    private fun tarefaHls(registro: MediaDownloadRecord, url: NSURL): AVAssetDownloadTask? {
        val parcial = registro.localPath
            ?.let { caminhoAbsoluto(it) }
            ?.takeIf { NSFileManager.defaultManager.fileExistsAtPath(it) }
        val ativo = if (parcial != null) {
            AVURLAsset(uRL = NSURL.fileURLWithPath(parcial), options = null)
        } else {
            AVURLAsset(uRL = url, options = null)
        }
        val opcoes = mutableMapOf<Any?, Any?>()
        // A qualidade se pede por bitrate MÍNIMO: a AVFoundation escolhe a menor variante que o
        // alcança. É a única alavanca pública de seleção de faixa no download — não há como pedir
        // "a de 720p".
        bitrateMinimoPara(registro.quality)?.let {
            opcoes[AVAssetDownloadTaskMinimumRequiredMediaBitrateKey] = NSNumber(int = it)
        }
        // ⚠️ `URLAsset` em MAIÚSCULAS: o cinterop preserva a sigla do selector Objective-C neste
        // método. Não é o mesmo do construtor `AVURLAsset(uRL = …)`, que vira minúsculo — por isso
        // o erro sai como "None of the following candidates is applicable", e não como nome errado.
        return sessaoHls.assetDownloadTaskWithURLAsset(
            URLAsset = ativo,
            assetTitle = registro.title ?: registro.id,
            assetArtworkData = null,
            options = opcoes,
        )
    }

    /** Arquivo progressivo: retoma pelos dados de retomada quando eles existem. */
    private fun tarefaDeArquivo(registro: MediaDownloadRecord, url: NSURL): NSURLSessionDownloadTask {
        val retomada = NSData.dataWithContentsOfFile(caminhoDeRetomada(registro.id))
        return if (retomada != null) {
            apagarDoDisco(caminhoDeRetomada(registro.id))
            sessaoArquivo.downloadTaskWithResumeData(retomada)
        } else {
            sessaoArquivo.downloadTaskWithURL(url)
        }
    }

    private suspend fun retomarPendentes() {
        mediaDownloadsToResume(store.all(), agora()).forEach { iniciarTarefa(it) }
    }

    // -----------------------------------------------------------------------------------------
    // Callbacks da sessão (chamados pelo [Delegado], sempre na main queue)
    // -----------------------------------------------------------------------------------------

    fun aoProgredir(id: String, baixado: Long, total: Long) {
        progresso[id] = ProgressoNativo(baixado = baixado, total = total, rodando = true)
        scope.launch { publicar() }
    }

    fun aoTerminarArquivo(id: String, caminhoRelativo: String) {
        scope.launch {
            val registro = store.get(id) ?: return@launch
            store.put(registro.copy(completed = true, localPath = caminhoRelativo, pausedByUser = false))
            progresso[id] = (progresso[id] ?: ProgressoNativo()).copy(rodando = false, concluido = true)
            publicar()
        }
    }

    /**
     * O pacote parcial de um HLS **também** chega por `didFinishDownloadingTo`, e é por isso que
     * este caminho não marca conclusão sozinho: quem diz que terminou é o `didCompleteWithError`
     * com erro nulo. Guardar o caminho aqui é o que permite retomar depois.
     */
    fun aoReceberCaminhoDeHls(id: String, caminhoRelativo: String) {
        scope.launch {
            val registro = store.get(id) ?: return@launch
            store.put(registro.copy(localPath = caminhoRelativo))
        }
    }

    fun aoCompletar(id: String, erro: NSError?) {
        scope.launch {
            val registro = store.get(id) ?: return@launch
            if (erro == null) {
                store.put(registro.copy(completed = true, pausedByUser = false))
                progresso[id] = (progresso[id] ?: ProgressoNativo()).copy(rodando = false, concluido = true)
                publicar()
                return@launch
            }
            progresso[id] = (progresso[id] ?: ProgressoNativo()).copy(rodando = false, falha = erroPara(erro))
            publicar()
            tentarRenovar(registro, erroPara(erro))
        }
    }

    private suspend fun tentarRenovar(registro: MediaDownloadRecord, kind: MediaDownloadErrorKind) {
        val renovar = onRenewUrl ?: return
        val gastas = renovacoes[registro.id] ?: 0
        if (!shouldRenewDownloadUrl(kind, gastas)) return
        renovacoes[registro.id] = gastas + 1
        val nova = runCatching { renovar(registro.toRequest()) }
            .onFailure { AppLogger.w(DOWNLOAD_TAG, "onRenewUrl falhou para ${registro.id}: ${it.message}") }
            .getOrNull()
        if (nova.isNullOrBlank()) return
        val atualizado = registro.copy(url = nova)
        store.put(atualizado)
        iniciarTarefa(atualizado)
        publicar()
    }

    // -----------------------------------------------------------------------------------------
    // Estado publicado
    // -----------------------------------------------------------------------------------------

    private suspend fun publicar() {
        val agora = agora()
        _downloads.value = store.all().map { paraMediaDownload(it, agora) }
    }

    private fun paraMediaDownload(
        registro: MediaDownloadRecord,
        agora: Long = agora(),
    ): MediaDownload {
        val p = progresso[registro.id] ?: ProgressoNativo()
        val concluido = registro.completed || p.concluido
        val status = mediaDownloadStatusOf(
            completed = concluido,
            running = p.rodando,
            pausedByUser = registro.pausedByUser,
            failure = p.falha?.let { config.texts.failure(it) },
            expired = isMediaDownloadExpired(registro.expiresAtMillis, agora),
        )
        val total = if (p.total > 0) p.total else registro.estimatedBytes
        return MediaDownload(
            id = registro.id,
            url = registro.url,
            title = registro.title,
            groupId = registro.groupId,
            status = status,
            downloadedBytes = p.baixado,
            totalBytes = total,
            percent = if (concluido) 100 else mediaDownloadPercent(p.baixado, total),
            // O iOS não expõe "estou esperando o Wi-Fi": a tarefa fica simplesmente parada. Dizer
            // que está esperando rede sem saber seria inventar informação na tela.
            waitingForNetwork = false,
            expiresAtMillis = registro.expiresAtMillis,
        )
    }

    private fun agora(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    // -----------------------------------------------------------------------------------------
    // O delegado ObjC
    // -----------------------------------------------------------------------------------------

    private class Delegado(
        private val dono: IosMediaDownloadManager,
    ) : NSObject(), AVAssetDownloadDelegateProtocol, NSURLSessionDownloadDelegateProtocol {

        /** HLS: o andamento vem em TEMPO carregado, não em bytes — a conversão é aproximada. */
        override fun URLSession(
            session: NSURLSession,
            assetDownloadTask: AVAssetDownloadTask,
            didLoadTimeRange: CValue<CMTimeRange>,
            totalTimeRangesLoaded: List<*>,
            timeRangeExpectedToLoad: CValue<CMTimeRange>,
        ) {
            val id = assetDownloadTask.taskDescription ?: return
            val esperado = CMTimeGetSeconds(CMTimeRangeGetEnd(timeRangeExpectedToLoad))
            // O FIM da faixa mais avançada, e não a soma das faixas: num download de HLS elas são
            // contíguas a partir de zero, e somar durações de intervalos que se sobrepõem faria a
            // barra passar de 100%.
            val carregado = totalTimeRangesLoaded.maxOfOrNull { faixa ->
                (faixa as? NSValue)?.let { CMTimeGetSeconds(CMTimeRangeGetEnd(it.CMTimeRangeValue)) } ?: 0.0
            } ?: 0.0
            if (esperado <= 0.0) return
            // Converte fração de tempo em "bytes" usando o tamanho estimado: a tela quer um
            // percentual, e o `AVAssetDownloadTask` não informa bytes. Sem estimativa, a fração
            // vira 0..1 sobre uma escala de 100 e o percentual ainda sai certo.
            val fracao = (carregado / esperado).coerceIn(0.0, 1.0)
            val total = ESCALA_DE_TEMPO
            dono.aoProgredir(id, (fracao * total).toLong(), total)
        }

        override fun URLSession(
            session: NSURLSession,
            assetDownloadTask: AVAssetDownloadTask,
            didFinishDownloadingToURL: NSURL,
        ) {
            val id = assetDownloadTask.taskDescription ?: return
            val relativo = caminhoRelativo(didFinishDownloadingToURL.path) ?: return
            dono.aoReceberCaminhoDeHls(id, relativo)
        }

        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didWriteData: Long,
            totalBytesWritten: Long,
            totalBytesExpectedToWrite: Long,
        ) {
            val id = downloadTask.taskDescription ?: return
            dono.aoProgredir(id, totalBytesWritten, totalBytesExpectedToWrite.coerceAtLeast(0L))
        }

        /**
         * ⚠️ O arquivo em `location` é **temporário e some quando este método retorna**. Mover tem
         * de acontecer aqui, de forma síncrona — despachar para outra fila é o erro clássico, e o
         * resultado é um download que "terminou" e não deixou arquivo nenhum.
         */
        override fun URLSession(
            session: NSURLSession,
            downloadTask: NSURLSessionDownloadTask,
            didFinishDownloadingToURL: NSURL,
        ) {
            val id = downloadTask.taskDescription ?: return
            val destino = mediaDownloadsDirectory()?.let { "$it/$id" } ?: return
            val gerenciador = NSFileManager.defaultManager
            gerenciador.removeItemAtPath(destino, null)
            val movido = gerenciador.moveItemAtPath(
                srcPath = didFinishDownloadingToURL.path ?: return,
                toPath = destino,
                error = null,
            )
            if (!movido) {
                AppLogger.e(DOWNLOAD_TAG, "Não foi possível guardar o arquivo baixado de $id")
                return
            }
            caminhoRelativo(destino)?.let { dono.aoTerminarArquivo(id, it) }
        }

        override fun URLSession(
            session: NSURLSession,
            task: NSURLSessionTask,
            didCompleteWithError: NSError?,
        ) {
            val id = task.taskDescription ?: return
            dono.aoCompletar(id, didCompleteWithError)
        }
    }

    private companion object {
        const val SESSAO_HLS = "br.com.codecacto.kmplib.video.download.hls"
        const val SESSAO_ARQUIVO = "br.com.codecacto.kmplib.video.download.file"

        /** A escala em que o progresso de HLS é publicado (o iOS informa tempo, não bytes). */
        const val ESCALA_DE_TEMPO = 10_000L
    }
}

/** O que se sabe do andamento de um item, do lado nativo. */
private data class ProgressoNativo(
    val baixado: Long = 0L,
    val total: Long = 0L,
    val rodando: Boolean = false,
    val concluido: Boolean = false,
    val falha: MediaDownloadErrorKind? = null,
)

/** O diretório durável das mídias baixadas: *Application Support*, nunca *Caches*. */
internal fun mediaDownloadsDirectory(): String? {
    val base = NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
    ).firstOrNull() as? String ?: return null
    val diretorio = "$base/kmplib_media_downloads"
    if (!NSFileManager.defaultManager.fileExistsAtPath(diretorio)) {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = diretorio,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }
    return diretorio
}

/**
 * O caminho **relativo à casa do app** — ver a decisão 2 no KDoc da classe: o container muda de
 * UUID a cada atualização, e um caminho absoluto persistido volta apontando para o nada.
 */
private fun caminhoRelativo(absoluto: String?): String? {
    val caminho = absoluto ?: return null
    val casa = NSHomeDirectory()
    return if (caminho.startsWith(casa)) caminho.removePrefix(casa).removePrefix("/") else caminho
}

private fun caminhoAbsoluto(relativo: String): String =
    if (relativo.startsWith("/")) relativo else "${NSHomeDirectory()}/$relativo"

private fun caminhoDeRetomada(id: String): String = "${mediaDownloadsDirectory()}/$id.retomada"

private fun apagarDoDisco(caminho: String) {
    if (NSFileManager.defaultManager.fileExistsAtPath(caminho)) {
        NSFileManager.defaultManager.removeItemAtPath(caminho, null)
    }
}

private fun tamanhoEmDisco(caminho: String): Long {
    val gerenciador = NSFileManager.defaultManager
    val atributos = gerenciador.attributesOfItemAtPath(caminho, null) ?: return 0L
    val tamanho = (atributos[NSFileSize] as? Number)?.toLong() ?: 0L
    if (tamanho > 0L) return tamanho
    // Pacote `.movpkg` é um DIRETÓRIO: o `NSFileSize` dele é o do nó, não o do conteúdo. Sem somar
    // recursivamente, a tela de Downloads mostraria "0 KB" para toda aula em HLS.
    val enumerador = gerenciador.enumeratorAtPath(caminho) ?: return 0L
    var soma = 0L
    while (true) {
        val relativo = enumerador.nextObject() as? String ?: break
        val atributosFilho = gerenciador.attributesOfItemAtPath("$caminho/$relativo", null)
        soma += (atributosFilho?.get(NSFileSize) as? Number)?.toLong() ?: 0L
    }
    return soma
}

/** O bitrate mínimo pedido à AVFoundation para cada qualidade. `null` = a escolha default dela. */
private fun bitrateMinimoPara(quality: MediaDownloadQuality): Int? = when (quality) {
    // Sem mínimo: a AVFoundation baixa a menor variante disponível.
    MediaDownloadQuality.Low -> null
    MediaDownloadQuality.Standard -> STANDARD_BITRATE_CEILING
    // Um mínimo altíssimo faz a AVFoundation cair na MAIOR variante existente (ela escolhe a menor
    // que alcança o mínimo e, não havendo nenhuma, a maior de todas).
    MediaDownloadQuality.High -> BITRATE_ALTISSIMO
}

private const val BITRATE_ALTISSIMO = 100_000_000

/** Traduz o `NSError` da sessão no tipo de falha do produto. */
private fun erroPara(erro: NSError): MediaDownloadErrorKind {
    val resposta = erro.userInfo["NSHTTPPropertyStatusCodeKey"] as? Number
    if (resposta != null) return mediaDownloadErrorKindForHttpStatus(resposta.toInt())
    return when (erro.code) {
        NSURLErrorNotConnectedToInternet,
        NSURLErrorTimedOut,
        NSURLErrorNetworkConnectionLost,
        NSURLErrorCannotConnectToHost,
        -> MediaDownloadErrorKind.Network

        NSURLErrorUserAuthenticationRequired,
        NSURLErrorNoPermissionsToReadFile,
        -> MediaDownloadErrorKind.Expired

        NSURLErrorFileDoesNotExist,
        NSURLErrorResourceUnavailable,
        -> MediaDownloadErrorKind.NotFound

        NSURLErrorCannotWriteToFile,
        NSURLErrorCannotCreateFile,
        -> MediaDownloadErrorKind.NoSpace

        NSURLErrorUnsupportedURL -> MediaDownloadErrorKind.Unsupported

        else -> MediaDownloadErrorKind.Unknown
    }
}

/** Ver o KDoc da instância única no Android: aqui o motivo é a sessão de fundo, uma por processo. */
private object InstanciaDeDownload {
    var atual: MediaDownloadManager? = null
}

actual fun createMediaDownloadManager(
    config: MediaDownloadConfig,
    store: MediaDownloadStore?,
    onRenewUrl: (suspend (MediaDownloadRequest) -> String?)?,
): MediaDownloadManager {
    InstanciaDeDownload.atual?.let {
        it.updateConfig(config)
        return it
    }
    return IosMediaDownloadManager(
        initialConfig = config,
        store = store ?: PreferencesMediaDownloadStore(appPreferences()),
        onRenewUrl = onRenewUrl,
    ).also { InstanciaDeDownload.atual = it }
}

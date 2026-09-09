package br.com.codecacto.kmplib.video.download

import br.com.codecacto.kmplib.video.VideoMedia
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * **Baixar a aula para assistir sem internet** — a fila, o andamento, a pausa e a faxina.
 *
 * ```kotlin
 * val downloads = remember { createMediaDownloadManager(onRenewUrl = { vm.novaUrl(it.id) }) }
 *
 * downloads.enqueue(
 *     MediaDownloadRequest(
 *         id = aula.id,
 *         url = aula.hlsUrl,
 *         title = aula.titulo,
 *         groupId = curso.id,          // apagar o curso inteiro é removeGroup(curso.id)
 *         estimatedBytes = aula.bytes, // confere o espaço ANTES de começar
 *     ),
 * )
 *
 * // Na tela de Downloads:
 * val itens by downloads.downloads.collectAsState()
 *
 * // Tocar o que foi baixado: MESMO player, muda só a fonte.
 * val media = downloads.offlineMediaFor(aula.id) ?: VideoMedia(url = aula.hlsUrl)
 * val player = rememberVideoPlayerState(media)
 * ```
 *
 * ### Um por processo
 * [createMediaDownloadManager] devolve **sempre a mesma instância**. Não é conveniência: no Android
 * o cache de download é um `SimpleCache`, e abrir dois sobre o mesmo diretório **lança** — o
 * segundo `createMediaDownloadManager` derrubaria a tela que o chamou. Chamar de novo com outra
 * [MediaDownloadConfig] **atualiza** a configuração da instância existente.
 *
 * ### O que é da plataforma e o que é nosso
 * A transferência, a retomada e a sobrevivência ao app ser fechado são do subsistema nativo — o
 * `DownloadManager`/`DownloadService` do Media3 no Android, o `AVAssetDownloadTask` no iOS. É o
 * caminho oficial dos dois, e é o único que baixa **HLS como HLS**: guardar o `.m3u8` como arquivo
 * solto não toca em lugar nenhum, porque o manifesto é um índice de outros arquivos.
 * O que é nosso é o que o produto precisa e nenhum dos dois tem: a que curso o item pertence, até
 * quando o direito vale e o que renovar quando a URL assinada morre no meio da transferência.
 */
interface MediaDownloadManager {

    /** A configuração em vigor. Ver [updateConfig]. */
    val config: MediaDownloadConfig

    /**
     * A lista inteira, sempre atual — é o que a tela de Downloads observa.
     *
     * Ordenada por ordem de entrada na fila. Enquanto há transferência acontecendo, ela é
     * republicada ~1×/s (ver [PROGRESS_POLL_MILLIS]).
     */
    val downloads: StateFlow<List<MediaDownload>>

    /** Um item só — para o ⏸/● do `LessonRow`, que não quer a lista inteira. */
    fun observe(id: String): Flow<MediaDownload?> = downloads.map { lista -> lista.firstOrNull { it.id == id } }

    /** O estado atual de [id], ou `null` se ele não está na fila nem no disco. */
    suspend fun get(id: String): MediaDownload?

    /**
     * Põe na fila (ou atualiza a URL de quem já está lá).
     *
     * Confere o espaço antes: com [MediaDownloadRequest.estimatedBytes] preenchido e o aparelho
     * cheio, devolve [MediaDownloadOutcome.Rejected] com [MediaDownloadErrorKind.NoSpace] **sem
     * começar** — a alternativa é o aluno esperar dez minutos por um download que vai falhar.
     */
    suspend fun enqueue(request: MediaDownloadRequest): MediaDownloadOutcome

    /** Pausa [id]. A pausa é persistida: continua pausado depois de fechar e reabrir o app. */
    suspend fun pause(id: String)

    /** Retoma [id] **de onde parou** — nunca do zero. */
    suspend fun resume(id: String)

    /** Pausa tudo o que está na fila. */
    suspend fun pauseAll()

    /** Retoma tudo o que foi pausado. */
    suspend fun resumeAll()

    /** Cancela e apaga [id] (baixado ou pela metade). Idempotente. */
    suspend fun remove(id: String)

    /**
     * Apaga tudo de um [MediaDownloadRequest.groupId] — **o curso inteiro**.
     *
     * É este o mecanismo do "o aluno perdeu o acesso": estorno, reembolso, assinatura cancelada. A
     * lib oferece a alavanca; **quem decide puxá-la é o app**, porque só ele sabe o que o servidor
     * respondeu.
     *
     * @return quantos itens saíram.
     */
    suspend fun removeGroup(groupId: String): Int

    /** Apaga tudo. É o ‹Remover tudo› do cabeçalho da tela de Downloads. */
    suspend fun removeAll()

    /**
     * Apaga o que passou de [MediaDownloadRequest.expiresAtMillis].
     *
     * Chame na abertura do app e ao entrar na tela de Downloads. Ver [isMediaDownloadExpired] — e a
     * ressalva de que isto **não é DRM**.
     *
     * @return os ids que saíram.
     */
    suspend fun purgeExpired(nowMillis: Long): List<String>

    /** Quanto ocupa, quantos itens e quanto ainda cabe — o cabeçalho da tela de Downloads. */
    suspend fun storageUsage(): MediaStorageUsage

    /**
     * Troca a configuração a quente (é assim que "baixar só no Wi-Fi" liga e desliga).
     *
     * ⚠️ **No iOS a restrição de rede vale para os downloads começados DEPOIS da troca.** A
     * `URLSession` fixa `allowsCellularAccess` na criação, e invalidar a sessão para recriá-la
     * derrubaria as transferências em andamento — perder o que já foi baixado para aplicar uma
     * preferência é pior do que aplicá-la a partir do próximo item. No Android o Media3 reavalia
     * os requisitos na hora e pausa quem está no 4G.
     */
    fun updateConfig(config: MediaDownloadConfig)

    /**
     * A [VideoMedia] que toca **a cópia baixada** de [id], ou `null` se ela não existe ou não
     * terminou.
     *
     * É o que faz "tocar o baixado" ser o **mesmo** [VideoMedia] e o mesmo `VideoPlayer`, mudando
     * só a fonte:
     *
     * ```kotlin
     * val media = downloads.offlineMediaFor(aula.id, title = aula.titulo)
     *     ?: VideoMedia(url = aula.hlsUrl, title = aula.titulo)
     * ```
     *
     * Item [MediaDownloadStatus.Expired] devolve `null` de propósito: o direito caiu, e o vídeo não
     * deve tocar mesmo com os bytes ainda no disco.
     */
    suspend fun offlineMediaFor(
        id: String,
        title: String? = null,
        artist: String? = null,
        startPositionMillis: Long = 0L,
    ): VideoMedia?
}

/** O que aconteceu com um [MediaDownloadManager.enqueue]. */
sealed interface MediaDownloadOutcome {
    /** Entrou na fila. */
    data object Enqueued : MediaDownloadOutcome

    /** Já estava baixado e válido — nada a fazer. */
    data object AlreadyDownloaded : MediaDownloadOutcome

    /**
     * Recusado **antes de começar**. [message] é a frase para a tela.
     *
     * @param missingBytes quanto falta de espaço, quando [kind] é [MediaDownloadErrorKind.NoSpace].
     */
    data class Rejected(
        val kind: MediaDownloadErrorKind,
        val message: String,
        val missingBytes: Long = 0L,
    ) : MediaDownloadOutcome
}

/**
 * Como o gerenciador se comporta.
 *
 * @param wifiOnly só baixa em rede não tarifada. É o ☐ "Baixar só no Wi-Fi" das Configurações.
 *   Ver a ressalva de plataforma em [MediaDownloadManager.updateConfig].
 * @param maxParallelDownloads quantos itens transferem ao mesmo tempo. **2**, e não 5: numa conexão
 *   doméstica, cinco downloads simultâneos terminam todos juntos no fim, enquanto dois entregam a
 *   primeira aula em 40% do tempo — e é a primeira aula que o aluno vai assistir.
 * @param reservedSpaceBytes o espaço do aparelho que a lib nunca ocupa. Ver
 *   [DEFAULT_RESERVED_SPACE_BYTES].
 * @param texts as frases de falha.
 */
data class MediaDownloadConfig(
    val wifiOnly: Boolean = false,
    val maxParallelDownloads: Int = 2,
    val reservedSpaceBytes: Long = DEFAULT_RESERVED_SPACE_BYTES,
    val texts: MediaDownloadTexts = MediaDownloadTexts(),
)

/**
 * O gerenciador de downloads do processo.
 *
 * @param config ver [MediaDownloadConfig]. Chamar de novo com outra config **atualiza** a instância
 *   existente (ver [MediaDownloadManager]).
 * @param store onde os metadados do produto ficam. Default: [PreferencesMediaDownloadStore] sobre
 *   as preferências do app.
 * @param onRenewUrl **a renovação da URL assinada, tratada como caso normal e não como erro.**
 *   Um download de aula leva minutos ou dezenas de minutos, e um token de 15 minutos vence no meio
 *   — não é exceção, é o comportamento esperado. Quando a falha é
 *   [MediaDownloadErrorKind.Expired], o gerenciador pede aqui uma URL nova e **retoma de onde
 *   parou**; devolver `null` (ou lançar) deixa a falha na tela. Vale
 *   [MAX_DOWNLOAD_URL_RENEWALS] renovações por item.
 *   ⚠️ No Android a retomada é literal (os pedaços já baixados ficam, porque a chave do cache é o
 *   [MediaDownloadRequest.id] e não a URL). No iOS, ver a ressalva no CHANGELOG: quando o pacote
 *   parcial não puder ser reaproveitado, o item recomeça — o que se resolve do lado do servidor,
 *   assinando a URL de **download** com validade longa.
 */
expect fun createMediaDownloadManager(
    config: MediaDownloadConfig = MediaDownloadConfig(),
    store: MediaDownloadStore? = null,
    onRenewUrl: (suspend (MediaDownloadRequest) -> String?)? = null,
): MediaDownloadManager

/**
 * 1 s. O passo com que o andamento é relido enquanto há transferência.
 *
 * Nem o Media3 nem o `AVAssetDownloadTask` **empurram** bytes baixados de forma contínua: o
 * primeiro publica um `Download` só quando o estado muda, e o progresso se lê de
 * `getCurrentDownloads()`. Um segundo é o passo que a própria notificação de download do Media3
 * usa; mais rápido que isso é recomposição à toa numa lista inteira.
 */
const val PROGRESS_POLL_MILLIS: Long = 1_000L

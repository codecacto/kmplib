package br.com.codecacto.kmplib.video.feed

import android.content.Context
import android.net.ConnectivityManager
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.preload.DefaultPreloadManager
import androidx.media3.exoplayer.source.preload.TargetPreloadStatusControl
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.video.VideoPlayerHolder
import br.com.codecacto.kmplib.video.VideoStreamKind
import br.com.codecacto.kmplib.video.videoStreamKindOf

/**
 * O pré-carregador do Android: **`DefaultPreloadManager` da Media3** — o caminho oficial para feed
 * de vídeo curto, e o mesmo que o demo `ShortFormState` do Google usa.
 *
 * Não é "baixar na mão numa thread": o `DefaultPreloadManager` prepara a **fonte de mídia** (abre o
 * manifesto, seleciona faixa, carrega o intervalo pedido) na thread de reprodução da Media3 e, ao
 * ser entregue ao player, ela já está pronta — sem reabrir nada. Um `HttpURLConnection` puxando
 * bytes não produz nada disso: em HLS baixaria um índice de 2 KB.
 *
 * A escada de quanto pré-carregar é [feedPreloadTargetFor], em `commonMain` e coberta por teste.
 */
@OptIn(UnstableApi::class)
internal class Media3FeedPreloader(
    private val context: Context,
    private val config: FeedVideoConfig,
) : FeedPreloader {

    /** A posição de cada item na tela, na última atualização. É o `rankingData` do manager. */
    private var indiceAtual: Int = -1
    private var pausado: Boolean = true

    /** URL → o `MediaItem` entregue ao manager. A identidade dele é a chave de tudo lá dentro. */
    private val itensPorUrl = LinkedHashMap<String, MediaItem>()

    /** URL → distância do que toca agora. Lido pela [Escada] a cada `invalidate()`. */
    private val distancias = HashMap<String, Int>()

    /** Posição no manager → URL. O `rankingData` é um `Int`, então a ponte é esta. */
    private val urlPorRanking = HashMap<Int, String>()

    private val manager: DefaultPreloadManager? by lazy { criarManager() }

    override fun update(items: List<FeedPreloadItem>, currentIndex: Int, paused: Boolean) {
        val gerente = manager ?: return
        indiceAtual = currentIndex
        pausado = paused || !preloadPermitido()

        val urlsVivas = items.map { it.url }.toSet()
        // Item que saiu da lista sai do manager: senão a fila cresce com a rolagem e o pré-carregamento
        // passaria a disputar banda em nome de posts que nem estão mais compostos.
        itensPorUrl.keys.toList()
            .filter { it !in urlsVivas }
            .forEach { url -> itensPorUrl.remove(url)?.let { gerente.remove(it) }; distancias.remove(url) }

        items.forEachIndexed { indice, item ->
            distancias[item.url] = if (currentIndex < 0) Int.MAX_VALUE else indice - currentIndex
            urlPorRanking[indice] = item.url
            if (itensPorUrl.containsKey(item.url)) return@forEachIndexed
            val mediaItem = mediaItemDe(item, indice)
            itensPorUrl[item.url] = mediaItem
            gerente.add(mediaItem, indice)
        }

        gerente.setCurrentPlayingIndex(if (currentIndex < 0) C_INDICE_NENHUM else currentIndex)
        // `invalidate()` é o gatilho: ele reavalia TODOS os itens pela [Escada]. É por aqui que o
        // cancelamento acontece — com `pausado`, a escada devolve "não pré-carregado" para todos, e
        // o manager solta o que estava buscando.
        gerente.invalidate()
    }

    override fun reset() {
        pausado = true
        distancias.clear()
        urlPorRanking.clear()
        itensPorUrl.clear()
        runCatching { manager?.reset() }
    }

    override fun release() {
        reset()
        runCatching { manager?.release() }
    }

    /**
     * A fonte já preparada para [url], ou `null` se o manager não a tem.
     *
     * É o que faz o pré-carregamento valer: o player adota a fonte **no estado em que ela está**,
     * em vez de abrir o manifesto de novo.
     */
    fun mediaSourceFor(url: String): MediaSource? {
        val gerente = manager ?: return null
        val item = itensPorUrl[url] ?: return null
        return runCatching { gerente.getMediaSource(item) }.getOrNull()
    }

    /**
     * Um `ExoPlayer` do pool construído **pelo mesmo builder** do manager.
     *
     * É exigência de arquitetura, não conveniência: assim o player e o pré-carregamento dividem a
     * thread de reprodução, o `LoadControl` e o cache. Players criados por fora disputariam recursos
     * com o manager sem que ele soubesse.
     */
    fun buildPlayer(builder: ExoPlayer.Builder): ExoPlayer? =
        builderRef?.let { runCatching { it.buildExoPlayer(builder) }.getOrNull() }

    private var builderRef: DefaultPreloadManager.Builder? = null

    private fun criarManager(): DefaultPreloadManager? = runCatching {
        val builder = DefaultPreloadManager.Builder(context.applicationContext, Escada())
            .setCache(FeedVideoCache.cache(context, config.diskCacheBytes))
            .setLoadControl(feedLoadControl())
        builderRef = builder
        builder.build()
    }.onFailure {
        AppLogger.w(FEED_TAG, "Pré-carregamento de feed indisponível: ${it.message}")
    }.getOrNull()

    private fun mediaItemDe(item: FeedPreloadItem, indice: Int): MediaItem {
        val forma = if (item.kind == VideoStreamKind.Auto) videoStreamKindOf(item.url) else item.kind
        return MediaItem.Builder()
            .setUri(item.url)
            // A mesma chave do player: sem ela, o pedaço que o pré-carregamento gravou ficaria
            // endereçado pela URL assinada e o player não o encontraria (baixaria tudo de novo).
            .setCustomCacheKey(item.cacheKey)
            .apply { if (forma == VideoStreamKind.Hls) setMimeType(MimeTypes.APPLICATION_M3U8) }
            .build()
    }

    /**
     * A tradução da escada comum para o vocabulário da Media3.
     *
     * O "não pré-carregue" é `PRELOAD_STATUS_NOT_PRELOADED`, e **não `null`**: a interface é Java e
     * declara o retorno como não-nulo, então devolver `null` daqui compilaria no Kotlin e explodiria
     * na thread de reprodução da Media3. A constante existe exatamente para este caso.
     */
    private inner class Escada :
        TargetPreloadStatusControl<Int, DefaultPreloadManager.PreloadStatus> {

        override fun getTargetPreloadStatus(rankingData: Int): DefaultPreloadManager.PreloadStatus {
            val url = urlPorRanking[rankingData] ?: return NAO_PRE_CARREGAR
            val distancia = distancias[url] ?: return NAO_PRE_CARREGAR
            return when (val alvo = feedPreloadTargetFor(distancia, pausado)) {
                FeedPreloadTarget.None -> NAO_PRE_CARREGAR
                is FeedPreloadTarget.Loaded ->
                    DefaultPreloadManager.PreloadStatus.specifiedRangeLoaded(alvo.millis)
                is FeedPreloadTarget.Cached ->
                    DefaultPreloadManager.PreloadStatus.specifiedRangeCached(alvo.millis)
            }
        }
    }

    /**
     * **Rede medida e Data Saver desligam o PRÉ-CARREGAMENTO** — nunca a reprodução.
     *
     * A distinção é a regra: "só toca vídeo no Wi-Fi" é padrão legado que nem o Instagram nem o
     * TikTok têm, e num feed ele quebra o produto. O que se corta no plano de dados é o que a pessoa
     * **ainda não pediu** — o vídeo de baixo, que ela talvez pule.
     *
     * `isActiveNetworkMetered` cobre os dois casos que importam: dados móveis e Wi-Fi que o dono
     * marcou como limitado. O Data Saver entra por
     * `getRestrictBackgroundStatus` — quem o ligou disse ao sistema, com todas as letras, para não
     * gastar com o que não foi pedido.
     */
    private fun preloadPermitido(): Boolean {
        if (!config.preloadEnabled) return false
        if (config.preloadOnMeteredNetwork) return true
        val agora = nowMillis()
        if (agora - medidaEm < MEDIDA_VALIDA_POR_MILLIS) return !ultimaMedida
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val medida = runCatching {
            cm.isActiveNetworkMetered ||
                cm.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        }.getOrDefault(false)
        ultimaMedida = medida
        medidaEm = agora
        return !medida
    }

    private var ultimaMedida = false
    private var medidaEm = 0L

    private fun nowMillis(): Long = android.os.SystemClock.elapsedRealtime()

    private companion object {
        /**
         * A consulta ao `ConnectivityManager` é um binder call, e `update` roda a cada relatório de
         * rolagem. 10 s é curto para acompanhar uma troca de Wi-Fi e longo para não pesar.
         */
        const val MEDIDA_VALIDA_POR_MILLIS = 10_000L

        /** O manager entende "ninguém tocando" como um índice fora da lista. */
        const val C_INDICE_NENHUM = -1

        /** O "não pré-carregue nada deste item" da Media3. Ver [Media3FeedPreloader.Escada]. */
        val NAO_PRE_CARREGAR: DefaultPreloadManager.PreloadStatus =
            DefaultPreloadManager.PreloadStatus.PRELOAD_STATUS_NOT_PRELOADED
    }
}

internal actual fun createFeedPreloader(config: FeedVideoConfig): FeedPreloader {
    val context = VideoPlayerHolder.getContext() ?: return NoFeedPreloader
    if (!config.preloadEnabled) return NoFeedPreloader
    return Media3FeedPreloader(context, config)
}

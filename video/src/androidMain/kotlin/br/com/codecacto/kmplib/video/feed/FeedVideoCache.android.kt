package br.com.codecacto.kmplib.video.feed

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.video.Media3Cache
import java.io.File

/**
 * O **cache de disco do vídeo de feed** — um por processo.
 *
 * ### Por que ele existe (não é preparação para o pré-carregamento; é conserto)
 * O vídeo de feed toca em **laço** (`REPEAT_MODE_ONE`) com buffer curto (15 s). Sem cache, um vídeo
 * de 60 s **rebaixa da rede a cada volta**, para sempre, enquanto o post estiver na tela: é plano de
 * dados e bateria indo embora em algo que já foi baixado um minuto atrás. Com o cache, a segunda
 * volta e as seguintes saem do disco.
 *
 * ### Singleton por imposição da Media3, não por gosto
 * Um `SimpleCache` **tranca** o diretório (`SimpleCache.isCacheFolderLocked`): abrir uma segunda
 * instância sobre a mesma pasta lança. Como o pool cria e destrói players o tempo todo, a instância
 * tem de viver acima deles.
 *
 * ### Três decisões que separam este cache do de download
 * | | feed (aqui) | download (`Media3Cache`) |
 * |---|---|---|
 * | onde | `cacheDir` — **descartável** | `filesDir` — durável |
 * | expurgo | `LeastRecentlyUsedCacheEvictor` (teto) | `NoOpCacheEvictor` (só o usuário apaga) |
 * | escrita | **liga**da: assistir preenche | desligada (`setCacheWriteDataSinkFactory(null)`) |
 *
 * São opostos de propósito. Uma aula baixada é conteúdo que o usuário **pediu**, e sumir dela é
 * defeito; um vídeo de feed é sobra de rede, e mantê-la ocupando o aparelho é que seria o defeito.
 * Misturar os dois no mesmo diretório daria o pior dos dois: ou o evictor apagaria a aula do aluno,
 * ou o feed encheria o disco sem nunca esvaziar.
 *
 * O `DatabaseProvider` **é o mesmo** do download, e isso é seguro: cada `SimpleCache` recebe um uid
 * próprio e escreve nas suas tabelas. Abrir um segundo banco para a mesma finalidade seria custo sem
 * contrapartida.
 */
@OptIn(UnstableApi::class)
internal object FeedVideoCache {

    /** `cacheDir/kmplib_feed_video`. Ver a tabela no KDoc da classe. */
    const val DIRECTORY: String = "kmplib_feed_video"

    private var cacheRef: SimpleCache? = null
    private var tetoAplicado: Long = 0L

    /**
     * O cache, criado na primeira chamada com o teto de [maxBytes].
     *
     * ⚠️ **O primeiro teto vence.** Um `SimpleCache` aberto não muda de tamanho — o evictor é
     * imutável —, e fechá-lo para reabrir maior derrubaria os players que estão lendo dele. Dois
     * feeds na mesma sessão pedindo tetos diferentes é configuração incoerente do app, e por isso a
     * lib **avisa no log** em vez de escolher em silêncio.
     */
    @Synchronized
    fun cache(context: Context, maxBytes: Long): SimpleCache {
        cacheRef?.let { existente ->
            if (maxBytes != tetoAplicado) {
                AppLogger.w(
                    FEED_TAG,
                    "Cache de vídeo de feed já aberto com teto de $tetoAplicado bytes; " +
                        "o pedido de $maxBytes bytes foi ignorado (o primeiro feed do processo decide).",
                )
            }
            return existente
        }
        val app = context.applicationContext
        val teto = maxBytes.coerceAtLeast(MIN_CACHE_BYTES)
        val cache = SimpleCache(
            File(app.cacheDir, DIRECTORY),
            LeastRecentlyUsedCacheEvictor(teto),
            Media3Cache.databaseProvider(app),
        )
        cacheRef = cache
        tetoAplicado = teto
        return cache
    }

    /**
     * A origem de dados dos players do feed: **lê do cache e escreve nele**.
     *
     * `FLAG_IGNORE_CACHE_ON_ERROR` faz um pedaço corrompido cair para a rede em vez de derrubar a
     * reprodução — o mesmo que o player de aula faz com o cache de download.
     */
    fun dataSourceFactory(context: Context, maxBytes: Long): DataSource.Factory {
        val app = context.applicationContext
        return CacheDataSource.Factory()
            .setCache(cache(app, maxBytes))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(app, httpDataSourceFactory()))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Segue redirecionamento entre hosts: URL assinada de CDN quase sempre redireciona. */
    fun httpDataSourceFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)

    /**
     * Teto mínimo. Abaixo de 16 MB o evictor passaria a apagar pedaços do vídeo que está tocando
     * agora — um cache que se atropela é pior que nenhum.
     */
    private const val MIN_CACHE_BYTES: Long = 16L * 1024 * 1024
}

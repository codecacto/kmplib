package br.com.codecacto.kmplib.video

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * A **infraestrutura de cache Media3** do vídeo — um por processo.
 *
 * ### Por que isto mora no `kmplib-video`, e não no `kmplib-video-download` (2.212.0)
 * Até a 2.211.0 este objeto se chamava `Media3Downloads` e morava junto do serviço de download. O
 * player dependia dele nas duas pontas — o `VideoPlayerState` lê a cópia baixada por aqui, e o
 * cache do feed reaproveita o [databaseProvider] —, então o módulo do player não podia existir sem
 * o do download, e **todo app que só toca vídeo herdava o foreground service `dataSync`** e as duas
 * permissões dele. A Play barra o bundle num formulário que o app não tem como responder.
 *
 * O recorte certo foi **dentro** do objeto: o que é cache (diretório, banco, `SimpleCache`, fábrica
 * de leitura) fica aqui; o `DownloadManager`, a notificação e os requisitos de rede foram para o
 * `kmplib-video-download`, que constrói **em cima** deste. A dependência é de mão única:
 * `kmplib-video-download → kmplib-video`.
 *
 * ### Singleton por imposição da Media3
 * Um `SimpleCache` **tranca** o diretório (`SimpleCache.isCacheFolderLocked`) e abrir um segundo
 * sobre a mesma pasta lança. O player, o `DownloadService` e o `MediaDownloadManager` leem daqui a
 * **mesma** instância — é isso que faz o serviço em primeiro plano continuar exatamente a fila que
 * a tela enfileirou, e o player achar o que foi baixado.
 *
 * ⚠️ Público só porque o `kmplib-video-download` é outro módulo (e `internal` do Kotlin não cruza
 * módulo). Não é API de app: exige opt-in em [KmpLibVideoInternalApi].
 */
@KmpLibVideoInternalApi
@OptIn(UnstableApi::class)
object Media3Cache {

    /**
     * `filesDir/kmplib_media_downloads` — armazenamento **interno privado e durável**.
     *
     * Não é o `cacheDir`, pela mesma razão do `BlobStore` e do cache de PDF: o Android apaga o
     * cache sob pressão de espaço, que é exatamente o momento em que a aula baixada some sem
     * ninguém saber. Uma aula baixada é conteúdo que o usuário **pediu**, não sobra de rede.
     *
     * O nome da pasta NÃO mudou com o split: é o que faz o aparelho que baixou numa versão anterior
     * continuar achando as aulas depois de atualizar.
     */
    const val DIRECTORY: String = "kmplib_media_downloads"

    private var cacheRef: SimpleCache? = null
    private var databaseRef: DatabaseProvider? = null

    fun directory(context: Context): File = File(context.applicationContext.filesDir, DIRECTORY)

    /**
     * `true` se já existe algo baixado no disco.
     *
     * Serve para o player **não pagar a indexação do cache** quando o app nunca baixou nada: abrir
     * um `SimpleCache` percorre o diretório, e fazer isso na composição de um app que só faz
     * streaming é custo sem contrapartida.
     */
    fun hasDownloadsOnDisk(context: Context): Boolean =
        directory(context).let { it.isDirectory && it.list()?.isNotEmpty() == true }

    /** O banco de índice da Media3, compartilhado pelo cache de download e pelo do feed. */
    @Synchronized
    fun databaseProvider(context: Context): DatabaseProvider =
        databaseRef ?: StandaloneDatabaseProvider(context.applicationContext).also { databaseRef = it }

    /**
     * O cache dos downloads.
     *
     * `NoOpCacheEvictor` é deliberado: **nada aqui é despejado por conta própria**. Um evictor por
     * tamanho (`LeastRecentlyUsedCacheEvictor`) apagaria a aula que o aluno baixou para o voo
     * porque outra aula entrou depois — e a tela de Downloads continuaria listando as duas. Quem
     * apaga é o usuário, pelo `MediaDownloadManager.remove`, ou a regra de expiração do app.
     */
    @Synchronized
    fun cache(context: Context): SimpleCache = cacheRef ?: SimpleCache(
        directory(context),
        NoOpCacheEvictor(),
        databaseProvider(context),
    ).also { cacheRef = it }

    /** A fábrica HTTP de quem baixa. Segue os redirecionamentos entre hosts (CDN assinado). */
    fun httpDataSourceFactory(): DefaultHttpDataSource.Factory =
        DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)

    /**
     * A fábrica de origem de dados **para tocar** o que já está baixado.
     *
     * ⚠️ **Somente leitura, e isto não é detalhe.** Sem `setCacheWriteDataSinkFactory(null)`, tudo
     * o que o aluno assiste em streaming é **gravado** neste cache — que tem `NoOpCacheEvictor` e
     * portanto nunca esvazia. O aparelho enche em silêncio com vídeo que ninguém pediu para baixar,
     * e a tela de Downloads mostra "1,4 GB" enquanto o app ocupa 12 GB.
     *
     * `FLAG_IGNORE_CACHE_ON_ERROR` faz um pedaço corrompido cair para a rede em vez de derrubar a
     * reprodução.
     */
    fun playbackDataSourceFactory(context: Context): DataSource.Factory {
        val app = context.applicationContext
        return CacheDataSource.Factory()
            .setCache(cache(app))
            .setUpstreamDataSourceFactory(DefaultDataSource.Factory(app, httpDataSourceFactory()))
            .setCacheWriteDataSinkFactory(null)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }
}

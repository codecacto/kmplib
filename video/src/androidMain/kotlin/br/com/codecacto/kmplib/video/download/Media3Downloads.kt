package br.com.codecacto.kmplib.video.download

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
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.scheduler.Requirements
import java.io.File
import java.util.concurrent.Executors

/**
 * O núcleo Media3 do download, **um por processo**.
 *
 * Precisa ser singleton por imposição da própria biblioteca: um `SimpleCache` **tranca** o
 * diretório (`SimpleCache.isCacheFolderLocked`) e abrir um segundo sobre a mesma pasta lança. O
 * `DownloadService` e o [MediaDownloadManager] leem daqui a **mesma** instância — é isso que faz o
 * serviço em primeiro plano continuar exatamente a fila que a tela enfileirou.
 */
@OptIn(UnstableApi::class)
internal object Media3Downloads {

    /**
     * `filesDir/kmplib_media_downloads` — armazenamento **interno privado e durável**.
     *
     * Não é o `cacheDir`, pela mesma razão do `BlobStore` e do cache de PDF: o Android apaga o
     * cache sob pressão de espaço, que é exatamente o momento em que a aula baixada some sem
     * ninguém saber. Uma aula baixada é conteúdo que o usuário **pediu**, não sobra de rede.
     */
    const val DIRECTORY: String = "kmplib_media_downloads"

    /** O canal de notificação do download em primeiro plano. */
    const val NOTIFICATION_CHANNEL_ID: String = "kmplib_downloads"

    /** O id da notificação persistente do [KmplibDownloadService]. */
    const val FOREGROUND_NOTIFICATION_ID: Int = 0xCAC70

    /** O id do job do `PlatformScheduler` — precisa ser único dentro do app. */
    const val SCHEDULER_JOB_ID: Int = 0xCAC71

    private var cacheRef: SimpleCache? = null
    private var databaseRef: DatabaseProvider? = null
    private var managerRef: DownloadManager? = null
    private var notificationHelperRef: DownloadNotificationHelper? = null

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

    @Synchronized
    fun databaseProvider(context: Context): DatabaseProvider =
        databaseRef ?: StandaloneDatabaseProvider(context.applicationContext).also { databaseRef = it }

    /**
     * O cache dos downloads.
     *
     * `NoOpCacheEvictor` é deliberado: **nada aqui é despejado por conta própria**. Um evictor por
     * tamanho (`LeastRecentlyUsedCacheEvictor`) apagaria a aula que o aluno baixou para o voo
     * porque outra aula entrou depois — e a tela de Downloads continuaria listando as duas. Quem
     * apaga é o usuário, por [MediaDownloadManager.remove], ou a regra de expiração do app.
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

    @Synchronized
    fun downloadManager(context: Context): DownloadManager {
        managerRef?.let { return it }
        val app = context.applicationContext
        return DownloadManager(
            app,
            databaseProvider(app),
            cache(app),
            httpDataSourceFactory(),
            // Pool próprio: com `Runnable::run` (o exemplo mais curto da documentação) o download
            // roda na thread que o chamou e a fila deixa de ser paralela.
            Executors.newFixedThreadPool(MAX_DOWNLOAD_THREADS),
        ).also { managerRef = it }
    }

    @Synchronized
    fun notificationHelper(context: Context): DownloadNotificationHelper =
        notificationHelperRef ?: DownloadNotificationHelper(
            context.applicationContext,
            NOTIFICATION_CHANNEL_ID,
        ).also { notificationHelperRef = it }

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

    /** Traduz "só no Wi-Fi" para os requisitos do Media3. */
    fun requirementsFor(wifiOnly: Boolean): Requirements = Requirements(
        // DEVICE_STORAGE_NOT_LOW junto de propósito: o Android já sinaliza "pouco espaço" antes de
        // acabar, e insistir em baixar nesse estado é o que faz o aparelho parar de tirar foto.
        (if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK) or
            Requirements.DEVICE_STORAGE_NOT_LOW,
    )

    /** O motivo de parada que a lib usa para dizer "quem pausou foi o usuário". */
    const val STOP_REASON_USER_PAUSED: Int = 1

    private const val MAX_DOWNLOAD_THREADS = 3
}

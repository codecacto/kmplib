package br.com.codecacto.kmplib.video.download

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.scheduler.Requirements
import br.com.codecacto.kmplib.video.KmpLibVideoInternalApi
import br.com.codecacto.kmplib.video.Media3Cache
import java.util.concurrent.Executors

/**
 * O núcleo Media3 **do download** — um por processo — construído em cima do [Media3Cache].
 *
 * Desde a 2.212.0 este objeto guarda só o que é de BAIXAR: o `DownloadManager`, a notificação do
 * serviço em primeiro plano e a tradução de "só no Wi-Fi" em requisitos. O cache (diretório, banco,
 * `SimpleCache`, a fábrica de leitura do player) é do `kmplib-video`, porque o player precisa dele
 * para tocar o baixado e o feed reaproveita o banco — e o player não pode arrastar um foreground
 * service para quem só toca vídeo. Ver o KDoc de [Media3Cache].
 *
 * O `DownloadManager` recebe **a mesma** instância de `SimpleCache` que o player lê
 * ([Media3Cache.cache]): é isso que faz a aula baixada aqui abrir sem rede lá.
 */
@OptIn(UnstableApi::class, KmpLibVideoInternalApi::class)
internal object Media3Downloads {

    /** O canal de notificação do download em primeiro plano. */
    const val NOTIFICATION_CHANNEL_ID: String = "kmplib_downloads"

    /** O id da notificação persistente do [KmplibDownloadService]. */
    const val FOREGROUND_NOTIFICATION_ID: Int = 0xCAC70

    /** O id do job do `PlatformScheduler` — precisa ser único dentro do app. */
    const val SCHEDULER_JOB_ID: Int = 0xCAC71

    /** O motivo de parada que a lib usa para dizer "quem pausou foi o usuário". */
    const val STOP_REASON_USER_PAUSED: Int = 1

    private const val MAX_DOWNLOAD_THREADS = 3

    private var managerRef: DownloadManager? = null
    private var notificationHelperRef: DownloadNotificationHelper? = null

    @Synchronized
    fun downloadManager(context: Context): DownloadManager {
        managerRef?.let { return it }
        val app = context.applicationContext
        return DownloadManager(
            app,
            Media3Cache.databaseProvider(app),
            Media3Cache.cache(app),
            Media3Cache.httpDataSourceFactory(),
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

    /** Traduz "só no Wi-Fi" para os requisitos do Media3. */
    fun requirementsFor(wifiOnly: Boolean): Requirements = Requirements(
        // DEVICE_STORAGE_NOT_LOW junto de propósito: o Android já sinaliza "pouco espaço" antes de
        // acabar, e insistir em baixar nesse estado é o que faz o aparelho parar de tirar foto.
        (if (wifiOnly) Requirements.NETWORK_UNMETERED else Requirements.NETWORK) or
            Requirements.DEVICE_STORAGE_NOT_LOW,
    )
}

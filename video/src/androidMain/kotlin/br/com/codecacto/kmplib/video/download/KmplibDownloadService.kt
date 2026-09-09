package br.com.codecacto.kmplib.video.download

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import br.com.codecacto.kmplib.video.R

/**
 * O serviço em **primeiro plano** que continua os downloads quando a tela sai da frente.
 *
 * É ele que atende o requisito de o download **não recomeçar do zero por causa de uma troca de
 * tela** — e de sobreviver ao app ser fechado. Sem serviço, a transferência viveria no processo da
 * Activity: o Android o mata assim que o usuário abre outro app, e o pior é que isso **não dá
 * erro**; o download simplesmente não anda, e o aluno volta uma hora depois e encontra 12%.
 *
 * O `PlatformScheduler` (JobScheduler) é o que devolve a fila ao ar quando o requisito volta a ser
 * atendido — o Wi-Fi que caiu, o aparelho que estava com pouco espaço — e depois de um reinício.
 *
 * ⚠️ **O app não declara nada.** O `<service>`, o `<receiver>` do agendador e as permissões de
 * primeiro plano vêm no `AndroidManifest.xml` deste módulo. Foi a lição da 2.175.0: elemento de
 * manifesto mora no módulo que tem a classe, senão quem consome o artefato granular recebe uma
 * biblioteca que compila e falha em runtime.
 */
@OptIn(UnstableApi::class)
class KmplibDownloadService : DownloadService(
    Media3Downloads.FOREGROUND_NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    Media3Downloads.NOTIFICATION_CHANNEL_ID,
    R.string.kmplib_download_channel_name,
    R.string.kmplib_download_channel_description,
) {

    override fun getDownloadManager(): DownloadManager {
        val manager = Media3Downloads.downloadManager(this)
        // O canal precisa existir antes da primeira notificação; criá-lo aqui (e não no
        // Application do app) é o que mantém o consumidor sem trabalho.
        NotificationUtil.createNotificationChannel(
            this,
            Media3Downloads.NOTIFICATION_CHANNEL_ID,
            R.string.kmplib_download_channel_name,
            R.string.kmplib_download_channel_description,
            NotificationUtil.IMPORTANCE_LOW,
        )
        return manager
    }

    /**
     * `PlatformScheduler` (JobScheduler), e não `WorkManagerScheduler`: ele é da própria Media3,
     * não arrasta o WorkManager para dentro de todo app que baixa um vídeo e cobre exatamente o
     * que precisamos — "volte quando houver rede não tarifada".
     */
    override fun getScheduler(): Scheduler = PlatformScheduler(this, Media3Downloads.SCHEDULER_JOB_ID)

    override fun getForegroundNotification(
        downloads: List<Download>,
        notMetRequirements: Int,
    ): Notification = Media3Downloads.notificationHelper(this).buildProgressNotification(
        /* context = */ this,
        /* smallIcon = */ android.R.drawable.stat_sys_download,
        /* contentIntent = */ null,
        /* message = */ null,
        /* downloads = */ downloads,
        /* notMetRequirements = */ notMetRequirements,
    )
}

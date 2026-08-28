package br.com.codecacto.kmplib.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.com.codecacto.kmplib.core.util.AppLogger

/**
 * Monta e exibe a notificação — **com os botões de ação**, quando o agendamento os declara.
 *
 * Ponto único de exibição: o alarme que dispara ([NotificationReceiver]), o disparo perdido que
 * volta depois do reboot e o `showNotificationNow` do app passam todos por aqui, então a notificação
 * é sempre a mesma coisa (mesmos botões, mesmo `contentIntent`).
 */
internal object NotificationPresenter {

    private const val TAG = "NotificationPresenter"

    /** Esquema da URI que torna cada `PendingIntent` de ação distinto — ver [actionPendingIntent]. */
    private const val ACTION_URI_SCHEME = "kmplib"

    /**
     * @param fixa `true` = notificação PERSISTENTE (2.144.0): não sai ao deslizar nem ao ser
     *   tocada, e só desaparece com `cancel`. Ver o KDoc de `NotificationScheduler
     *   .showOngoingNotification`.
     */
    fun show(ctx: Context, item: ScheduledNotification, fixa: Boolean = false) {
        val channelId = item.channelId ?: AndroidNotificationScheduler.DEFAULT_CHANNEL_ID
        val builder = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(item.title)
            .setContentText(item.body)
            .setContentIntent(contentPendingIntent(ctx, item.id, item.data))
            .setPriority(
                if (channelId == AndroidNotificationScheduler.CRITICAL_CHANNEL_ID) {
                    NotificationCompat.PRIORITY_HIGH
                } else {
                    NotificationCompat.PRIORITY_DEFAULT
                },
            )
            // ⚠️ Os dois JUNTOS, e é o par que importa. `setOngoing` sozinho ainda some quando a
            // pessoa toca na notificação, porque quem a remove nesse caso é o `autoCancel` — e o
            // sintoma seria a faixa desaparecer exatamente para quem a usou.
            .setOngoing(fixa)
            .setAutoCancel(!fixa)

        NotificationActionRules.distinctActions(item.actions).forEach { action ->
            builder.addAction(
                // Ícone 0: o Android não exibe ícone de ação desde a 7.0, e a lib não conhece os
                // drawables do app consumidor. O rótulo é o que o usuário lê.
                NotificationCompat.Action.Builder(0, action.title, actionPendingIntent(ctx, item, action))
                    .build(),
            )
        }

        try {
            NotificationManagerCompat.from(ctx).notify(item.id, builder.build())
            AppLogger.d(TAG, "Notificação exibida: id=${item.id}, ações=${item.actions.size}")
        } catch (e: SecurityException) {
            AppLogger.e(TAG, "Sem permissão para exibir notificação", e)
        }
    }

    /** Dispensa a notificação da bandeja (o que a lib faz assim que uma ação é tocada). */
    fun dismiss(ctx: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(ctx).cancel(id) }
            .onFailure { AppLogger.w(TAG, "Falha ao dispensar notificação id=$id: ${it.message}") }
    }

    /**
     * `PendingIntent` de um botão.
     *
     * **A URI não é decorativa.** Dois `PendingIntent` são considerados o mesmo quando `requestCode`,
     * componente e `Intent.filterEquals` coincidem — e `filterEquals` **ignora os extras**. Sem uma
     * `data` distinta por ação, "Marcar como tomada" e "Adiar 30 min" da mesma notificação virariam o
     * mesmo `PendingIntent`, e o segundo botão executaria a ação do primeiro. É a pegadinha clássica
     * da API, e a `Uri` única é a forma recomendada de resolvê-la.
     */
    private fun actionPendingIntent(
        ctx: Context,
        item: ScheduledNotification,
        action: NotificationAction,
    ): PendingIntent {
        val intent = Intent(ctx, NotificationActionReceiver::class.java).apply {
            data = Uri.parse("$ACTION_URI_SCHEME://notification-action/${item.id}/${Uri.encode(action.id)}")
            putExtra(AndroidNotificationScheduler.EXTRA_NOTIFICATION_ID, item.id)
            putExtra(NotificationActionReceiver.EXTRA_ACTION_ID, action.id)
            putExtra(NotificationAlarms.EXTRA_PAYLOAD, NotificationAlarms.encode(item))
        }
        return PendingIntent.getBroadcast(
            ctx,
            requestCode(item.id, action.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Código de requisição estável e distinto por (notificação, ação). */
    private fun requestCode(notificationId: Int, actionId: String): Int =
        (notificationId * 31 + actionId.hashCode()) and 0x7FFFFFFF

    fun contentPendingIntent(
        ctx: Context,
        id: Int,
        data: Map<String, String>,
    ): PendingIntent? {
        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName) ?: return null
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        launchIntent.putExtra(AndroidNotificationScheduler.EXTRA_NOTIFICATION_ID, id)
        data.forEach { (key, value) -> launchIntent.putExtra(key, value) }
        return PendingIntent.getActivity(
            ctx,
            id,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

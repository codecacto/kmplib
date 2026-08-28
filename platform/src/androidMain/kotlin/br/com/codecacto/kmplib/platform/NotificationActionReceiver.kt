package br.com.codecacto.kmplib.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Recebe o toque nos **botões** de uma notificação local.
 *
 * ### Por que um `BroadcastReceiver` e não uma `Activity`
 * O valor da ação está justamente em **não abrir o app**: a pessoa toca "Marcar como tomada" na
 * notificação e volta ao que estava fazendo. Um `PendingIntent` de `Activity` abriria a tela; um de
 * `Service` exigiria foreground service no Android 12+ (com notificação própria — o oposto do que se
 * quer). O broadcast é o caminho oficial: o sistema sobe o processo se ele estiver morto, roda
 * `Application.onCreate()` e só então entrega o evento — por isso o handler é registrado lá
 * ([NotificationActions.setHandler]).
 *
 * ### Declarado no manifesto da lib
 * Como o [NotificationReceiver] e o [BootCompletedReceiver], está no `AndroidManifest.xml` da
 * própria kmplib (`exported="false"`): todo consumidor herda o comportamento só bumpando a versão,
 * sem editar manifesto.
 *
 * ### O que acontece em cada tipo de ação
 * - **Adiar** ([NotificationActionKind.SNOOZE]): a lib reagenda o MESMO id daqui a N minutos e
 *   dispensa a notificação. Nada chega ao app.
 * - **Ação do app** ([NotificationActionKind.APP]): a notificação é dispensada e o evento vai ao
 *   handler, dentro de um `goAsync()` — o broadcast fica vivo enquanto o app grava no banco local.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(
            AndroidNotificationScheduler.EXTRA_NOTIFICATION_ID,
            Int.MIN_VALUE,
        )
        val actionId = intent.getStringExtra(EXTRA_ACTION_ID)
        if (notificationId == Int.MIN_VALUE || actionId.isNullOrBlank()) {
            AppLogger.w(TAG, "Ação sem id de notificação/ação — ignorada")
            return
        }

        val appContext = context.applicationContext
        if (NotificationSchedulerHolder.getContext() == null) {
            // Processo recém-criado pelo broadcast: garante o holder mesmo que o app tenha esquecido
            // do KmpLib.init — perder a ação do usuário por um detalhe de setup seria pior.
            NotificationSchedulerHolder.init(appContext)
        }

        // O payload do PendingIntent é o retrato do agendamento quando a notificação foi exibida; o
        // registro é a verdade corrente (um lembrete diário, ao disparar, já foi reagendado para
        // amanhã). Preferimos o registro e caímos no payload quando o item já saiu dele — que é o
        // caso normal de um disparo único.
        val fromIntent = NotificationAlarms.decode(intent.getStringExtra(NotificationAlarms.EXTRA_PAYLOAD))
        val stored = runCatching { AndroidNotificationScheduleStore(appContext).get(notificationId) }.getOrNull()
        val item = stored ?: fromIntent
        val action = item?.let { NotificationActionRules.actionOf(it, actionId) }
            ?: fromIntent?.let { NotificationActionRules.actionOf(it, actionId) }

        // A notificação sai da bandeja em qualquer caso: o usuário já agiu sobre ela.
        NotificationPresenter.dismiss(appContext, notificationId)

        when {
            action != null && action.isSnooze -> snooze(appContext, item ?: fromIntent, action)
            else -> {
                if (action == null) {
                    // Ação desconhecida (app atualizado, id de ação renomeado): o app é quem decide
                    // o que fazer — melhor entregar o evento do que engolir o toque.
                    AppLogger.w(TAG, "Ação '$actionId' não está declarada no agendamento id=$notificationId")
                }
                if (action?.opensApp == true) openApp(appContext, notificationId, item?.data.orEmpty())
                dispatchToApp(notificationId, actionId, item?.data.orEmpty())
            }
        }
    }

    /** Adia o MESMO agendamento — sem criar id novo e sem matar a recorrência do lembrete diário. */
    private fun snooze(ctx: Context, item: ScheduledNotification?, action: NotificationAction) {
        if (item == null) {
            AppLogger.w(TAG, "Adiamento sem agendamento conhecido — ignorado")
            return
        }
        val snoozed = NotificationActionRules.applySnooze(
            item = item,
            minutes = action.snoozeMinutes,
            nowMillis = System.currentTimeMillis(),
        )
        runCatching { AndroidNotificationScheduleStore(ctx).put(snoozed) }
            .onFailure { AppLogger.w(TAG, "Falha ao registrar adiamento: ${it.message}") }
        NotificationAlarms.arm(ctx, snoozed)
        AppLogger.d(
            TAG,
            "Notificação id=${item.id} adiada em ${action.snoozeMinutes} min " +
                "(próximo disparo=${snoozed.nextTriggerMillis})",
        )
    }

    /**
     * Entrega o evento ao app segurando o broadcast com `goAsync()`.
     *
     * Sem isso o processo poderia ser encerrado no instante em que `onReceive` retorna — antes de a
     * gravação no banco terminar — e a ação do usuário sumiria em silêncio, que é exatamente o
     * defeito que esta entrega existe para evitar.
     */
    private fun dispatchToApp(notificationId: Int, actionId: String, data: Map<String, String>) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                NotificationActions.dispatch(
                    NotificationActionEvent(
                        notificationId = notificationId,
                        actionId = actionId,
                        data = data,
                    ),
                )
            } finally {
                runCatching { result.finish() }
            }
        }
    }

    private fun openApp(ctx: Context, notificationId: Int, data: Map<String, String>) {
        runCatching {
            NotificationPresenter.contentPendingIntent(ctx, notificationId, data)?.send()
        }.onFailure {
            AppLogger.w(TAG, "Não foi possível abrir o app pela ação: ${it.message}")
        }
    }

    companion object {
        private const val TAG = "NotificationAction"

        /** Id da ação tocada (o [NotificationAction.id] declarado pelo app). */
        const val EXTRA_ACTION_ID = "kmplib_notification_action_id"
    }
}

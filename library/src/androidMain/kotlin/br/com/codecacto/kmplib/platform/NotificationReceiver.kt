package br.com.codecacto.kmplib.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.codecacto.kmplib.core.util.AppLogger

/**
 * BroadcastReceiver que EXIBE a notificação quando o alarme dispara.
 *
 * Declarado no `AndroidManifest.xml` da própria lib desde a 2.99.0 — o app consumidor não precisa
 * mais declará-lo (a declaração é idêntica à que os apps já usavam, então o manifest merger mescla
 * sem conflito com quem a mantiver).
 */
class NotificationReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val item = NotificationAlarms.itemFromIntent(intent)
        if (item == null) {
            AppLogger.w(TAG, "Alarme sem payload legível — nada a exibir")
            return
        }
        AppLogger.d(TAG, "Recebido alarme para notificação: id=${item.id}")

        NotificationPresenter.show(context, item)

        if (item.isRecurring) {
            // Lembrete recorrente: reagenda o próximo disparo (amanhã no diário, na semana que vem
            // no semanal), no mesmo horário.
            rescheduleRecurring(context, item)
        } else {
            // Disparo único consumido: sai do registro persistente para não ser "restaurado" num
            // boot futuro (o plano de restauração o descartaria, mas registro sujo vira lixo eterno).
            runCatching { AndroidNotificationScheduleStore(context).remove(item.id) }
        }
    }

    /**
     * Reagenda o lembrete recorrente para o próximo disparo — amanhã (diário) ou na semana que vem
     * (semanal), pela regra pura [NotificationRescheduling.nextRecurringTriggerMillis], que é a mesma
     * usada ao agendar e ao restaurar depois do boot. **O fuso vem do agendamento**, não do aparelho:
     * um lembrete de culto agendado no fuso da cidade continuaria certo no primeiro disparo e
     * escorregaria uma hora a partir do segundo se o reagendamento usasse o relógio local.
     *
     * O adiamento é **limpo** aqui: o disparo adiado acabou de acontecer, então o que vale a partir
     * de agora é o horário regular. Sem essa limpeza, um `snoozedUntilMillis` já vencido ficaria no
     * registro e o próximo boot mandaria o lembrete disparar no passado.
     */
    private fun rescheduleRecurring(context: Context, item: ScheduledNotification) {
        val next = item.copy(
            snoozedUntilMillis = 0L,
            triggerAtMillis = NotificationRescheduling.nextRecurringTriggerMillis(
                item = item,
                nowMillis = System.currentTimeMillis(),
            ),
        )
        NotificationAlarms.arm(context, next)
        AppLogger.d(TAG, "Lembrete recorrente reagendado: id=${item.id}, proximo=${next.triggerAtMillis}")

        // Mantém o registro persistente apontando para o PRÓXIMO disparo: é ele que o
        // BootCompletedReceiver vai ler se o aparelho reiniciar antes da próxima dose.
        runCatching { AndroidNotificationScheduleStore(context).put(next) }
            .onFailure { AppLogger.w(TAG, "Falha ao atualizar registro do lembrete recorrente: ${it.message}") }
    }
}

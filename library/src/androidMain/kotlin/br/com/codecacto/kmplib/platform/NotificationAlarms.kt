package br.com.codecacto.kmplib.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import br.com.codecacto.kmplib.core.util.AppLogger

/**
 * Ponte entre um [ScheduledNotification] e o `AlarmManager` — **o único lugar** da lib que arma,
 * desarma e reconstrói alarme no Android.
 *
 * Antes da 2.100.0 essa montagem estava espalhada em três arquivos (o agendador, o receiver que
 * exibe e o reagendamento diário), cada um repetindo a lista de `putExtra`. Com o payload virando um
 * objeto só, um campo novo (as ações) entraria em três lugares — e bastaria esquecer um para o
 * lembrete voltar do reboot sem os botões.
 */
internal object NotificationAlarms {

    private const val TAG = "NotificationAlarms"

    /**
     * Payload completo do agendamento, em JSON.
     *
     * Existe porque a ação precisa **se bastar**: quando o usuário toca "Adiar 30 min" num disparo
     * único que já saiu do registro (ele é removido ao disparar), o `PendingIntent` é a única fonte
     * do conteúdo a reagendar.
     */
    const val EXTRA_PAYLOAD = "kmplib_notification_payload"

    /** Monta o `Intent` do alarme a partir do agendamento. */
    fun buildAlarmIntent(ctx: Context, item: ScheduledNotification): Intent =
        Intent(ctx, NotificationReceiver::class.java).apply {
            // Extras "soltos" mantidos por compatibilidade: um alarme armado por versão anterior da
            // lib pode disparar depois da atualização, e o receiver novo continua sabendo lê-lo.
            putExtra(AndroidNotificationScheduler.EXTRA_NOTIFICATION_ID, item.id)
            putExtra(AndroidNotificationScheduler.EXTRA_TITLE, item.title)
            putExtra(AndroidNotificationScheduler.EXTRA_BODY, item.body)
            putExtra(AndroidNotificationScheduler.EXTRA_DATA, HashMap(item.data))
            putExtra(
                AndroidNotificationScheduler.EXTRA_CHANNEL_ID,
                item.channelId ?: AndroidNotificationScheduler.DEFAULT_CHANNEL_ID,
            )
            putExtra(AndroidNotificationScheduler.EXTRA_DAILY, item.isDaily)
            putExtra(AndroidNotificationScheduler.EXTRA_HOUR, item.hour)
            putExtra(AndroidNotificationScheduler.EXTRA_MINUTE, item.minute)
            putExtra(AndroidNotificationScheduler.EXTRA_CRITICAL, item.isCritical)
            putExtra(EXTRA_PAYLOAD, encode(item))
        }

    /**
     * Arma o alarme de [item] no instante [ScheduledNotification.nextTriggerMillis] (o adiamento
     * quando existe, o horário regular quando não).
     *
     * `FLAG_UPDATE_CURRENT` + `requestCode = id` fazem o mesmo id **substituir** o alarme anterior —
     * é o que garante que reagendar, restaurar depois do boot e adiar nunca produzam dois disparos.
     */
    fun arm(ctx: Context, item: ScheduledNotification) {
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            item.id,
            buildAlarmIntent(ctx, item),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerTime = item.nextTriggerMillis
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !alarmManager.canScheduleExactAlarms()
            ) {
                // Sem a permissão de alarme exato o disparo pode atrasar alguns minutos — melhor
                // isso do que não agendar nada. O app pode oferecer `requestExactAlarmPermission()`.
                AppLogger.w(TAG, "Sem permissão de alarme exato; agendando alarme inexato (id=${item.id})")
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao agendar alarme (id=${item.id})", e)
        }
    }

    /** Desarma o alarme de [id] (não mexe no registro persistente nem na bandeja). */
    fun cancel(ctx: Context, id: Int) {
        val alarmManager = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            ctx,
            id,
            Intent(ctx, NotificationReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        runCatching { alarmManager.cancel(pendingIntent) }
            .onFailure { AppLogger.w(TAG, "Falha ao cancelar alarme id=$id: ${it.message}") }
    }

    /**
     * Reconstrói o agendamento a partir do `Intent` do alarme.
     *
     * Prefere o payload JSON; cai nos extras soltos quando o alarme foi armado por uma versão
     * anterior da lib (que não tinha payload). Nunca lança — `null` só quando não há nada legível.
     */
    fun itemFromIntent(intent: Intent): ScheduledNotification? {
        decode(intent.getStringExtra(EXTRA_PAYLOAD))?.let { return it }

        val id = intent.getIntExtra(AndroidNotificationScheduler.EXTRA_NOTIFICATION_ID, Int.MIN_VALUE)
        if (id == Int.MIN_VALUE) return null
        val daily = intent.getBooleanExtra(AndroidNotificationScheduler.EXTRA_DAILY, false)
        return ScheduledNotification(
            id = id,
            title = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_TITLE).orEmpty(),
            body = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_BODY).orEmpty(),
            kind = if (daily) NotificationScheduleKind.DAILY else NotificationScheduleKind.ONE_SHOT,
            triggerAtMillis = System.currentTimeMillis(),
            // Os defaults 8/0 do caminho diário reproduzem exatamente o comportamento anterior à
            // 2.100.0 (o reagendamento lia `getIntExtra(EXTRA_HOUR, 8)`). Na prática os extras sempre
            // estão presentes; o default só existe para um alarme legado corrompido não virar 00:00.
            hour = intent.getIntExtra(AndroidNotificationScheduler.EXTRA_HOUR, if (daily) 8 else -1),
            minute = intent.getIntExtra(AndroidNotificationScheduler.EXTRA_MINUTE, if (daily) 0 else -1),
            data = readData(intent),
            channelId = intent.getStringExtra(AndroidNotificationScheduler.EXTRA_CHANNEL_ID),
            isCritical = intent.getBooleanExtra(AndroidNotificationScheduler.EXTRA_CRITICAL, false),
        )
    }

    fun encode(item: ScheduledNotification): String =
        runCatching { notificationScheduleJson.encodeToString(item) }.getOrDefault("")

    fun decode(raw: String?): ScheduledNotification? {
        if (raw.isNullOrBlank()) return null
        return runCatching { notificationScheduleJson.decodeFromString<ScheduledNotification>(raw) }
            .onFailure { AppLogger.w(TAG, "Payload de notificação ilegível: ${it.message}") }
            .getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun readData(intent: Intent): Map<String, String> =
        (intent.getSerializableExtra(AndroidNotificationScheduler.EXTRA_DATA) as? HashMap<*, *>)
            ?.entries
            ?.mapNotNull { (key, value) ->
                val k = key as? String ?: return@mapNotNull null
                val v = value as? String ?: return@mapNotNull null
                k to v
            }
            ?.toMap()
            .orEmpty()
}

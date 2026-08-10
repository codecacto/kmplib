package br.com.codecacto.kmplib.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSCalendar
import platform.Foundation.NSCalendarUnitDay
import platform.Foundation.NSCalendarUnitHour
import platform.Foundation.NSCalendarUnitMinute
import platform.Foundation.NSCalendarUnitMonth
import platform.Foundation.NSCalendarUnitSecond
import platform.Foundation.NSCalendarUnitYear
import platform.Foundation.NSDateComponents
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import kotlin.concurrent.AtomicInt

/**
 * Agendador iOS sobre `UNUserNotificationCenter`.
 *
 * ### Diferença de contrato para o Android (documentada de propósito)
 * No Android, a lib precisa de um `BOOT_COMPLETED` receiver porque o `AlarmManager` é **zerado no
 * boot**. No iOS **não existe esse problema**: uma notificação entregue ao
 * `UNUserNotificationCenter` é persistida pelo sistema e sobrevive a reboot, a app encerrado e a
 * atualização — quem dispara é o SO, não o app. Por isso não há (nem pode haver) receiver de boot
 * aqui: o iOS sequer entrega broadcast de boot a apps de terceiros.
 *
 * ### O limite que EXISTE no iOS: 64 pendentes por app
 * O sistema mantém no máximo **64 notificações pendentes por app** e **descarta em silêncio** o que
 * passar disso — sem erro, sem callback de falha. Um cronograma de 26 dias com dose de 12/12h pede
 * 52 disparos só de dose, mais avisos: encosta no teto.
 *
 * A lib resolve com um **espelho persistente** ([IosNotificationScheduleStore]) e uma **janela**:
 * registra no sistema os próximos [NotificationRescheduling.IOS_PENDING_LIMIT] disparos e guarda o
 * resto, reabastecendo em [refreshScheduledNotifications] (chame na abertura do app). Lembretes
 * diários usam `repeats = true`, então **um** pedido cobre disparos infinitos e quase não consome
 * cota — por isso eles têm prioridade na janela.
 */
@Suppress("UNCHECKED_CAST")
class IosNotificationScheduler : NotificationScheduler {

    companion object {
        private const val TAG = "NotificationScheduler"
    }

    private val notificationCenter = UNUserNotificationCenter.currentNotificationCenter()
    private val permissionGranted = AtomicInt(0) // 0 = unknown, 1 = granted, -1 = denied
    private val store: NotificationScheduleStore = IosNotificationScheduleStore()

    /**
     * Verifica se tem permissão para notificações.
     *
     * NOTA: Na primeira chamada, retorna o valor em cache ou `false` como fallback
     * seguro, pois a verificação real é assíncrona. Para garantir um resultado
     * preciso, chame `requestPermission()` primeiro ou use `checkPermission(callback)`.
     */
    override fun hasPermission(): Boolean {
        // Retorna cache se disponível
        if (permissionGranted.value != 0) {
            return permissionGranted.value == 1
        }

        // Inicia verificação assíncrona para atualizar cache
        notificationCenter.getNotificationSettingsWithCompletionHandler { settings ->
            val granted = settings?.authorizationStatus == UNAuthorizationStatusAuthorized
            permissionGranted.value = if (granted) 1 else -1
        }

        // Retorna false como fallback seguro (melhor pedir permissão do que assumir que tem)
        return false
    }

    /**
     * Verifica permissão de forma assíncrona (recomendado).
     */
    fun checkPermission(onResult: (Boolean) -> Unit) {
        notificationCenter.getNotificationSettingsWithCompletionHandler { settings ->
            val granted = settings?.authorizationStatus == UNAuthorizationStatusAuthorized
            permissionGranted.value = if (granted) 1 else -1
            onResult(granted)
        }
    }

    override fun requestPermission(onResult: (Boolean) -> Unit) {
        val options = UNAuthorizationOptionAlert or
                UNAuthorizationOptionSound or
                UNAuthorizationOptionBadge

        notificationCenter.requestAuthorizationWithOptions(options) { granted, error ->
            permissionGranted.value = if (granted) 1 else -1
            if (error != null) {
                AppLogger.e(TAG, "Erro ao solicitar permissão: ${error.localizedDescription}")
            }
            onResult(granted)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun scheduleNotification(
        id: Int,
        title: String,
        body: String,
        scheduledTime: Instant,
        data: Map<String, String>,
        channelId: String?,
        isCritical: Boolean
    ) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(
                if (isCritical) UNNotificationSound.defaultCriticalSound()
                else UNNotificationSound.defaultSound()
            )
            if (data.isNotEmpty()) {
                setUserInfo(data.mapKeys { it.key as Any } as Map<Any?, *>)
            }
        }

        // Converter Instant para DateComponents
        val localDateTime = scheduledTime.toLocalDateTime(TimeZone.currentSystemDefault())
        val dateComponents = NSDateComponents().apply {
            year = localDateTime.year.toLong()
            month = localDateTime.monthNumber.toLong()
            day = localDateTime.dayOfMonth.toLong()
            hour = localDateTime.hour.toLong()
            minute = localDateTime.minute.toLong()
            second = localDateTime.second.toLong()
        }

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            dateComponents,
            repeats = false
        )

        val scheduled = ScheduledNotification(
            id = id,
            title = title,
            body = body,
            kind = NotificationScheduleKind.ONE_SHOT,
            triggerAtMillis = scheduledTime.toEpochMilliseconds(),
            data = data,
            channelId = channelId,
            isCritical = isCritical,
        )
        store.put(scheduled)

        if (fitsInWindow(scheduled)) {
            submit(id, content, trigger)
        } else {
            AppLogger.d(TAG, "Notificação id=$id além do teto de pendentes do iOS — fica na fila da lib")
        }
        pruneDeferred()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun scheduleDailyNotification(
        id: Int,
        title: String,
        body: String,
        hour: Int,
        minute: Int,
        data: Map<String, String>,
        channelId: String?,
        isCritical: Boolean
    ) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(
                if (isCritical) UNNotificationSound.defaultCriticalSound()
                else UNNotificationSound.defaultSound()
            )
            if (data.isNotEmpty()) {
                setUserInfo(data.mapKeys { it.key as Any } as Map<Any?, *>)
            }
        }

        // Apenas hour/minute => dispara todo dia nesse horário local quando repeats=true.
        val dateComponents = NSDateComponents().apply {
            this.hour = hour.coerceIn(0, 23).toLong()
            this.minute = minute.coerceIn(0, 59).toLong()
        }

        val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
            dateComponents,
            repeats = true
        )

        store.put(
            ScheduledNotification(
                id = id,
                title = title,
                body = body,
                kind = NotificationScheduleKind.DAILY,
                triggerAtMillis = NotificationRescheduling.nextDailyTriggerMillis(
                    hour = hour,
                    minute = minute,
                    nowMillis = nowMillis(),
                ),
                hour = hour.coerceIn(0, 23),
                minute = minute.coerceIn(0, 59),
                data = data,
                channelId = channelId,
                isCritical = isCritical,
            )
        )

        submit(id, content, trigger)
        pruneDeferred()
    }

    override fun cancelNotification(id: Int) {
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(id.toString()))
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(id.toString()))
        store.remove(id)
        AppLogger.d(TAG, "Notificação cancelada: id=$id")
        // Cancelar libera vaga no teto de 64: promove quem estava esperando na fila da lib.
        refreshScheduledNotifications()
    }

    override fun cancelAllNotifications() {
        notificationCenter.removeAllPendingNotificationRequests()
        notificationCenter.removeAllDeliveredNotifications()
        store.clear()
        AppLogger.d(TAG, "Todas as notificações canceladas")
    }

    override fun showNotificationNow(
        id: Int,
        title: String,
        body: String,
        data: Map<String, String>,
        channelId: String?
    ) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound())
            if (data.isNotEmpty()) {
                setUserInfo(data.mapKeys { it.key as Any } as Map<Any?, *>)
            }
        }

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = id.toString(),
            content = content,
            trigger = null // null = imediato
        )

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                AppLogger.e(TAG, "Erro ao exibir notificação: ${error.localizedDescription}")
            } else {
                AppLogger.d(TAG, "Notificação exibida: id=$id")
            }
        }
    }

    override fun scheduledNotifications(): List<ScheduledNotification> = store.all()

    /**
     * Reabastece a fila do sistema dentro do teto de 64 pendentes e limpa disparos únicos vencidos.
     *
     * Diferente do Android, **não** exibe "disparos perdidos": no iOS quem entrega é o próprio SO,
     * mesmo com o app encerrado, então o usuário já recebeu — reexibir aqui duplicaria o aviso.
     *
     * Chame na abertura do app (é idempotente e barato).
     */
    override fun refreshScheduledNotifications() {
        val now = nowMillis()
        val plan = NotificationRescheduling.plan(stored = store.all(), nowMillis = now)
        plan.expiredIds.forEach { expired ->
            store.remove(expired)
            notificationCenter.removePendingNotificationRequestsWithIdentifiers(listOf(expired.toString()))
        }
        plan.toSchedule.forEach(store::put)

        val window = NotificationRescheduling.selectWindow(store.all(), now)
        window.register.forEach { item -> submit(item) }
        if (window.deferred.isNotEmpty()) {
            notificationCenter.removePendingNotificationRequestsWithIdentifiers(
                window.deferred.map { it.id.toString() }
            )
        }
        AppLogger.d(
            TAG,
            "Fila do iOS reconciliada: ${window.register.size} registradas, ${window.deferred.size} aguardando"
        )
    }

    /** `true` se [item] cabe na janela de pendentes registrada no sistema. */
    private fun fitsInWindow(item: ScheduledNotification): Boolean =
        NotificationRescheduling.selectWindow(store.all(), nowMillis())
            .register
            .any { it.id == item.id }

    /** Tira do sistema o que passou do teto (ficam guardados no espelho da lib). */
    private fun pruneDeferred() {
        val deferred = NotificationRescheduling.selectWindow(store.all(), nowMillis()).deferred
        if (deferred.isNotEmpty()) {
            notificationCenter.removePendingNotificationRequestsWithIdentifiers(
                deferred.map { it.id.toString() }
            )
        }
    }

    /** Registra (ou substitui, pelo identifier) um agendamento do espelho no sistema. */
    @OptIn(ExperimentalForeignApi::class)
    private fun submit(item: ScheduledNotification) {
        val content = UNMutableNotificationContent().apply {
            setTitle(item.title)
            setBody(item.body)
            setSound(
                if (item.isCritical) UNNotificationSound.defaultCriticalSound()
                else UNNotificationSound.defaultSound()
            )
            if (item.data.isNotEmpty()) {
                setUserInfo(item.data.mapKeys { it.key as Any } as Map<Any?, *>)
            }
        }

        val components = NSDateComponents()
        val trigger = if (item.isDaily) {
            components.hour = item.hour.coerceIn(0, 23).toLong()
            components.minute = item.minute.coerceIn(0, 59).toLong()
            UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
        } else {
            val local = Instant.fromEpochMilliseconds(item.triggerAtMillis)
                .toLocalDateTime(TimeZone.currentSystemDefault())
            components.year = local.year.toLong()
            components.month = local.monthNumber.toLong()
            components.day = local.dayOfMonth.toLong()
            components.hour = local.hour.toLong()
            components.minute = local.minute.toLong()
            components.second = local.second.toLong()
            UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
        }

        submit(item.id, content, trigger)
    }

    private fun submit(
        id: Int,
        content: UNMutableNotificationContent,
        trigger: UNCalendarNotificationTrigger,
    ) {
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = id.toString(),
            content = content,
            trigger = trigger
        )
        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                AppLogger.e(TAG, "Erro ao agendar notificação: ${error.localizedDescription}")
            } else {
                AppLogger.d(TAG, "Notificação agendada: id=$id")
            }
        }
    }

    private fun nowMillis(): Long = currentTimeMillis()
}

actual fun getNotificationScheduler(): NotificationScheduler = IosNotificationScheduler()

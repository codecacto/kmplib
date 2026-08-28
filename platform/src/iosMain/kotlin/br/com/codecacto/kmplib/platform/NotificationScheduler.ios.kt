package br.com.codecacto.kmplib.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import platform.Foundation.NSDateComponents
import platform.Foundation.NSTimeZone
import platform.Foundation.timeZoneWithName
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionDestructive
import platform.UserNotifications.UNNotificationActionOptionForeground
import platform.UserNotifications.UNNotificationActionOptions
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentTimeMillis
import kotlin.concurrent.AtomicInt

/**
 * Identificadores das requisições registradas no `UNUserNotificationCenter`.
 *
 * O iOS chaveia notificação por **String**, então o adiamento pode ter requisição própria — o que o
 * Android não permite (lá a chave é o `requestCode` `Int` do `PendingIntent`). Isso importa num caso
 * concreto: adiar um lembrete **diário** no iOS não pode substituir a requisição `repeats = true`,
 * senão o lembrete de amanhã só voltaria quando o app fosse aberto de novo.
 */
internal object IosNotificationRequests {

    private const val SNOOZE_SUFFIX = "#snooze"

    /** Requisição do horário regular. */
    fun base(id: Int): String = id.toString()

    /** Requisição do disparo adiado (convive com a regular). */
    fun snooze(id: Int): String = "$id$SNOOZE_SUFFIX"

    /** Todas as requisições que um agendamento pode ocupar. */
    fun all(id: Int): List<String> = listOf(base(id), snooze(id))

    /** Volta do identificador para o id do agendamento; `null` quando não é requisição da lib. */
    fun notificationIdOf(identifier: String): Int? =
        identifier.removeSuffix(SNOOZE_SUFFIX).toIntOrNull()
}

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
 *
 * ### Botões de ação (2.100.0)
 * O iOS não aceita ações por notificação: elas vivem numa `UNNotificationCategory` registrada no
 * centro, e a notificação aponta para a categoria pelo identificador. A lib deriva esse
 * identificador do **conteúdo** das ações ([NotificationActionRules.categoryIdentifier]) e
 * re-registra o conjunto de categorias a cada agendamento — dois lembretes com os mesmos botões
 * compartilham uma categoria, e o app não precisa inventar nome nenhum.
 *
 * A resposta do usuário chega pelo delegate: ver [NotificationActionBridge] e
 * [installNotificationActionDelegate].
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

    override fun scheduleNotification(
        id: Int,
        title: String,
        body: String,
        scheduledTime: Instant,
        data: Map<String, String>,
        channelId: String?,
        isCritical: Boolean,
        actions: List<NotificationAction>
    ) {
        val item = ScheduledNotification(
            id = id,
            title = title,
            body = body,
            kind = NotificationScheduleKind.ONE_SHOT,
            triggerAtMillis = scheduledTime.toEpochMilliseconds(),
            data = data,
            channelId = channelId,
            isCritical = isCritical,
            actions = NotificationActionRules.distinctActions(actions),
        )
        store.put(item)
        syncCategories()

        if (fitsInWindow(item)) {
            submit(item)
        } else {
            AppLogger.d(TAG, "Notificação id=$id além do teto de pendentes do iOS — fica na fila da lib")
        }
        pruneDeferred()
    }

    override fun scheduleDailyNotification(
        id: Int,
        title: String,
        body: String,
        hour: Int,
        minute: Int,
        data: Map<String, String>,
        channelId: String?,
        isCritical: Boolean,
        actions: List<NotificationAction>
    ) {
        val item = ScheduledNotification(
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
            actions = NotificationActionRules.distinctActions(actions),
        )
        store.put(item)
        syncCategories()
        submit(item)
        pruneDeferred()
    }

    override fun scheduleWeeklyNotification(
        id: Int,
        title: String,
        body: String,
        weekday: Int,
        hour: Int,
        minute: Int,
        timeZoneId: String?,
        data: Map<String, String>,
        channelId: String?,
        isCritical: Boolean,
        actions: List<NotificationAction>
    ) {
        val base = ScheduledNotification(
            id = id,
            title = title,
            body = body,
            kind = NotificationScheduleKind.WEEKLY,
            triggerAtMillis = 0L,
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            weekday = weekday.coerceIn(1, 7),
            timeZoneId = timeZoneId?.trim()?.takeIf { it.isNotEmpty() },
            data = data,
            channelId = channelId,
            isCritical = isCritical,
            actions = NotificationActionRules.distinctActions(actions),
        )
        // O `triggerAtMillis` do semanal não é o que dispara no iOS (quem repete é o
        // UNCalendarNotificationTrigger) — serve à ORDENAÇÃO dentro do teto de 64 pendentes.
        val item = base.copy(
            triggerAtMillis = NotificationRescheduling.nextRecurringTriggerMillis(base, nowMillis()),
        )
        store.put(item)
        syncCategories()
        submit(item)
        pruneDeferred()
    }

    override fun cancelNotification(id: Int) {
        val identifiers = IosNotificationRequests.all(id)
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(identifiers)
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(identifiers)
        store.remove(id)
        AppLogger.d(TAG, "Notificação cancelada: id=$id")
        // Cancelar libera vaga no teto de 64: promove quem estava esperando na fila da lib.
        refreshScheduledNotifications()
    }

    override fun cancelAllNotifications() {
        notificationCenter.removeAllPendingNotificationRequests()
        notificationCenter.removeAllDeliveredNotifications()
        store.clear()
        syncCategories()
        AppLogger.d(TAG, "Todas as notificações canceladas")
    }

    override fun showNotificationNow(
        id: Int,
        title: String,
        body: String,
        data: Map<String, String>,
        channelId: String?,
        actions: List<NotificationAction>
    ) {
        val resolvedActions = NotificationActionRules.distinctActions(actions)
        val content = buildContent(
            title = title,
            body = body,
            data = data,
            isCritical = false,
            actions = resolvedActions,
        )
        if (resolvedActions.isNotEmpty()) syncCategories(extra = resolvedActions)

        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = IosNotificationRequests.base(id),
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
     * Adia o agendamento [id] em [minutes] minutos.
     *
     * No iOS o disparo adiado ganha uma **requisição própria** (`"<id>#snooze"`) para não substituir
     * a requisição `repeats = true` do lembrete diário — assim o adiamento de hoje não custa o
     * lembrete de amanhã.
     */
    override fun snoozeNotification(id: Int, minutes: Int) {
        val item = store.get(id)
        if (item == null) {
            AppLogger.w(TAG, "Adiar id=$id: agendamento não está no registro — nada a fazer")
            return
        }
        val snoozed = NotificationActionRules.applySnooze(item, minutes, nowMillis())
        store.put(snoozed)
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(IosNotificationRequests.all(id))
        submit(snoozed)
        AppLogger.d(TAG, "Notificação id=$id adiada em $minutes min (próximo=${snoozed.nextTriggerMillis})")
    }

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
            notificationCenter.removePendingNotificationRequestsWithIdentifiers(
                IosNotificationRequests.all(expired)
            )
        }
        plan.toSchedule.forEach(store::put)
        syncCategories()

        val window = NotificationRescheduling.selectWindow(store.all(), now)
        window.register.forEach { item -> submit(item) }
        if (window.deferred.isNotEmpty()) {
            notificationCenter.removePendingNotificationRequestsWithIdentifiers(
                window.deferred.flatMap { IosNotificationRequests.all(it.id) }
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
                deferred.flatMap { IosNotificationRequests.all(it.id) }
            )
        }
    }

    /**
     * Registra (ou substitui, pelo identifier) um agendamento do espelho no sistema.
     *
     * Um item pode ocupar **duas** requisições: a do horário regular e a do adiamento pendente.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun submit(item: ScheduledNotification) {
        val now = nowMillis()
        val hasPendingSnooze = item.isSnoozed && item.snoozedUntilMillis > now

        when {
            item.isDaily -> submitRequest(
                identifier = IosNotificationRequests.base(item.id),
                item = item,
                trigger = dailyTrigger(item),
            )

            item.isWeekly -> submitRequest(
                identifier = IosNotificationRequests.base(item.id),
                item = item,
                trigger = weeklyTrigger(item),
            )

            // Disparo único adiado: a requisição regular apontaria para um instante no passado, que
            // o iOS nunca entrega — a do adiamento a substitui.
            hasPendingSnooze -> notificationCenter.removePendingNotificationRequestsWithIdentifiers(
                listOf(IosNotificationRequests.base(item.id))
            )

            else -> submitRequest(
                identifier = IosNotificationRequests.base(item.id),
                item = item,
                trigger = calendarTrigger(item.triggerAtMillis),
            )
        }

        if (hasPendingSnooze) {
            submitRequest(
                identifier = IosNotificationRequests.snooze(item.id),
                item = item,
                trigger = calendarTrigger(item.snoozedUntilMillis),
            )
        } else {
            notificationCenter.removePendingNotificationRequestsWithIdentifiers(
                listOf(IosNotificationRequests.snooze(item.id))
            )
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun dailyTrigger(item: ScheduledNotification): UNCalendarNotificationTrigger {
        // Apenas hour/minute => dispara todo dia nesse horário local quando repeats=true.
        val components = NSDateComponents().apply {
            hour = item.hour.coerceIn(0, 23).toLong()
            minute = item.minute.coerceIn(0, 59).toLong()
        }
        return UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
    }

    /**
     * Gatilho semanal nativo: `weekday` + `hour` + `minute` com `repeats = true` — o sistema repete
     * sozinho, com o app fechado, sem alarme nem reagendamento do nosso lado.
     *
     * **O `weekday` do Apple não é o ISO.** `NSDateComponents.weekday` conta **1 = domingo … 7 =
     * sábado**; a lib fala ISO-8601 (**1 = segunda … 7 = domingo**), que é o de `kotlinx.datetime`.
     * Passar o número ISO direto desloca todo lembrete em um dia — e o de domingo cai no sábado.
     *
     * Quando o agendamento traz fuso ([ScheduledNotification.timeZoneId]), ele vai **dentro** dos
     * componentes: sem isso o iOS lê `hour:minute` no fuso do aparelho, e o lembrete do culto
     * escorrega junto com quem viajou.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun weeklyTrigger(item: ScheduledNotification): UNCalendarNotificationTrigger {
        val components = NSDateComponents().apply {
            weekday = appleWeekday(item.weekday).toLong()
            hour = item.hour.coerceIn(0, 23).toLong()
            minute = item.minute.coerceIn(0, 59).toLong()
            item.timeZoneId?.let { id ->
                val zone = NSTimeZone.timeZoneWithName(id)
                if (zone != null) {
                    timeZone = zone
                } else {
                    AppLogger.w(TAG, "Fuso desconhecido '$id' — lembrete semanal no fuso do aparelho")
                }
            }
        }
        return UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = true)
    }

    /** ISO-8601 (1 = segunda … 7 = domingo) → Apple (1 = domingo … 7 = sábado). */
    private fun appleWeekday(isoWeekday: Int): Int = (isoWeekday.coerceIn(1, 7) % 7) + 1

    @OptIn(ExperimentalForeignApi::class)
    private fun calendarTrigger(atMillis: Long): UNCalendarNotificationTrigger {
        val local = Instant.fromEpochMilliseconds(atMillis)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        val components = NSDateComponents().apply {
            year = local.year.toLong()
            month = local.monthNumber.toLong()
            day = local.dayOfMonth.toLong()
            hour = local.hour.toLong()
            minute = local.minute.toLong()
            second = local.second.toLong()
        }
        return UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)
    }

    private fun buildContent(
        title: String,
        body: String,
        data: Map<String, String>,
        isCritical: Boolean,
        actions: List<NotificationAction>,
    ): UNMutableNotificationContent = UNMutableNotificationContent().apply {
        setTitle(title)
        setBody(body)
        setSound(
            if (isCritical) UNNotificationSound.defaultCriticalSound()
            else UNNotificationSound.defaultSound()
        )
        if (data.isNotEmpty()) {
            setUserInfo(data.mapKeys { it.key as Any } as Map<Any?, *>)
        }
        val category = NotificationActionRules.categoryIdentifier(actions)
        if (category.isNotEmpty()) setCategoryIdentifier(category)
    }

    private fun submitRequest(
        identifier: String,
        item: ScheduledNotification,
        trigger: UNNotificationTrigger,
    ) {
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifier,
            content = buildContent(
                title = item.title,
                body = item.body,
                data = item.data,
                isCritical = item.isCritical,
                actions = item.actions,
            ),
            trigger = trigger,
        )
        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                AppLogger.e(TAG, "Erro ao agendar notificação: ${error.localizedDescription}")
            } else {
                AppLogger.d(TAG, "Notificação agendada: $identifier")
            }
        }
    }

    /**
     * Re-registra no centro **todas** as categorias de ação em uso.
     *
     * `setNotificationCategories` **substitui** o conjunto inteiro (não acrescenta), então o registro
     * é sempre montado a partir do espelho da lib — registrar só a categoria do agendamento atual
     * apagaria os botões de todos os outros lembretes já agendados.
     */
    private fun syncCategories(extra: List<NotificationAction> = emptyList()) {
        val sets = buildList {
            store.all().forEach { if (it.actions.isNotEmpty()) add(it.actions) }
            if (extra.isNotEmpty()) add(extra)
        }
        if (sets.isEmpty()) {
            notificationCenter.setNotificationCategories(emptySet<UNNotificationCategory>())
            return
        }

        val categories = sets
            .associateBy { NotificationActionRules.categoryIdentifier(it) }
            .map { (identifier, actions) -> buildCategory(identifier, actions) }
            .toSet()
        notificationCenter.setNotificationCategories(categories)
    }

    private fun buildCategory(
        identifier: String,
        actions: List<NotificationAction>,
    ): UNNotificationCategory = UNNotificationCategory.categoryWithIdentifier(
        identifier = identifier,
        actions = actions.map { action ->
            var options: UNNotificationActionOptions = 0uL
            if (action.opensApp) options = options or UNNotificationActionOptionForeground
            if (action.destructive) options = options or UNNotificationActionOptionDestructive
            UNNotificationAction.actionWithIdentifier(
                identifier = action.id,
                title = action.title,
                options = options,
            )
        },
        intentIdentifiers = emptyList<String>(),
        options = 0uL,
    )

    private fun nowMillis(): Long = currentTimeMillis()
}

actual fun getNotificationScheduler(): NotificationScheduler = IosNotificationScheduler()

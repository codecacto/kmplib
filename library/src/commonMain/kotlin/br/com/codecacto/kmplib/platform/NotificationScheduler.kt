package br.com.codecacto.kmplib.platform

import kotlinx.datetime.Instant

/**
 * Agendador de notificações locais multiplataforma.
 *
 * Uso:
 * ```kotlin
 * val scheduler = getNotificationScheduler()
 *
 * // Verificar permissão
 * if (!scheduler.hasPermission()) {
 *     scheduler.requestPermission { granted ->
 *         if (granted) {
 *             // Agendar notificação
 *         }
 *     }
 * }
 *
 * // Agendar notificação
 * scheduler.scheduleNotification(
 *     id = 1,
 *     title = "Lembrete",
 *     body = "Hora de tomar seu medicamento",
 *     scheduledTime = Clock.System.now() + 1.hours,
 *     data = mapOf("medicationId" to "123")
 * )
 *
 * // Cancelar notificação
 * scheduler.cancelNotification(1)
 * ```
 */
interface NotificationScheduler {
    /**
     * Verifica se tem permissão para enviar notificações.
     */
    fun hasPermission(): Boolean

    /**
     * Solicita permissão para notificações.
     * @param onResult Callback com resultado (true = permitido)
     */
    fun requestPermission(onResult: (Boolean) -> Unit)

    /**
     * Agenda uma notificação.
     *
     * @param id ID único da notificação
     * @param title Título da notificação
     * @param body Corpo/descrição da notificação
     * @param scheduledTime Momento em que a notificação deve ser exibida
     * @param data Dados extras para a notificação
     * @param channelId ID do canal (Android) - usa padrão se não informado
     * @param isCritical Se true, tenta bypassar modo não perturbe
     */
    fun scheduleNotification(
        id: Int,
        title: String,
        body: String,
        scheduledTime: Instant,
        data: Map<String, String> = emptyMap(),
        channelId: String? = null,
        isCritical: Boolean = false
    )

    /**
     * Agenda uma notificação local que se repete TODO dia no horário (hour:minute) local.
     *
     * Use para lembretes recorrentes (ex.: versículo diário, dose diária). O disparo ocorre
     * todos os dias no mesmo horário local até ser cancelado com [cancelNotification] (mesmo [id]).
     *
     * @param id ID único da notificação/lembrete (use o mesmo para reagendar/cancelar)
     * @param title Título da notificação
     * @param body Corpo/descrição da notificação
     * @param hour Hora do disparo (0..23, horário local)
     * @param minute Minuto do disparo (0..59, horário local)
     * @param data Dados extras para a notificação
     * @param channelId ID do canal (Android) - usa padrão se não informado
     * @param isCritical Se true, tenta bypassar modo não perturbe
     *
     * Android: o disparo usa `setExactAndAllowWhileIdle` com reagendamento do próximo dia dentro do
     * receiver, e o agendamento é **persistido** pela lib — o `BootCompletedReceiver` restaura tudo
     * depois de reiniciar o aparelho ou atualizar o app (desde a 2.99.0). Ver
     * [refreshScheduledNotifications].
     */
    fun scheduleDailyNotification(
        id: Int,
        title: String,
        body: String,
        hour: Int,
        minute: Int,
        data: Map<String, String> = emptyMap(),
        channelId: String? = null,
        isCritical: Boolean = false
    )

    /**
     * Cancela uma notificação agendada (única ou diária recorrente).
     * @param id ID da notificação a cancelar
     */
    fun cancelNotification(id: Int)

    /**
     * Cancela todas as notificações agendadas.
     */
    fun cancelAllNotifications()

    /**
     * Exibe uma notificação imediatamente.
     */
    fun showNotificationNow(
        id: Int,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
        channelId: String? = null
    )

    // -----------------------------------------------------------------------------------------
    // Sobrevivência a reboot / atualização (2.99.0)
    //
    // Métodos ADITIVOS, todos com corpo default: um `NotificationScheduler` implementado à mão pelo
    // app (fake de teste, decorator) continua compilando sem tocar em nada.
    // -----------------------------------------------------------------------------------------

    /**
     * Agendamentos que a lib tem registrados (o espelho persistente, não o estado do SO).
     *
     * Útil para diagnóstico ("o que está agendado?") e para o app conferir depois de um boot.
     */
    fun scheduledNotifications(): List<ScheduledNotification> = emptyList()

    /**
     * Reconcilia o registro persistente da lib com o sistema operacional.
     *
     * **Android:** reagenda no `AlarmManager` tudo o que estiver registrado (o `AlarmManager` é
     * zerado no boot), exibe os disparos perdidos que ainda estejam dentro da janela de graça
     * ([NotificationRescheduling.DEFAULT_MISSED_GRACE_MILLIS]) e descarta os únicos já vencidos.
     * Chamado automaticamente pelo `BootCompletedReceiver` no boot e na atualização do app.
     *
     * **iOS:** o `UNUserNotificationCenter` persiste as notificações agendadas sozinho, então aqui a
     * função serve ao **teto de 64 pendentes por app**: reabastece a fila com os próximos disparos
     * que ainda não estavam registrados.
     *
     * É **idempotente** — chamar de novo não duplica nada. Chamar na abertura do app é barato e
     * cobre o caso do usuário que desligou o aparelho por dias.
     */
    fun refreshScheduledNotifications() {}

    /**
     * Android 12+ (API 31): o app pode agendar **alarmes exatos**?
     *
     * Quando `false`, a lib continua agendando (com `setAndAllowWhileIdle`), mas o disparo pode
     * atrasar minutos — o SO agrupa alarmes inexatos para poupar bateria. Para lembrete de dose isso
     * costuma ser aceitável; para alarme de despertar, não. Nas demais plataformas/versões devolve
     * `true`.
     */
    fun canScheduleExactAlarms(): Boolean = true

    /**
     * Abre a tela do sistema onde o usuário concede "alarmes e lembretes" (Android 12+,
     * `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`). No-op nas demais plataformas.
     *
     * Chamar **só depois** de explicar o porquê na UI: é uma tela de Configurações, e mandar o
     * usuário para lá sem contexto é o caminho mais curto para ele voltar sem conceder.
     */
    fun requestExactAlarmPermission() {}

    /**
     * Abre a tela de **otimização de bateria** do sistema (Android).
     *
     * Existe por causa das camadas agressivas de fabricante (Xiaomi, Samsung, Huawei…), que matam
     * alarmes de apps "não usados recentemente" — a causa nº 1 de "meu lembrete não tocou" em
     * Android, independente do código estar certo. Abre a **lista geral** de otimização
     * (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`), que não exige permissão restrita e não põe em
     * risco a revisão na Play, ao contrário de pedir a isenção direto.
     */
    fun openBatteryOptimizationSettings() {}
}

/**
 * Obtém a implementação do NotificationScheduler para a plataforma atual.
 */
expect fun getNotificationScheduler(): NotificationScheduler

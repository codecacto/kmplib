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
     * Cancela uma notificação agendada.
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
}

/**
 * Obtém a implementação do NotificationScheduler para a plataforma atual.
 */
expect fun getNotificationScheduler(): NotificationScheduler

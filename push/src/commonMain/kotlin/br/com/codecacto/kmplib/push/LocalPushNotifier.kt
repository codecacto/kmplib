package br.com.codecacto.kmplib.push

/**
 * Notificação local (exibida pelo próprio app, tipicamente quando um push chega em **foreground**).
 *
 * @param id          identificador estável (permite atualizar/remover depois).
 * @param title       título; `null`/vazio → sem título.
 * @param body        corpo; `null`/vazio → sem corpo.
 * @param payloadData dados que o app quer receber ao tocar (ex.: rota de deep link).
 */
data class LocalPushNotification(
    val id: Int,
    val title: String?,
    val body: String?,
    val payloadData: Map<String, String> = emptyMap(),
)

/**
 * Ponto de extensão **comum** para exibir uma notificação local, funcionando tanto com KMPNotifier
 * (Android) quanto com o caminho APNs-direto (iOS).
 *
 * Antes, os consumidores chamavam `NotifierManager.getLocalNotifier()` direto — o que não existe no
 * iOS quando o KMPNotifier sai do caminho (piloto own-stack). Aqui a lib abstrai:
 *  - **Android** → delega ao `LocalNotifier` do KMPNotifier (canais já configurados no boot).
 *  - **iOS** → usa `UNUserNotificationCenter` (fluxo oficial Apple) com entrega imediata.
 *
 * Obtenha a implementação da plataforma via [createLocalPushNotifier].
 */
interface LocalPushNotifier {
    /** Exibe a notificação; devolve o `id` usado (útil para remover depois). */
    fun notify(notification: LocalPushNotification): Int

    /** Remove uma notificação já entregue. */
    fun remove(id: Int)

    /** Remove todas as notificações entregues por este app. */
    fun removeAll()
}

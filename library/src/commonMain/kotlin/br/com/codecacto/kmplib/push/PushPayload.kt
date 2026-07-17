package br.com.codecacto.kmplib.push

/**
 * Extração pura de título/corpo do payload de push, agnóstica de provedor.
 *
 * Serve o caminho **APNs-direto do iOS**, onde o `userInfo` da notificação é achatado em
 * `Map<String, String>` pelo AppDelegate antes de chegar ao [ApplePushBridge]. A carga own-stack
 * costuma trazer as chaves customizadas `title`/`body`; APNs padrão aninha o texto sob
 * `aps.alert.*`. Ambas as convenções são cobertas.
 *
 * commonMain puro (sem I/O, sem plataforma) — testável em `commonTest`.
 */
object PushPayload {
    private val TITLE_KEYS = listOf("title", "aps.alert.title", "gcm.notification.title")
    private val BODY_KEYS = listOf("body", "message", "aps.alert.body", "gcm.notification.body")

    /** Título derivado das chaves conhecidas; `null` se nenhuma existir/estiver em branco. */
    fun title(data: Map<String, String>): String? = firstNonBlank(data, TITLE_KEYS)

    /** Corpo derivado das chaves conhecidas; `null` se nenhuma existir/estiver em branco. */
    fun body(data: Map<String, String>): String? = firstNonBlank(data, BODY_KEYS)

    private fun firstNonBlank(data: Map<String, String>, keys: List<String>): String? {
        for (key in keys) {
            val value = data[key]
            if (!value.isNullOrBlank()) return value
        }
        return null
    }
}

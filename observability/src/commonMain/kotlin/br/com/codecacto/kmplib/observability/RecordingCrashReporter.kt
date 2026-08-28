package br.com.codecacto.kmplib.observability

/**
 * [CrashReporter] que guarda tudo em memória e não fala com o Sentry.
 *
 * Espelha a semântica da implementação real: com `enabled=false` (ou DSN em branco) no [init], as
 * demais chamadas são no-op — nada é registrado. Serve a três usos: asserção em teste, execução
 * local sem DSN configurado (onde o relato de crash não deve sair da máquina) e modo demonstração.
 *
 * Mora no `commonMain` pelo mesmo motivo do `InMemoryAppPreferences`: como dublê de `commonTest`
 * ela só existia para quem compilava o mesmo módulo, e o teste do alerta de pagamento
 * (`monetization`) já a usava de fora. Source set de teste não é publicado, então cada módulo novo
 * precisaria da própria cópia.
 */
class RecordingCrashReporter : CrashReporter {

    var initializedConfig: CrashReporterConfig? = null
    var active: Boolean = false
        private set

    override val isActive: Boolean get() = active

    val exceptions = mutableListOf<Pair<Throwable, Map<String, String>>>()
    val messages = mutableListOf<Message>()
    val breadcrumbs = mutableListOf<Breadcrumb>()
    val tags = mutableMapOf<String, String>()
    var currentUserId: String? = null
        private set

    data class Message(val message: String, val level: CrashLevel, val tags: Map<String, String>)

    data class Breadcrumb(val message: String, val category: String?, val level: CrashLevel)

    override fun init(config: CrashReporterConfig) {
        initializedConfig = config
        active = config.enabled && config.dsn.isNotBlank()
    }

    override fun captureException(throwable: Throwable, tags: Map<String, String>) {
        if (!active) return
        exceptions.add(throwable to tags)
    }

    override fun captureMessage(message: String, level: CrashLevel, tags: Map<String, String>) {
        if (!active) return
        messages.add(Message(message, level, tags))
    }

    override fun addBreadcrumb(message: String, category: String?, level: CrashLevel) {
        if (!active) return
        breadcrumbs.add(Breadcrumb(message, category, level))
    }

    override fun setUser(id: String?) {
        if (!active) return
        currentUserId = id?.takeIf { it.isNotBlank() }
    }

    override fun clearUser() {
        if (!active) return
        currentUserId = null
    }

    override fun setTag(key: String, value: String) {
        if (!active) return
        tags[key] = value
    }
}

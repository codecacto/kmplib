package br.com.codecacto.kmplib.observability

import io.sentry.kotlin.multiplatform.Sentry
import io.sentry.kotlin.multiplatform.SentryLevel
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.User

/**
 * Implementação de [CrashReporter] sobre o `sentry-kotlin-multiplatform` (padrão-ouro).
 *
 * **Thin wrapper:** delega direto à API oficial do Sentry KMP — a única lógica própria é
 * mapear a [CrashReporterConfig] para as `SentryOptions` e travar a política de privacidade/
 * compatibilidade com o GlitchTip (sem PII, sem tracing, sem session/replay/attachments).
 *
 * A API do Sentry KMP é 100% `commonMain`: no Android delega ao Sentry Android SDK e no iOS ao
 * Sentry Cocoa SDK (via cinterop do próprio artefato) — por isso NÃO há `expect/actual` aqui.
 * O `Sentry.init { }` sem `Context` no Android obtém o contexto via `ContentProvider` do SDK.
 */
internal class SentryCrashReporter : CrashReporter {

    /** Guarda se o SDK foi inicializado de fato — quando `false`, tudo é no-op. */
    private var active: Boolean = false

    override val isActive: Boolean get() = active

    override fun init(config: CrashReporterConfig) {
        // enabled=false OU DSN em branco => no-op (debug local sem DSN configurado).
        if (!config.enabled || config.dsn.isBlank()) {
            active = false
            return
        }

        Sentry.init { options ->
            options.dsn = config.dsn
            options.environment = config.environment
            options.release = config.release

            // LGPD — NUNCA enviar PII (IP, dados pessoais). Default false; travado na config.
            options.sendDefaultPii = config.sendDefaultPii

            // Performance/tracing OFF — o GlitchTip não suporta bem transações.
            options.tracesSampleRate = config.tracesSampleRate

            // GlitchTip ignora envelopes de sessão — desligar o auto session tracking.
            options.enableAutoSessionTracking = false

            // Sem session replay / screenshots / view hierarchy: o GlitchTip só entende
            // erros/mensagens/breadcrumbs/tags/user/release/environment.
            options.attachScreenshot = false
            options.attachViewHierarchy = false
        }
        active = true
    }

    override fun captureException(throwable: Throwable, tags: Map<String, String>) {
        if (!active) return
        if (tags.isEmpty()) {
            Sentry.captureException(throwable)
        } else {
            Sentry.captureException(throwable) { scope ->
                tags.forEach { (key, value) -> scope.setTag(key, value) }
            }
        }
    }

    override fun captureMessage(message: String, level: CrashLevel, tags: Map<String, String>) {
        if (!active) return
        Sentry.captureMessage(message) { scope ->
            scope.level = level.toSentryLevel()
            tags.forEach { (key, value) -> scope.setTag(key, value) }
        }
    }

    override fun addBreadcrumb(message: String, category: String?, level: CrashLevel) {
        if (!active) return
        val crumb = Breadcrumb()
        crumb.message = message
        crumb.level = level.toSentryLevel()
        if (category != null) crumb.category = category
        Sentry.addBreadcrumb(crumb)
    }

    override fun setUser(id: String?) {
        if (!active) return
        if (id.isNullOrBlank()) {
            Sentry.setUser(null)
        } else {
            // SÓ id opaco — jamais e-mail/username/nome/CPF (LGPD).
            val user = User()
            user.id = id
            Sentry.setUser(user)
        }
    }

    override fun clearUser() {
        if (!active) return
        Sentry.setUser(null)
    }

    override fun setTag(key: String, value: String) {
        if (!active) return
        Sentry.configureScope { scope -> scope.setTag(key, value) }
    }
}

/** Mapeamento [CrashLevel] -> `SentryLevel` do Sentry KMP. */
internal fun CrashLevel.toSentryLevel(): SentryLevel = when (this) {
    CrashLevel.Debug -> SentryLevel.DEBUG
    CrashLevel.Info -> SentryLevel.INFO
    CrashLevel.Warning -> SentryLevel.WARNING
    CrashLevel.Error -> SentryLevel.ERROR
    CrashLevel.Fatal -> SentryLevel.FATAL
}

/**
 * Cria a implementação padrão do [CrashReporter] (Sentry/GlitchTip).
 * O app inicializa via [CrashReporter.init] passando a [CrashReporterConfig].
 */
fun createCrashReporter(): CrashReporter = SentryCrashReporter()

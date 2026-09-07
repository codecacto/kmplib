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

            /*
             * Traduz a tag reservada `_fingerprint` no fingerprint DE VERDADE do evento.
             *
             * Por que passar por uma tag em vez de setar direto no `captureMessage`: o `Scope` do
             * `sentry-kotlin-multiplatform` (0.13.0, a última publicada) expõe tag, contexto, user,
             * level e breadcrumb — e NÃO expõe fingerprint. Quem expõe é o `SentryEvent`, e o único
             * ponto comum (Android + iOS) em que se alcança o evento antes do envio é o
             * `beforeSend`. É a via oficial do próprio Sentry para agrupar; não é contorno.
             *
             * Guardar o valor numa variável do reporter em vez da tag seria mais direto e ERRADO: o
             * `beforeSend` roda fora da chamada, e dois alertas simultâneos leriam o valor um do
             * outro. Na tag, o dado viaja DENTRO do evento — não há corrida possível.
             *
             * A tag é removida depois de traduzida: ela é transporte, não informação para quem lê o
             * painel.
             */
            options.beforeSend = { event ->
                event.getTag(TAG_FINGERPRINT)?.let { bruto ->
                    val partes = bruto.split(SEPARADOR_FINGERPRINT).filter { it.isNotBlank() }
                    if (partes.isNotEmpty()) event.fingerprint = partes.toMutableList()
                    event.removeTag(TAG_FINGERPRINT)
                }
                event
            }
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

    override fun captureMessage(
        message: String,
        level: CrashLevel,
        tags: Map<String, String>,
        fingerprint: List<String>,
    ) {
        if (!active) return
        Sentry.captureMessage(message) { scope ->
            scope.level = level.toSentryLevel()
            tags.forEach { (key, value) -> scope.setTag(key, value) }
            // Lista vazia NÃO é passada: `fingerprints = emptyList()` não é "sem fingerprint" para o
            // servidor — é um fingerprint vazio, e todo evento cairia na MESMA issue.
            // Vai como tag e o `beforeSend` a converte em fingerprint (ver o KDoc lá em cima).
            if (fingerprint.isNotEmpty()) {
                scope.setTag(TAG_FINGERPRINT, fingerprint.joinToString(SEPARADOR_FINGERPRINT))
            }
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

    private companion object {
        /**
         * Tag reservada que carrega o fingerprint do `Scope` até o `beforeSend`. O underscore marca
         * que é transporte interno da lib, não informação de produto — e o `beforeSend` a remove
         * antes do evento sair.
         */
        const val TAG_FINGERPRINT = "_fingerprint"

        /** `|` não aparece em slug de alerta (todos são `a-z_`), então nunca parte um termo ao meio. */
        const val SEPARADOR_FINGERPRINT = "|"
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

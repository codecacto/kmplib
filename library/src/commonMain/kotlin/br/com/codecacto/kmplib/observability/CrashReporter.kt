package br.com.codecacto.kmplib.observability

/**
 * Observabilidade de crashes/erros multiplataforma (Android + iOS).
 *
 * Contrato NEUTRO em relação ao fornecedor — a implementação real
 * ([createCrashReporter]) delega ao `sentry-kotlin-multiplatform`, que reporta
 * para **Sentry/GlitchTip**. O app injeta o DSN via [CrashReporterConfig.dsn]
 * (a lib é agnóstica; NÃO carrega DSN algum).
 *
 * Uso típico (bootstrap do app):
 * ```kotlin
 * val reporter: CrashReporter = koinInject()
 * reporter.init(
 *     CrashReporterConfig(
 *         dsn = BuildConfig.SENTRY_DSN,
 *         environment = if (BuildConfig.DEBUG) "debug" else "production",
 *         release = "meu-app@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}",
 *     )
 * )
 * ```
 *
 * **LGPD:** [setUser] recebe SÓ um id opaco — nunca e-mail/CPF/nome. E
 * [CrashReporterConfig.sendDefaultPii] é `false` por padrão e NUNCA deve ser `true`.
 */
interface CrashReporter {

    /**
     * Inicializa o reporter com a [config] do app. **No-op** quando
     * [CrashReporterConfig.enabled] é `false` ou o DSN está em branco (útil em
     * debug local sem DSN). Idempotente do ponto de vista do app: chame uma vez
     * no bootstrap.
     */
    fun init(config: CrashReporterConfig)

    /**
     * Reporta uma exceção capturada. [tags] opcionais são anexados ao evento
     * (ex.: `mapOf("feature" to "checkout")`).
     */
    fun captureException(throwable: Throwable, tags: Map<String, String> = emptyMap())

    /** Reporta uma mensagem (sem exceção) no [level] informado. */
    fun captureMessage(message: String, level: CrashLevel = CrashLevel.Error)

    /**
     * Adiciona um breadcrumb (trilha de contexto que acompanha o próximo evento).
     * [category] agrupa breadcrumbs semelhantes (ex.: `"navigation"`, `"http"`).
     */
    fun addBreadcrumb(message: String, category: String? = null, level: CrashLevel = CrashLevel.Info)

    /**
     * Define o usuário corrente por um **id opaco** (nunca e-mail/CPF/nome — LGPD).
     * `null`/vazio limpa o usuário (equivale a [clearUser]).
     */
    fun setUser(id: String?)

    /** Remove o usuário corrente do escopo (ex.: logout). */
    fun clearUser()

    /** Define uma tag global no escopo corrente (anexada aos próximos eventos). */
    fun setTag(key: String, value: String)
}

/**
 * Configuração do [CrashReporter], injetada pelo app.
 *
 * @property dsn Endpoint do Sentry/GlitchTip do projeto. **Injetado pelo app** — a lib nunca hardcoda.
 * @property environment `"production"` | `"debug"` (ou outro rótulo do app).
 * @property release Versão canônica: `"<app>@<versionName>+<versionCode>"`.
 * @property sendDefaultPii **LGPD: mantenha `false` sempre.** Ligar enviaria IP/dados pessoais.
 * @property tracesSampleRate Performance/tracing. `0.0` (OFF) — GlitchTip não suporta bem.
 * @property enabled `false` faz [CrashReporter.init] virar no-op (debug local sem DSN).
 */
data class CrashReporterConfig(
    val dsn: String,
    val environment: String,
    val release: String,
    val sendDefaultPii: Boolean = false,
    val tracesSampleRate: Double = 0.0,
    val enabled: Boolean = true,
)

/** Nível/severidade de um evento ou breadcrumb (mapeia 1:1 ao `SentryLevel`). */
enum class CrashLevel {
    Debug,
    Info,
    Warning,
    Error,
    Fatal,
}

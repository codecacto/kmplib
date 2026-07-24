@file:OptIn(kotlin.experimental.ExperimentalObjCName::class)

package br.com.codecacto.kmplib.observability

import br.com.codecacto.kmplib.core.util.BuildInfo
import kotlin.native.ObjCName

/**
 * Rótulos canônicos de ambiente do [CrashReporterConfig.environment]. Padronizados na lib para que
 * o painel de Crashes (GlitchTip) agrupe por um vocabulário único em todos os apps.
 */
object CrashEnvironment {
    const val PRODUCTION = "production"
    @ObjCName("DEBUG_ENV") // Evita conflito com macro DEBUG do Xcode
    const val DEBUG = "debug"
}

/**
 * Monta a `release` canônica `"<appSlug>@<versionName>+<versionCode>"` — o **único** formato aceito
 * pelo painel de Crashes. Fonte única de verdade (37 apps montavam essa string à mão, com variações).
 */
fun crashReporterRelease(appSlug: String, versionName: String, versionCode: Int): String =
    "${appSlug.trim()}@${versionName.trim()}+$versionCode"

/**
 * Deriva um [CrashReporterConfig] a partir dos campos crus do `BuildConfig`/Info.plist do app —
 * **lógica pura, testável** (sem tocar plataforma). Centraliza as três decisões que os apps repetiam:
 *
 * 1. `environment` = [CrashEnvironment.DEBUG] quando [isDebug], senão [CrashEnvironment.PRODUCTION];
 * 2. `release` = [crashReporterRelease] (formato canônico);
 * 3. `enabled` = há DSN não-branco (DSN vazio ⇒ `init` vira no-op, sem ligar Sentry em dev local).
 *
 * @param dsn DSN do Sentry/GlitchTip do projeto (injetado pelo app; a lib nunca hardcoda).
 * @param appSlug slug do projeto (ex.: `"meu-barbeiro"`).
 * @param versionName versão legível (ex.: `"1.4.0"`).
 * @param versionCode build number monotônico.
 * @param isDebug se é build de debug (ver [CrashReporter.initFromBuildConfig], que lê de [BuildInfo]).
 */
fun crashReporterConfigFromBuildConfig(
    dsn: String,
    appSlug: String,
    versionName: String,
    versionCode: Int,
    isDebug: Boolean,
    sendDefaultPii: Boolean = false,
    tracesSampleRate: Double = 0.0,
): CrashReporterConfig = CrashReporterConfig(
    dsn = dsn.trim(),
    environment = if (isDebug) CrashEnvironment.DEBUG else CrashEnvironment.PRODUCTION,
    release = crashReporterRelease(appSlug, versionName, versionCode),
    sendDefaultPii = sendDefaultPii,
    tracesSampleRate = tracesSampleRate,
    enabled = dsn.isNotBlank(),
)

/**
 * **Inicialização sem boilerplate** — elimina o `expect/actual` de DSN/versão/debug que ~37 apps
 * repetiam para montar o [CrashReporterConfig]. O app passa só o que é intrinsecamente seu
 * (DSN + slug + versão, que vêm do `BuildConfig`/Info.plist e não podem morar na lib); o resto
 * (ambiente, release canônica, gate de DSN vazio, leitura de `isDebug`) fica na lib.
 *
 * `isDebug` é lido de [BuildInfo] (expect/actual **já existente** na lib) — o app não precisa mais
 * de um `expect/actual` próprio só para isso. Ainda é sobreponível (testes/casos especiais).
 *
 * Uso (bootstrap do app):
 * ```kotlin
 * // Android: BuildConfig.SENTRY_DSN / VERSION_NAME / VERSION_CODE
 * koin.get<CrashReporter>().initFromBuildConfig(
 *     dsn = BuildConfig.SENTRY_DSN,
 *     appSlug = "meu-barbeiro",
 *     versionName = BuildConfig.VERSION_NAME,
 *     versionCode = BuildConfig.VERSION_CODE,
 * )
 * ```
 *
 * @see crashReporterConfigFromBuildConfig para a derivação pura (testável).
 */
fun CrashReporter.initFromBuildConfig(
    dsn: String,
    appSlug: String,
    versionName: String,
    versionCode: Int,
    isDebug: Boolean = BuildInfo.isDebug,
    sendDefaultPii: Boolean = false,
    tracesSampleRate: Double = 0.0,
) {
    init(
        crashReporterConfigFromBuildConfig(
            dsn = dsn,
            appSlug = appSlug,
            versionName = versionName,
            versionCode = versionCode,
            isDebug = isDebug,
            sendDefaultPii = sendDefaultPii,
            tracesSampleRate = tracesSampleRate,
        ),
    )
}

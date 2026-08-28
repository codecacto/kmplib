package br.com.codecacto.kmplib.observability

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Módulo Koin da observabilidade de crashes.
 *
 * Expõe um [CrashReporter] singleton (implementação Sentry/GlitchTip). O app inclui o módulo
 * e chama [CrashReporter.init] no bootstrap com o DSN próprio:
 * ```kotlin
 * startKoin { modules(crashReporterModule, /* ... */) }
 * // no bootstrap:
 * koin.get<CrashReporter>().init(CrashReporterConfig(dsn = ..., environment = ..., release = ...))
 * ```
 */
val crashReporterModule: Module = module {
    single<CrashReporter> { createCrashReporter() }
}

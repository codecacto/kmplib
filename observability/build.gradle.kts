plugins {
    id("kmplib.module")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            // O módulo expõe `crashReporterModule`, cujo tipo `Module` é do Koin — logo api().
            api(libs.koin.core)
            // `implementation` de propósito: SentryCrashReporter é internal e a interface pública
            // `CrashReporter` é NEUTRA ao fornecedor. É o que permite trocar de backend de crash
            // sem tocar em nenhum app.
            implementation(libs.sentry.kmp)
        }
    }
}

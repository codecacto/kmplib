plugins {
    id("kmplib.module")
}

// `kmplib-widget` (2.355.0, OPT-IN) — a PONTE de dados app ↔ widget de tela inicial: instantâneo gravado
// onde o widget lê (Android: SharedPreferences lidas pelo Glance; iOS: App Group `UserDefaults(suiteName:)`),
// pedido de atualização e a renovação diária à meia-noite (Android: WorkManager). A UI do widget
// (GlanceAppWidget / extensão SwiftUI) fica no app — receita em `references/widget.md`.
// NÃO está no umbrella.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            // A renovação à meia-noite com o app fechado (trabalho adiável = WorkManager, o recomendado).
            implementation(libs.androidx.work.runtime)
        }
    }
}

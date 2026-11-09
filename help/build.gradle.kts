plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

// `kmplib-help` (2.352.0) — Biblioteca de vídeos e tela de ajuda dos produtos (`GET /public/ajuda` e
// `POST /public/ajuda/perguntar` do apps-api central).
//
// Módulo próprio, e não dentro do `kmplib-central`, porque a tela toca vídeo: o `kmplib-central` entra
// em todo app e o `kmplib-video` traz a Media3/ExoPlayer, que app sem vídeo nenhum não pode herdar por
// causa de um serviço online (mesmo critério da separação do `kmplib-video` na 2.190.0).
kotlin {
    sourceSets {
        // `redactMediaUrl` (kmplib-core): URL de mídia sem assinatura no toString.
        all { languageSettings.optIn("br.com.codecacto.kmplib.core.util.KmpLibCoreInternalApi") }

        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            api(project(":kmplib-video"))

            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            // `viewModel { }` na composição e `LifecycleResumeEffect` (toda listagem recarrega ao voltar).
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            // O voltar do sistema fecha a resposta e o detalhe antes de sair da tela.
            implementation(libs.compose.ui.backhandler)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}

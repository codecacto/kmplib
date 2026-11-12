plugins {
    // 2.360.0: compose — o push ganhou a seção "Notificações" de Configurações e o roteador do toque
    // (que abre o comunicado no mesmo modal dos Comunicados).
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            // KMPNotifier aparece na API pública (o listener que o app registra).
            api(libs.kmpnotifier)
            // 2.360.0: registro central do aparelho no apps-api (`/push/v1/aparelhos`) e o roteador do
            // toque (`cc_acao = comunicado` busca o aviso pelo AnnouncementService do central e abre o
            // AnnouncementDialog da ui). O central NÃO depende do push — sem ciclo, e quem usa só o
            // central não leva FCM/KMPNotifier.
            api(project(":kmplib-central"))
            // 2.362.0: a abertura de campanha conta também `push_aberto` no Umami, quando o app liga o
            // `Analytics` (no-op sem initialize). O analytics NÃO depende do push — sem ciclo.
            implementation(project(":kmplib-analytics"))
            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
        }

        androidMain.dependencies {
            // FCM: o serviço do KMPNotifier no Android precisa do Firebase Messaging.
            api(libs.firebase.common.android)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}

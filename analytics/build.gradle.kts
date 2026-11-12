plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

// `kmplib-analytics` (2.361.0) — telas e eventos-chave do app no **Umami** auto-hospedado da fábrica
// (`https://metricas.codecacto.com.br`). O Umami não tem SDK móvel: o envio é o `POST /api/batch`
// (com recuo para o `POST /api/m`, a coleta renomeada) que o próprio script do site usa.
//
// Módulo próprio, e não dentro do `kmplib-central`, porque não fala com o apps-api e traz o
// `navigation-runtime` (o rastreio automático de tela lê o `NavController`).
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            // `AutomationMode` (build de teste não envia), `InstallId` (id anônimo da instalação) e
            // `KmpLibAudience` (app infantil sem id).
            api(project(":kmplib-platform"))

            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            // `NavController` aparece na assinatura pública do `TrackNavigationScreens`. O app já traz
            // o `navigation-compose` na MESMA versão (2.9.1), que depende deste.
            api(libs.navigation.runtime)
        }

        androidMain.dependencies {
            // ProcessLifecycleOwner: reenviar a fila ao voltar ao primeiro plano e esvaziá-la ao sair.
            implementation(libs.androidx.lifecycle.process)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}

plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))

            // Notificação agendada, biometria, permissões, áudio, TTS, lanterna, assinatura e
            // checagem de versão. Tudo isso tem superfície Compose (o host de permissão, o pad de
            // assinatura, o diálogo de atualização), daí o convention plugin com Compose.
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            implementation(compose.components.uiToolingPreview)
            @Suppress("DEPRECATION")
            implementation(compose.materialIconsExtended)

            api(libs.kotlinx.datetime)
            api(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            // AppUpdateService consulta o admin-api; o teste sobe um MockEngine no lugar da rede.
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            // FileProvider do ShareHandler.shareFile — declarado explicitamente porque o
            // <provider> do manifesto da lib referencia a classe em runtime, e não dá para contar
            // com qual AndroidX o app mantém.
            implementation(libs.androidx.core)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.biometric)
            // FragmentActivity está na API pública do androidMain (KmpLib.setActivity), exigida
            // por Credential Manager, permissões e biometria. Até a 2.100.0 a classe só chegava ao
            // consumidor por acaso, transitivamente pelo Firebase.
            api(libs.androidx.fragment)
        }
    }
}

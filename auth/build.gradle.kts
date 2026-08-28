plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            // As telas de login/cadastro pedem permissão e abrem o navegador do sistema.
            api(project(":kmplib-platform"))
            // Firebase Auth continua atendendo os 10 projetos que já têm projeto Firebase; o
            // own-auth (backlib-auth-local) não passa por aqui. api() porque o `IAuthRepository`
            // do módulo firebase aparece na assinatura pública.
            api(project(":kmplib-firebase"))

            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }

        androidMain.dependencies {
            // Credential Manager — o caminho oficial do Google Sign-In nativo no Android.
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.google.googleid)
            // Cofre cifrado no Android Keystore para o refresh token do own-auth.
            implementation(libs.androidx.security.crypto)
        }
    }
}

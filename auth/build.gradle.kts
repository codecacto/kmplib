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
            // SEM kmplib-firebase (2.291.0): o own-auth só usa os contratos NEUTROS
            // (`IAuthRepository`, `User`, `AuthException`), que moram no kmplib-core no mesmo
            // pacote `firebase.auth`. A dependência antiga levava Firebase Auth/Storage/Remote Config
            // (e, até a 2.290.0, `firebase-analytics` + AD_ID) a todo app com login. Quem usa
            // Firebase Auth (os projetos que já têm projeto Firebase) declara `kmplib-firebase`.

            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)

            // AccountDeletionGate (2.301.0): `viewModel { }` na composição e o voltar do sistema
            // segurado enquanto a conta com exclusão programada cobre a área logada.
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.compose.ui.backhandler)
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

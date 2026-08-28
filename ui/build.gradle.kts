plugins {
    id("kmplib.module.compose")
}

// Os recursos compartilhados da lib (as 4 traduções, o logo CodeCacto, os ícones de Google e
// Apple) moram aqui: `Res` é uma classe gerada por MÓDULO, e gerá-la em dois lugares daria duas
// classes de mesmo nome no mesmo pacote. Quem precisa delas (o leitor de código de barras, por
// exemplo) depende de `kmplib-ui`.
compose.resources {
    publicResClass = true
    packageOfResClass = "br.com.codecacto.kmplib.generated.resources"
    generateResClass = always
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-mask"))
            api(project(":kmplib-platform"))

            // A kmplib É uma biblioteca de UI: todo composable público dela nomeia tipos do
            // Compose, então api() — mesmo padrão do androidx.compose.material3, que declara
            // `api` para ui e foundation.
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            @Suppress("DEPRECATION")
            implementation(compose.materialIconsExtended)

            // ViewModel é SUPERTIPO público do BaseViewModel, a classe-base de todo ViewModel de
            // todo app do ecossistema.
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.kotlinx.datetime)
            // Carrossel e imagem remota dos componentes.
            api(libs.coil.compose)
            implementation(libs.coil.network.ktor)
        }

        commonTest.dependencies {
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core)
            // Correção de orientação no seletor de imagem.
            implementation(libs.androidx.exifinterface)
        }
    }
}

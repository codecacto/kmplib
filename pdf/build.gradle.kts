plugins {
    // Compose entrou na 2.190.0, com o VISUALIZADOR (`pdf.viewer`). Até então o módulo só GERAVA
    // PDF e não tinha um `@Composable` sequer — ver o KDoc do convention plugin.
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            // Gera o arquivo e o entrega ao compartilhamento do sistema.
            api(project(":kmplib-platform"))
            // Os textos do visualizador (4 idiomas) moram nos recursos da lib, em `kmplib-ui`
            // (2.219.0) — `Res` é gerado num módulo só. `implementation`: nenhum tipo do `ui`
            // aparece na API pública do `pdf`.
            implementation(project(":kmplib-ui"))
            api(libs.kotlinx.serialization.json)
            // Baixa o PDF remoto pelo `createHttpClient` do core e o guarda no `BlobStore` —
            // ver `pdf/viewer/PdfViewerState.kt`.
            api(libs.ktor.client.core)
        }

        commonTest.dependencies {
            // `MockEngine` — o caminho remoto do visualizador (cache primeiro, 404, resposta que
            // não é PDF) se prova sem rede.
            implementation(libs.ktor.client.mock)
        }
    }
}

plugins {
    id("kmplib.module.compose")
}

// `kmplib-licenses` (2.384.0) — a tela "Licenças de código aberto" que todo app mostra a partir do Sobre.
//
// MIT, Apache-2.0, BSD e OFL pedem atribuição: o app que distribui o binário tem de mostrar o aviso e o
// texto da licença das bibliotecas que leva. Solução padrão do ecossistema Compose Multiplatform:
// AboutLibraries (mikepenz) — o plugin Gradle lê o grafo de dependências do APP e gera o JSON
// (`aboutlibraries.json`, em `composeResources/files/`), e o runtime desenha a lista nas duas plataformas.
// O plugin quem aplica é o app, pela `br.com.codecacto.kmplib.licenses` do `kmplib-gradle-plugin`, que
// fixa a versão (a mesma deste runtime) e o caminho do arquivo.
//
// Módulo próprio, e não dentro do `kmplib-ui`, para o `kmplib-ui` não arrastar o AboutLibraries para
// quem declara módulos granulares e não liga a tela.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            // `Libs`/`Library` aparecem na API pública (`parseOpenSourceLicenses`).
            api(libs.aboutlibraries.core)
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.compose.components.resources)
        }
        // O parser Android do AboutLibraries lê com `org.json` (stub no android.jar do teste de unidade).
        androidUnitTest.dependencies {
            implementation(libs.json.org)
        }
    }
}

plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

// `kmplib-fiscal` (2.380.0) — a NFS-e no APP: o par mobile do `/fiscal` da weblib (0.340.0), sobre as rotas do
// PRODUTO que a `backlib-fiscal` (0.278.0) monta (`fiscalProductRoutes`, ex. `/locaki/v2/fiscal`). O app nunca fala
// com o Fiscal Central: fala com o próprio backend, com o token da sessão (`DomainApiClient`).
//
// Tem: contrato de fio (os nomes JSON da `FiscalModels.kt` da backlib), `FiscalApi`, `FiscalRepository`, as regras
// puras (regime em palavras ↔ códigos do leiaute, validação, montagem do pedido), os ViewModels (MVI da lib) e as
// telas: cadastro fiscal em etapas, lista de notas, detalhe (PDF, cancelar, substituir) e emitir.
//
// NÃO está no umbrella: só produto com o módulo fiscal declara (critério do `kmplib-chat`/`kmplib-inspection`).
// Nota fiscal de serviço é coisa do Brasil: os textos são só pt-BR (`values/`), como no `/fiscal` da weblib.
compose.resources {
    publicResClass = false
    packageOfResClass = "br.com.codecacto.kmplib.fiscal.generated.resources"
    generateResClass = always
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            api(project(":kmplib-platform"))
            api(project(":kmplib-mask"))
            // Estado/Cidade em seletores (AddressFields) e o código IBGE do município (BrazilianCities).
            api(project(":kmplib-brdata"))
            api(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
            api(libs.kotlinx.datetime)
            api(libs.compose.components.resources)
            // `viewModel { }` na composição e `LifecycleResumeEffect` (a lista recarrega ao voltar).
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
        }
    }
}

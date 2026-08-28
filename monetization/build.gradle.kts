plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            // O relato de falha de pagamento vai para o Sentry.
            api(project(":kmplib-observability"))

            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)

            // RevenueCat — `implementation` está auditado e correto: o repositório concreto é
            // internal e a API pública é NEUTRA ao fornecedor (PurchaseResult, PurchasePackage,
            // PurchaseErrorCode). purchases-kmp-datetime fica DE FORA de propósito: ele referencia
            // o antigo kotlinx.datetime.Instant, hoje typealias, e o R8 falha no release com
            // "Missing class kotlinx.datetime.Instant".
            implementation(libs.purchases.kmp.core)
            implementation(libs.purchases.kmp.result)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}

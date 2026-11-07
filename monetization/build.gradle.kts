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
            // AutomationMode (2.350.0): alerta de pagamento não sai de processo de teste (Test Lab,
            // runner de QA, dublê). Já vinha transitivo pelo kmplib-ui; declarado por ser uso direto.
            // Sem ciclo: o kmplib-platform só depende do kmplib-core.
            api(project(":kmplib-platform"))

            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)
            // PaywallHost (2.224.0): relê a oferta a cada ON_RESUME e coleta o estado com lifecycle.
            implementation(libs.androidx.lifecycle.runtime.compose)
            // SubscriptionBlockGate (2.300.0): o voltar do sistema não desempilha a navegação coberta.
            implementation(libs.compose.ui.backhandler)

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

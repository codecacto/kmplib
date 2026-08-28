plugins {
    id("kmplib.module")
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // api() vs implementation() é regra do Gradle, não preferência: tipo que aparece na
            // assinatura pública precisa de api(), senão o consumidor não consegue NOMEAR o que a
            // lib exige dele e acaba declarando a coordenada por conta própria, adivinhando a
            // versão. Auditoria original em 2.101.0, mantida aqui módulo a módulo.

            // Flow/StateFlow/CoroutineScope são públicos (ConnectivityObserver.isOnline, os
            // repositórios REST, DailyQuotaStore).
            api(libs.kotlinx.coroutines.core)
            // KSerializer/Json são públicos (RestCrudEntity.serializer, HttpClientOptions.json,
            // DefaultHttpClientJson).
            api(libs.kotlinx.serialization.json)
            // Instant/LocalDate/TimeZone são públicos (TimeUtils, DateFormatters(timeZone),
            // DailyQuotaStore(timeZone)).
            api(libs.kotlinx.datetime)
            // HttpClient é público (RestConfig, DomainApiClient, createHttpClient devolve um).
            api(libs.ktor.client.core)

            // Plugins que o createHttpClient instala por dentro — nenhum tipo deles aparece em
            // assinatura pública (HttpLogLevel é enum próprio; o mapeamento é internal).
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            // Manda Accept-Encoding e descomprime a resposta. Medido no Cidade Conectada:
            // /v1/categories 26.847 B -> 8.172 B, /v1/feed?size=20 15.065 B -> 4.815 B. -69%.
            implementation(libs.ktor.client.encoding)
        }

        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)
            // Engine oficial recomendado no Android.
            implementation(libs.ktor.client.okhttp)
        }
    }
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kover)
    alias(libs.plugins.sqldelight)
}

group = "br.com.codecacto"
version = "2.66.0"

// =============================================================================
// SQLDelight — banco local do módulo sync/ (offline-first genérico — T1a)
// =============================================================================
//
// Schema agnóstico de domínio (synced_entity + sync_cursor) em
// src/commonMain/sqldelight. Gera a classe `SyncDatabase` em
// `br.com.codecacto.kmplib.sync.db`. Drivers por plataforma:
//  - Android: AndroidSqliteDriver (androidMain)
//  - iOS: NativeSqliteDriver (iosMain — só valida em host macOS, dívida conhecida)
//
sqldelight {
    databases {
        create("SyncDatabase") {
            packageName.set("br.com.codecacto.kmplib.sync.db")
            // SQLite 3.38 → habilita UPSERT (ON CONFLICT DO UPDATE) usado no espelho.
            dialect(libs.sqldelight.dialect.sqlite)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "br.com.codecacto.kmplib.generated.resources"
    generateResClass = always
}

// =============================================================================
// Kover — cobertura de testes
// =============================================================================
//
// Roda automaticamente com :library:koverHtmlReport e :library:koverXmlReport.
// Threshold de 40% no `koverVerify` (não-bloqueante em CI por enquanto;
// quando estabilizar, mover `koverVerify` para os jobs obrigatórios em
// .github/workflows/tests.yml).
//
// Exclusões: classes geradas, telas Compose UI (testadas separadamente),
// holders Android que dependem de Activity/Context, código iOS bridge.
//
kover {
    reports {
        filters {
            excludes {
                // Classes geradas pelo Compose
                classes("*generated.resources*")
                classes("*ComposableSingletons*")
                // Holders são providers para Android lifecycle — não testáveis em unit test
                classes("*Holder")
                // Adapters Android internos (não API pública)
                classes("br.com.codecacto.kmplib.firebase.auth.GoogleAuthHolder")
                classes("br.com.codecacto.kmplib.firebase.crashlytics.CrashlyticsHolder")
                classes("br.com.codecacto.kmplib.platform.BiometricAuthHolder")
                classes("br.com.codecacto.kmplib.platform.NotificationSchedulerHolder")
                classes("br.com.codecacto.kmplib.platform.ShareHandlerHolder")
                classes("br.com.codecacto.kmplib.platform.UrlLauncherHolder")
                classes("br.com.codecacto.kmplib.media.AudioPlayerHolder")
                classes("br.com.codecacto.kmplib.platform.permission.PermissionHostHolder")
            }
        }

        verify {
            rule {
                minBound(40)  // bound conservador. Aumentar conforme cobertura crescer.
            }
        }
    }
}

android {
    namespace = "br.com.codecacto.kmplib"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    androidTarget()

    jvmToolchain(17)

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "KmpLib"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Coroutines
            implementation(libs.kotlinx.coroutines.core)

            // Serialization
            implementation(libs.kotlinx.serialization.json)

            // DateTime
            implementation(libs.kotlinx.datetime)

            // Ktor
            implementation(libs.ktor.client.core)
            // HttpClientFactory (createHttpClient) — plugins opcionais do factory padrão:
            // logging (opt-in) e ContentNegotiation JSON (opt-in p/ apps que fazem REST de domínio).
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Push Notifications
            api(libs.kmpnotifier)

            // Lifecycle ViewModel
            implementation(libs.androidx.lifecycle.viewmodel)

            // Firebase GitLive (Auth + Storage + Remote Config; SEM Firestore — decisão de arquitetura:
            // dados via REST/apps-api, Firebase só para Auth e Crashlytics)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.storage)
            implementation(libs.firebase.config)

            // RevenueCat
            implementation(libs.purchases.kmp.core)
            implementation(libs.purchases.kmp.result)
            // NOTA: purchases-kmp-datetime NÃO é incluído de propósito. Ele referencia o antigo
            // `kotlinx.datetime.Instant` (classe real removida no kotlinx-datetime 0.7.x, hoje typealias
            // de kotlin.time.Instant), o que faz o R8 falhar no release ("Missing class kotlinx.datetime.Instant").
            // Nenhuma extensão datetime do RevenueCat é usada aqui (toSubscriptionInfo não lê expiration
            // via essas helpers), então o módulo é código morto e fica de fora.

            // Compose (for VisualTransformation and UI Components)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            // Compose @Preview support (multiplatform) — módulo do próprio Compose MP
            implementation(compose.components.uiToolingPreview)
            @Suppress("DEPRECATION")
            implementation(compose.materialIconsExtended)

            // Coil 3 — image loading for Custom Ads
            api(libs.coil.compose)
            implementation(libs.coil.network.ktor)

            // SQLDelight — sync offline-first (T1a). runtime + coroutines (Flow das queries).
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)

            // AndroidX Core — FileProvider (ShareHandler.shareFile). Declarado
            // explicitamente para garantir a classe androidx.core.content.FileProvider
            // referenciada pelo <provider> do AndroidManifest da lib em runtime,
            // independente de quais outras deps AndroidX o app mantiver.
            implementation(libs.androidx.core)

            // Firebase Android (required by GitLive) - exposed as api() for consumer projects
            api(libs.firebase.auth.android)
            api(libs.firebase.storage.android)
            api(libs.firebase.common.android)
            api(libs.firebase.crashlytics.android)
            api(libs.firebase.analytics.android)
            api(libs.firebase.config.android)

            // Firebase Crashlytics GitLive (KMP)
            implementation(libs.firebase.crashlytics)

            // AndroidX Activity Compose (for ImagePicker camera/gallery launchers)
            implementation(libs.androidx.activity.compose)

            // AndroidX ExifInterface (for ImagePicker orientation correction)
            implementation(libs.androidx.exifinterface)

            // AndroidX Biometric
            implementation(libs.androidx.biometric)

            // Credentials (for Google Sign-In)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.google.googleid)

            // Google Maps (GAP-02) — MapView/MapMarker via maps-compose
            implementation(libs.maps.compose)
            implementation(libs.play.services.maps)

            // Location (GAP-04) — LocationProvider via Fused Location Provider
            implementation(libs.play.services.location)

            // Camera + OCR de placa (GAP-ME-01) — CameraView via CameraX + ML Kit
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.mlkit.text.recognition)

            // SQLDelight driver Android (sync — T1a)
            implementation(libs.sqldelight.driver.android)

            // Ktor engine Android (createHttpClient) — OkHttp (engine oficial recomendado no Android).
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            // SQLDelight driver nativo iOS (sync — T1a). Só valida em host macOS.
            implementation(libs.sqldelight.driver.native)

            // Ktor engine iOS (createHttpClient) — Darwin (engine oficial recomendado no iOS/K/N).
            implementation(libs.ktor.client.darwin)
        }

        // Note: firebase-crashlytics GitLive não suporta iosX64.
        // A impl iOS do CrashlyticsService usa NSLog como fallback.
        // O Crashlytics real no iOS funciona via SDK nativo no Xcode.
    }
}

mavenPublishing {
    publishToMavenCentral()

    // Só assinar se tiver configuração de GPG (para Maven Central)
    // Para Maven Local, não precisa assinatura
    if (project.hasProperty("signing.keyId")) {
        signAllPublications()
    }

    coordinates(group.toString(), "kmplib", version.toString())

    pom {
        name = "CodeCacto KMP Library"
        description = "Biblioteca KMP com utilitários reutilizáveis para Android e iOS"
        inceptionYear = "2025"
        url = "https://github.com/codecacto/kmplib"
        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }
        developers {
            developer {
                id = "codecacto"
                name = "CodeCacto"
                url = "https://codecacto.com.br"
            }
        }
        scm {
            url = "https://github.com/codecacto/kmplib"
            connection = "scm:git:git://github.com/codecacto/kmplib.git"
            developerConnection = "scm:git:ssh://github.com/codecacto/kmplib.git"
        }
    }
}

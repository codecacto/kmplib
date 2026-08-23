import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.konan.target.HostManager

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
version = "2.140.0"

// =============================================================================
// Guarda de host — alvos Apple só existem em macOS (padrão-ouro KMP)
// =============================================================================
//
// Kotlin/Native não compila iOS fora de macOS (precisa do Xcode/SDKs da Apple). Antes desta
// guarda, os alvos `iosX64/iosArm64/iosSimulatorArm64` eram SEMPRE declarados: no Linux o KGP os
// desabilitava silenciosamente (`kotlin.native.ignoreDisabledTargets=true`), mas o
// `publishToMavenLocal` ainda gravava no módulo Gradle (`kmplib-<v>.module`) variantes
// `iosArm64ApiElements-published` etc. apontando (`available-at`) para artefatos
// `kmplib-iosarm64`/`-iosx64`/`-iossimulatorarm64` que NUNCA eram publicados — um módulo
// incoerente, que quebra o fallback `mavenLocal` de qualquer clone isolado.
//
// Solução oficial (HostManager do próprio Kotlin, mesma checagem que o KGP usa internamente):
// declarar os alvos Apple apenas quando o host é macOS. No Linux/CI sobram `commonMain` +
// `androidTarget()` (klib de metadata + AAR), coerentes e publicáveis; num Mac o publish sai
// completo, com os artefatos iOS.
//
// iOS NÃO é desativado — é CONDICIONAL AO HOST. Para forçar a tentativa (ex.: diagnóstico):
//   ./gradlew publishToMavenLocal -Pkmplib.forceAppleTargets=true
//
val appleTargetsEnabled: Boolean =
    HostManager.hostIsMac || providers.gradleProperty("kmplib.forceAppleTargets").orNull == "true"

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

    // Alvos Apple: só em macOS (ver `appleTargetsEnabled` acima). Em Linux/Windows o build
    // continua válido para commonMain + Android, e o publish gera um módulo coerente.
    if (appleTargetsEnabled) {
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
    } else {
        logger.lifecycle(
            "[kmplib] Host não-macOS: alvos iOS não declarados (publish sai com commonMain + Android). " +
                "Publique de um Mac para incluir os artefatos iOS."
        )
    }

    sourceSets {
        commonMain.dependencies {
            // =================================================================
            // api() vs implementation() — regra do Gradle, não preferência
            // =================================================================
            //
            // Tipo que aparece na API PÚBLICA da lib exige `api()`. Com
            // `implementation()` a dependência não é exportada, então o consumidor
            // NÃO CONSEGUE NEM NOMEAR o tipo que a lib exige dele — e acaba
            // declarando a coordenada por conta própria, ADIVINHANDO a versão.
            //
            // Isso não é cosmético. Se o app declarar uma versão diferente, o Gradle
            // resolve para a MAIOR; no kotlinx-datetime 0.7.x o `Instant` deixou de ser
            // classe própria e virou typealias de `kotlin.time.Instant`, e o resultado
            // é R8 falhando no release com "Missing class kotlinx.datetime.Instant"
            // (mesma armadilha documentada no bloco do RevenueCat, abaixo).
            // Ou seja: compila em debug e quebra no release.
            //
            // Auditoria completa da API pública em 2.101.0. As deps abaixo marcadas
            // `implementation` foram VERIFICADAS como uso interno (nenhum tipo delas
            // aparece em assinatura pública).

            // Coroutines — api(): Flow/StateFlow/CoroutineScope são API pública
            // (BaseViewModel.state/effect, ConnectivityObserver.isOnline, AudioPlayer,
            // TtsController, SpeechRecognizer, SyncEngine.state, PurchaseManager.isPremium,
            // SyncStore.accountScope, DefaultSyncEngine(scope = ...)).
            api(libs.kotlinx.coroutines.core)

            // Serialization — api(): KSerializer/Json/JsonObject são API pública
            // (SyncableEntity.serializer, RestCrudEntity.serializer, OwnAuthConfig.json,
            // HttpClientOptions.json, val DefaultHttpClientJson, PermissionMatrixJson,
            // RestIdResolver.resolveFor(json = ...)).
            api(libs.kotlinx.serialization.json)

            // DateTime — api(): Instant/LocalDate/LocalDateTime/LocalTime/TimeZone são
            // API pública (astro/MoonPhaseEvent.instant + dateIn(TimeZone), ui/calendar
            // ScheduleEvent.start, NotificationScheduler.scheduleNotification(scheduledTime),
            // core/util TimeUtils, SubscriptionInfo.expirationDate, DateFormatters(timeZone),
            // DailyQuotaStore(timeZone), NotificationRescheduling).
            api(libs.kotlinx.datetime)

            // Ktor — api(): HttpClient é API pública (FeedbackConfig/ContactConfig/
            // DeveloperConfig/OwnAuthConfig/CustomAdConfig/CentralServicesConfig/RestConfig/
            // AppUpdateConfig/DomainApiClient/AdminApiEntitlementRepository), e ainda
            // createHttpClient(): HttpClient, HttpClientEngine, HttpClientConfig<*> no
            // lambda de configuração e ResponseException.quotaExceededOrNull().
            api(libs.ktor.client.core)
            // HttpClientFactory (createHttpClient) — plugins opcionais do factory padrão:
            // logging (opt-in) e ContentNegotiation JSON (opt-in p/ apps que fazem REST de
            // domínio). Uso INTERNO: `HttpLogLevel` é enum próprio da lib e o mapeamento
            // p/ o LogLevel do Ktor é `internal`; ContentNegotiation é instalada dentro do
            // factory. Nenhum tipo destes três aparece em assinatura pública.
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            // Push Notifications
            api(libs.kmpnotifier)

            // Lifecycle ViewModel — api(): `ViewModel` é SUPERTIPO público de
            // BaseViewModel, a classe-base de todo ViewModel de todo app do ecossistema.
            api(libs.androidx.lifecycle.viewmodel)

            // Firebase GitLive (Auth + Storage + Remote Config; SEM Firestore — decisão de arquitetura:
            // dados via REST/apps-api, Firebase só para Auth e Crashlytics).
            // `implementation` CORRETO (auditado em 2.101.0): FirebaseAuth/FirebaseUser/
            // FirebaseStorage/Data são `private`/`internal` — a API pública é toda em tipos
            // próprios (IAuthRepository, User, StorageService). É o que permite a um projeto
            // own-auth consumir a lib sem falar Firebase.
            implementation(libs.firebase.auth)
            implementation(libs.firebase.storage)
            implementation(libs.firebase.config)

            // RevenueCat — `implementation` CORRETO (auditado em 2.101.0):
            // RevenueCatPurchaseRepository é `internal` e o mapeamento
            // PurchasesError/PurchasesErrorCode → PurchaseErrorCode também. A API pública é
            // neutra ao fornecedor (PurchaseResult/PurchasePackage/PurchaseErrorCode).
            implementation(libs.purchases.kmp.core)
            implementation(libs.purchases.kmp.result)
            // NOTA: purchases-kmp-datetime NÃO é incluído de propósito. Ele referencia o antigo
            // `kotlinx.datetime.Instant` (classe real removida no kotlinx-datetime 0.7.x, hoje typealias
            // de kotlin.time.Instant), o que faz o R8 falhar no release ("Missing class kotlinx.datetime.Instant").
            // Nenhuma extensão datetime do RevenueCat é usada aqui (toSubscriptionInfo não lê expiration
            // via essas helpers), então o módulo é código morto e fica de fora.

            // Compose — api(): a kmplib É uma biblioteca de UI, e TODO composable público
            // dela nomeia tipos do Compose. Mesmo padrão das bibliotecas oficiais
            // (androidx.compose.material3 declara `api` para ui e foundation).
            //  - compose.ui        → Modifier, Color, Dp, ImageVector, TextStyle, Shape
            //                        (133 assinaturas só com `modifier: Modifier = Modifier`)
            //  - compose.foundation → RowScope/ColumnScope/BoxScope nos slots
            //                        (AppTopBar.actions, FormContainer.content,
            //                         ScrollableFillBox/RefreshableBox.content)
            //  - compose.material3  → ColorScheme (createDarkColorScheme/createLightColorScheme/
            //                        createHighContrast*), Typography (scaleTypography),
            //                        SnackbarHostState (PaywallScreen)
            //  - components.resources → `Res` é gerado PÚBLICO (publicResClass = true, ver bloco
            //                        compose.resources acima) e expõe StringResource/DrawableResource
            api(libs.compose.ui)
            api(libs.compose.foundation)
            api(libs.compose.material3)
            api(libs.compose.components.resources)
            // Compose @Preview support (multiplatform) — módulo do próprio Compose MP.
            // Uso INTERNO: as 28 funções `@Preview` da lib são todas `private`.
            implementation(compose.components.uiToolingPreview)
            // Ícones: a lib usa VALORES (Icons.Outlined.*) como default de parâmetro, nunca
            // um TIPO deste artefato — o tipo é `ImageVector`, que vem do compose.ui acima.
            @Suppress("DEPRECATION")
            implementation(compose.materialIconsExtended)

            // Coil 3 — image loading for Custom Ads. O fetcher de rede é uso interno.
            api(libs.coil.compose)
            implementation(libs.coil.network.ktor)

            // SQLDelight — sync offline-first (T1a). `runtime` é api() (SqlDriver/SyncDatabase
            // aparecem em createSyncDatabase/SqlDelightSyncStore); `coroutines-extensions` é uso
            // interno (só as extensões asFlow/mapToList dentro do SyncStore).
            api(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)

            // Observabilidade de crashes — sentry-kotlin-multiplatform (padrão-ouro; reporta para
            // Sentry/GlitchTip). O artefato KMP já expõe Android + iOS reais (Sentry Android + Sentry
            // Cocoa via cinterop); a API é 100% commonMain, sem expect/actual. Ver module `observability`.
            // `implementation` CORRETO (auditado em 2.101.0): SentryCrashReporter é `internal` e a
            // interface pública `CrashReporter` é NEUTRA ao fornecedor, de propósito.
            implementation(libs.sentry.kmp)

            // Koin (DI padrão do ecossistema) — api() porque a lib expõe módulos Koin prontos
            // (crashReporterModule) cujo tipo `org.koin.core.module.Module` é público. Todo app
            // consumidor já traz Koin, então é retrocompatível.
            api(libs.koin.core)
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

            // EncryptedSharedPreferences (Jetpack Security) — cofre cifrado ancorado no Android
            // Keystore para o refresh token da autenticação própria (auth/SecureTokenStorage).
            implementation(libs.androidx.security.crypto)

            // Firebase Android (required by GitLive) - exposed as api() for consumer projects.
            // Crashlytics saiu (2.75.0): observabilidade de crashes migrou para sentry-kotlin-multiplatform
            // (módulo `observability`). Firebase Analytics mantido (base para Auth/Config).
            api(libs.firebase.auth.android)
            api(libs.firebase.storage.android)
            api(libs.firebase.common.android)
            api(libs.firebase.analytics.android)
            api(libs.firebase.config.android)

            // AndroidX Activity Compose (for ImagePicker camera/gallery launchers)
            implementation(libs.androidx.activity.compose)

            // AndroidX ExifInterface (for ImagePicker orientation correction)
            implementation(libs.androidx.exifinterface)

            // AndroidX Fragment — api(): `FragmentActivity` está na API pública do androidMain
            // (`KmpLib.setActivity(activity: FragmentActivity)`, exigido por Credential Manager,
            // permissões e biometria). Até a 2.100.0 a classe só chegava ao consumidor por ACASO,
            // transitivamente pelo api(firebase-auth-android) → play-services-base; um projeto
            // own-auth (sem Firebase) ficaria sem conseguir nomear o tipo que a lib exige.
            api(libs.androidx.fragment)

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

            // Leitura de código de barras (GAP-CV-M-01) — ML Kit Barcode Scanning sobre a MESMA
            // base CameraX (CameraXPreview). Modelo embarcado: funciona offline no primeiro uso.
            implementation(libs.mlkit.barcode.scanning)

            // SQLDelight driver Android (sync — T1a)
            implementation(libs.sqldelight.driver.android)

            // Ktor engine Android (createHttpClient) — OkHttp (engine oficial recomendado no Android).
            implementation(libs.ktor.client.okhttp)
        }

        // O source set `iosMain` só existe quando há alvo Apple declarado (ver guarda de host).
        if (appleTargetsEnabled) {
            iosMain.dependencies {
                // SQLDelight driver nativo iOS (sync — T1a). Só valida em host macOS.
                implementation(libs.sqldelight.driver.native)

                // Ktor engine iOS (createHttpClient) — Darwin (engine oficial recomendado no iOS/K/N).
                implementation(libs.ktor.client.darwin)
            }
        }

        // Observabilidade de crashes no iOS: o artefato sentry-kotlin-multiplatform publica
        // iosArm64/iosSimulatorArm64/iosX64 com cinterop para o Sentry Cocoa SDK. A linkagem final
        // acontece no host macOS (fora deste build Linux). Ver módulo `observability`.
    }
}

// =============================================================================
// Guarda de release — Maven Central só sai de um host macOS
// =============================================================================
//
// `publishToMavenLocal` num host Linux é LEGÍTIMO e suportado (dev local / fallback do
// `includeBuild`): sai `commonMain` + Android (AAR), com o módulo Gradle coerente. Mas o artefato
// resultante é PARCIAL — sem alvos Apple e, como o KGP não gera klib de metadata quando existe um
// único alvo, com o jar de metadata vazio. Publicar isso no Maven Central quebraria todos os
// consumidores iOS. A release oficial sai do Mac.
//
if (!appleTargetsEnabled) {
    tasks.matching { it.name.contains("MavenCentral") }.configureEach {
        doFirst {
            throw GradleException(
                "kmplib: publicação no Maven Central exige host macOS (alvos iOS). " +
                    "Este host não é macOS — use ./gradlew publishToMavenLocal para desenvolvimento."
            )
        }
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

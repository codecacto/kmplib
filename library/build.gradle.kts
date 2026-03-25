import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "br.com.codecacto"
version = "2.0.0"

compose.resources {
    publicResClass = true
    packageOfResClass = "br.com.codecacto.kmplib.generated.resources"
    generateResClass = always
}

android {
    namespace = "br.com.codecacto.kmplib"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

kotlin {
    androidTarget()

    jvmToolchain(17)

    val xcframeworkPath = rootProject.file(".build/artifacts/swift-package-manager-google-mobile-ads/GoogleMobileAds/GoogleMobileAds.xcframework")
    val iosDeviceFrameworkPath = xcframeworkPath.resolve("ios-arm64/GoogleMobileAds.framework")
    val iosSimulatorFrameworkPath = xcframeworkPath.resolve("ios-arm64_x86_64-simulator/GoogleMobileAds.framework")

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "KmpLib"
            isStatic = true
            linkerOpts("-weak_framework", "GoogleMobileAds")
        }

        val frameworkPath = when (iosTarget.name) {
            "iosArm64" -> iosDeviceFrameworkPath
            else -> iosSimulatorFrameworkPath
        }

        iosTarget.compilations["main"].cinterops {
            create("GoogleMobileAds") {
                defFile(project.file("src/nativeInterop/cinterop/GoogleMobileAds.def"))
                compilerOpts("-F${frameworkPath.parentFile.absolutePath}")
            }
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

            // Lifecycle ViewModel
            implementation(libs.androidx.lifecycle.viewmodel)

            // Firebase GitLive
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.firebase.storage)
            implementation(libs.firebase.config)

            // RevenueCat
            implementation(libs.purchases.kmp.core)
            implementation(libs.purchases.kmp.result)
            implementation(libs.purchases.kmp.datetime)

            // Compose (for VisualTransformation and UI Components)
            implementation(libs.compose.ui)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.components.resources)
            @Suppress("DEPRECATION")
            implementation(compose.materialIconsExtended)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        androidMain.dependencies {
            implementation(libs.kotlinx.coroutines.android)

            // Firebase Android (required by GitLive) - exposed as api() for consumer projects
            api(libs.firebase.auth.android)
            api(libs.firebase.firestore.android)
            api(libs.firebase.storage.android)
            api(libs.firebase.common.android)
            api(libs.firebase.crashlytics.android)
            api(libs.firebase.analytics.android)
            api(libs.firebase.config.android)

            // Firebase Crashlytics GitLive (KMP)
            implementation(libs.firebase.crashlytics)

            // AdMob
            implementation(libs.play.services.ads)

            // AndroidX Biometric
            implementation(libs.androidx.biometric)

            // Credentials (for Google Sign-In)
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services)
            implementation(libs.google.googleid)
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

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
version = "1.1.0"

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

        iosTarget.compilations["main"].cinterops {
            create("GoogleMobileAds") {
                defFile(project.file("src/nativeInterop/cinterop/GoogleMobileAds.def"))
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

            // Firebase Android (required by GitLive)
            implementation(libs.firebase.auth.android)
            implementation(libs.firebase.firestore.android)
            implementation(libs.firebase.storage.android)
            implementation(libs.firebase.common.android)

            // AdMob
            implementation(libs.play.services.ads)

            // Firebase Remote Config Android
            implementation(libs.firebase.config.android)

            // AndroidX Biometric
            implementation(libs.androidx.biometric)
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

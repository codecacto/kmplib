import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.konan.target.HostManager

/**
 * Convention plugin de módulo de DOMÍNIO PURO da kmplib que também compila para watchOS e jvm.
 *
 * Mesma base do `kmplib.module` (Android + alvos Apple + publicação Maven), com dois acréscimos
 * restritos a quem realmente precisa:
 * - `jvm()`, para o backend Ktor validar a MESMA regra que o app;
 * - os três alvos watchOS (`watchosArm64`, `watchosDeviceArm64`, `watchosSimulatorArm64`), sob a
 *   MESMA trava de host dos alvos Apple (`HostManager.hostIsMac` / `-Pkmplib.forceAppleTargets=true`),
 *   porque o watchOS também exige Xcode para o link final.
 *
 * Só módulo de domínio sem Compose, Koin, Ktor ou persistência usa este plugin: o `compose-runtime`
 * não existe para `watchosDeviceArm64`, e nenhum módulo com `kmplib.module.compose` pode declarar
 * esses alvos. Ver `kmplib-catalog` → `references/workout.md` para o primeiro consumidor.
 */
plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.library")
    id("com.vanniktech.maven.publish")
}

// Em script pré-compilado não existe o acessor `libs` — o catálogo se lê pelo tipo gerado.
val libs = the<LibrariesForLibs>()

group = "br.com.codecacto"
version = providers.gradleProperty("kmplib.version").get()

/**
 * Mesma trava de `kmplib.module`: alvos Apple (e aqui, watchOS) só em macOS, para não publicar
 * módulo Gradle com variantes `available-at` apontando para artefato que nunca sai do Linux.
 */
val appleTargetsEnabled: Boolean =
    HostManager.hostIsMac || providers.gradleProperty("kmplib.forceAppleTargets").orNull == "true"

android {
    namespace = "br.com.codecacto." + project.name.replace('-', '.')
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
    jvm()
    jvmToolchain(17)

    if (appleTargetsEnabled) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
        watchosArm64()
        watchosDeviceArm64()
        watchosSimulatorArm64()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Maven Central só sai de um host macOS: sem os alvos Apple/watchOS o artefato é parcial, e
// publicá-lo quebraria todo consumidor de relógio. `publishToMavenLocal` no Linux continua legítimo.
if (!appleTargetsEnabled) {
    tasks.matching { it.name.contains("MavenCentral") }.configureEach {
        doFirst {
            throw GradleException(
                "kmplib: publicação no Maven Central exige host macOS (alvos iOS/watchOS). " +
                    "Este host não é macOS — use ./gradlew publishToMavenLocal para desenvolvimento."
            )
        }
    }
}

mavenPublishing {
    publishToMavenCentral()
    if (project.hasProperty("signing.keyId")) {
        signAllPublications()
    }
    coordinates(group.toString(), project.name, version.toString())

    pom {
        name = project.name
        description = "CodeCacto KMP — módulo ${project.name}"
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

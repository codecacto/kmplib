import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.konan.target.HostManager

/**
 * Convention plugin de TODO módulo publicável da kmplib.
 *
 * Carrega de uma vez o que era copiado no `build.gradle.kts` do monólito: alvos (Android + os três
 * Apple), toolchain, namespace do Android derivado do nome do módulo, guarda de host e a publicação
 * Maven. Um módulo novo passa a ser `plugins { id("kmplib.module") }` mais as suas dependências.
 *
 * Compose e serialização NÃO entram aqui: módulo de dado puro (`brdata`, `validation`) não deve
 * arrastar o compilador do Compose. Quem desenha tela aplica também `kmplib.module.compose`.
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
 * Alvos Apple só existem em macOS: Kotlin/Native precisa do Xcode. Declará-los no Linux gravaria
 * no módulo Gradle publicado variantes `available-at` apontando para artefatos que nunca serão
 * publicados — módulo incoerente, que quebra o fallback `mavenLocal` de qualquer clone.
 * Forçar a tentativa (diagnóstico): `-Pkmplib.forceAppleTargets=true`.
 */
val appleTargetsEnabled: Boolean =
    HostManager.hostIsMac || providers.gradleProperty("kmplib.forceAppleTargets").orNull == "true"

android {
    // `:kmplib-auth` -> `br.com.codecacto.kmplib.auth`. Namespace por módulo é exigência do AGP
    // (dois módulos com o mesmo namespace colidem no R e no manifesto).
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
    jvmToolchain(17)

    if (appleTargetsEnabled) {
        iosX64()
        iosArm64()
        iosSimulatorArm64()
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Maven Central só sai de um host macOS: sem os alvos Apple o artefato é parcial, e publicá-lo
// quebraria todo consumidor iOS. `publishToMavenLocal` no Linux continua legítimo (dev local).
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

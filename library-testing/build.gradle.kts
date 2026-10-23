import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "br.com.codecacto"
// Mesma versão do conjunto (gradle.properties), como os outros 21 módulos. Enquanto este número
// era escrito à mão aqui, ele ficou parado em 2.111.0 enquanto a lib chegava à 2.16x — e o
// catálogo de um app que aponta `kmplib-testing` para a versão da lib não resolvia nada.
version = providers.gradleProperty("kmplib.version").get()

// =============================================================================
// br.com.codecacto:kmplib-testing — artefato SÓ DE TESTE
// =============================================================================
//
// Por que um artefato separado, e não uma API nova na kmplib de produção:
//
// O que este módulo oferece é o poder de **trocar a implementação que decide se alguém é
// assinante**. Se isso morasse na `kmplib` (publicada em todo app), seria um caminho para injetar
// um repositório que responde "é premium" para todo mundo, alcançável em build de release por
// qualquer código do app — ou por uma dependência dele. Gancho de teste dentro do caminho do
// dinheiro.
//
// Como artefato separado, ele entra apenas em `androidInstrumentedTestImplementation` /
// `commonTestImplementation` e **não existe no APK/AAB de release** — o que é verificável com um
// grep no bundle (ver o CHANGELOG desta versão).
//
// O gancho alcança a loja pela costura marcada do `:kmplib-monetization`
// (`@KmpLibStoreDoubleApi`, opt-in nível ERROR) — ver o bloco "Sem friend modules", abaixo.
//

// Mesma guarda de host da `:kmplib` — Kotlin/Native não compila iOS fora de macOS. Em Linux o
// módulo sai com commonMain + Android, coerente e publicável; num Mac sai completo.
val appleTargetsEnabled: Boolean =
    HostManager.hostIsMac || providers.gradleProperty("kmplib.forceAppleTargets").orNull == "true"

android {
    namespace = "br.com.codecacto.kmplib.testing"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    // Mesma opção do convention plugin (`kmplib.module`): o `AppLogger` da lib chama
    // `android.util.Log`, que no teste de JVM só existe como stub.
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
    } else {
        logger.lifecycle(
            "[kmplib-testing] Host não-macOS: alvos iOS não declarados (publish sai com commonMain + Android)."
        )
    }

    sourceSets {
        commonMain.dependencies {
            // `api`, e não `implementation`: TODO tipo desta lib aparece na API pública daqui
            // (`PurchaseRepository`, `PurchaseResult`, `PurchasePackage`…). Com `implementation`, o
            // consumidor não conseguiria nem nomear o que este módulo devolve.
            // `api`, e o módulo é o `:kmplib-monetization` — não mais o umbrella. Este artefato
            // dubla a LOJA, e é o `PurchaseManager` dele que precisa ser amigo (abaixo). Depender
            // do umbrella traria a lib inteira para um artefato que só existe para builds de QA.
            api(project(":kmplib-monetization"))
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// =============================================================================
// Sem friend modules desde a 2.325.0
// =============================================================================
//
// Até a 2.324.0 o `PurchaseTestHooks` alcançava o `internal fun initializeWith` do
// `:kmplib-monetization` por `-Xfriend-paths` (JVM) e `-friend-modules` (Kotlin/Native) — flags NÃO
// estáveis do compilador, passadas à mão por tarefa, com o caminho do KLIB derivado do nome da
// tarefa. A compilação de metadata do `iosMain` (`compileIosMainKotlinMetadata`) nunca recebeu a
// amizade, e o `publishToMavenLocal -Pkmplib.forceAppleTargets=true` quebrava com "it is internal
// in PurchaseManager".
//
// Hoje o dono expõe a costura explicitamente: `PurchaseManager.installStoreDouble`/
// `clearStoreDouble`, marcadas com `@KmpLibStoreDoubleApi` (`@RequiresOptIn`, nível ERROR). É a
// forma recomendada pelo Kotlin para API pública que não é contrato de app; nenhuma configuração
// de compilador é necessária aqui.

// A release oficial sai de um Mac (mesma regra da :kmplib): sem os alvos Apple, o módulo publicado
// seria parcial e quebraria os consumidores iOS.
if (!appleTargetsEnabled) {
    tasks.matching { it.name.contains("MavenCentral") }.configureEach {
        doFirst {
            throw GradleException(
                "kmplib-testing: publicação no Maven Central exige host macOS (alvos iOS). " +
                    "Use ./gradlew publishToMavenLocal para desenvolvimento."
            )
        }
    }
}

mavenPublishing {
    publishToMavenCentral()

    if (project.hasProperty("signing.keyId")) {
        signAllPublications()
    }

    coordinates(group.toString(), "kmplib-testing", version.toString())

    pom {
        name = "CodeCacto KMP Library — Testing"
        description = "Dublês e ganchos de teste da kmplib (nunca entra em build de produção)"
        inceptionYear = "2026"
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

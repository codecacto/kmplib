import org.jetbrains.kotlin.konan.target.HostManager

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.vanniktech.mavenPublish)
}

group = "br.com.codecacto"
version = "2.111.0"

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
// O gancho alcança o `internal fun initializeWith` da `:kmplib` por **friend modules**, o mecanismo
// oficial do compilador Kotlin para dar acesso a `internal` sem torná-lo público (é o mesmo que o
// Gradle usa para o source set de teste de um módulo ver o `main` dele). Ver o bloco de
// `-Xfriend-paths`, abaixo.
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
// Friend modules — acesso ao `internal` da :kmplib
// =============================================================================
//
// `PurchaseTestHooks` (androidMain) chama `PurchaseManager.initializeWith`, que é `internal` na
// `:kmplib`. `internal` em Kotlin é visível dentro do MÓDULO DE COMPILAÇÃO, então ele serve aos
// testes da própria kmplib e é invisível para outro módulo Gradle — foi exatamente por isso que o
// `FakePurchaseRepository` do Super 8 nunca pôde ser ligado a nada.
//
// `-Xfriend-paths` é a resposta oficial do compilador: declara que as classes da `:kmplib-monetization` são de um
// "módulo amigo", cujo `internal` este módulo pode ver. Nada é tornado público — a API publicada da
// `:kmplib` fica idêntica.
//
// Aplicado SÓ às compilações Kotlin/JVM-Android (`compileDebugKotlinAndroid`,
// `compileReleaseKotlinAndroid`), de propósito:
//  · é onde o `androidMain` (o único código daqui que usa `internal`) é compilado;
//  · a compilação de metadata e as de iOS compilam apenas `commonMain`, que só implementa a
//    interface PÚBLICA `PurchaseRepository` e não precisa de amizade nenhuma;
//  · assim a release oficial (que sai do Mac, com os alvos Apple) não depende deste ajuste —
//    o que evita que um detalhe de build de teste possa travar a publicação da lib.
//
// O caminho amigo tem de ser **exatamente a entrada de classpath** de onde o compilador carrega as
// classes da `:kmplib` — quem monta essa entrada é o AGP (`bundleLibCompileToJar<Variante>` →
// `intermediates/compile_library_classes_jar/…/classes.jar`), e escrever esse caminho à mão seria
// depender de um detalhe interno do plugin, que muda de versão para versão.
//
// Então não se escreve: filtra-se. "Amigo é toda entrada do MEU PRÓPRIO classpath que vem da pasta
// de build da `:kmplib`" — verdadeiro em qualquer versão do AGP, e imune a mudança de layout.
// Avaliado só na execução da tarefa (`providers.provider`), quando o classpath já está resolvido.
//
// A dependência de tarefa vem de graça pelo `api(project(":kmplib-monetization"))`: as classes já precisam
// existir para compilar contra elas.
//
val kmplibBuildDir: String =
    project(":kmplib-monetization").layout.buildDirectory.get().asFile.absolutePath

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    val classpath = libraries
    compilerOptions.freeCompilerArgs.add(
        providers.provider {
            val amigos = classpath.files
                .filter { it.absolutePath.startsWith(kmplibBuildDir) }
                .map { it.absolutePath }
            // Sem amigo encontrado, NÃO passa a flag vazia: `-Xfriend-paths=` sem valor é erro de
            // argumento no compilador, e o build morreria dizendo algo sobre sintaxe de flag em vez
            // de "não achei a :kmplib-monetization no classpath".
            if (amigos.isEmpty()) "" else "-Xfriend-paths=${amigos.joinToString(",")}"
        }.filter { it.isNotEmpty() }
    )
}

// A MESMA amizade para as compilações Kotlin/Native (iOS), desde 14/08/2026.
//
// Passou a ser necessária quando o `PurchaseTestHooks` ganhou um par em `iosMain`: a captura do print
// de review compila o app com o dublê ligado, e sem o gancho no alvo Apple o app não resolvia o
// símbolo. O que muda é só o nome da flag — no Kotlin/Native a amizade é entre KLIBs
// (`-Xfriend-modules`), não entre diretórios de classes.
//
// Mesmo princípio de antes: nada de caminho escrito à mão. Filtra-se o próprio classpath da tarefa
// atrás do que veio da pasta de build da `:kmplib-monetization`.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeCompile>().configureEach {
    // Aqui NÃO dá para filtrar o classpath como na versão JVM: tocar em `libraries` numa tarefa
    // nativa força a avaliação da distribuição do Kotlin/Native antes do download dela terminar, e o
    // build morre com "Querying the mapped value of task 'downloadKotlinNativeDistribution' ... is
    // not supported" — erro que não tem nada a ver com amizade de módulo e manda procurar longe.
    //
    // Então o caminho do KLIB é derivado do NOME DA TAREFA (`compileKotlinIosSimulatorArm64` →
    // `iosSimulatorArm64`), que é o mesmo nome que o Gradle usa na pasta de saída. É menos elegante
    // que filtrar, e é o que funciona sem acordar a distribuição.
    val alvo = name.removePrefix("compileKotlin").replaceFirstChar { it.lowercase() }
    val saidaDoAlvo = File("$kmplibBuildDir/classes/kotlin/$alvo/main")
    val nomeDaTarefa = name

    // `addAll` com LISTA, e não `add` + `filter`: um provider filtrado que fica "sem valor" faz o
    // Gradle abortar com "Assign a value to 'compilerOptions.freeCompilerArgs'". Lista vazia é o
    // jeito de dizer "nada a acrescentar".
    compilerOptions.freeCompilerArgs.addAll(
        providers.provider {
            // Duas coisas aqui foram aferidas no `konanc -help` da máquina que compila (14/08/2026),
            // depois de três rodadas perdidas adivinhando:
            //
            // 1. **A flag do Kotlin/Native é `-friend-modules <path>`** — sem o `-X` da forma JVM, e
            //    com o caminho como argumento SEPARADO. `-Xfriend-modules=` é aceito calado e não
            //    faz nada, e o erro que sobra é "it is internal in PurchaseManager", que manda
            //    investigar visibilidade quando o problema é a flag.
            // 2. **O KLIB é um DIRETÓRIO**: `…/main/klib/<nome-do-módulo>`. Procurar por arquivo
            //    `.klib` não acha nada.
            val pastaKlib = File(saidaDoAlvo, "klib")
            val amigo = pastaKlib.listFiles()?.firstOrNull()?.absolutePath
                ?: saidaDoAlvo.listFiles()?.firstOrNull { it.extension == "klib" }?.absolutePath
            if (amigo == null) {
                logger.lifecycle(
                    "[kmplib-testing] $nomeDaTarefa: não achei o KLIB da :kmplib-monetization em " +
                        "${pastaKlib.absolutePath} — sem amizade, o `internal` da lib fica invisível.",
                )
                emptyList()
            } else {
                listOf("-friend-modules", amigo)
            }
        }
    )
}

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

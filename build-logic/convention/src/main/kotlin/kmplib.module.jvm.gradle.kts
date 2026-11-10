import org.gradle.accessors.dm.LibrariesForLibs
import org.jetbrains.kotlin.konan.target.HostManager

/**
 * Alvo **`jvm` (desktop)** OPT-IN, por módulo (2.353.0).
 *
 * Aplique JUNTO de `kmplib.module` ou `kmplib.module.compose`:
 * ```kotlin
 * plugins {
 *     id("kmplib.module.compose")
 *     id("kmplib.module.jvm")
 * }
 * ```
 *
 * Por que opt-in, e não na convenção base: um alvo novo exige um `actual` para cada `expect` do
 * módulo E que toda dependência de `commonMain` publique variante JVM. Ligar em todos os módulos de
 * uma vez obrigaria câmera, mapa, vídeo e saúde (sem equivalente oficial no desktop) a fingirem
 * suporte. Cada módulo entra quando tiver actuals honestos — o que a plataforma não faz devolve
 * "indisponível", nunca finge.
 *
 * Alvos Android e iOS ficam exatamente como estão: este plugin só ACRESCENTA `jvm()`. O nome do alvo
 * é `jvm` (o `kmplib.module.pure` usa o mesmo nome), então o consumidor Compose Desktop declara a
 * dependência normal e o Gradle escolhe a variante `-jvm` sozinho.
 *
 * Toolchain 17 (a mesma dos outros alvos): o app desktop roda em JRE ≥ 17.
 *
 * ### Quando o alvo `jvm` é DECLARADO (a mesma lógica da trava dos alvos Apple)
 * O conjunto de alvos da lib tem de COBRIR o de todo consumidor. Um app Android+iOS compilando no
 * Linux/Windows (sem os alvos Apple na lib) com a lib em Android+JVM ganha um `commonMain` da lib
 * (android, jvm) que não cobre o iOS dele: "KMP Dependencies Resolution Failure". Por isso o `jvm()`
 * só entra quando:
 * - os alvos Apple também entram (`HostManager.hostIsMac` ou `-Pkmplib.forceAppleTargets=true`) — a
 *   lib fica em Android+iOS+JVM, superconjunto de qualquer app; é assim que a release do Mac publica
 *   a variante JVM; **ou**
 * - quem compila o DESKTOP pede: `-Pkmplib.jvmTarget=true` na linha de comando, ou
 *   `systemProp.kmplib.jvmTarget=true` no `gradle.properties` do app desktop (propriedade de sistema
 *   chega ao `includeBuild`; propriedade de projeto do consumidor não chega).
 * Fora disso a lib fica exatamente como antes (só Android no Linux/Windows).
 *
 * Os módulos declaram dependências do `jvmMain` com `sourceSets.matching { it.name == "jvmMain" }`,
 * para o build sem o alvo não quebrar.
 */
plugins {
    id("kmplib.module")
}

val libs = the<LibrariesForLibs>()

private fun flag(name: String): Boolean =
    providers.gradleProperty(name).orNull == "true" || providers.systemProperty(name).orNull == "true"

val kmplibJvmTargetEnabled: Boolean =
    HostManager.hostIsMac || flag("kmplib.forceAppleTargets") || flag("kmplib.jvmTarget")

if (kmplibJvmTargetEnabled) {
    kotlin {
        jvm()

        sourceSets {
            jvmTest.dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}

// Módulo com Compose: o teste JVM roda o Compose de verdade (os textos da lib saem do
// `compose-resources`, que no desktop carrega o Skiko). O runtime nativo do Skiko do SO do host é
// dependência SÓ de teste — o app desktop já o traz pelo `compose.desktop.currentOs` dele.
pluginManager.withPlugin("org.jetbrains.compose") {
    kotlin.sourceSets.matching { it.name == "jvmTest" }.configureEach {
        dependencies {
            implementation(org.jetbrains.compose.ComposePlugin.DesktopDependencies.currentOs)
        }
    }
}

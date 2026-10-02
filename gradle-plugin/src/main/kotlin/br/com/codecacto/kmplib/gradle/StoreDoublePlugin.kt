package br.com.codecacto.kmplib.gradle

import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

/**
 * `kmplibStoreDouble { }` — o que o app lê do plugin.
 *
 * @property enabled o build pediu o dublê (`-Pqa.paywallDemo=true` / `QA_PAYWALL_DEMO=1`). O app usa
 *   para trocar o diretório (`*QaDemo` × `*QaNoop`) e declarar a `kmplib-testing` — sem a flag, ela nem
 *   é dependência do binário publicável. Pode ser sobrescrito (`enabled.set(...)`), e a trava segue o
 *   valor final.
 */
abstract class StoreDoubleExtension {
    abstract val enabled: Property<Boolean>
}

/**
 * Falha a tarefa quando o dublê está ligado e o alvo desta âncora é publicável. Sem saída: roda sempre
 * (é barata) e nunca vem do cache.
 */
@DisableCachingByDefault(because = "verificação sem saída")
abstract class StoreDoubleReleaseGuard : DefaultTask() {
    @get:Input
    abstract val doubleEnabled: Property<Boolean>

    @get:Input
    abstract val publishable: Property<Boolean>

    @get:Input
    abstract val target: Property<String>

    @TaskAction
    fun verify() {
        if (doubleEnabled.get() && publishable.get()) {
            throw GradleException(StoreDoubleRules.failureMessage(target.get()))
        }
    }
}

/**
 * **`br.com.codecacto.kmplib.store-double`** (2.233.0) — o dublê da loja (`kmplib-testing`) só em build
 * de QA, com a trava que o impede de chegar a um binário publicável. Uma linha no app:
 *
 * ```kotlin
 * plugins { alias(libs.plugins.kmplib.storeDouble) }
 * val demoDePaywall: Boolean = kmplibStoreDouble.enabled.get()
 * ```
 *
 * A trava é pela VARIANTE, não pelo nome digitado:
 * - **Android** — toda variante cujo build type não é debuggable ganha a guarda antes do
 *   `pre<Variante>Build`, a âncora de que dependem `assemble`, `bundle`, `publish`, `build`,
 *   abreviações (`aR`) e flavors;
 * - **Kotlin/Native** — todo link de binário `RELEASE` (o framework do Archive, XCFramework);
 * - **Xcode** — `embedAndSign…AppleFrameworkForXcode` com `CONFIGURATION` `Release*`,
 *   `KOTLIN_FRAMEWORK_BUILD_TYPE=release` ou `ACTION=install` (archive).
 *
 * A guarda falha na EXECUÇÃO, não na configuração: o build de QA (debug) configura a variante release
 * junto, e reprovar na configuração derrubaria o próprio build de QA.
 */
class StoreDoublePlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create("kmplibStoreDouble", StoreDoubleExtension::class.java)
        val providers = project.providers
        extension.enabled.convention(
            providers.gradleProperty(StoreDoubleRules.PROPERTY).orElse("")
                .zip(providers.environmentVariable(StoreDoubleRules.ENV).orElse("")) { property, env ->
                    StoreDoubleRules.isRequested(property, env)
                },
        )

        if (extension.enabled.get()) {
            project.logger.lifecycle(
                "⚠️  kmplib: build de QA com o DUBLÊ da loja (${StoreDoubleRules.PROPERTY}). " +
                    "Serve para ver o paywall no emulador/simulador — NUNCA para publicar.",
            )
        }

        fun guard(name: String, target: String, publishable: Boolean) =
            project.tasks.register(name, StoreDoubleReleaseGuard::class.java) { task ->
                task.group = "verification"
                task.description = "Reprova o dublê da loja num build publicável ($target)."
                task.doubleEnabled.set(extension.enabled)
                task.publishable.set(publishable)
                task.target.set(target)
            }

        // ── Android: pela variante ──────────────────────────────────────────────────────────────
        project.pluginManager.withPlugin("com.android.application") {
            val dsl = project.extensions.getByType(ApplicationExtension::class.java)
            val components = project.extensions.getByType(ApplicationAndroidComponentsExtension::class.java)
            components.onVariants(components.selector().all()) { variant ->
                val buildType = variant.buildType
                val debuggable = buildType?.let { dsl.buildTypes.findByName(it)?.isDebuggable }
                if (!StoreDoubleRules.isPublishableAndroidBuildType(debuggable)) return@onVariants
                val capitalized = variant.name.replaceFirstChar { it.uppercaseChar() }
                val guardTask = guard(
                    name = "verifyStoreDoubleNotIn$capitalized",
                    target = "variante Android ${variant.name}",
                    publishable = true,
                )
                val anchor = StoreDoubleRules.preBuildTaskName(variant.name)
                project.tasks.named { it == anchor }.configureEach { task -> task.dependsOn(guardTask) }
            }
        }

        // ── Kotlin/Native: todo link RELEASE ────────────────────────────────────────────────────
        project.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
            val nativeGuard = guard(
                name = "verifyStoreDoubleNotInNativeRelease",
                target = "framework Kotlin/Native RELEASE",
                publishable = true,
            )
            val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlin.targets.withType(KotlinNativeTarget::class.java).configureEach { target ->
                target.binaries.configureEach { binary ->
                    if (binary.buildType == NativeBuildType.RELEASE) {
                        binary.linkTaskProvider.configure { link -> link.dependsOn(nativeGuard) }
                    }
                }
            }

            // ── Xcode: a configuração que o script de build exporta ─────────────────────────────
            val xcodeGuard = project.tasks.register("verifyStoreDoubleNotInXcodeRelease", StoreDoubleReleaseGuard::class.java) { task ->
                task.group = "verification"
                task.description = "Reprova o dublê da loja num Release/Archive do Xcode."
                task.doubleEnabled.set(extension.enabled)
                task.publishable.set(
                    providers.environmentVariable("CONFIGURATION").orElse("")
                        .zip(providers.environmentVariable("KOTLIN_FRAMEWORK_BUILD_TYPE").orElse("")) { c, k -> c to k }
                        .zip(providers.environmentVariable("ACTION").orElse("")) { (c, k), a ->
                            StoreDoubleRules.isXcodeRelease(c, k, a)
                        },
                )
                task.target.set(
                    providers.environmentVariable("CONFIGURATION").orElse("?")
                        .map { configuration -> "Xcode CONFIGURATION=$configuration" },
                )
            }
            project.tasks.named { StoreDoubleRules.isXcodeEmbedTask(it) }.configureEach { task -> task.dependsOn(xcodeGuard) }
        }
    }
}

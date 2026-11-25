package br.com.codecacto.kmplib.gradle

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Sync

/**
 * `kmplibTestLevel { }` — o que o app pode ajustar no plugin de nível de teste. Os defaults são a régua da
 * fábrica: mexer neles tira o projeto da comparação com os outros, então só com motivo.
 *
 * @property variant a variante Android medida (`debug`). Vazio = o total do Kover (projeto sem Android).
 * @property testTask a tarefa de teste de unidade cujos XML do JUnit vão para a medição. Default:
 *   `test<Variante>UnitTest` (`testDebugUnitTest`); sem variante, `test`.
 * @property excludedClasses classes fora da conta de linhas (curinga do Kover). Default: [TestLevelRules.EXCLUDED_CLASSES].
 * @property excludedAnnotations anotações cujas classes/funções saem da conta. Default: `@Composable` (a tela —
 *   provada pela célula ponta a ponta, não por linha; decisão P1 do fundador, 25/nov/2026).
 */
abstract class TestLevelExtension {
    abstract val variant: Property<String>
    abstract val testTask: Property<String>
    abstract val excludedClasses: SetProperty<String>
    abstract val excludedAnnotations: SetProperty<String>
}

/**
 * **`br.com.codecacto.kmplib.test-level`** (2.381.0) — o nível mínimo de testes no app, numa linha:
 *
 * ```kotlin
 * plugins { alias(libs.plugins.kmplib.testLevel) }
 * ```
 *
 * 1. **Aplica o Kover** (a versão da kmplib) com o filtro da fábrica: telas `@Composable`,
 *    `ComposableSingletons`, recursos gerados, DI (`*.di.*`), `*Holder`, `*_Factory` e as classes geradas do
 *    Android (`BuildConfig`, `R`) ficam FORA da conta de linhas. Sem `minBound`: o mínimo é o das linhas NOVAS
 *    (≥ 80%, calculado fora, por diff, em `Ferramentas/nivel-de-teste`), e o total só não pode cair.
 * 2. **Registra `nivelDeTeste`**: roda o teste de unidade e o relatório XML (JaCoCo) do Kover da [variante]
 *    e copia os dois para `build/nivel-de-teste/` (`kover.xml` + `junit/TEST-…xml`) — de onde a medição lê.
 *
 * O Kover não mede Kotlin/Native: o `iosMain` não entra na conta (o `commonMain` sim, pelo alvo Android).
 */
class TestLevelPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(TestLevelRules.EXTENSION, TestLevelExtension::class.java)
        extension.variant.convention(TestLevelRules.DEFAULT_VARIANT)
        extension.testTask.convention(extension.variant.map(TestLevelRules::testTaskFor))
        extension.excludedClasses.convention(TestLevelRules.EXCLUDED_CLASSES)
        extension.excludedAnnotations.convention(TestLevelRules.EXCLUDED_ANNOTATIONS)

        project.pluginManager.apply(TestLevelRules.KOVER_PLUGIN_ID)
        project.extensions.configure(KoverProjectExtension::class.java) { kover ->
            kover.reports { reports ->
                reports.filters { filters ->
                    filters.excludes { excludes ->
                        excludes.classes.addAll(extension.excludedClasses)
                        excludes.annotatedBy.addAll(extension.excludedAnnotations)
                        excludes.androidGeneratedClasses()
                    }
                }
            }
        }

        val layout = project.layout
        project.tasks.register(TestLevelRules.TASK, Sync::class.java) { task ->
            task.group = "verification"
            task.description = "Roda o teste de unidade e o relatório do Kover e junta os dois em build/nivel-de-teste/ " +
                "(nível mínimo de testes da fábrica)."
            task.dependsOn(extension.testTask, extension.variant.map(TestLevelRules::koverXmlTaskFor))
            task.from(extension.variant.flatMap { layout.buildDirectory.file(TestLevelRules.koverXmlPathFor(it)) }) { spec ->
                spec.rename { TestLevelRules.KOVER_XML_NAME }
            }
            task.from(extension.testTask.flatMap { layout.buildDirectory.dir("test-results/$it") }) { spec ->
                spec.include("*.xml")
                spec.into(TestLevelRules.JUNIT_DIR)
            }
            task.into(layout.buildDirectory.dir(TestLevelRules.OUTPUT_DIR))
        }
    }
}

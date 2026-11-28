package br.com.codecacto.kmplib.gradle

import com.mikepenz.aboutlibraries.plugin.AboutLibrariesExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * **`br.com.codecacto.kmplib.licenses`** (2.384.0) — a lista de licenças de código aberto do app, numa linha:
 *
 * ```kotlin
 * plugins { alias(libs.plugins.kmplib.licenses) }
 * ```
 *
 * 1. **Aplica o AboutLibraries** (mikepenz, a solução padrão do Compose Multiplatform) na MESMA versão do
 *    runtime que a `OpenSourceLicensesScreen` (`kmplib-licenses`) lê — plugin e runtime de versões diferentes
 *    gravam/leem formatos diferentes.
 * 2. **Fixa o arquivo** em `src/commonMain/composeResources/files/aboutlibraries.json` (convenção: o app pode
 *    trocar por `aboutLibraries { export { outputFile = … } }`), com o JSON indentado para o diff do commit
 *    mostrar a dependência que entrou ou saiu.
 * 3. **Gera no build**: as tarefas do Compose que leem o `composeResources` do `commonMain` passam a depender
 *    de `exportLibraryDefinitions` — trocou dependência, o JSON se refaz sozinho no próximo build (Android e
 *    iOS). A tarefa só roda de verdade quando o grafo muda (cache do Gradle). Desligar:
 *    `kmplib.licenses.autoExport=false` no `gradle.properties` (aí o app roda `./gradlew :composeApp:exportLibraryDefinitions`
 *    antes de cada release).
 *
 * O arquivo gerado é COMMITADO no repositório do app (é recurso do binário, como uma imagem).
 */
class LicensesPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.apply(LicensesRules.ABOUT_LIBRARIES_PLUGIN_ID)
        val aboutLibraries = project.extensions.getByType(AboutLibrariesExtension::class.java)
        aboutLibraries.export.outputFile.convention(project.layout.projectDirectory.file(LicensesRules.OUTPUT_FILE))
        aboutLibraries.export.prettyPrint.convention(true)

        val auto = project.providers.gradleProperty(LicensesRules.AUTO_EXPORT_PROPERTY).orNull
        if (LicensesRules.autoExportEnabled(auto)) {
            project.tasks.configureEach { task ->
                if (LicensesRules.readsCommonComposeResources(task.name)) task.dependsOn(LicensesRules.EXPORT_TASK)
            }
        }
    }
}

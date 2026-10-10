plugins {
    id("kmplib.module.compose")
    // O questionário (`ui.questionnaire`, 2.265.0) é JSON vindo do servidor — o MESMO contrato do
    // `QuestionnaireRunner` da weblib. O runtime (`kotlinx-serialization-json`) já chega por `core`.
    alias(libs.plugins.kotlinSerialization)
}

// Os recursos compartilhados da lib (as 4 traduções, o logo CodeCacto, os ícones de Google e
// Apple) moram aqui: `Res` é uma classe gerada por MÓDULO, e gerá-la em dois lugares daria duas
// classes de mesmo nome no mesmo pacote. Quem precisa delas (o leitor de código de barras, por
// exemplo) depende de `kmplib-ui`.
compose.resources {
    publicResClass = true
    packageOfResClass = "br.com.codecacto.kmplib.generated.resources"
    generateResClass = always
}

/**
 * As fixtures compartilhadas do FormSchema v1 (`src/commonTest/fixtures/form-schema-v1.fixtures.json`,
 * cópia SEM edição do recurso do `backlib-forms` — a mesma que a weblib copia) viram fonte Kotlin do
 * `commonTest`: o contrato roda no Android (JVM) E no iOS (Native), sem API de arquivo comum. A cópia
 * versionada é o JSON; o `.kt` gerado nunca é editado nem versionado. Mudou expectativa no servidor:
 * troca-se a cópia (e sobe o `fixturesVersion` do teste) — nunca se "conserta" a cópia de um lado só.
 */
abstract class GenerateFormSchemaFixtures : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fixtures: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val json = fixtures.get().asFile.readText(Charsets.UTF_8)
        // Pedaços pequenos: a constante de string da JVM tem teto de 64 KB, e o arquivo passa disso.
        val chunks = ArrayList<String>()
        var start = 0
        while (start < json.length) {
            var end = minOf(start + 4_000, json.length)
            if (end < json.length && json[end].isLowSurrogate()) end--
            chunks += json.substring(start, end)
            start = end
        }
        val body = chunks.joinToString("\n") { chunk ->
            val escaped = buildString {
                chunk.forEach { c ->
                    when {
                        c == '\\' -> append("\\\\")
                        c == '"' -> append("\\\"")
                        c == '$' -> append("\\$")
                        c == '\n' -> append("\\n")
                        c == '\r' -> append("\\r")
                        c == '\t' -> append("\\t")
                        c < ' ' -> append("\\u" + c.code.toString(16).padStart(4, '0'))
                        else -> append(c)
                    }
                }
            }
            "    append(\"$escaped\")"
        }
        val file = outputDir.get().file("br/com/codecacto/kmplib/ui/form/FormSchemaFixturesJson.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "// GERADO por :kmplib-ui:generateFormSchemaFixtures a partir de\n" +
                "// src/commonTest/fixtures/form-schema-v1.fixtures.json — não editar.\n" +
                "package br.com.codecacto.kmplib.ui.form\n\n" +
                "internal val FORM_SCHEMA_FIXTURES_JSON: String = buildString {\n$body\n}\n",
            Charsets.UTF_8,
        )
    }
}

val generateFormSchemaFixtures = tasks.register<GenerateFormSchemaFixtures>("generateFormSchemaFixtures") {
    fixtures.set(layout.projectDirectory.file("src/commonTest/fixtures/form-schema-v1.fixtures.json"))
    outputDir.set(layout.buildDirectory.dir("generated/formSchemaFixtures/commonTest/kotlin"))
}

kotlin {
    sourceSets {
        // `redactMediaUrl`/`redactMediaUrlsIn` (kmplib-core): URL de mídia sem assinatura no log.
        all { languageSettings.optIn("br.com.codecacto.kmplib.core.util.KmpLibCoreInternalApi") }
        commonTest {
            kotlin.srcDir(generateFormSchemaFixtures.flatMap { it.outputDir })
        }

        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-mask"))
            api(project(":kmplib-platform"))

            api(libs.compose.components.resources)

            // ViewModel é SUPERTIPO público do BaseViewModel, a classe-base de todo ViewModel de
            // todo app do ecossistema.
            api(libs.androidx.lifecycle.viewmodel)
            // LifecycleEventEffect/LocalLifecycleOwner do AppLockGate (ui/security).
            implementation(libs.androidx.lifecycle.runtime.compose)
            // BackHandler multiplataforma (a tela cheia de "sem internet" segura o voltar do
            // sistema). Artefato SEPARADO: o `ui` o traz em runtime mas não o expõe como `api`.
            // ⚠️ Não trocar pelo `navigationevent-compose`: ele é Android-only e derruba o iOS.
            implementation(libs.compose.ui.backhandler)
            api(libs.kotlinx.datetime)
            // Carrossel e imagem remota dos componentes.
            api(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            // ReorderableList (2.221.0): arrasto com autoscroll e animação dos vizinhos. Nenhum tipo
            // dele aparece na API pública (o escopo do item é nosso), por isso implementation.
            implementation(libs.reorderable)
        }

        commonTest.dependencies {
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            // PhotoSource.authenticated contra um DomainApiClient de mentira.
            implementation(libs.ktor.client.mock)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core)
            // Correção de orientação no seletor de imagem.
            implementation(libs.androidx.exifinterface)
        }
    }
}

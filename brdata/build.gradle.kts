plugins {
    id("kmplib.module.compose")
    alias(libs.plugins.kotlinSerialization)
}

/**
 * A régua COMUM do BR Code Pix (`src/commonTest/fixtures/pix-brcode.fixtures.json`) — cópia SEM edição
 * do arquivo da weblib (`src/pix/__fixtures__/`), o mesmo que a backlib copia — vira fonte Kotlin do
 * `commonTest`: o teste roda no Android (JVM) E no iOS (Native), sem API de arquivo comum. Mesmo
 * modelo das fixtures do FormSchema no `kmplib-ui`. A cópia versionada é o JSON; o `.kt` gerado nunca
 * é editado nem versionado. Caso que diverge: corrige-se a IMPLEMENTAÇÃO errada, nunca a cópia.
 */
abstract class GeneratePixBrCodeFixtures : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val fixtures: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val json = fixtures.get().asFile.readText(Charsets.UTF_8)
        // Pedaços pequenos: a constante de string da JVM tem teto de 64 KB.
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
        val file = outputDir.get().file("br/com/codecacto/kmplib/pix/PixBrCodeFixturesJson.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "// GERADO por :kmplib-brdata:generatePixBrCodeFixtures a partir de\n" +
                "// src/commonTest/fixtures/pix-brcode.fixtures.json — não editar.\n" +
                "package br.com.codecacto.kmplib.pix\n\n" +
                "internal val PIX_BRCODE_FIXTURES_JSON: String = buildString {\n$body\n}\n",
            Charsets.UTF_8,
        )
    }
}

val generatePixBrCodeFixtures = tasks.register<GeneratePixBrCodeFixtures>("generatePixBrCodeFixtures") {
    fixtures.set(layout.projectDirectory.file("src/commonTest/fixtures/pix-brcode.fixtures.json"))
    outputDir.set(layout.buildDirectory.dir("generated/pixBrCodeFixtures/commonTest/kotlin"))
}

kotlin {
    sourceSets {
        commonTest {
            kotlin.srcDir(generatePixBrCodeFixtures.flatMap { it.outputDir })
        }
        commonMain.dependencies {
            api(project(":kmplib-core"))
            // `AddressFields` — o formulário de endereço que preenche sozinho a partir do CEP.
            // É o único Composable do módulo, e é por causa dele que o Compose entra aqui.
            api(project(":kmplib-ui"))
            api(project(":kmplib-mask"))
            // Os ~100 mil CEPs e o parser de BrCode do Pix são modelos @Serializable públicos.
            api(libs.kotlinx.serialization.json)
        }
    }
}

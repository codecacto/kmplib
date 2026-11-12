plugins {
    id("kmplib.module.compose")
}

// `kmplib-familia` (2.354.0, OPT-IN) — o KIT da família de 12 apps cristãos: as 12 paletas (11 tokens ×
// claro/escuro) com a ponte para o AppTheme, Fraunces + Atkinson Hyperlegible embarcadas, a escala
// nomeada do protótipo que segue o A− A+, os primitivos nas medidas do `ds.css`, a casca de navegação
// (topo com selo Premium, menu, banner, abas) e as telas comuns desenhadas como no protótipo.
// Especificação: docs/estudos/igrejas-e-perfis-instagram-2026-11-11/familia-12-apps/01 e 02.
//
// NÃO está no umbrella: só os 12 apps declaram (precedente: kmplib-inspection, o "Laudo Kit").
compose.resources {
    publicResClass = false
    packageOfResClass = "br.com.codecacto.kmplib.familia.generated.resources"
    generateResClass = always
}

/**
 * A base pt-BR dos textos do kit (`values/strings.xml`) vira tabela Kotlin — o mesmo mecanismo do
 * `kmplib-ui` (GenerateKmpLibBaseStrings): app BR (`KmpLibLocales.configure(listOf(PT_BR))`) em aparelho
 * em inglês mostra o kit em pt-BR, como o resto do app, sem a API interna de locale do Compose.
 */
abstract class GenerateFamiliaBaseStrings : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val strings: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(strings.get().asFile)
        val nodes = doc.getElementsByTagName("string")
        val entries = LinkedHashMap<String, String>()
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as org.w3c.dom.Element
            require(entries.put(el.getAttribute("name"), el.textContent.replace("\\'", "'")) == null) { "string repetida" }
        }
        val dir = outputDir.get().asFile
        dir.deleteRecursively()
        val file = File(dir, "br/com/codecacto/kmplib/familia/FamiliaBaseStrings.kt")
        file.parentFile.mkdirs()
        fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$").replace("\n", "\\n")
        val branches = entries.entries.joinToString("\n") { (k, v) -> "    \"$k\" -> \"${esc(v)}\"" }
        file.writeText(
            "// GERADO por :kmplib-familia:generateFamiliaBaseStrings a partir de values/strings.xml — não editar.\n" +
                "package br.com.codecacto.kmplib.familia\n\n" +
                "internal const val FAMILIA_BASE_STRING_COUNT: Int = ${entries.size}\n\n" +
                "internal fun familiaBaseString(key: String): String? = when (key) {\n$branches\n    else -> null\n}\n",
            Charsets.UTF_8,
        )
    }
}

val generateFamiliaBaseStrings = tasks.register<GenerateFamiliaBaseStrings>("generateFamiliaBaseStrings") {
    strings.set(layout.projectDirectory.file("src/commonMain/composeResources/values/strings.xml"))
    outputDir.set(layout.buildDirectory.dir("generated/familiaBaseStrings/commonMain/kotlin"))
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateFamiliaBaseStrings.flatMap { it.outputDir })
        }
        commonMain.dependencies {
            api(project(":kmplib-core"))
            api(project(":kmplib-ui"))
            api(project(":kmplib-platform"))
            // Premium (PaywallViewModel/MonetizationManager), banner e intersticial, feedback/Desenvolvido por.
            api(project(":kmplib-monetization"))
            api(project(":kmplib-ads"))
            api(project(":kmplib-central"))
            api(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
    }
}

package br.com.codecacto.kmplib.gradle

/** As regras do `test-level`, puras (testadas sem Gradle). */
object TestLevelRules {
    const val EXTENSION: String = "kmplibTestLevel"
    const val TASK: String = "nivelDeTeste"
    const val KOVER_PLUGIN_ID: String = "org.jetbrains.kotlinx.kover"
    const val DEFAULT_VARIANT: String = "debug"
    const val OUTPUT_DIR: String = "nivel-de-teste"
    const val KOVER_XML_NAME: String = "kover.xml"
    const val JUNIT_DIR: String = "junit"

    /** O filtro da fábrica (decisão P1, 25/nov/2026): o que não é lógica testável por unidade. */
    val EXCLUDED_CLASSES: Set<String> = setOf(
        "*ComposableSingletons*",
        "*generated.resources*",
        "*.di.*",
        "*Holder",
        "*_Factory",
    )

    /** Tela fica fora da conta de linhas; entra pela célula ponta a ponta (Maestro). */
    val EXCLUDED_ANNOTATIONS: Set<String> = setOf("androidx.compose.runtime.Composable")

    /** `debug` → `testDebugUnitTest`; vazio (sem Android) → `test`. */
    fun testTaskFor(variant: String): String =
        if (variant.isBlank()) "test" else "test${capitalized(variant)}UnitTest"

    /** `debug` → `koverXmlReportDebug`; vazio → `koverXmlReport` (o total). */
    fun koverXmlTaskFor(variant: String): String =
        if (variant.isBlank()) "koverXmlReport" else "koverXmlReport${capitalized(variant)}"

    /** Onde o Kover grava o XML, relativo ao `build/`. */
    fun koverXmlPathFor(variant: String): String =
        if (variant.isBlank()) "reports/kover/report.xml" else "reports/kover/report${capitalized(variant)}.xml"

    private fun capitalized(s: String): String = s.trim().replaceFirstChar { it.uppercaseChar() }
}

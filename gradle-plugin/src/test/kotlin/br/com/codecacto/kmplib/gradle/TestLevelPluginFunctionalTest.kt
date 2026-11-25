package br.com.codecacto.kmplib.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O `test-level` num app KMP + AGP de verdade (projeto temporário, configuration cache ligado): o `nivelDeTeste`
 * roda o teste, gera o XML do Kover e junta os dois em `build/nivel-de-teste/`, e a função `@Composable` fica fora
 * da conta de linhas (a anotação é declarada no próprio projeto com o nome do Compose — o Kover filtra pelo nome).
 */
class TestLevelPluginFunctionalTest {

    private lateinit var dir: File
    private val sdk: String? = System.getProperty("kmplib.androidSdk")

    @BeforeTest
    fun setUp() {
        assumeTrue("Android SDK ausente — teste funcional pulado", sdk != null && File(sdk).isDirectory)
        dir = Files.createTempDirectory("test-level").toFile()
        dir.resolve("local.properties").writeText("sdk.dir=$sdk\n")
        dir.resolve("gradle.properties").writeText(
            "android.useAndroidX=true\norg.gradle.configuration-cache=true\n",
        )
        dir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement { repositories { google(); mavenCentral() } }
            rootProject.name = "app"
            """.trimIndent(),
        )
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform")
                id("com.android.application")
                id("br.com.codecacto.kmplib.test-level")
            }
            kotlin {
                androidTarget()
                sourceSets {
                    commonTest.dependencies { implementation(kotlin("test")) }
                }
            }
            android {
                namespace = "teste.app"
                compileSdk = 36
                defaultConfig { minSdk = 24 }
            }
            """.trimIndent(),
        )
        dir.resolve("src/androidMain").apply { mkdirs() }.resolve("AndroidManifest.xml")
            .writeText("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" />\n")
        val main = dir.resolve("src/commonMain/kotlin/teste").apply { mkdirs() }
        main.resolve("Calculo.kt").writeText(
            """
            package teste
            fun soma(a: Int, b: Int): Int = a + b
            fun naoTestada(x: Int): Int {
                val y = x * 2
                return y + 1
            }
            """.trimIndent(),
        )
        dir.resolve("src/commonMain/kotlin/androidx/compose/runtime").apply { mkdirs() }
            .resolve("Composable.kt").writeText(
                "package androidx.compose.runtime\n@Retention(AnnotationRetention.BINARY)\nannotation class Composable\n",
            )
        main.resolve("Tela.kt").writeText(
            """
            package teste
            import androidx.compose.runtime.Composable
            @Composable
            fun TelaDeTeste(n: Int): Int {
                val dobro = n * 2
                return dobro + soma(n, 1)
            }
            """.trimIndent(),
        )
        dir.resolve("src/commonTest/kotlin/teste").apply { mkdirs() }.resolve("CalculoTest.kt").writeText(
            """
            package teste
            import kotlin.test.Test
            import kotlin.test.assertEquals
            class CalculoTest {
                @Test fun somaDoisNumeros() { assertEquals(3, soma(1, 2)) }
            }
            """.trimIndent(),
        )
    }

    @Test
    fun `nivelDeTeste junta o XML do Kover e o do JUnit, com a tela fora da conta`() {
        val r = GradleRunner.create()
            .withProjectDir(dir)
            .withPluginClasspath()
            .withArguments("nivelDeTeste", "--stacktrace")
            .build()

        assertEquals(TaskOutcome.SUCCESS, r.task(":nivelDeTeste")?.outcome, r.output)
        val saida = dir.resolve("build/nivel-de-teste")
        val kover = saida.resolve("kover.xml").readText()
        assertTrue("CalculoKt" in kover, kover)
        assertTrue("naoTestada" in kover, "o código não testado CONTA (aparece descoberto)")
        assertFalse("TelaDeTeste" in kover, "a função @Composable fica fora da conta de linhas")
        val junit = saida.resolve("junit").listFiles().orEmpty().map { it.name }
        assertTrue(junit.any { it.startsWith("TEST-") && it.endsWith(".xml") }, junit.toString())
        assertTrue("somaDoisNumeros" in saida.resolve("junit").listFiles()!!.first { it.name.startsWith("TEST-") }.readText())
    }

    @Test
    fun `variante e tarefa de teste sao ajustaveis pela extensao`() {
        dir.resolve("build.gradle.kts").appendText(
            """

            kmplibTestLevel { variant.set("release") }
            tasks.register("mostrarNivel") {
                val t = kmplibTestLevel.testTask
                doLast { println("tarefa=" + t.get()) }
            }
            """.trimIndent(),
        )
        val r = GradleRunner.create().withProjectDir(dir).withPluginClasspath()
            .withArguments("mostrarNivel", "nivelDeTeste", "--dry-run").build()
        assertTrue("tarefa=testReleaseUnitTest" in r.output || ":testReleaseUnitTest SKIPPED" in r.output, r.output)
        assertTrue(":koverXmlReportRelease SKIPPED" in r.output, r.output)
    }
}

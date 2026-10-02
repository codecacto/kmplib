package br.com.codecacto.kmplib.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * O plugin aplicado num app KMP + AGP de verdade (projeto temporário), com configuration cache — o
 * mesmo arranjo da `casca-mobile`. Prova a trava pela VARIANTE: release e flavor-release reprovam;
 * debug passa; framework release reprova; Xcode `Release-<flavor>` reprova.
 */
class StoreDoublePluginFunctionalTest {

    private lateinit var dir: File
    private val sdk: String? = System.getProperty("kmplib.androidSdk")

    @BeforeTest
    fun setUp() {
        assumeTrue("Android SDK ausente — teste funcional pulado", sdk != null && File(sdk).isDirectory)
        dir = Files.createTempDirectory("store-double").toFile()
        dir.resolve("local.properties").writeText("sdk.dir=$sdk\n")
        dir.resolve("gradle.properties").writeText(
            "android.useAndroidX=true\norg.gradle.configuration-cache=true\nkotlin.native.ignoreDisabledTargets=true\n",
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
                id("br.com.codecacto.kmplib.store-double")
            }
            kotlin {
                androidTarget()
                linuxX64 { binaries.executable() }
            }
            android {
                namespace = "teste.app"
                compileSdk = 36
                defaultConfig { minSdk = 24 }
                flavorDimensions += "marca"
                productFlavors { create("azul") { dimension = "marca" } }
            }
            tasks.register("printDouble") {
                val on = kmplibStoreDouble.enabled
                doLast { println("dublê=" + on.get()) }
            }
            """.trimIndent(),
        )
    }

    private fun run(vararg args: String, env: Map<String, String> = emptyMap(), fail: Boolean = false): BuildResult {
        val runner = GradleRunner.create()
            .withProjectDir(dir)
            .withPluginClasspath()
            .withArguments(*args, "--stacktrace")
            .withEnvironment(System.getenv() + env)
        return if (fail) runner.buildAndFail() else runner.build()
    }

    @Test
    fun `sem a flag nada trava e o enabled e falso`() {
        val r = run("printDouble", "preAzulReleaseBuild")
        assertTrue("dublê=false" in r.output)
    }

    @Test
    fun `flag liga o enabled pela propriedade e pela variavel`() {
        assertTrue("dublê=true" in run("printDouble", "-Pqa.paywallDemo=true").output)
        assertTrue("dublê=true" in run("printDouble", env = mapOf("QA_PAYWALL_DEMO" to "1")).output)
    }

    @Test
    fun `dublê no debug passa — e o build de QA`() {
        run("preAzulDebugBuild", "-Pqa.paywallDemo=true")
    }

    @Test
    fun `dublê no release com flavor reprova, inclusive por abreviacao`() {
        val r = run("preAzRB", "-Pqa.paywallDemo=true", fail = true)
        assertTrue("DUBLÊ DA LOJA" in r.output, r.output)
        assertTrue("azulRelease" in r.output)
    }

    @Test
    fun `dublê no framework release reprova, no debug nao`() {
        val r = run("linkReleaseExecutableLinuxX64", "-Pqa.paywallDemo=true", "--dry-run")
        assertTrue(":verifyStoreDoubleNotInNativeRelease" in r.output, r.output)
        val d = run("linkDebugExecutableLinuxX64", "-Pqa.paywallDemo=true", "--dry-run")
        assertFalse(":verifyStoreDoubleNotInNativeRelease" in d.output, d.output)
    }

    @Test
    fun `Xcode Release com flavor reprova e Debug passa`() {
        val release = run(
            "verifyStoreDoubleNotInXcodeRelease",
            env = mapOf("QA_PAYWALL_DEMO" to "1", "CONFIGURATION" to "Release-azul"),
            fail = true,
        )
        assertTrue("Release-azul" in release.output, release.output)
        run(
            "verifyStoreDoubleNotInXcodeRelease",
            env = mapOf("QA_PAYWALL_DEMO" to "1", "CONFIGURATION" to "Debug"),
        )
    }
}

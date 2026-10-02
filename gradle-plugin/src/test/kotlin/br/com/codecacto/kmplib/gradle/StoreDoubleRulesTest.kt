package br.com.codecacto.kmplib.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** As regras do dublê: o que liga e o que é publicável. */
class StoreDoubleRulesTest {

    @Test
    fun `liga pela propriedade ou pela variavel do Xcode`() {
        assertTrue(StoreDoubleRules.isRequested("true", null))
        assertTrue(StoreDoubleRules.isRequested(null, "1"))
        assertTrue(StoreDoubleRules.isRequested("TRUE", ""))
        assertFalse(StoreDoubleRules.isRequested(null, null))
        assertFalse(StoreDoubleRules.isRequested("false", "0"))
        assertFalse(StoreDoubleRules.isRequested("", ""))
    }

    @Test
    fun `variante Android publicavel e a de build type nao debuggable — na duvida, fecha`() {
        assertFalse(StoreDoubleRules.isPublishableAndroidBuildType(true))
        assertTrue(StoreDoubleRules.isPublishableAndroidBuildType(false))
        assertTrue(StoreDoubleRules.isPublishableAndroidBuildType(null))
    }

    @Test
    fun `Xcode Release com e sem flavor, KOTLIN_FRAMEWORK_BUILD_TYPE e Archive sao publicaveis`() {
        assertTrue(StoreDoubleRules.isXcodeRelease("Release", null, null))
        assertTrue(StoreDoubleRules.isXcodeRelease("Release-diariaCerta", null, null))
        assertTrue(StoreDoubleRules.isXcodeRelease("release", null, null))
        assertTrue(StoreDoubleRules.isXcodeRelease("Debug", "release", null))
        assertTrue(StoreDoubleRules.isXcodeRelease("Debug", null, "install"))
    }

    @Test
    fun `Xcode Debug (com flavor) nao e publicavel`() {
        assertFalse(StoreDoubleRules.isXcodeRelease("Debug", null, "build"))
        assertFalse(StoreDoubleRules.isXcodeRelease("Debug-diariaCerta", "debug", "build"))
        assertFalse(StoreDoubleRules.isXcodeRelease(null, null, null))
    }

    @Test
    fun `ancora da variante Android`() {
        assertEquals("preReleaseBuild", StoreDoubleRules.preBuildTaskName("release"))
        assertEquals("preDiariaCertaReleaseBuild", StoreDoubleRules.preBuildTaskName("diariaCertaRelease"))
    }

    @Test
    fun `tarefas do script do Xcode`() {
        assertTrue(StoreDoubleRules.isXcodeEmbedTask("embedAndSignAppleFrameworkForXcode"))
        assertTrue(StoreDoubleRules.isXcodeEmbedTask("embedAndSignMainAppleFrameworkForXcode"))
        assertFalse(StoreDoubleRules.isXcodeEmbedTask("linkReleaseFrameworkIosArm64"))
    }
}

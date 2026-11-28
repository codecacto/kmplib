package br.com.codecacto.kmplib.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LicensesRulesTest {

    @Test
    fun `arquivo no composeResources do commonMain, lido como files aboutlibraries json`() {
        assertEquals("src/commonMain/composeResources/files/aboutlibraries.json", LicensesRules.OUTPUT_FILE)
        assertEquals("com.mikepenz.aboutlibraries.plugin", LicensesRules.ABOUT_LIBRARIES_PLUGIN_ID)
    }

    @Test
    fun `so as tarefas de recurso do commonMain esperam a exportacao`() {
        assertTrue(LicensesRules.readsCommonComposeResources("prepareComposeResourcesTaskForCommonMain"))
        assertTrue(LicensesRules.readsCommonComposeResources("generateResourceAccessorsForCommonMain"))
        assertTrue(LicensesRules.readsCommonComposeResources("copyNonXmlValueResourcesForCommonMain"))
        assertFalse(LicensesRules.readsCommonComposeResources("prepareComposeResourcesTaskForIosMain"))
        assertFalse(LicensesRules.readsCommonComposeResources("compileKotlinIosArm64"))
        assertFalse(LicensesRules.readsCommonComposeResources("exportLibraryDefinitions"))
    }

    @Test
    fun `geracao automatica ligada por padrao e so false desliga`() {
        assertTrue(LicensesRules.autoExportEnabled(null))
        assertTrue(LicensesRules.autoExportEnabled("true"))
        assertTrue(LicensesRules.autoExportEnabled(""))
        assertFalse(LicensesRules.autoExportEnabled(" FALSE "))
    }
}

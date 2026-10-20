package br.com.codecacto.kmplib.platform.battery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 2.308.0 — fabricante, regra do onboarding e textos nos 4 idiomas. */
class BatteryOnboardingTest {

    @Test
    fun fabricantePeloManufacturerOuPelaMarca() {
        assertEquals(BatteryVendor.XIAOMI, batteryVendorOf("Xiaomi", "Redmi"))
        assertEquals(BatteryVendor.XIAOMI, batteryVendorOf("unknown", "Redmi"))
        assertEquals(BatteryVendor.XIAOMI, batteryVendorOf("Xiaomi", "POCO"))
        assertEquals(BatteryVendor.SAMSUNG, batteryVendorOf("samsung", "samsung"))
        assertEquals(BatteryVendor.MOTOROLA, batteryVendorOf("motorola", "motorola"))
        assertEquals(BatteryVendor.HUAWEI, batteryVendorOf("HUAWEI", "HUAWEI"))
        assertEquals(BatteryVendor.HUAWEI, batteryVendorOf("HONOR", "HONOR"))
        assertEquals(BatteryVendor.OTHER, batteryVendorOf("Google", "google"))
        assertEquals(BatteryVendor.OTHER, batteryVendorOf(null, null))
        assertEquals(BatteryVendor.XIAOMI, batteryVendorOf("  XIAOMI ", null))
    }

    @Test
    fun soFabricanteAgressivoSemIsencaoPrecisa() {
        assertTrue(batteryOnboardingNeeded(BatteryVendor.XIAOMI, isIgnoringOptimizations = false))
        assertFalse(batteryOnboardingNeeded(BatteryVendor.XIAOMI, isIgnoringOptimizations = true))
        assertFalse(batteryOnboardingNeeded(BatteryVendor.OTHER, isIgnoringOptimizations = false))
        // iOS: sem fabricante e sem o conceito de isenção.
        assertFalse(batteryOnboardingNeeded(null, null))
        // Android sem contexto (init esquecido): não incomoda o usuário com um aviso que não sabe avaliar.
        assertFalse(batteryOnboardingNeeded(BatteryVendor.SAMSUNG, null))
    }

    @Test
    fun textosNosQuatroIdiomasComONomeDoApp() {
        val br = BatteryOnboardingTexts.forLanguage("pt-BR", "RondAki")
        val pt = BatteryOnboardingTexts.forLanguage("pt-PT", "RondAki")
        val en = BatteryOnboardingTexts.forLanguage("en-US", "RondAki")
        val es = BatteryOnboardingTexts.forLanguage("es-419", "RondAki")
        assertEquals("Permitir em segundo plano", br.requestExemption)
        assertTrue(pt.openAppSettings.contains("definições"))
        assertEquals("Allow in background", en.requestExemption)
        assertEquals("Permitir en segundo plano", es.requestExemption)
        for (t in listOf(br, pt, en, es)) {
            for (v in BatteryVendor.entries) {
                val passos = t.steps(v)
                assertTrue(passos.size >= 2, "passos de $v")
                assertTrue(passos.any { "RondAki" in it }, "nome do app nos passos de $v")
                assertTrue(t.vendorHeading(v).isNotBlank())
            }
        }
    }

    @Test
    fun idiomaNaoTraduzidoCaiNoPtBrESemNomeUsaGenerico() {
        val fr = BatteryOnboardingTexts.forLanguage("fr-FR", null)
        assertEquals("Permitir em segundo plano", fr.requestExemption)
        assertTrue(fr.steps(BatteryVendor.SAMSUNG).any { "este app" in it })
    }

    @Test
    fun idsComPrefixoUnico() {
        val ids = listOf(
            BatteryOnboardingTestTags.CONTEUDO, BatteryOnboardingTestTags.TITULO,
            BatteryOnboardingTestTags.BTN_PERMITIR, BatteryOnboardingTestTags.PERMITIDO,
            BatteryOnboardingTestTags.BTN_CONFIGURACOES, BatteryOnboardingTestTags.BTN_CONCLUIR,
        )
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.all { it.startsWith("bateria-onboarding") })
    }
}

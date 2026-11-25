package br.com.codecacto.kmplib.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TestLevelRulesTest {

    @Test
    fun `nomes de tarefa e caminho do relatorio pela variante`() {
        assertEquals("testDebugUnitTest", TestLevelRules.testTaskFor("debug"))
        assertEquals("koverXmlReportDebug", TestLevelRules.koverXmlTaskFor("debug"))
        assertEquals("reports/kover/reportDebug.xml", TestLevelRules.koverXmlPathFor("debug"))
        assertEquals("testAzulDebugUnitTest", TestLevelRules.testTaskFor("azulDebug"))
    }

    @Test
    fun `sem variante e o total do Kover e a tarefa test`() {
        assertEquals("test", TestLevelRules.testTaskFor(""))
        assertEquals("koverXmlReport", TestLevelRules.koverXmlTaskFor(" "))
        assertEquals("reports/kover/report.xml", TestLevelRules.koverXmlPathFor(""))
    }

    @Test
    fun `o filtro da fabrica tira tela, DI e gerados`() {
        assertTrue("androidx.compose.runtime.Composable" in TestLevelRules.EXCLUDED_ANNOTATIONS)
        assertEquals(
            setOf("*ComposableSingletons*", "*generated.resources*", "*.di.*", "*Holder", "*_Factory"),
            TestLevelRules.EXCLUDED_CLASSES,
        )
    }
}

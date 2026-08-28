package br.com.codecacto.kmplib.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Testes da regra pura de responsividade (GAP-SAL-07).
 *
 * `ProvideIsCompact`/`LocalIsCompact` dependem de `BoxWithConstraints` (host Compose), que não roda
 * no `testDebugUnitTest` deste ambiente — mesma limitação dos demais testes de UI da lib. Aqui
 * cobrimos a parte testável em JVM puro: o limiar [CompactWidthThreshold] e a regra [gridColumns].
 */
class ResponsiveTest {

    @Test
    fun threshold_eh_600dp() {
        assertEquals(600.dp, CompactWidthThreshold)
    }

    @Test
    fun gridColumns_compacto_usa_uma_coluna_por_padrao() {
        assertEquals(1, gridColumns(isCompact = true))
    }

    @Test
    fun gridColumns_expandido_usa_duas_colunas_por_padrao() {
        assertEquals(2, gridColumns(isCompact = false))
    }

    @Test
    fun gridColumns_respeita_overrides() {
        assertEquals(2, gridColumns(isCompact = true, compact = 2, expanded = 4))
        assertEquals(4, gridColumns(isCompact = false, compact = 2, expanded = 4))
    }

    @Test
    fun gridColumns_distingue_breakpoints() {
        assertFalse(gridColumns(isCompact = true) == gridColumns(isCompact = false))
    }
}

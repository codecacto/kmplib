package br.com.codecacto.kmplib.ui

import br.com.codecacto.kmplib.ui.components.OptionTestTags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Id de automação por opção de `SegmentedControl`/`ChoiceChipGroup`/`FilterChipRow` (2.247.0).
 * O flow do Maestro toca em `<grupo>-<chave>`; o contrato do nome é o que este teste trava.
 */
class OptionTestTagsTest {

    @Test
    fun chaveInformadaViraOSufixo() {
        assertEquals(
            listOf("calculadora-unidade-arroba", "calculadora-unidade-kg"),
            OptionTestTags.options("calculadora-unidade", 2, listOf("arroba", "kg")),
        )
        assertEquals("calculadora-unidade-kg", OptionTestTags.option("calculadora-unidade", "kg"))
    }

    @Test
    fun semChaveOIndiceEhOSufixo() {
        // O índice não muda com o idioma do aparelho — o rótulo traduzido mudaria.
        assertEquals(
            listOf("historico-periodo-0", "historico-periodo-1", "historico-periodo-2"),
            OptionTestTags.options("historico-periodo", 3),
        )
    }

    @Test
    fun chaveFaltandoOuEmBrancoCaiNoIndice() {
        assertEquals(
            listOf("g-pix", "g-1", "g-2"),
            OptionTestTags.options("g", 3, listOf("pix", "  ")),
        )
        assertEquals(listOf("g-0"), OptionTestTags.options("g", 1, listOf("!!!")))
    }

    @Test
    fun chaveEhNormalizadaParaMinusculoComHifen() {
        assertEquals("kg", OptionTestTags.normalizeKey("Kg"))
        assertEquals("a-vencer", OptionTestTags.normalizeKey("A Vencer"))
        assertEquals("ultimos-30-dias", OptionTestTags.normalizeKey("  Ultimos 30  dias! "))
        assertEquals("cart-o", OptionTestTags.normalizeKey("Cartão"))
    }

    @Test
    fun chaveRepetidaNaoGeraIdDuplicado() {
        val ids = OptionTestTags.options("g", 4, listOf("kg", "kg", "Kg", "x"))
        assertEquals(listOf("g-kg", "g-kg-1", "g-kg-2", "g-x"), ids)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun chaveIgualAoIndiceDeOutraOpcaoNaoColide() {
        val ids = OptionTestTags.options("g", 3, listOf("1", "", "x"))
        assertEquals(ids.size, ids.toSet().size)
        // O desempate "<chave>-<índice>" pode coincidir com uma chave já usada.
        val desempate = OptionTestTags.options("g", 3, listOf("k-2", "k", "k"))
        assertEquals(listOf("g-k-2", "g-k", "g-k-2-1"), desempate)
        assertTrue(ids.all { it.startsWith("g-") })
    }

    @Test
    fun contagemZeroOuNegativaNaoGeraNada() {
        assertEquals(emptyList(), OptionTestTags.options("g", 0))
        assertEquals(emptyList(), OptionTestTags.options("g", -1))
    }

    @Test
    fun prefixosPadraoSaoDistintosEntreComponentes() {
        val prefixos = setOf(
            OptionTestTags.SEGMENTED_GROUP,
            OptionTestTags.CHOICE_CHIP_GROUP,
            OptionTestTags.FILTER_CHIP_GROUP,
        )
        assertEquals(3, prefixos.size)
        assertEquals("segmento-0", OptionTestTags.options(OptionTestTags.SEGMENTED_GROUP, 1).single())
    }
}

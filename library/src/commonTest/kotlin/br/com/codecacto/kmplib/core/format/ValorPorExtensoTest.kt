package br.com.codecacto.kmplib.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Teste parametrizado do contrato de paridade `valorPorExtenso`.
 *
 * Cobre TODOS os casos da tabela §2 e §3 de
 * `ReciboFacil/docs/design/valor-por-extenso-casos.md`. Esta tabela é a fonte de
 * verdade — qualquer divergência aqui é bug de paridade com a weblib e bloqueia a
 * Onda 2 do ReciboFacil.
 */
class ValorPorExtensoTest {

    /** (centavos -> saída esperada) — réplica exata das tabelas do contrato. */
    private val casos: List<Pair<Long, String>> = listOf(
        // 2.1 Zero e centavos puros
        0L to "zero reais",
        1L to "um centavo",
        2L to "dois centavos",
        5L to "cinco centavos",
        10L to "dez centavos",
        15L to "quinze centavos",
        19L to "dezenove centavos",
        20L to "vinte centavos",
        23L to "vinte e três centavos",
        50L to "cinquenta centavos",
        99L to "noventa e nove centavos",

        // 2.2 Reais inteiros (sem centavos)
        100L to "um real",
        200L to "dois reais",
        300L to "três reais",
        1000L to "dez reais",
        1100L to "onze reais",
        1400L to "quatorze reais",
        1500L to "quinze reais",
        1600L to "dezesseis reais",
        2000L to "vinte reais",
        2100L to "vinte e um reais",
        5000L to "cinquenta reais",
        9900L to "noventa e nove reais",

        // 2.3 Reais + centavos
        101L to "um real e um centavo",
        150L to "um real e cinquenta centavos",
        199L to "um real e noventa e nove centavos",
        250L to "dois reais e cinquenta centavos",
        1099L to "dez reais e noventa e nove centavos",
        12000L to "cento e vinte reais",
        12050L to "cento e vinte reais e cinquenta centavos",
        123456L to "mil duzentos e trinta e quatro reais e cinquenta e seis centavos",

        // 2.4 Centenas — "cem" vs "cento" e conjunção "e"
        10000L to "cem reais",
        10001L to "cem reais e um centavo",
        10100L to "cento e um real",
        11000L to "cento e dez reais",
        11500L to "cento e quinze reais",
        19900L to "cento e noventa e nove reais",
        20000L to "duzentos reais",
        20500L to "duzentos e cinco reais",
        21700L to "duzentos e dezessete reais",
        30000L to "trezentos reais",
        50000L to "quinhentos reais",
        99900L to "novecentos e noventa e nove reais",
        99999L to "novecentos e noventa e nove reais e noventa e nove centavos",

        // 2.5 Milhares — conjunção "e" entre grupos (R8)
        100000L to "mil reais",
        100100L to "mil e um reais",
        110000L to "mil e cem reais",
        110500L to "mil cento e cinco reais",
        120000L to "mil e duzentos reais",
        123400L to "mil duzentos e trinta e quatro reais",
        150000L to "mil e quinhentos reais",
        200000L to "dois mil reais",
        234500L to "dois mil trezentos e quarenta e cinco reais",
        1000000L to "dez mil reais",
        1050000L to "dez mil e quinhentos reais",
        1234500L to "doze mil trezentos e quarenta e cinco reais",
        10000000L to "cem mil reais",
        10000100L to "cem mil e um reais",
        15050000L to "cento e cinquenta mil e quinhentos reais",
        99999900L to "novecentos e noventa e nove mil novecentos e noventa e nove reais",

        // 2.6 Milhões, bilhões — escalas + "de reais" (R9, R10)
        100000000L to "um milhão de reais",
        100000050L to "um milhão de reais e cinquenta centavos",
        100000100L to "um milhão e um reais",
        150000000L to "um milhão e quinhentos mil reais",
        100100000L to "um milhão e mil reais",
        123456700L to "um milhão duzentos e trinta e quatro mil quinhentos e sessenta e sete reais",
        200000000L to "dois milhões de reais",
        250000000L to "dois milhões e quinhentos mil reais",
        1000000000L to "dez milhões de reais",
        100000000000L to "um bilhão de reais",
        250000000000L to "dois bilhões e quinhentos milhões de reais",
        100000000000000L to "um trilhão de reais",

        // 2.7 Casos de borda de gênero / plural / conjunção
        200001L to "dois mil reais e um centavo",
        100000001L to "um milhão de reais e um centavo",
        110000000L to "um milhão e cem mil reais",

        // Regressão paridade: "real" singular SÓ em 1 e 101; demais terminações em "um" → plural.
        20100L to "duzentos e um reais",
        30100L to "trezentos e um reais",
        90100L to "novecentos e um reais",
        110100L to "mil cento e um reais",
    )

    @Test
    fun valorPorExtenso_cobreTodosOsCasosDoContrato() {
        val falhas = mutableListOf<String>()
        for ((centavos, esperado) in casos) {
            val obtido = valorPorExtenso(centavos)
            if (obtido != esperado) {
                falhas += "centavos=$centavos: esperado=\"$esperado\" obtido=\"$obtido\""
            }
        }
        assertEquals(
            emptyList<String>(),
            falhas,
            "Casos divergentes do contrato de valor por extenso:\n${falhas.joinToString("\n")}",
        )
    }

    @Test
    fun valorPorExtenso_rejeitaNegativo() {
        assertFailsWith<IllegalArgumentException> { valorPorExtenso(-1L) }
        assertFailsWith<IllegalArgumentException> { valorPorExtenso(-100L) }
    }

    @Test
    fun valorPorExtenso_zero_eh_zero_reais() {
        assertEquals("zero reais", valorPorExtenso(0L))
    }
}

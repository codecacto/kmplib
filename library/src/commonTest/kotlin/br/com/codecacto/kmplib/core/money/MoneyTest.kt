package br.com.codecacto.kmplib.core.money

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cobre os invariantes de dinheiro em centavos: string decimal ponta a ponta (sem Double),
 * arredondamento/truncamento, piso 0, múltiplos itens, qtd × preço, soma e saldo.
 */
class MoneyTest {

    // ---- toCents / fromCents (ida e volta canônica) ----

    @Test
    fun toCents_decimalComPonto() {
        assertEquals(12345L, Money.toCents("123.45"))
    }

    @Test
    fun toCents_aceitaVirgula() {
        assertEquals(1050L, Money.toCents("10,50"))
    }

    @Test
    fun toCents_nuloOuVazio_ehZero() {
        assertEquals(0L, Money.toCents(null))
        assertEquals(0L, Money.toCents(""))
        assertEquals(0L, Money.toCents("   "))
    }

    @Test
    fun toCents_umaCasaDecimal_completaComZero() {
        // "0.1" -> 10 centavos (= R$ 0,10), não 1 centavo.
        assertEquals(10L, Money.toCents("0.1"))
    }

    @Test
    fun toCents_truncaTerceiraCasa_semArredondar() {
        assertEquals(100L, Money.toCents("1.005"))
        assertEquals(199L, Money.toCents("1.999"))
    }

    @Test
    fun toCents_negativo() {
        assertEquals(-500L, Money.toCents("-5.00"))
    }

    @Test
    fun toCents_decimalCanonicoSemMilhar() {
        // Entrada canônica (sem separador de milhar) — shape persistido / vindo de máscara.
        assertEquals(123456L, Money.toCents("1234.56"))
    }

    @Test
    fun fromCents_formataDuasCasas() {
        assertEquals("123.45", Money.fromCents(12345L))
        assertEquals("0.05", Money.fromCents(5L))
        assertEquals("0.00", Money.fromCents(0L))
        assertEquals("-5.00", Money.fromCents(-500L))
    }

    @Test
    fun normalize_formaCanonica() {
        assertEquals("123.45", Money.normalize("123,45"))
        assertEquals("0.00", Money.normalize(null))
    }

    @Test
    fun zero_constante() {
        assertEquals("0.00", Money.ZERO)
    }

    // ---- subtotal = unitPrice × quantity ----

    @Test
    fun subtotal_quantidadeMultiplica() {
        assertEquals("360.00", Money.subtotal("120.00", 3))
    }

    @Test
    fun subtotal_quantidadeZero_ehZero() {
        assertEquals("0.00", Money.subtotal("120.00", 0))
    }

    @Test
    fun subtotal_quantidadeNegativa_pisoEmZero() {
        assertEquals("0.00", Money.subtotal("120.00", -2))
    }

    // ---- sum ----

    @Test
    fun sum_somaSubtotais() {
        assertEquals("325.50", Money.sum(listOf("120.00", "200.00", "5.50")))
    }

    @Test
    fun sum_listaVazia_ehZero() {
        assertEquals("0.00", Money.sum(emptyList()))
    }

    // ---- total = max(0, Σ subtotais − desconto) ----

    @Test
    fun total_semDesconto() {
        assertEquals("320.00", Money.total(listOf("120.00", "200.00"), "0.00"))
    }

    @Test
    fun total_comDesconto() {
        assertEquals("300.00", Money.total(listOf("120.00", "200.00"), "20.00"))
    }

    @Test
    fun total_descontoMaiorQueSoma_pisoEmZero() {
        assertEquals("0.00", Money.total(listOf("100.00"), "150.00"))
    }

    @Test
    fun total_descontoIgualSoma_ehZero() {
        assertEquals("0.00", Money.total(listOf("100.00"), "100.00"))
    }

    @Test
    fun total_listaVazia() {
        assertEquals("0.00", Money.total(emptyList(), "0.00"))
        assertEquals("0.00", Money.total(emptyList(), "50.00"))
    }

    @Test
    fun total_multiplosItensComCentavos() {
        // 19.99 + 0.01 + 5.50 = 25.50 ; - 0.50 = 25.00
        assertEquals("25.00", Money.total(listOf("19.99", "0.01", "5.50"), "0.50"))
    }

    // ---- balance = max(0, base − deduction) ----

    @Test
    fun balance_descontaAbatimento() {
        assertEquals("70.00", Money.balance("100.00", "30.00"))
    }

    @Test
    fun balance_abatimentoMaior_pisoEmZero() {
        assertEquals("0.00", Money.balance("100.00", "150.00"))
    }

    // ---- máscara (dígitos) ----

    @Test
    fun fromDigits_centavos() {
        assertEquals("123.45", Money.fromDigits("12345"))
        assertEquals("0.00", Money.fromDigits(""))
    }

    @Test
    fun toDigits_inverso() {
        assertEquals("12345", Money.toDigits("123.45"))
        assertEquals("0", Money.toDigits(null))
    }

    // ---- apresentação BRL ----

    @Test
    fun formatBRL_milhar() {
        assertEquals("R$ 1.234,56", Money.formatBRL("1234.56"))
        assertEquals("R$ 0,00", Money.formatBRL("0.00"))
        assertEquals("R$ 1.000.000,00", Money.formatBRL("1000000.00"))
    }

    @Test
    fun formatBRL_negativo() {
        assertEquals("-R$ 5,00", Money.formatBRL("-5.00"))
    }
}

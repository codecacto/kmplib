package br.com.codecacto.kmplib.qr

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * As **4 regras de penalidade** isoladas, com matrizes construídas à mão para exercitar cada uma.
 *
 * Aqui é onde encoders caseiros erram sem perceber: as regras se somam, e um erro numa delas só
 * aparece como "máscara escolhida diferente" — nunca como exceção. Testar cada regra sozinha, com uma
 * matriz cujo valor esperado se calcula no papel, é a única forma de saber qual está errada.
 */
class QrPenaltyTest {

    // -------------------------------------------------------------------------------------------
    // Regra 1 — faixas de 5+ da mesma cor
    // -------------------------------------------------------------------------------------------

    @Test
    fun `regra 1 pontua 3 na faixa de cinco e mais um por modulo extra`() {
        // Linha única de 5 escuros num tabuleiro 5x5 alternado nas outras linhas.
        val size = 5

        // Toda a matriz clara: cada uma das 5 linhas e 5 colunas é uma faixa clara de 5 ⇒ 10 × 3.
        val allLight = BooleanArray(size * size)
        assertEquals(30, QrMask.penaltyRule1(allLight, size))

        // Toda escura: mesma conta (a regra não distingue cor).
        val allDark = BooleanArray(size * size) { true }
        assertEquals(30, QrMask.penaltyRule1(allDark, size))

        // 6x6 com a PRIMEIRA linha escura e o resto claro. A conta, no papel:
        //   linha 0: faixa escura de 6            ⇒ 3 + 1      =  4
        //   linhas 1..5: faixa clara de 6, cada   ⇒ 5 × 4      = 20
        //   colunas 0..5: 1 escuro + 5 claros     ⇒ 6 × 3      = 18   (a faixa de 1 não pontua)
        //                                                   total = 42
        val row = BooleanArray(6 * 6) { it < 6 }
        assertEquals(42, QrMask.penaltyRule1(row, 6))
    }

    @Test
    fun `regra 1 ignora faixa de quatro`() {
        // 4x4 todo claro: nenhuma faixa alcança 5.
        val size = 4
        assertEquals(0, QrMask.penaltyRule1(BooleanArray(size * size), size))
    }

    // -------------------------------------------------------------------------------------------
    // Regra 2 — blocos 2×2
    // -------------------------------------------------------------------------------------------

    @Test
    fun `regra 2 conta blocos 2x2 SOBREPOSTOS`() {
        // 2x2 uniforme ⇒ exatamente 1 bloco ⇒ 3.
        assertEquals(3, QrMask.penaltyRule2(BooleanArray(4) { true }, 2))

        // 3x3 uniforme ⇒ 4 blocos sobrepostos ⇒ 12. Contar só blocos disjuntos daria 3 (errado),
        // e o efeito seria escolher a máscara errada em símbolos com áreas maciças.
        assertEquals(12, QrMask.penaltyRule2(BooleanArray(9) { true }, 3))

        // Tabuleiro de xadrez 4x4 ⇒ nenhum bloco uniforme.
        val checker = BooleanArray(16) { (it % 4 + it / 4) % 2 == 0 }
        assertEquals(0, QrMask.penaltyRule2(checker, 4))
    }

    // -------------------------------------------------------------------------------------------
    // Regra 3 — trecho parecido com o padrão de localização
    // -------------------------------------------------------------------------------------------

    @Test
    fun `regra 3 pontua 40 no padrao 1-1-3-1-1 com area clara ao lado`() {
        // Linha de 11 módulos: 1011101 + 0000 ⇒ uma ocorrência (40).
        val pattern = "10111010000"
        val size = 11
        val dark = BooleanArray(size * size)
        for (x in 0 until size) if (pattern[x] == '1') dark[0 * size + x] = true

        // Exatamente UMA ocorrência (a janela da linha 0); nenhuma coluna casa.
        assertEquals(QrMask.PENALTY_N3, QrMask.penaltyRule3(dark, size))
    }

    @Test
    fun `regra 3 tambem detecta o arranjo espelhado (area clara ANTES)`() {
        val size = 11
        val mirrored = "00001011101"
        val dark = BooleanArray(size * size)
        for (x in 0 until size) if (mirrored[x] == '1') dark[0 * size + x] = true
        assertEquals(QrMask.PENALTY_N3, QrMask.penaltyRule3(dark, size))
    }

    @Test
    fun `regra 3 nao pontua padrao de localizacao SEM area clara ao lado`() {
        // 1011101 grudado em escuros dos dois lados: não é o arranjo que confunde o leitor.
        val size = 11
        val dark = BooleanArray(size * size) { true }
        val core = "11011101111" // escuros com o "claro" só nas posições internas do núcleo
        for (x in 0 until size) dark[0 * size + x] = core[x] == '1'
        // Nenhuma das duas janelas de 11 bits casa — penalidade ZERO, não "múltiplo de 40".
        assertEquals(0, QrMask.penaltyRule3(dark, size))
    }

    // -------------------------------------------------------------------------------------------
    // Regra 4 — equilíbrio claro/escuro
    // -------------------------------------------------------------------------------------------

    @Test
    fun `regra 4 nao pontua dentro da faixa de 45 a 55 por cento`() {
        val size = 10 // 100 módulos: fácil de raciocinar em porcentagem
        assertEquals(0, QrMask.penaltyRule4(BooleanArray(size * size) { it < 50 }, size)) // 50%
        assertEquals(0, QrMask.penaltyRule4(BooleanArray(size * size) { it < 45 }, size)) // 45%
        assertEquals(0, QrMask.penaltyRule4(BooleanArray(size * size) { it < 55 }, size)) // 55%
    }

    @Test
    fun `regra 4 pontua 10 por cada 5 por cento fora da faixa`() {
        val size = 10
        assertEquals(10, QrMask.penaltyRule4(BooleanArray(size * size) { it < 60 }, size)) // 60%
        assertEquals(10, QrMask.penaltyRule4(BooleanArray(size * size) { it < 40 }, size)) // 40%
        assertEquals(20, QrMask.penaltyRule4(BooleanArray(size * size) { it < 65 }, size)) // 65%
        assertEquals(20, QrMask.penaltyRule4(BooleanArray(size * size) { it < 35 }, size)) // 35%
    }

    @Test
    fun `regra 4 no extremo - matriz toda de uma cor`() {
        val size = 10
        // 0% escuro ⇒ desvio de 50 pontos ⇒ k = 9 ⇒ 90.
        assertEquals(90, QrMask.penaltyRule4(BooleanArray(size * size), size))
        assertEquals(90, QrMask.penaltyRule4(BooleanArray(size * size) { true }, size))
    }

    // -------------------------------------------------------------------------------------------
    // Máscaras
    // -------------------------------------------------------------------------------------------

    @Test
    fun `as oito mascaras seguem a Tabela 10 e nao confundem linha com coluna`() {
        // Máscara 1 depende SÓ da linha; máscara 2 SÓ da coluna. São elas que revelam a troca de i/j —
        // as outras seis são simétricas o suficiente para o erro passar.
        assertTrue(QrMask.isMasked(1, x = 0, y = 0))
        assertTrue(QrMask.isMasked(1, x = 5, y = 0), "máscara 1 não deve depender de x")
        assertTrue(!QrMask.isMasked(1, x = 0, y = 1))

        assertTrue(QrMask.isMasked(2, x = 0, y = 0))
        assertTrue(QrMask.isMasked(2, x = 0, y = 7), "máscara 2 não deve depender de y")
        assertTrue(!QrMask.isMasked(2, x = 1, y = 0))

        // Máscara 4: (y/2 + x/3) % 2 == 0.
        assertTrue(QrMask.isMasked(4, x = 0, y = 0))
        assertTrue(!QrMask.isMasked(4, x = 3, y = 0))
        assertTrue(QrMask.isMasked(4, x = 3, y = 2))

        assertEquals(8, QrMask.COUNT)
    }

    @Test
    fun `mascara invalida lanca`() {
        val threw = try {
            QrMask.isMasked(8, 0, 0)
            false
        } catch (_: IllegalArgumentException) {
            true
        }
        assertTrue(threw)
    }
}

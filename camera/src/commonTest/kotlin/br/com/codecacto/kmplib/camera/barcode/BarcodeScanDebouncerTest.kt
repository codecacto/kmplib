package br.com.codecacto.kmplib.camera.barcode

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

/**
 * Anti-repetição da leitura contínua — a diferença entre "scanner" e "trinta cadastros por
 * segundo". O tempo entra por parâmetro, então todo o comportamento é verificável sem device.
 */
class BarcodeScanDebouncerTest {

    private val ean = "7891000100103"
    private val outro = "7896004000015"

    @Test
    fun `o mesmo codigo na mira dispara UMA vez, nao a cada frame`() {
        val debouncer = BarcodeScanDebouncer()
        var aceitos = 0
        // 2 segundos de câmera a 30 fps sobre o mesmo produto.
        for (frame in 0 until 60) {
            if (debouncer.accept(ean, frame * 33L)) aceitos++
        }
        assertEquals(1, aceitos)
    }

    @Test
    fun `passado o cooldown do mesmo codigo, ele pode ser lido de novo`() {
        // Duas caixas do MESMO produto com validades diferentes é o caso normal do varejo.
        val debouncer = BarcodeScanDebouncer(BarcodeScanDebounce(sameCodeCooldownMillis = 2_000))
        assertTrue(debouncer.accept(ean, 0))
        assertFalse(debouncer.accept(ean, 1_999))
        assertTrue(debouncer.accept(ean, 2_000))
    }

    @Test
    fun `codigo DIFERENTE passa sem recriar a tela, respeitado o intervalo global`() {
        val debouncer = BarcodeScanDebouncer(
            BarcodeScanDebounce(sameCodeCooldownMillis = 5_000, anyCodeCooldownMillis = 600)
        )
        assertTrue(debouncer.accept(ean, 0))
        // Produto vizinho capturado na mesma rajada: barrado.
        assertFalse(debouncer.accept(outro, 200))
        // Depois do intervalo, o próximo produto entra normalmente.
        assertTrue(debouncer.accept(outro, 700))
        // E o primeiro continua suprimido pela janela dele.
        assertFalse(debouncer.accept(ean, 1_400))
    }

    @Test
    fun `reset libera imediatamente ate o ultimo codigo lido`() {
        val debouncer = BarcodeScanDebouncer()
        assertTrue(debouncer.accept(ean, 0))
        assertFalse(debouncer.accept(ean, 100))
        debouncer.reset()
        assertTrue(debouncer.accept(ean, 101), "após salvar o item, reler o mesmo produto deve valer")
    }

    @Test
    fun `confirmacao por leituras consecutivas exige o mesmo valor N vezes`() {
        val debouncer = BarcodeScanDebouncer(
            BarcodeScanDebounce(requiredConsecutiveReads = 3, anyCodeCooldownMillis = 0)
        )
        assertFalse(debouncer.accept(ean, 0))
        assertFalse(debouncer.accept(ean, 33))
        assertTrue(debouncer.accept(ean, 66))
    }

    @Test
    fun `leitura instavel reinicia a contagem de confirmacao`() {
        val debouncer = BarcodeScanDebouncer(
            BarcodeScanDebounce(requiredConsecutiveReads = 3, anyCodeCooldownMillis = 0)
        )
        assertFalse(debouncer.accept(ean, 0))
        assertFalse(debouncer.accept(outro, 33), "valor mudou: contagem zera")
        assertFalse(debouncer.accept(ean, 66))
        assertFalse(debouncer.accept(ean, 99))
        assertTrue(debouncer.accept(ean, 132))
    }

    @Test
    fun `valor em branco nunca e aceito`() {
        val debouncer = BarcodeScanDebouncer()
        assertFalse(debouncer.accept("", 0))
        assertFalse(debouncer.accept("   ", 10))
    }

    @Test
    fun `cooldown zero do mesmo codigo desliga a supressao`() {
        val debouncer = BarcodeScanDebouncer(
            BarcodeScanDebounce(sameCodeCooldownMillis = 0, anyCodeCooldownMillis = 0)
        )
        assertTrue(debouncer.accept(ean, 0))
        assertTrue(debouncer.accept(ean, 1))
    }

    @Test
    fun `perfil SEQUENCE tem janela curta para o modo varios seguidos`() {
        assertTrue(
            BarcodeScanDebounce.SEQUENCE.sameCodeCooldownMillis <
                BarcodeScanDebounce().sameCodeCooldownMillis
        )
    }

    @Test
    fun `configuracao invalida falha alto, nao silenciosamente`() {
        assertFailsWith<IllegalArgumentException> { BarcodeScanDebounce(requiredConsecutiveReads = 0) }
        assertFailsWith<IllegalArgumentException> { BarcodeScanDebounce(sameCodeCooldownMillis = -1) }
        assertFailsWith<IllegalArgumentException> { BarcodeScanDebounce(anyCodeCooldownMillis = -1) }
    }

    @Test
    fun `historico nao cresce sem limite num turno inteiro de escaneamento`() {
        val debouncer = BarcodeScanDebouncer(
            BarcodeScanDebounce(sameCodeCooldownMillis = 1_000, anyCodeCooldownMillis = 0)
        )
        // 500 produtos distintos, um por segundo: nada pode ficar retido além da janela.
        repeat(500) { index ->
            assertTrue(debouncer.accept("codigo-$index", index * 1_000L))
        }
        // O primeiro código já saiu da janela e é aceito de novo (nada ficou preso "para sempre").
        assertTrue(debouncer.accept("codigo-0", 500_000L))
    }
}

package br.com.codecacto.kmplib.pdf

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Testes do contrato de dados do PDF do recibo (commonMain puro). O render em si é
 * por plataforma (Android validado neste host; iOS pelo fundador em macOS).
 */
class ReciboPdfDataTest {

    private fun sample(numero: String = "0001") = ReciboPdfData(
        emitente = ReciboParte(nome = "Diana Souza", documento = "CPF: 123.***.***-09"),
        pagador = ReciboParte(nome = "João da Silva", documento = "CPF: 987.***.***-00"),
        valorFormatado = "R$ 120,00",
        valorPorExtenso = "cento e vinte reais",
        descricao = "aula de violão",
        localData = "São Paulo, 6 de junho de 2026.",
        numeroRecibo = numero,
        dataHoraEmissao = "06/06/2026 14:32",
    )

    @Test
    fun equals_consideraConteudoDeByteArrays() {
        val a = sample().copy(logoBytes = byteArrayOf(1, 2, 3))
        val b = sample().copy(logoBytes = byteArrayOf(1, 2, 3))
        val c = sample().copy(logoBytes = byteArrayOf(9, 9, 9))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }

    @Test
    fun defaultReciboPdfFileName_sanitizaEAplicaExtensao() {
        assertEquals("recibo-0001.pdf", defaultReciboPdfFileName(sample("0001")))
        assertEquals("recibo-OS_2026_7.pdf", defaultReciboPdfFileName(sample("OS/2026 7")))
    }

    @Test
    fun defaultReciboPdfFileName_fallbackQuandoNumeroVazio() {
        val name = defaultReciboPdfFileName(sample(""))
        assertTrue(name.endsWith(".pdf"))
        assertTrue(name.isNotBlank())
    }

    @Test
    fun watermarkText_temDefaultDaSpec() {
        assertEquals("Gerado com ReciboFácil — versão gratuita", sample().watermarkText)
    }
}

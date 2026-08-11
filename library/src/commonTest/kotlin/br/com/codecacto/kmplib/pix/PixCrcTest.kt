package br.com.codecacto.kmplib.pix

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CRC-16/CCITT-FALSE — o algoritmo da tag `63` do BR Code.
 *
 * A validação é ancorada em **fonte externa**, não na própria implementação: o *check value*
 * publicado do CRC-16/CCITT-FALSE (também catalogado como CRC-16/IBM-3740) é `0x29B1` para a
 * entrada `"123456789"`, e o valor inicial `0xFFFF` aparece como resultado da entrada vazia (porque
 * o algoritmo não tem XOR final). Batendo esses dois pontos, os parâmetros — polinômio `0x1021`,
 * init `0xFFFF`, sem reflexão, sem XOR final — estão todos exercitados.
 */
class PixCrcTest {

    @Test
    fun `check value publicado do CRC-16 CCITT-FALSE`() {
        // Catálogo de CRC (CRC-16/CCITT-FALSE, alias CRC-16/IBM-3740): check = 0x29B1.
        assertEquals("29B1", PixCrc.compute("123456789"))
    }

    @Test
    fun `entrada vazia devolve o valor inicial, provando ausencia de XOR final`() {
        assertEquals("FFFF", PixCrc.compute(""))
    }

    @Test
    fun `resultado sai em hex maiusculo de quatro digitos, com zeros a esquerda`() {
        val crc = PixCrc.compute(PixFixtures.staticPix().dropLast(PixCrc.VALUE_LENGTH))
        assertEquals(4, crc.length)
        assertTrue(crc.all { it in '0'..'9' || it in 'A'..'F' }, "esperado hex maiúsculo, foi $crc")
    }

    @Test
    fun `o calculo e sobre bytes UTF-8, nao sobre caracteres`() {
        // "é" é um caractere e dois bytes em UTF-8 (0xC3 0xA9). O CRC tem de ver os bytes.
        val fromText = PixCrc.compute("é")
        val fromBytes = PixCrc.compute(byteArrayOf(0xC3.toByte(), 0xA9.toByte()))
        assertEquals(fromBytes, fromText)
        // E o acento muda o resultado — não é ignorado no caminho.
        assertTrue(fromText != PixCrc.compute("e"))
    }

    @Test
    fun `sign acrescenta 6304 e o CRC, e e idempotente quando o sufixo ja esta la`() {
        val body = "0002010102115204000053039865802BR5913CODECACTO6009SAO PAULO"

        val signed = PixCrc.sign(body)
        assertTrue(signed.startsWith(body + PixCrc.TAG_WITH_LENGTH))
        assertEquals(body.length + 8, signed.length)

        val signedFromTagged = PixCrc.sign(body + PixCrc.TAG_WITH_LENGTH)
        assertEquals(signed, signedFromTagged)
    }

    @Test
    fun `o CRC cobre o payload incluindo 6304 - so o valor fica de fora`() {
        val payload = PixFixtures.staticPix()

        // A regra correta: calcular sobre tudo menos os 4 dígitos do valor.
        val correct = PixCrc.compute(payload.dropLast(PixCrc.VALUE_LENGTH))
        assertEquals(PixCrc.declaredCrcOf(payload), correct)

        // O erro clássico: deixar "6304" de fora do cálculo. Tem de dar OUTRO valor —
        // se um dia coincidir, o teste avisa que a regra foi afrouxada.
        val wrong = PixCrc.compute(payload.dropLast(PixCrc.VALUE_LENGTH + PixCrc.TAG_WITH_LENGTH.length))
        assertTrue(correct != wrong, "o sufixo 6304 tem de entrar no cálculo")
    }

    @Test
    fun `isValid aceita payload intacto e recusa payload alterado`() {
        val payload = PixFixtures.staticPix()
        assertTrue(PixCrc.isValid(payload))
        assertFalse(PixCrc.isValid(PixFixtures.withBrokenCrc(payload)))
        assertFalse(PixCrc.isValid(payload.dropLast(1)))
        assertFalse(PixCrc.isValid(null))
        assertFalse(PixCrc.isValid(""))
    }

    @Test
    fun `declaredCrcOf normaliza a caixa e recusa payload sem a tag 63 no fim`() {
        val payload = PixFixtures.staticPix()
        val declared = PixCrc.declaredCrcOf(payload)!!

        val lowercase = payload.dropLast(PixCrc.VALUE_LENGTH) + declared.lowercase()
        assertEquals(declared, PixCrc.declaredCrcOf(lowercase))
        assertTrue(PixCrc.isValid(lowercase), "CRC minúsculo é íntegro — recusar por caixa é falso alarme")

        assertNull(PixCrc.declaredCrcOf("00020101021126"))
        assertNull(PixCrc.declaredCrcOf(payload.dropLast(PixCrc.VALUE_LENGTH) + "ZZZZ"))
    }
}

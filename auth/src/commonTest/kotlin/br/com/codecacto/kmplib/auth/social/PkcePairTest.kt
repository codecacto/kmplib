package br.com.codecacto.kmplib.auth.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Contrato do PKCE do aplicativo com o nosso backend.
 *
 * A codificação é o ponto sensível: o backend recalcula `base64url(SHA-256(verifier))` e compara em
 * tempo constante. Um `+`, uma `/` ou um `=` a mais aqui — a diferença entre base64 comum e base64url
 * — fazem **todo** login social falhar com 401, sem nada indicando a causa.
 */
class PkcePairTest {

    @Test
    fun base64url_nao_usa_o_alfabeto_do_base64_comum() {
        // Estes três bytes produzem `+` e `/` em base64 comum; em base64url viram `-` e `_`.
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xFE.toByte())

        val codificado = base64UrlNoPadding(bytes)

        assertEquals("-__-", codificado)
        assertTrue(codificado.none { it == '+' || it == '/' || it == '=' })
    }

    @Test
    fun base64url_nao_leva_preenchimento_em_nenhum_resto() {
        // Restos 1 e 2 são onde o `=` apareceria.
        assertEquals("AA", base64UrlNoPadding(byteArrayOf(0)))
        assertEquals("AAA", base64UrlNoPadding(byteArrayOf(0, 0)))
        assertEquals("AAAA", base64UrlNoPadding(byteArrayOf(0, 0, 0)))
    }

    @Test
    fun base64url_codifica_valor_conhecido() {
        // "abc" -> YWJj (mesmo resultado em base64 comum, sem caractere especial)
        assertEquals("YWJj", base64UrlNoPadding("abc".encodeToByteArray()))
    }

    @Test
    fun verifier_tem_o_tamanho_que_a_rfc_e_o_backend_exigem() {
        val par = PkcePair.generate()

        // 32 bytes em base64url sem preenchimento = 43 caracteres, o piso da RFC 7636 §4.1.
        assertEquals(43, par.verifier.length)
        assertTrue(par.verifier.all { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun desafio_e_o_hash_do_verifier_no_mesmo_alfabeto() {
        val par = PkcePair.generate()

        assertEquals(base64UrlNoPadding(PkceCrypto.sha256(par.verifier.encodeToByteArray())), par.challenge)
        assertTrue(par.challenge.none { it == '+' || it == '/' || it == '=' })
        assertNotEquals(par.verifier, par.challenge)
    }

    @Test
    fun cada_login_tem_um_par_proprio() {
        // Reusar o par entre logins deixaria um código interceptado utilizável no login seguinte.
        assertNotEquals(PkcePair.generate().verifier, PkcePair.generate().verifier)
    }
}

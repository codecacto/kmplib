package br.com.codecacto.kmplib.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JwtDecoderTest {

    @Test
    fun `extrai o sub do payload`() {
        assertEquals("acc-123", JwtDecoder.subject(fakeJwt("acc-123")))
    }

    @Test
    fun `sub com uuid completo`() {
        val uuid = "550e8400-e29b-41d4-a716-446655440000"
        assertEquals(uuid, JwtDecoder.subject(fakeJwt(uuid)))
    }

    @Test
    fun `token malformado devolve null`() {
        assertNull(JwtDecoder.subject("nao-e-um-jwt"))
        assertNull(JwtDecoder.subject(""))
        assertNull(JwtDecoder.subject("a.b"))
    }

    @Test
    fun `claim inexistente no payload devolve null`() {
        // payload válido (tem sub/iat), mas o claim pedido não existe
        assertNull(JwtDecoder.claim(fakeJwt("acc-1"), "email"))
    }
}

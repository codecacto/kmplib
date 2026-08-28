@file:Suppress("DEPRECATION")

package br.com.codecacto.kmplib.auth.social

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Contrato de compatibilidade da mudança de pacote (2.98.0).
 *
 * Os 10 apps que usam Firebase Auth importam `br.com.codecacto.kmplib.firebase.auth.*` para os
 * providers sociais. O `typealias @Deprecated` tem de resolver para **o mesmo tipo**, não para uma
 * cópia — senão um `is`/`as` num app existente passaria a falhar em runtime com a mesma aparência
 * de antes na compilação.
 */
class SocialProviderAliasTest {

    @Test
    fun `alias do pacote firebase resolve para o MESMO tipo do pacote social`() {
        val novo: GoogleSignInResult = GoogleSignInResult.success("id-1", accessToken = "acc")
        // O tipo declarado é o do pacote antigo; a atribuição só compila se for o mesmo tipo.
        val antigo: br.com.codecacto.kmplib.firebase.auth.GoogleSignInResult = novo
        assertSame(novo, antigo)
        assertEquals("id-1", antigo.idToken)

        val apple: AppleSignInResult = AppleSignInResult.success("t", "n")
        val appleAntigo: br.com.codecacto.kmplib.firebase.auth.AppleSignInResult = apple
        assertSame(apple, appleAntigo)
    }

    @Test
    fun `construir pelo nome antigo produz instancia do tipo novo`() {
        val viaAlias = br.com.codecacto.kmplib.firebase.auth.GoogleSignInResult(idToken = "x")
        assertTrue(viaAlias is GoogleSignInResult)
        assertTrue(viaAlias.isSuccess)
    }

    @Test
    fun `GoogleSignInResult distingue sucesso, erro e cancelamento`() {
        assertTrue(GoogleSignInResult.success("t").isSuccess)
        assertFalse(GoogleSignInResult.error("boom").isSuccess)
        assertTrue(GoogleSignInResult.cancelled().isCancelled)
        assertFalse(GoogleSignInResult.error("boom").isCancelled)
        assertNull(GoogleSignInResult.error("boom").idToken)
    }

    @Test
    fun `AppleSignInResult so e sucesso com idToken E nonce`() {
        assertTrue(AppleSignInResult.success("t", "n").isSuccess)
        // Sem o nonce cru não há como o servidor refazer o hash e conferir — não é sucesso.
        assertFalse(AppleSignInResult(idToken = "t", nonce = null).isSuccess)
        assertFalse(AppleSignInResult(idToken = null, nonce = "n").isSuccess)
        assertTrue(AppleSignInResult.cancelled().isCancelled)
    }
}

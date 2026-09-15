package br.com.codecacto.kmplib.auth.social

import br.com.codecacto.kmplib.auth.SocialProvider
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Por onde cada provedor negocia o login.
 *
 * O caso que originou a regra: com o modo `BACKEND`, a Apple ia para o `/social/start`, onde o
 * backend só registra o Google, e o botão respondia "Provedor social não habilitado" (Backhand,
 * 15/set/2026).
 */
class CaminhoDoLoginSocialTest {

    @Test
    fun `Apple e nativa no modo BACKEND`() {
        assertEquals(
            CaminhoDoLoginSocial.NATIVO,
            caminhoDoLogin(SocialLoginMode.BACKEND, SocialProvider.APPLE),
            "A Apple não passa pelo BFF: o backend não a registra no fluxo pelo navegador.",
        )
    }

    @Test
    fun `Apple e nativa no modo NATIVE`() {
        assertEquals(
            CaminhoDoLoginSocial.NATIVO,
            caminhoDoLogin(SocialLoginMode.NATIVE, SocialProvider.APPLE),
        )
    }

    @Test
    fun `Google segue o modo do projeto`() {
        assertEquals(
            CaminhoDoLoginSocial.NAVEGADOR,
            caminhoDoLogin(SocialLoginMode.BACKEND, SocialProvider.GOOGLE),
        )
        assertEquals(
            CaminhoDoLoginSocial.NATIVO,
            caminhoDoLogin(SocialLoginMode.NATIVE, SocialProvider.GOOGLE),
        )
    }
}

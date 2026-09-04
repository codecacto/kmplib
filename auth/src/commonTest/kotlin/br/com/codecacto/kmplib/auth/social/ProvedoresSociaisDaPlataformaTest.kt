package br.com.codecacto.kmplib.auth.social

import br.com.codecacto.kmplib.auth.SocialProvider
import br.com.codecacto.kmplib.core.util.currentPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A regra que decide se o botão da Apple aparece.
 *
 * O teste ataca a função pura ([provedoresSociaisPara]) porque a constante de verdade depende do
 * alvo em que a suíte roda — e o caso que importa (Android **sem** Apple) é justamente o que não
 * dá para provar rodando no iOS, e vice-versa.
 */
class ProvedoresSociaisDaPlataformaTest {

    @Test
    fun `no Android a Apple NAO entra`() {
        val provedores = provedoresSociaisPara("android")
        assertFalse(
            SocialProvider.APPLE in provedores,
            "Sign in with Apple não existe no Android: o botão ali só sabe dar erro.",
        )
        assertEquals(setOf(SocialProvider.GOOGLE), provedores)
    }

    @Test
    fun `no iOS os dois entram`() {
        assertEquals(
            setOf(SocialProvider.GOOGLE, SocialProvider.APPLE),
            provedoresSociaisPara("ios"),
        )
    }

    @Test
    fun `plataforma desconhecida cai no denominador comum, nunca na Apple`() {
        // Alvo novo (desktop, web) entra pelo `else`. A Apple é exceção declarada, não presunção.
        val provedores = provedoresSociaisPara("jvm")
        assertEquals(setOf(SocialProvider.GOOGLE), provedores)
    }

    @Test
    fun `o Google esta disponivel em toda plataforma`() {
        listOf("android", "ios", "jvm").forEach { plataforma ->
            assertTrue(SocialProvider.GOOGLE in provedoresSociaisPara(plataforma), plataforma)
        }
    }

    @Test
    fun `a constante da plataforma concorda com a funcao pura`() {
        assertEquals(provedoresSociaisPara(currentPlatform), provedoresSociaisDaPlataforma)
        assertEquals(
            SocialProvider.APPLE in provedoresSociaisDaPlataforma,
            SocialProvider.APPLE.disponivelNestaPlataforma,
        )
    }
}

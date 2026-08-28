package br.com.codecacto.kmplib.ui.screens.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O id de teste é **contrato**: o flow do Maestro e o spec do Playwright escrevem estas strings. Um
 * rename aqui não quebra build nenhum — quebra a suíte de login de todos os apps, com um erro que
 * manda procurar defeito de autenticação onde só mudou um nome.
 */
class LoginTestTagsTest {

    private val todos = listOf(
        LoginTestTags.INPUT_EMAIL,
        LoginTestTags.INPUT_SENHA,
        LoginTestTags.BOTAO_ENTRAR,
        LoginTestTags.BOTAO_ESQUECI_SENHA,
        LoginTestTags.BOTAO_CADASTRAR,
        LoginTestTags.BOTAO_GOOGLE,
        LoginTestTags.BOTAO_APPLE,
        LoginTestTags.ERRO,
    )

    @Test
    fun `nenhum id se repete`() {
        // O caso que isto pega é o do paywall, de novo: dois elementos com o mesmo id fazem o teste
        // tocar no primeiro da tela e passar verde tendo exercitado o outro. Em login, "entrou" é
        // verdade tanto pelo Google quanto pela Apple — e aí o provedor quebrado nunca aparece.
        assertEquals(todos.toSet().size, todos.size)
    }

    @Test
    fun `o vocabulario e o mesmo do lado web`() {
        // Prefixo `login-`, minúsculo, com hífen: a convenção declarada no briefing do runner, para
        // um flow servir app e portal do mesmo produto sem tradução de seletor.
        todos.forEach { id ->
            assertTrue(id.startsWith("login-"), "id fora da convenção: $id")
            assertTrue(id == id.lowercase(), "id com maiúscula: $id")
            assertTrue(!id.contains("_"), "id com underscore em vez de hífen: $id")
        }
    }

    @Test
    fun `cada provedor social tem id proprio`() {
        assertEquals("login-btn-google", LoginTestTags.BOTAO_GOOGLE)
        assertEquals("login-btn-apple", LoginTestTags.BOTAO_APPLE)
    }

    @Test
    fun `o erro tem id, senao falha silenciosa passa por tela que nao abriu`() {
        assertEquals("login-erro", LoginTestTags.ERRO)
    }
}

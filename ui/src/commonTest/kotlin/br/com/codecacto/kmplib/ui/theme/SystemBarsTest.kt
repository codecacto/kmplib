package br.com.codecacto.kmplib.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Trava a regra dos ícones das barras do sistema (2.241.0): a cor sai do que está ATRÁS do ícone,
 * e o pedido de uma tela vale só enquanto ela está em cena.
 */
class SystemBarsTest {

    private val claro = Color(0xFFF5F5F5)
    private val escuro = Color(0xFF121212)

    @Test
    fun `fundo claro pede icone escuro e fundo escuro pede icone claro`() {
        assertTrue(needsDarkSystemBarIcons(claro))
        assertTrue(needsDarkSystemBarIcons(Color.White))
        assertFalse(needsDarkSystemBarIcons(escuro))
        assertFalse(needsDarkSystemBarIcons(Color.Black))
        // Faixa de marca saturada e escura (verde de topo): ícone claro.
        assertFalse(needsDarkSystemBarIcons(Color(0xFF0A7D3B)))
    }

    @Test
    fun `barra transparente decide pelo conteudo`() {
        // Edge-to-edge: o defeito do print — tela clara tem de ter ícone escuro, seja qual for o modo do aparelho.
        assertTrue(needsDarkSystemBarIcons(barColor = Color.Transparent, contentBackground = claro))
        assertFalse(needsDarkSystemBarIcons(barColor = Color.Transparent, contentBackground = escuro))
    }

    @Test
    fun `barra opaca decide pela cor dela e nao pelo conteudo`() {
        // Android <= 14 sem edge-to-edge: barra preta do tema da janela sobre app claro.
        assertFalse(needsDarkSystemBarIcons(barColor = Color.Black, contentBackground = claro))
        assertTrue(needsDarkSystemBarIcons(barColor = Color.White, contentBackground = escuro))
    }

    @Test
    fun `pelicula translucida decide pela mistura`() {
        // Película clara (90% branco) da navegação por botões sobre conteúdo escuro: fica clara.
        assertTrue(needsDarkSystemBarIcons(Color.White.copy(alpha = 0.9f), escuro))
        // Película escura a 80% sobre conteúdo claro: cinza-escuro → ícone claro.
        assertFalse(needsDarkSystemBarIcons(Color(0xFF1B1B1B).copy(alpha = 0.8f), claro))
    }

    @Test
    fun `o ultimo pedido a entrar vence e sair devolve ao anterior`() {
        val requests = SystemBarsRequests()
        assertNull(requests.current)
        val tema = Any()
        val login = Any()
        requests.put(tema, SystemBarsBackground(escuro))
        assertEquals(escuro, requests.current?.statusBar)
        requests.put(login, SystemBarsBackground(claro))
        assertEquals(claro, requests.current?.statusBar)
        requests.remove(login)
        assertEquals(escuro, requests.current?.statusBar)
        requests.remove(tema)
        assertNull(requests.current)
    }

    @Test
    fun `atualizar um pedido nao passa na frente de quem esta por cima`() {
        val requests = SystemBarsRequests()
        val tema = Any()
        val login = Any()
        requests.put(tema, SystemBarsBackground(escuro))
        requests.put(login, SystemBarsBackground(claro))
        // O tema troca de modo com o login em cena: o login continua valendo.
        requests.put(tema, SystemBarsBackground(Color.White))
        assertEquals(claro, requests.current?.statusBar)
        requests.remove(login)
        assertEquals(Color.White, requests.current?.statusBar)
    }

    @Test
    fun `barra de navegacao herda a da status bar quando nao informada`() {
        val so = SystemBarsBackground(claro)
        assertEquals(claro, so.navigationBar)
        assertEquals(escuro, SystemBarsBackground(claro, escuro).navigationBar)
    }
}

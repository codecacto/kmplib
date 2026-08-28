package br.com.codecacto.kmplib.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Testes de lógica pura do Avatar — não requerem Compose UI test.
 *
 * Testes de renderização ficam em [AvatarUiTest].
 */
class AvatarLogicTest {

    @Test
    fun `colorForName retorna cor determinística para mesmo nome`() {
        assertEquals(colorForName("Joao Silva"), colorForName("Joao Silva"))
        assertEquals(colorForName("Maria"), colorForName("Maria"))
    }

    @Test
    fun `colorForName é case insensitive`() {
        assertEquals(colorForName("Joao"), colorForName("JOAO"))
        assertEquals(colorForName("joao"), colorForName("Joao"))
    }

    @Test
    fun `colorForName ignora espaços nas pontas`() {
        assertEquals(colorForName("Joao"), colorForName("  Joao  "))
    }

    @Test
    fun `colorForName retorna cor consistente para nome vazio`() {
        // Nome vazio retorna primeira cor da paleta (fallback).
        // Ambas as chamadas devem retornar o mesmo valor.
        assertEquals(colorForName(""), colorForName(""))
        assertEquals(colorForName(""), colorForName("   "))
    }

    @Test
    fun `colorForName retorna cores diferentes para nomes muito diferentes`() {
        // Não dá pra garantir 100% (colisão de hash existe), mas com 10 cores
        // na paleta a chance de colisão entre dois nomes muito diferentes é baixa.
        val colors = listOf(
            "Joao", "Maria", "Pedro", "Ana", "Carlos",
            "Lucia", "Roberto", "Beatriz", "Felipe", "Camila"
        ).map { colorForName(it) }.toSet()

        // Espera-se pelo menos 5 cores distintas entre os 10 nomes
        // (com 10 cores na paleta e hashCode bem distribuído).
        assert(colors.size >= 5) { "Esperava ao menos 5 cores distintas, obteve ${colors.size}" }
    }

    @Test
    fun `colorForName distingue case com mesma string apenas se nome diferente`() {
        // Mesmo nome em case diferente → mesma cor (case insensitive)
        assertEquals(colorForName("Test"), colorForName("test"))
        // Nomes diferentes → cores podem ser diferentes
        assertNotEquals(
            colorForName("AAAAAAAAA"),
            colorForName("ZZZZZZZZZ"),
            "Nomes muito diferentes devem (provavelmente) gerar cores distintas"
        )
    }
}

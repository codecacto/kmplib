package br.com.codecacto.kmplib.developer

import kotlin.test.Test
import kotlin.test.assertEquals

class WithoutCurrentAppTest {

    private val vitrine = listOf(
        DeveloperApp(id = "super-8", name = "Super 8"),
        DeveloperApp(id = "btu", name = "Calculadora BTU (app)"),
        DeveloperApp(id = "locaki", name = "LocAki"),
        DeveloperApp(id = "advogado", name = "Meu Advogado"),
    )

    @Test
    fun `tira o proprio app ignorando caixa acento e espacos`() {
        assertEquals(
            listOf("super-8", "btu", "advogado"),
            vitrine.withoutCurrentApp("  locaki ").map { it.id },
        )
        assertEquals(
            listOf("super-8", "btu", "locaki"),
            vitrine.withoutCurrentApp("Meu  Advogádo").map { it.id },
        )
    }

    @Test
    fun `ignora o complemento entre parenteses do cadastro`() {
        assertEquals(
            listOf("super-8", "locaki", "advogado"),
            vitrine.withoutCurrentApp("Calculadora BTU").map { it.id },
        )
    }

    @Test
    fun `nome desconhecido nulo ou em branco mostra todos`() {
        assertEquals(vitrine, vitrine.withoutCurrentApp(null))
        assertEquals(vitrine, vitrine.withoutCurrentApp("   "))
        assertEquals(vitrine, vitrine.withoutCurrentApp("OnMore"))
    }

    @Test
    fun `nao tira projeto que so comeca com o nome do app`() {
        val lista = listOf(DeveloperApp(id = "s8", name = "Super 8"), DeveloperApp(id = "s8pro", name = "Super 8 Pro"))
        assertEquals(listOf("s8pro"), lista.withoutCurrentApp("Super 8").map { it.id })
    }
}

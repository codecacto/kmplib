package br.com.codecacto.kmplib.testing.vm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MarcaDeFluxoTest {

    @Test
    fun nome_troca_o_ponto_por_sublinhado_e_volta_exato() {
        val nome = MarcaDeFluxo.nome("locacao.cancelar", Celula.TELA, Caminho.CONEXAO_CAIU, "confere no servidor")
        assertEquals("fluxo locacao_cancelar tela conexao-caiu - confere no servidor", nome)
        assertEquals(MarcaDeFluxo.Marca("locacao.cancelar", "tela", "conexao-caiu"), MarcaDeFluxo.ler(nome))
    }

    @Test
    fun nome_sem_caminho_e_sem_descricao() {
        val nome = MarcaDeFluxo.nome("assinatura.premium", Celula.SERVIDOR)
        assertEquals("fluxo assinatura_premium servidor", nome)
        assertEquals(MarcaDeFluxo.Marca("assinatura.premium", "servidor", null), MarcaDeFluxo.ler(nome))
    }

    @Test
    fun colchetes_para_nome_em_texto() {
        val nome = MarcaDeFluxo.colchetes("item.criar-editar", Celula.TELA, Caminho.DUPLO_TOQUE, "grava uma vez")
        assertEquals("[item.criar-editar|tela|duplo-toque] grava uma vez", nome)
        assertEquals(MarcaDeFluxo.Marca("item.criar-editar", "tela", "duplo-toque"), MarcaDeFluxo.ler(nome))
        assertEquals(MarcaDeFluxo.Marca("auth.entrar", "e2e", null), MarcaDeFluxo.ler("[auth.entrar|e2e]"))
    }

    @Test
    fun nome_sem_marca_devolve_null() {
        assertNull(MarcaDeFluxo.ler("calcula o total"))
        assertNull(MarcaDeFluxo.ler("fluxo x celula-errada"))
    }

    @Test
    fun valida_id_celula_e_caminho() {
        assertFailsWith<IllegalArgumentException> { MarcaDeFluxo.nome("Locacao.Cancelar", Celula.TELA) }
        assertFailsWith<IllegalArgumentException> { MarcaDeFluxo.nome("locacao_cancelar", Celula.TELA) }
        assertFailsWith<IllegalArgumentException> { MarcaDeFluxo.nome("locacao.cancelar", "ui") }
        assertFailsWith<IllegalArgumentException> { MarcaDeFluxo.colchetes("locacao.cancelar", Celula.TELA, "timeout") }
    }

    @Test
    fun constantes_dos_caminhos_e_celulas() {
        assertEquals(listOf("sucesso", "recusa", "conexao-caiu", "duplo-toque"), Caminho.TODOS)
        assertEquals(listOf("regra", "servidor", "tela", "e2e"), Celula.TODAS)
    }
}

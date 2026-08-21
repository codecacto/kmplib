package br.com.codecacto.kmplib.brdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A REGRA do bloco de endereço — não a UI dele.
 *
 * A fábrica não escreve teste de UI em KMP (decisão jun/2026), e é por isso que `mergedWith`,
 * `completo` e `isValidUf` são funções puras fora do composable: o que quebra caro aqui é o merge do
 * CEP apagando o que a pessoa digitou, e isso se testa sem desenhar nada.
 */
class AddressTest {

    private val completo = Address(
        cep = "05424020",
        logradouro = "Rua Professor Carlos Reis",
        numero = "77",
        complemento = "Apto 12",
        bairro = "Pinheiros",
        cidade = "São Paulo",
        uf = "SP",
    )

    // ── completo / temAlgumCampo ────────────────────────────────────────────────────────────────

    @Test
    fun endereco_com_todos_os_campos_e_completo() {
        assertTrue(completo.completo)
    }

    @Test
    fun complemento_nao_entra_no_completo() {
        // É o único campo que um endereço válido pode não ter — e exigi-lo faria uma casa sem apto
        // nunca pré-preencher checkout nenhum.
        assertTrue(completo.copy(complemento = "").completo)
    }

    @Test
    fun falta_de_qualquer_outro_campo_derruba_o_completo() {
        assertFalse(completo.copy(cep = "").completo)
        assertFalse(completo.copy(logradouro = "").completo)
        assertFalse(completo.copy(numero = "").completo)
        assertFalse(completo.copy(bairro = "").completo)
        assertFalse(completo.copy(cidade = "").completo)
        assertFalse(completo.copy(uf = "").completo)
    }

    @Test
    fun vazio_nao_tem_campo_algum_e_um_campo_ja_conta() {
        // A diferença entre "está em branco" e "nunca preencheu" decide se o PATCH manda o bloco —
        // e mandar sete vazios APAGA o endereço no servidor.
        assertFalse(Address.EMPTY.temAlgumCampo)
        assertTrue(Address.EMPTY.copy(complemento = "fundos").temAlgumCampo)
    }

    // ── mergedWith: o CEP nunca apaga o que a pessoa digitou ────────────────────────────────────

    @Test
    fun busca_preenche_apenas_o_que_esta_em_branco() {
        val digitado = Address(cep = "05424020", logradouro = "Rua que eu corrigi")
        val resultado = digitado.mergedWith(
            CepLookupResult(
                logradouro = "Rua Professor Carlos Reis",
                bairro = "Pinheiros",
                cidade = "São Paulo",
                uf = "SP",
            ),
        )
        // O que a pessoa escreveu VENCE: um CEP genérico de bairro devolve outra rua, e sobrescrever
        // faria a correção dela sumir sem aviso.
        assertEquals("Rua que eu corrigi", resultado.logradouro)
        assertEquals("Pinheiros", resultado.bairro)
        assertEquals("São Paulo", resultado.cidade)
        assertEquals("SP", resultado.uf)
    }

    @Test
    fun busca_nao_apaga_com_valor_vazio() {
        // CEP de cidade inteira devolve logradouro em branco. Sobrescrever seria piorar o que já
        // estava lá.
        val base = Address(bairro = "Centro", logradouro = "Av. Brasil")
        val resultado = base.mergedWith(CepLookupResult(cidade = "Mirassol", uf = "SP"))
        assertEquals("Av. Brasil", resultado.logradouro)
        assertEquals("Centro", resultado.bairro)
        assertEquals("Mirassol", resultado.cidade)
    }

    @Test
    fun busca_sem_resultado_nao_muda_nada() {
        // "Não achei" é desfecho normal: serviço fora do ar, CEP inexistente, rede ruim.
        assertEquals(completo, completo.mergedWith(null))
    }

    @Test
    fun busca_nunca_preenche_o_numero() {
        // Nenhum serviço de CEP sabe o número — por isso ele é o campo que ganha o foco depois.
        val resultado = Address(cep = "05424020").mergedWith(
            CepLookupResult(logradouro = "Rua X", bairro = "Y", cidade = "Z", uf = "SP"),
        )
        assertEquals("", resultado.numero)
    }

    @Test
    fun busca_normaliza_a_uf_para_maiuscula() {
        val resultado = Address.EMPTY.mergedWith(CepLookupResult(uf = "sp", cidade = "São Paulo"))
        assertEquals("SP", resultado.uf)
    }

    // ── UF ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun uf_existente_passa_em_qualquer_caixa() {
        assertTrue(isValidUf("SP"))
        assertTrue(isValidUf("sp"))
        assertTrue(isValidUf(" RJ "))
    }

    @Test
    fun uf_inexistente_reprova() {
        assertFalse(isValidUf("XX"))
        assertFalse(isValidUf("S"))
    }

    @Test
    fun uf_em_branco_passa_porque_o_bloco_pode_ser_opcional() {
        // Recusar campo vazio transformaria um endereço opcional em obrigatório sem ninguém decidir.
        assertTrue(isValidUf(""))
        assertTrue(isValidUf("   "))
    }

    @Test
    fun filtro_da_uf_tira_numero_corta_em_duas_e_sobe_a_caixa() {
        assertEquals("SP", filterUfInput("sp1"))
        assertEquals("RJ", filterUfInput("rjxx"))
        assertEquals("", filterUfInput("123"))
    }

    // ── normalização ────────────────────────────────────────────────────────────────────────────

    @Test
    fun normalized_apara_espacos_e_sobe_a_uf() {
        val bagunçado = Address(logradouro = "  Rua X  ", cidade = " Santos ", uf = " sp ")
        val limpo = bagunçado.normalized()
        assertEquals("Rua X", limpo.logradouro)
        assertEquals("Santos", limpo.cidade)
        assertEquals("SP", limpo.uf)
    }

    @Test
    fun cepDigits_devolve_so_numero() {
        assertEquals("05424020", Address(cep = "05424-020").cepDigits)
    }
}

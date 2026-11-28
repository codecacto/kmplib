package br.com.codecacto.kmplib.core.config

import br.com.codecacto.kmplib.core.format.isValidCnpj
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProviderIdentityTest {

    @AfterTest
    fun limpar() = KmpLibProvider.reset()

    @Test
    fun codecacto_tem_os_dados_do_decreto_7962() {
        val p = ProviderIdentity.CodeCacto
        assertEquals("CodeCacto", p.brandName)
        assertEquals("RAPHAEL AMORIM MARQUES TECNOLOGIA DA INFORMAÇÃO LTDA", p.legalName)
        assertTrue(isValidCnpj(p.cnpj), "CNPJ da CodeCacto tem de ser válido")
        assertEquals("61.927.873/0001-39", p.formattedCnpj)
        assertEquals("contato@codecacto.com.br", p.contactEmail)
        assertEquals("dpo@codecacto.com.br", p.dpoEmail)
        assertTrue(p.hasDistinctBrand)
    }

    @Test
    fun endereco_no_formato_da_fabrica() {
        assertEquals(
            "Rua Pais Leme, 215, Conj. 1713 — Pinheiros — São Paulo/SP — CEP 05424-150",
            ProviderIdentity.CodeCacto.address.formatted(),
        )
    }

    @Test
    fun endereco_sem_complemento_e_cep_sem_traco() {
        val a = ProviderAddress(
            street = "Av. Brasil",
            number = "10",
            complement = "  ",
            district = "Centro",
            city = "Cuiabá",
            state = "MT",
            postalCode = "78005000",
        )
        assertEquals("Av. Brasil, 10 — Centro — Cuiabá/MT — CEP 78005-000", a.formatted())
    }

    @Test
    fun campo_em_branco_sai_da_linha() {
        val a = ProviderAddress(street = "Rua A", number = "", district = "", city = "Lisboa", state = "", postalCode = "")
        assertEquals("Rua A — Lisboa", a.formatted())
    }

    @Test
    fun cnpj_sem_pontuacao_sai_formatado_e_invalido_sai_como_veio() {
        val base = ProviderIdentity.CodeCacto
        assertEquals("61.927.873/0001-39", base.copy(cnpj = "61927873000139").formattedCnpj)
        assertEquals("123", base.copy(cnpj = " 123 ").formattedCnpj)
    }

    @Test
    fun marca_igual_a_razao_social_ou_em_branco_nao_e_marca_distinta() {
        val base = ProviderIdentity.CodeCacto
        assertFalse(base.copy(brandName = "").hasDistinctBrand)
        assertFalse(base.copy(brandName = base.legalName.lowercase()).hasDistinctBrand)
    }

    @Test
    fun default_e_codecacto_e_app_de_cliente_troca() {
        assertSame(ProviderIdentity.CodeCacto, KmpLibProvider.current)
        val cliente = ProviderIdentity.CodeCacto.copy(brandName = "Clínica X", legalName = "CLINICA X LTDA")
        KmpLibProvider.configure(cliente)
        assertEquals(cliente, KmpLibProvider.current)
        KmpLibProvider.reset()
        assertSame(ProviderIdentity.CodeCacto, KmpLibProvider.current)
    }
}

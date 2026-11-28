package br.com.codecacto.kmplib.ui.screens.about

import br.com.codecacto.kmplib.core.config.ProviderAddress
import br.com.codecacto.kmplib.core.config.ProviderIdentity
import kotlin.test.Test
import kotlin.test.assertEquals

class ProviderLegalNoticeTest {

    private val texts = ProviderLegalTexts()

    @Test
    fun `codecacto sai como marca da razao social com cnpj endereco e emails`() {
        assertEquals(
            listOf(
                "CodeCacto é uma marca de RAPHAEL AMORIM MARQUES TECNOLOGIA DA INFORMAÇÃO LTDA · " +
                    "CNPJ 61.927.873/0001-39 · " +
                    "Rua Pais Leme, 215, Conj. 1713 — Pinheiros — São Paulo/SP — CEP 05424-150",
                "Contato: contato@codecacto.com.br · Encarregado de dados (LGPD): dpo@codecacto.com.br",
            ),
            providerLegalLines(ProviderIdentity.CodeCacto, texts),
        )
    }

    @Test
    fun `sem marca distinta mostra so a razao social e sem dpo some o encarregado`() {
        val p = ProviderIdentity(
            brandName = "",
            legalName = "CLINICA X LTDA",
            cnpj = "61927873000139",
            address = ProviderAddress("Rua A", "1", null, "Centro", "Cuiabá", "MT", "78005000"),
            contactEmail = "oi@x.com",
        )
        assertEquals(
            listOf(
                "CLINICA X LTDA · CNPJ 61.927.873/0001-39 · Rua A, 1 — Centro — Cuiabá/MT — CEP 78005-000",
                "Contato: oi@x.com",
            ),
            providerLegalLines(p, texts),
        )
    }

    @Test
    fun `traducao entra pelas lambdas`() {
        val en = ProviderLegalTexts(
            brandOf = { b, l -> "$b is a brand of $l" },
            contact = { "Contact: $it" },
            dpo = { "Data protection officer: $it" },
        )
        val lines = providerLegalLines(ProviderIdentity.CodeCacto, en)
        assertEquals(true, lines[0].startsWith("CodeCacto is a brand of RAPHAEL AMORIM"))
        assertEquals("Contact: contato@codecacto.com.br · Data protection officer: dpo@codecacto.com.br", lines[1])
    }

    @Test
    fun `sem email de contato nem dpo fica so a primeira linha`() {
        val p = ProviderIdentity.CodeCacto.copy(contactEmail = " ", dpoEmail = null)
        assertEquals(1, providerLegalLines(p, texts).size)
    }
}

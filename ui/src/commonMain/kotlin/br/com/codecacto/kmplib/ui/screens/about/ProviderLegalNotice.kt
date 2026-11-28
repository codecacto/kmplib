package br.com.codecacto.kmplib.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.config.ProviderIdentity
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_provider_brand_of
import br.com.codecacto.kmplib.generated.resources.kmplib_provider_contact
import br.com.codecacto.kmplib.generated.resources.kmplib_provider_dpo
import br.com.codecacto.kmplib.generated.resources.kmplib_provider_tax_id
import br.com.codecacto.kmplib.ui.locale.kmpGetString
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Textos do [ProviderLegalNotice] (2.384.0). Defaults literais em pt-BR (teste); na tela use
 * [rememberProviderLegalTexts] (pt-BR / pt-PT / en / es).
 */
data class ProviderLegalTexts(
    val brandOf: (brand: String, legalName: String) -> String = { b, l -> "$b é uma marca de $l" },
    val taxId: (cnpj: String) -> String = { "CNPJ $it" },
    val contact: (email: String) -> String = { "Contato: $it" },
    val dpo: (email: String) -> String = { "Encarregado de dados (LGPD): $it" },
)

/** [ProviderLegalTexts] no idioma da tela. */
@Composable
fun rememberProviderLegalTexts(): ProviderLegalTexts {
    val brandOf = kmpStringResource(Res.string.kmplib_provider_brand_of, ARG_1, ARG_2)
    val taxId = kmpStringResource(Res.string.kmplib_provider_tax_id, ARG_1)
    val contact = kmpStringResource(Res.string.kmplib_provider_contact, ARG_1)
    val dpo = kmpStringResource(Res.string.kmplib_provider_dpo, ARG_1)
    return providerLegalTextsOf(brandOf, taxId, contact, dpo)
}

/** [ProviderLegalTexts] fora de composição, no idioma da tela. */
suspend fun loadProviderLegalTexts(): ProviderLegalTexts = providerLegalTextsOf(
    kmpGetString(Res.string.kmplib_provider_brand_of, ARG_1, ARG_2),
    kmpGetString(Res.string.kmplib_provider_tax_id, ARG_1),
    kmpGetString(Res.string.kmplib_provider_contact, ARG_1),
    kmpGetString(Res.string.kmplib_provider_dpo, ARG_1),
)

private fun providerLegalTextsOf(brandOf: String, taxId: String, contact: String, dpo: String) =
    ProviderLegalTexts(
        brandOf = { b, l -> brandOf.replace(ARG_1, b).replace(ARG_2, l) },
        taxId = { taxId.replace(ARG_1, it) },
        contact = { contact.replace(ARG_1, it) },
        dpo = { dpo.replace(ARG_1, it) },
    )

// Marcadores improváveis em texto real (mesma técnica do `rememberAboutTexts`).
private const val ARG_1 = "\u0001a\u0001"
private const val ARG_2 = "\u0001b\u0001"

/**
 * As linhas do aviso do fornecedor — regra pura (2.384.0):
 * 1. "CodeCacto é uma marca de RAZÃO SOCIAL · CNPJ 00.000.000/0000-00 · Endereço" (sem marca
 *    distinta: "RAZÃO SOCIAL · CNPJ … · Endereço");
 * 2. "Contato: e-mail" (+ " · Encarregado de dados (LGPD): dpo@…" quando houver DPO).
 *
 * Campo em branco sai da linha; e-mail de contato em branco tira a 2ª linha (se não houver DPO).
 */
fun providerLegalLines(provider: ProviderIdentity, texts: ProviderLegalTexts): List<String> {
    val name = if (provider.hasDistinctBrand) {
        texts.brandOf(provider.brandName.trim(), provider.legalName.trim())
    } else {
        provider.legalName.trim()
    }
    val cnpj = provider.formattedCnpj.takeIf(String::isNotBlank)?.let(texts.taxId)
    val first = listOfNotNull(name.takeIf(String::isNotBlank), cnpj, provider.address.formatted().takeIf(String::isNotBlank))
        .joinToString(SEPARATOR)
    val second = listOfNotNull(
        provider.contactEmail.trim().takeIf(String::isNotEmpty)?.let(texts.contact),
        provider.dpoEmail?.trim()?.takeIf(String::isNotEmpty)?.let(texts.dpo),
    ).joinToString(SEPARATOR)
    return listOf(first, second).filter(String::isNotEmpty)
}

private const val SEPARATOR = " · "

/** Id do aviso para os flows Maestro. */
const val PROVIDER_LEGAL_NOTICE_TEST_TAG: String = "fornecedor-identificacao"

/**
 * Linha DISCRETA de identificação do fornecedor (Decreto 7.962/2013 art. 2º; LGPD art. 41) —
 * "CodeCacto é uma marca de RAPHAEL AMORIM MARQUES TECNOLOGIA DA INFORMAÇÃO LTDA · CNPJ … · endereço",
 * com o e-mail de contato e o do encarregado logo abaixo. Texto pequeno, centralizado e selecionável
 * (dá para copiar o CNPJ ou o e-mail).
 *
 * Já aparece na [AboutScreen] e na `DeveloperScreen`; use avulso só em tela própria de protótipo.
 */
@Composable
fun ProviderLegalNotice(
    provider: ProviderIdentity,
    modifier: Modifier = Modifier,
    texts: ProviderLegalTexts = rememberProviderLegalTexts(),
) {
    val lines = providerLegalLines(provider, texts)
    if (lines.isEmpty()) return
    SelectionContainer(modifier = modifier.fillMaxWidth().testTag(PROVIDER_LEGAL_NOTICE_TEST_TAG)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            lines.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

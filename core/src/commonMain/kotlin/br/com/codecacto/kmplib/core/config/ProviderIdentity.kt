package br.com.codecacto.kmplib.core.config

import br.com.codecacto.kmplib.core.format.formatCnpj
import br.com.codecacto.kmplib.core.format.isValidCnpj
import kotlin.concurrent.Volatile

/**
 * Endereço físico do fornecedor, como o Decreto 7.962/2013 (art. 2º, II) pede que apareça no app.
 *
 * Os campos ficam separados para cada superfície montar a linha do seu jeito; [formatted] é o formato
 * da fábrica: "Rua Pais Leme, 215, Conj. 1713 — Pinheiros — São Paulo/SP — CEP 05424-150".
 */
data class ProviderAddress(
    val street: String,
    val number: String,
    val complement: String? = null,
    val district: String,
    val city: String,
    val state: String,
    val postalCode: String,
) {
    /** "Rua X, 215, Conj. 1713 — Bairro — Cidade/UF — CEP 00000-000"; campo em branco sai da linha. */
    fun formatted(): String {
        val streetLine = listOf(street, number, complement.orEmpty())
            .map(String::trim)
            .filter(String::isNotEmpty)
            .joinToString(", ")
        val cityLine = listOf(city.trim(), state.trim()).filter(String::isNotEmpty).joinToString("/")
        val cep = formatPostalCode(postalCode)
        return listOf(streetLine, district.trim(), cityLine, cep?.let { "CEP $it" }.orEmpty())
            .filter(String::isNotEmpty)
            .joinToString(" — ")
    }

    private fun formatPostalCode(value: String): String? {
        val digits = value.filter(Char::isDigit)
        return when {
            value.isBlank() -> null
            digits.length == 8 -> "${digits.substring(0, 5)}-${digits.substring(5)}"
            else -> value.trim()
        }
    }
}

/**
 * Identificação do FORNECEDOR que o app mostra — Decreto 7.962/2013, art. 2º (comércio eletrônico):
 * nome empresarial, CNPJ, endereço físico e eletrônico. Lei 13.709 (LGPD), art. 41 §1º: a identidade
 * e o contato do encarregado (DPO) têm de estar à vista.
 *
 * A fonte é UMA: [ProviderIdentity.CodeCacto], o default de toda a kmplib (tela Sobre e
 * "Desenvolvido por"). App de cliente ou de parceria, que é vendido pela empresa DELE, troca no boot:
 *
 * ```kotlin
 * KmpLibProvider.configure(
 *     ProviderIdentity(
 *         brandName = "Clínica X",
 *         legalName = "CLINICA X SERVICOS MEDICOS LTDA",
 *         cnpj = "00.000.000/0001-00",
 *         address = ProviderAddress(…),
 *         contactEmail = "contato@clinicax.com.br",
 *     ),
 * )
 * ```
 *
 * @property brandName a marca que a pessoa conhece ("CodeCacto"). Em branco ou igual a [legalName],
 *   a linha mostra só o nome empresarial.
 * @property legalName razão social, como no CNPJ.
 * @property cnpj CNPJ (com ou sem pontuação; sai formatado em [formattedCnpj]).
 * @property address endereço físico.
 * @property contactEmail endereço eletrônico de contato.
 * @property dpoEmail e-mail do encarregado de dados (LGPD); `null` = linha omitida.
 */
data class ProviderIdentity(
    val brandName: String,
    val legalName: String,
    val cnpj: String,
    val address: ProviderAddress,
    val contactEmail: String,
    val dpoEmail: String? = null,
) {
    /** "61.927.873/0001-39" — formatado quando é um CNPJ válido; senão, como veio. */
    val formattedCnpj: String
        get() = if (isValidCnpj(cnpj)) formatCnpj(cnpj) else cnpj.trim()

    /** `true` quando a marca é outra coisa que o nome empresarial ("CodeCacto é uma marca de …"). */
    val hasDistinctBrand: Boolean
        get() = brandName.isNotBlank() && !brandName.trim().equals(legalName.trim(), ignoreCase = true)

    companion object {
        /**
         * A CodeCacto: marca de RAPHAEL AMORIM MARQUES TECNOLOGIA DA INFORMAÇÃO LTDA. Muda aqui, e só
         * aqui — todo app que bumpa a kmplib passa a mostrar o dado novo.
         */
        val CodeCacto: ProviderIdentity = ProviderIdentity(
            brandName = "CodeCacto",
            legalName = "RAPHAEL AMORIM MARQUES TECNOLOGIA DA INFORMAÇÃO LTDA",
            cnpj = "61.927.873/0001-39",
            address = ProviderAddress(
                street = "Rua Pais Leme",
                number = "215",
                complement = "Conj. 1713",
                district = "Pinheiros",
                city = "São Paulo",
                state = "SP",
                postalCode = "05424-150",
            ),
            contactEmail = "contato@codecacto.com.br",
            dpoEmail = "dpo@codecacto.com.br",
        )
    }
}

/**
 * Qual [ProviderIdentity] a lib mostra na tela Sobre (2.384.0). Default: [ProviderIdentity.CodeCacto].
 *
 * App de cliente/parceria chama [configure] no boot (antes da primeira tela). A tela "Desenvolvido por"
 * continua mostrando a CodeCacto — ela fala de quem DESENVOLVEU, não de quem vende.
 */
object KmpLibProvider {
    @Volatile
    private var identity: ProviderIdentity = ProviderIdentity.CodeCacto

    /** O fornecedor vigente. */
    val current: ProviderIdentity get() = identity

    /** Troca o fornecedor que o app mostra (app de cliente ou de parceria). */
    fun configure(provider: ProviderIdentity) {
        identity = provider
    }

    /** Volta ao default (CodeCacto). Para teste. */
    fun reset() {
        identity = ProviderIdentity.CodeCacto
    }
}

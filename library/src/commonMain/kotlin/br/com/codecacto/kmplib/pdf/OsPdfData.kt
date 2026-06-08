package br.com.codecacto.kmplib.pdf

import kotlinx.serialization.Serializable

/**
 * Modelo de entrada (genérico) para a geração de PDF de uma Ordem de Serviço /
 * orçamento / recibo. Não acoplado a nenhum app específico: qualquer consumidor
 * (MinhaOS, Meu Estacionamento, ReciboFacil, ...) monta este [OsPdfData] a partir
 * do seu próprio domínio e entrega ao [OsPdfGenerator].
 *
 * Convenções de dados:
 *  - Valores monetários são **strings decimais** (ex.: "320.00") — dinheiro nunca
 *    `Double`. O render formata para BRL ("R$ 320,00") internamente.
 *  - Datas/textos livres ficam a cargo do app (já formatados, p.ex. dd/MM/yyyy).
 *
 * Layout lógico produzido (idêntico em todas as plataformas):
 *  - Cabeçalho: logo (opcional) + nome da empresa + contato; número/título da OS + status.
 *  - Bloco do cliente.
 *  - Tabela de itens (descrição × qtd × preço unit. = subtotal).
 *  - Desconto (se houver) + Total destacado.
 *  - Observações (opcional).
 *  - Rodapé (texto livre, ex.: data de emissão).
 *  - Marca d'água discreta quando [watermark] = true (plano gratuito).
 *
 * @see OsPdfGenerator
 * @see generateOsPdfBytes
 * @see generateAndShareOsPdf
 */
@Serializable
data class OsPdfData(
    /** Dados da empresa/emitente exibidos no cabeçalho. */
    val company: OsPdfCompany,
    /** Número/identificador da OS (ex.: "7", "OS-2026-007"). */
    val number: String,
    /** Título do documento (default "Ordem de Serviço"). Permite "Orçamento", "Recibo", etc. */
    val title: String = "Ordem de Serviço",
    /** Rótulo de status já traduzido (ex.: "Em andamento", "Concluída"). Opcional. */
    val status: String? = null,
    /** Dados do cliente/destinatário. Opcional (alguns recibos podem não ter). */
    val client: OsPdfClient? = null,
    /** Descrição geral do serviço. Opcional. */
    val description: String? = null,
    /** Itens da OS. Pode ser vazio (ex.: recibo de valor único — use [total]). */
    val items: List<OsPdfItem> = emptyList(),
    /** Desconto em string decimal (ex.: "10.00"). Default "0.00". */
    val discount: String = "0.00",
    /** Total em string decimal (ex.: "320.00"). Fonte de verdade do total exibido. */
    val total: String,
    /** Observações livres exibidas antes do rodapé. Opcional. */
    val notes: String? = null,
    /** Texto do rodapé (ex.: "Emitido em 06/06/2026"). Opcional. */
    val footer: String? = null,
    /**
     * Quando `true`, imprime uma marca d'água discreta na página.
     * Regra de negócio do app: tipicamente `watermark = (plan == "free")`.
     */
    val watermark: Boolean = false,
    /** Texto da marca d'água (default genérico). Apps customizam, ex.: "Feito com MinhaOS". */
    val watermarkText: String = "Gerado com kmplib",
)

/** Empresa/emitente do documento. */
@Serializable
data class OsPdfCompany(
    val name: String,
    /** Telefone/contato exibido sob o nome. Opcional. */
    val phone: String? = null,
    /** E-mail/contato adicional. Opcional. */
    val email: String? = null,
    /** Endereço/linha de contato adicional. Opcional. */
    val address: String? = null,
    /**
     * Logo em PNG/JPEG (bytes). Opcional — quando ausente, o cabeçalho usa só o
     * nome. O app baixa os bytes do logo (Storage/cache) e os passa aqui.
     */
    val logoBytes: ByteArray? = null,
) {
    // equals/hashCode manuais por causa do ByteArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OsPdfCompany) return false
        return name == other.name &&
            phone == other.phone &&
            email == other.email &&
            address == other.address &&
            (logoBytes?.contentEquals(other.logoBytes) ?: (other.logoBytes == null))
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (phone?.hashCode() ?: 0)
        result = 31 * result + (email?.hashCode() ?: 0)
        result = 31 * result + (address?.hashCode() ?: 0)
        result = 31 * result + (logoBytes?.contentHashCode() ?: 0)
        return result
    }
}

/** Cliente/destinatário do documento. */
@Serializable
data class OsPdfClient(
    val name: String,
    val phone: String? = null,
    val address: String? = null,
)

/** Item da OS (linha da tabela). */
@Serializable
data class OsPdfItem(
    val description: String,
    /** Quantidade (ex.: 1, 2, ...). */
    val quantity: Int,
    /** Preço unitário em string decimal (ex.: "120.00"). */
    val unitPrice: String,
    /** Subtotal em string decimal (ex.: "120.00"). */
    val subtotal: String,
)

package br.com.codecacto.kmplib.pdf

/**
 * Dados de entrada para a geração do PDF do recibo (ReciboFacil — GAP-RF-M-03).
 *
 * Modelo dedicado ao **layout congelado** de `ReciboFacil/docs/design/recibo-layout-spec.md`
 * (A4 retrato, margens 20mm, blocos Emitente/Pagador, frase do corpo, valor em destaque,
 * local/data, assinatura(s) ancoradas, rodapé, marca d'água condicional). Diferente do
 * [OsPdfData] (genérico de OS/orçamento): este segue medidas e tipografia exatas da spec,
 * para **paridade visual** com o renderer da weblib (`@react-pdf/renderer`).
 *
 * Convenções:
 *  - Strings já formatadas pelo app (o renderer NÃO formata moeda nem data):
 *    `valorFormatado` (ex.: "R$ 120,00"), `valorPorExtenso` (saída de
 *    [br.com.codecacto.kmplib.core.format.valorPorExtenso]), `localData`
 *    (ex.: "São Paulo, 6 de junho de 2026.").
 *  - Imagens (logo, assinaturas) em PNG/JPEG bytes; assinaturas tipicamente PNG transparente
 *    do `SignaturePad`.
 *  - A marca d'água é controlada pelo parâmetro `watermark` de [generateReciboPdf]
 *    (regra de negócio do app: `watermark = (plan == "free")`).
 *
 * @see generateReciboPdf
 */
data class ReciboPdfData(
    /** Emitente (quem recebeu o valor) — coluna esquerda do cabeçalho de partes. */
    val emitente: ReciboParte,
    /** Pagador (quem pagou) — coluna direita. */
    val pagador: ReciboParte,
    /** Valor numérico já formatado em BRL (ex.: "R$ 120,00"). Usado inline e no destaque. */
    val valorFormatado: String,
    /** Valor por extenso (minúsculo), saída do contrato `valorPorExtenso`. */
    val valorPorExtenso: String,
    /** Descrição do que foi pago ("referente a ..."). */
    val descricao: String,
    /** Local e data já compostos por extenso (ex.: "São Paulo, 6 de junho de 2026."). */
    val localData: String,
    /** Identificação/número do recibo (ex.: "0001" ou id curto). Exibido como "Nº {num}". */
    val numeroRecibo: String,
    /** Data/hora de emissão para o rodapé (ex.: "06/06/2026 14:32"). */
    val dataHoraEmissao: String,
    /**
     * `true` se o emitente é pessoa jurídica (CNPJ) → frase usa "Recebemos";
     * `false`/null → "Recebi".
     */
    val emitentePessoaJuridica: Boolean = false,
    /** Logo do emitente (PNG/JPEG), opcional (Premium). Caixa 40×20mm, *contain*. */
    val logoBytes: ByteArray? = null,
    /** PNG transparente da assinatura do emitente (do `SignaturePad`), opcional. */
    val assinaturaEmitenteBytes: ByteArray? = null,
    /** PNG transparente da assinatura do pagador, opcional. Se presente → 2 colunas de assinatura. */
    val assinaturaPagadorBytes: ByteArray? = null,
    /**
     * Bytes da fonte Inter Regular (TTF/OTF), opcional. Quando ausente, o renderer usa
     * a fonte sans-serif padrão da plataforma (métricas aproximadas). Para paridade
     * exata com a weblib, o app deve fornecer a mesma Inter.
     */
    val interRegularBytes: ByteArray? = null,
    /** Bytes da fonte Inter Bold (TTF/OTF), opcional (ver [interRegularBytes]). */
    val interBoldBytes: ByteArray? = null,
    /** Texto da marca d'água (plano Free). Default conforme spec §9. */
    val watermarkText: String = "Gerado com ReciboFácil — versão gratuita",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ReciboPdfData) return false
        return emitente == other.emitente &&
            pagador == other.pagador &&
            valorFormatado == other.valorFormatado &&
            valorPorExtenso == other.valorPorExtenso &&
            descricao == other.descricao &&
            localData == other.localData &&
            numeroRecibo == other.numeroRecibo &&
            dataHoraEmissao == other.dataHoraEmissao &&
            emitentePessoaJuridica == other.emitentePessoaJuridica &&
            (logoBytes?.contentEquals(other.logoBytes) ?: (other.logoBytes == null)) &&
            (assinaturaEmitenteBytes?.contentEquals(other.assinaturaEmitenteBytes)
                ?: (other.assinaturaEmitenteBytes == null)) &&
            (assinaturaPagadorBytes?.contentEquals(other.assinaturaPagadorBytes)
                ?: (other.assinaturaPagadorBytes == null)) &&
            (interRegularBytes?.contentEquals(other.interRegularBytes)
                ?: (other.interRegularBytes == null)) &&
            (interBoldBytes?.contentEquals(other.interBoldBytes)
                ?: (other.interBoldBytes == null)) &&
            watermarkText == other.watermarkText
    }

    override fun hashCode(): Int {
        var result = emitente.hashCode()
        result = 31 * result + pagador.hashCode()
        result = 31 * result + valorFormatado.hashCode()
        result = 31 * result + valorPorExtenso.hashCode()
        result = 31 * result + descricao.hashCode()
        result = 31 * result + localData.hashCode()
        result = 31 * result + numeroRecibo.hashCode()
        result = 31 * result + dataHoraEmissao.hashCode()
        result = 31 * result + emitentePessoaJuridica.hashCode()
        result = 31 * result + (logoBytes?.contentHashCode() ?: 0)
        result = 31 * result + (assinaturaEmitenteBytes?.contentHashCode() ?: 0)
        result = 31 * result + (assinaturaPagadorBytes?.contentHashCode() ?: 0)
        result = 31 * result + (interRegularBytes?.contentHashCode() ?: 0)
        result = 31 * result + (interBoldBytes?.contentHashCode() ?: 0)
        result = 31 * result + watermarkText.hashCode()
        return result
    }
}

/** Parte do recibo (emitente ou pagador). */
data class ReciboParte(
    /** Nome / razão social (obrigatório). */
    val nome: String,
    /** Documento já mascarado (ex.: "CPF: 123.***.***-09"). Opcional para o pagador. */
    val documento: String? = null,
    /** Linha de contato (telefone e/ou e-mail) — só emitente. Opcional. */
    val contato: String? = null,
    /** Endereço — só emitente. Opcional. */
    val endereco: String? = null,
)

/**
 * Lançada quando a plataforma atual ainda não suporta o render do PDF do recibo
 * (ex.: iOS sem build em host macOS). Consumidores devem tratar com fallback.
 */
class ReciboPdfNotSupportedException(message: String) : Exception(message)

/**
 * Gera o PDF do recibo conforme o layout congelado da spec, retornando os bytes do arquivo.
 *
 * Implementação por plataforma (expect/actual):
 *  - **Android:** `android.graphics.pdf.PdfDocument` (este host valida).
 *  - **iOS:** `UIGraphicsPDFRenderer` — escrito, mas só compila/valida em host macOS.
 *
 * @param data dados do recibo.
 * @param watermark quando `true`, desenha a marca d'água diagonal (§9, plano Free).
 * @return bytes do PDF (A4, 1 página).
 * @throws ReciboPdfNotSupportedException se a plataforma não suportar render de PDF.
 */
expect fun generateReciboPdf(data: ReciboPdfData, watermark: Boolean): ByteArray

/**
 * Nome de arquivo default para o PDF do recibo (sanitizado, com extensão `.pdf`).
 */
fun defaultReciboPdfFileName(data: ReciboPdfData): String {
    val base = "recibo-${data.numeroRecibo}"
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifEmpty { "recibo" }
    return if (base.endsWith(".pdf", ignoreCase = true)) base else "$base.pdf"
}

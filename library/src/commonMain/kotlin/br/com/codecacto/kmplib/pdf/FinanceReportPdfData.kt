package br.com.codecacto.kmplib.pdf

import kotlinx.serialization.Serializable

/**
 * Modelo de entrada (genérico) para a geração de um **relatório financeiro** em PDF.
 * Não acoplado a nenhum app específico: qualquer consumidor (MinhaOS, ...) monta este
 * [FinanceReportPdfData] a partir do seu próprio domínio e entrega ao
 * [FinanceReportPdfGenerator].
 *
 * **PARIDADE COM A WEBLIB:** este shape é espelhado na weblib (mesmo arquivo lógico
 * `FinanceReportPdfData`). Mantenha nomes e semântica **idênticos** em ambos os lados.
 *
 * Convenções de dados (iguais às do [OsPdfData]):
 *  - Valores monetários são **strings decimais** (ex.: "320.00") — dinheiro nunca
 *    `Double`. O render formata para BRL ("R$ 320,00") internamente via [OsPdfFormat].
 *  - Datas/textos livres ficam a cargo do app (já formatados, p.ex. dd/MM/yyyy).
 *
 * Layout lógico produzido:
 *  - Cabeçalho: logo (opcional) + nome da empresa + telefone; à direita "Relatório
 *    financeiro", período e data de emissão.
 *  - Seção **Recebimentos** (R-A): tabela (nº, cliente, pago em, status, valor recebido)
 *    + total recebido destacado.
 *  - Seção **Contas a receber** (R-B): tabela (nº, cliente, status, total, recebido,
 *    saldo) + total a receber destacado.
 *
 * @see FinanceReportPdfGenerator
 * @see generateFinanceReportPdfBytes
 * @see generateAndShareFinanceReportPdf
 */
@Serializable
data class FinanceReportPdfData(
    /** Dados da empresa/emitente exibidos no cabeçalho. */
    val company: FinanceReportPdfCompany,
    /** Rótulo do período coberto pelo relatório (ex.: "Junho/2026", "01/06 a 30/06"). */
    val periodLabel: String,
    /** Data/hora de emissão do relatório, já formatada (ex.: "Emitido em 07/06/2026"). */
    val generatedAtLabel: String,
    /** Seção R-A — linhas de recebimentos do período. Pode ser vazia. */
    val receipts: List<FinanceReportReceipt> = emptyList(),
    /** Total recebido no período, string decimal (ex.: "1230.00"). */
    val totalReceived: String,
    /** Seção R-B — linhas de contas a receber. Pode ser vazia. */
    val receivables: List<FinanceReportReceivable> = emptyList(),
    /** Total a receber (saldo em aberto), string decimal (ex.: "450.00"). */
    val totalReceivable: String,
)

/** Empresa/emitente do relatório (subconjunto do [OsPdfCompany], mesmo mecanismo de logo). */
@Serializable
data class FinanceReportPdfCompany(
    val name: String,
    /** Telefone/contato exibido sob o nome. Opcional. */
    val phone: String? = null,
    /**
     * Logo em PNG/JPEG (bytes). Opcional — quando ausente, o cabeçalho usa só o nome.
     * Mesmo mecanismo do [OsPdfCompany.logoBytes]: o app baixa os bytes e os passa aqui.
     */
    val logoBytes: ByteArray? = null,
) {
    // equals/hashCode manuais por causa do ByteArray
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FinanceReportPdfCompany) return false
        return name == other.name &&
            phone == other.phone &&
            (logoBytes?.contentEquals(other.logoBytes) ?: (other.logoBytes == null))
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (phone?.hashCode() ?: 0)
        result = 31 * result + (logoBytes?.contentHashCode() ?: 0)
        return result
    }
}

/** Seção R-A — uma linha de recebimento. */
@Serializable
data class FinanceReportReceipt(
    /** Número/identificador do documento de origem (ex.: nº da OS). */
    val number: Int,
    /** Nome do cliente. */
    val clientName: String,
    /** Data do pagamento, já formatada (ex.: "05/06/2026"). */
    val paidAtLabel: String,
    /** Rótulo do status de pagamento, já traduzido (ex.: "Pago", "Pago (PIX)"). */
    val paymentStatusLabel: String,
    /** Valor recebido, string decimal (ex.: "320.00"). */
    val amountReceived: String,
)

/** Seção R-B — uma linha de conta a receber. */
@Serializable
data class FinanceReportReceivable(
    /** Número/identificador do documento de origem (ex.: nº da OS). */
    val number: Int,
    /** Nome do cliente. */
    val clientName: String,
    /** Rótulo do status, já traduzido (ex.: "Em aberto", "Parcial"). */
    val statusLabel: String,
    /** Valor total do documento, string decimal (ex.: "500.00"). */
    val total: String,
    /** Valor já recebido, string decimal (ex.: "200.00"). */
    val amountReceived: String,
    /** Saldo a receber, string decimal (ex.: "300.00"). */
    val balance: String,
)

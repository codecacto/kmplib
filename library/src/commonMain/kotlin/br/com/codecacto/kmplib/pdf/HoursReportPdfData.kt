package br.com.codecacto.kmplib.pdf

import kotlinx.serialization.Serializable

/**
 * Modelo de entrada (genérico) para a geração do **"Relatório de horas extras"** (dossiê de
 * cobrança) em PDF. Não acoplado a nenhum app específico: qualquer consumidor (MinhasHoras, ...)
 * monta este [HoursReportPdfData] a partir do seu próprio domínio e entrega ao
 * [HoursReportPdfGenerator].
 *
 * **PARIDADE COM A WEBLIB (GAP-MH-M-02):** este shape é o **contrato espelhado** com a weblib
 * (mesmo arquivo lógico `HoursReportPdfData`, padrão idêntico ao [WorkReportPdfData] /
 * [FinanceReportPdfData]). Mantenha nomes, tipos, ordem e semântica **idênticos** em ambos os
 * lados — o SHAPE está **congelado**.
 *
 * Convenções de dados (iguais às do [OsPdfData] / [FinanceReportPdfData] / [WorkReportPdfData]):
 *  - Datas/horas/valores ficam a cargo do app (já formatados, p.ex. "07/06/2026", "08:00",
 *    "R$ 120,00"). A lib só exibe o texto recebido — não calcula nem reformata.
 *  - Imagens em bytes (PNG/JPEG). O app baixa/decodifica e passa os bytes; a kmplib NÃO baixa
 *    URLs (mantém o gerador puro/sem rede). **Anexos são SEMPRE imagem**: foto direta do
 *    comprovante OU página de PDF já rasterizada pelo app via [renderPdfPagesToImages].
 *  - [watermark] segue a regra de plano do app (Free = `true`); o render desenha a marca d'água
 *    quando `true`. A lib NÃO decide o plano.
 *
 * Layout lógico produzido (espelhado na weblib):
 *  - Cabeçalho: logo (opcional, só Pro) + nome do emissor ([company]) + telefone; à direita
 *    "Relatório de horas extras", período ([periodLabel]) e data de emissão ([generatedAtLabel]).
 *  - Bloco da empresa cobrada ([companyLabel]).
 *  - Tabela de [entries]: data, entrada–saída, duração, valor (opcional) e status.
 *  - Bloco de **totais** com destaque do pendente ([totalPendingLabel]).
 *  - Seção **Comprovantes**: grade de [attachments] (imagem + legenda).
 *  - Marca d'água -45° quando [watermark] = `true`.
 *
 * @see HoursReportPdfGenerator
 * @see generateHoursReportPdfBytes
 * @see generateAndShareHoursReportPdf
 */
@Serializable
data class HoursReportPdfData(
    /** Emissor exibido no cabeçalho (quem cobra as horas). */
    val company: HoursReportPdfCompany,
    /** Rótulo do período coberto pelo relatório (ex.: "01/05 a 31/05/2026"). */
    val periodLabel: String,
    /** Empresa/contratante cobrada (ex.: "Empresa: Construtora X"). */
    val companyLabel: String,
    /** Data/hora de emissão, já formatada (ex.: "Emitido em 07/06/2026"). */
    val generatedAtLabel: String,
    /** Lançamentos de horas. Pode ser vazia (render mostra "Nenhum lançamento."). */
    val entries: List<HoursReportEntry> = emptyList(),
    /** Total de horas, já formatado (ex.: "32h30"). */
    val totalHoursLabel: String,
    /** Total pendente (a receber), já formatado. Quando presente, sai em **destaque**. */
    val totalPendingLabel: String? = null,
    /** Total já pago, já formatado. Opcional. */
    val totalPaidLabel: String? = null,
    /** Total contestado, já formatado. Opcional. */
    val totalContestedLabel: String? = null,
    /** Comprovantes anexados (SEMPRE imagem). Pode ser vazia. */
    val attachments: List<HoursReportAttachment> = emptyList(),
    /** `true` para desenhar a marca d'água (regra de plano Free = true, decidida no app). */
    val watermark: Boolean = false,
    /** Texto da marca d'água (ex.: nome do app/emissor). Usado quando [watermark] = true. */
    val watermarkText: String? = null,
)

/** Emissor do relatório (mesmo mecanismo de logo do [WorkReportPdfCompany]). */
@Serializable
data class HoursReportPdfCompany(
    val name: String,
    /** Telefone/contato exibido sob o nome. Opcional. */
    val phone: String? = null,
    /**
     * Logo em PNG/JPEG (bytes). Opcional — **só Pro** (o app decide). Quando ausente, o
     * cabeçalho usa só o nome. Mesmo mecanismo do [WorkReportPdfCompany.logoBytes].
     */
    val logoBytes: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HoursReportPdfCompany) return false
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

/** Um lançamento de horas (uma linha da tabela). Todos os campos já formatados pelo app. */
@Serializable
data class HoursReportEntry(
    /** Data do lançamento, já formatada (ex.: "07/06/2026"). */
    val date: String,
    /** Horário de entrada, já formatado (ex.: "08:00"). */
    val start: String,
    /** Horário de saída, já formatado (ex.: "12:30"). */
    val end: String,
    /** Duração do lançamento, já formatada (ex.: "4h30"). */
    val durationLabel: String,
    /** Valor do lançamento, já formatado (ex.: "R$ 90,00"). Opcional. */
    val valueLabel: String? = null,
    /** Status do lançamento, já traduzido (ex.: "Pendente", "Pago", "Contestado"). */
    val statusLabel: String,
)

/**
 * Um comprovante anexado — **sempre imagem**. Pode ser uma foto direta do comprovante ou uma
 * página de PDF já rasterizada pelo app via [renderPdfPagesToImages].
 */
@Serializable
data class HoursReportAttachment(
    /**
     * Bytes da imagem (PNG/JPEG), já baixada/decodificada e idealmente comprimida pelo app.
     * A kmplib não baixa URLs nem rasteriza PDF aqui — use [renderPdfPagesToImages] antes.
     */
    val imageBytes: ByteArray,
    /** Legenda opcional do comprovante (ex.: descrição/origem). */
    val caption: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HoursReportAttachment) return false
        return imageBytes.contentEquals(other.imageBytes) && caption == other.caption
    }

    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + (caption?.hashCode() ?: 0)
        return result
    }
}

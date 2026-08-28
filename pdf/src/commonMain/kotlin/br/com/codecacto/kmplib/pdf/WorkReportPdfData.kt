package br.com.codecacto.kmplib.pdf

import kotlinx.serialization.Serializable

/**
 * Modelo de entrada (genérico) para a geração de um **relatório de acompanhamento de obra**
 * em PDF. Não acoplado a nenhum app específico: qualquer consumidor (MinhaObra, ...) monta
 * este [WorkReportPdfData] a partir do seu próprio domínio e entrega ao
 * [WorkReportPdfGenerator].
 *
 * **PARIDADE COM A WEBLIB (GAP-MO-W-09):** este shape é o **contrato espelhado** com a weblib
 * (mesmo arquivo lógico `WorkReportPdfData`, padrão idêntico ao [FinanceReportPdfData]).
 * Mantenha nomes, tipos e semântica **idênticos** em ambos os lados. **Contrato C-5 da Onda 4
 * do MinhaObra** — fechado pelo tech-lead + CTO (shape final conciliado).
 *
 * Convenções de dados (iguais às do [OsPdfData]/[FinanceReportPdfData]):
 *  - Datas/textos livres ficam a cargo do app (já formatados, p.ex. "07/06/2026 14:30").
 *    O carimbo data/hora da foto (DP-7, serverTimestamp) é resolvido **no app** e chega aqui
 *    já como string em [WorkReportPhoto.takenAtLabel].
 *  - Progresso é um inteiro **0..100** ([overallProgress], [WorkReportPdfStage.progress]); o
 *    render exibe como barra + "%".
 *  - Imagens em bytes (PNG/JPEG). O app baixa/decodifica e passa os bytes; a kmplib NÃO
 *    baixa URLs (mantém o gerador puro/sem rede). Recomenda-se comprimir antes via
 *    [br.com.codecacto.kmplib.platform.ImageCompressor].
 *  - [watermark] segue a regra de plano do app (Free = `true`); o render desenha a marca
 *    d'água quando `true`. A lib NÃO decide o plano.
 *
 * Layout lógico produzido (espelhado na weblib):
 *  - Cabeçalho: logo (opcional, só Pro) + nome da empresa/gestor + telefone; à direita
 *    "Acompanhamento de obra", período e data de emissão.
 *  - Bloco da obra: nome, cliente, endereço, progresso geral (barra + %).
 *  - Seção **Etapas**: lista (nome, status, % de progresso com barra, observação opcional).
 *  - Seção **Registro fotográfico**: grade de fotos selecionadas (imagem + etapa + legenda +
 *    data/hora do carimbo).
 *  - Seção **Diário de obra**: blocos (data, clima, equipe, autor, anotações).
 *  - Marca d'água -45° quando [watermark] = `true`.
 *
 * @see WorkReportPdfGenerator
 * @see generateWorkReportPdfBytes
 * @see generateAndShareWorkReportPdf
 */
@Serializable
data class WorkReportPdfData(
    /** Empresa/gestor exibido no cabeçalho. */
    val company: WorkReportPdfCompany,
    /** Nome da obra (ex.: "Residência Família Silva"). */
    val workName: String,
    /** Nome do cliente/contratante da obra. Opcional. */
    val clientName: String? = null,
    /** Endereço/local da obra. Opcional. */
    val address: String? = null,
    /** Rótulo do período coberto pelo relatório (ex.: "01/05 a 31/05/2026"). Opcional. */
    val periodLabel: String? = null,
    /** Data/hora de emissão, já formatada (ex.: "Emitido em 07/06/2026"). */
    val generatedAtLabel: String,
    /** Progresso geral da obra, inteiro 0..100 (render clampa). */
    val overallProgress: Int = 0,
    /** Etapas da obra com status e progresso. Pode ser vazia. */
    val stages: List<WorkReportPdfStage> = emptyList(),
    /** Fotos selecionadas para o relatório. Pode ser vazia. */
    val photos: List<WorkReportPhoto> = emptyList(),
    /** Trechos do diário de obra. Pode ser vazio. */
    val diaryEntries: List<WorkReportPdfDiaryEntry> = emptyList(),
    /** `true` para desenhar a marca d'água (regra de plano Free = true, decidida no app). */
    val watermark: Boolean = false,
    /** Texto da marca d'água (ex.: nome do app/gestor). Usado quando [watermark] = true. */
    val watermarkText: String? = null,
)

/** Empresa/gestor do relatório (mesmo mecanismo de logo do [FinanceReportPdfCompany]). */
@Serializable
data class WorkReportPdfCompany(
    val name: String,
    /** Telefone/contato exibido sob o nome. Opcional. */
    val phone: String? = null,
    /**
     * Logo em PNG/JPEG (bytes). Opcional — **só Pro** (o app decide). Quando ausente, o
     * cabeçalho usa só o nome. Mesmo mecanismo do [FinanceReportPdfCompany.logoBytes].
     */
    val logoBytes: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WorkReportPdfCompany) return false
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

/** Uma etapa da obra. */
@Serializable
data class WorkReportPdfStage(
    /** Nome da etapa (ex.: "Fundação", "Alvenaria"). */
    val name: String,
    /** Rótulo de status, já traduzido (ex.: "Em andamento", "Concluída", "Não iniciada"). */
    val statusLabel: String,
    /** Progresso da etapa, inteiro 0..100 (render clampa e exibe "%"). */
    val progress: Int = 0,
    /** Observação/nota da etapa, texto livre. Opcional. */
    val note: String? = null,
)

/** Uma foto do registro fotográfico. */
@Serializable
data class WorkReportPhoto(
    /**
     * Bytes da imagem (PNG/JPEG), já baixada/decodificada e idealmente comprimida pelo app
     * via [br.com.codecacto.kmplib.platform.ImageCompressor]. A kmplib não baixa URLs.
     */
    val imageBytes: ByteArray,
    /** Nome da etapa associada à foto, já traduzido (ex.: "Fundação"). Opcional. */
    val stageName: String? = null,
    /** Legenda opcional da foto (ex.: etapa/descrição). */
    val caption: String? = null,
    /**
     * Carimbo de data/hora da foto, já formatado pelo app (ex.: "07/06/2026 14:30").
     * Origem do timestamp = serverTimestamp (DP-7), resolvido no app — a lib só exibe o texto.
     */
    val takenAtLabel: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WorkReportPhoto) return false
        return imageBytes.contentEquals(other.imageBytes) &&
            stageName == other.stageName &&
            caption == other.caption &&
            takenAtLabel == other.takenAtLabel
    }

    override fun hashCode(): Int {
        var result = imageBytes.contentHashCode()
        result = 31 * result + (stageName?.hashCode() ?: 0)
        result = 31 * result + (caption?.hashCode() ?: 0)
        result = 31 * result + (takenAtLabel?.hashCode() ?: 0)
        return result
    }
}

/**
 * Um trecho do diário de obra.
 *
 * Decisão do CTO (C-5): este modelo é o **SUPERCONJUNTO** do contrato — preserva
 * [weatherLabel]/[crewLabel] já renderizados no Android além dos campos canônicos do C-5
 * ([text], [author]). Consumidores que não usarem clima/equipe simplesmente não os passam.
 */
@Serializable
data class WorkReportPdfDiaryEntry(
    /** Data do registro, já formatada (ex.: "07/06/2026"). */
    val dateLabel: String,
    /** Clima do dia, já traduzido (ex.: "Ensolarado", "Chuva"). Opcional (superconjunto). */
    val weatherLabel: String? = null,
    /** Equipe/efetivo presente, texto livre (ex.: "5 pedreiros, 2 serventes"). Opcional (superconjunto). */
    val crewLabel: String? = null,
    /** Autor do registro do diário (quem anotou). Opcional. */
    val author: String? = null,
    /** Anotações/observações do dia. */
    val text: String,
)

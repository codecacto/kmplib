package br.com.codecacto.kmplib.pdf

import kotlinx.serialization.Serializable

/**
 * Modelo de entrada (genérico) para a geração de um **PDF de vistoria/checklist veicular** com
 * **imagens embarcadas de verdade** (foto-prova por item + assinaturas), não textual.
 *
 * Distinto dos demais geradores do módulo `pdf`:
 *  - [DocumentPdfData] é multi-seção genérico mas **não embarca imagem por item/assinatura** (era o
 *    atalho que o ChecklistVeicular usava, gerando PDF textual — ver GAP-CV-M-03);
 *  - [WorkReportPdfData] embarca fotos numa grade separada (registro fotográfico), sem status
 *    OK/Não-OK/N-A por item nem assinaturas de partes;
 *  - [OsPdfData]/[FinanceReportPdfData] são financeiros.
 *
 * Este contrato modela a **vistoria/laudo**: cabeçalho do veículo (snapshot), itens **agrupados por
 * seção** com status/observação e **foto-prova embarcada por item**, e — no modo **Laudo** —
 * identificação do terceiro + **assinaturas embarcadas** (responsável + cliente).
 *
 * Convenções (iguais às do [WorkReportPdfData]):
 *  - Datas/labels já formatados pelo app (a lib só exibe).
 *  - Imagens em bytes (PNG/JPEG). O app decodifica/comprime (via
 *    [br.com.codecacto.kmplib.platform.ImageCompressor] / `compressToPair`) e passa os bytes; a
 *    kmplib NÃO baixa URLs (gerador puro/sem rede).
 *  - Assinaturas em PNG transparente, tipicamente vindas de
 *    [br.com.codecacto.kmplib.signature.SignaturePadState.toPngBytes].
 *  - [watermark] segue a regra de plano do app (Free = `true`); a lib NÃO decide o plano.
 *
 * @see InspectionPdfGenerator
 * @see generateInspectionPdfBytes
 * @see generateAndShareInspectionPdf
 */
@Serializable
data class InspectionPdfData(
    /** Empresa/vistoriador exibido no cabeçalho. */
    val company: InspectionPdfCompany,
    /** Cabeçalho do veículo (snapshot no momento da vistoria). */
    val vehicle: InspectionPdfVehicle,
    /** Título do documento (ex.: "Vistoria", "Laudo de Vistoria"). */
    val title: String = "Vistoria",
    /** Contexto/tipo da vistoria já traduzido (ex.: "Entrada", "Locação"). Opcional. */
    val contextLabel: String? = null,
    /** Data/hora de emissão, já formatada (ex.: "07/07/2026 14:30"). */
    val generatedAtLabel: String,
    /** Itens agrupados por seção. Pode ser vazia. */
    val sections: List<InspectionPdfSection> = emptyList(),
    /** Identificação do terceiro (modo Laudo). `null` = vistoria simples sem terceiro. */
    val thirdParty: InspectionPdfThirdParty? = null,
    /** Assinaturas embarcadas (modo Laudo). Pode ser vazia. */
    val signatures: List<InspectionPdfSignature> = emptyList(),
    /** `true` para desenhar a marca d'água (regra de plano Free = true, decidida no app). */
    val watermark: Boolean = false,
    /** Texto da marca d'água. Usado quando [watermark] = true. */
    val watermarkText: String? = null,
)

/** Empresa/vistoriador do documento (mesmo mecanismo de logo do [WorkReportPdfCompany]). */
@Serializable
data class InspectionPdfCompany(
    val name: String,
    /** Telefone/contato exibido sob o nome. Opcional. */
    val phone: String? = null,
    /** Logo em PNG/JPEG (bytes). Opcional — quando ausente, o cabeçalho usa só o nome. */
    val logoBytes: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InspectionPdfCompany) return false
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

/** Snapshot do veículo vistoriado (apelido/placa/tipo). */
@Serializable
data class InspectionPdfVehicle(
    /** Apelido/identificação do veículo (ex.: "Gol da frota"). */
    val nickname: String,
    /** Placa **já formatada para exibição** (ex.: "ABC1D23" ou "ABC-1234"). */
    val plate: String,
    /** Tipo do veículo, já traduzido (ex.: "Carro", "Moto"). Opcional. */
    val typeLabel: String? = null,
)

/** Uma seção do checklist (grupo de itens). */
@Serializable
data class InspectionPdfSection(
    /** Título da seção (ex.: "Lataria", "Pneus", "Interior"). */
    val title: String,
    /** Itens da seção. Pode ser vazia (render exibe "Sem itens."). */
    val items: List<InspectionPdfItem> = emptyList(),
)

/** Um item do checklist com status, observação e foto-prova embarcada. */
@Serializable
data class InspectionPdfItem(
    /** Texto do item vistoriado (ex.: "Farol dianteiro esquerdo"). */
    val text: String,
    /** Rótulo do status, já traduzido (ex.: "OK", "Não-OK", "N-A"). */
    val statusLabel: String,
    /**
     * Cor ARGB do status (ex.: verde OK / vermelho Não-OK / âmbar N-A). Opcional — quando ausente,
     * o render usa a cor de texto padrão. O app decide as cores (tokens do seu tema).
     */
    val statusColorArgb: Int? = null,
    /** Observação/nota do item, texto livre. Opcional. */
    val observation: String? = null,
    /**
     * Bytes das fotos-prova (PNG/JPEG), já decodificadas e idealmente comprimidas pelo app via
     * [br.com.codecacto.kmplib.platform.ImageCompressor]. **Embarcadas no PDF** como miniaturas.
     */
    val photos: List<ByteArray> = emptyList(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InspectionPdfItem) return false
        return text == other.text &&
            statusLabel == other.statusLabel &&
            statusColorArgb == other.statusColorArgb &&
            observation == other.observation &&
            photos.size == other.photos.size &&
            photos.indices.all { photos[it].contentEquals(other.photos[it]) }
    }

    override fun hashCode(): Int {
        var result = text.hashCode()
        result = 31 * result + statusLabel.hashCode()
        result = 31 * result + (statusColorArgb ?: 0)
        result = 31 * result + (observation?.hashCode() ?: 0)
        result = 31 * result + photos.sumOf { it.contentHashCode() }
        return result
    }
}

/** Identificação do terceiro (modo Laudo). PII sensível — o app deve ter consentimento (ConsentGate). */
@Serializable
data class InspectionPdfThirdParty(
    /** Nome do responsável pela vistoria (obrigatório no laudo). */
    val responsibleName: String,
    /** Documento (CPF/RG) do responsável. Opcional. */
    val responsibleDocument: String? = null,
    /** Nome do cliente/contratante. Opcional. */
    val clientName: String? = null,
    /** Documento do cliente. Opcional. */
    val clientDocument: String? = null,
)

/** Uma assinatura embarcada no PDF (imagem + rótulo + nome). */
@Serializable
data class InspectionPdfSignature(
    /** Papel de quem assina (ex.: "Responsável", "Cliente"). */
    val label: String,
    /** Bytes do PNG da assinatura (transparente), tipicamente de `SignaturePadState.toPngBytes()`. */
    val pngBytes: ByteArray,
    /** Nome exibido sob a linha da assinatura. Opcional. */
    val name: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InspectionPdfSignature) return false
        return label == other.label &&
            name == other.name &&
            pngBytes.contentEquals(other.pngBytes)
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + (name?.hashCode() ?: 0)
        result = 31 * result + pngBytes.contentHashCode()
        return result
    }
}

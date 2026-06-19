package br.com.codecacto.kmplib.pdf

import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.getShareHandler

/**
 * Gerador de **PDF de documento estruturado** (multi-seção), multiplataforma.
 *
 * Segue o MESMO padrão de API dos demais geradores da lib
 * ([OsPdfGenerator]/[FinanceReportPdfGenerator]/[TableReportPdfGenerator]):
 *  - **Android:** render nativo via `android.graphics.pdf.PdfDocument` (cabeçalho com
 *    logo/empresa + título/subtítulo + bloco de metadados; seções de chave→valor, tabelas
 *    com zebra, cards, parágrafos e total destacado; paginação automática; marca d'água
 *    -45° quando `watermark=true`).
 *  - **iOS:** render nativo via `UIGraphicsPDFRenderer`/Core Graphics — **funcional**, com o
 *    MESMO layout lógico do Android (não é placeholder). Valida em host macOS, como todo o
 *    alvo iOS da lib.
 *
 * Uso típico (em ViewModel/repository, fora da UI):
 * ```kotlin
 * val data = DocumentPdfData(
 *     company = DocumentPdfCompany(name = "Minha Agência", logoBytes = logo),
 *     title = "Contrato de Parceria",
 *     subtitle = "Recorrente",
 *     headerInfo = listOf(
 *         DocumentInfoRow("Contratada", cliente.nome),
 *         DocumentInfoRow("Documento", cliente.cpf),
 *     ),
 *     sections = listOf(
 *         DocumentSection.Table(
 *             title = "Termos",
 *             columns = listOf(DocumentColumn("Item", weight = 2f), DocumentColumn("Valor", DocumentAlign.END)),
 *             rows = listOf(DocumentRow(listOf("Stories / mês", "10")), DocumentRow(listOf("Reels / mês", "3"))),
 *         ),
 *         DocumentSection.Total(label = "Valor mensal", value = "R$ 1.500,00"),
 *     ),
 *     footer = "Emitido em 14/06/2026",
 * )
 * generateAndShareDocumentPdf(data)         // gera + abre o share sheet
 * // ou, se precisar dos bytes (anexar/upload):
 * val bytes = generateDocumentPdfBytes(data)
 * ```
 */
interface DocumentPdfGenerator {
    /** Renderiza [data] em um documento PDF e retorna os bytes do arquivo. */
    fun generate(data: DocumentPdfData): ByteArray
}

/** Fábrica do [DocumentPdfGenerator] da plataforma atual. */
expect fun createDocumentPdfGenerator(): DocumentPdfGenerator

/**
 * Gera os bytes do PDF de documento a partir de [data], usando o gerador da plataforma.
 * Atalho para `createDocumentPdfGenerator().generate(data)`.
 */
fun generateDocumentPdfBytes(data: DocumentPdfData): ByteArray =
    createDocumentPdfGenerator().generate(data)

/**
 * Gera o PDF de documento e abre o share sheet do sistema (salvar/enviar/imprimir) via
 * [ShareHandler], reusando a infraestrutura de compartilhamento já existente na lib.
 *
 * @return os bytes do PDF gerado (úteis para reuso, p.ex. upload).
 */
fun generateAndShareDocumentPdf(
    data: DocumentPdfData,
    shareHandler: ShareHandler = getShareHandler(),
    fileName: String = defaultDocumentPdfFileName(data.title),
    shareTitle: String = "Compartilhar documento",
): ByteArray {
    val bytes = generateDocumentPdfBytes(data)
    shareHandler.shareFile(
        fileBytes = bytes,
        fileName = fileName,
        mimeType = "application/pdf",
        title = shareTitle,
    )
    return bytes
}

/**
 * Nome de arquivo default para o PDF de documento, derivado do [title].
 * Sanitiza caracteres inválidos e garante a extensão `.pdf`.
 */
fun defaultDocumentPdfFileName(title: String): String {
    val safe = title
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifEmpty { "documento" }
    return if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf"
}

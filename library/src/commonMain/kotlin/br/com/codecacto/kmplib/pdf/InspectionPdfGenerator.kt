package br.com.codecacto.kmplib.pdf

import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.getShareHandler

/**
 * Gerador de PDF de **vistoria/checklist veicular** com **imagens embarcadas** (genérico,
 * multiplataforma). Substitui o PDF textual que o ChecklistVeicular montava sobre o
 * [DocumentPdfGenerator] (GAP-CV-M-03) — agora a foto-prova por item e as assinaturas do laudo
 * saem **embarcadas de verdade** no documento.
 *
 * Segue o MESMO padrão de API do [WorkReportPdfGenerator]/[FinanceReportPdfGenerator]:
 *  - **Android:** render nativo via `android.graphics.pdf.PdfDocument` (embarca imagens via
 *    `Canvas.drawBitmap`, mesma técnica do [WorkReportPdfGenerator]).
 *  - **iOS:** **stub** que lança [OsPdfNotSupportedException] — débito conhecido dos PDFs iOS da
 *    kmplib (APIs de desenho de texto UIKit indisponíveis no Kotlin/Native 2.x; nenhum app builda
 *    iOS no servidor). Ver `CLAUDE.md` da kmplib e a memória `kmplib-ios-pdf-stub-debt`.
 *
 * Uso típico (em ViewModel/repository, fora da UI):
 * ```kotlin
 * val data = InspectionPdfData(
 *     company = InspectionPdfCompany(name = "Vistoria X"),
 *     vehicle = InspectionPdfVehicle(nickname = "Gol da frota", plate = "ABC1D23", typeLabel = "Carro"),
 *     title = "Laudo de Vistoria",
 *     generatedAtLabel = formatDateTimeBrFromMillis(currentTimeMillis()),
 *     sections = ...,                     // itens com status + foto-prova (bytes)
 *     thirdParty = InspectionPdfThirdParty(responsibleName = "..."),   // modo Laudo
 *     signatures = listOf(
 *         InspectionPdfSignature("Responsável", responsiblePng, name = "..."),
 *         InspectionPdfSignature("Cliente", clientPng),
 *     ),
 *     watermark = !isPro,
 * )
 * generateAndShareInspectionPdf(data)      // gera + abre o share sheet
 * // ou, se precisar dos bytes (anexar/upload):
 * val bytes = generateInspectionPdfBytes(data)
 * ```
 */
interface InspectionPdfGenerator {
    /**
     * Renderiza [data] em um documento PDF (com imagens embarcadas) e retorna os bytes do arquivo.
     *
     * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir (stub).
     */
    fun generate(data: InspectionPdfData): ByteArray
}

/** Fábrica do [InspectionPdfGenerator] da plataforma atual. */
expect fun createInspectionPdfGenerator(): InspectionPdfGenerator

/**
 * Gera os bytes do PDF de vistoria a partir de [data]. Atalho para
 * `createInspectionPdfGenerator().generate(data)`.
 *
 * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir (stub).
 */
fun generateInspectionPdfBytes(data: InspectionPdfData): ByteArray =
    createInspectionPdfGenerator().generate(data)

/**
 * Gera o PDF de vistoria e abre o share sheet do sistema (salvar/enviar/imprimir) via [ShareHandler].
 *
 * @return os bytes do PDF gerado (úteis para reuso, p.ex. upload).
 * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir (stub).
 */
fun generateAndShareInspectionPdf(
    data: InspectionPdfData,
    shareHandler: ShareHandler = getShareHandler(),
    fileName: String = defaultInspectionPdfFileName(data.vehicle),
    shareTitle: String = "Compartilhar vistoria",
): ByteArray {
    val bytes = generateInspectionPdfBytes(data)
    shareHandler.shareFile(
        fileBytes = bytes,
        fileName = fileName,
        mimeType = "application/pdf",
        title = shareTitle,
    )
    return bytes
}

/**
 * Nome de arquivo default para o PDF de vistoria, derivado do apelido/placa do veículo.
 * Sanitiza caracteres inválidos e garante a extensão `.pdf`.
 */
fun defaultInspectionPdfFileName(vehicle: InspectionPdfVehicle): String {
    val label = listOf(vehicle.nickname, vehicle.plate)
        .filter { it.isNotBlank() }
        .joinToString("-")
    val rawBase = if (label.isEmpty()) "vistoria" else "vistoria-$label"
    val safe = rawBase
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifEmpty { "vistoria" }
    return if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf"
}

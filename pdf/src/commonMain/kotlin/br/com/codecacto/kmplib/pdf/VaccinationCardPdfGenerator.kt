package br.com.codecacto.kmplib.pdf

import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.getShareHandler

/**
 * Gerador de PDF da **carteira/cronograma de vacinação** (genérico, multiplataforma).
 *
 * Segue o MESMO padrão de API dos demais geradores do módulo `pdf`
 * ([WorkReportPdfGenerator]/[TableReportPdfGenerator]/[OsPdfGenerator]):
 *  - **Android:** render nativo via `android.graphics.pdf.PdfDocument`.
 *  - **iOS:** render nativo via `UIGraphicsPDFRenderer` (UIKit), com **paridade de layout** com o
 *    Android. **Diferente** dos templates `OsPdf`/`WorkReport`/`TableReport`/`Finance`/`Hours`, que
 *    ainda têm iOS como placeholder — este template tem o `actual` iOS escrito (modelado no
 *    `ReciboPdf.ios.kt`, que já usa `UIGraphicsPDFRenderer`). **Pendência:** a validação visual no
 *    iOS só é possível em host macOS (o ambiente de build atual é Windows e a kmplib não publica
 *    artefatos iOS no mavenLocal aqui). O código foi escrito por construção; ver o handoff.
 *
 * Uso típico (em ViewModel/repository, fora da UI):
 * ```kotlin
 * val data = VaccinationCardPdfData(
 *     holder = VaccinationHolder(name = "João", lines = listOf(
 *         VaccinationHolderLine("Nascimento", "12/03/2022"),
 *     )),
 *     columns = listOf(
 *         VaccinationColumn("Vacina", weight = 2f),
 *         VaccinationColumn("Dose"),
 *         VaccinationColumn("Aplicada", VaccinationAlign.CENTER),
 *         VaccinationColumn("Status", VaccinationAlign.END),
 *     ),
 *     items = doses.map { VaccinationItem(cells = listOf(it.vacina, it.dose, it.data, it.status)) },
 *     generatedAtLabel = "Gerado em 14/06/2026",
 *     watermark = isFreePlan,
 * )
 * generateAndShareVaccinationCardPdf(data)        // gera + abre o share sheet
 * // ou, se precisar dos bytes (anexar/upload):
 * val bytes = generateVaccinationCardPdfBytes(data)
 * ```
 *
 * Para o **comprovante do Rebanho**, preencha [VaccinationCardPdfData.notice] com o aviso de
 * documento não-oficial — o mesmo template/render é reutilizado (sem hardcode de domínio).
 */
interface VaccinationCardPdfGenerator {
    /**
     * Renderiza [data] em um documento PDF e retorna os bytes do arquivo.
     *
     * @throws OsPdfNotSupportedException se a plataforma atual não suportar render de PDF.
     */
    fun generate(data: VaccinationCardPdfData): ByteArray
}

/** Fábrica do [VaccinationCardPdfGenerator] da plataforma atual. */
expect fun createVaccinationCardPdfGenerator(): VaccinationCardPdfGenerator

/**
 * Gera os bytes do PDF da carteira a partir de [data], usando o gerador da plataforma. Atalho para
 * `createVaccinationCardPdfGenerator().generate(data)`.
 */
fun generateVaccinationCardPdfBytes(data: VaccinationCardPdfData): ByteArray =
    createVaccinationCardPdfGenerator().generate(data)

/**
 * Gera o PDF da carteira e abre o share sheet do sistema (salvar/enviar/imprimir) via
 * [ShareHandler].
 *
 * @return os bytes do PDF gerado (úteis para reuso, p.ex. upload).
 */
fun generateAndShareVaccinationCardPdf(
    data: VaccinationCardPdfData,
    shareHandler: ShareHandler = getShareHandler(),
    fileName: String = defaultVaccinationCardPdfFileName(data.holder.name),
    shareTitle: String = "Compartilhar carteira de vacinação",
): ByteArray {
    val bytes = generateVaccinationCardPdfBytes(data)
    shareHandler.shareFile(
        fileBytes = bytes,
        fileName = fileName,
        mimeType = "application/pdf",
        title = shareTitle,
    )
    return bytes
}

/**
 * Nome de arquivo default para o PDF da carteira, derivado do nome do titular [holderName].
 * Sanitiza caracteres inválidos e garante a extensão `.pdf`.
 */
fun defaultVaccinationCardPdfFileName(holderName: String): String {
    val rawBase = "carteira-vacinacao-$holderName"
    val safe = rawBase
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifEmpty { "carteira-vacinacao" }
    return if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf"
}

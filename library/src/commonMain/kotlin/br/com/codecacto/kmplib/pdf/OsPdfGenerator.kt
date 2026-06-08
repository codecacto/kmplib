package br.com.codecacto.kmplib.pdf

import br.com.codecacto.kmplib.platform.ShareHandler
import br.com.codecacto.kmplib.platform.getShareHandler

/**
 * Gerador de PDF de Ordem de Serviço (genérico, multiplataforma).
 *
 * Implementação por plataforma (expect/actual):
 *  - **Android:** render nativo via `android.graphics.pdf.PdfDocument`.
 *  - **iOS:** placeholder (TODO `UIGraphicsPDFRenderer` em host macOS) — ver
 *    [createOsPdfGenerator] e o backlog da kmplib.
 *
 * Uso típico (em ViewModel/repository, fora da UI):
 * ```kotlin
 * val data = OsPdfData(company = ..., number = "7", items = ..., total = "320.00",
 *                      watermark = plan == "free", watermarkText = "Feito com MinhaOS")
 * generateAndShareOsPdf(data)               // gera + abre o share sheet
 * // ou, se precisar dos bytes (anexar, fazer upload, etc.):
 * val bytes = generateOsPdfBytes(data)
 * ```
 */
interface OsPdfGenerator {
    /**
     * Renderiza [data] em um documento PDF e retorna os bytes do arquivo.
     *
     * @throws OsPdfNotSupportedException se a plataforma atual ainda não suporta
     *   render de PDF (caso do iOS sem host macOS).
     */
    fun generate(data: OsPdfData): ByteArray
}

/**
 * Lançada quando a plataforma atual não suporta geração de PDF (ex.: iOS antes da
 * implementação nativa em host macOS). Consumidores devem tratar com fallback
 * (ex.: compartilhar texto via [ShareHandler]).
 */
class OsPdfNotSupportedException(message: String) : Exception(message)

/**
 * Fábrica do [OsPdfGenerator] da plataforma atual.
 */
expect fun createOsPdfGenerator(): OsPdfGenerator

/**
 * Gera os bytes do PDF da OS a partir de [data], usando o gerador da plataforma.
 * Atalho para `createOsPdfGenerator().generate(data)`.
 *
 * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir.
 */
fun generateOsPdfBytes(data: OsPdfData): ByteArray =
    createOsPdfGenerator().generate(data)

/**
 * Gera o PDF da OS e abre o share sheet do sistema (salvar/enviar/imprimir) via
 * [ShareHandler], reusando a infraestrutura de compartilhamento já existente na lib.
 *
 * @param data dados da OS.
 * @param fileName nome do arquivo gerado (default derivado do número da OS).
 * @param shareTitle título exibido no chooser de compartilhamento.
 * @param shareHandler handler de share (default: o da plataforma).
 * @return os bytes do PDF gerado (úteis para reuso, p.ex. upload).
 * @throws OsPdfNotSupportedException no iOS enquanto o render nativo não existir.
 */
fun generateAndShareOsPdf(
    data: OsPdfData,
    fileName: String = defaultOsPdfFileName(data),
    shareTitle: String = "Compartilhar OS",
    shareHandler: ShareHandler = getShareHandler(),
): ByteArray {
    val bytes = generateOsPdfBytes(data)
    shareHandler.shareFile(
        fileBytes = bytes,
        fileName = fileName,
        mimeType = "application/pdf",
        title = shareTitle,
    )
    return bytes
}

/**
 * Nome de arquivo default para o PDF da OS, derivado de número/título.
 * Sanitiza caracteres inválidos e garante a extensão `.pdf`.
 */
fun defaultOsPdfFileName(data: OsPdfData): String {
    val rawBase = "${data.title}-${data.number}"
    val safe = rawBase
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifEmpty { "documento" }
    return if (safe.endsWith(".pdf", ignoreCase = true)) safe else "$safe.pdf"
}

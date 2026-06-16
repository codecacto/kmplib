package br.com.codecacto.kmplib.pdf

/**
 * Implementação iOS do gerador de PDF do relatório de obra.
 *
 * PENDÊNCIA (herda o item de prioridade alta do backlog da kmplib — publicação de artefatos
 * iOS a partir de host macOS): o render real deve usar `UIGraphicsPDFRenderer` (UIKit) para
 * desenhar o mesmo layout lógico do Android (cabeçalho com logo/nome/telefone, período +
 * emissão, bloco da obra com barra de progresso, seção Etapas, grade de fotos, blocos de
 * diário, marca d'água -45° quando `watermark=true`). Só pode ser escrita/validada em macOS.
 *
 * Até lá, [generate] lança [OsPdfNotSupportedException] — o consumidor deve tratar com
 * fallback (ex.: compartilhar um resumo em texto via `ShareHandler`). Reusa a mesma exceção
 * dos demais geradores para uniformizar o tratamento no app.
 */
class IosWorkReportPdfGenerator : WorkReportPdfGenerator {
    override fun generate(data: WorkReportPdfData): ByteArray {
        // TODO(iOS/macOS): implementar com UIGraphicsPDFRenderer (UIKit).
        //  - PDF A4 (595 x 842 pt).
        //  - Cabeçalho (UIImage do logoBytes), período + emissão à direita.
        //  - Bloco da obra (nome, cliente, endereço, barra de progresso geral, 0..100).
        //  - Seção Etapas (nome + status + barra/% + nota opcional).
        //  - Grade de fotos (UIImage do imageBytes, etapa + legenda + takenAtLabel).
        //  - Diário (data/clima/equipe/autor + texto com wrap).
        //  - Marca d'água -45° quando watermark=true.
        //  - Retornar os bytes do NSData do PDF (toByteArray).
        throw OsPdfNotSupportedException(
            "Geração de PDF do relatório de obra ainda não implementada no iOS (requer render " +
                "nativo UIGraphicsPDFRenderer em host macOS). Use um fallback de texto via ShareHandler.",
        )
    }
}

actual fun createWorkReportPdfGenerator(): WorkReportPdfGenerator =
    IosWorkReportPdfGenerator()

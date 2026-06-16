package br.com.codecacto.kmplib.pdf

/**
 * Implementação iOS do gerador de PDF de tabela genérico.
 *
 * PENDÊNCIA (herda o item de prioridade alta do backlog da kmplib — publicação de artefatos
 * iOS a partir de host macOS): o render real deve usar `UIGraphicsPDFRenderer` (UIKit) para
 * desenhar o mesmo layout lógico do Android (cabeçalho com logo/empresa, título/subtítulo à
 * direita, linha de cabeçalho da tabela em negrito com fundo neutro, linhas com zebra/strokes,
 * resumo textual, rodapé, marca d'água -45° quando `watermark=true`). Só pode ser
 * escrita/validada em macOS.
 *
 * Até lá, [generate] lança [OsPdfNotSupportedException] — o consumidor deve tratar com
 * fallback (ex.: compartilhar um resumo em texto via `ShareHandler`). Reusa a mesma exceção
 * dos demais geradores para uniformizar o tratamento no app.
 */
class IosTableReportPdfGenerator : TableReportPdfGenerator {
    override fun generate(data: TableReportPdfData): ByteArray {
        // TODO(iOS/macOS): implementar com UIGraphicsPDFRenderer (UIKit).
        //  - PDF A4 (595 x 842 pt).
        //  - Cabeçalho (UIImage do logoBytes) + empresa; título/subtítulo à direita.
        //  - Linha de cabeçalho da tabela (negrito, fundo neutro) com colunas ponderadas.
        //  - Linhas de dados com zebra + strokes leves; alinhamento por coluna.
        //  - Paginação (repetir o cabeçalho da tabela no topo de cada página).
        //  - Resumo textual (sem dinheiro) + rodapé.
        //  - Marca d'água -45° quando watermark=true.
        //  - Retornar os bytes do NSData do PDF (toByteArray).
        throw OsPdfNotSupportedException(
            "Geração de PDF de tabela ainda não implementada no iOS (requer render nativo " +
                "UIGraphicsPDFRenderer em host macOS). Use um fallback de texto via ShareHandler.",
        )
    }
}

actual fun createTableReportPdfGenerator(): TableReportPdfGenerator =
    IosTableReportPdfGenerator()

package br.com.codecacto.kmplib.pdf

/**
 * Implementação iOS do gerador de PDF do relatório financeiro.
 *
 * PENDÊNCIA (herda o item de prioridade alta do backlog da kmplib — publicação de
 * artefatos iOS a partir de host macOS): o render real deve usar `UIGraphicsPDFRenderer`
 * (UIKit) para desenhar o mesmo layout lógico do Android (cabeçalho com logo/nome/telefone,
 * período + emissão, seção Recebimentos, seção Contas a receber, totais destacados). Essa
 * implementação só pode ser escrita/compilada/validada em host macOS com o SDK iOS.
 *
 * Até lá, [generate] lança [OsPdfNotSupportedException] — o consumidor deve tratar com
 * fallback (ex.: compartilhar um resumo em texto via `ShareHandler`). Reusa a mesma
 * exceção do gerador de OS para uniformizar o tratamento no app.
 */
class IosFinanceReportPdfGenerator : FinanceReportPdfGenerator {
    override fun generate(data: FinanceReportPdfData): ByteArray {
        // TODO(iOS/macOS): implementar com UIGraphicsPDFRenderer (UIKit).
        //  - PDF A4 (595 x 842 pt).
        //  - Cabeçalho (UIImage do logoBytes), período + emissão à direita.
        //  - Seção Recebimentos (tabela) + total recebido em caixa destacada.
        //  - Seção Contas a receber (tabela) + total a receber em caixa destacada.
        //  - Reusar OsPdfFormat.money(...) para formatar valores BRL.
        //  - Retornar os bytes do NSData do PDF (toByteArray).
        throw OsPdfNotSupportedException(
            "Geração de PDF do relatório financeiro ainda não implementada no iOS (requer " +
                "render nativo UIGraphicsPDFRenderer em host macOS). Use um fallback de texto " +
                "via ShareHandler.",
        )
    }
}

actual fun createFinanceReportPdfGenerator(): FinanceReportPdfGenerator =
    IosFinanceReportPdfGenerator()

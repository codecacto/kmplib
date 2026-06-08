package br.com.codecacto.kmplib.pdf

/**
 * Implementação iOS do gerador de PDF da OS.
 *
 * PENDÊNCIA (herda o item de prioridade alta do backlog da kmplib — publicação de
 * artefatos iOS a partir de host macOS): o render real deve usar
 * `UIGraphicsPDFRenderer` (UIKit) + `NSString.drawAtPoint`/`CGContext` para
 * desenhar o mesmo layout lógico do Android (cabeçalho com logo/nome/contato,
 * tabela de itens, total destacado, rodapé e marca d'água condicional). Essa
 * implementação só pode ser escrita/compilada/validada em host macOS com o SDK iOS.
 *
 * Até lá, [generate] lança [OsPdfNotSupportedException] — o consumidor deve tratar
 * com fallback (ex.: compartilhar um resumo em texto via `ShareHandler`).
 */
class IosOsPdfGenerator : OsPdfGenerator {
    override fun generate(data: OsPdfData): ByteArray {
        // TODO(iOS/macOS): implementar com UIGraphicsPDFRenderer (UIKit).
        //  - Criar PDF A4 (612 x 792 pt em UIKit ou 595 x 842 para A4 real).
        //  - Desenhar cabeçalho (UIImage do logoBytes via UIImage.imageWithData),
        //    bloco do cliente, tabela de itens, total em caixa destacada e rodapé.
        //  - Marca d'água rotacionada quando data.watermark == true.
        //  - Reusar OsPdfFormat.money(...) para formatar valores BRL.
        //  - Retornar os bytes do NSData do PDF (toByteArray).
        throw OsPdfNotSupportedException(
            "Geração de PDF da OS ainda não implementada no iOS (requer render nativo " +
                "UIGraphicsPDFRenderer em host macOS). Use um fallback de texto via ShareHandler.",
        )
    }
}

actual fun createOsPdfGenerator(): OsPdfGenerator = IosOsPdfGenerator()

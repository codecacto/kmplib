package br.com.codecacto.kmplib.platform

/**
 * Handler para compartilhamento de conteúdo.
 *
 * Uso:
 * ```kotlin
 * val shareHandler = getShareHandler()
 *
 * // Compartilhar texto
 * shareHandler.shareText("Texto para compartilhar", "Título")
 *
 * // Compartilhar imagem
 * shareHandler.shareImage(imageBytes, "imagem.png", "Título")
 *
 * // Compartilhar arquivo
 * shareHandler.shareFile(fileBytes, "documento.pdf", "application/pdf", "Título")
 * ```
 *
 * **Contrato de erro (desde 2.31.0):** se o compartilhamento falhar (ex.: no
 * Android o FileProvider não conseguir resolver a URI, ou nenhum app puder abrir
 * o chooser), a exceção é **propagada** ao chamador — não mais engolida com log.
 * Envolva a chamada em try/catch (ou propague) para refletir `Falha` na UI.
 */
interface ShareHandler {
    /**
     * Compartilha texto.
     * @param text Texto a ser compartilhado
     * @param title Título do compartilhamento (usado no chooser)
     * @throws Exception se o compartilhamento não puder ser iniciado.
     */
    fun shareText(text: String, title: String = "")

    /**
     * Compartilha uma imagem.
     * @param imageBytes Bytes da imagem
     * @param fileName Nome do arquivo
     * @param title Título do compartilhamento
     * @throws Exception se o compartilhamento não puder ser iniciado.
     */
    fun shareImage(imageBytes: ByteArray, fileName: String, title: String = "")

    /**
     * Compartilha um arquivo.
     * @param fileBytes Bytes do arquivo
     * @param fileName Nome do arquivo
     * @param mimeType Tipo MIME do arquivo
     * @param title Título do compartilhamento
     * @throws Exception se o compartilhamento não puder ser iniciado (ex.:
     *   FileProvider indisponível). Antes da 2.31.0 essa falha era engolida e o
     *   chamador recebia sucesso indevidamente.
     */
    fun shareFile(fileBytes: ByteArray, fileName: String, mimeType: String, title: String = "")
}

/**
 * Obtém a implementação do ShareHandler para a plataforma atual.
 */
expect fun getShareHandler(): ShareHandler

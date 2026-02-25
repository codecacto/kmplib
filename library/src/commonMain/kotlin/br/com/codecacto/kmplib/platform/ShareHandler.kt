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
 */
interface ShareHandler {
    /**
     * Compartilha texto.
     * @param text Texto a ser compartilhado
     * @param title Título do compartilhamento (usado no chooser)
     */
    fun shareText(text: String, title: String = "")

    /**
     * Compartilha uma imagem.
     * @param imageBytes Bytes da imagem
     * @param fileName Nome do arquivo
     * @param title Título do compartilhamento
     */
    fun shareImage(imageBytes: ByteArray, fileName: String, title: String = "")

    /**
     * Compartilha um arquivo.
     * @param fileBytes Bytes do arquivo
     * @param fileName Nome do arquivo
     * @param mimeType Tipo MIME do arquivo
     * @param title Título do compartilhamento
     */
    fun shareFile(fileBytes: ByteArray, fileName: String, mimeType: String, title: String = "")
}

/**
 * Obtém a implementação do ShareHandler para a plataforma atual.
 */
expect fun getShareHandler(): ShareHandler

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

    /**
     * Compartilha um **link** com uma mensagem, pelo share sheet nativo (Android `ACTION_SEND` +
     * chooser do sistema; iOS `UIActivityViewController`). Desde 2.195.0.
     *
     * Mensagem e link vão num **texto único** ([composeShareText]) — ver lá o porquê. Para o link do
     * próprio app, com UTM, monte [url] com [AppShareLink]; a tela pronta é o `ShareAppMenuItem`
     * (`kmplib-ui`).
     *
     * @param url link absoluto (idealmente já com UTM).
     * @param message texto que antecede o link. Em branco = só o link.
     * @param title título do compartilhamento: no Android vira o `EXTRA_TITLE` (a prévia no topo da
     *   folha do Android 10+) e o `EXTRA_SUBJECT` (assunto, quando o destino é e-mail); no iOS não
     *   tem onde aparecer, igual ao [shareText].
     * @throws Exception se o compartilhamento não puder ser iniciado.
     */
    fun shareLink(url: String, message: String = "", title: String = "") {
        shareText(composeShareText(message, url), title)
    }

    /**
     * Apaga os arquivos que este handler criou para compartilhar e que já passaram de
     * [olderThanMillis] (default [DEFAULT_SHARED_FILE_TTL_MILLIS]).
     *
     * **Chame no bootstrap do app.** `shareImage`/`shareFile` precisam materializar um arquivo, e até
     * a 2.104.0 essa cópia **nunca era apagada** — o app exportava o dado, o usuário depois apagava o
     * registro dentro do app, e a cópia em texto claro continuava no armazenamento, invisível e sem
     * nenhuma ação do app capaz de removê-la. A partir da 2.105.0 a lib purga sozinha antes de cada
     * novo share e, no iOS, ao fim da folha de compartilhamento; este método é o complemento para o
     * caso em que o app nunca mais compartilha nada.
     *
     * ```kotlin
     * // bootstrap (Application / MainViewController)
     * getShareHandler().clearSharedFiles()
     * ```
     *
     * @param olderThanMillis idade mínima do arquivo para ser apagado. **`0` apaga tudo** — reserve
     *   para ação explícita do usuário ("limpar dados"), porque um share disparado e ainda em leitura
     *   pelo app receptor seria interrompido.
     * @return quantos arquivos foram apagados (`0` também quando não havia nada a apagar).
     */
    fun clearSharedFiles(olderThanMillis: Long = DEFAULT_SHARED_FILE_TTL_MILLIS): Int = 0
}

/**
 * Obtém a implementação do ShareHandler para a plataforma atual.
 */
expect fun getShareHandler(): ShareHandler

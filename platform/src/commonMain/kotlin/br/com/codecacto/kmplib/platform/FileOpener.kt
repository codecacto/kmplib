package br.com.codecacto.kmplib.platform

/**
 * Abre um arquivo (bytes que o app já tem — ex.: anexo baixado do servidor) **no visualizador do
 * sistema** (2.311.0).
 *
 * - **Android:** grava em `cacheDir/shared_files` (a pasta do [ShareHandler], já exposta pelo
 *   FileProvider da lib e já varrida por [clearKmpLibTemporaryFiles]) e dispara `ACTION_VIEW` com a
 *   URI do FileProvider e permissão de leitura temporária — o sistema oferece os apps que abrem
 *   aquele tipo. Sem nenhum, [FileOpenResult.NoAppAvailable].
 * - **iOS:** `QLPreviewController` (Quick Look) — o visualizador nativo de PDF, imagem, documento do
 *   Office, texto etc., com o botão de compartilhar/salvar em Arquivos do próprio sistema. O arquivo
 *   temporário é apagado quando a pré-visualização fecha. Tipo que o Quick Look não sabe mostrar:
 *   [FileOpenResult.NoAppAvailable].
 *
 * Em [FileOpenResult.NoAppAvailable], a saída de sempre é compartilhar
 * ([ShareHandler.shareFile]) — a pessoa escolhe para onde mandar.
 *
 * App infantil (`KmpLibAudience.KIDS`): abrir outro app passa pelo portão de pais, como o
 * compartilhar; nesse caso a chamada devolve [FileOpenResult.Opened] e a abertura acontece depois do
 * adulto.
 */
interface FileOpener {
    /**
     * @param fileName nome de exibição (vai sanitizado para o disco — um separador escreveria fora
     *   da pasta). Não vai para log.
     * @param mimeType tipo do arquivo; branco = `application/octet-stream`.
     */
    fun open(fileBytes: ByteArray, fileName: String, mimeType: String): FileOpenResult
}

/** Desfecho de [FileOpener.open]. */
enum class FileOpenResult {
    /** O visualizador foi aberto. */
    Opened,

    /** Nenhum app/visualizador do aparelho abre este tipo — ofereça compartilhar. */
    NoAppAvailable,

    /** Falha ao gravar o temporário ou ao apresentar (sem janela ativa, por exemplo). */
    Failed,
}

/** O [FileOpener] da plataforma (Android exige `initKmpLibPlatform`/`ShareHandlerHolder.init`). */
expect fun getFileOpener(): FileOpener

/** MIME seguro para o sistema: branco ou malformado vira `application/octet-stream`. */
internal fun normalizeOpenMimeType(mimeType: String): String {
    val limpo = mimeType.substringBefore(';').trim().lowercase()
    return if (limpo.isEmpty() || !limpo.contains('/') || limpo.startsWith("/") || limpo.endsWith("/")) {
        "application/octet-stream"
    } else {
        limpo
    }
}

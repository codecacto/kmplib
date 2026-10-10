package br.com.codecacto.kmplib.platform

/**
 * Pasta (dentro do `cacheDir` do Android) do temporário que o `PdfRenderer` exige para abrir um PDF
 * no `PdfViewer` (`kmplib-pdf`). No iOS o visualizador lê da memória (`PDFDocument(data:)`) e não
 * grava nada.
 */
const val PDF_VIEWER_TEMP_DIRECTORY: String = "kmplib_pdfviewer"

/**
 * Pasta (dentro do `cacheDir` do Android) do PDF entregue ao spooler pelo `PrintHandler`. No iOS a
 * impressão recebe o `NSData` direto (`printingItem`) e não grava nada.
 */
const val PRINT_TEMP_DIRECTORY: String = "kmplib_print"

/**
 * Apaga **todos os temporários que a kmplib grava em disco** (2.280.0): as cópias de
 * compartilhamento ([ShareHandler.clearSharedFiles]), o temporário do visualizador de PDF
 * ([PDF_VIEWER_TEMP_DIRECTORY]) e o PDF de impressão ([PRINT_TEMP_DIRECTORY]).
 *
 * ### Por que existe
 * Esses arquivos podem ser dado sensível — receita, laudo, relatório. A lib apaga cada um ao fim do
 * uso, mas **processo morto não roda `close()`**: o sistema mata o app com o PDF aberto ou com o
 * diálogo de impressão na tela, e o arquivo fica no `cacheDir`, numa pasta que nenhuma tela mostra.
 * Esta função é a varredura que fecha esse buraco.
 *
 * ### Onde chamar
 * ```kotlin
 * // bootstrap (Application.onCreate depois de initKmpLibPlatform / MainViewController)
 * clearKmpLibTemporaryFiles()
 * // logout e exclusão de conta — tudo, não só o que passou da idade
 * clearKmpLibTemporaryFiles(olderThanMillis = 0L)
 * ```
 * Substitui a chamada solta de `getShareHandler().clearSharedFiles(...)` (que continua valendo e
 * limpa só o compartilhamento).
 *
 * O que está **em uso agora neste processo** (diálogo de impressão aberto, PDF sendo aberto) nunca é
 * apagado, nem com `0`: ele some sozinho quando o uso termina. Com `0`, um compartilhamento recém
 * disparado pode ser interrompido no app receptor — por isso `0` é para logout/exclusão, não para o
 * bootstrap.
 *
 * Nunca lança. No Android, sem `initKmpLibPlatform` (contexto ausente) devolve `0` com aviso no log.
 *
 * @param olderThanMillis idade mínima para apagar; `0` (ou negativo) apaga tudo o que não está em uso.
 * @return quantos arquivos foram apagados.
 */
fun clearKmpLibTemporaryFiles(olderThanMillis: Long = DEFAULT_SHARED_FILE_TTL_MILLIS): Int =
    clearPlatformTemporaryFiles(olderThanMillis)

internal expect fun clearPlatformTemporaryFiles(olderThanMillis: Long): Int

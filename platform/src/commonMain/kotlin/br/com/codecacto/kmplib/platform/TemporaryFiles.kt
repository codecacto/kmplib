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
 * Pasta do vídeo **gravado** pela câmera da lib (`VideoRecorderCamera`, `kmplib-camera`, 2.286.0):
 * dentro do `cacheDir` no Android e do `NSTemporaryDirectory()` no iOS.
 *
 * É vídeo da pessoa (no App do Personal, o corpo dela): **temporário por contrato** — o app apaga
 * depois de preparar/enviar (`RecordedVideo.deleteFile()`), e esta varredura apaga o que sobrou de
 * processo morto. Fila de envio durável que precise do arquivo além disso **move-o para a área
 * dela** antes; daqui ele sai.
 */
const val VIDEO_CAPTURE_TEMP_DIRECTORY: String = "kmplib_video_capture"

/**
 * Pasta do vídeo **comprimido** pelo `VideoTranscoder` (`kmplib-video`, 2.286.0), no mesmo lugar e
 * com o mesmo contrato de [VIDEO_CAPTURE_TEMP_DIRECTORY].
 */
const val VIDEO_PREPARED_TEMP_DIRECTORY: String = "kmplib_video_prepared"

/**
 * Pasta da **nota de voz gravada** pelo `AudioRecorder` (`kmplib-media`, 2.287.0), no mesmo lugar e
 * com o mesmo contrato de [VIDEO_CAPTURE_TEMP_DIRECTORY]: é a voz da pessoa, temporária — o app a
 * envia e apaga (`RecordedAudio.deleteFile()`); fila durável que precise dela além disso a move antes.
 */
const val AUDIO_CAPTURE_TEMP_DIRECTORY: String = "kmplib_audio_capture"

/**
 * Pasta do áudio **baixado para tocar** pelo `VoiceNotePlayer` (`kmplib-media`, 2.287.0) — a nota de
 * voz recebida numa conversa. Cópia de leitura; sai pela mesma varredura.
 */
const val AUDIO_PLAYBACK_TEMP_DIRECTORY: String = "kmplib_audio_playback"

/**
 * Apaga **todos os temporários que a kmplib grava em disco** (2.280.0): as cópias de
 * compartilhamento ([ShareHandler.clearSharedFiles]), o temporário do visualizador de PDF
 * ([PDF_VIEWER_TEMP_DIRECTORY]), o PDF de impressão ([PRINT_TEMP_DIRECTORY]) e, desde a 2.286.0, o
 * vídeo gravado ([VIDEO_CAPTURE_TEMP_DIRECTORY]) e o comprimido ([VIDEO_PREPARED_TEMP_DIRECTORY]) e,
 * desde a 2.287.0, a nota de voz gravada ([AUDIO_CAPTURE_TEMP_DIRECTORY]) e a baixada para tocar
 * ([AUDIO_PLAYBACK_TEMP_DIRECTORY]).
 *
 * ### Por que existe
 * Esses arquivos podem ser dado sensível — receita, laudo, relatório, o vídeo do corpo do aluno. A lib apaga cada um ao fim do
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

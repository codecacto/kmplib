package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable

/**
 * **Salvar um arquivo onde a pessoa escolher** (2.225.0) — o "Salvar" ao lado de "Compartilhar".
 *
 * - Android: **Storage Access Framework** (`ACTION_CREATE_DOCUMENT`) — o seletor do sistema abre
 *   com o nome sugerido, a pessoa escolhe a pasta (Downloads, Drive…) e o app grava sem pedir
 *   permissão de armazenamento, em qualquer versão do Android.
 * - iOS: `UIDocumentPickerViewController(forExportingURLs:asCopy:)` — o "Salvar em Arquivos".
 *
 * ```kotlin
 * val saver = rememberFileSaver { result ->
 *     when (result) {
 *         is FileSaveResult.Saved -> showSnackbar("PDF salvo")
 *         FileSaveResult.Cancelled -> Unit
 *         is FileSaveResult.Failed -> showSnackbar("Não foi possível salvar")
 *     }
 * }
 * AppOutlinedButton("Salvar") { saver.save(pdf, "colinha-sp-cartao.pdf", "application/pdf") }
 * ```
 *
 * O conteúdo fica em memória enquanto o seletor está aberto. Se o sistema matar o app nesse
 * intervalo, a volta chega sem o conteúdo: o documento vazio que o seletor criou é apagado e o
 * resultado é [FileSaveResult.Failed].
 */
interface FileSaver {
    /**
     * Abre o seletor para gravar [bytes] como [fileName]. [onResult] (do [rememberFileSaver]) é
     * chamado uma vez, na thread principal.
     */
    fun save(bytes: ByteArray, fileName: String, mimeType: String)
}

/** Resultado de [FileSaver.save]. */
sealed interface FileSaveResult {
    /** Gravado. [location] = URI (Android) ou caminho (iOS) escolhido, para log. */
    data class Saved(val location: String?) : FileSaveResult

    /** A pessoa fechou o seletor sem salvar. */
    data object Cancelled : FileSaveResult

    /** Falhou ao gravar. [message] é técnica, para log — não para a tela. */
    data class Failed(val message: String, val cause: Throwable? = null) : FileSaveResult
}

/** Salvador de arquivo da tela atual. */
@Composable
expect fun rememberFileSaver(onResult: (FileSaveResult) -> Unit): FileSaver

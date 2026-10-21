package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.platform.audience.withParentalGate
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.QuickLook.QLPreviewController
import platform.QuickLook.QLPreviewControllerDataSourceProtocol
import platform.QuickLook.QLPreviewControllerDelegateProtocol
import platform.QuickLook.QLPreviewItemProtocol
import platform.darwin.NSObject

/**
 * Quick Look (`QLPreviewController`) — o visualizador nativo do iOS para PDF, imagem, Office, texto,
 * com o botão de compartilhar/salvar em Arquivos do próprio sistema. O temporário fica em
 * `NSTemporaryDirectory()/shared_files` (a pasta da purga de [clearKmpLibTemporaryFiles]) e é
 * apagado quando a pré-visualização fecha (`previewControllerDidDismiss`).
 */
internal class IosFileOpener : FileOpener {

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun open(fileBytes: ByteArray, fileName: String, mimeType: String): FileOpenResult {
        var caminho: String? = null
        return try {
            val presenter = topViewController() ?: run {
                AppLogger.w(OPEN_TAG, "nenhuma janela ativa para abrir o arquivo")
                return FileOpenResult.Failed
            }
            val pasta = NSTemporaryDirectory().trimEnd('/') + "/" + SHARED_FILES_DIRECTORY
            val fm = NSFileManager.defaultManager
            if (!fm.fileExistsAtPath(pasta)) {
                fm.createDirectoryAtPath(pasta, withIntermediateDirectories = true, attributes = null, error = null)
            }
            val path = "$pasta/${sanitizeSharedFileName(fileName)}".also { caminho = it }
            val data = fileBytes.usePinned { pinned ->
                NSData.create(bytes = if (fileBytes.isEmpty()) null else pinned.addressOf(0), length = fileBytes.size.toULong())
            }
            if (!data.writeToFile(path, atomically = true)) {
                apagar(path)
                return FileOpenResult.Failed
            }

            val item = PreviewItem(NSURL.fileURLWithPath(path), fileName)
            if (!QLPreviewController.canPreviewItem(item)) {
                apagar(path)
                return FileOpenResult.NoAppAvailable
            }
            val sessao = PreviewSession(item, path)
            val controller = QLPreviewController()
            controller.dataSource = sessao
            controller.delegate = sessao
            // dataSource e delegate são FRACOS no UIKit: a sessão fica viva aqui até fechar.
            ativa = sessao
            presenter.presentViewController(controller, animated = true, completion = null)
            FileOpenResult.Opened
        } catch (e: Exception) {
            caminho?.let(::apagar)
            // Só o tipo do erro: o nome do arquivo é dado pessoal.
            AppLogger.e(OPEN_TAG, "Erro ao abrir arquivo: ${e::class.simpleName}")
            FileOpenResult.Failed
        }
    }
}

/** Item do Quick Look com o nome de exibição original (o do disco é sanitizado). */
private class PreviewItem(
    private val url: NSURL,
    private val title: String,
) : NSObject(), QLPreviewItemProtocol {
    override fun previewItemURL(): NSURL? = url
    override fun previewItemTitle(): String? = title
}

private class PreviewSession(
    private val item: PreviewItem,
    private val path: String,
) : NSObject(), QLPreviewControllerDataSourceProtocol, QLPreviewControllerDelegateProtocol {

    override fun numberOfPreviewItemsInPreviewController(controller: QLPreviewController): Long = 1L

    override fun previewController(controller: QLPreviewController, previewItemAtIndex: Long): QLPreviewItemProtocol = item

    override fun previewControllerDidDismiss(controller: QLPreviewController) {
        apagar(path)
        if (ativa === this) ativa = null
    }
}

/** A pré-visualização aberta (uma por vez — o Quick Look é modal). */
private var ativa: PreviewSession? = null

@OptIn(ExperimentalForeignApi::class)
private fun apagar(path: String) {
    val fm = NSFileManager.defaultManager
    if (fm.fileExistsAtPath(path) && !fm.removeItemAtPath(path, null)) {
        AppLogger.w(OPEN_TAG, "não foi possível apagar o arquivo aberto")
    }
}

// Constante de arquivo: campo em companion de subclasse de NSObject não compila no K/N.
private const val OPEN_TAG = "FileOpener"

actual fun getFileOpener(): FileOpener = IosFileOpener().withParentalGate()

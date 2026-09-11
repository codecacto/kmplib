package br.com.codecacto.kmplib.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.timeIntervalSince1970
import platform.Foundation.writeToFile
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import br.com.codecacto.kmplib.core.util.AppLogger

class IosShareHandler : ShareHandler {

    companion object {
        private const val TAG = "ShareHandler"
    }

    override fun shareText(text: String, title: String) {
        try {
            val activityController = UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null
            )
            presentActivityController(activityController)
        } catch (e: Exception) {
            // Loga E RELANÇA (paridade com Android): falha de share deve propagar
            // para o chamador refletir erro, não reportar sucesso silencioso.
            AppLogger.e(TAG, "Erro ao compartilhar texto", e)
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun shareImage(imageBytes: ByteArray, fileName: String, title: String) {
        try {
            val nsData = imageBytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
            }

            // Bytes que não decodificam como imagem LANÇAM (contrato do ShareHandler, paridade com o
            // Android): antes o método voltava calado, e o chamador registrava um share que não houve.
            val image = UIImage.imageWithData(nsData)
                ?: throw IllegalArgumentException("os bytes de '$fileName' não são uma imagem decodificável")
            val activityController = UIActivityViewController(
                activityItems = listOf(image),
                applicationActivities = null
            )
            presentActivityController(activityController)
        } catch (e: Exception) {
            // Loga E RELANÇA (paridade com Android).
            AppLogger.e(TAG, "Erro ao compartilhar imagem", e)
            throw e
        }
    }

    /**
     * Grava o arquivo num diretório dedicado dentro do `NSTemporaryDirectory()` e o **apaga quando a
     * folha de compartilhamento termina** (`completionWithItemsHandler` — o sinal oficial da Apple de
     * que o fluxo acabou, inclusive quando o usuário cancela).
     *
     * Apagar antes disso quebraria o share: o app receptor lê a `file://` depois. E confiar na purga
     * "eventual" do diretório temporário pelo sistema não serve para um app cuja política afirma que
     * o dado não sobra em lugar nenhum — daí o handler + a purga por idade como rede de segurança.
     */
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun shareFile(fileBytes: ByteArray, fileName: String, mimeType: String, title: String) {
        try {
            // Rede de segurança: recolhe o que sobrou de shares anteriores (processo morto antes do
            // completion handler, por exemplo). O arquivo deste share é gravado DEPOIS.
            clearSharedFiles()

            val nsData = fileBytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = fileBytes.size.toULong())
            }

            val dir = sharedFilesDirectory()
            ensureDirectory(dir)
            val tempPath = "$dir/${sanitizeSharedFileName(fileName)}"
            nsData.writeToFile(tempPath, atomically = true)

            val fileUrl = NSURL.fileURLWithPath(tempPath)
            val activityController = UIActivityViewController(
                activityItems = listOf(fileUrl),
                applicationActivities = null
            )
            activityController.completionWithItemsHandler = { _, _, _, _ ->
                deleteFile(tempPath)
            }
            presentActivityController(activityController)
        } catch (e: Exception) {
            // Loga E RELANÇA (paridade com Android).
            AppLogger.e(TAG, "Erro ao compartilhar arquivo: $fileName", e)
            throw e
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun clearSharedFiles(olderThanMillis: Long): Int {
        val dir = sharedFilesDirectory()
        val fileManager = NSFileManager.defaultManager
        val nomes = fileManager.contentsOfDirectoryAtPath(dir, null) ?: return 0
        val agora = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
        var apagados = 0
        nomes.filterIsInstance<String>().forEach { nome ->
            val path = "$dir/$nome"
            if (!shouldPurgeSharedFile(lastModifiedMillis(path), agora, olderThanMillis)) return@forEach
            if (fileManager.removeItemAtPath(path, null)) {
                apagados++
            } else {
                AppLogger.w(TAG, "não foi possível apagar o arquivo compartilhado $nome")
            }
        }
        return apagados
    }

    private fun sharedFilesDirectory(): String =
        NSTemporaryDirectory().trimEnd('/') + "/" + SHARED_FILES_DIRECTORY

    @OptIn(ExperimentalForeignApi::class)
    private fun ensureDirectory(path: String) {
        val fileManager = NSFileManager.defaultManager
        if (fileManager.fileExistsAtPath(path)) return
        fileManager.createDirectoryAtPath(
            path = path,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun deleteFile(path: String) {
        val fileManager = NSFileManager.defaultManager
        if (!fileManager.fileExistsAtPath(path)) return
        if (!fileManager.removeItemAtPath(path, null)) {
            AppLogger.w(TAG, "não foi possível apagar o arquivo compartilhado após o share")
        }
    }

    /** `0` quando o sistema não informa a data — a regra pura trata isso como resíduo antigo. */
    @OptIn(ExperimentalForeignApi::class)
    private fun lastModifiedMillis(path: String): Long {
        val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, null) ?: return 0L
        val data = attrs[NSFileModificationDate] as? NSDate ?: return 0L
        return (data.timeIntervalSince1970 * 1000.0).toLong()
    }

    /**
     * Apresenta a folha de compartilhamento a partir do controlador **no topo** da janela-chave.
     *
     * Três correções de 2.195.0 sobre o que havia:
     * 1. **Janela** pelas `connectedScenes` (cena ativa em primeiro plano, `keyWindow` dela) — o
     *    `UIApplication.windows` está obsoleto desde o iOS 15, e o primeiro da lista nem sempre é a
     *    janela que o usuário vê.
     * 2. **Controlador do topo** (seguindo `presentedViewController`): apresentar a partir do
     *    `rootViewController` quando já há algo apresentado por cima (uma sheet, um diálogo) não
     *    abre nada — o UIKit só loga "already presenting" e o toque em "Compartilhar" morre.
     * 3. **Âncora de popover no iPad**: lá o `UIActivityViewController` é um popover, e sem
     *    `sourceView` o UIKit lança exceção na apresentação — app universal fechava ao compartilhar.
     *    Ancoramos no centro da tela, sem seta (não há botão nativo para apontar numa tela Compose).
     *
     * Sem janela ou controlador para apresentar, **lança** — é o contrato do [ShareHandler]: falha
     * de compartilhamento propaga, em vez de parecer sucesso.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun presentActivityController(controller: UIActivityViewController) {
        val presenter = topViewController()
            ?: throw IllegalStateException("nenhuma janela ativa para apresentar o compartilhamento")

        controller.popoverPresentationController?.let { popover ->
            val anchor = presenter.view
            popover.sourceView = anchor
            anchor.bounds.useContents {
                popover.sourceRect = CGRectMake(
                    origin.x + size.width / 2.0,
                    origin.y + size.height / 2.0,
                    0.0,
                    0.0,
                )
            }
            popover.permittedArrowDirections = 0uL
        }
        presenter.presentViewController(controller, animated = true, completion = null)
    }

    private fun topViewController(): UIViewController? {
        val scenes = UIApplication.sharedApplication.connectedScenes.filterIsInstance<UIWindowScene>()
        val scene = scenes.firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
            ?: scenes.firstOrNull()
        val window = scene?.keyWindow
            ?: scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull { it.isKeyWindow() }
            ?: scene?.windows?.filterIsInstance<UIWindow>()?.firstOrNull()
        var top = window?.rootViewController ?: return null
        while (true) {
            val next = top.presentedViewController ?: break
            // Não empilha em cima de um controlador que está saindo de cena.
            if (next.isBeingDismissed()) break
            top = next
        }
        return top
    }
}

actual fun getShareHandler(): ShareHandler = IosShareHandler()

package br.com.codecacto.kmplib.platform

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIImage
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
            AppLogger.e(TAG, "Erro ao compartilhar texto", e)
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun shareImage(imageBytes: ByteArray, fileName: String, title: String) {
        try {
            val nsData = imageBytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = imageBytes.size.toULong())
            }

            val image = UIImage.imageWithData(nsData)
            if (image != null) {
                val activityController = UIActivityViewController(
                    activityItems = listOf(image),
                    applicationActivities = null
                )
                presentActivityController(activityController)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao compartilhar imagem", e)
        }
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun shareFile(fileBytes: ByteArray, fileName: String, mimeType: String, title: String) {
        try {
            val nsData = fileBytes.usePinned { pinned ->
                NSData.create(bytes = pinned.addressOf(0), length = fileBytes.size.toULong())
            }

            // Salva arquivo temporário
            val tempPath = NSTemporaryDirectory() + fileName
            nsData.writeToFile(tempPath, atomically = true)

            val fileUrl = NSURL.fileURLWithPath(tempPath)
            val activityController = UIActivityViewController(
                activityItems = listOf(fileUrl),
                applicationActivities = null
            )
            presentActivityController(activityController)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao compartilhar arquivo: $fileName", e)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun presentActivityController(controller: UIActivityViewController) {
        try {
            // Get the topmost view controller
            val application = UIApplication.sharedApplication
            val windows = application.windows as List<*>
            val window = windows.firstOrNull() as? platform.UIKit.UIWindow

            window?.let { w ->
                @Suppress("UNCHECKED_CAST")
                val rootVC = w.rootViewController
                rootVC?.presentViewController(controller, animated = true, completion = null)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro ao apresentar share controller", e)
        }
    }
}

actual fun getShareHandler(): ShareHandler = IosShareHandler()

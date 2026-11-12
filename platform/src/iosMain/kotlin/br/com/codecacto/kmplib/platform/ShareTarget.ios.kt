package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.writeToFile
import platform.UIKit.UIApplication
import platform.UIKit.UIDocumentInteractionController
import platform.UIKit.UIDocumentInteractionControllerDelegateProtocol
import platform.UIKit.UIPasteboard
import platform.UIKit.UIPasteboardOptionExpirationDate
import platform.darwin.NSObject

/**
 * Envio direto de imagem no iOS (2.354.0) — ver [ShareTarget].
 *
 * - **WhatsApp:** o caminho documentado pelo WhatsApp para IMAGEM no iOS é o
 *   `UIDocumentInteractionController` com arquivo `.wai` e UTI `net.whatsapp.image` (a UTI exclusiva
 *   faz o menu oferecer só o WhatsApp). O esquema `whatsapp://send` só leva texto.
 * - **Instagram Stories:** a imagem vai na área de transferência sob
 *   `com.instagram.sharedSticker.backgroundImage` (expira em 5 min) e abre-se
 *   `instagram-stories://share?source_application=<App ID>` (documentação da Meta).
 *
 * O `Info.plist` do app declara `LSApplicationQueriesSchemes` com `whatsapp` e `instagram-stories`.
 */
internal object IosShareTargets {
    private const val TAG = "ShareHandler"

    /** O controlador precisa sobreviver enquanto o menu está aberto (o UIKit não o retém). */
    private var currentController: UIDocumentInteractionController? = null
    private var currentDelegate: NSObject? = null

    fun isInstalled(target: ShareTarget, options: ShareImageOptions): Boolean = when (target) {
        ShareTarget.WHATSAPP -> canOpen(WHATSAPP_IOS_SCHEME)
        ShareTarget.WHATSAPP_STATUS -> false
        ShareTarget.INSTAGRAM_STORIES -> instagramStoriesUrl(options.metaAppId)?.let { canOpen(it) } ?: false
    }

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    fun share(target: ShareTarget, bytes: ByteArray, fileName: String, options: ShareImageOptions): ShareTargetResult {
        if (!isInstalled(target, options)) return ShareTargetResult.NotAvailable
        return try {
            val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
            when (target) {
                ShareTarget.WHATSAPP -> shareToWhatsApp(data, fileName)
                ShareTarget.INSTAGRAM_STORIES -> shareToInstagram(data, instagramStoriesUrl(options.metaAppId)!!)
                ShareTarget.WHATSAPP_STATUS -> ShareTargetResult.NotAvailable
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Erro no envio direto (${target.name}): ${e::class.simpleName}")
            ShareTargetResult.Failed(e::class.simpleName ?: "erro")
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun shareToWhatsApp(data: NSData, fileName: String): ShareTargetResult {
        val presenter = topViewController() ?: return ShareTargetResult.Failed("sem janela")
        val dir = NSTemporaryDirectory().trimEnd('/') + "/" + SHARED_FILES_DIRECTORY
        val fm = NSFileManager.defaultManager
        if (!fm.fileExistsAtPath(dir)) fm.createDirectoryAtPath(dir, true, null, null)
        val path = "$dir/${whatsAppIosFileName(fileName)}"
        if (!data.writeToFile(path, atomically = true)) return ShareTargetResult.Failed("gravação")
        val controller = UIDocumentInteractionController.interactionControllerWithURL(NSURL.fileURLWithPath(path))
        controller.UTI = WHATSAPP_IOS_IMAGE_UTI
        val delegate = object : NSObject(), UIDocumentInteractionControllerDelegateProtocol {
            override fun documentInteractionControllerDidDismissOpenInMenu(controller: UIDocumentInteractionController) {
                currentController = null
                currentDelegate = null
            }
        }
        controller.delegate = delegate
        currentController = controller
        currentDelegate = delegate
        val view = presenter.view
        val opened = view.bounds.useContents {
            controller.presentOpenInMenuFromRect(
                CGRectMake(origin.x + size.width / 2.0, origin.y + size.height / 2.0, 0.0, 0.0),
                inView = view,
                animated = true,
            )
        }
        if (!opened) {
            currentController = null
            currentDelegate = null
            return ShareTargetResult.NotAvailable
        }
        return ShareTargetResult.Opened
    }

    private fun shareToInstagram(data: NSData, url: String): ShareTargetResult {
        val nsUrl = NSURL.URLWithString(url) ?: return ShareTargetResult.Failed("url")
        val expiration = NSDate.dateWithTimeIntervalSinceNow(INSTAGRAM_PASTEBOARD_TTL_SECONDS)
        val options: Map<Any?, Any?> = UIPasteboardOptionExpirationDate?.let { mapOf(it to expiration) } ?: emptyMap()
        UIPasteboard.generalPasteboard.setItems(
            listOf(mapOf("com.instagram.sharedSticker.backgroundImage" to data)),
            options = options,
        )
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
        return ShareTargetResult.Opened
    }

    private fun canOpen(url: String): Boolean =
        NSURL.URLWithString(url)?.let { UIApplication.sharedApplication.canOpenURL(it) } ?: false

    /** A Meta recomenda 5 minutos de validade para o item da área de transferência. */
    private const val INSTAGRAM_PASTEBOARD_TTL_SECONDS: Double = 300.0
}

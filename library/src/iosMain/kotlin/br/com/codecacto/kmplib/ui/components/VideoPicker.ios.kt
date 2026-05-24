package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIApplication
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerMediaURL
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.darwin.NSObject
import platform.posix.memcpy

actual class VideoPickerLauncher(
    private val onLaunch: () -> Unit
) {
    actual fun launch() {
        onLaunch()
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberVideoPickerLauncher(
    onVideoSelected: (SelectedVideo) -> Unit
): VideoPickerLauncher {
    return remember {
        VideoPickerLauncher {
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
                ?: return@VideoPickerLauncher

            val cameraDelegate = object : NSObject(),
                UIImagePickerControllerDelegateProtocol,
                UINavigationControllerDelegateProtocol {
                override fun imagePickerController(
                    picker: UIImagePickerController,
                    didFinishPickingMediaWithInfo: Map<Any?, *>
                ) {
                    picker.dismissViewControllerAnimated(true, null)
                    val url = didFinishPickingMediaWithInfo[UIImagePickerControllerMediaURL] as? NSURL
                        ?: return
                    val data = NSData.dataWithContentsOfURL(url) ?: return
                    val bytes = data.toByteArray()
                    val name = url.lastPathComponent ?: "video.mov"
                    val mimeType = when {
                        name.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
                        name.endsWith(".m4v", ignoreCase = true) -> "video/mp4"
                        else -> "video/quicktime"
                    }
                    onVideoSelected(SelectedVideo(bytes = bytes, mimeType = mimeType, name = name))
                }

                override fun imagePickerControllerDidCancel(picker: UIImagePickerController) {
                    picker.dismissViewControllerAnimated(true, null)
                }
            }

            val galleryDelegate = object : NSObject(), PHPickerViewControllerDelegateProtocol {
                override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                    picker.dismissViewControllerAnimated(true, null)

                    val result = didFinishPicking.firstOrNull() as? PHPickerResult ?: return
                    val provider = result.itemProvider ?: return

                    val typeIdentifier = when {
                        provider.hasItemConformingToTypeIdentifier("public.mpeg-4") -> "public.mpeg-4"
                        provider.hasItemConformingToTypeIdentifier("public.movie") -> "public.movie"
                        else -> return
                    }

                    provider.loadDataRepresentationForTypeIdentifier(typeIdentifier) { data, error ->
                        if (error != null || data == null) return@loadDataRepresentationForTypeIdentifier
                        val bytes = data.toByteArray()
                        val name = provider.suggestedName?.let { suggested ->
                            if (suggested.endsWith(".mp4", true) || suggested.endsWith(".mov", true)) {
                                suggested
                            } else if (typeIdentifier == "public.mpeg-4") {
                                "$suggested.mp4"
                            } else {
                                "$suggested.mov"
                            }
                        } ?: if (typeIdentifier == "public.mpeg-4") "video.mp4" else "video.mov"
                        val mimeType = if (typeIdentifier == "public.mpeg-4") "video/mp4" else "video/quicktime"
                        onVideoSelected(SelectedVideo(bytes = bytes, mimeType = mimeType, name = name))
                    }
                }
            }

            val alert = UIAlertController.alertControllerWithTitle(
                title = "Adicionar video",
                message = null,
                preferredStyle = UIAlertControllerStyleActionSheet
            )

            if (UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
                alert.addAction(
                    UIAlertAction.actionWithTitle(
                        title = "Gravar video",
                        style = UIAlertActionStyleDefault
                    ) {
                        val cameraPicker = UIImagePickerController().apply {
                            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
                            mediaTypes = listOf("public.movie")
                            delegate = cameraDelegate
                        }
                        rootViewController.presentViewController(cameraPicker, animated = true, completion = null)
                    }
                )
            }

            alert.addAction(
                UIAlertAction.actionWithTitle(
                    title = "Escolher da galeria",
                    style = UIAlertActionStyleDefault
                ) {
                    val configuration = PHPickerConfiguration().apply {
                        selectionLimit = 1
                        filter = PHPickerFilter.videosFilter
                    }
                    val picker = PHPickerViewController(configuration = configuration)
                    picker.delegate = galleryDelegate
                    rootViewController.presentViewController(picker, animated = true, completion = null)
                }
            )

            alert.addAction(
                UIAlertAction.actionWithTitle(
                    title = "Cancelar",
                    style = UIAlertActionStyleCancel,
                    handler = null
                )
            )

            rootViewController.presentViewController(alert, animated = true, completion = null)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) {
        bytes.usePinned { pinned ->
            memcpy(pinned.addressOf(0), this.bytes, this.length)
        }
    }
    return bytes
}

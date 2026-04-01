package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
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
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIImagePickerController
import platform.UIKit.UIImagePickerControllerDelegateProtocol
import platform.UIKit.UIImagePickerControllerEditedImage
import platform.UIKit.UIImagePickerControllerOriginalImage
import platform.UIKit.UIImagePickerControllerSourceType
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.drawInRect
import platform.darwin.NSObject
import platform.posix.memcpy

actual class ImagePickerLauncher(
    private val onLaunch: () -> Unit
) {
    actual fun launch() {
        onLaunch()
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberImagePickerLauncher(
    onImageSelected: (ByteArray) -> Unit
): ImagePickerLauncher {
    return remember {
        ImagePickerLauncher {
            val rootViewController = UIApplication.sharedApplication.keyWindow?.rootViewController
                ?: return@ImagePickerLauncher

            val cameraDelegate = object : NSObject(),
                UIImagePickerControllerDelegateProtocol,
                UINavigationControllerDelegateProtocol {
                override fun imagePickerController(
                    picker: UIImagePickerController,
                    didFinishPickingMediaWithInfo: Map<Any?, *>
                ) {
                    picker.dismissViewControllerAnimated(true, null)
                    val image = (didFinishPickingMediaWithInfo[UIImagePickerControllerEditedImage]
                        ?: didFinishPickingMediaWithInfo[UIImagePickerControllerOriginalImage]) as? UIImage
                        ?: return

                    val scaledImage = scaleImage(image, 1024.0)
                    val jpegData = UIImageJPEGRepresentation(scaledImage, 0.85)
                        ?: return
                    val bytes = jpegData.toByteArray()
                    onImageSelected(bytes)
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

                    if (provider.hasItemConformingToTypeIdentifier("public.image")) {
                        provider.loadDataRepresentationForTypeIdentifier("public.image") { data, error ->
                            if (error != null || data == null) return@loadDataRepresentationForTypeIdentifier
                            val image = UIImage(data = data)
                                ?: return@loadDataRepresentationForTypeIdentifier

                            val scaledImage = scaleImage(image, 1024.0)
                            val jpegData = UIImageJPEGRepresentation(scaledImage, 0.85)
                                ?: return@loadDataRepresentationForTypeIdentifier

                            val bytes = jpegData.toByteArray()
                            onImageSelected(bytes)
                        }
                    }
                }
            }

            val alert = UIAlertController.alertControllerWithTitle(
                title = "Adicionar foto",
                message = null,
                preferredStyle = UIAlertControllerStyleActionSheet
            )

            if (UIImagePickerController.isSourceTypeAvailable(UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera)) {
                alert.addAction(
                    UIAlertAction.actionWithTitle(
                        title = "Tirar foto",
                        style = UIAlertActionStyleDefault
                    ) {
                        val cameraPicker = UIImagePickerController().apply {
                            sourceType = UIImagePickerControllerSourceType.UIImagePickerControllerSourceTypeCamera
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
                        filter = PHPickerFilter.imagesFilter
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

@OptIn(ExperimentalForeignApi::class)
private fun scaleImage(image: UIImage, maxSize: Double): UIImage {
    val (width, height) = image.size.useContents { width to height }

    if (width <= maxSize && height <= maxSize) return image

    val ratio = minOf(maxSize / width, maxSize / height)
    val newWidth = width * ratio
    val newHeight = height * ratio

    val newSize = CGSizeMake(newWidth, newHeight)
    UIGraphicsBeginImageContextWithOptions(newSize, false, 1.0)
    image.drawInRect(CGRectMake(0.0, 0.0, newWidth, newHeight))
    val scaledImage = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()

    return scaledImage ?: image
}

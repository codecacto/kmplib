@file:OptIn(ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.platform.photos

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Photos.PHAccessLevelAddOnly
import platform.Photos.PHAssetCreationRequest
import platform.Photos.PHAssetResourceCreationOptions
import platform.Photos.PHAssetResourceTypePhoto
import platform.Photos.PHAssetResourceTypeVideo
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHPhotoLibrary
import kotlin.coroutines.resume

private const val TAG = "GallerySaver"

actual fun getGallerySaver(): GallerySaver = IosGallerySaver()

/** [GallerySaver] pela fototeca: acesso "só adicionar" + `PHAssetCreationRequest.addResource(fileURL:)`. */
class IosGallerySaver : GallerySaver {

    override suspend fun save(path: String, fileName: String, mimeType: String, album: String?): GallerySaveResult {
        val tipo = galleryMediaKindOf(mimeType) ?: return GallerySaveResult.UnsupportedType
        if (!NSFileManager.defaultManager.fileExistsAtPath(path)) return GallerySaveResult.Failed
        if (!autorizado()) return GallerySaveResult.PermissionDenied
        val nome = fileName.substringAfterLast('/').ifBlank { path.substringAfterLast('/') }
        return suspendCancellableCoroutine { cont ->
            PHPhotoLibrary.sharedPhotoLibrary().performChanges({
                val opcoes = PHAssetResourceCreationOptions().apply { originalFilename = nome }
                // Recurso a partir do ARQUIVO: a fototeca guarda os bytes como vieram (sem reencodar).
                PHAssetCreationRequest.creationRequestForAsset().addResourceWithType(
                    if (tipo == GalleryMediaKind.IMAGE) PHAssetResourceTypePhoto else PHAssetResourceTypeVideo,
                    fileURL = NSURL.fileURLWithPath(path),
                    options = opcoes,
                )
            }) { ok, erro ->
                if (!ok) AppLogger.w(TAG, "fototeca recusou o arquivo (${erro?.code ?: "sem erro"})")
                if (cont.isActive) cont.resume(if (ok) GallerySaveResult.Saved else GallerySaveResult.Failed)
            }
        }
    }

    /** "Só adicionar" (iOS 14+): pede na primeira vez; `limited` também permite adicionar. */
    private suspend fun autorizado(): Boolean {
        val atual = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelAddOnly)
        val status = if (atual == PHAuthorizationStatusNotDetermined) {
            suspendCancellableCoroutine { cont ->
                PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelAddOnly) { s -> if (cont.isActive) cont.resume(s) }
            }
        } else {
            atual
        }
        return status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited
    }
}

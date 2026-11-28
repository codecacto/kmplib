@file:OptIn(ExperimentalForeignApi::class)

package br.com.codecacto.kmplib.ui.components

import kotlinx.cinterop.ExperimentalForeignApi
import platform.CoreFoundation.CFRetain
import platform.CoreFoundation.CFStringRef
import platform.Foundation.CFBridgingRelease
import platform.Foundation.timeIntervalSince1970
import platform.ImageIO.CGImageSourceCopyPropertiesAtIndex
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImagePropertyExifDateTimeDigitized
import platform.ImageIO.kCGImagePropertyExifDateTimeOriginal
import platform.ImageIO.kCGImagePropertyExifDictionary
import platform.ImageIO.kCGImagePropertyExifOffsetTime
import platform.ImageIO.kCGImagePropertyExifOffsetTimeDigitized
import platform.ImageIO.kCGImagePropertyExifOffsetTimeOriginal
import platform.ImageIO.kCGImagePropertyTIFFDateTime
import platform.ImageIO.kCGImagePropertyTIFFDictionary
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHPhotoLibrary
import kotlin.time.Instant

/**
 * A data da captura pelos metadados da imagem ORIGINAL, lidos pelo ImageIO
 * (`CGImageSourceCopyPropertiesAtIndex`, a forma oficial da Apple) — `{Exif}` `DateTimeOriginal` +
 * `OffsetTimeOriginal`, depois `DateTimeDigitized`, depois `{TIFF}` `DateTime`. Lido da MESMA fonte que
 * vai ser reduzida, antes da recodificação (o JPEG que sai não tem EXIF).
 */
internal fun CGImageSourceRef.photoTakenAtFromMetadata(): PhotoTakenAt? {
    val propriedades = CGImageSourceCopyPropertiesAtIndex(this, 0u, null) ?: return null
    // `Copy` = +1; o `CFBridgingRelease` passa a posse ao ARC e devolve o NSDictionary (= Map).
    val mapa = CFBridgingRelease(propriedades) as? Map<*, *> ?: return null
    val exif = mapa[chave(kCGImagePropertyExifDictionary)] as? Map<*, *>
    val tiff = mapa[chave(kCGImagePropertyTIFFDictionary)] as? Map<*, *>
    fun Map<*, *>?.texto(k: CFStringRef?): String? = this?.get(chave(k)) as? String
    return PhotoTakenAt.fromExif(
        dateTimeOriginal = exif.texto(kCGImagePropertyExifDateTimeOriginal),
        offsetTimeOriginal = exif.texto(kCGImagePropertyExifOffsetTimeOriginal),
        dateTimeDigitized = exif.texto(kCGImagePropertyExifDateTimeDigitized),
        offsetTimeDigitized = exif.texto(kCGImagePropertyExifOffsetTimeDigitized),
        dateTime = tiff.texto(kCGImagePropertyTIFFDateTime),
        offsetTime = exif.texto(kCGImagePropertyExifOffsetTime),
    )
}

/** Constante `CFStringRef` do ImageIO como `String` do Kotlin (chave do `Map` vindo do NSDictionary). */
private fun chave(k: CFStringRef?): String? = k?.let { CFBridgingRelease(CFRetain(it)) as? String }

/**
 * `PHAsset.creationDate` da foto escolhida no `PHPicker` (configurado com a fototeca, para trazer o
 * `assetIdentifier`). **Só lê se o app JÁ tem acesso de leitura** (total ou limitado): buscar um
 * `PHAsset` sem autorização faz o sistema pedir a permissão da fototeca, e a lib não pede permissão
 * nenhuma só por causa da data — o `PHPicker` existe justamente para não precisar dela.
 */
internal fun photoLibraryTakenAt(assetIdentifier: String?): PhotoTakenAt? {
    if (assetIdentifier.isNullOrBlank()) return null
    val acesso = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
    if (acesso != PHAuthorizationStatusAuthorized && acesso != PHAuthorizationStatusLimited) return null
    val ativo = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(assetIdentifier), null).firstObject as? PHAsset
        ?: return null
    val criada = ativo.creationDate ?: return null
    val instante = Instant.fromEpochMilliseconds((criada.timeIntervalSince1970 * 1000.0).toLong())
    return PhotoTakenAt.fromInstant(instante, PhotoTakenAtSource.PHOTO_LIBRARY)
}

/** A foto da galeria com a melhor data entre o EXIF dela e a fototeca ([preferredPhotoTakenAt]). */
internal fun PickedImage.withPhotoLibraryDate(assetIdentifier: String?): PickedImage {
    // EXIF do disparo já é a melhor fonte: não há por que tocar na fototeca.
    val atual = takenAt
    if (atual != null && atual.source != PhotoTakenAtSource.EXIF_MODIFIED) return this
    val melhor = preferredPhotoTakenAt(atual, photoLibraryTakenAt(assetIdentifier))
    return if (melhor == atual) this else copy(takenAt = melhor)
}

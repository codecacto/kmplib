package br.com.codecacto.kmplib.camera.barcode

import platform.AVFoundation.AVMetadataObjectTypeAztecCode
import platform.AVFoundation.AVMetadataObjectTypeCode128Code
import platform.AVFoundation.AVMetadataObjectTypeCode39Code
import platform.AVFoundation.AVMetadataObjectTypeCode93Code
import platform.AVFoundation.AVMetadataObjectTypeDataMatrixCode
import platform.AVFoundation.AVMetadataObjectTypeEAN13Code
import platform.AVFoundation.AVMetadataObjectTypeEAN8Code
import platform.AVFoundation.AVMetadataObjectTypeITF14Code
import platform.AVFoundation.AVMetadataObjectTypeInterleaved2of5Code
import platform.AVFoundation.AVMetadataObjectTypePDF417Code
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.AVMetadataObjectTypeUPCECode
import platform.Vision.VNBarcodeSymbologyAztec
import platform.Vision.VNBarcodeSymbologyCode128
import platform.Vision.VNBarcodeSymbologyCode39
import platform.Vision.VNBarcodeSymbologyCode93
import platform.Vision.VNBarcodeSymbologyDataMatrix
import platform.Vision.VNBarcodeSymbologyEAN13
import platform.Vision.VNBarcodeSymbologyEAN8
import platform.Vision.VNBarcodeSymbologyI2of5
import platform.Vision.VNBarcodeSymbologyITF14
import platform.Vision.VNBarcodeSymbologyPDF417
import platform.Vision.VNBarcodeSymbologyQR
import platform.Vision.VNBarcodeSymbologyUPCE

/**
 * Tradução entre [BarcodeFormat] e as simbologias da Apple (AVFoundation ao vivo, Vision em imagem
 * parada). Ponto único de mapeamento no iOS.
 *
 * Duas particularidades da plataforma, tratadas aqui:
 * - **UPC-A não existe** como simbologia própria na Apple. Um UPC-A é entregue como **EAN-13** com
 *   zero à esquerda — que é a forma canônica GS1 do mesmo item. Por isso [BarcodeFormat.UPC_A]
 *   liga o tipo EAN-13, e o resultado chega ao app como [BarcodeFormat.EAN_13];
 *   [ScannedBarcode.toGtin13] iguala as duas plataformas.
 * - **Codabar não é oferecido no iOS pela lib.** A constante só existe a partir do iOS 15.4 (e é
 *   fraca em SDKs anteriores); como nenhum produto do portfólio usa Codabar, preferimos declarar a
 *   ausência a arriscar um símbolo nulo em runtime.
 */
internal fun BarcodeFormat.toAvMetadataTypes(): List<String> = when (this) {
    BarcodeFormat.EAN_13, BarcodeFormat.UPC_A -> listOf(AVMetadataObjectTypeEAN13Code)
    BarcodeFormat.EAN_8 -> listOf(AVMetadataObjectTypeEAN8Code)
    BarcodeFormat.UPC_E -> listOf(AVMetadataObjectTypeUPCECode)
    BarcodeFormat.CODE_128 -> listOf(AVMetadataObjectTypeCode128Code)
    BarcodeFormat.CODE_39 -> listOf(AVMetadataObjectTypeCode39Code)
    BarcodeFormat.CODE_93 -> listOf(AVMetadataObjectTypeCode93Code)
    BarcodeFormat.ITF -> listOf(
        AVMetadataObjectTypeITF14Code,
        AVMetadataObjectTypeInterleaved2of5Code,
    )
    BarcodeFormat.QR_CODE -> listOf(AVMetadataObjectTypeQRCode)
    BarcodeFormat.DATA_MATRIX -> listOf(AVMetadataObjectTypeDataMatrixCode)
    BarcodeFormat.PDF_417 -> listOf(AVMetadataObjectTypePDF417Code)
    BarcodeFormat.AZTEC -> listOf(AVMetadataObjectTypeAztecCode)
    BarcodeFormat.CODABAR -> emptyList()
}

/** Tipo de metadata da AVFoundation → [BarcodeFormat]; `null` para o que a lib não modela. */
internal fun String.avMetadataTypeToBarcodeFormat(): BarcodeFormat? = when (this) {
    AVMetadataObjectTypeEAN13Code -> BarcodeFormat.EAN_13
    AVMetadataObjectTypeEAN8Code -> BarcodeFormat.EAN_8
    AVMetadataObjectTypeUPCECode -> BarcodeFormat.UPC_E
    AVMetadataObjectTypeCode128Code -> BarcodeFormat.CODE_128
    AVMetadataObjectTypeCode39Code -> BarcodeFormat.CODE_39
    AVMetadataObjectTypeCode93Code -> BarcodeFormat.CODE_93
    AVMetadataObjectTypeITF14Code, AVMetadataObjectTypeInterleaved2of5Code -> BarcodeFormat.ITF
    AVMetadataObjectTypeQRCode -> BarcodeFormat.QR_CODE
    AVMetadataObjectTypeDataMatrixCode -> BarcodeFormat.DATA_MATRIX
    AVMetadataObjectTypePDF417Code -> BarcodeFormat.PDF_417
    AVMetadataObjectTypeAztecCode -> BarcodeFormat.AZTEC
    else -> null
}

/** [BarcodeFormat] → simbologias do Vision (imagem parada). */
internal fun BarcodeFormat.toVisionSymbologies(): List<String> = when (this) {
    BarcodeFormat.EAN_13, BarcodeFormat.UPC_A -> listOf(VNBarcodeSymbologyEAN13)
    BarcodeFormat.EAN_8 -> listOf(VNBarcodeSymbologyEAN8)
    BarcodeFormat.UPC_E -> listOf(VNBarcodeSymbologyUPCE)
    BarcodeFormat.CODE_128 -> listOf(VNBarcodeSymbologyCode128)
    BarcodeFormat.CODE_39 -> listOf(VNBarcodeSymbologyCode39)
    BarcodeFormat.CODE_93 -> listOf(VNBarcodeSymbologyCode93)
    BarcodeFormat.ITF -> listOf(VNBarcodeSymbologyITF14, VNBarcodeSymbologyI2of5)
    BarcodeFormat.QR_CODE -> listOf(VNBarcodeSymbologyQR)
    BarcodeFormat.DATA_MATRIX -> listOf(VNBarcodeSymbologyDataMatrix)
    BarcodeFormat.PDF_417 -> listOf(VNBarcodeSymbologyPDF417)
    BarcodeFormat.AZTEC -> listOf(VNBarcodeSymbologyAztec)
    BarcodeFormat.CODABAR -> emptyList()
}

/** Simbologia do Vision → [BarcodeFormat]. */
internal fun String.visionSymbologyToBarcodeFormat(): BarcodeFormat? = when (this) {
    VNBarcodeSymbologyEAN13 -> BarcodeFormat.EAN_13
    VNBarcodeSymbologyEAN8 -> BarcodeFormat.EAN_8
    VNBarcodeSymbologyUPCE -> BarcodeFormat.UPC_E
    VNBarcodeSymbologyCode128 -> BarcodeFormat.CODE_128
    VNBarcodeSymbologyCode39 -> BarcodeFormat.CODE_39
    VNBarcodeSymbologyCode93 -> BarcodeFormat.CODE_93
    VNBarcodeSymbologyITF14, VNBarcodeSymbologyI2of5 -> BarcodeFormat.ITF
    VNBarcodeSymbologyQR -> BarcodeFormat.QR_CODE
    VNBarcodeSymbologyDataMatrix -> BarcodeFormat.DATA_MATRIX
    VNBarcodeSymbologyPDF417 -> BarcodeFormat.PDF_417
    VNBarcodeSymbologyAztec -> BarcodeFormat.AZTEC
    else -> null
}

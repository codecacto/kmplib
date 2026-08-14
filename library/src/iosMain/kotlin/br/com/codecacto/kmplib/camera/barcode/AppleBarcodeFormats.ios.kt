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
// `listOfNotNull` pelo mesmo motivo da função de simbologias abaixo: as constantes
// `AVMetadataObjectType*` também chegam como `String?` no Kotlin/Native.
internal fun BarcodeFormat.toAvMetadataTypes(): List<String> = when (this) {
    BarcodeFormat.EAN_13, BarcodeFormat.UPC_A -> listOfNotNull(AVMetadataObjectTypeEAN13Code)
    BarcodeFormat.EAN_8 -> listOfNotNull(AVMetadataObjectTypeEAN8Code)
    BarcodeFormat.UPC_E -> listOfNotNull(AVMetadataObjectTypeUPCECode)
    BarcodeFormat.CODE_128 -> listOfNotNull(AVMetadataObjectTypeCode128Code)
    BarcodeFormat.CODE_39 -> listOfNotNull(AVMetadataObjectTypeCode39Code)
    BarcodeFormat.CODE_93 -> listOfNotNull(AVMetadataObjectTypeCode93Code)
    BarcodeFormat.ITF -> listOfNotNull(
        AVMetadataObjectTypeITF14Code,
        AVMetadataObjectTypeInterleaved2of5Code,
    )
    BarcodeFormat.QR_CODE -> listOfNotNull(AVMetadataObjectTypeQRCode)
    BarcodeFormat.DATA_MATRIX -> listOfNotNull(AVMetadataObjectTypeDataMatrixCode)
    BarcodeFormat.PDF_417 -> listOfNotNull(AVMetadataObjectTypePDF417Code)
    BarcodeFormat.AZTEC -> listOfNotNull(AVMetadataObjectTypeAztecCode)
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
// `listOfNotNull`, e não `listOf`: as constantes `VNBarcodeSymbology*` do Vision chegam ao
// Kotlin/Native como `String?` (a Apple as declara sem anotação de nulidade), então `listOf` produz
// `List<String?>` e o iOS deixa de compilar com "Return type mismatch". Filtrar aqui é o certo: uma
// simbologia ausente no SDK da versão do Xcode simplesmente não entra na lista, em vez de virar um
// `null` que o Vision receberia.
internal fun BarcodeFormat.toVisionSymbologies(): List<String> = when (this) {
    BarcodeFormat.EAN_13, BarcodeFormat.UPC_A -> listOfNotNull(VNBarcodeSymbologyEAN13)
    BarcodeFormat.EAN_8 -> listOfNotNull(VNBarcodeSymbologyEAN8)
    BarcodeFormat.UPC_E -> listOfNotNull(VNBarcodeSymbologyUPCE)
    BarcodeFormat.CODE_128 -> listOfNotNull(VNBarcodeSymbologyCode128)
    BarcodeFormat.CODE_39 -> listOfNotNull(VNBarcodeSymbologyCode39)
    BarcodeFormat.CODE_93 -> listOfNotNull(VNBarcodeSymbologyCode93)
    BarcodeFormat.ITF -> listOfNotNull(VNBarcodeSymbologyITF14, VNBarcodeSymbologyI2of5)
    BarcodeFormat.QR_CODE -> listOfNotNull(VNBarcodeSymbologyQR)
    BarcodeFormat.DATA_MATRIX -> listOfNotNull(VNBarcodeSymbologyDataMatrix)
    BarcodeFormat.PDF_417 -> listOfNotNull(VNBarcodeSymbologyPDF417)
    BarcodeFormat.AZTEC -> listOfNotNull(VNBarcodeSymbologyAztec)
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

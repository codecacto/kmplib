package br.com.codecacto.kmplib.camera.barcode

import com.google.mlkit.vision.barcode.common.Barcode

/**
 * Tradução entre o [BarcodeFormat] da lib e as constantes do **ML Kit Barcode Scanning**.
 *
 * Ponto único de mapeamento, usado pelo preview ao vivo e pelo [BarcodeAnalyzer] — um formato novo
 * entra aqui e vale para os dois.
 */
internal fun BarcodeFormat.toMlKitFormat(): Int = when (this) {
    BarcodeFormat.EAN_13 -> Barcode.FORMAT_EAN_13
    BarcodeFormat.EAN_8 -> Barcode.FORMAT_EAN_8
    BarcodeFormat.UPC_A -> Barcode.FORMAT_UPC_A
    BarcodeFormat.UPC_E -> Barcode.FORMAT_UPC_E
    BarcodeFormat.CODE_128 -> Barcode.FORMAT_CODE_128
    BarcodeFormat.CODE_39 -> Barcode.FORMAT_CODE_39
    BarcodeFormat.CODE_93 -> Barcode.FORMAT_CODE_93
    BarcodeFormat.ITF -> Barcode.FORMAT_ITF
    BarcodeFormat.CODABAR -> Barcode.FORMAT_CODABAR
    BarcodeFormat.QR_CODE -> Barcode.FORMAT_QR_CODE
    BarcodeFormat.DATA_MATRIX -> Barcode.FORMAT_DATA_MATRIX
    BarcodeFormat.PDF_417 -> Barcode.FORMAT_PDF417
    BarcodeFormat.AZTEC -> Barcode.FORMAT_AZTEC
}

/** Formato do ML Kit → [BarcodeFormat]; `null` para simbologia fora do contrato da lib. */
internal fun Int.toBarcodeFormatOrNull(): BarcodeFormat? = when (this) {
    Barcode.FORMAT_EAN_13 -> BarcodeFormat.EAN_13
    Barcode.FORMAT_EAN_8 -> BarcodeFormat.EAN_8
    Barcode.FORMAT_UPC_A -> BarcodeFormat.UPC_A
    Barcode.FORMAT_UPC_E -> BarcodeFormat.UPC_E
    Barcode.FORMAT_CODE_128 -> BarcodeFormat.CODE_128
    Barcode.FORMAT_CODE_39 -> BarcodeFormat.CODE_39
    Barcode.FORMAT_CODE_93 -> BarcodeFormat.CODE_93
    Barcode.FORMAT_ITF -> BarcodeFormat.ITF
    Barcode.FORMAT_CODABAR -> BarcodeFormat.CODABAR
    Barcode.FORMAT_QR_CODE -> BarcodeFormat.QR_CODE
    Barcode.FORMAT_DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
    Barcode.FORMAT_PDF417 -> BarcodeFormat.PDF_417
    Barcode.FORMAT_AZTEC -> BarcodeFormat.AZTEC
    else -> null
}

/**
 * Converte a lista bruta do ML Kit em [ScannedBarcode] normalizados, descartando o que não fecha
 * (dígito verificador, simbologia fora do contrato, valor vazio).
 */
internal fun List<Barcode>.toScannedBarcodes(): List<ScannedBarcode> = mapNotNull { barcode ->
    val format = barcode.format.toBarcodeFormatOrNull() ?: return@mapNotNull null
    parseBarcode(barcode.rawValue, format)
}

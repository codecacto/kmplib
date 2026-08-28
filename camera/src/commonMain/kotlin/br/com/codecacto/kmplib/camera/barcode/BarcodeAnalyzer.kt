package br.com.codecacto.kmplib.camera.barcode

/**
 * Leitor de código de barras a partir de **bytes de imagem** (foto da galeria, arquivo recebido,
 * frame já capturado) — a peça de baixo nível, irmã do
 * [br.com.codecacto.kmplib.camera.PlateOcrAnalyzer] do OCR de placa.
 *
 * Para leitura **ao vivo** use [BarcodeScannerView] (tela pronta) ou [BarcodeCameraPreview]
 * (preview cru): ambos rodam no pipeline de câmera da plataforma, muito mais eficiente do que
 * codificar cada frame em bytes e passar por aqui.
 *
 * Padrão-ouro por plataforma:
 * - **Android:** **ML Kit Barcode Scanning** (`com.google.mlkit:barcode-scanning`, modelo
 *   embarcado — funciona offline, sem depender de download do Google Play Services).
 * - **iOS:** **Apple Vision** (`VNDetectBarcodesRequest`), a API oficial para detecção em imagem
 *   parada.
 *
 * **Best-effort — nunca lança:** imagem ilegível, formato não suportado ou falha do motor
 * resultam em lista vazia. Os valores devolvidos já passaram por [parseBarcode] (normalizados e
 * com dígito verificador conferido).
 *
 * ```kotlin
 * val analyzer = BarcodeAnalyzer()
 * val codigos = analyzer.analyze(bytesDaFoto)          // lista, possivelmente vazia
 * val ean = analyzer.analyzeFirst(bytesDaFoto)?.toGtin13()
 * analyzer.close()
 * ```
 */
expect class BarcodeAnalyzer() {

    /**
     * Lê [imageBytes] e devolve **todos** os códigos reconhecidos e válidos.
     *
     * @param formats simbologias a procurar. Peça só o que interessa — cada formato a mais é um
     *   decodificador a mais por imagem.
     */
    suspend fun analyze(
        imageBytes: ByteArray,
        formats: Set<BarcodeFormat> = BarcodeFormats.RETAIL,
    ): List<ScannedBarcode>

    /**
     * Libera o recurso nativo do motor de leitura. Chame quando o analyzer não for mais usado
     * (ex.: `onCleared()` do ViewModel).
     */
    fun close()
}

/**
 * Atalho para o caso comum: devolve o **primeiro** código válido encontrado, ou `null`.
 */
suspend fun BarcodeAnalyzer.analyzeFirst(
    imageBytes: ByteArray,
    formats: Set<BarcodeFormat> = BarcodeFormats.RETAIL,
): ScannedBarcode? = analyze(imageBytes, formats).firstOrNull()

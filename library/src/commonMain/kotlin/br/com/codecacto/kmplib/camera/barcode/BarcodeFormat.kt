package br.com.codecacto.kmplib.camera.barcode

/**
 * Simbologias de código de barras suportadas pelo módulo `camera/barcode`.
 *
 * A lista é a **interseção** do que os dois SDKs oficiais entregam no mesmo fluxo de leitura ao
 * vivo — **ML Kit Barcode Scanning** (Android) e **AVFoundation / Apple Vision** (iOS) —, para que
 * o mesmo `Set<BarcodeFormat>` produza o mesmo comportamento nas duas plataformas.
 *
 * ## Varejo (GTIN) × simbologias livres
 *
 * As quatro primeiras ([EAN_13], [EAN_8], [UPC_A], [UPC_E]) são **numéricas com dígito
 * verificador** (família GTIN): o valor lido é validado por [Gtin.isValid] antes de chegar ao app,
 * então um código parcial/borrado é descartado em vez de virar produto errado. As demais carregam
 * texto livre (etiqueta interna de loja, QR de campanha) e **não têm** checksum a conferir.
 *
 * ## Pegadinha do UPC-A no iOS (tratada pela lib)
 *
 * O iOS **não expõe UPC-A como simbologia própria** — um UPC-A é devolvido como [EAN_13] com um
 * zero à esquerda (`"0"` + 12 dígitos), que é a forma canônica GS1 do mesmo produto. O Android
 * (ML Kit) devolve os 12 dígitos e o tipo [UPC_A]. Por isso **nunca compare `format` para decidir
 * qual produto foi lido**: use [ScannedBarcode.toGtin13] / [ScannedBarcode.toGtin14], que
 * normalizam as duas plataformas para a mesma chave.
 *
 * @see BarcodeFormats presets prontos (`RETAIL`, `COMMON`, `ALL`).
 */
enum class BarcodeFormat {
    /** EAN-13 — 13 dígitos. Padrão de produto no varejo brasileiro. */
    EAN_13,

    /** EAN-8 — 8 dígitos. Embalagem pequena. */
    EAN_8,

    /** UPC-A — 12 dígitos (EUA). No iOS chega como [EAN_13] com zero à esquerda. */
    UPC_A,

    /** UPC-E — forma comprimida do UPC-A (8 dígitos). Expandida por [Gtin.expandUpcE]. */
    UPC_E,

    /** Code 128 — alfanumérico de tamanho livre. Etiqueta interna de loja/logística. */
    CODE_128,

    /** Code 39 — alfanumérico legado. */
    CODE_39,

    /** Code 93 — alfanumérico compacto. */
    CODE_93,

    /** Interleaved 2 of 5 — numérico de tamanho par; com 14 dígitos é um GTIN-14 (caixa). */
    ITF,

    /** Codabar — numérico legado (biblioteca, laboratório). */
    CODABAR,

    /** QR Code — 2D, texto livre. */
    QR_CODE,

    /** Data Matrix — 2D compacto. */
    DATA_MATRIX,

    /** PDF417 — 2D empilhado (documentos). */
    PDF_417,

    /** Aztec — 2D (bilhetes/transporte). */
    AZTEC;

    /**
     * `true` para as simbologias da família **GTIN** (varejo), cujo valor é numérico e tem
     * dígito verificador conferido pela lib.
     */
    val isRetail: Boolean
        get() = this == EAN_13 || this == EAN_8 || this == UPC_A || this == UPC_E
}

/**
 * Conjuntos prontos de [BarcodeFormat].
 *
 * **Peça só o que você vai ler.** Tanto o ML Kit quanto o AVFoundation orientam restringir as
 * simbologias: cada formato habilitado é um decodificador a mais rodando por frame, e a lista
 * inteira derruba a taxa de leitura justo onde ela importa (gôndola escura, uma mão só). Por isso
 * o default do [BarcodeScannerView] é [RETAIL], não [ALL].
 */
object BarcodeFormats {

    /** Varejo (GTIN): EAN-13, EAN-8, UPC-A e UPC-E. **Default do scanner.** */
    val RETAIL: Set<BarcodeFormat> = setOf(
        BarcodeFormat.EAN_13,
        BarcodeFormat.EAN_8,
        BarcodeFormat.UPC_A,
        BarcodeFormat.UPC_E,
    )

    /**
     * [RETAIL] + **Code 128** + **QR Code** — cobre a etiqueta interna que a loja imprime e o QR
     * de campanha, sem pagar o custo das simbologias 2D raras.
     */
    val COMMON: Set<BarcodeFormat> = RETAIL + setOf(
        BarcodeFormat.CODE_128,
        BarcodeFormat.QR_CODE,
    )

    /** Todas as simbologias suportadas. Use só quando o app realmente não souber o que virá. */
    val ALL: Set<BarcodeFormat> = BarcodeFormat.entries.toSet()
}

package br.com.codecacto.kmplib.camera.barcode

/**
 * Um código de barras **já lido, normalizado e validado** pela lib.
 *
 * O app nunca recebe o `rawValue` cru do SDK: antes de chegar aqui, o valor passou por
 * [parseBarcode], que remove espaços, expande UPC-E, confere o **dígito verificador** das
 * simbologias de varejo e descarta o que não fecha. Um código parcial/borrado vira `null` em vez
 * de virar produto errado no estoque.
 *
 * @property value valor normalizado. Para varejo, só dígitos (ex.: `"7891000100103"`); para as
 *   simbologias livres (QR, Code 128), o texto com as bordas aparadas.
 * @property format simbologia relatada pela plataforma. **Não use para identificar o produto** —
 *   o mesmo item chega como [BarcodeFormat.UPC_A] no Android e [BarcodeFormat.EAN_13] no iOS.
 *   Para a chave do catálogo, use [toGtin13] / [toGtin14].
 */
data class ScannedBarcode(
    val value: String,
    val format: BarcodeFormat,
) {
    /** `true` quando o código é da família GTIN (varejo) e, portanto, tem verificador conferido. */
    val isRetail: Boolean get() = format.isRetail

    /**
     * `true` quando este código identifica um **produto** e, portanto, serve de chave de catálogo
     * ([toGtin13]/[toGtin14]) — os quatro de varejo e o ITF-14 válido.
     */
    val isProductCode: Boolean
        get() = isRetail || (format == BarcodeFormat.ITF && Gtin.isValid(value))

    /**
     * Chave canônica de **13 dígitos** (GTIN-13) — o formato que os catálogos de produto do
     * ecossistema usam.
     *
     * UPC-E é expandido para UPC-A, e UPC-A/EAN-8 recebem zeros à esquerda (normalização GS1, que
     * **não** altera o dígito verificador). Um **ITF-14** válido (a caixa do produto) também é
     * aceito, encurtado apenas se o excesso forem zeros à esquerda — um GTIN-14 com indicador
     * diferente de zero é **outro item** e devolve `null` aqui (use [toGtin14]). Simbologia livre
     * (QR, Code 128) não identifica produto: devolve `null`.
     */
    fun toGtin13(): String? = toGtin(13)

    /**
     * Chave canônica de **14 dígitos** (GTIN-14) — a forma "de caixa" da GS1, útil quando o mesmo
     * catálogo mistura unidade de consumo e caixa (ITF-14). É a chave que **sempre** existe para
     * qualquer código de produto lido.
     */
    fun toGtin14(): String? = toGtin(14)

    private fun toGtin(length: Int): String? {
        val expanded = when {
            format == BarcodeFormat.UPC_E -> Gtin.expandUpcE(value) ?: return null
            format.isRetail -> value
            // ITF-14 é um GTIN legítimo (a caixa do produto). Simbologia livre (QR, Code 128) não
            // identifica produto e nunca vira chave de catálogo.
            format == BarcodeFormat.ITF && Gtin.isValid(value) -> value
            else -> return null
        }
        if (expanded.length > length) {
            // Encurtar só é lícito se o excesso for zero à esquerda (GTIN-14 "0"+EAN-13). Um
            // GTIN-14 com indicador != 0 é OUTRO item (a caixa), e devolver os 13 finais o
            // confundiria com a unidade de consumo.
            val excess = expanded.length - length
            if (!expanded.take(excess).all { it == '0' }) return null
            return expanded.drop(excess)
        }
        return Gtin.pad(expanded, length)
    }
}

/**
 * Converte o valor cru devolvido por um SDK de leitura ([rawValue]) no [ScannedBarcode]
 * normalizado, **ou `null` se o código não for plausível**.
 *
 * É o ponto ÚNICO de normalização — as implementações Android e iOS chamam esta função, de modo
 * que uma correção aqui vale para as duas plataformas.
 *
 * Regras por família:
 * - **Varejo (GTIN):** mantém só dígitos; UPC-E vira UPC-A; UPC-A com 13 dígitos e zero à esquerda
 *   (como o iOS devolve) é aceito; exige **dígito verificador válido** ([Gtin.isValid]).
 * - **ITF:** só dígitos e tamanho par; se tiver 14 dígitos (GTIN-14), o verificador é conferido.
 * - **Simbologias livres** (Code 128/39/93, Codabar, QR, Data Matrix, PDF417, Aztec): apara as
 *   bordas e exige texto não vazio — não há verificador a conferir.
 *
 * @param rawValue valor cru do SDK (pode ser `null`/vazio; nesse caso devolve `null`).
 * @param format simbologia relatada pela plataforma.
 */
fun parseBarcode(rawValue: String?, format: BarcodeFormat): ScannedBarcode? {
    val raw = rawValue?.trim().orEmpty()
    if (raw.isEmpty()) return null

    if (format.isRetail) {
        val digits = raw.filter { it in '0'..'9' }
        if (digits.length != raw.length) return null

        if (format == BarcodeFormat.UPC_E) {
            // expandUpcE já confere o dígito verificador declarado. O valor guardado é sempre a
            // forma canônica de 8 dígitos (sistema + corpo + verificador), e a expansão para
            // UPC-A/GTIN fica em toGtin13()/toGtin14().
            val expanded = Gtin.expandUpcE(digits) ?: return null
            val canonical = when (digits.length) {
                8 -> digits
                6 -> "0" + digits + expanded.last()
                else -> return null
            }
            return ScannedBarcode(canonical, BarcodeFormat.UPC_E)
        }

        val normalized = if (
            format == BarcodeFormat.UPC_A && digits.length == 13 && digits.first() == '0'
        ) {
            // iOS entrega UPC-A como 13 dígitos com zero à esquerda; ML Kit entrega 12.
            digits.drop(1)
        } else {
            digits
        }
        if (!Gtin.isValid(normalized)) return null
        return ScannedBarcode(normalized, format)
    }

    if (format == BarcodeFormat.ITF) {
        if (!raw.all { it in '0'..'9' }) return null
        if (raw.length % 2 != 0) return null
        if (raw.length == 14 && !Gtin.isValid(raw)) return null
        return ScannedBarcode(raw, format)
    }

    return ScannedBarcode(raw, format)
}

/**
 * Interpreta um código **digitado à mão** (fallback de teclado da tela de scanner) como um código
 * de varejo, inferindo a simbologia pelo tamanho.
 *
 * Existe porque toda tela de scanner do ecossistema tem — por exigência de produto — a entrada
 * manual ao lado da câmera (produto com etiqueta rasgada, luz impossível), e o app não deveria
 * reescrever a validação de dígito verificador no formulário.
 *
 * Ignora espaços, pontos e hífens que a pessoa digite. Devolve `null` quando o tamanho não
 * corresponde a nenhum GTIN ou quando o dígito verificador não fecha — nesse caso o app mostra
 * "código inválido" em vez de gravar lixo no estoque.
 *
 * **Ambiguidade declarada:** 8 dígitos podem ser um EAN-8 **ou** um UPC-E, e há valores que
 * satisfazem os dois. A preferência é **EAN-8** (o caso do varejo brasileiro), caindo para UPC-E
 * só quando o verificador de EAN-8 não fecha.
 *
 * ```kotlin
 * val lido = parseTypedRetailBarcode(campo) ?: return setState { copy(erro = texts.invalidCode) }
 * buscarProduto(lido.toGtin13()!!)
 * ```
 */
fun parseTypedRetailBarcode(text: String?): ScannedBarcode? {
    val digits = text?.filter { it in '0'..'9' }.orEmpty()
    val format = when (digits.length) {
        8 -> if (Gtin.isValid(digits)) BarcodeFormat.EAN_8 else BarcodeFormat.UPC_E
        12 -> BarcodeFormat.UPC_A
        13 -> BarcodeFormat.EAN_13
        14 -> BarcodeFormat.ITF
        else -> return null
    }
    return parseBarcode(digits, format)
}

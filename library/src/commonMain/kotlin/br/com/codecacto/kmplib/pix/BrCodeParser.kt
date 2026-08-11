package br.com.codecacto.kmplib.pix

/**
 * Por que um texto lido **não é** um BR Code.
 *
 * Deliberadamente separado de [BrCodeReading.InvalidCrc]: "não é EMV" é um QR de outro assunto
 * (link, texto, Wi-Fi, vCard, boleto), enquanto "CRC não confere" é um BR Code **adulterado ou
 * truncado** — o produto vai querer alertar diferente.
 */
enum class BrCodeError {

    /** Entrada nula, vazia ou só espaços. */
    Blank,

    /** O enquadramento TLV não fecha — ver [BrCodeReading.NotEmv.tlvError] para o detalhe. */
    InvalidFraming,

    /**
     * O payload não termina em `"6304" + CRC`.
     *
     * No EMV MPM a tag `63` é obrigatoriamente o **último** campo; um payload sem ela não é um BR
     * Code, e não há o que conferir.
     */
    MissingCrc,
}

/**
 * Resultado de ler o texto de um QR Code, com os quatro desfechos que o produto precisa distinguir.
 *
 * O contrato é: **nunca lança** e sempre devolve um destes casos. O insumo vem de uma câmera lendo
 * etiqueta suja, amassada, atrás de vidro — lixo é o caso normal, não a exceção.
 */
sealed interface BrCodeReading {

    /** O BR Code decodificado, quando houver algum (mesmo parcial — ver [InvalidCrc]). */
    val brCode: BrCode?

    /** **Íntegro e é Pix.** É o único caso em que se pode falar de "quem recebe" com confiança. */
    data class Pix(override val brCode: BrCode) : BrCodeReading

    /**
     * **Íntegro, é EMV MPM, mas não é Pix** — outro arranjo de pagamento, GUI diferente.
     *
     * Não é erro: o app deve exibir "este QR não é de Pix" (e o que der para exibir do recebedor),
     * em vez de tratar como leitura falhada.
     */
    data class NotPix(override val brCode: BrCode) : BrCodeReading

    /**
     * **O CRC não confere**: o payload foi truncado na leitura ou alterado depois de emitido.
     *
     * [brCode] traz o que foi possível decodificar **apenas como diagnóstico** (ajuda a explicar ao
     * usuário o que o QR parecia dizer). **Não use para decidir pagamento nem para cadastrar no
     * cofre** — o conteúdo não tem integridade comprovada. Vem `null` quando nem o enquadramento
     * pôde ser interpretado.
     */
    data class InvalidCrc(
        val declaredCrc: String,
        val computedCrc: String,
        override val brCode: BrCode?,
    ) : BrCodeReading

    /** **Não é EMV MPM.** [tlvError] detalha o enquadramento quando foi ele que reprovou. */
    data class NotEmv(
        val error: BrCodeError,
        val tlvError: EmvTlvError? = null,
    ) : BrCodeReading {
        override val brCode: BrCode? get() = null
    }
}

/** `true` só quando a leitura é um Pix íntegro. */
val BrCodeReading.isValidPix: Boolean get() = this is BrCodeReading.Pix

/**
 * `true` quando o texto **era** um BR Code, mas a integridade falhou.
 *
 * É o sinal para um alerta mais forte que "QR não reconhecido": alguém pode ter colado uma etiqueta
 * por cima, ou a plaquinha foi impressa a partir de um payload cortado.
 */
val BrCodeReading.isIntegrityFailure: Boolean get() = this is BrCodeReading.InvalidCrc

/** O BR Code quando a leitura é íntegra (Pix ou não), ignorando o parcial de [BrCodeReading.InvalidCrc]. */
val BrCodeReading.validBrCode: BrCode?
    get() = when (this) {
        is BrCodeReading.Pix -> brCode
        is BrCodeReading.NotPix -> brCode
        is BrCodeReading.InvalidCrc, is BrCodeReading.NotEmv -> null
    }

/**
 * **Ponto de entrada do módulo**: recebe o texto lido do QR e devolve um resultado tipado.
 *
 * ```kotlin
 * BarcodeScannerView(formats = BarcodeFormats.COMMON) { scanned ->
 *     when (val reading = parseBrCode(scanned.value)) {
 *         is BrCodeReading.Pix       -> mostrarRecebedor(reading.brCode)   // chave, nome, cidade
 *         is BrCodeReading.NotPix    -> avisar("Este QR não é de Pix")
 *         is BrCodeReading.InvalidCrc -> alertar("QR adulterado ou ilegível")
 *         is BrCodeReading.NotEmv    -> avisar("Não é um QR de pagamento")
 *     }
 * }
 * ```
 *
 * Ordem de avaliação, e o motivo de cada passo:
 *
 * 1. **Bordas aparadas** (espaço, `\n`, `\r`, `\t`, BOM, *zero-width*). Alguns leitores devolvem o
 *    payload com sobras nas pontas. **O interior nunca é tocado** — mexer nele mudaria o CRC e a
 *    identidade da plaquinha.
 * 2. **Enquadramento TLV** ([parseEmvTlv]): estrito na estrutura, tolerante com ID desconhecido.
 * 3. **CRC** ([PixCrc]): tem de ser o último campo e fechar. Falhar aqui é [BrCodeReading.InvalidCrc],
 *    nunca "não é EMV" — a diferença é a que separa adulteração de "QR de outro assunto".
 * 4. **Template Pix** (GUI `br.gov.bcb.pix`, comparada sem caixa): presente ⇒
 *    [BrCodeReading.Pix]; ausente ⇒ [BrCodeReading.NotPix].
 *
 * Nunca lança.
 */
fun parseBrCode(text: String?): BrCodeReading {
    val payload = normalizeBrCodePayload(text)
    if (payload.isEmpty()) return BrCodeReading.NotEmv(BrCodeError.Blank)

    val fields = when (val parsed = parseEmvTlv(payload)) {
        is EmvTlvResult.Success -> parsed.fields
        is EmvTlvResult.Failure ->
            return BrCodeReading.NotEmv(BrCodeError.InvalidFraming, parsed.error)
    }

    val declaredCrc = PixCrc.declaredCrcOf(payload)
        ?: return BrCodeReading.NotEmv(BrCodeError.MissingCrc)

    // O CRC cobre o payload inteiro INCLUINDO "6304" — só os 4 dígitos do valor ficam de fora.
    val computedCrc = PixCrc.compute(payload.dropLast(PixCrc.VALUE_LENGTH))
    val brCode = decodeBrCode(payload, fields, declaredCrc)

    if (declaredCrc != computedCrc) {
        return BrCodeReading.InvalidCrc(
            declaredCrc = declaredCrc,
            computedCrc = computedCrc,
            brCode = brCode,
        )
    }

    return if (brCode.isPix) BrCodeReading.Pix(brCode) else BrCodeReading.NotPix(brCode)
}

/**
 * Apara **somente as bordas** do texto lido: espaços, quebras de linha, tabs, BOM (`U+FEFF`) e
 * marcas invisíveis (`U+200B` *zero-width space*, `U+200E`/`U+200F` marcas de direção) que alguns
 * decodificadores acrescentam.
 *
 * O interior fica intacto por decisão de segurança: o CRC é calculado sobre os bytes exatos, e a
 * identidade de um QR estático **é** o payload inteiro. Uma "limpeza" no meio (colapsar espaço,
 * mudar caixa) poderia fazer dois payloads distintos virarem iguais — e "iguais" aqui significa
 * "plaquinha válida".
 */
fun normalizeBrCodePayload(text: String?): String =
    text?.trim { it.isWhitespace() || it in INVISIBLE_EDGE_CHARS }.orEmpty()

private val INVISIBLE_EDGE_CHARS =
    charArrayOf('\uFEFF', '\u200B', '\u200E', '\u200F')

private fun decodeBrCode(payload: String, fields: List<EmvField>, crc: String): BrCode {
    val initiationRaw = fields.emvValue(BrCodeTag.INITIATION_METHOD)

    return BrCode(
        payload = payload,
        formatIndicator = fields.emvValue(BrCodeTag.FORMAT_INDICATOR)?.cleanedOrNull(),
        initiationMethod = PixInitiationMethod.fromCode(initiationRaw),
        initiationMethodRaw = initiationRaw,
        accounts = pixAccountsOf(fields),
        merchantCategoryCode = fields.emvValue(BrCodeTag.MERCHANT_CATEGORY_CODE)?.cleanedOrNull(),
        currency = fields.emvValue(BrCodeTag.TRANSACTION_CURRENCY)?.cleanedOrNull(),
        amount = fields.emvValue(BrCodeTag.TRANSACTION_AMOUNT)?.cleanedOrNull(),
        countryCode = fields.emvValue(BrCodeTag.COUNTRY_CODE)?.cleanedOrNull(),
        merchantName = fields.emvValue(BrCodeTag.MERCHANT_NAME)?.cleanedOrNull(),
        merchantCity = fields.emvValue(BrCodeTag.MERCHANT_CITY)?.cleanedOrNull(),
        postalCode = fields.emvValue(BrCodeTag.POSTAL_CODE)?.cleanedOrNull(),
        txid = fields.emvField(BrCodeTag.ADDITIONAL_DATA)
            ?.childValue(BrCodeTag.ADDITIONAL_TXID)
            ?.cleanedOrNull(),
        crc = crc,
        fields = fields,
    )
}

/**
 * Todos os templates `26`–`51` cujo GUI é `br.gov.bcb.pix`.
 *
 * O normal é exatamente um. Mais de um significa um payload que anuncia o Pix duas vezes — a lib
 * devolve todos (é informação, não erro) e o [BrCode.account] usa o de menor ID, que é a ordem em
 * que o padrão manda escrever.
 */
private fun pixAccountsOf(fields: List<EmvField>): List<PixAccount> {
    val first = BrCodeTag.MERCHANT_ACCOUNT_FIRST.toInt()
    val last = BrCodeTag.MERCHANT_ACCOUNT_LAST.toInt()

    return fields
        .filter { field ->
            val id = field.id.toIntOrNull() ?: return@filter false
            id in first..last
        }
        .filter { field ->
            // Comparação sem caixa: o padrão manda minúsculo, mas há emissor que grava maiúsculo.
            field.childValue(BrCodeTag.ACCOUNT_GUI)?.trim()
                ?.equals(BrCodeTag.PIX_GUI, ignoreCase = true) == true
        }
        .sortedBy { it.id }
        .map { field ->
            PixAccount(
                templateId = field.id,
                gui = field.childValue(BrCodeTag.ACCOUNT_GUI).orEmpty(),
                key = field.childValue(BrCodeTag.ACCOUNT_KEY)?.cleanedOrNull(),
                description = field.childValue(BrCodeTag.ACCOUNT_DESCRIPTION)?.cleanedOrNull(),
                url = field.childValue(BrCodeTag.ACCOUNT_URL)?.cleanedOrNull(),
                fields = field.children,
            )
        }
}

/** Bordas aparadas para exibição; `null` quando não sobra nada. */
private fun String.cleanedOrNull(): String? = trim().ifEmpty { null }

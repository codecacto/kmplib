package br.com.codecacto.kmplib.pix

/**
 * Payloads de BR Code **sintéticos** para os testes do módulo `pix`.
 *
 * Nenhum dado real de comerciante: chaves, nomes e hosts são inventados (`CODECACTO SERVICOS`,
 * `pix.example.com`, CPF/CNPJ de teste).
 *
 * O CRC é sempre gerado por [PixCrc.sign] — a implementação sob teste. Isso **não** torna a suíte
 * auto-referente: a corretude do algoritmo é ancorada em `PixCrcTest`, que confere o *check value*
 * publicado do CRC-16/CCITT-FALSE (`"123456789"` → `0x29B1`) e o valor inicial (`""` → `0xFFFF`).
 * Provado o algoritmo contra a fonte externa, usá-lo para fechar as fixtures é o único jeito de
 * escrever payload válido sem colar CRC de QR de terceiro.
 */
internal object PixFixtures {

    const val CPF_KEY: String = "12345678901"
    const val CNPJ_KEY: String = "12345678000195"
    const val EMAIL_KEY: String = "pagamentos@example.com"
    const val PHONE_KEY: String = "+5511999998888"
    const val RANDOM_KEY: String = "123e4567-e89b-12d3-a456-426614174000"

    const val MERCHANT_NAME: String = "CODECACTO SERVICOS"
    const val MERCHANT_CITY: String = "SAO PAULO"

    /** Um campo TLV: `ID(2) + tamanho(2) + valor`. */
    fun tlv(id: String, value: String): String {
        require(value.length <= 99) { "EMV MPM não tem tamanho estendido: valor de ${value.length} chars" }
        return id + value.length.toString().padStart(2, '0') + value
    }

    /**
     * BR Code **estático** (payload fixo, o caso da plaquinha de balcão).
     *
     * @param initiation valor cru da tag `01`; `null` **omite a tag** (o default do padrão é
     *   estático, e omitir é justamente o caso que a lib precisa tratar).
     */
    fun staticPix(
        key: String = CPF_KEY,
        name: String = MERCHANT_NAME,
        city: String = MERCHANT_CITY,
        gui: String = BrCodeTag.PIX_GUI,
        amount: String? = null,
        txid: String = "***",
        initiation: String? = null,
        accountTemplateId: String = "26",
        description: String? = null,
        extraFields: String = "",
    ): String = buildBrCode(
        initiation = initiation,
        accountTemplateId = accountTemplateId,
        accountBody = buildString {
            append(tlv(BrCodeTag.ACCOUNT_GUI, gui))
            if (key.isNotEmpty()) append(tlv(BrCodeTag.ACCOUNT_KEY, key))
            if (description != null) append(tlv(BrCodeTag.ACCOUNT_DESCRIPTION, description))
        },
        name = name,
        city = city,
        amount = amount,
        txid = txid,
        extraFields = extraFields,
    )

    /** BR Code **dinâmico** (tag `01` = `12`, conta com URL de payload na sub-tag `25`). */
    fun dynamicPix(
        url: String,
        name: String = MERCHANT_NAME,
        city: String = MERCHANT_CITY,
        gui: String = BrCodeTag.PIX_GUI,
        amount: String? = null,
        txid: String = "***",
        initiation: String? = PixInitiationMethod.Dynamic.code,
        key: String? = null,
    ): String = buildBrCode(
        initiation = initiation,
        accountTemplateId = "26",
        accountBody = buildString {
            append(tlv(BrCodeTag.ACCOUNT_GUI, gui))
            if (key != null) append(tlv(BrCodeTag.ACCOUNT_KEY, key))
            append(tlv(BrCodeTag.ACCOUNT_URL, url))
        },
        name = name,
        city = city,
        amount = amount,
        txid = txid,
        extraFields = "",
    )

    /** EMV MPM válido de **outro arranjo** (GUI que não é Pix) — cidadão de primeira classe. */
    fun otherArrangement(
        name: String = "LOJA EXEMPLO",
        city: String = MERCHANT_CITY,
    ): String = buildBrCode(
        initiation = null,
        accountTemplateId = "27",
        accountBody = tlv(BrCodeTag.ACCOUNT_GUI, "com.outroarranjo") +
            tlv(BrCodeTag.ACCOUNT_KEY, "999888777"),
        name = name,
        city = city,
        amount = null,
        txid = "***",
        extraFields = "",
    )

    private fun buildBrCode(
        initiation: String?,
        accountTemplateId: String,
        accountBody: String,
        name: String,
        city: String,
        amount: String?,
        txid: String,
        extraFields: String,
    ): String {
        val body = buildString {
            append(tlv(BrCodeTag.FORMAT_INDICATOR, "01"))
            if (initiation != null) append(tlv(BrCodeTag.INITIATION_METHOD, initiation))
            append(tlv(accountTemplateId, accountBody))
            append(tlv(BrCodeTag.MERCHANT_CATEGORY_CODE, "0000"))
            append(tlv(BrCodeTag.TRANSACTION_CURRENCY, BrCodeTag.CURRENCY_BRL))
            if (amount != null) append(tlv(BrCodeTag.TRANSACTION_AMOUNT, amount))
            append(tlv(BrCodeTag.COUNTRY_CODE, BrCodeTag.COUNTRY_BR))
            append(tlv(BrCodeTag.MERCHANT_NAME, name))
            append(tlv(BrCodeTag.MERCHANT_CITY, city))
            append(tlv(BrCodeTag.ADDITIONAL_DATA, tlv(BrCodeTag.ADDITIONAL_TXID, txid)))
            append(extraFields)
        }
        return PixCrc.sign(body)
    }

    /** Troca o CRC do payload por um valor errado, preservando o enquadramento. */
    fun withBrokenCrc(payload: String): String {
        val declared = PixCrc.declaredCrcOf(payload) ?: error("payload sem CRC: $payload")
        val broken = if (declared == "0000") "FFFF" else "0000"
        return payload.dropLast(PixCrc.VALUE_LENGTH) + broken
    }
}

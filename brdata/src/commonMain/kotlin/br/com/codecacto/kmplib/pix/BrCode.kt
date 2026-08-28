package br.com.codecacto.kmplib.pix

/**
 * IDs de campo do BR Code (EMV MPM + Manual do BR Code / Bacen).
 *
 * Só os que a lib interpreta. Todo o resto continua acessível na árvore crua ([BrCode.fields]) —
 * a lib **preserva** ID que não conhece, em vez de descartar.
 */
object BrCodeTag {

    // --- primeiro nível -------------------------------------------------------------------------

    /** `00` — indicador de formato do payload (sempre `"01"` hoje). */
    const val FORMAT_INDICATOR: String = "00"

    /** `01` — método de iniciação: `11` estático/reutilizável, `12` dinâmico/uso único. */
    const val INITIATION_METHOD: String = "01"

    /** Primeiro ID da faixa de *Merchant Account Information*. */
    const val MERCHANT_ACCOUNT_FIRST: String = "26"

    /** Último ID da faixa de *Merchant Account Information*. */
    const val MERCHANT_ACCOUNT_LAST: String = "51"

    /** `52` — MCC (*Merchant Category Code*); `"0000"` = não informado. */
    const val MERCHANT_CATEGORY_CODE: String = "52"

    /** `53` — moeda em ISO 4217 numérico (`"986"` = BRL). */
    const val TRANSACTION_CURRENCY: String = "53"

    /** `54` — valor da transação, **opcional** (plaquinha de balcão em geral não tem valor). */
    const val TRANSACTION_AMOUNT: String = "54"

    /** `58` — país em ISO 3166-1 alpha-2 (`"BR"`). */
    const val COUNTRY_CODE: String = "58"

    /** `59` — nome do recebedor. */
    const val MERCHANT_NAME: String = "59"

    /** `60` — cidade do recebedor. */
    const val MERCHANT_CITY: String = "60"

    /** `61` — CEP do recebedor (opcional). */
    const val POSTAL_CODE: String = "61"

    /** `62` — *Additional Data Field Template* (carrega o `txid`). */
    const val ADDITIONAL_DATA: String = "62"

    /** `63` — CRC-16 (ver [PixCrc]). */
    const val CRC: String = "63"

    // --- dentro do template da conta (26–51) ----------------------------------------------------

    /** `00` — GUI do arranjo. Pix ⇒ [PIX_GUI]. */
    const val ACCOUNT_GUI: String = "00"

    /** `01` — chave Pix (caso estático). */
    const val ACCOUNT_KEY: String = "01"

    /** `02` — descrição/informação adicional do recebedor. */
    const val ACCOUNT_DESCRIPTION: String = "02"

    /** `25` — URL do payload (caso dinâmico). */
    const val ACCOUNT_URL: String = "25"

    // --- dentro do template de dados adicionais (62) --------------------------------------------

    /** `05` — `txid` (identificador da cobrança; `"***"` quando não informado). */
    const val ADDITIONAL_TXID: String = "05"

    /**
     * GUI que identifica o arranjo **Pix** dentro do template da conta.
     *
     * A comparação é **case-insensitive**: o padrão manda escrever em minúsculo, mas há emissor que
     * grava `BR.GOV.BCB.PIX` — e recusar por causa da caixa marcaria como "não é Pix" um QR
     * perfeitamente legítimo.
     */
    const val PIX_GUI: String = "br.gov.bcb.pix"

    /** ISO 4217 numérico do Real. */
    const val CURRENCY_BRL: String = "986"

    /** ISO 3166-1 alpha-2 do Brasil. */
    const val COUNTRY_BR: String = "BR"
}

/**
 * Método de iniciação do BR Code (tag `01`) — a distinção que **decide como comparar** duas
 * leituras (ver [PixIdentity]).
 */
enum class PixInitiationMethod(val code: String) {

    /**
     * `11` — **estático/reutilizável**. O payload é fixo: a mesma plaquinha produz sempre
     * exatamente os mesmos caracteres. Também é o valor assumido quando a tag `01` está **ausente**
     * (é o default do padrão).
     */
    Static("11"),

    /**
     * `12` — **dinâmico/uso único**. O payload aponta para uma **URL de payload** que o PSP troca a
     * cada cobrança. Duas leituras da MESMA plaquinha legítima trazem payloads diferentes.
     */
    Dynamic("12"),

    /**
     * Valor presente mas fora do padrão (nem `11` nem `12`).
     *
     * Não é normalizado para [Static]: se o regime é desconhecido, a lib não sabe qual regra de
     * comparação vale, e chutar produziria um "válido" sem base. A exibição de quem recebe continua
     * funcionando; só a comparação fica indisponível.
     */
    Unknown("");

    companion object {

        /**
         * Interpreta o valor cru da tag `01`.
         *
         * `null`/em branco ⇒ [Static] — **é o default do padrão**, não um chute: o BR Code sem tag
         * `01` é o estático clássico de plaquinha de balcão.
         */
        fun fromCode(raw: String?): PixInitiationMethod {
            val code = raw?.trim()
            if (code.isNullOrEmpty()) return Static
            return entries.firstOrNull { it != Unknown && it.code == code } ?: Unknown
        }
    }
}

/**
 * Tipo de chave Pix, inferido **pelo formato**.
 *
 * A inferência é só para **exibir** ("quem recebe é o CNPJ 12.345.678/0001-95") e para dar contexto
 * à comparação. **Não há validação de dígito verificador de CPF/CNPJ como critério de rejeição**, de
 * propósito: chave registrada com DV inválido é problema do PSP que a aceitou, e recusar aqui
 * esconderia do usuário justamente o QR que ele precisa ver.
 */
enum class PixKeyType {

    /** 11 dígitos. */
    CPF,

    /** 14 dígitos. */
    CNPJ,

    /** Contém `@` e formato de e-mail. */
    EMAIL,

    /**
     * Formato `+55DDNNNNNNNNN` — o padrão Pix exige o `+` e o DDI. Máscara (espaço, `-`, `()`) é
     * tolerada, porque existe emissor que grava formatado.
     */
    PHONE,

    /** Chave aleatória (EVP) — UUID. */
    RANDOM,

    /** Ausente ou fora de qualquer formato conhecido. */
    UNKNOWN,
}

/**
 * Infere o [PixKeyType] pelo formato da chave.
 *
 * **Ambiguidade conhecida:** um telefone escrito sem DDI (`11999998888`) tem os mesmos 11 dígitos de
 * um CPF. O padrão Pix registra telefone **sempre** com `+55`, então 11 dígitos são lidos como
 * [PixKeyType.CPF]. É a leitura correta na esmagadora maioria e, no pior caso, erra só o **rótulo**
 * exibido — nunca a comparação, que usa a chave inteira.
 */
fun inferPixKeyType(key: String?): PixKeyType {
    val value = key?.trim().orEmpty()
    if (value.isEmpty()) return PixKeyType.UNKNOWN

    if (value.contains('@')) {
        return if (looksLikeEmail(value)) PixKeyType.EMAIL else PixKeyType.UNKNOWN
    }

    if (value.startsWith('+')) {
        // Formatação de telefone é tolerada (`+55 11 99999-8888`): o padrão manda gravar só
        // dígitos, mas emissor com máscara existe, e o tipo não pode depender disso.
        val body = value.drop(1)
        if (body.any { it !in '0'..'9' && it !in PHONE_PUNCTUATION }) return PixKeyType.UNKNOWN
        val digits = body.filter { it in '0'..'9' }
        // DDI + DDD + número: o Pix trabalha com 12 a 14 dígitos depois do "+".
        return if (digits.length in 12..14) PixKeyType.PHONE else PixKeyType.UNKNOWN
    }

    if (looksLikeUuid(value)) return PixKeyType.RANDOM

    // CPF/CNPJ: o padrão manda gravar só dígitos, mas máscara (`123.456.789-01`) existe no mundo
    // real. Tolerar a pontuação não aproxima documentos distintos — a contagem de dígitos é a mesma.
    if (value.all { it in '0'..'9' || it in DOCUMENT_PUNCTUATION }) {
        return when (value.count { it in '0'..'9' }) {
            11 -> PixKeyType.CPF
            14 -> PixKeyType.CNPJ
            else -> PixKeyType.UNKNOWN
        }
    }

    return PixKeyType.UNKNOWN
}

/** Pontuação aceita numa chave de CPF/CNPJ formatada por emissor fora do padrão. */
private val DOCUMENT_PUNCTUATION = charArrayOf('.', '-', '/')

/** Pontuação aceita numa chave de telefone formatada por emissor fora do padrão. */
private val PHONE_PUNCTUATION = charArrayOf(' ', '-', '(', ')', '.')

private fun looksLikeEmail(value: String): Boolean {
    val at = value.indexOf('@')
    if (at <= 0 || at != value.lastIndexOf('@')) return false
    val local = value.substring(0, at)
    val domain = value.substring(at + 1)
    if (domain.length < 3 || !domain.contains('.')) return false
    if (domain.startsWith('.') || domain.endsWith('.')) return false
    if (value.any { it.isWhitespace() }) return false
    return local.isNotEmpty()
}

private fun looksLikeUuid(value: String): Boolean {
    if (value.length != 36) return false
    val groups = value.split('-')
    if (groups.size != 5) return false
    val expected = intArrayOf(8, 4, 4, 4, 12)
    groups.forEachIndexed { index, group ->
        if (group.length != expected[index]) return false
        if (!group.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return false
    }
    return true
}

/**
 * O template de conta do recebedor (`26`–`51`) do arranjo **Pix**, decodificado.
 *
 * O que está aqui é o que responde "**quem recebe?**" — a informação que permite desconfiar de uma
 * plaquinha **mesmo nunca cadastrada**.
 */
data class PixAccount(

    /**
     * Em qual ID da faixa `26`–`51` o template Pix estava.
     *
     * Faz parte do payload e, portanto, da identidade de um QR estático; guardado também porque um
     * BR Code pode conter mais de um arranjo, e é útil saber de onde o dado veio.
     */
    val templateId: String,

    /** GUI como veio no payload (não normalizado — serve de diagnóstico). */
    val gui: String,

    /** `01` — chave Pix. Ausente no caso dinâmico puro. */
    val key: String?,

    /** `02` — descrição/informação adicional definida pelo recebedor. */
    val description: String?,

    /**
     * `25` — URL do payload (caso dinâmico). Vem tipicamente **sem** esquema
     * (`pix.example.com/qr/v2/abc123`).
     */
    val url: String?,

    /** Sub-campos crus do template, incluindo os que a lib não interpreta. */
    val fields: List<EmvField>,
) {

    /** Tipo da chave, inferido pelo formato — ver [inferPixKeyType]. */
    val keyType: PixKeyType get() = inferPixKeyType(key)

    /** `true` quando há chave Pix legível. */
    val hasKey: Boolean get() = !key.isNullOrBlank()

    /** `true` quando há URL de payload (indício do caso dinâmico). */
    val hasUrl: Boolean get() = !url.isNullOrBlank()
}

/**
 * Um **BR Code** decodificado: o payload EMV MPM já com enquadramento validado e CRC conferido.
 *
 * O nome é "BR Code" e não "Pix" porque o formato é do arranjo brasileiro em geral: um payload pode
 * ser EMV MPM válido **sem** ser Pix (outro arranjo, GUI diferente). Nesse caso [account] é `null` e
 * a leitura chega ao app como [BrCodeReading.NotPix] — é um cidadão de primeira classe, não um erro.
 *
 * Os campos de texto ([merchantName], [merchantCity], …) vêm com as bordas aparadas, para exibição.
 * O [payload] é o texto **exato** que foi validado — e é ele, e só ele, que serve de identidade de um
 * QR estático (ver [PixIdentity]).
 */
data class BrCode(

    /**
     * O payload exatamente como validado (bordas aparadas, interior intocado).
     *
     * Não reformate nem "limpe" este valor: qualquer caractere alterado invalida o CRC e muda a
     * identidade da plaquinha.
     */
    val payload: String,

    /** `00` — indicador de formato (`"01"`). */
    val formatIndicator: String?,

    /** `01` interpretado. Ausente ⇒ [PixInitiationMethod.Static] (default do padrão). */
    val initiationMethod: PixInitiationMethod,

    /** Valor cru da tag `01` (`null` quando ausente) — para diagnóstico de emissor fora do padrão. */
    val initiationMethodRaw: String?,

    /** Todos os templates de conta com GUI Pix (o normal é exatamente um). */
    val accounts: List<PixAccount>,

    /** `52` — MCC. */
    val merchantCategoryCode: String?,

    /** `53` — moeda ISO 4217 numérico (`"986"` = BRL). */
    val currency: String?,

    /**
     * `54` — valor da transação, como **string decimal** (`"12.34"`), ou `null` quando o QR não fixa
     * valor (o caso da plaquinha de balcão).
     *
     * Fica como string de propósito: dinheiro em `Double` é proibido no ecossistema. Para calcular,
     * converta com `Money.toCents(...)` (`core/money`).
     */
    val amount: String?,

    /** `58` — país. */
    val countryCode: String?,

    /** `59` — nome do recebedor. */
    val merchantName: String?,

    /** `60` — cidade do recebedor. */
    val merchantCity: String?,

    /** `61` — CEP do recebedor. */
    val postalCode: String?,

    /** `62`/`05` — `txid`. `"***"` significa "não informado" para o PSP. */
    val txid: String?,

    /** `63` — CRC declarado, em hex maiúsculo (já conferido quando este objeto existe). */
    val crc: String,

    /** Árvore TLV completa, com os IDs que a lib não interpreta preservados. */
    val fields: List<EmvField>,
) {

    /** O template Pix (o primeiro, quando há mais de um). `null` ⇒ é EMV, mas não é Pix. */
    val account: PixAccount? get() = accounts.firstOrNull()

    /** `true` quando há template com GUI `br.gov.bcb.pix`. */
    val isPix: Boolean get() = accounts.isNotEmpty()

    /** Chave Pix, quando houver. */
    val pixKey: String? get() = account?.key

    /** Tipo da chave Pix (ver [inferPixKeyType]). */
    val pixKeyType: PixKeyType get() = account?.keyType ?: PixKeyType.UNKNOWN

    /** URL do payload dinâmico, quando houver. */
    val payloadUrl: String? get() = account?.url

    /** `true` quando o payload é fixo (estático/reutilizável). */
    val isStatic: Boolean get() = initiationMethod == PixInitiationMethod.Static

    /** `true` quando o payload aponta para uma URL de cobrança que muda a cada uso. */
    val isDynamic: Boolean get() = initiationMethod == PixInitiationMethod.Dynamic

    /** `true` quando o QR fixa um valor. */
    val hasAmount: Boolean get() = !amount.isNullOrBlank()

    /** Valor cru de um campo de primeiro nível — inclusive os que a lib não interpreta. */
    fun rawValue(tag: String): String? = fields.emvValue(tag)
}

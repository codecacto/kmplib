package br.com.codecacto.kmplib.pix

import br.com.codecacto.kmplib.brdata.removeAccents
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * O recebedor de um BR Code, **normalizado para comparação**.
 *
 * Os valores aqui NÃO servem para exibir — para a tela, use [BrCode.merchantName]/
 * [BrCode.merchantCity]/[BrCode.pixKey], que preservam o texto como o emissor escreveu. Aqui os
 * campos já passaram pela normalização de comparação ([normalizePixKeyForCompare] e
 * [normalizePixTextForCompare]), que é conservadora de propósito: ela apara diferença de
 * apresentação (caixa, espaço duplicado, acento, pontuação de CPF/CNPJ) e **nada além disso**, para
 * que dois recebedores realmente distintos nunca colidam. Colidir aqui significaria dar "válido" a
 * um QR de golpista.
 */
@Serializable
data class PixReceiver(

    /** Chave Pix normalizada (vazia quando o QR não traz chave — o caso dinâmico puro). */
    val key: String = "",

    /** Tipo inferido da chave (rótulo; não participa da decisão de igualdade). */
    val keyType: PixKeyType = PixKeyType.UNKNOWN,

    /** Nome do recebedor normalizado. */
    val name: String = "",

    /** Cidade do recebedor normalizada. */
    val city: String = "",
) {

    /** `true` quando não há nenhum dado de recebedor — nada a comparar. */
    val isEmpty: Boolean get() = key.isEmpty() && name.isEmpty() && city.isEmpty()
}

/**
 * O endereço **estável** de uma cobrança dinâmica: o host da URL de payload e o prefixo de caminho.
 *
 * O que muda a cada cobrança é o último segmento do caminho (o identificador da cobrança) e,
 * eventualmente, a query. O que **não** muda numa plaquinha legítima é o host e o caminho até ali —
 * é por isso que a identidade de um QR dinâmico se ancora aqui, e não no payload.
 */
@Serializable
data class PixEndpoint(

    /**
     * Host em minúsculas, sem esquema e sem *userinfo*. Inclui a porta quando ela vem explícita na
     * URL (`pix.example.com:8443`) — a porta faz parte do endereço.
     */
    val host: String,

    /** Caminho sem o último segmento (que é o id da cobrança). Vazio quando não há mais de um. */
    val pathPrefix: String = "",
)

/**
 * A **identidade de uma plaquinha** — o valor que o app guarda no cadastro e compara na ronda.
 *
 * Existem dois regimes, e a diferença entre eles é a razão de esta classe existir:
 *
 * - [Static] — o payload é **fixo**. A identidade é o payload inteiro; igualdade exata resolve.
 * - [Dynamic] — o payload aponta para uma **URL de cobrança que muda a cada uso**. Igualdade exata
 *   **nunca** casa, e é aqui que o erro clássico acontece.
 *
 * ### O erro clássico: comparar payload cru de QR dinâmico
 *
 * Um app ingênuo guarda a string lida no cadastro e, na ronda, compara com a string lida agora. Num
 * QR **estático** isso funciona. Num QR **dinâmico** o payload muda a cada cobrança emitida pelo PSP
 * (a URL da tag `25` carrega um identificador novo), então a comparação **nunca** casa e **toda
 * plaquinha legítima é marcada como fraude**. O resultado prático é pior que não ter a checagem: o
 * funcionário aprende que o app "dá erro sempre" e passa a ignorar o alerta — inclusive o alerta
 * verdadeiro, no dia em que a plaquinha for realmente trocada.
 *
 * Por isso a identidade dinâmica é derivada do que é estável: **host + prefixo de caminho +
 * recebedor** ([PixEndpoint] + [PixReceiver]).
 *
 * ### Persistência
 *
 * `@Serializable`. Guarde com [encode] e leia com [decode] — o formato tem discriminador `type`
 * (`"static"`/`"dynamic"`) estável entre versões. Não há hash: o valor guardado é legível e
 * auditável, o que também permite ao app exibir *por que* duas leituras divergiram.
 */
@Serializable
sealed interface PixIdentity {

    /** Recebedor normalizado, comum aos dois regimes. */
    val receiver: PixReceiver

    /** Regime desta identidade. */
    val initiationMethod: PixInitiationMethod
        get() = when (this) {
            is Static -> PixInitiationMethod.Static
            is Dynamic -> PixInitiationMethod.Dynamic
        }

    /**
     * QR **estático**: o payload é fixo, então ele **é** a identidade.
     *
     * [payload] é o texto exato validado por `parseBrCode` (bordas aparadas, interior intocado).
     * Igualdade é caractere a caractere: nada é "relaxado" aqui, porque relaxar seria aceitar como
     * igual um payload que não é.
     */
    @Serializable
    @SerialName("static")
    data class Static(
        val payload: String,
        override val receiver: PixReceiver,
    ) : PixIdentity

    /**
     * QR **dinâmico**: identidade = endereço estável da cobrança + recebedor.
     *
     * Nunca compare [PixIdentity.Static.payload] com o payload de um dinâmico, e nunca guarde o
     * payload de um dinâmico como identidade — ver a nota sobre o erro clássico em [PixIdentity].
     */
    @Serializable
    @SerialName("dynamic")
    data class Dynamic(
        val endpoint: PixEndpoint,
        override val receiver: PixReceiver,
    ) : PixIdentity {

        /** Atalho para `endpoint.host`. */
        val host: String get() = endpoint.host

        /** Atalho para `endpoint.pathPrefix`. */
        val pathPrefix: String get() = endpoint.pathPrefix
    }

    companion object {

        /** Serializa a identidade para guardar no cadastro (JSON com discriminador `type`). */
        fun encode(identity: PixIdentity): String =
            pixIdentityJson.encodeToString(serializer(), identity)

        /**
         * Lê uma identidade guardada. Devolve `null` para texto nulo/vazio/corrompido — **nunca
         * lança**: cadastro gravado por versão antiga ou registro truncado não pode derrubar a ronda.
         */
        fun decode(text: String?): PixIdentity? {
            val json = text?.trim().orEmpty()
            if (json.isEmpty()) return null
            return runCatching { pixIdentityJson.decodeFromString(serializer(), json) }.getOrNull()
        }
    }
}

private val pixIdentityJson = Json {
    classDiscriminator = "type"
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** Por que duas leituras não podem ser comparadas. Não é divergência — é falta de base. */
enum class PixNotComparableReason {

    /** Não há identidade cadastrada para comparar (plaquinha nunca cadastrada). */
    NothingRegistered,

    /** O texto lido não é um BR Code. */
    ScannedNotEmv,

    /** O texto lido é um BR Code com **CRC inválido** — adulterado ou truncado. */
    ScannedInvalidCrc,

    /** O texto lido é EMV MPM, mas não é Pix. */
    ScannedNotPix,

    /**
     * O método de iniciação (tag `01`) tem valor fora do padrão.
     *
     * Sem saber o regime, não há regra de comparação aplicável. Fail-closed de propósito: assumir
     * "estático" produziria um veredito sem base.
     */
    UnknownInitiationMethod,

    /**
     * QR dinâmico cuja URL de payload está ausente ou ilegível.
     *
     * Sem host não há nada estável em que ancorar a identidade.
     */
    UnusablePayloadUrl,
}

/** Campo do recebedor que divergiu. */
enum class PixReceiverField {
    KEY,
    NAME,
    CITY,
}

/**
 * O veredito da comparação **com o motivo** — nunca um `Boolean` mudo.
 *
 * O motivo não é luxo de API: "o nome do recebedor é outro" é a frase que faz o funcionário parar o
 * pagamento, e "não deu para comparar" é o oposto de "divergente". Um booleano obrigaria a tela a
 * escolher entre alarmar de menos e alarmar de mais.
 *
 * Precedência (do mais grave ao menos), aplicada por [comparePix]:
 * [NotComparable] → [ReceiverChanged] → [RegimeChanged] → [EndpointChanged] → [PayloadChanged] →
 * [SamePlaque].
 */
sealed interface PixComparison {

    /** **Mesma plaquinha.** Tudo o que deveria ser estável está igual. */
    data object SamePlaque : PixComparison

    /**
     * **Recebedor diferente** — o caso que interessa ao produto: o dinheiro iria para outra conta.
     *
     * [changed] diz exatamente o que mudou, para a mensagem ser específica ("a chave Pix é outra" é
     * mais forte que "divergente").
     */
    data class ReceiverChanged(
        val expected: PixReceiver,
        val found: PixReceiver,
        val changed: Set<PixReceiverField>,
    ) : PixComparison

    /**
     * **Endereço de cobrança diferente** (QR dinâmico): host e/ou prefixo de caminho não batem.
     *
     * Host diferente com recebedor aparentemente igual é o padrão de um QR falso que imita o nome do
     * comerciante — o dado do recebedor é texto livre escolhido por quem emitiu o QR, o host não.
     */
    data class EndpointChanged(
        val expected: PixEndpoint,
        val found: PixEndpoint,
    ) : PixComparison {

        /** `true` quando o próprio host mudou (e não apenas o caminho). */
        val hostChanged: Boolean get() = expected.host != found.host
    }

    /**
     * **Payload diferente** num QR estático, com o mesmo recebedor.
     *
     * Não é o cenário de golpe clássico (o dinheiro vai para a mesma conta), mas a plaquinha **não é
     * a cadastrada**: pode ser reemissão legítima do PSP, ou um QR do mesmo recebedor com valor fixo
     * embutido. Merece "confira", não "válido".
     */
    data class PayloadChanged(
        val expectedPayload: String,
        val foundPayload: String,
    ) : PixComparison

    /**
     * **Regime diferente**: cadastrou-se um estático e leu-se um dinâmico (ou vice-versa).
     *
     * A plaquinha foi substituída por outra de natureza diferente. Só é reportado quando o recebedor
     * bate — recebedor diferente tem precedência, por ser mais grave.
     */
    data class RegimeChanged(
        val expected: PixInitiationMethod,
        val found: PixInitiationMethod,
    ) : PixComparison

    /** **Não comparável** — falta base. Ver [PixNotComparableReason]. */
    data class NotComparable(val reason: PixNotComparableReason) : PixComparison
}

/** `true` só no veredito "mesma plaquinha". */
val PixComparison.isMatch: Boolean get() = this is PixComparison.SamePlaque

/** `true` quando houve divergência de fato (exclui [PixComparison.NotComparable]). */
val PixComparison.isDivergent: Boolean
    get() = when (this) {
        is PixComparison.ReceiverChanged,
        is PixComparison.EndpointChanged,
        is PixComparison.PayloadChanged,
        is PixComparison.RegimeChanged -> true
        is PixComparison.SamePlaque, is PixComparison.NotComparable -> false
    }

/** Resultado de derivar a identidade de uma leitura. */
sealed interface PixIdentityResult {

    /** Identidade derivada — é isto que o app guarda no cadastro. */
    data class Available(val identity: PixIdentity) : PixIdentityResult

    /** Não foi possível derivar; [reason] é o mesmo vocabulário de [PixComparison.NotComparable]. */
    data class Unavailable(val reason: PixNotComparableReason) : PixIdentityResult
}

/** A identidade quando derivável, `null` caso contrário. */
val PixIdentityResult.identityOrNull: PixIdentity?
    get() = (this as? PixIdentityResult.Available)?.identity

/**
 * O recebedor normalizado de um BR Code — o que entra na comparação.
 *
 * Chave, nome e cidade passam por normalização de apresentação apenas (ver [PixReceiver]).
 */
fun pixReceiverOf(brCode: BrCode): PixReceiver {
    val rawKey = brCode.pixKey
    val keyType = inferPixKeyType(rawKey)
    return PixReceiver(
        key = normalizePixKeyForCompare(rawKey, keyType),
        keyType = keyType,
        name = normalizePixTextForCompare(brCode.merchantName),
        city = normalizePixTextForCompare(brCode.merchantCity),
    )
}

/**
 * Deriva a identidade de um BR Code **íntegro**.
 *
 * Estático ⇒ [PixIdentity.Static] com o payload inteiro. Dinâmico ⇒ [PixIdentity.Dynamic] com o
 * endereço estável extraído da URL de payload.
 *
 * Casos fail-closed (devolvem [PixIdentityResult.Unavailable] em vez de chutar):
 * BR Code que não é Pix, método de iniciação fora do padrão, e dinâmico sem URL legível.
 *
 * Estático que **também** traz URL é tratado como estático: o payload é fixo, e igualdade exata é a
 * comparação mais forte disponível.
 */
fun pixIdentityOf(brCode: BrCode): PixIdentityResult {
    if (!brCode.isPix) {
        return PixIdentityResult.Unavailable(PixNotComparableReason.ScannedNotPix)
    }

    val receiver = pixReceiverOf(brCode)

    return when (brCode.initiationMethod) {
        PixInitiationMethod.Static ->
            PixIdentityResult.Available(
                PixIdentity.Static(payload = brCode.payload, receiver = receiver),
            )

        PixInitiationMethod.Dynamic -> {
            val endpoint = pixEndpointOf(brCode.payloadUrl)
                ?: return PixIdentityResult.Unavailable(PixNotComparableReason.UnusablePayloadUrl)
            PixIdentityResult.Available(
                PixIdentity.Dynamic(endpoint = endpoint, receiver = receiver),
            )
        }

        PixInitiationMethod.Unknown ->
            PixIdentityResult.Unavailable(PixNotComparableReason.UnknownInitiationMethod)
    }
}

/**
 * Deriva a identidade a partir do resultado da leitura — **é o que o cadastro usa**.
 *
 * CRC inválido nunca produz identidade: cadastrar um payload sem integridade comprovada gravaria a
 * plaquinha errada no cofre, e todo o resto passaria a comparar contra ela.
 */
fun pixIdentityOf(reading: BrCodeReading): PixIdentityResult = when (reading) {
    is BrCodeReading.Pix -> pixIdentityOf(reading.brCode)
    is BrCodeReading.NotPix -> PixIdentityResult.Unavailable(PixNotComparableReason.ScannedNotPix)
    is BrCodeReading.InvalidCrc ->
        PixIdentityResult.Unavailable(PixNotComparableReason.ScannedInvalidCrc)
    is BrCodeReading.NotEmv -> PixIdentityResult.Unavailable(PixNotComparableReason.ScannedNotEmv)
}

/** Açúcar de [pixIdentityOf] para quem não precisa do motivo. */
fun BrCodeReading.pixIdentityOrNull(): PixIdentity? = pixIdentityOf(this).identityOrNull

/**
 * Compara a plaquinha cadastrada com o que a câmera acabou de ler.
 *
 * É a API que a tela de ronda usa:
 *
 * ```kotlin
 * val cadastrada = PixIdentity.decode(plaquinha.identidadeJson)
 * when (val veredito = comparePix(cadastrada, parseBrCode(scanned.value))) {
 *     PixComparison.SamePlaque         -> marcarValido()
 *     is PixComparison.ReceiverChanged -> alertar("Quem recebe é outro: ${veredito.changed}")
 *     is PixComparison.EndpointChanged -> alertar("O QR aponta para outro endereço")
 *     is PixComparison.PayloadChanged  -> avisar("Mesmo recebedor, mas o código mudou")
 *     is PixComparison.RegimeChanged   -> avisar("A plaquinha mudou de tipo")
 *     is PixComparison.NotComparable   -> avisar(motivo(veredito.reason))
 * }
 * ```
 *
 * `registered == null` ⇒ [PixNotComparableReason.NothingRegistered] (plaquinha não cadastrada — o
 * app ainda exibe "quem recebe" a partir da leitura, que é o diferencial do produto).
 *
 * Nunca lança.
 */
fun comparePix(registered: PixIdentity?, scanned: BrCodeReading): PixComparison {
    if (registered == null) {
        return PixComparison.NotComparable(PixNotComparableReason.NothingRegistered)
    }
    return when (val derived = pixIdentityOf(scanned)) {
        is PixIdentityResult.Available -> comparePix(registered, derived.identity)
        is PixIdentityResult.Unavailable -> PixComparison.NotComparable(derived.reason)
    }
}

/**
 * Compara duas identidades já derivadas (ex.: duas plaquinhas do cofre).
 *
 * Precedência documentada em [PixComparison].
 */
fun comparePix(registered: PixIdentity?, scanned: PixIdentity?): PixComparison {
    if (registered == null || scanned == null) {
        return PixComparison.NotComparable(PixNotComparableReason.NothingRegistered)
    }

    // Recebedor diferente é o veredito mais grave: vem antes de regime, endereço e payload.
    val changedFields = receiverDiff(registered.receiver, scanned.receiver)
    if (changedFields.isNotEmpty()) {
        return PixComparison.ReceiverChanged(
            expected = registered.receiver,
            found = scanned.receiver,
            changed = changedFields,
        )
    }

    if (registered.initiationMethod != scanned.initiationMethod) {
        return PixComparison.RegimeChanged(
            expected = registered.initiationMethod,
            found = scanned.initiationMethod,
        )
    }

    return when (registered) {
        is PixIdentity.Static -> {
            val found = scanned as PixIdentity.Static
            if (registered.payload == found.payload) {
                PixComparison.SamePlaque
            } else {
                PixComparison.PayloadChanged(
                    expectedPayload = registered.payload,
                    foundPayload = found.payload,
                )
            }
        }

        is PixIdentity.Dynamic -> {
            val found = scanned as PixIdentity.Dynamic
            if (registered.endpoint == found.endpoint) {
                PixComparison.SamePlaque
            } else {
                PixComparison.EndpointChanged(
                    expected = registered.endpoint,
                    found = found.endpoint,
                )
            }
        }
    }
}

/**
 * Quais campos do recebedor divergem.
 *
 * Campo ausente **nos dois lados** não é divergência — a lib não inventa alarme a partir de dado que
 * o padrão nem exige. Presente em um só lado **é** divergência: a estrutura do QR de uma mesma
 * plaquinha não muda de uma leitura para a outra.
 *
 * [PixReceiver.keyType] é rótulo e não participa: ele é derivado da chave, e a chave já é comparada.
 */
private fun receiverDiff(expected: PixReceiver, found: PixReceiver): Set<PixReceiverField> {
    val changed = mutableSetOf<PixReceiverField>()
    if (expected.key != found.key) changed += PixReceiverField.KEY
    if (expected.name != found.name) changed += PixReceiverField.NAME
    if (expected.city != found.city) changed += PixReceiverField.CITY
    return changed
}

/**
 * Extrai o endereço estável de uma URL de payload dinâmico.
 *
 * A URL da tag `25` vem tipicamente **sem esquema** (`pix.example.com/qr/v2/cobv/abc123`), mas há
 * emissor que grava com `https://` — os dois são aceitos.
 *
 * O que é descartado, e por quê:
 * - **Query e fragmento** — é onde parte dos PSPs põe o identificador da cobrança, que muda a cada
 *   uso.
 * - **Último segmento do caminho** — o identificador da cobrança no formato mais comum. Sobrando um
 *   segmento só, o prefixo fica vazio e a comparação recai em host + recebedor (menos específica,
 *   nunca mais permissiva do que precisa: host diferente continua sendo divergência).
 * - ***Userinfo*** (`algo@host`) — o host real é o que vem **depois do último `@`**. Ignorar isso
 *   seria cair na armadilha clássica de phishing `https://banco-de-verdade.com@servidor-do-golpe.com/x`,
 *   em que o olho lê o primeiro nome e o aparelho conecta no segundo.
 *
 * Devolve `null` quando não há host utilizável, quando o esquema não é HTTP(S), ou quando há espaço
 * no meio. Nunca lança.
 */
fun pixEndpointOf(url: String?): PixEndpoint? {
    var value = url?.trim().orEmpty()
    if (value.isEmpty()) return null
    if (value.any { it.isWhitespace() }) return null

    val schemeSeparator = value.indexOf("://")
    if (schemeSeparator >= 0) {
        val scheme = value.substring(0, schemeSeparator).lowercase()
        if (scheme != "http" && scheme != "https") return null
        value = value.substring(schemeSeparator + 3)
    } else {
        // Sem "://": `:` só é aceitável como separador de PORTA. Um esquema sem barras
        // (`mailto:`, `tel:`, `javascript:`) não é URL de payload Pix.
        val colon = value.indexOf(':')
        if (colon > 0) {
            val beforeColon = value.substring(0, colon)
            val afterColon = value.substring(colon + 1).substringBefore('/')
            val looksLikePort = !beforeColon.contains('/') &&
                afterColon.isNotEmpty() &&
                afterColon.all { it in '0'..'9' }
            if (!looksLikePort) return null
        }
    }

    value = value.substringBefore('#').substringBefore('?')
    if (value.isEmpty()) return null

    val authority = value.substringBefore('/')
    val path = value.substringAfter('/', missingDelimiterValue = "")

    val hostPart = authority.substringAfterLast('@')
    val host = hostPart.trimEnd('.').lowercase()
    if (host.isEmpty()) return null
    if (host.any { it == '/' || it == '@' }) return null

    val segments = path.split('/').filter { it.isNotEmpty() }
    val pathPrefix = if (segments.size >= 2) {
        segments.dropLast(1).joinToString("/")
    } else {
        ""
    }

    return PixEndpoint(host = host, pathPrefix = pathPrefix)
}

/**
 * Normaliza uma chave Pix **para comparação** (nunca para exibição).
 *
 * O que é aparado, por tipo — e nada além disso:
 * - **CPF/CNPJ:** só dígitos (o padrão manda sem máscara, mas emissor com máscara existe; remover
 *   pontuação não pode fazer dois documentos distintos colidirem).
 * - **Telefone:** só dígitos, com o `+` e a formatação removidos (`+55 11 99999-8888` e
 *   `+5511999998888` são o mesmo número).
 * - **E-mail:** minúsculas (e-mail não distingue caixa na prática, e o Bacen registra em minúsculas).
 * - **Aleatória (EVP):** minúsculas (UUID em hexadecimal não distingue caixa).
 * - **Desconhecida:** apenas as bordas aparadas. Chave que a lib não reconhece é comparada **como
 *   veio** — mexer no que não se entende é o caminho para uma colisão.
 */
fun normalizePixKeyForCompare(key: String?, type: PixKeyType = inferPixKeyType(key)): String {
    val value = key?.trim().orEmpty()
    if (value.isEmpty()) return ""
    return when (type) {
        PixKeyType.CPF, PixKeyType.CNPJ, PixKeyType.PHONE -> value.filter { it in '0'..'9' }
        PixKeyType.EMAIL, PixKeyType.RANDOM -> value.lowercase()
        PixKeyType.UNKNOWN -> value
    }
}

/**
 * Normaliza nome/cidade **para comparação** (nunca para exibição): bordas aparadas, espaços internos
 * colapsados, acentos removidos e caixa alta.
 *
 * Justificativa de cada passo: o mesmo comerciante aparece como `"padaria do joão"`,
 * `"PADARIA DO JOAO"` e `"Padaria  do  Joao"` conforme o PSP e o cadastro — tratar isso como
 * divergência produziria alarme falso diário, que é o que faz o usuário desligar o alerta. Nenhum
 * dos passos pode aproximar dois nomes diferentes: nada é truncado, removido por semelhança ou
 * abreviado.
 */
fun normalizePixTextForCompare(text: String?): String {
    val value = text?.trim().orEmpty()
    if (value.isEmpty()) return ""
    return value
        .removeAccents()
        .split(' ', '\t', '\n', '\r')
        .filter { it.isNotEmpty() }
        .joinToString(" ")
        .uppercase()
}

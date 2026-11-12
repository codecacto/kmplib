package br.com.codecacto.kmplib.analytics

import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.isCanonicalUuid
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/*
 * Regras puras do que sai para o Umami — sem rede, sem plataforma, testadas uma a uma.
 *
 * O princípio é o da constituição: **nada de dado pessoal** na estatística. A lib não tem como saber
 * o que é PII no domínio de cada app, então faz as duas coisas que dá para fazer na borda:
 *  1. o CAMINHO da tela perde query, fragmento e todo trecho com cara de identificador
 *     (número, UUID, e-mail, token longo) — `cliente/123` vira `/app/cliente/:id`;
 *  2. a propriedade de EVENTO com nome de dado pessoal (e-mail, telefone, CPF, nome…) ou com valor
 *     que parece um (e-mail, 8+ algarismos) é descartada, com aviso no log (só o NOME da chave).
 * O resto é responsabilidade de quem chama: evento leva categoria, contagem, plano, origem — nunca
 * quem a pessoa é.
 */

/** Prefixo de todo caminho de tela no Umami: `/app/<rota>`. */
const val ANALYTICS_PATH_PREFIX: String = "/app"

/** Teto do nome de evento do Umami (coluna `event_name`, 50). */
const val ANALYTICS_MAX_EVENT_NAME: Int = 50

/** Teto de propriedades por evento — estatística é categoria, não registro. */
const val ANALYTICS_MAX_DATA_KEYS: Int = 20

/** Teto de uma propriedade texto (o Umami guarda até 500). */
const val ANALYTICS_MAX_STRING_VALUE: Int = 500

/** Teto do caminho da tela. */
const val ANALYTICS_MAX_PATH: Int = 200

private const val TAG = "Analytics"

private val CSV_INJECTION_PREFIX = Regex("^[=+\\-@\\t\\r]+")
private val EMAIL = Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")
private val LONG_TOKEN = Regex("^[A-Za-z0-9_\\-]{16,}$")

/**
 * Nomes de propriedade que carregam dado pessoal. Comparados contra a chave normalizada (minúsculas,
 * sem acento, sem `_`/`-`/espaço), por PEDAÇO: `email_cliente`, `telefoneContato` e `userName`
 * caem; `plano`, `origem` e `quantidade` passam.
 */
private val PII_KEY_PARTS = listOf(
    "email", "mail", "phone", "telefone", "celular", "whatsapp", "cpf", "cnpj", "rg",
    "nome", "name", "senha", "password", "token", "secret", "endereco", "address", "cep",
    "nascimento", "birth", "documento", "document",
)

/** Partes de chave que contêm um pedaço de [PII_KEY_PARTS] mas são inofensivas. */
private val SAFE_KEY_EXACT = setOf("screenname", "tela", "plano", "origem", "zip")

/**
 * O caminho da tela no Umami: `/app/<rota>`, sem query nem fragmento, em minúsculas, com cada trecho
 * que parece identificador trocado por `:id` e `{arg}` de rota virando `:arg`. Rota vazia = `/app`.
 *
 * ```
 * "home"                     -> "/app/home"
 * "/Clientes/123?aba=fotos"  -> "/app/clientes/:id"
 * "item/{id}"                -> "/app/item/:id"
 * ```
 */
fun analyticsScreenPath(route: String): String {
    val semQuery = route.substringBefore('?').substringBefore('#').trim()
    val trechos = semQuery.split('/')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { sanitizePathSegment(it) }
    if (trechos.isEmpty()) return ANALYTICS_PATH_PREFIX
    val caminho = "$ANALYTICS_PATH_PREFIX/" + trechos.joinToString("/")
    return if (caminho.length <= ANALYTICS_MAX_PATH) caminho else caminho.take(ANALYTICS_MAX_PATH).trimEnd('/')
}

private fun sanitizePathSegment(segment: String): String {
    if (segment.startsWith("{") && segment.endsWith("}") && segment.length > 2) {
        val nome = segment.substring(1, segment.length - 1).substringBefore(':').trim()
        return ":" + nome.lowercase().ifEmpty { "arg" }
    }
    val s = segment.lowercase()
    val pareceId = s.all { it.isDigit() } ||
        isCanonicalUuid(s) ||
        EMAIL.containsMatchIn(s) ||
        s.count { it.isDigit() } >= 4 ||
        (LONG_TOKEN.matches(s) && s.any { it.isDigit() })
    return if (pareceId) ":id" else s
}

/**
 * Nome legível de tela a partir da ROTA de um destino do Navigation (o `NavDestination.route`):
 *
 * - rota type-safe (`br.com.app.Route.ItemDetail/{id}`) → `item-detail` (o nome da classe em kebab,
 *   sem o pacote e sem os argumentos — o valor do argumento nunca sai);
 * - rota em texto (`item/{id}`, `home`) → os trechos fixos (`item`, `home`);
 * - `null`/vazia → `null` (grafo, nada a contar).
 */
fun screenNameForRoute(route: String?): String? {
    val r = route?.trim()?.ifEmpty { null } ?: return null
    val base = r.substringBefore('?')
    val primeiro = base.substringBefore('/')
    if (primeiro.contains('.')) {
        val classe = primeiro.substringAfterLast('.').substringAfterLast('$')
        return toKebabCase(classe).ifEmpty { null }
    }
    val fixos = base.split('/').map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("{") }
    return fixos.joinToString("/") { toKebabCase(it) }.ifEmpty { null }
}

/** `ItemDetail` → `item-detail`; `HTTPSCheck` → `https-check`; `item_lista` → `item-lista`. */
internal fun toKebabCase(value: String): String {
    val out = StringBuilder()
    value.forEachIndexed { i, c ->
        when {
            c == '_' || c == ' ' || c == '-' -> if (out.isNotEmpty() && out.last() != '-') out.append('-')
            c.isUpperCase() -> {
                val anterior = value.getOrNull(i - 1)
                val proximo = value.getOrNull(i + 1)
                val novaPalavra = i > 0 && anterior != null && anterior != '_' && anterior != '-' &&
                    (anterior.isLowerCase() || anterior.isDigit() || (proximo != null && proximo.isLowerCase()))
                if (novaPalavra && out.isNotEmpty() && out.last() != '-') out.append('-')
                out.append(c.lowercaseChar())
            }
            else -> out.append(c.lowercaseChar())
        }
    }
    return out.toString().trim('-')
}

/**
 * Nome de evento aceito pelo Umami: aparado, sem os prefixos que ele recusa (`= + - @`, tab, CR —
 * guarda contra injeção em CSV), até [ANALYTICS_MAX_EVENT_NAME] caracteres. Vazio → `null` (o evento
 * é descartado).
 */
fun sanitizeEventName(name: String): String? =
    name.trim().replace(CSV_INJECTION_PREFIX, "").trim().take(ANALYTICS_MAX_EVENT_NAME).trim().ifEmpty { null }

/** `true` se a chave [key] nomeia dado pessoal (ver [PII_KEY_PARTS]). */
fun isPersonalDataKey(key: String): Boolean {
    val k = normalizeKey(key)
    if (k in SAFE_KEY_EXACT) return false
    return PII_KEY_PARTS.any { parte ->
        // Parte curta (cpf, cep, rg) só no começo ou no fim, senão "cargo" cairia por "rg".
        when {
            parte == "rg" -> k == parte || k.startsWith(parte)
            parte.length <= 4 -> k.startsWith(parte) || k.endsWith(parte)
            else -> k.contains(parte)
        }
    }
}

/** `true` se o texto [value] parece dado pessoal: e-mail ou 8+ algarismos (telefone, CPF, CNPJ, conta). */
fun looksLikePersonalData(value: String): Boolean =
    EMAIL.containsMatchIn(value) || value.count { it.isDigit() } >= 8

private fun normalizeKey(key: String): String = buildString {
    for (c in key.lowercase()) {
        when (c) {
            '_', '-', ' ', '.' -> Unit
            'á', 'à', 'â', 'ã', 'ä' -> append('a')
            'é', 'ê', 'è', 'ë' -> append('e')
            'í', 'î', 'ì', 'ï' -> append('i')
            'ó', 'ô', 'õ', 'ò', 'ö' -> append('o')
            'ú', 'û', 'ù', 'ü' -> append('u')
            'ç' -> append('c')
            else -> append(c)
        }
    }
}

/**
 * As propriedades do evento no formato do Umami — só texto, número e booleano; até
 * [ANALYTICS_MAX_DATA_KEYS] chaves; texto até [ANALYTICS_MAX_STRING_VALUE]. Descarta (com aviso no
 * log, só o nome da chave): chave de dado pessoal, texto que parece dado pessoal, número não finito e
 * tipo que não é primitivo. Nada sobrou → `null`.
 */
fun sanitizeEventData(data: Map<String, Any?>): JsonObject? {
    if (data.isEmpty()) return null
    val out = LinkedHashMap<String, JsonPrimitive>()
    for ((rawKey, value) in data) {
        if (out.size >= ANALYTICS_MAX_DATA_KEYS) {
            AppLogger.w(TAG, "Evento com mais de $ANALYTICS_MAX_DATA_KEYS propriedades — o resto foi descartado.")
            break
        }
        val key = rawKey.trim().replace(CSV_INJECTION_PREFIX, "").take(ANALYTICS_MAX_EVENT_NAME)
        if (key.isEmpty() || value == null) continue
        if (isPersonalDataKey(key)) {
            AppLogger.w(TAG, "Propriedade '$key' descartada: nome de dado pessoal.")
            continue
        }
        val primitive: JsonPrimitive? = when (value) {
            is Boolean -> JsonPrimitive(value)
            is Int -> JsonPrimitive(value)
            is Long -> JsonPrimitive(value)
            is Short -> JsonPrimitive(value)
            is Byte -> JsonPrimitive(value)
            is Double -> if (value.isFinite()) JsonPrimitive(value) else null
            is Float -> if (value.isFinite()) JsonPrimitive(value) else null
            is String, is Enum<*> -> {
                val text = (if (value is Enum<*>) value.name else value as String).trim()
                if (looksLikePersonalData(text)) {
                    AppLogger.w(TAG, "Propriedade '$key' descartada: o valor parece dado pessoal.")
                    null
                } else {
                    JsonPrimitive(text.take(ANALYTICS_MAX_STRING_VALUE))
                }
            }
            else -> {
                AppLogger.w(TAG, "Propriedade '$key' descartada: tipo ${value::class.simpleName} não é texto/número/booleano.")
                null
            }
        }
        if (primitive != null) out[key] = primitive
    }
    return if (out.isEmpty()) null else JsonObject(out)
}

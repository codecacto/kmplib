package br.com.codecacto.kmplib.ui.tour

import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * O "já viu" NA CONTA sobre a API do próprio produto (2.371.0) — o contrato do `backlib-product-tour`
 * (`productTourSeenRoutes`), par do `createProductTourAccountStore` da weblib:
 *
 * - `GET  {path}`       → `200 {"seen": ["tour.inicio.v1", "welcome", …]}`
 * - `PUT  {path}/{key}` → `204`
 *
 * Serve ao tour (`ProductTourHost(firstRunAccountStore = …)`) e às boas-vindas do 1º acesso
 * (`WelcomeAnnouncementConfig(accountStore = …)`) com UM objeto. A lista lida fica em memória até a
 * próxima gravação — o tour e as boas-vindas, na mesma abertura, fazem um GET só.
 *
 * Falha (rede, 401, corpo ilegível) **lança**: quem chama trata como "não sei" — o tour e as boas-vindas
 * não abrem em vez de arriscar repetir a quem já viu.
 *
 * @param api o `DomainApiClient` do app (Bearer da sessão).
 * @param path caminho da rota, ex.: `"/<slug>/v1/me/tours/seen"` (o default da casca-backend é `/v1/me/tours/seen`
 *   sob o slug do projeto).
 */
class RestProductTourAccountStore(
    private val api: DomainApiClient,
    private val path: String,
) : ProductTourAccountStore {
    private val lock = Mutex()
    private var cached: Set<String>? = null

    override suspend fun isSeen(key: String): Boolean = lock.withLock {
        val seen = cached ?: fetchSeen().also { cached = it }
        key.trim() in seen
    }

    override suspend fun markSeen(key: String) {
        val k = key.trim()
        when (val r = api.putJson("${path.trimEnd('/')}/${encodePathSegment(k)}", "{}")) {
            is DomainResult.Success -> lock.withLock { cached = cached?.plus(k) }
            else -> throw ProductTourStoreException("Falha ao marcar '$k' na conta: $r")
        }
    }

    private suspend fun fetchSeen(): Set<String> {
        val r = api.getJson(path)
        if (r !is DomainResult.Success) throw ProductTourStoreException("Falha ao ler o que a conta já viu: $r")
        return parseSeenKeys(r.data) ?: throw ProductTourStoreException("Resposta de 'já viu' ilegível.")
    }
}

/** Falha ao ler/gravar o "já viu" na conta. */
class ProductTourStoreException(message: String) : Exception(message)

private val SeenJson = Json { ignoreUnknownKeys = true }

/** `{"seen": [...]}` → as chaves; `null` se o corpo não tem esse formato. */
internal fun parseSeenKeys(body: String): Set<String>? {
    val root = runCatching { SeenJson.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return null
    val list = root["seen"] as? JsonArray ?: return null
    return list.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull?.trim() }.toSet()
}

private fun encodePathSegment(value: String): String = buildString {
    value.encodeToByteArray().forEach { b ->
        val c = b.toInt().toChar()
        if (b >= 0 && (c.isLetterOrDigit() || c in "-._~")) append(c)
        else append('%').append(((b.toInt() and 0xFF) or 0x100).toString(16).substring(1).uppercase())
    }
}

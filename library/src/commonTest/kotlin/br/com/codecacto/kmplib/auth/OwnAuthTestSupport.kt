package br.com.codecacto.kmplib.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Cofre seguro em memória para testes (o real usa EncryptedSharedPreferences/Keychain). */
class FakeSecureTokenStorage : SecureTokenStorage {
    private val map = HashMap<String, String>()
    private val lock = Mutex()

    override suspend fun getString(key: String): String? = lock.withLock { map[key] }
    override suspend fun putString(key: String, value: String) { lock.withLock { map[key] = value } }
    override suspend fun remove(key: String) { lock.withLock { map.remove(key) } }
    override suspend fun clear() { lock.withLock { map.clear() } }
}

/** Requisição capturada pelo MockEngine. */
class CapturedRequest(val url: String, val body: String)

/**
 * Monta um [OwnAuthApi] sobre um MockEngine controlado por [responder] (recebe o path final e o
 * número da tentativa, devolve status + corpo JSON). Captura as requisições em [captured].
 */
fun mockOwnAuthApi(
    captured: MutableList<CapturedRequest> = mutableListOf(),
    baseUrl: String = "https://api.example.com",
    authBasePath: String = "/v1/staff/auth",
    responder: (path: String, attempt: Int) -> Pair<HttpStatusCode, String>,
): Pair<OwnAuthApi, MutableList<CapturedRequest>> {
    var attempt = 0
    val engine = MockEngine { request ->
        attempt++
        val bodyText = (request.body as? io.ktor.http.content.TextContent)?.text ?: ""
        captured += CapturedRequest(request.url.toString(), bodyText)
        val (status, body) = responder(request.url.encodedPath, attempt)
        respond(content = body, status = status, headers = headersOf("Content-Type", "application/json"))
    }
    val config = OwnAuthConfig(HttpClient(engine), baseUrl = baseUrl, authBasePath = authBasePath)
    return OwnAuthApi(config) to captured
}

private const val B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

/** Codifica base64url sem padding (para montar JWTs de teste). */
private fun base64UrlNoPad(bytes: ByteArray): String {
    val sb = StringBuilder()
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else -1
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else -1
        sb.append(B64[b0 shr 2])
        when {
            b1 < 0 -> sb.append(B64[(b0 and 0x03) shl 4])
            b2 < 0 -> {
                sb.append(B64[((b0 and 0x03) shl 4) or (b1 shr 4)])
                sb.append(B64[(b1 and 0x0F) shl 2])
            }
            else -> {
                sb.append(B64[((b0 and 0x03) shl 4) or (b1 shr 4)])
                sb.append(B64[((b1 and 0x0F) shl 2) or (b2 shr 6)])
                sb.append(B64[b2 and 0x3F])
            }
        }
        i += 3
    }
    return sb.toString()
}

/** Monta um JWT falso `header.payload.sig` cujo payload contém o `sub` informado. */
fun fakeJwt(sub: String): String {
    val header = base64UrlNoPad("""{"alg":"HS256","typ":"JWT"}""".encodeToByteArray())
    val payload = base64UrlNoPad("""{"sub":"$sub","iat":1700000000}""".encodeToByteArray())
    return "$header.$payload.signature"
}

/** JSON de resposta de tokens. */
fun tokensJson(access: String, refresh: String, expiresIn: Long = 3600): String =
    """{"accessToken":"$access","refreshToken":"$refresh","expiresInSeconds":$expiresIn,"tokenType":"Bearer"}"""

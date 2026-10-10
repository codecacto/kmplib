package br.com.codecacto.kmplib.core.network

import br.com.codecacto.kmplib.sync.rest.ServerErrorDetails
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    /**
     * @param serverCode o `code` do envelope de erro do servidor (2.261.0), quando veio — é por ele
     *   que um 401 de **reautenticação** ([isReauthRequired]) se distingue do 401 de sessão expirada.
     * @param details os `details` do envelope (2.261.0) — em `REAUTH_REQUIRED`, `maxAgeSeconds`. Só
     *   valores **primitivos**.
     * @param detailsJson o objeto `details` inteiro, como o servidor mandou (2.279.0) — com as listas e
     *   os objetos que [details] descarta. Leia com [detailList], [detailObject], [decodeDetail], [detail].
     */
    data class Error(
        val code: Int = -1,
        val message: String,
        val serverCode: String? = null,
        val details: Map<String, String> = emptyMap(),
        val detailsJson: JsonObject = ServerErrorDetails.EMPTY,
    ) : ApiResult<Nothing>() {
        /** `details[key]` cru (2.279.0); `null` se ausente. */
        fun detail(key: String): JsonElement? = ServerErrorDetails.element(detailsJson, key)

        /** `details[key]` como lista de textos (2.279.0) — mesma regra de `DomainResult.Error.detailList`. */
        fun detailList(key: String): List<String>? = ServerErrorDetails.list(detailsJson, key)

        /** `details[key]` como objeto JSON (2.279.0). */
        fun detailObject(key: String): JsonObject? = ServerErrorDetails.obj(detailsJson, key)

        /** `details[key]` decodificado como [T] (2.279.0); `null` se não casar. */
        inline fun <reified T> decodeDetail(key: String): T? = ServerErrorDetails.decode<T>(detailsJson, key)

        /**
         * `true` no 401 `REAUTH_REQUIRED` (step-up, backlib ≥ 0.151.0): **não é logout** — ver
         * [RecentAuthChallenge]. O `RestRepository` não chama `onUnauthorized` nele.
         */
        val isReauthRequired: Boolean get() = code == 401 && serverCode == RecentAuthChallenge.CODE

        /** O erro tipado para atravessar `Result`/`withRecentAuth`; só tem sentido se [isReauthRequired]. */
        fun toReauthRequiredException(): ReauthRequiredException =
            ReauthRequiredException(RecentAuthChallenge.maxAgeSeconds(details, null), message)
    }
    data object Loading : ApiResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): String? = (this as? Error)?.message

    inline fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }

    inline fun onSuccess(action: (T) -> Unit): ApiResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (String) -> Unit): ApiResult<T> {
        if (this is Error) action(message)
        return this
    }
}

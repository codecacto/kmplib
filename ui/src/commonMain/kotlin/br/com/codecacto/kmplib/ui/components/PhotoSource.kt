package br.com.codecacto.kmplib.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainResult
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import okio.Buffer

/**
 * **De onde vem a imagem** de uma miniatura (2.216.0) — URL pública, URL com cabeçalhos, bytes já em
 * memória ou um carregador autenticado.
 *
 * ## Por que existe
 * O `PhotoStripItem` só aceitava URL, e foto **privada** não tem URL que se possa entregar ao
 * carregador de imagem: ela sai de um `GET /v1/.../fotos/{id}/bytes` autenticado, com o Bearer da
 * sessão e o 401→refresh do [DomainApiClient]. Sem uma fonte autenticada, o app de clínica tinha
 * duas saídas ruins — baixar os bytes na tela e montar a miniatura na mão (perdendo a faixa
 * inteira), ou expor a foto numa URL pública para caber no componente.
 *
 * ## Qual usar
 * - [Url] — endereço público ou pré-assinado. `headers` só para cabeçalho **estável** (uma chave de
 *   API do CDN); **nunca** um Bearer de sessão: ele vence em 15 minutos e a faixa passa a mostrar
 *   falha sem nada ter mudado.
 * - [Loader] — **o caminho da foto privada.** Uma função suspensa que devolve os bytes; o atalho
 *   [authenticated] liga direto ao [DomainApiClient.getBytes], que já anexa o token certo e renova
 *   no 401.
 * - [Bytes] — a foto que acabou de ser escolhida e ainda está em memória (ex.: `PickedImage.bytes`),
 *   para a miniatura aparecer antes do upload terminar.
 *
 * ## Privacidade: foto privada NÃO vai para o disco
 * [Loader], [Bytes] e [Url] **com cabeçalhos** saem com o cache de disco do Coil **desligado** — o
 * cache de disco é um arquivo em claro no aparelho, e foto clínica gravada ali sobrevive ao logout.
 * Fica só o cache de **memória**, indexado pela [key], que morre com o processo.
 *
 * ## …e não sobrevive ao logout (2.218.0)
 * "Morre com o processo" não bastava: o logout não mata o processo, e a próxima pessoa a entrar no
 * mesmo aparelho via, de relance, a miniatura em memória da conta anterior sempre que a chave
 * coincidisse (o mesmo `path` de endpoint, como `/v1/pacientes/1/foto`). Duas defesas:
 * - [authenticated] recebe `accountId` e o põe **na chave** — contas diferentes nunca compartilham
 *   entrada de cache;
 * - as fontes privadas ([Loader], [Bytes], [Url] com cabeçalhos) entram no cache com o prefixo
 *   [PRIVATE_PHOTO_MEMORY_KEY_PREFIX], e [clearPrivatePhotoMemoryCache] tira só elas. A limpeza de
 *   conta do sync (`SyncAccountDataPurger`) e o `AccountDeletionService` já chamam; app sem sync
 *   chama no próprio logout.
 *
 * ⚠️ A [key] é a identidade da imagem para o cache: duas fotos diferentes com a mesma chave mostram a
 * mesma imagem. Use o id da foto no servidor (ou o caminho do endpoint), nunca um índice da lista.
 */
sealed interface PhotoSource {

    /** Chave da imagem no cache de memória. */
    val key: String

    /**
     * URL, com cabeçalhos opcionais.
     *
     * @param headers cabeçalhos da requisição da imagem. Com algum, o cache de disco é desligado.
     */
    data class Url(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
    ) : PhotoSource {
        override val key: String get() = url
    }

    /**
     * Bytes já em memória (JPEG/PNG/WebP). Igualdade pela [key], não pelo conteúdo: comparar
     * megabytes a cada recomposição seria o custo de nada.
     */
    class Bytes(
        override val key: String,
        val bytes: ByteArray,
    ) : PhotoSource {
        override fun equals(other: Any?): Boolean = other is Bytes && other.key == key
        override fun hashCode(): Int = key.hashCode()
        override fun toString(): String = "PhotoSource.Bytes(key=$key, ${bytes.size} bytes)"
    }

    /**
     * Carregador suspenso — o caminho da foto privada. Devolva `null` (ou lance) quando não der para
     * carregar: a miniatura mostra a marca de falha em vez de girar para sempre.
     *
     * Igualdade pela [key]: a lambda muda a cada recomposição, e comparar por ela faria o Coil
     * baixar a foto de novo a cada quadro.
     */
    class Loader(
        override val key: String,
        val load: suspend () -> ByteArray?,
    ) : PhotoSource {
        override fun equals(other: Any?): Boolean = other is Loader && other.key == key
        override fun hashCode(): Int = key.hashCode()
        override fun toString(): String = "PhotoSource.Loader(key=$key)"
    }

    companion object {
        /**
         * Foto privada servida pelo backend de domínio: `GET [path]` com o Bearer da sessão, 401→
         * refresh e transporte que não lança — tudo do [DomainApiClient.getBytes].
         *
         * ```kotlin
         * PhotoStripItem(
         *     id = foto.id,
         *     source = PhotoSource.authenticated(api, "/v1/fotos/${foto.id}/bytes", accountId = sessao.userId),
         * )
         * ```
         *
         * @param accountId o id da conta da sessão. **Passe sempre em app com login**: sem ele, duas
         *   contas que abrem o mesmo [path] no mesmo processo dividem a entrada do cache de memória.
         */
        fun authenticated(
            api: DomainApiClient,
            path: String,
            key: String = path,
            accountId: String? = null,
        ): Loader =
            Loader(accountScopedPhotoKey(key, accountId)) {
                when (val r = api.getBytes(path)) {
                    is DomainResult.Success -> r.data
                    else -> null
                }
            }
    }
}

/**
 * O [ImageRequest] do Coil para uma [PhotoSource] — para quem desenha a imagem num componente
 * próprio (`AsyncImage(model = rememberPhotoSourceRequest(fonte), …)`) com as mesmas garantias da
 * faixa: fonte autenticada, sem disco para foto privada, cache de memória pela [PhotoSource.key].
 */
@Composable
fun rememberPhotoSourceRequest(source: PhotoSource): ImageRequest {
    val context = LocalPlatformContext.current
    return remember(context, source) { photoSourceRequest(context, source) }
}

/** Versão não-composable de [rememberPhotoSourceRequest]. */
fun photoSourceRequest(context: PlatformContext, source: PhotoSource): ImageRequest {
    val builder = ImageRequest.Builder(context).memoryCacheKey(photoMemoryCacheKey(source))
    return when (source) {
        is PhotoSource.Url -> {
            builder.data(source.url)
            if (source.headers.isNotEmpty()) {
                val headers = NetworkHeaders.Builder()
                source.headers.forEach { (nome, valor) -> headers.set(nome, valor) }
                builder.httpHeaders(headers.build()).diskCachePolicy(CachePolicy.DISABLED)
            }
            builder.build()
        }
        is PhotoSource.Bytes -> builder
            .data(source.bytes)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
        is PhotoSource.Loader -> builder
            .data(source)
            .fetcherFactory(PhotoLoaderFetcher.Factory, PhotoSource.Loader::class)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
}

/** Prefixo da chave de cache de memória de toda fonte PRIVADA — é por ele que a limpeza as acha. */
const val PRIVATE_PHOTO_MEMORY_KEY_PREFIX: String = "kmplib-private-photo:"

/** A fonte é privada (não pode sobreviver ao logout nem ir ao disco)? */
internal val PhotoSource.isPrivate: Boolean
    get() = when (this) {
        is PhotoSource.Url -> headers.isNotEmpty()
        is PhotoSource.Bytes, is PhotoSource.Loader -> true
    }

/** Chave no cache de memória: privada ganha o prefixo; URL pública fica como sempre foi. */
internal fun photoMemoryCacheKey(source: PhotoSource): String =
    if (source.isPrivate) PRIVATE_PHOTO_MEMORY_KEY_PREFIX + source.key else source.key

/** A chave com a conta à frente — contas diferentes, entradas diferentes. */
internal fun accountScopedPhotoKey(key: String, accountId: String?): String =
    if (accountId.isNullOrBlank()) key else "$accountId|$key"

/**
 * Tira do cache de **memória** do [imageLoader] toda imagem de fonte privada (2.218.0) — as públicas
 * ficam. Chame no logout/troca de conta. Devolve quantas entradas saíram.
 */
fun clearPrivatePhotoMemoryCache(imageLoader: ImageLoader): Int {
    val cache = imageLoader.memoryCache ?: return 0
    return cache.keys
        .filter { isPrivatePhotoMemoryKey(it.key) }
        .count { cache.remove(it) }
}

/** A entrada do cache veio de uma fonte privada? (É o critério da limpeza.) */
internal fun isPrivatePhotoMemoryKey(key: String): Boolean = key.startsWith(PRIVATE_PHOTO_MEMORY_KEY_PREFIX)

/**
 * O mesmo, sobre o `ImageLoader` **padrão** do app (`SingletonImageLoader`, o que `AsyncImage` usa
 * quando não recebe outro). Sem a lib inicializada (Android sem `Context`) devolve `0`.
 */
fun clearPrivatePhotoMemoryCache(): Int {
    val context = defaultPhotoPlatformContext() ?: return 0
    return clearPrivatePhotoMemoryCache(SingletonImageLoader.get(context))
}

/** O `PlatformContext` do app fora da composição (Android: o `Context` registrado no init). */
internal expect fun defaultPhotoPlatformContext(): PlatformContext?

/**
 * Fetcher do Coil para [PhotoSource.Loader] — a extensão oficial do pipeline de imagem (um `Fetcher`
 * por tipo de dado), registrada **por requisição** para não exigir que o app troque o `ImageLoader`
 * global.
 */
internal class PhotoLoaderFetcher(
    private val source: PhotoSource.Loader,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val bytes = source.load()
        // Lançar é como o Coil entende "falhou": vira `AsyncImagePainter.State.Error`, e a miniatura
        // mostra a marca de falha. Devolver vazio travaria o decodificador numa imagem inexistente.
        if (bytes == null || bytes.isEmpty()) {
            throw IllegalStateException("Não foi possível carregar a imagem (${source.key}).")
        }
        return SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    object Factory : Fetcher.Factory<PhotoSource.Loader> {
        override fun create(data: PhotoSource.Loader, options: Options, imageLoader: ImageLoader): Fetcher =
            PhotoLoaderFetcher(data, options)
    }
}

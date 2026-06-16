package br.com.codecacto.kmplib.core.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * Cria [RestRepository] por entidade reusando a mesma [RestConfig] do app, de forma amigável ao
 * Koin.
 *
 * Declare a config e a factory como singletons; depois declare um repositório por entidade:
 *
 * ```kotlin
 * val dataModule = module {
 *     // 1) Uma config para todo o backend do app
 *     single {
 *         RestConfig(
 *             httpClient = get(),
 *             baseUrl = "https://api.codecacto.com.br",
 *             projectSlug = "meu-advogado",
 *             tokenProvider = {
 *                 val auth: AuthRepository = get()
 *                 auth.currentUser.value?.getIdToken(false) // Firebase ID token (suspend)
 *             },
 *             onUnauthorized = { /* forçar re-login */ },
 *         )
 *     }
 *
 *     // 2) Uma factory
 *     single { RestRepositoryFactory(get()) }
 *
 *     // 3) Um repositório por entidade — bindando o CONTRATO p/ testabilidade
 *     single<Repository<Request, String>> {
 *         get<RestRepositoryFactory>().create("/meu-advogado/v1/requests")
 *     }
 * }
 * ```
 *
 * No ViewModel injete o contrato: `class RequestsViewModel(private val repo: Repository<Request, String>)`.
 * Em teste, troque por um fake de `Repository<Request, String>`.
 *
 * @param config Configuração compartilhada do backend.
 */
class RestRepositoryFactory(
    private val config: RestConfig,
) {

    /**
     * Cria um [RestRepository] para a entidade [T] no recurso [pathPrefix].
     *
     * O serializer de [T] é resolvido automaticamente via `reified` — o tipo precisa ser
     * `@Serializable`. Para casos sem `reified` (serializer custom), use a sobrecarga que recebe
     * o [KSerializer] explicitamente.
     *
     * @param pathPrefix Caminho do recurso após a baseUrl (ex.: `"/meu-advogado/v1/requests"`).
     * @param idToPath Conversão do id para segmento de caminho (default `toString()`).
     */
    inline fun <reified T, ID> create(
        pathPrefix: String,
        noinline idToPath: (ID) -> String = { it.toString() },
    ): RestRepository<T, ID> =
        create(pathPrefix, serializer<T>(), idToPath)

    /**
     * Cria um [RestRepository] para a entidade [T] passando o [entitySerializer] explicitamente.
     * Útil quando o serializer é custom ou quando o tipo não está disponível como `reified`.
     */
    fun <T, ID> create(
        pathPrefix: String,
        entitySerializer: KSerializer<T>,
        idToPath: (ID) -> String = { it.toString() },
    ): RestRepository<T, ID> =
        RestRepository(
            config = config,
            pathPrefix = pathPrefix,
            entitySerializer = entitySerializer,
            idToPath = idToPath,
        )
}

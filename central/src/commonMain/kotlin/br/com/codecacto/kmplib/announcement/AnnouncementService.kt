package br.com.codecacto.kmplib.announcement

import br.com.codecacto.kmplib.core.locale.appLanguageTag
import br.com.codecacto.kmplib.core.network.ApiResult
import br.com.codecacto.kmplib.core.network.handleApiCall
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.core.util.currentPlatform
import br.com.codecacto.kmplib.ui.announcement.Announcement
import br.com.codecacto.kmplib.ui.announcement.AnnouncementJson
import br.com.codecacto.kmplib.ui.announcement.decodeAnnouncements
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray

/**
 * Configuração da busca dos avisos do app no apps-api central (GAP-ANNOUNCEMENT-02, 2.295.0).
 *
 * @param projectSlug slug do projeto no Nexus/apps-api (o `project=` da consulta).
 * @param httpClient `HttpClient` do app (Ktor core puro — idealmente o `createHttpClient` da lib).
 * @param appsApiBaseUrl base pública do apps-api (sem barra final). Default de produção.
 */
data class AnnouncementConfig(
    val projectSlug: String,
    val httpClient: HttpClient,
    val appsApiBaseUrl: String = DEFAULT_APPS_API_BASE_URL,
) {
    companion object {
        const val DEFAULT_APPS_API_BASE_URL: String = "https://apps-api.codecacto.com.br"
    }
}

/**
 * Cliente de `GET {appsApi}/public/announcements?project=<slug>&platform=android|ios&locale=<bcp47>`
 * — o cadastro do **Nexus → Avisos** chegando ao app. A resposta é um array JSON de [Announcement]
 * (o mesmo formato do [decodeAnnouncements]: item malformado é descartado sozinho). Sem auth.
 *
 * Duas formas de uso:
 * - [fetch] — uma leitura avulsa; **nunca lança** e devolve lista vazia em qualquer falha;
 * - [refresh] + [latest] — o que o [rememberRemoteAnnouncements] usa: a última lista boa fica em
 *   memória no processo (voltar a uma tela já mostra o aviso), uma falha **mantém** a lista anterior
 *   (sem rede o aviso de manutenção não some) e uma lista ainda fresca não é buscada de novo.
 *
 * @param timeSource relógio MONOTÔNICO do intervalo mínimo (troque por `TestTimeSource` em teste).
 */
class AnnouncementClient(
    val config: AnnouncementConfig,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val mutex = Mutex()
    private val _latest = MutableStateFlow<List<Announcement>>(emptyList())

    /** A última lista lida com sucesso neste processo (vazia até a primeira). */
    val latest: StateFlow<List<Announcement>> = _latest.asStateFlow()

    @Volatile
    private var lastSuccess: TimeMark? = null

    @Volatile
    private var lastKey: String? = null

    /**
     * Uma leitura avulsa. Nunca lança (salvo cancelamento): erro de rede, HTTP fora de 2xx ou corpo que
     * não é array JSON → lista vazia.
     *
     * @param platform `"android"` ou `"ios"` — default, o alvo em que o app roda.
     * @param locale tag BCP 47 — default, o idioma em que a TELA está ([appLanguageTag]), que é o
     *   que o servidor deve usar para escolher o texto.
     */
    suspend fun fetch(
        platform: String = currentPlatform,
        locale: String = appLanguageTag(),
    ): List<Announcement> = fetchOrNull(platform, locale) ?: emptyList()

    /**
     * Como [fetch], mas distingue a FALHA (`null`) da resposta vazia (`emptyList()`): o servidor dizer
     * "não há aviso" apaga o aviso; o servidor não responder não apaga nada.
     */
    suspend fun fetchOrNull(
        platform: String = currentPlatform,
        locale: String = appLanguageTag(),
    ): List<Announcement>? {
        val url = config.appsApiBaseUrl.trimEnd('/') + PATH
        val result: ApiResult<String> = handleApiCall {
            config.httpClient.get(url) {
                expectSuccess = true
                parameter("project", config.projectSlug)
                parameter("platform", platform)
                parameter("locale", locale)
            }.bodyAsText()
        }
        return when (result) {
            is ApiResult.Success -> {
                val isArray = runCatching { AnnouncementJson.parseToJsonElement(result.data) }.getOrNull() is JsonArray
                if (isArray) {
                    decodeAnnouncements(result.data)
                } else {
                    AppLogger.w(TAG, "Resposta de avisos não é um array JSON — ignorada.")
                    null
                }
            }
            is ApiResult.Error -> {
                AppLogger.w(TAG, "Falha ao buscar avisos (code=${result.code}).")
                null
            }
            ApiResult.Loading -> null
        }
    }

    /**
     * Atualiza [latest] se a última leitura boa tem mais de [minInterval] (ou se a plataforma/idioma
     * mudou). Chamadas simultâneas fazem UMA requisição. Falha mantém a lista anterior e não conta
     * para o intervalo — a próxima volta ao primeiro plano tenta de novo.
     *
     * @return `true` se buscou e a leitura deu certo; `false` se pulou (fresca) ou falhou.
     */
    suspend fun refresh(
        minInterval: Duration = Duration.ZERO,
        platform: String = currentPlatform,
        locale: String = appLanguageTag(),
    ): Boolean = mutex.withLock {
        val key = "$platform|$locale"
        val last = lastSuccess
        if (key == lastKey && last != null && last.elapsedNow() < minInterval) return@withLock false
        val list = fetchOrNull(platform, locale) ?: return@withLock false
        _latest.value = list
        lastSuccess = timeSource.markNow()
        lastKey = key
        true
    }

    private companion object {
        const val TAG = "AnnouncementService"
        const val PATH = "/public/announcements"
    }
}

/**
 * Ponto de acesso do processo aos avisos remotos. O [br.com.codecacto.kmplib.core.central.CentralServices]
 * já o inicializa (com o `projectSlug` e o `HttpClient` que o app passou) — o app normalmente não chama
 * [initialize]; põe o [RemoteAnnouncementHost] na tela e pronto.
 *
 * Sem inicialização, [fetch] devolve lista vazia e o host mostra só os avisos locais.
 */
object AnnouncementService {

    @Volatile
    private var _client: AnnouncementClient? = null

    /** O cliente do processo (`null` antes de [initialize]). */
    val client: AnnouncementClient? get() = _client

    val isInitialized: Boolean get() = _client != null

    /** Inicializa (ou troca) o cliente. A lista em memória recomeça vazia. */
    fun initialize(config: AnnouncementConfig) {
        _client = AnnouncementClient(config)
        AppLogger.d("AnnouncementService", "Inicializado projeto='${config.projectSlug}'")
    }

    /** [AnnouncementClient.fetch] do cliente do processo; vazia se não inicializado. Nunca lança. */
    suspend fun fetch(
        platform: String = currentPlatform,
        locale: String = appLanguageTag(),
    ): List<Announcement> = _client?.fetch(platform, locale) ?: emptyList()

    /** Apenas para testes. */
    internal fun resetForTesting() {
        _client = null
    }
}

/**
 * Junta os avisos do app ([local], constantes ou do backend próprio) com os do servidor central
 * ([remote]). Mesmo `id` nos dois → vale o REMOTO (o painel corrige o texto sem release). Ordem: os
 * remotos primeiro, na ordem do servidor, depois os locais que sobraram — o desempate da escolha é a
 * ordem da lista. `id` repetido dentro da mesma lista: vale o primeiro.
 */
fun mergeAnnouncements(local: List<Announcement>, remote: List<Announcement>): List<Announcement> {
    if (local.isEmpty() && remote.isEmpty()) return emptyList()
    val byId = LinkedHashMap<String, Announcement>(local.size + remote.size)
    remote.forEach { if (it.id !in byId) byId[it.id] = it }
    local.forEach { if (it.id !in byId) byId[it.id] = it }
    return byId.values.toList()
}

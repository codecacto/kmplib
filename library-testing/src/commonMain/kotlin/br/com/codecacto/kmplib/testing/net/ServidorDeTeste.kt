@file:OptIn(ExperimentalAtomicApi::class)

package br.com.codecacto.kmplib.testing.net

import br.com.codecacto.kmplib.account.AccountDeletionState
import br.com.codecacto.kmplib.core.network.HttpClientOptions
import br.com.codecacto.kmplib.core.network.createHttpClient
import br.com.codecacto.kmplib.sync.rest.DomainApiClient
import br.com.codecacto.kmplib.sync.rest.DomainTokenProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.cancel
import io.ktor.utils.io.toByteArray
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.io.IOException
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Uma requisição como o "servidor" a viu (2.381.0).
 *
 * @property metodo `GET`, `POST`…
 * @property caminho o caminho da URL **sem** o caminho da [ServidorDeTeste.base] (ex.: `/locaki/v2/locacoes/l1`).
 * @property query os parâmetros da URL (`?q=maria` → `{"q": ["maria"]}`).
 * @property corpo o corpo enviado, como texto UTF-8 (vazio em GET).
 * @property cabecalhos os cabeçalhos enviados (inclusive o `Authorization` — útil para provar a renovação do token).
 * @property parametros os pedaços `{nome}` do caminho da rota que atendeu (`/locacoes/{id}` → `{"id": "l1"}`);
 *   vazio quando nenhuma rota casou.
 */
class ChamadaRecebida(
    val metodo: HttpMethod,
    val caminho: String,
    val query: Map<String, List<String>>,
    val corpo: String,
    val cabecalhos: Headers,
    val parametros: Map<String, String> = emptyMap(),
) {
    /** `query["nome"]?.first()`. */
    fun parametroDaQuery(nome: String): String? = query[nome]?.firstOrNull()

    /** Sem corpo nem cabeçalhos: o teste pode imprimir a lista sem despejar token ou dado do cenário. */
    override fun toString(): String = "${metodo.value} $caminho"
}

/**
 * O que a rota recebe para montar a resposta — as funções [json], [vazio], [recusa], [cotaEsgotada],
 * [assinaturaBloqueada] e [naoAutorizado] (`Respostas.kt`), e [responder] para o caso cru.
 */
class RespostaScope internal constructor(internal val mock: MockRequestHandleScope) {
    /** Resposta crua: corpo em texto, status e cabeçalhos. */
    fun responder(corpo: String, status: HttpStatusCode = HttpStatusCode.OK, cabecalhos: Headers = Headers.Empty): HttpResponseData =
        mock.respond(corpo, status, cabecalhos)

    /** Resposta crua com corpo binário (PDF, imagem). */
    fun responder(corpo: ByteArray, status: HttpStatusCode = HttpStatusCode.OK, cabecalhos: Headers = Headers.Empty): HttpResponseData =
        mock.respond(corpo, status, cabecalhos)
}

/**
 * **Dublê de REDE para o teste do app** (2.381.0, `kmplib-testing`) — um "servidor" em memória sobre o
 * `MockEngine` oficial do Ktor, falando com o `createHttpClient` e o `DomainApiClient` REAIS da lib.
 *
 * O teste exercita a cadeia inteira do aparelho (ViewModel → repositório → API → `DomainApiClient` →
 * `HttpClient`) sem rede, e o caminho de falha vira uma linha:
 *
 * ```kotlin
 * val servidor = ServidorDeTeste()
 * servidor.rota(HttpMethod.Post, "/locaki/v2/locacoes/{id}/cancelar") { chamada ->
 *     banco.cancelar(chamada.parametros.getValue("id"))     // "o servidor gravou"
 *     json("""{"id":"l1","status":"CANCELADA"}""")
 * }
 * servidor.falhar(HttpMethod.Post, "/locaki/v2/locacoes/{id}/cancelar", FalhaDeRede.GravouERespostaSePerdeu)
 *
 * val api = LocAkiApi(servidor.domainApiClient(dispatcher = dispatcher))
 * ```
 *
 * **Rotas.** [rota] declara quem responde; o caminho aceita `{nome}` para um pedaço qualquer
 * (`/clientes/{id}`). A última rota declarada que casa é a que atende — declarar de novo troca a resposta.
 * Pedido sem rota responde **404** no envelope da backlib (`ROTA_NAO_DECLARADA`) e entra em [naoDeclaradas];
 * [confirmarTodasDeclaradas] reprova o teste se sobrou alguma.
 *
 * **Falhas.** [falhar] agenda uma [FalhaDeRede] para as próximas `vezes` chamadas que casarem. Ela é
 * consumida por TENTATIVA: um GET com `SemConexao` uma vez é repetido pela nova tentativa da lib e chega ao
 * servidor na 2ª — exatamente como no aparelho. [tentativas] mostra todas, [chamadas] só as que chegaram.
 *
 * **Tempo.** Passe o dispatcher do teste (`dispatcher = StandardTestDispatcher(testScheduler)`, ou o
 * `dispatcher` do [br.com.codecacto.kmplib.testing.vm.runViewModelTest]) para o engine responder no mesmo
 * relógio virtual do `runTest`: sem ele, o engine roda no `Dispatchers.IO`/`Default`, o `advanceUntilIdle()` não
 * espera a resposta e [FalhaDeRede.Demora] dorme de verdade.
 *
 * Seguro entre threads (o engine pode responder fora da thread do teste).
 */
class ServidorDeTeste(val base: String = "https://servidor-de-teste") {

    private val basePath: String = Url(base).encodedPath.trimEnd('/')

    private class Rota(
        val metodo: HttpMethod,
        val modelo: ModeloDeCaminho,
        val responder: suspend RespostaScope.(ChamadaRecebida) -> HttpResponseData,
    )

    private class FalhaAgendada(val metodo: HttpMethod, val modelo: ModeloDeCaminho, val falha: FalhaDeRede, val restantes: Int)

    private val rotas = AtomicReference<List<Rota>>(emptyList())
    private val falhas = AtomicReference<List<FalhaAgendada>>(emptyList())
    private val recebidas = AtomicReference<List<ChamadaRecebida>>(emptyList())
    private val todasAsTentativas = AtomicReference<List<ChamadaRecebida>>(emptyList())
    private val semRota = AtomicReference<List<ChamadaRecebida>>(emptyList())

    /** Toda requisição que CHEGOU ao "servidor", na ordem — sem as derrubadas por [FalhaDeRede.SemConexao]. */
    val chamadas: List<ChamadaRecebida> get() = recebidas.load()

    /** Toda tentativa que saiu do cliente, na ordem — inclusive as que a rede derrubou antes do servidor. */
    val tentativas: List<ChamadaRecebida> get() = todasAsTentativas.load()

    /** As chamadas que chegaram sem nenhuma rota declarada (responderam 404). */
    val naoDeclaradas: List<ChamadaRecebida> get() = semRota.load()

    /** As [chamadas] de [metodo] cujo caminho casa com [caminho] (aceita `{nome}`). */
    fun chamadas(metodo: HttpMethod, caminho: String): List<ChamadaRecebida> {
        val modelo = ModeloDeCaminho(caminho)
        return chamadas.filter { it.metodo == metodo && modelo.casar(it.caminho) != null }
    }

    /**
     * Declara a rota [metodo] [caminho]. O bloco roda quando o pedido CHEGA ao servidor — é o "servidor gravou" —,
     * inclusive com [FalhaDeRede.GravouERespostaSePerdeu], [FalhaDeRede.CorpoCortado] e [FalhaDeRede.Demora]
     * agendadas (com [FalhaDeRede.SemConexao], não roda).
     */
    fun rota(
        metodo: HttpMethod,
        caminho: String,
        responder: suspend RespostaScope.(ChamadaRecebida) -> HttpResponseData,
    ) {
        val nova = Rota(metodo, ModeloDeCaminho(caminho), responder)
        atualizar(rotas) { it + nova }
    }

    /** Agenda [falha] para as próximas [vezes] tentativas de [metodo] [caminho] (aceita `{nome}`). */
    fun falhar(metodo: HttpMethod, caminho: String, falha: FalhaDeRede, vezes: Int = 1) {
        require(vezes >= 1) { "vezes tem de ser >= 1: $vezes" }
        val agendada = FalhaAgendada(metodo, ModeloDeCaminho(caminho), falha, vezes)
        atualizar(falhas) { it + agendada }
    }

    /** Esquece as falhas agendadas que ainda não aconteceram. */
    fun limparFalhas() {
        falhas.store(emptyList())
    }

    /** Reprova o teste se algum pedido chegou sem rota declarada — o 404 não deve passar despercebido. */
    fun confirmarTodasDeclaradas() {
        val sobras = naoDeclaradas
        if (sobras.isNotEmpty()) {
            throw AssertionError("ServidorDeTeste: pedidos sem rota declarada: ${sobras.joinToString()}")
        }
    }

    /** O `MockEngine` deste servidor. [dispatcher] = o do teste, para o relógio virtual (ver o KDoc da classe). */
    fun engine(dispatcher: CoroutineDispatcher? = null): MockEngine = MockEngine(
        MockEngineConfig().apply {
            dispatcher?.let { this.dispatcher = it }
            reuseHandlers = true
            addHandler { requisicao -> atender(this, requisicao) }
        },
    )

    /**
     * O `createHttpClient` REAL da lib sobre o [engine]: timeout, nova tentativa de GET/HEAD/OPTIONS e de POST
     * `idempotent()`, leitura do corpo dentro da nova tentativa, gzip. [opcoes] parte do default da lib com o log
     * desligado e um [AccountDeletionState] próprio (o `Shared` é do processo e vazaria de um teste para o outro).
     */
    fun httpClient(
        dispatcher: CoroutineDispatcher? = null,
        opcoes: HttpClientOptions = HttpClientOptions(enableLogging = false, accountDeletion = AccountDeletionState()),
    ): HttpClient = createHttpClient(engine(dispatcher), opcoes)

    /**
     * O `DomainApiClient` REAL sobre o [httpClient], com a [base] deste servidor. [token] vai no `Authorization`;
     * depois de um 401, a lib pede o token renovado, que é [tokenRenovado].
     */
    fun domainApiClient(
        token: String = "token-de-teste",
        tokenRenovado: String = token,
        dispatcher: CoroutineDispatcher? = null,
        opcoes: HttpClientOptions = HttpClientOptions(enableLogging = false, accountDeletion = AccountDeletionState()),
    ): DomainApiClient = DomainApiClient(
        httpClient = httpClient(dispatcher, opcoes),
        tokenProvider = DomainTokenProvider { forceRefresh -> if (forceRefresh) tokenRenovado else token },
        baseUrl = base,
    )

    private suspend fun atender(scope: MockRequestHandleScope, requisicao: HttpRequestData): HttpResponseData {
        val caminho = requisicao.url.encodedPath.let { path ->
            if (basePath.isNotEmpty() && path.startsWith(basePath)) path.removePrefix(basePath).ifEmpty { "/" } else path
        }
        val crua = ChamadaRecebida(
            metodo = requisicao.method,
            caminho = caminho,
            query = requisicao.url.parameters.entries().associate { (k, v) -> k to v },
            corpo = requisicao.body.toByteArray().decodeToString(),
            cabecalhos = requisicao.headers,
        )
        atualizar(todasAsTentativas) { it + crua }

        val falha = consumirFalha(crua)
        if (falha == FalhaDeRede.SemConexao) {
            throw IOException("ServidorDeTeste: sem conexão (${crua.metodo.value} ${crua.caminho})")
        }

        val rota = rotas.load().lastOrNull { it.metodo == crua.metodo && it.modelo.casar(caminho) != null }
        val chamada = if (rota == null) crua else ChamadaRecebida(
            crua.metodo, crua.caminho, crua.query, crua.corpo, crua.cabecalhos,
            parametros = rota.modelo.casar(caminho).orEmpty(),
        )
        atualizar(recebidas) { it + chamada }

        val resposta = RespostaScope(scope)
        val dados = if (rota == null) {
            atualizar(semRota) { it + chamada }
            resposta.recusa(
                HttpStatusCode.NotFound,
                codigo = "ROTA_NAO_DECLARADA",
                mensagem = "ServidorDeTeste: nenhuma rota para ${chamada.metodo.value} ${chamada.caminho}",
            )
        } else {
            rota.responder(resposta, chamada)
        }

        return when (falha) {
            null, FalhaDeRede.SemConexao -> dados
            FalhaDeRede.GravouERespostaSePerdeu ->
                throw IOException("ServidorDeTeste: a resposta se perdeu (${chamada.metodo.value} ${chamada.caminho})")
            is FalhaDeRede.Demora -> {
                delay(falha.ms)
                dados
            }
            is FalhaDeRede.CorpoCortado -> cortarCorpo(scope, dados, falha.bytes)
        }
    }

    private fun consumirFalha(chamada: ChamadaRecebida): FalhaDeRede? {
        while (true) {
            val atual = falhas.load()
            val indice = atual.indexOfFirst { it.metodo == chamada.metodo && it.modelo.casar(chamada.caminho) != null }
            if (indice < 0) return null
            val agendada = atual[indice]
            val nova = if (agendada.restantes <= 1) {
                atual.filterIndexed { i, _ -> i != indice }
            } else {
                atual.mapIndexed { i, f -> if (i == indice) FalhaAgendada(f.metodo, f.modelo, f.falha, f.restantes - 1) else f }
            }
            if (falhas.compareAndSet(atual, nova)) return agendada.falha
        }
    }

    /** Mesma resposta, com o corpo fechado com erro depois de [bytes] bytes. */
    private suspend fun cortarCorpo(scope: MockRequestHandleScope, dados: HttpResponseData, bytes: Int): HttpResponseData {
        val inteiro = when (val corpo = dados.body) {
            is ByteReadChannel -> corpo.toByteArray()
            is ByteArray -> corpo
            else -> ByteArray(0)
        }
        val canal = ByteChannel(autoFlush = true)
        canal.writeFully(inteiro, 0, bytes.coerceAtMost(inteiro.size))
        canal.cancel(IOException("ServidorDeTeste: corpo cortado depois de $bytes bytes"))
        // O Content-Length do corpo inteiro: é o que o cliente veria de um servidor que caiu no meio.
        val cabecalhos = Headers.build {
            appendAll(dados.headers)
            remove(HttpHeaders.ContentLength)
            append(HttpHeaders.ContentLength, inteiro.size.toString())
        }
        return scope.respond(canal, dados.statusCode, cabecalhos)
    }

    private fun <T> atualizar(ref: AtomicReference<List<T>>, mudar: (List<T>) -> List<T>) {
        while (true) {
            val atual = ref.load()
            if (ref.compareAndSet(atual, mudar(atual))) return
        }
    }

    internal companion object {
        val JSON: Headers = headersOf(HttpHeaders.ContentType, "application/json")
    }
}

/** `/locacoes/{id}/cancelar` → casa `/locacoes/l1/cancelar` e devolve `{"id": "l1"}`. Barra final é ignorada. */
internal class ModeloDeCaminho(modelo: String) {
    private val partes: List<String> = segmentos(modelo)

    init {
        require(modelo.startsWith("/")) { "o caminho começa com '/': $modelo" }
    }

    fun casar(caminho: String): Map<String, String>? {
        val pedacos = segmentos(caminho)
        if (pedacos.size != partes.size) return null
        val parametros = mutableMapOf<String, String>()
        for (i in partes.indices) {
            val p = partes[i]
            if (p.length > 2 && p.startsWith("{") && p.endsWith("}")) {
                if (pedacos[i].isEmpty()) return null
                parametros[p.substring(1, p.length - 1)] = pedacos[i]
            } else if (p != pedacos[i]) {
                return null
            }
        }
        return parametros
    }

    private companion object {
        fun segmentos(caminho: String): List<String> = caminho.trim('/').let { if (it.isEmpty()) emptyList() else it.split('/') }
    }
}

package br.com.codecacto.kmplib.help

import br.com.codecacto.kmplib.core.util.KmpLibCoreInternalApi
import br.com.codecacto.kmplib.core.util.redactMediaUrl
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Proporção em que o vídeo foi gravado. */
enum class HelpVideoFormat {
    VERTICAL,
    HORIZONTAL,
    ;

    internal companion object {
        fun fromWire(value: String?): HelpVideoFormat =
            if (value.equals("horizontal", ignoreCase = true)) HORIZONTAL else VERTICAL
    }
}

/**
 * Um vídeo da Biblioteca.
 *
 * @property flowId o fluxo que o vídeo demonstra, ou `null` em vídeo solto (demonstração, curto).
 * @property url arquivo do vídeo, sempre `https`: item de outro esquema é descartado na leitura.
 */
data class HelpVideo(
    val id: String,
    val flowId: String?,
    val type: String?,
    val title: String,
    val description: String?,
    val format: HelpVideoFormat,
    val url: String,
    val posterUrl: String?,
    val durationSeconds: Int?,
) {
    /** URL sem query nem fragmento: pode levar assinatura. */
    @OptIn(KmpLibCoreInternalApi::class)
    override fun toString(): String =
        "HelpVideo(id=$id, flowId=$flowId, type=$type, format=$format, url=${redactMediaUrl(url)})"
}

/** Uma tarefa que a pessoa faz no produto, escrita como resposta: título, passos, texto e vídeo. */
data class HelpFlow(
    val id: String,
    val slug: String,
    val title: String,
    val summary: String?,
    val steps: List<String>,
    val text: String?,
    val video: HelpVideo?,
)

/**
 * O conteúdo de ajuda do projeto. [active] é o `ativo` do servidor: `false` = projeto desligado, sempre
 * com as listas vazias.
 */
data class HelpCatalog(
    val active: Boolean,
    val flows: List<HelpFlow>,
    val videos: List<HelpVideo>,
)

/** O que o servidor respondeu a uma pergunta. */
sealed interface HelpAnswer {
    /** O fluxo que responde. Com vídeo em [HelpFlow.video], mostra-se o vídeo junto. */
    data class Found(val flow: HelpFlow) : HelpAnswer

    /** Nenhum fluxo responde: a tela oferece o suporte. */
    data object NotFound : HelpAnswer
}

internal val HelpJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = false
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
private data class HelpVideoDto(
    val id: String? = null,
    val fluxoId: String? = null,
    val tipo: String? = null,
    val titulo: String? = null,
    val descricao: String? = null,
    val formato: String? = null,
    val url: String? = null,
    val posterUrl: String? = null,
    val duracaoSeg: Int? = null,
)

@Serializable
private data class HelpFlowDto(
    val id: String? = null,
    val slug: String? = null,
    val titulo: String? = null,
    val resumo: String? = null,
    val passos: List<String> = emptyList(),
    val texto: String? = null,
    val video: JsonElement? = null,
)

private fun isHttps(url: String?): Boolean = url?.startsWith("https://", ignoreCase = true) == true

private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

private fun decodeVideo(element: JsonElement, parentFlow: HelpFlowDto? = null): HelpVideo? {
    if (element is JsonNull) return null
    val dto = runCatching { HelpJson.decodeFromJsonElement(HelpVideoDto.serializer(), element) }.getOrNull()
        ?: return null
    val url = dto.url?.trim()
    if (url == null || !isHttps(url)) return null
    val id = dto.id.orNullIfBlank() ?: parentFlow?.id.orNullIfBlank() ?: return null
    val title = dto.titulo.orNullIfBlank() ?: parentFlow?.titulo.orNullIfBlank() ?: return null
    return HelpVideo(
        id = id,
        flowId = dto.fluxoId.orNullIfBlank() ?: parentFlow?.id.orNullIfBlank(),
        type = dto.tipo.orNullIfBlank(),
        title = title,
        description = dto.descricao.orNullIfBlank(),
        format = HelpVideoFormat.fromWire(dto.formato),
        url = url,
        posterUrl = dto.posterUrl?.trim()?.takeIf(::isHttps),
        durationSeconds = dto.duracaoSeg?.takeIf { it > 0 },
    )
}

private fun decodeFlow(element: JsonElement): HelpFlow? {
    val dto = runCatching { HelpJson.decodeFromJsonElement(HelpFlowDto.serializer(), element) }.getOrNull()
        ?: return null
    val id = dto.id.orNullIfBlank() ?: return null
    val title = dto.titulo.orNullIfBlank() ?: return null
    return HelpFlow(
        id = id,
        slug = dto.slug.orNullIfBlank() ?: id,
        title = title,
        summary = dto.resumo.orNullIfBlank(),
        steps = dto.passos.mapNotNull { it.orNullIfBlank() },
        text = dto.texto.orNullIfBlank(),
        video = dto.video?.let { decodeVideo(it, parentFlow = dto) },
    )
}

private fun parseObject(body: String): JsonObject? =
    runCatching { HelpJson.parseToJsonElement(body) }.getOrNull() as? JsonObject

/**
 * Lê a resposta de `GET /public/ajuda`. `null` = corpo que não é o contrato (não confundir com projeto
 * desligado, que é `active = false`). Item malformado é descartado sem derrubar os demais.
 */
internal fun parseHelpCatalog(body: String): HelpCatalog? {
    val root = parseObject(body) ?: return null
    val active = (root["ativo"] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: return null
    if (!active) return HelpCatalog(active = false, flows = emptyList(), videos = emptyList())
    return HelpCatalog(
        active = true,
        flows = (root["fluxos"] as? JsonArray).orEmpty().mapNotNull(::decodeFlow),
        videos = (root["videos"] as? JsonArray).orEmpty().mapNotNull { decodeVideo(it) },
    )
}

/**
 * Lê a resposta de `POST /public/ajuda/perguntar`. `null` = corpo que não é o contrato. `resultado`
 * desconhecido com um fluxo válido conta como resposta: versão nova do servidor não apaga a resposta do
 * app já publicado.
 */
internal fun parseHelpAnswer(body: String): HelpAnswer? {
    val root = parseObject(body) ?: return null
    val resultado = (root["resultado"] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: return null
    if (resultado == RESULTADO_SEM_RESPOSTA) return HelpAnswer.NotFound
    val flow = root["fluxo"]?.takeIf { it !is JsonNull }?.let(::decodeFlow)
    return if (flow != null) HelpAnswer.Found(flow) else HelpAnswer.NotFound
}

private const val RESULTADO_SEM_RESPOSTA = "sem_resposta"

@Serializable
internal data class HelpAskRequest(
    val project: String,
    val perfil: String? = null,
    val surface: String,
    val lang: String,
    val pergunta: String,
) {
    /** A pergunta é texto livre: pode trazer dado pessoal, e não vai para log. */
    override fun toString(): String = "HelpAskRequest(project=$project, perfil=$perfil, surface=$surface, lang=$lang)"
}

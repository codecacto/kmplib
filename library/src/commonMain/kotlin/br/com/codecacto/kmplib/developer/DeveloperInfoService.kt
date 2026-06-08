package br.com.codecacto.kmplib.developer

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Serviço de leitura do catálogo "Desenvolvido por CodeCacto" via Firestore REST API.
 *
 * Lê dados do projeto Firebase central `code-cacto`, independentemente do projeto
 * Firebase usado pelo app consumidor (mesmo princípio do [br.com.codecacto.kmplib.feedback.FeedbackService]).
 * Por isso usa REST com `projectId`/`apiKey` explícitos — `Firebase.firestore` apontaria
 * para o projeto do app, não para o central.
 *
 * Os defaults já apontam para `code-cacto`, então nenhum app precisa configurar nada.
 * Em caso de falha de rede, retorna fallback (contato padrão + lista vazia) sem lançar exceção.
 */
object DeveloperInfoService {

    private const val TAG = "DeveloperInfoService"

    private const val DEFAULT_PROJECT_ID = "code-cacto"
    // Chave web pública do projeto code-cacto (restrita por regras do Firestore; não é segredo).
    private const val DEFAULT_API_KEY = "AIzaSyDtdk8vEj286ywMUH1nPxLLN97jYKIqi1k"

    private const val APPS_COLLECTION = "developer_apps"
    private const val CONTACT_PATH = "developer_info/contact"

    private var projectId: String = DEFAULT_PROJECT_ID
    private var apiKey: String = DEFAULT_API_KEY

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Opcional: sobrescrever o projeto/chave central. Os defaults já apontam para `code-cacto`.
     */
    fun configure(projectId: String = DEFAULT_PROJECT_ID, apiKey: String = DEFAULT_API_KEY) {
        this.projectId = projectId
        this.apiKey = apiKey
    }

    private fun documentsUrl(path: String): String =
        "https://firestore.googleapis.com/v1/projects/$projectId/databases/(default)/documents/$path?key=$apiKey"

    /** Busca as informações de contato. Sempre retorna sucesso (fallback no erro). */
    suspend fun fetchContact(): Result<DeveloperContact> {
        return try {
            val body = httpGet(documentsUrl(CONTACT_PATH)).getOrThrow()
            val fields = json.parseToJsonElement(body).jsonObject["fields"]?.jsonObject
                ?: return Result.success(DeveloperContact())
            val fallback = DeveloperContact()
            Result.success(
                DeveloperContact(
                    whatsapp = fields.stringValue("whatsapp").ifBlank { fallback.whatsapp },
                    email = fields.stringValue("email").ifBlank { fallback.email },
                    site = fields.stringValue("site"),
                )
            )
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao buscar contato, usando default", e)
            Result.success(DeveloperContact())
        }
    }

    /** Busca os apps ativos, ordenados por `order`. Sempre retorna sucesso (lista vazia no erro). */
    suspend fun fetchApps(): Result<List<DeveloperApp>> {
        return try {
            val body = httpGet(documentsUrl(APPS_COLLECTION)).getOrThrow()
            val documents = json.parseToJsonElement(body).jsonObject["documents"]?.jsonArray
                ?: return Result.success(emptyList())

            val apps = documents.mapNotNull { element ->
                val docObject = element.jsonObject
                val fields = docObject["fields"]?.jsonObject ?: return@mapNotNull null
                val id = docObject["name"]?.jsonPrimitive?.contentOrNull
                    ?.substringAfterLast('/') ?: ""
                DeveloperApp(
                    id = id,
                    name = fields.stringValue("name"),
                    description = fields.stringValue("description"),
                    logoUrl = fields.stringValue("logoUrl"),
                    storeUrl = fields.stringValue("storeUrl"),
                    active = fields.booleanValue("active", default = true),
                    order = fields.intValue("order"),
                )
            }
                .filter { it.active }
                .sortedBy { it.order }

            Result.success(apps)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Falha ao buscar apps", e)
            Result.success(emptyList())
        }
    }

    // --- Helpers de parsing do envelope REST do Firestore (fields -> typedValue) ---

    private fun JsonObject.field(name: String): JsonObject? = this[name]?.jsonObject

    private fun JsonObject.stringValue(name: String): String =
        field(name)?.get("stringValue")?.jsonPrimitive?.contentOrNull ?: ""

    private fun JsonObject.booleanValue(name: String, default: Boolean = false): Boolean =
        field(name)?.get("booleanValue")?.jsonPrimitive?.booleanOrNull ?: default

    private fun JsonObject.intValue(name: String, default: Int = 0): Int =
        field(name)?.get("integerValue")?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: default
}

/** GET HTTP simples; retorna o corpo da resposta como String. Implementado por plataforma. */
internal expect suspend fun httpGet(url: String): Result<String>

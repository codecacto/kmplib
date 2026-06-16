package br.com.codecacto.kmplib.map.route

import br.com.codecacto.kmplib.map.LatLng
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Implementação de [RouteProvider] sobre o **OpenRouteService** (ORS) usando o
 * [HttpClient] (Ktor) injetado pelo app — sem ContentNegotiation, parse manual de JSON
 * (mesma estratégia do `AdminApiEntitlementRepository`, sem acoplar engine).
 *
 * @param httpClient HttpClient já configurado pelo app.
 * @param apiKey chave da API ORS (placeholder em runtime — passar via config do app).
 * @param baseUrl base do ORS (default público).
 * @param profile perfil de roteamento ("driving-car", "driving-hgv" p/ caminhão, etc.).
 */
class OrsRouteProvider(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_ORS_BASE_URL,
    private val profile: String = "driving-car",
    private val json: Json = orsJson,
) : RouteProvider {

    override suspend fun route(origin: LatLng, destination: LatLng): Result<RouteResult> = runCatching {
        // ORS Directions (GeoJSON): POST /v2/directions/{profile}/geojson
        val url = "$baseUrl/v2/directions/$profile/geojson"
        val body = """
            {"coordinates":[[${origin.longitude},${origin.latitude}],[${destination.longitude},${destination.latitude}]]}
        """.trimIndent()
        val text = httpClient.post(url) {
            header("Authorization", apiKey)
            contentType(ContentType.Application.Json)
            setBody(body)
        }.bodyAsText()

        val root = json.parseToJsonElement(text).jsonObject
        val feature = root["features"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("ORS: resposta sem features")
        val summary = feature["properties"]?.jsonObject?.get("summary")?.jsonObject
        val distance = summary?.get("distance")?.jsonPrimitive?.doubleOrNull ?: 0.0
        val duration = summary?.get("duration")?.jsonPrimitive?.doubleOrNull
        val coords = feature["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
        val polyline = coords?.map { pair ->
            val arr = pair.jsonArray
            // ORS = [lon, lat]
            LatLng(latitude = arr[1].jsonPrimitive.double, longitude = arr[0].jsonPrimitive.double)
        } ?: emptyList()

        RouteResult(
            distanceMeters = distance.toLong(),
            durationSeconds = duration?.toLong(),
            polyline = polyline,
        )
    }
}

/**
 * Implementação de [GeocodingProvider] sobre o **OpenRouteService** (Pelias geocoding).
 *
 * @param apiKey chave da API ORS.
 * @param boundaryCountry filtra por país (ISO; default "BR").
 */
class OrsGeocodingProvider(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = DEFAULT_ORS_BASE_URL,
    private val boundaryCountry: String? = "BR",
    private val json: Json = orsJson,
) : GeocodingProvider {

    override suspend fun search(query: String, near: LatLng?): Result<List<GeocodeResult>> = runCatching {
        if (query.isBlank()) return@runCatching emptyList()
        val sb = StringBuilder("$baseUrl/geocode/search?api_key=$apiKey&text=${query.encodeURLParameter()}")
        boundaryCountry?.let { sb.append("&boundary.country=$it") }
        near?.let { sb.append("&focus.point.lon=${it.longitude}&focus.point.lat=${it.latitude}") }
        val text = httpClient.get(sb.toString()).bodyAsText()
        parseFeatures(text)
    }

    override suspend fun reverse(position: LatLng): Result<GeocodeResult> = runCatching {
        val url = "$baseUrl/geocode/reverse?api_key=$apiKey" +
            "&point.lon=${position.longitude}&point.lat=${position.latitude}&size=1"
        val text = httpClient.get(url).bodyAsText()
        parseFeatures(text).firstOrNull()
            ?: GeocodeResult(label = "${position.latitude}, ${position.longitude}", position = position)
    }

    private fun parseFeatures(text: String): List<GeocodeResult> {
        val root = json.parseToJsonElement(text).jsonObject
        val features = root["features"]?.jsonArray ?: return emptyList()
        return features.mapNotNull { it.toGeocodeResult() }
    }

    private fun kotlinx.serialization.json.JsonElement.toGeocodeResult(): GeocodeResult? {
        val obj = this as? JsonObject ?: return null
        val geometry = obj["geometry"]?.jsonObject ?: return null
        val coords = geometry["coordinates"]?.jsonArray ?: return null
        val lon = coords.getOrNull(0)?.jsonPrimitive?.doubleOrNull ?: return null
        val lat = coords.getOrNull(1)?.jsonPrimitive?.doubleOrNull ?: return null
        val props = obj["properties"]?.jsonObject
        val label = props?.get("label")?.jsonPrimitive?.contentOrNull
            ?: props?.get("name")?.jsonPrimitive?.contentOrNull
            ?: "$lat, $lon"
        val region = props?.get("region")?.jsonPrimitive?.contentOrNull
        val locality = props?.get("locality")?.jsonPrimitive?.contentOrNull
        val secondary = listOfNotNull(locality, region).joinToString(" · ").ifBlank { null }
        return GeocodeResult(label = label, position = LatLng(lat, lon), secondaryLabel = secondary)
    }

    @Suppress("unused")
    private fun JsonArray.firstNum(idx: Int): Double? = getOrNull(idx)?.jsonPrimitive?.doubleOrNull
}

/** Base pública do OpenRouteService. */
const val DEFAULT_ORS_BASE_URL: String = "https://api.openrouteservice.org"

/** Json tolerante para parse das respostas ORS. */
internal val orsJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

@Suppress("unused")
private fun JsonObject.intField(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

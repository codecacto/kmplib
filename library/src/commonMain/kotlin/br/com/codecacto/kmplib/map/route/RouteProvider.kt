package br.com.codecacto.kmplib.map.route

import br.com.codecacto.kmplib.map.LatLng

/**
 * Resultado de cálculo de rota entre dois pontos.
 *
 * @param distanceMeters distância da rota em metros.
 * @param durationSeconds duração estimada em segundos (NULL se o provedor não retornar).
 * @param polyline pontos da geometria da rota (para desenhar no mapa).
 */
data class RouteResult(
    val distanceMeters: Long,
    val durationSeconds: Long? = null,
    val polyline: List<LatLng> = emptyList(),
) {
    /** Distância em km com 1 casa (apresentação). */
    val distanceKm: Double get() = distanceMeters / 1000.0
}

/**
 * Resultado de geocoding (endereço/POI → coordenada) ou reverse.
 *
 * @param label rótulo principal (endereço formatado / nome do POI).
 * @param position coordenada.
 * @param secondaryLabel linha secundária (cidade/UF/CEP), opcional.
 */
data class GeocodeResult(
    val label: String,
    val position: LatLng,
    val secondaryLabel: String? = null,
)

/**
 * Provedor de rota entre origem e destino. Genérico — a impl ([OrsRouteProvider]) usa
 * OpenRouteService, mas o app pode injetar qualquer outra (Google, Mapbox).
 *
 * Best-effort: sem rede / falha do serviço → [Result.failure] (commonMain puro).
 */
interface RouteProvider {
    suspend fun route(origin: LatLng, destination: LatLng): Result<RouteResult>
}

/**
 * Provedor de geocoding/autocomplete e reverse geocoding. Genérico.
 *
 * @see OrsGeocodingProvider
 */
interface GeocodingProvider {
    /** Busca endereços/POIs por texto. [near] enviesa por proximidade (opcional). */
    suspend fun search(query: String, near: LatLng? = null): Result<List<GeocodeResult>>

    /** Reverse geocoding: coordenada → endereço. */
    suspend fun reverse(position: LatLng): Result<GeocodeResult>
}

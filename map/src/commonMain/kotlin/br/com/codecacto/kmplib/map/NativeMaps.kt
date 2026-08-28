package br.com.codecacto.kmplib.map

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Wrappers de alto nível dos mapas **nativos (Google Maps SDK)** — Android via `maps-compose`,
 * iOS via `GMSMapView` (cinterop). São o padrão-ouro de mapa do ecossistema; espelham os 3 modos
 * que a web expõe (`MapInner.tsx`) para garantir paridade web=Android=iOS:
 *  - [MarkersMap] — N pins, toque → navega (espelha `ClientsMap`).
 *  - [SinglePinMap] — 1 pin só-leitura (espelha `SinglePinMap`).
 *  - [PickerMap] — toque no mapa escolhe a coordenada (espelha `PickerMap`).
 *
 * Substituem o caminho legado em WebView (`map.osm.OsmMap`), que fica reservado para apps que
 * precisem explicitamente de OpenStreetMap sem chave.
 */
object MapDefaults {
    /** Centro padrão Brasil/São Paulo (igual ao `DEFAULT_CENTER` da web). */
    val BRAZIL_CENTER = LatLng(-23.55052, -46.633308)

    /** Zoom de cidade (~12). */
    const val CITY_ZOOM = 12f

    /** Zoom de rua/quadra (~15). */
    const val STREET_ZOOM = 15f

    /** Zoom de país (~5), usado quando não há nenhum pin. */
    const val COUNTRY_ZOOM = 5f
}

/** Pin para o [MarkersMap]. */
@Immutable
data class MapPin(
    val id: String,
    val position: LatLng,
    val title: String = "",
)

/**
 * Mapa nativo com N pins; toque no pin chama [onMarkerClick] com o [MapPin.id].
 * Centraliza no primeiro pin (ou em [center] quando vazio).
 */
@Composable
fun MarkersMap(
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    center: LatLng = MapDefaults.BRAZIL_CENTER,
    zoom: Float = MapDefaults.CITY_ZOOM,
    height: Dp = 360.dp,
    onMarkerClick: (String) -> Unit = {},
) {
    val hasPins = pins.isNotEmpty()
    MapView(
        modifier = modifier.fillMaxWidth().height(height),
        cameraPosition = CameraPosition(
            target = if (hasPins) pins.first().position else center,
            zoom = if (hasPins) zoom else MapDefaults.COUNTRY_ZOOM,
        ),
    ) {
        pins.forEach { pin ->
            MapMarker(position = pin.position, title = pin.title, onClick = { onMarkerClick(pin.id) })
        }
    }
}

/** Mapa nativo só-leitura com 1 pin (ficha do cliente). */
@Composable
fun SinglePinMap(
    point: LatLng,
    modifier: Modifier = Modifier,
    label: String = "",
    zoom: Float = MapDefaults.STREET_ZOOM,
    height: Dp = 220.dp,
) {
    MapView(
        modifier = modifier.fillMaxWidth().height(height),
        cameraPosition = CameraPosition(target = point, zoom = zoom),
    ) {
        MapMarker(position = point, title = label)
    }
}

/**
 * Mini-mapa nativo para escolher uma coordenada (form de cliente). Toque no mapa devolve a
 * coordenada via [onPick] e o pin é desenhado em [value]. Centraliza em [value] (ou no centro
 * padrão quando nulo).
 */
@Composable
fun PickerMap(
    value: LatLng?,
    onPick: (LatLng) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    zoomWithValue: Float = MapDefaults.STREET_ZOOM,
    zoomEmpty: Float = MapDefaults.CITY_ZOOM,
) {
    MapView(
        modifier = modifier.fillMaxWidth().height(height),
        cameraPosition = CameraPosition(
            target = value ?: MapDefaults.BRAZIL_CENTER,
            zoom = if (value != null) zoomWithValue else zoomEmpty,
        ),
        onMapClick = { onPick(it) },
    ) {
        if (value != null) MapMarker(position = value)
    }
}

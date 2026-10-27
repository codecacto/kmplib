@file:Suppress("DEPRECATION") // o próprio pacote obsoleto usa os seus tipos (2.330.0)

package br.com.codecacto.kmplib.map.osm

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Mensagem comum do `@Deprecated` do pacote `map.osm` (2.330.0).
 *
 * Decisão do fundador, 27/out/2026: **"a gente só usa Google Maps, nenhum outro; o padrão da fábrica é
 * o Google Maps"**. O mapa da fábrica é o nativo deste mesmo módulo (`NativeMap`, `MarkersMap`,
 * `SinglePinMap`, `PickerMap`, `MapView`). O pacote `map.osm` continua compilando e funcionando
 * igual até a próxima major, quando sai (`docs/backlog.md`).
 */
internal const val OSM_DEPRECATION_MESSAGE =
    "Obsoleto (2.330.0): mapa na fábrica = Google Maps, nenhum outro (fundador, 27/out/2026). " +
        "Use o mapa nativo do módulo map (MarkersMap/SinglePinMap/PickerMap/NativeMap). " +
        "O map.osm (WebView + Leaflet + tiles do OpenStreetMap) sai na próxima major."

/**
 * Mapa OpenStreetMap multiplataforma (Compose MP) — **sem chave de API**.
 *
 * ## ⚠️ OBSOLETO desde a 2.330.0 — mapa na fábrica = Google Maps
 * Decisão do fundador (27/out/2026): *"a gente só usa Google Maps, nenhum outro"*. Use
 * [br.com.codecacto.kmplib.map.MarkersMap] (N pins), [br.com.codecacto.kmplib.map.SinglePinMap],
 * [br.com.codecacto.kmplib.map.PickerMap] ou [br.com.codecacto.kmplib.map.NativeMap] (carteira,
 * agrupamento, rota). Este pacote segue funcionando, sem mudança de comportamento, até a próxima major.
 *
 * ## Por que existe (e por que não é o [br.com.codecacto.kmplib.map.MapView])
 * A lib já tem um [br.com.codecacto.kmplib.map.MapView] baseado em **Google Maps**
 * (GAP-02, app Exiba). Esse caminho exige `play-services-maps` + uma **API key**
 * faturável e a impl iOS ainda é um placeholder. Para a **paridade web↔mobile do
 * Influencer** o requisito é outro:
 *  - **mesmo stack da web** (`react-leaflet` + tiles do OpenStreetMap — ver
 *    `Influencer/web/src/components/map/MapInner.tsx`);
 *  - **sem nenhuma chave paga**;
 *  - **iOS funcional desde já** (não pode ficar "para depois").
 *
 * A forma mais pragmática de garantir os três é **renderizar o próprio Leaflet
 * dentro de um WebView** (Android `WebView` / iOS `WKWebView`). O HTML/JS é o mesmo
 * Leaflet + os mesmos tiles `tile.openstreetmap.org` da web — fidelidade visual
 * praticamente 1:1, zero SDK nativo de mapa, zero key. O WebView é exposto via
 * [br.com.codecacto.kmplib.platform.webview.WebViewHost] (expect/actual), reutilizável
 * por qualquer outra feature que precise embutir HTML.
 *
 * ## ⚠️ Política de uso dos tiles do OSM (conferida em 27/out/2026)
 * A política da OSMF trata **distribuir um app** que usa `tile.openstreetmap.org` como uso pesado,
 * proibido **sem permissão prévia** do Operations Working Group — independente do volume. A lib
 * cumpre a parte técnica (host único, User-Agent com o id do app, atribuição, cache do WebView,
 * sem recarregar o mapa a cada recomposição — 2.329.0), mas a permissão (ou um provedor de tiles
 * contratado, ou o mapa nativo [br.com.codecacto.kmplib.map.NativeMap] com chave do Google Maps)
 * é decisão do produto. App NOVO usa o `NativeMap`.
 *
 * ## Três modos (espelham os componentes da web)
 *  - [OsmMap] (este) — mapa genérico com **N pins**; clique no pin → [onMarkerClick]
 *    (espelha `ClientsMap`).
 *  - [OsmSinglePinMap] — **1 pin** só-leitura (espelha `SinglePinMap`).
 *  - [OsmPickerMap] — **escolher localização**: clique no mapa devolve a coordenada
 *    (espelha `PickerMap`, usado no form de cliente).
 *
 * Todos aceitam centro/zoom parametrizáveis. A comunicação JS↔Kotlin (clique em pin /
 * clique no mapa) trafega por uma bridge de mensagens do WebView.
 *
 * @param markers pins a exibir. Lista vazia → mapa centrado em [center].
 * @param modifier modificador Compose.
 * @param center centro inicial da câmera. Default: São Paulo (igual ao web).
 * @param zoom zoom inicial (1 = mundo, ~12 = cidade, ~15 = rua).
 * @param height altura do mapa (o WebView precisa de altura definida).
 * @param onMarkerClick chamado com o [OsmMarker.id] do pin tocado.
 */
@Deprecated(
    message = OSM_DEPRECATION_MESSAGE,
    replaceWith = ReplaceWith(
        "MarkersMap(pins = markers.map { MapPin(it.id, LatLng(it.position.latitude, it.position.longitude), it.title) }, " +
            "modifier = modifier, center = LatLng(center.latitude, center.longitude), zoom = zoom, height = height, " +
            "onMarkerClick = onMarkerClick)",
        "br.com.codecacto.kmplib.map.MarkersMap",
        "br.com.codecacto.kmplib.map.MapPin",
        "br.com.codecacto.kmplib.map.LatLng",
    ),
    level = DeprecationLevel.WARNING,
)
@Composable
fun OsmMap(
    markers: List<OsmMarker>,
    modifier: Modifier = Modifier,
    center: OsmLatLng = OsmDefaults.BRAZIL_CENTER,
    zoom: Float = OsmDefaults.CITY_ZOOM,
    height: Dp = 360.dp,
    onMarkerClick: (markerId: String) -> Unit = {},
) {
    OsmMapWebView(
        spec = OsmMapSpec(
            markers = markers,
            center = center,
            zoom = zoom,
            interactive = true,
            pickable = false,
        ),
        modifier = modifier,
        height = height,
        onMarkerClick = onMarkerClick,
        onMapClick = null,
    )
}

/**
 * Mapa OSM só-leitura com **um único pin** (espelha `SinglePinMap` da web — ficha do
 * cliente). Centraliza no ponto e desabilita o gesto de scroll por padrão.
 *
 * @param point coordenada do pin.
 * @param label rótulo opcional exibido no popup do pin.
 * @param zoom zoom inicial (default ~15 = rua, igual ao web).
 * @param scrollEnabled habilita arrastar/zoom por gesto (default `false`, como no web).
 */
@Deprecated(
    message = OSM_DEPRECATION_MESSAGE + " O SinglePinMap nativo não tem scrollEnabled.",
    replaceWith = ReplaceWith(
        "SinglePinMap(point = LatLng(point.latitude, point.longitude), modifier = modifier, " +
            "label = label.orEmpty(), zoom = zoom, height = height)",
        "br.com.codecacto.kmplib.map.SinglePinMap",
        "br.com.codecacto.kmplib.map.LatLng",
    ),
    level = DeprecationLevel.WARNING,
)
@Composable
fun OsmSinglePinMap(
    point: OsmLatLng,
    modifier: Modifier = Modifier,
    label: String? = null,
    zoom: Float = OsmDefaults.STREET_ZOOM,
    height: Dp = 220.dp,
    scrollEnabled: Boolean = false,
) {
    OsmMapWebView(
        spec = OsmMapSpec(
            markers = listOf(OsmMarker(id = "single", position = point, title = label.orEmpty())),
            center = point,
            zoom = zoom,
            interactive = scrollEnabled,
            pickable = false,
        ),
        modifier = modifier,
        height = height,
        onMarkerClick = {},
        onMapClick = null,
    )
}

/**
 * Mini-mapa **clicável para escolher uma localização** (espelha `PickerMap` da web —
 * form de cliente). O clique no mapa devolve a coordenada via [onPick] e move o pin.
 *
 * @param value coordenada atualmente selecionada (ou `null` → sem pin, centro padrão).
 * @param onPick chamado com a coordenada tocada no mapa.
 * @param zoomWithValue zoom usado quando há [value] selecionado.
 * @param zoomEmpty zoom usado quando ainda não há seleção.
 */
@Deprecated(
    message = OSM_DEPRECATION_MESSAGE,
    replaceWith = ReplaceWith(
        "PickerMap(value = value?.let { LatLng(it.latitude, it.longitude) }, " +
            "onPick = { onPick(OsmLatLng(it.latitude, it.longitude)) }, modifier = modifier, height = height, " +
            "zoomWithValue = zoomWithValue, zoomEmpty = zoomEmpty)",
        "br.com.codecacto.kmplib.map.PickerMap",
        "br.com.codecacto.kmplib.map.LatLng",
    ),
    level = DeprecationLevel.WARNING,
)
@Composable
fun OsmPickerMap(
    value: OsmLatLng?,
    onPick: (OsmLatLng) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    zoomWithValue: Float = OsmDefaults.STREET_ZOOM,
    zoomEmpty: Float = OsmDefaults.CITY_ZOOM,
) {
    OsmMapWebView(
        spec = OsmMapSpec(
            markers = value?.let { listOf(OsmMarker(id = "pick", position = it)) } ?: emptyList(),
            center = value ?: OsmDefaults.BRAZIL_CENTER,
            zoom = if (value != null) zoomWithValue else zoomEmpty,
            interactive = true,
            pickable = true,
        ),
        modifier = modifier,
        height = height,
        onMarkerClick = {},
        onMapClick = onPick,
    )
}

/**
 * Composable interno comum: monta o HTML do Leaflet a partir do [spec] e o entrega
 * ao [br.com.codecacto.kmplib.platform.webview.WebViewHost] da plataforma, fiando os
 * callbacks de clique. Não é API pública — use [OsmMap]/[OsmSinglePinMap]/[OsmPickerMap].
 */
@Composable
internal expect fun OsmMapWebView(
    spec: OsmMapSpec,
    modifier: Modifier,
    height: Dp,
    onMarkerClick: (String) -> Unit,
    onMapClick: ((OsmLatLng) -> Unit)?,
)

/** Coordenada geográfica simples (latitude/longitude) usada pelos mapas OSM. */
@Deprecated(
    message = OSM_DEPRECATION_MESSAGE,
    replaceWith = ReplaceWith("LatLng", "br.com.codecacto.kmplib.map.LatLng"),
    level = DeprecationLevel.WARNING,
)
@Immutable
data class OsmLatLng(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Marcador (pin) do [OsmMap].
 *
 * @param id identificador estável devolvido em [OsmMap.onMarkerClick].
 * @param position coordenada do pin.
 * @param title texto opcional exibido no popup ao tocar o pin.
 */
@Deprecated(
    message = OSM_DEPRECATION_MESSAGE,
    replaceWith = ReplaceWith("MapPin", "br.com.codecacto.kmplib.map.MapPin"),
    level = DeprecationLevel.WARNING,
)
@Immutable
data class OsmMarker(
    val id: String,
    val position: OsmLatLng,
    val title: String = "",
)

/** Spec interna serializável da renderização (centro/zoom/pins/modos). */
@Immutable
internal data class OsmMapSpec(
    val markers: List<OsmMarker>,
    val center: OsmLatLng,
    val zoom: Float,
    /** `true` permite arrastar/zoom por gesto. */
    val interactive: Boolean,
    /** `true` clica no mapa para escolher coordenada (modo picker). */
    val pickable: Boolean,
)

/** Defaults de centro/zoom — alinhados ao web (`MapInner.tsx`). */
@Deprecated(
    message = OSM_DEPRECATION_MESSAGE,
    replaceWith = ReplaceWith("MapDefaults", "br.com.codecacto.kmplib.map.MapDefaults"),
    level = DeprecationLevel.WARNING,
)
object OsmDefaults {
    /** Centro padrão Brasil/São Paulo (igual ao `DEFAULT_CENTER` da web). */
    val BRAZIL_CENTER = OsmLatLng(-23.55052, -46.633308)

    /** Zoom de cidade (~12). */
    const val CITY_ZOOM = 12f

    /** Zoom de rua/quadra (~15). */
    const val STREET_ZOOM = 15f
}

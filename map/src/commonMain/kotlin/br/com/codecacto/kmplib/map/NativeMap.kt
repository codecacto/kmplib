package br.com.codecacto.kmplib.map

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import br.com.codecacto.kmplib.ui.theme.ColorContrast

/** Pino com a aparência já resolvida (tema aplicado, glifo cortado, contraste escolhido). */
@Immutable
internal data class ResolvedMarker(
    val item: MapItem,
    val color: Color,
    val glyph: String?,
    val glyphColor: Color,
)

/** Linha com a cor já resolvida. */
@Immutable
internal data class ResolvedPolyline(
    val id: String,
    val points: List<LatLng>,
    val color: Color,
    val widthDp: Float,
)

/** Aparência final de um pino — pura e testada. */
internal fun resolveMarker(item: MapItem, defaultColor: Color): ResolvedMarker {
    val color = (item.style?.color ?: defaultColor).copy(alpha = 1f)
    return ResolvedMarker(
        item = item,
        color = color,
        glyph = limitGlyph(item.style?.glyph),
        glyphColor = item.style?.glyphColor ?: ColorContrast.pickOnColor(color),
    )
}

/** Aparência de um GRUPO: a cor do membro mais grave e o contador. */
internal data class ResolvedCluster(val color: Color, val label: String, val labelColor: Color)

internal fun resolveCluster(members: List<ResolvedMarker>, defaultColor: Color): ResolvedCluster {
    val representative = clusterRepresentative(members.map { it.item })
    val color = members.firstOrNull { it.item == representative }?.color ?: defaultColor
    return ResolvedCluster(color, clusterCountLabel(members.size), ColorContrast.pickOnColor(color))
}

/** O que fazer no toque de um grupo quando o app não passou `onClusterClick`. */
internal sealed interface ClusterTapAction {
    /** Aproximar até separar os pinos. */
    data class ZoomIn(val points: List<LatLng>) : ClusterTapAction

    /** Estão todos no mesmo ponto: nenhum zoom separa — abre o mais grave. */
    data class OpenItem(val item: MapItem) : ClusterTapAction
}

internal fun defaultClusterTap(members: List<MapItem>): ClusterTapAction? {
    if (members.isEmpty()) return null
    val bounds = LatLngBounds.of(members.map { it.position })
    return if (bounds == null || bounds.isSinglePoint) {
        ClusterTapAction.OpenItem(clusterRepresentative(members)!!)
    } else {
        ClusterTapAction.ZoomIn(members.map { it.position })
    }
}

/**
 * **Mapa nativo de carteira** (2.220.0 — GAP-ER-01): muitos pinos coloridos por item, agrupamento,
 * trajeto de rota e a posição do usuário, com a MESMA API nos dois sistemas.
 *
 * - **Android:** Google Maps SDK via `maps-compose`; agrupamento pelo `ClusterManager` oficial do
 *   `maps-compose-utils` (android-maps-utils). Exige a chave `com.google.android.geo.API_KEY` no
 *   manifesto do app.
 * - **iOS:** **MapKit** (`MKMapView`), o SDK oficial da Apple — sem chave, sem pacote SPM, sem ponte
 *   Swift; agrupamento pelo `clusteringIdentifier` nativo (iOS 11+).
 *
 * Comportamento:
 * - **Pino**: cor e texto curto por item ([MapMarkerStyle]); toque → [onItemClick] (o balão com
 *   [MapItem.title] abre junto).
 * - **Grupo** ([clustering] = `true`): pinos próximos viram um círculo com o contador, **na cor do
 *   membro mais grave** ([MapItem.priority]). Toque → [onClusterClick] se informado; senão aproxima
 *   até separar — e, se os pinos estiverem no MESMO ponto (vários itens no mesmo endereço, que zoom
 *   nenhum separa), abre o mais grave via [onItemClick]. Para listar todos, passe [onClusterClick].
 * - **Rota**: [polylines] desenhadas por baixo dos pinos; numere as paradas com
 *   `MapMarkerStyle(glyph = "1")`, `"2"`…
 * - **Câmera**: [fitOnFirstLoad] enquadra pinos + rotas na primeira vez que houver dados; depois,
 *   quem manda é o [controller] (`fitTo`, `moveTo`) — recarregar a lista não arranca a câmera de
 *   onde o usuário a deixou.
 * - **Posição do usuário** ([showUserLocation]): o ponto azul do sistema, **só se a permissão já foi
 *   concedida** — o mapa não pede permissão; peça antes com `createLocationProvider()` (módulo
 *   `location`). No iOS o `Info.plist` precisa de `NSLocationWhenInUseUsageDescription`.
 *
 * @param items pinos. Mudar a lista redesenha só o que mudou (chave = [MapItem.id]).
 */
@Composable
fun NativeMap(
    items: List<MapItem>,
    modifier: Modifier = Modifier,
    controller: MapController = rememberMapController(),
    polylines: List<MapPolyline> = emptyList(),
    clustering: Boolean = true,
    showUserLocation: Boolean = false,
    fitOnFirstLoad: Boolean = true,
    onItemClick: (MapItem) -> Unit = {},
    onClusterClick: ((List<MapItem>) -> Unit)? = null,
    onMapClick: ((LatLng) -> Unit)? = null,
    onMapLongClick: ((LatLng) -> Unit)? = null,
) {
    val primary = MaterialTheme.colorScheme.primary
    val markers = remember(items, primary) { items.map { resolveMarker(it, primary) } }
    val lines = remember(polylines, primary) {
        polylines.map { ResolvedPolyline(it.id, it.points, (it.color ?: primary).copy(alpha = 1f), it.width.value) }
    }

    var fitted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(items, polylines, fitOnFirstLoad) {
        if (fitOnFirstLoad && !fitted) {
            val points = items.map { it.position } + polylines.flatMap { it.points }
            if (points.isNotEmpty()) {
                controller.fitTo(points, animate = false)
                fitted = true
            }
        }
    }

    val itemClick by rememberUpdatedState(onItemClick)
    val clusterClick by rememberUpdatedState(onClusterClick)
    val handleCluster: (List<MapItem>) -> Unit = remember(controller) {
        { members ->
            val custom = clusterClick
            if (custom != null) {
                custom(members)
            } else {
                when (val action = defaultClusterTap(members)) {
                    is ClusterTapAction.ZoomIn -> controller.fitTo(action.points)
                    is ClusterTapAction.OpenItem -> itemClick(action.item)
                    null -> Unit
                }
            }
        }
    }

    PlatformNativeMap(
        markers = markers,
        polylines = lines,
        controller = controller,
        clustering = clustering,
        showUserLocation = showUserLocation,
        defaultColor = primary,
        onMarkerClick = { itemClick(it) },
        onClusterClick = handleCluster,
        onMapClick = onMapClick,
        onMapLongClick = onMapLongClick,
        modifier = modifier,
    )
}

/** Implementação nativa: `maps-compose` no Android, MapKit no iOS. */
@Composable
internal expect fun PlatformNativeMap(
    markers: List<ResolvedMarker>,
    polylines: List<ResolvedPolyline>,
    controller: MapController,
    clustering: Boolean,
    showUserLocation: Boolean,
    defaultColor: Color,
    onMarkerClick: (MapItem) -> Unit,
    onClusterClick: (List<MapItem>) -> Unit,
    onMapClick: ((LatLng) -> Unit)?,
    onMapLongClick: ((LatLng) -> Unit)?,
    modifier: Modifier,
)

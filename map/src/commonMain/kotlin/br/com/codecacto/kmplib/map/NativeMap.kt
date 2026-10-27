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

/** Círculo com as cores já resolvidas (tema aplicado) e o raio validado. */
@Immutable
internal data class ResolvedCircle(
    val id: String,
    val center: LatLng,
    val radiusMeters: Double,
    val fillColor: Color,
    val strokeColor: Color,
    val strokeWidthDp: Float,
)

/** Opacidade do preenchimento default de um [MapCircle] sem [MapCircle.fillColor]. */
internal const val DEFAULT_CIRCLE_FILL_ALPHA: Float = 0.15f

/** Aparência final de um círculo — pura e testada. `null` = centro ou raio inválidos (não desenha). */
internal fun resolveCircle(circle: MapCircle, defaultColor: Color): ResolvedCircle? {
    if (!circle.center.isValidCoordinate()) return null
    val r = circle.radiusMeters
    if (r.isNaN() || r.isInfinite() || r <= 0.0) return null
    return ResolvedCircle(
        id = circle.id,
        center = circle.center,
        radiusMeters = r,
        fillColor = circle.fillColor ?: defaultColor.copy(alpha = DEFAULT_CIRCLE_FILL_ALPHA),
        strokeColor = circle.strokeColor ?: defaultColor.copy(alpha = 1f),
        strokeWidthDp = circle.strokeWidth.value.coerceAtLeast(0f),
    )
}

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
 * - **iOS:** **Google Maps pela ponte** (2.331.0) — `IosMapBridge.register(factory:)` no `AppDelegate`
 *   (a `casca-mobile` nasce assim); a lib agrupa (mesma régua de 100 pt do Android), desenha os ícones
 *   e enquadra, e a `GMSMapView` do Swift só desenha. Sem a ponte, **fallback MapKit** (`MKMapView`,
 *   agrupamento pelo `clusteringIdentifier`) com aviso no log uma vez por processo — app ainda não
 *   migrado, troca quando for tocado.
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
 * - **Círculos** ([circles], 2.312.0): raio REAL em metros (Google Maps `Circle` / MapKit `MKCircle`),
 *   por baixo das rotas e dos pinos; mudar raio, centro ou cor de um [MapCircle] atualiza só ele.
 * - **Câmera**: [fitOnFirstLoad] enquadra pinos + rotas na primeira vez que houver dados; depois,
 *   quem manda é o [controller] (`fitTo`, `moveTo`) — recarregar a lista não arranca a câmera de
 *   onde o usuário a deixou.
 * - **Posição do usuário** ([showUserLocation]): o ponto azul do sistema, **só se a permissão já foi
 *   concedida** — o mapa não pede permissão; peça antes com `createLocationProvider()` (módulo
 *   `location`). No iOS o `Info.plist` precisa de `NSLocationWhenInUseUsageDescription`.
 *
 * @param items pinos. Mudar a lista redesenha só o que mudou (chave = [MapItem.id]).
 * @param circles círculos de raio em metros (chave = [MapCircle.id]); entram no enquadramento inicial.
 * @param interactive `false` = mapa **só leitura** (2.331.0): sem arrastar, pinçar, girar nem inclinar,
 *   e câmera sem animação. No Android vira o **modo lite** do Google Maps (respeita cantos
 *   arredondados) e, por isso, **sem agrupamento** — todos os pinos desenhados, o mais grave por cima;
 *   no iOS os gestos saem da `GMSMapView` (ponte) ou do MapKit (fallback). Toque no pino continua
 *   chegando ao [onItemClick].
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
    circles: List<MapCircle> = emptyList(),
    interactive: Boolean = true,
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

    val rings = remember(circles, primary) { circles.mapNotNull { resolveCircle(it, primary) } }

    var fitted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(items, polylines, circles, fitOnFirstLoad) {
        if (fitOnFirstLoad && !fitted) {
            val points = items.map { it.position } + polylines.flatMap { it.points } + circles.flatMap { it.extremePoints() }
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
        circles = rings,
        controller = controller,
        clustering = clustering,
        showUserLocation = showUserLocation,
        interactive = interactive,
        defaultColor = primary,
        onMarkerClick = { itemClick(it) },
        onClusterClick = handleCluster,
        onMapClick = onMapClick,
        onMapLongClick = onMapLongClick,
        modifier = modifier,
    )
}

/** Implementação nativa: `maps-compose` no Android; no iOS, Google pela ponte (MapKit de fallback). */
@Composable
internal expect fun PlatformNativeMap(
    markers: List<ResolvedMarker>,
    polylines: List<ResolvedPolyline>,
    circles: List<ResolvedCircle>,
    controller: MapController,
    clustering: Boolean,
    showUserLocation: Boolean,
    interactive: Boolean,
    defaultColor: Color,
    onMarkerClick: (MapItem) -> Unit,
    onClusterClick: (List<MapItem>) -> Unit,
    onMapClick: ((LatLng) -> Unit)?,
    onMapLongClick: ((LatLng) -> Unit)?,
    modifier: Modifier,
)

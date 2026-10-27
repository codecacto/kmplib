package br.com.codecacto.kmplib.map

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// ---------------------------------------------------------------------------------------------
// Planejamento dos pinos e da câmera do NativeMap quando quem desenha é um SDK SEM agrupamento
// próprio — o Google Maps do iPhone, pela ponte `IosMapBridge` (2.331.0). Puro e testado: o lado
// nativo só desenha o plano.
// ---------------------------------------------------------------------------------------------

/**
 * Distância (em pontos de tela) dentro da qual pinos viram um grupo — a mesma régua do
 * `NonHierarchicalDistanceBasedAlgorithm` do android-maps-utils (100 dp), para o agrupamento do
 * iPhone e o do Android se comportarem igual.
 */
internal const val CLUSTER_DISTANCE_POINTS: Double = 100.0

/** O que desenhar: um pino de item ou um grupo. */
internal sealed interface PlannedPin {
    /** Identificador estável do pino no mapa nativo (é o que volta no toque). */
    val key: String
    val position: LatLng

    data class Single(val marker: ResolvedMarker) : PlannedPin {
        override val key: String get() = marker.item.id
        override val position: LatLng get() = marker.item.position
    }

    data class Group(override val key: String, override val position: LatLng, val members: List<ResolvedMarker>) : PlannedPin
}

/** Prefixo da chave de um grupo — não colide com id de item porque nenhum id começa com o separador. */
internal const val GROUP_KEY_PREFIX: String = "\u0000grupo:"

/** Posição na projeção Web Mercator, em pontos de tela no [zoom] dado (mundo = 256 × 2^zoom). */
internal fun mercatorPoint(position: LatLng, zoom: Double): Pair<Double, Double> {
    val world = WORLD_TILE_POINTS * 2.0.pow(zoom)
    val x = (position.longitude + 180.0) / 360.0 * world
    val lat = position.latitude.coerceIn(-85.05112878, 85.05112878)
    val s = sin(lat * PI / 180.0)
    val y = (0.5 - ln((1 + s) / (1 - s)) / (4 * PI)) * world
    return x to y
}

/**
 * Agrupa [markers] para o [zoom] da câmera.
 *
 * - [clustering] desligado → um pino por item válido.
 * - Ligado: percorre os itens do mais grave para o menos grave (empate pelo id, para o resultado não
 *   depender da ordem da lista); cada item ainda livre abre um grupo com os livres a até
 *   [CLUSTER_DISTANCE_POINTS]/2 dele em cada eixo (o quadrado do android-maps-utils). Grupo de um só
 *   vira pino. O grupo fica na posição de quem o abriu — o mais grave — e não "anda" quando entra
 *   mais gente. Em grade, para milhares de pinos não custarem O(n²) a cada parada da câmera.
 * - Coordenada inválida não é desenhada.
 */
internal fun planPins(markers: List<ResolvedMarker>, zoom: Float, clustering: Boolean): List<PlannedPin> {
    val valid = markers.filter { it.item.position.isValidCoordinate() }
    if (!clustering) return valid.map { PlannedPin.Single(it) }
    val ordered = valid.sortedWith(compareByDescending<ResolvedMarker> { it.item.priority }.thenBy { it.item.id })
    val z = zoom.toDouble().coerceIn(0.0, 22.0)
    val half = CLUSTER_DISTANCE_POINTS / 2.0
    val cell = CLUSTER_DISTANCE_POINTS
    val points = ordered.map { mercatorPoint(it.item.position, z) }
    val grid = HashMap<Long, MutableList<Int>>()
    fun cellKey(cx: Long, cy: Long): Long = (cx shl 32) xor (cy and 0xffffffffL)
    points.forEachIndexed { i, (x, y) ->
        grid.getOrPut(cellKey(floor(x / cell).toLong(), floor(y / cell).toLong())) { mutableListOf() }.add(i)
    }
    val taken = BooleanArray(ordered.size)
    val out = ArrayList<PlannedPin>()
    for (i in ordered.indices) {
        if (taken[i]) continue
        taken[i] = true
        val (x, y) = points[i]
        val members = mutableListOf(i)
        val cx = floor(x / cell).toLong()
        val cy = floor(y / cell).toLong()
        for (dx in -1L..1L) for (dy in -1L..1L) {
            val bucket = grid[cellKey(cx + dx, cy + dy)] ?: continue
            for (j in bucket) {
                if (taken[j]) continue
                val (xj, yj) = points[j]
                if (kotlin.math.abs(xj - x) <= half && kotlin.math.abs(yj - y) <= half) {
                    taken[j] = true
                    members += j
                }
            }
        }
        if (members.size == 1) {
            out += PlannedPin.Single(ordered[i])
        } else {
            members.sort()
            val group = members.map { ordered[it] }
            out += PlannedPin.Group(GROUP_KEY_PREFIX + ordered[i].item.id, ordered[i].item.position, group)
        }
    }
    return out
}

/**
 * Câmera que enquadra [bounds] numa vista de [widthPoints] × [heightPoints] com [paddingPoints] de
 * folga, sem passar de [maxZoom] (a mesma regra do `fitTo` nas outras plataformas). Vista ainda sem
 * medida → centro do retângulo em [maxZoom].
 */
internal fun cameraToFit(
    bounds: LatLngBounds,
    widthPoints: Double,
    heightPoints: Double,
    paddingPoints: Double,
    maxZoom: Float,
): CameraPosition {
    val usableW = widthPoints - 2 * paddingPoints
    val usableH = heightPoints - 2 * paddingPoints
    if (usableW <= 1.0 || usableH <= 1.0) return CameraPosition(bounds.center, maxZoom)
    val (x1, y1) = mercatorPoint(bounds.southwest, 0.0)
    val (x2, y2) = mercatorPoint(bounds.northeast, 0.0)
    val w0 = kotlin.math.abs(x2 - x1)
    val h0 = kotlin.math.abs(y2 - y1)
    val zoomW = if (w0 > 0) ln(usableW / w0) / ln(2.0) else Double.POSITIVE_INFINITY
    val zoomH = if (h0 > 0) ln(usableH / h0) / ln(2.0) else Double.POSITIVE_INFINITY
    val zoom = min(min(zoomW, zoomH), maxZoom.toDouble())
    // Centro na projeção (não a média das latitudes): em Mercator o meio vertical não é a média.
    val cxw = (x1 + x2) / 2.0
    val cyw = (y1 + y2) / 2.0
    val lng = cxw / WORLD_TILE_POINTS * 360.0 - 180.0
    val n = PI - 2.0 * PI * cyw / WORLD_TILE_POINTS
    val lat = 180.0 / PI * kotlin.math.atan(0.5 * (kotlin.math.exp(n) - kotlin.math.exp(-n)))
    return CameraPosition(LatLng(lat, lng), max(0.0, zoom).toFloat())
}

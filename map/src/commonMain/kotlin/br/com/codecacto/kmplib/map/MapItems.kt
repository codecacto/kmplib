package br.com.codecacto.kmplib.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.pow

// ---------------------------------------------------------------------------------------------
// Modelo de dados do NativeMap (2.220.0 — GAP-ER-01). Tudo aqui é puro e testado; as duas
// plataformas só desenham o que estas funções decidem.
// ---------------------------------------------------------------------------------------------

/**
 * Aparência de um pino.
 *
 * @param color cor do pino — token do tema ou `StatusLevel.color`; nunca hex na tela.
 * @param glyph texto curto DENTRO do pino (número da parada, "!", inicial). Até
 *   [MAX_GLYPH_LENGTH] caracteres visíveis — o resto é cortado (ver [limitGlyph]).
 * @param glyphColor cor do texto; `null` = claro ou escuro, o de maior contraste sobre [color].
 */
@Immutable
data class MapMarkerStyle(
    val color: Color,
    val glyph: String? = null,
    val glyphColor: Color? = null,
) {
    companion object {
        /** Quantos caracteres visíveis cabem num pino (MapKit recomenda 1–3; o Android segue igual). */
        const val MAX_GLYPH_LENGTH: Int = 3
    }
}

/**
 * Um ponto do [NativeMap].
 *
 * @param id identificador estável (é o que volta no clique e o que o mapa usa para diferenciar
 *   pino novo de pino que só mudou).
 * @param title título do balão ao tocar (vazio = sem balão).
 * @param snippet segunda linha do balão.
 * @param style aparência; `null` = pino na cor primária do tema.
 * @param priority gravidade; MAIOR = mais grave. Decide a cor do GRUPO quando os pinos se juntam
 *   (o grupo mostra o pior caso de dentro dele) e quem fica por cima quando se sobrepõem.
 */
@Immutable
data class MapItem(
    val id: String,
    val position: LatLng,
    val title: String = "",
    val snippet: String? = null,
    val style: MapMarkerStyle? = null,
    val priority: Int = 0,
)

/**
 * Linha no mapa — o trajeto de uma rota com N paradas (tipicamente o
 * [br.com.codecacto.kmplib.map.route.RouteResult.polyline]).
 *
 * @param color `null` = cor primária do tema.
 */
@Immutable
data class MapPolyline(
    val id: String,
    val points: List<LatLng>,
    val color: Color? = null,
    val width: Dp = 5.dp,
)

/**
 * Retângulo geográfico (sudoeste → nordeste). Não trata o antimeridiano (±180°) — irrelevante para
 * os produtos da fábrica, que operam dentro de um país.
 */
@Immutable
data class LatLngBounds(
    val southwest: LatLng,
    val northeast: LatLng,
) {
    val center: LatLng
        get() = LatLng(
            (southwest.latitude + northeast.latitude) / 2.0,
            (southwest.longitude + northeast.longitude) / 2.0,
        )

    /** `true` quando o retângulo é um ponto só (todos os pontos no mesmo lugar). */
    val isSinglePoint: Boolean
        get() = southwest.latitude == northeast.latitude && southwest.longitude == northeast.longitude

    fun contains(point: LatLng): Boolean =
        point.latitude in southwest.latitude..northeast.latitude &&
            point.longitude in southwest.longitude..northeast.longitude

    companion object {
        /** Menor retângulo que contém [points]; `null` se vazio. Coordenadas inválidas são ignoradas. */
        fun of(points: Iterable<LatLng>): LatLngBounds? {
            var minLat = Double.POSITIVE_INFINITY
            var maxLat = Double.NEGATIVE_INFINITY
            var minLng = Double.POSITIVE_INFINITY
            var maxLng = Double.NEGATIVE_INFINITY
            var any = false
            for (p in points) {
                if (!p.isValidCoordinate()) continue
                any = true
                if (p.latitude < minLat) minLat = p.latitude
                if (p.latitude > maxLat) maxLat = p.latitude
                if (p.longitude < minLng) minLng = p.longitude
                if (p.longitude > maxLng) maxLng = p.longitude
            }
            return if (any) LatLngBounds(LatLng(minLat, minLng), LatLng(maxLat, maxLng)) else null
        }
    }
}

/** Latitude em [-90, 90], longitude em [-180, 180], sem NaN. (0,0) é válido — é o mar, mas existe. */
internal fun LatLng.isValidCoordinate(): Boolean =
    !latitude.isNaN() && !longitude.isNaN() && latitude in -90.0..90.0 && longitude in -180.0..180.0

/**
 * Corta [glyph] em até [max] caracteres **visíveis**, sem partir um par substituto (emoji). Vazio ou
 * só espaço vira `null` (pino sem texto).
 */
fun limitGlyph(glyph: String?, max: Int = MapMarkerStyle.MAX_GLYPH_LENGTH): String? {
    val trimmed = glyph?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    val out = StringBuilder()
    var count = 0
    var i = 0
    while (i < trimmed.length && count < max) {
        val c = trimmed[i]
        if (c.isHighSurrogate() && i + 1 < trimmed.length && trimmed[i + 1].isLowSurrogate()) {
            out.append(c).append(trimmed[i + 1])
            i += 2
        } else {
            out.append(c)
            i++
        }
        count++
    }
    return out.toString()
}

/**
 * O item que representa um GRUPO de pinos: o de maior [MapItem.priority] (empate = o primeiro).
 * É a cor dele que o grupo mostra — num semáforo, um vencido escondido entre dez em dia pinta o
 * grupo de vermelho, e é isso que faz alguém tocar no grupo.
 */
fun clusterRepresentative(members: Iterable<MapItem>): MapItem? {
    var best: MapItem? = null
    for (m in members) if (best == null || m.priority > best.priority) best = m
    return best
}

/** Texto do contador de um grupo: `"7"`, e `"99+"` acima de 99 (cabe no círculo). */
fun clusterCountLabel(count: Int): String = if (count > 99) "99+" else count.toString()

// --- Conversão zoom (Google/Web Mercator) ↔ extensão (MapKit, que não tem "zoom") --------------

/** Largura do mundo em pontos de tela no zoom 0 da projeção Web Mercator. */
internal const val WORLD_TILE_POINTS: Double = 256.0

/**
 * Quantos graus de LONGITUDE cabem numa tela de [widthPoints] no [zoom] dado. É a ponte para o
 * MapKit, que trabalha por extensão (`MKCoordinateSpan`) e não por nível de zoom.
 */
fun longitudeSpanForZoom(zoom: Float, widthPoints: Double): Double =
    360.0 * widthPoints / (WORLD_TILE_POINTS * 2.0.pow(zoom.toDouble()))

/** Inverso de [longitudeSpanForZoom]. Extensão ≤ 0 devolve o zoom máximo usual (21). */
fun zoomForLongitudeSpan(longitudeSpan: Double, widthPoints: Double): Float {
    if (longitudeSpan <= 0.0 || widthPoints <= 0.0) return 21f
    val z = ln(360.0 * widthPoints / (WORLD_TILE_POINTS * longitudeSpan)) / ln(2.0)
    return z.toFloat().coerceIn(0f, 21f)
}

/**
 * Extensão de LATITUDE equivalente a [longitudeSpan] em torno de [latitude] (Mercator: um grau de
 * longitude encolhe com o cosseno da latitude, o de latitude não).
 */
fun latitudeSpanFor(longitudeSpan: Double, latitude: Double): Double =
    (longitudeSpan * cos(latitude * PI / 180.0)).coerceIn(0.0, 180.0)

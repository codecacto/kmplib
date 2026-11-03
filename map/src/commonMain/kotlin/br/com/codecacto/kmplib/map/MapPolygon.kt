package br.com.codecacto.kmplib.map

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------------------------------------
// Polígono e tipo de mapa do NativeMap (2.342.0). Tudo aqui é puro e testado; as plataformas só
// desenham o que estas funções decidem (Android `Polygon` do maps-compose, iOS `GMSPolygon` pela
// ponte ou `MKPolygon` no fallback MapKit).
// ---------------------------------------------------------------------------------------------

/**
 * Tipo do mapa de fundo do [NativeMap] (2.342.0) — os tipos do Google Maps SDK.
 *
 * - [NORMAL]: o mapa de ruas de sempre.
 * - [SATELLITE]: só a imagem de satélite, sem rótulos (o "mapa da fazenda": talhões desenhados por
 *   cima com [MapPolygon]).
 * - [HYBRID]: satélite com ruas e rótulos por cima.
 * - [TERRAIN]: relevo. **No fallback MapKit do iOS vira [NORMAL]** — o MapKit não tem relevo
 *   (`MKMapType` só tem padrão/satélite/híbrido); com a ponte do Google, relevo de verdade.
 *
 * O satélite precisa de internet como qualquer ladrilho do mapa; o app que funciona sem rede
 * decide o que mostrar quando ela falta (a lib não troca o tipo sozinha).
 */
enum class MapType {
    NORMAL,
    SATELLITE,
    HYBRID,
    TERRAIN,
}

/**
 * O tipo que o fallback **MapKit** consegue mostrar: [MapType.TERRAIN] não existe lá e cai no
 * [MapType.NORMAL]; os demais passam iguais.
 */
internal fun MapType.supportedByMapKit(): MapType = if (this == MapType.TERRAIN) MapType.NORMAL else this

/**
 * Área fechada no mapa (2.342.0) — o talhão de uma fazenda, um lote, uma zona de entrega. Os
 * vértices são geográficos (acompanha o zoom); o último ponto liga no primeiro sozinho, não precisa
 * repetir.
 *
 * Mudar pontos/cores de um [id] redesenha só aquele polígono — é assim que o talhão muda de cor
 * quando a situação dele muda. Polígonos ficam **por baixo** de círculos, rotas e pinos; entre eles,
 * o que vem **depois** na lista fica por cima.
 *
 * @param points vértices do contorno externo, em ordem (horária ou anti-horária). Menos de 3 pontos
 *   válidos = não desenha.
 * @param fillColor preenchimento; `null` = cor primária do tema a [DEFAULT_POLYGON_FILL_ALPHA].
 *   Sobre satélite, prefira alfa entre 0,3 e 0,5 — a imagem continua visível por baixo.
 * @param strokeColor contorno; `null` = cor primária do tema.
 * @param strokeWidth espessura do contorno (`0.dp` = sem contorno).
 * @param holes buracos dentro da área (sede dentro do talhão, uma lagoa) — cada um com ≥ 3 pontos
 *   válidos; buraco inválido é ignorado, o polígono continua.
 */
@Immutable
data class MapPolygon(
    val id: String,
    val points: List<LatLng>,
    val fillColor: Color? = null,
    val strokeColor: Color? = null,
    val strokeWidth: Dp = 2.dp,
    val holes: List<List<LatLng>> = emptyList(),
)

/** Opacidade do preenchimento default de um [MapPolygon] sem [MapPolygon.fillColor]. */
const val DEFAULT_POLYGON_FILL_ALPHA: Float = 0.3f

/** Polígono com as cores resolvidas (tema aplicado) e os anéis validados. */
@Immutable
internal data class ResolvedPolygon(
    val id: String,
    val points: List<LatLng>,
    val holes: List<List<LatLng>>,
    val fillColor: Color,
    val strokeColor: Color,
    val strokeWidthDp: Float,
)

/** Mínimo de vértices de uma área. */
private const val MIN_POLYGON_POINTS = 3

/**
 * Anel válido para desenhar: só as coordenadas válidas, sem o ponto final repetido (o SDK fecha
 * sozinho), e com ≥ 3 vértices. `null` = não desenha.
 */
internal fun polygonRing(points: List<LatLng>): List<LatLng>? {
    var valid = points.filter { it.isValidCoordinate() }
    if (valid.size > 1 && valid.first() == valid.last()) valid = valid.dropLast(1)
    return if (valid.size >= MIN_POLYGON_POINTS) valid else null
}

/** Aparência final de um polígono — pura e testada. `null` = contorno inválido (não desenha). */
internal fun resolvePolygon(polygon: MapPolygon, defaultColor: Color): ResolvedPolygon? {
    val outer = polygonRing(polygon.points) ?: return null
    return ResolvedPolygon(
        id = polygon.id,
        points = outer,
        holes = polygon.holes.mapNotNull { polygonRing(it) },
        fillColor = polygon.fillColor ?: defaultColor.copy(alpha = DEFAULT_POLYGON_FILL_ALPHA),
        strokeColor = polygon.strokeColor ?: defaultColor.copy(alpha = 1f),
        strokeWidthDp = polygon.strokeWidth.value.coerceAtLeast(0f),
    )
}

/** Pontos que entram no enquadramento inicial: o contorno externo (os buracos estão dentro dele). */
fun MapPolygon.fitPoints(): List<LatLng> = polygonRing(points).orEmpty()

/**
 * `true` se [point] cai dentro do anel (regra par-ímpar, em graus — exato o bastante para áreas do
 * tamanho de um município, o caso da fábrica). Ponto exatamente na borda pode cair de qualquer lado.
 */
internal fun ringContains(ring: List<LatLng>, point: LatLng): Boolean {
    var inside = false
    var j = ring.size - 1
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[j]
        if ((a.latitude > point.latitude) != (b.latitude > point.latitude)) {
            val crossLng = (b.longitude - a.longitude) * (point.latitude - a.latitude) / (b.latitude - a.latitude) + a.longitude
            if (point.longitude < crossLng) inside = !inside
        }
        j = i
    }
    return inside
}

/** Dentro do contorno e fora de todos os buracos. */
internal fun ResolvedPolygon.contains(point: LatLng): Boolean =
    ringContains(points, point) && holes.none { ringContains(it, point) }

/**
 * O polígono tocado em [point]: o de CIMA (o último da lista) que o contém, ou `null`. É a regra do
 * toque no fallback MapKit, que não tem clique em overlay — no Google (Android e ponte iOS) quem
 * decide é o SDK, com a mesma ordem.
 */
internal fun topPolygonAt(polygons: List<ResolvedPolygon>, point: LatLng): ResolvedPolygon? =
    polygons.lastOrNull { it.contains(point) }

/**
 * `zIndex` inteiro do polígono na posição [index] de [count] no Google Maps: todos abaixo do
 * círculo (−1), da rota (0) e dos pinos, e o que vem depois na lista por cima — `-count-1 … -2`.
 */
internal fun polygonZIndex(index: Int, count: Int): Int = index - count - 1

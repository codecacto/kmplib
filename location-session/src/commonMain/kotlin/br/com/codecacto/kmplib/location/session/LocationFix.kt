package br.com.codecacto.kmplib.location.session

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Uma leitura de localização da sessão (2.308.0).
 *
 * @property accuracy raio de incerteza horizontal, em **metros** (68% de confiança — o mesmo número
 *   que o Android e o iOS reportam). Quanto MENOR, melhor.
 * @property timestampMillis instante da leitura (epoch, ms) — o do sensor, não o da entrega.
 * @property isMock leitura vinda de localização simulada (app de GPS falso no Android —
 *   `Location.isMock`/`isFromMockProvider`; simulação por software no iOS —
 *   `CLLocationSourceInformation.isSimulatedBySoftware`). Quem decide o que fazer é o servidor: a lib
 *   só informa, não descarta.
 * @property speed velocidade em m/s, ou `null` quando a plataforma não a mediu.
 *
 * ⚠️ `toString` não traz as coordenadas: localização de pessoa é dado pessoal e não vai para log.
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestampMillis: Long,
    val isMock: Boolean,
    val speed: Float? = null,
) {
    /** Distância em metros até ([latitude], [longitude]) — haversine, raio médio da Terra. */
    fun distanceTo(latitude: Double, longitude: Double): Double =
        distanceMeters(this.latitude, this.longitude, latitude, longitude)

    override fun toString(): String =
        "LocationFix(accuracy=$accuracy, timestampMillis=$timestampMillis, isMock=$isMock, hasSpeed=${speed != null})"
}

private const val EARTH_RADIUS_METERS = 6_371_008.8

/**
 * Distância em metros entre dois pontos (haversine sobre o raio médio da Terra, IUGG). Erro abaixo de
 * 0,5% — irrelevante na escala de um ponto de ronda (dezenas a centenas de metros).
 */
fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val p1 = lat1.toRadians()
    val p2 = lat2.toRadians()
    val dp = (lat2 - lat1).toRadians()
    val dl = (lon2 - lon1).toRadians()
    val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
    return 2 * EARTH_RADIUS_METERS * asin(sqrt(a.coerceIn(0.0, 1.0)))
}

private fun Double.toRadians(): Double = this * kotlin.math.PI / 180.0

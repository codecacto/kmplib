package br.com.codecacto.kmplib.location

import androidx.compose.runtime.Immutable

/**
 * Coordenada geográfica (latitude/longitude).
 *
 * Multiplataforma — convertida para o tipo nativo de cada SDK no `actual`
 * (Google Maps `LatLng` no Android, `CLLocationCoordinate2D` no iOS).
 *
 * Mora em `location`, e não em `map`, porque coordenada é **dado**, não desenho: quem obtém a
 * posição do aparelho (`LocationProvider`) precisa dela sem arrastar o mapa junto. Enquanto o tipo
 * morava em `map`, `location` importava `map` e `map` importava `location` — o ciclo que impedia os
 * dois de virarem módulos separados. `br.com.codecacto.kmplib.map.LatLng` segue funcionando como
 * alias.
 */
@Immutable
data class LatLng(
    val latitude: Double,
    val longitude: Double
)

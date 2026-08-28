package br.com.codecacto.kmplib.location

/**
 * Provedor de localização GPS multiplataforma.
 *
 * Obtém um *fix* único (não streaming) da posição atual do dispositivo, ideal
 * para preencher coordenadas em um cadastro (ex.: botão "Usar GPS atual").
 *
 * - **Android:** Fused Location Provider (`play-services-location`).
 * - **iOS:** `CLLocationManager`.
 *
 * Reusa [LatLng] do módulo `map` para coerência de tipos entre mapa e GPS.
 */
interface LocationProvider {

    /**
     * Retorna a posição atual, ou `null` se a permissão for negada, o GPS
     * estiver indisponível ou ocorrer timeout (10s).
     *
     * Solicita a permissão de localização se ainda não concedida.
     */
    suspend fun getCurrentLocation(): LatLng?

    /** `true` se a permissão de localização (fine/when-in-use) já foi concedida. */
    suspend fun hasLocationPermission(): Boolean
}

/**
 * Cria a implementação de [LocationProvider] da plataforma corrente.
 *
 * No Android, depende do contexto/activity registrados em
 * `KmpLib.init(context)` / `KmpLib.setActivity(activity)`.
 */
expect fun createLocationProvider(): LocationProvider

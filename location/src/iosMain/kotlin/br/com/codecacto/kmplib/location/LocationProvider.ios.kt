package br.com.codecacto.kmplib.location

import br.com.codecacto.kmplib.core.util.AppLogger

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.darwin.NSObject

private const val TAG = "LocationProvider"
private const val LOCATION_TIMEOUT_MS = 10_000L

/**
 * Implementação iOS do [LocationProvider] via `CLLocationManager`.
 *
 * Requer `NSLocationWhenInUseUsageDescription` no Info.plist do app.
 *
 * Obtém um fix único: aciona `requestLocation()` e resolve no callback do
 * delegate. Solicita autorização "when in use" se ainda não concedida.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosLocationProvider : LocationProvider {

    /**
     * O manager e o delegate da consulta em curso, presos a referências **fortes**.
     *
     * ⚠️ **Não devolva isto a variáveis locais.** `CLLocationManager.delegate` é **weak**, e as duas
     * locais de [getCurrentLocation] não sobrevivem ao `deferred.await()`: a máquina de estados da
     * corrotina só preserva o que é usado **depois** da suspensão, e nada aqui é. O ARC libera os
     * dois enquanto se espera, o `requestLocation()` fica sem quem responda, e a função devolve
     * `null` no fim do timeout de 10 s — "não consegui achar sua localização", sem erro nenhum no
     * caminho. É o mesmo defeito de delegate frouxo já corrigido no `VideoPicker`/`ImagePicker`.
     */
    private var managerEmUso: CLLocationManager? = null
    private var delegateEmUso: NSObject? = null

    override suspend fun hasLocationPermission(): Boolean {
        val status = CLLocationManager.authorizationStatus()
        return status.isAuthorized()
    }

    override suspend fun getCurrentLocation(): LatLng? {
        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            val deferred = CompletableDeferred<LatLng?>()
            val manager = CLLocationManager()

            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManager(
                    manager: CLLocationManager,
                    didUpdateLocations: List<*>
                ) {
                    val location = didUpdateLocations.lastOrNull() as? CLLocation
                    if (location != null) {
                        val coord = location.coordinate.useContents {
                            LatLng(latitude = latitude, longitude = longitude)
                        }
                        if (!deferred.isCompleted) deferred.complete(coord)
                    } else {
                        if (!deferred.isCompleted) deferred.complete(null)
                    }
                }

                override fun locationManager(
                    manager: CLLocationManager,
                    didFailWithError: platform.Foundation.NSError
                ) {
                    AppLogger.e(TAG, "Erro CLLocationManager: ${didFailWithError.localizedDescription}")
                    if (!deferred.isCompleted) deferred.complete(null)
                }

                override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                    val status = manager.authorizationStatus
                    if (status.isAuthorized()) {
                        manager.requestLocation()
                    } else if (status != kCLAuthorizationStatusNotDetermined) {
                        if (!deferred.isCompleted) deferred.complete(null)
                    }
                }
            }

            managerEmUso = manager
            delegateEmUso = delegate
            manager.delegate = delegate

            try {
                if (CLLocationManager.authorizationStatus().isAuthorized()) {
                    manager.requestLocation()
                } else {
                    manager.requestWhenInUseAuthorization()
                }

                deferred.await()
            } finally {
                // Roda também quando o `withTimeoutOrNull` cancela: sem isto, o manager da consulta
                // que expirou continuaria vivo até a próxima.
                manager.delegate = null
                managerEmUso = null
                delegateEmUso = null
            }
        }
    }
}

private fun CLAuthorizationStatus.isAuthorized(): Boolean =
    this == kCLAuthorizationStatusAuthorizedWhenInUse ||
        this == kCLAuthorizationStatusAuthorizedAlways

actual fun createLocationProvider(): LocationProvider = IosLocationProvider()

/**
 * No iOS o provider não precisa de contexto Compose; este helper existe por
 * paridade de API com o Android.
 */
@androidx.compose.runtime.Composable
fun rememberLocationProvider(): LocationProvider =
    androidx.compose.runtime.remember { IosLocationProvider() }

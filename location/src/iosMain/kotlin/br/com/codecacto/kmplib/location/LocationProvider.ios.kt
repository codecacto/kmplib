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
 * O manager e o delegate de **uma** consulta, presos juntos.
 *
 * ⚠️ `CLLocationManager.delegate` é **weak**: quem tem de segurar o delegate é o código que o
 * criou. Guardar as duas coisas aqui, e referenciar **este objeto depois da suspensão** (no
 * `finally` de [IosLocationProvider.getCurrentLocation]), é o que faz a máquina de estados da
 * corrotina preservá-las durante o `await` — variáveis locais usadas só antes da suspensão são
 * descartadas, o ARC libera o delegate, o `requestLocation()` fica sem quem responda e a função
 * devolve `null` no fim do timeout de 10 s ("não consegui achar sua localização", sem erro nenhum
 * no caminho).
 *
 * ### Por que um objeto por consulta, e não um campo do provider (2.199.0)
 * Até a 2.198.0 as referências eram **dois campos** do provider, limpos no `finally`. Com duas
 * chamadas ao mesmo tempo — o que nada impede, e a lib não pode presumir —, a primeira a terminar
 * anulava as referências **da outra**: o delegate da consulta ainda em curso ficava sem dono, e
 * voltava exatamente o defeito que a 2.198.0 corrigiu. Amarrando o par ao **quadro da corrotina**
 * que o usa, cada consulta tem o seu, sem contador de referências e sem estado compartilhado (e,
 * portanto, sem disputa entre threads).
 */
@OptIn(ExperimentalForeignApi::class)
private class ConsultaDeLocalizacao(
    val manager: CLLocationManager,
    /** Não é lido: existe para manter o delegate vivo enquanto a consulta durar. */
    val delegate: NSObject,
)

/**
 * Implementação iOS do [LocationProvider] via `CLLocationManager`.
 *
 * Requer `NSLocationWhenInUseUsageDescription` no Info.plist do app.
 *
 * Obtém um fix único: aciona `requestLocation()` e resolve no callback do
 * delegate. Solicita autorização "when in use" se ainda não concedida.
 *
 * **Chamadas simultâneas são independentes** — ver [ConsultaDeLocalizacao].
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosLocationProvider : LocationProvider {

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

            // A referência forte desta consulta. É usada no `finally`, DEPOIS da suspensão — é
            // isso, e só isso, que a mantém viva durante o `await`. Ver [ConsultaDeLocalizacao].
            val consulta = ConsultaDeLocalizacao(manager, delegate)
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
                // que expirou continuaria vivo até a próxima. Solta só o DESTA consulta — nunca o
                // de outra que ainda esteja esperando.
                consulta.manager.delegate = null
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

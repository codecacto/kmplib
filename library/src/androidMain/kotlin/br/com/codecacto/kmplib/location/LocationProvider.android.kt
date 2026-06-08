package br.com.codecacto.kmplib.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import br.com.codecacto.kmplib.core.util.AppLogger
import br.com.codecacto.kmplib.map.LatLng
import br.com.codecacto.kmplib.platform.UrlLauncherHolder
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "LocationProvider"
private const val LOCATION_TIMEOUT_MS = 10_000L

/**
 * Solicitante de permissão de localização. Implementado por um launcher
 * Compose (ver [rememberLocationProvider]); quando ausente, a permissão não
 * pode ser solicitada e [LocationProvider.getCurrentLocation] retorna `null`
 * se ela ainda não tiver sido concedida.
 */
internal fun interface PermissionRequester {
    /** Solicita a permissão e retorna `true` se concedida. */
    suspend fun request(): Boolean
}

internal class AndroidLocationProvider(
    private val context: Context,
    private val permissionRequester: PermissionRequester? = null
) : LocationProvider {

    private fun hasPermissionSync(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    override suspend fun hasLocationPermission(): Boolean = hasPermissionSync()

    @Suppress("MissingPermission")
    override suspend fun getCurrentLocation(): LatLng? {
        val granted = hasPermissionSync() || (permissionRequester?.request() == true)
        if (!granted) {
            AppLogger.w(TAG, "Permissão de localização negada")
            return null
        }

        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    val client = LocationServices.getFusedLocationProviderClient(context)
                    val request = CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .build()

                    client.getCurrentLocation(request, null)
                        .addOnSuccessListener { location ->
                            if (location != null) {
                                cont.resume(LatLng(location.latitude, location.longitude))
                            } else {
                                AppLogger.w(TAG, "Fix de localização nulo")
                                cont.resume(null)
                            }
                        }
                        .addOnFailureListener { e ->
                            AppLogger.e(TAG, "Falha ao obter localização", e)
                            cont.resume(null)
                        }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Erro inesperado ao obter localização", e)
                    cont.resume(null)
                }
            }
        }
    }
}

actual fun createLocationProvider(): LocationProvider {
    val context = UrlLauncherHolder.getContext()
        ?: throw IllegalStateException(
            "KmpLib não inicializada. Chame KmpLib.init(context) no Application.onCreate() " +
                "antes de criar o LocationProvider."
        )
    return AndroidLocationProvider(context.applicationContext)
}

/**
 * Helper Composable que cria um [LocationProvider] já equipado com um launcher
 * de permissão de runtime — assim o `getCurrentLocation()` consegue **solicitar**
 * `ACCESS_FINE_LOCATION` ao usuário caso ainda não esteja concedida.
 *
 * Prefira este helper na UI ao invés de [createLocationProvider] direto quando
 * precisar disparar o pedido de permissão.
 */
@Composable
fun rememberLocationProvider(): LocationProvider {
    val context = androidx.compose.ui.platform.LocalContext.current
    val pending = remember { mutableListOf<CompletableDeferred<Boolean>>() }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val snapshot = pending.toList()
        pending.clear()
        snapshot.forEach { it.complete(granted) }
    }

    return remember(context) {
        val requester = PermissionRequester {
            val deferred = CompletableDeferred<Boolean>()
            pending.add(deferred)
            launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            deferred.await()
        }
        AndroidLocationProvider(context.applicationContext, requester)
    }
}

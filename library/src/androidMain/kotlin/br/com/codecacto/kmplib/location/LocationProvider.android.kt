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

import br.com.codecacto.kmplib.core.context.AndroidAppContext
import br.com.codecacto.kmplib.platform.permission.AppPermission
import br.com.codecacto.kmplib.platform.permission.PermissionStatus
import br.com.codecacto.kmplib.platform.permission.createPermissionManager
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.firstOrNull
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

    /**
     * **COARSE **ou** FINE serve.**
     *
     * Só a FINE era conferida aqui, e isso transformava o filtro "perto de mim" num controle mudo
     * em todo app que declara apenas `ACCESS_COARSE_LOCATION` (o que a fábrica recomenda para
     * ordenar por distância): a permissão concedida pelo usuário nunca casava com a conferida pela
     * lib, `getCurrentLocation()` devolvia `null`, e a tela dizia "não foi possível obter sua
     * localização" logo depois de o próprio usuário ter permitido.
     */
    private fun hasPermissionSync(): Boolean = PERMISSOES_ACEITAS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun hasLocationPermission(): Boolean = hasPermissionSync()

    @Suppress("MissingPermission")
    override suspend fun getCurrentLocation(): LatLng? {
        val granted = hasPermissionSync() || solicitarPermissao()
        if (!granted) {
            AppLogger.w(TAG, "Permissão de localização negada")
            return null
        }

        return withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    val client = LocationServices.getFusedLocationProviderClient(context)
                    // BALANCED, e não HIGH_ACCURACY: o app pede `ACCESS_COARSE_LOCATION`, e com
                    // ela o Fused já não entrega precisão de GPS — pedir alta precisão só gasta
                    // bateria e alonga o fix para devolver a mesma aproximação de quarteirão.
                    val request = CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
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

    /**
     * Pede a permissão — pelo [permissionRequester] quando a UI forneceu um, senão pelo
     * `PermissionManager` da lib.
     *
     * O caminho pelo `PermissionManager` existe porque [createLocationProvider] (o que o Koin
     * resolve) não tem launcher de Compose: sem ele, um provider injetado **nunca** conseguia abrir
     * o diálogo — a permissão precisava já estar concedida por outra tela, e o filtro simplesmente
     * falhava calado na primeira vez em que alguém o tocava.
     */
    private suspend fun solicitarPermissao(): Boolean {
        permissionRequester?.let { return it.request() }
        val status = createPermissionManager()
            .requestPermission(AppPermission.LOCATION)
            .firstOrNull()
        return status == PermissionStatus.GRANTED
    }

    private companion object {
        /** Qualquer uma das duas basta — ver [hasPermissionSync]. */
        val PERMISSOES_ACEITAS = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }
}

actual fun createLocationProvider(): LocationProvider {
    val context = AndroidAppContext.get()
        ?: throw IllegalStateException(
            "KmpLib não inicializada. Chame KmpLib.init(context) no Application.onCreate() " +
                "antes de criar o LocationProvider."
        )
    return AndroidLocationProvider(context.applicationContext)
}

/**
 * Helper Composable que cria um [LocationProvider] já equipado com um launcher
 * de permissão de runtime — assim o `getCurrentLocation()` consegue **solicitar**
 * `ACCESS_COARSE_LOCATION` ao usuário caso ainda não esteja concedida.
 *
 * Desde a 2.135.0 o [createLocationProvider] também sabe pedir (via `PermissionManager`), então
 * este helper deixou de ser obrigatório — ele continua sendo o caminho mais direto quando a tela
 * já é Compose e não há Koin no meio.
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
            launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
            deferred.await()
        }
        AndroidLocationProvider(context.applicationContext, requester)
    }
}

package br.com.codecacto.kmplib.health.heartrate

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import br.com.codecacto.kmplib.health.HealthActivityHolder
import br.com.codecacto.kmplib.health.declaredInManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

private const val TAG = "kmplib-health"

/**
 * As permissões de runtime que o Bluetooth LE exige NESTA versão do Android (guia oficial "Bluetooth
 * permissions"):
 * - Android 12+ (API 31): `BLUETOOTH_SCAN` (declarada com `neverForLocation` no manifesto da lib) e
 *   `BLUETOOTH_CONNECT`, do grupo "Dispositivos por perto";
 * - Android 11 e anteriores: `ACCESS_FINE_LOCATION` — o scan BLE só devolve resultado com ela. O APP a
 *   declara (a lib não, ver o manifesto); sem a declaração o sistema nega sem diálogo.
 */
internal fun bluetoothRuntimePermissions(): List<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

/**
 * Pedido e leitura da permissão de Bluetooth pela via oficial do AndroidX: `ActivityResultRegistry` +
 * `RequestMultiplePermissions` (a mesma do `PermissionManager` do `kmplib-platform`; o
 * `onRequestPermissionsResult` está depreciado). A Activity é a de `HealthActivityHolder.setActivity`.
 */
internal class AndroidBluetoothPermission(private val context: Context) {

    private val permissions: List<String> get() = bluetoothRuntimePermissions()

    private fun allGranted(): Boolean =
        permissions.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    private fun canExplain(activity: ComponentActivity): Boolean =
        permissions
            .filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            .any { activity.shouldShowRequestPermissionRationale(it) }

    suspend fun status(): BluetoothPermissionStatus = withContext(Dispatchers.Main) {
        bluetoothPermissionStatusOf(
            allGranted = allGranted(),
            askedBefore = BluetoothPermissionMemory.askedBefore(context),
            canExplain = HealthActivityHolder.getActivity()?.let(::canExplain),
        )
    }

    suspend fun request(): BluetoothPermissionStatus = withContext(Dispatchers.Main) {
        val current = status()
        if (current == BluetoothPermissionStatus.GRANTED || current == BluetoothPermissionStatus.PERMANENTLY_DENIED) {
            return@withContext current
        }
        // Sem a declaração no manifesto o sistema nega na hora, sem diálogo — e a negação pareceria
        // "definitiva", mandando a pessoa a um Ajuste onde não há nada para ligar. É erro do app: avisa
        // no logcat (só o nome da permissão) e não marca como pedida.
        val undeclared = permissions.filterNot { declaredInManifest(context, it) }
        if (undeclared.isNotEmpty()) {
            Log.w(TAG, "Permissão não declarada no AndroidManifest do app: ${undeclared.joinToString()}")
            return@withContext current
        }
        val activity = HealthActivityHolder.getActivity() ?: return@withContext current

        // Marca ANTES do diálogo: se o sistema matar o processo com o diálogo na tela, na volta a
        // permissão continua "já pedida".
        BluetoothPermissionMemory.markAsked(context)
        val result = suspendCancellableCoroutine<Map<String, Boolean>> { continuation ->
            val key = "kmplib_health_bluetooth_${System.nanoTime()}"
            lateinit var launcher: ActivityResultLauncher<Array<String>>
            launcher = activity.activityResultRegistry.register(
                key,
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { granted ->
                launcher.unregister()
                if (continuation.isActive) continuation.resume(granted)
            }
            continuation.invokeOnCancellation { launcher.unregister() }
            launcher.launch(permissions.toTypedArray())
        }
        bluetoothPermissionRequestResult(
            allGranted = permissions.all { result[it] == true || context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED },
            canExplain = canExplain(activity),
        )
    }
}

/**
 * "A lib já pediu a permissão de Bluetooth neste aparelho" — o bit que o Android não expõe
 * (`shouldShowRequestPermissionRationale` é `false` antes do 1º pedido E depois da negação definitiva).
 * Arquivo próprio da lib, separado das preferências do app: é estado de plataforma, não preferência.
 */
private object BluetoothPermissionMemory {
    private const val FILE = "kmplib_health_permission_memory"
    private const val KEY = "bluetooth_asked"

    fun askedBefore(context: Context): Boolean = prefs(context).getBoolean(KEY, false)

    fun markAsked(context: Context) {
        prefs(context).edit().putBoolean(KEY, true).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}

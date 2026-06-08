package br.com.codecacto.kmplib.platform.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

/**
 * Holder da Activity (host de permissões) para o Android.
 *
 * Configuração no app consumidor:
 * ```kotlin
 * class MainActivity : FragmentActivity() {
 *     override fun onResume() {
 *         super.onResume()
 *         PermissionHostHolder.setActivity(this)
 *     }
 *     override fun onPause() { PermissionHostHolder.clearActivity(); super.onPause() }
 *
 *     override fun onRequestPermissionsResult(
 *         requestCode: Int, permissions: Array<out String>, grantResults: IntArray
 *     ) {
 *         super.onRequestPermissionsResult(requestCode, permissions, grantResults)
 *         PermissionHostHolder.handlePermissionResult(requestCode, permissions, grantResults)
 *     }
 * }
 * ```
 */
object PermissionHostHolder {
    private var activityRef: WeakReference<FragmentActivity>? = null
    private val requestCode = AtomicInteger(7100)
    private val pending = mutableMapOf<Int, (IntArray, Array<out String>) -> Unit>()

    fun setActivity(activity: FragmentActivity) {
        activityRef = WeakReference(activity)
    }

    fun clearActivity() {
        activityRef = null
    }

    internal fun getActivity(): FragmentActivity? = activityRef?.get()

    internal fun nextRequestCode(): Int = requestCode.incrementAndGet()

    internal fun registerCallback(code: Int, callback: (IntArray, Array<out String>) -> Unit) {
        pending[code] = callback
    }

    /**
     * Repasse obrigatório de `Activity.onRequestPermissionsResult`.
     */
    fun handlePermissionResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        pending.remove(requestCode)?.invoke(grantResults, permissions)
    }
}

/**
 * Implementação Android do [PermissionManager].
 */
class AndroidPermissionManager : PermissionManager {

    companion object {
        private const val TAG = "PermissionManager"
    }

    private val context: Context?
        get() = PermissionHostHolder.getActivity()?.applicationContext

    /**
     * Permissões Android correspondentes. Vazio = sem manifesto requerido (ex.: a partir de
     * versões em que a permissão não existe). `null` retornado quando não aplicável.
     */
    private fun manifestPermission(permission: AppPermission): String? = when (permission) {
        AppPermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO
        AppPermission.PHONE_STATE -> Manifest.permission.READ_PHONE_STATE
        AppPermission.CALL_LOG -> Manifest.permission.READ_CALL_LOG
        AppPermission.CAMERA -> Manifest.permission.CAMERA
        AppPermission.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS
            else null // < API 33: não há permissão de runtime
    }

    override fun checkPermission(permission: AppPermission): PermissionStatus {
        val ctx = context ?: return PermissionStatus.NOT_REQUESTED
        val manifest = manifestPermission(permission)
            ?: return PermissionStatus.GRANTED // sem permissão de runtime nesta versão

        return if (ContextCompat.checkSelfPermission(ctx, manifest) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            PermissionStatus.GRANTED
        } else {
            PermissionStatus.DENIED
        }
    }

    override fun requestPermission(permission: AppPermission): Flow<PermissionStatus> {
        // Já concedida (ou sem permissão de runtime): resolve imediatamente.
        val current = checkPermission(permission)
        if (current == PermissionStatus.GRANTED) {
            return flowOf(PermissionStatus.GRANTED)
        }

        val activity = PermissionHostHolder.getActivity()
        val manifest = manifestPermission(permission)
        if (activity == null || manifest == null) {
            AppLogger.w(TAG, "Activity ausente ou permissão sem runtime: $permission")
            return flowOf(current)
        }

        return callbackFlow {
            val code = PermissionHostHolder.nextRequestCode()
            PermissionHostHolder.registerCallback(code) { grantResults, _ ->
                val granted = grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
                val status = when {
                    granted -> PermissionStatus.GRANTED
                    // Após negar, se não deve mais mostrar rationale => negação permanente.
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, manifest) ->
                        PermissionStatus.PERMANENTLY_DENIED
                    else -> PermissionStatus.DENIED
                }
                trySend(status)
                close()
            }
            ActivityCompat.requestPermissions(activity, arrayOf(manifest), code)
            awaitClose { }
        }
    }
}

actual fun createPermissionManager(): PermissionManager = AndroidPermissionManager()

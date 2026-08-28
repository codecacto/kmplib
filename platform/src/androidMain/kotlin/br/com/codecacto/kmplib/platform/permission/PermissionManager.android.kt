package br.com.codecacto.kmplib.platform.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
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
 * **Desde 2.154.0 o app não configura nada aqui.** `KmpLib.setActivity(this)`/`clearActivity()` — que
 * a `casca-mobile` já chama no `onResume`/`onPause` — registram este holder junto com os outros
 * quatro. Até a 2.153.0 ele ficava de fora, e a consequência era muda: `requestPermission` não abria
 * diálogo nenhum, só registrava um aviso e devolvia o status que já tinha. O botão "Permitir"
 * existia, era tocável, e não acontecia nada — com build verde.
 *
 * **O `onRequestPermissionsResult` também deixou de ser necessário**: o pedido agora passa pelo
 * `ActivityResultRegistry` (a API recomendada pelo AndroidX; `onRequestPermissionsResult` está
 * depreciado na `Activity`). Apps que já sobrescrevem o método e chamam
 * [handlePermissionResult] continuam compilando e funcionando — a chamada vira inofensiva.
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
     * Repasse de `Activity.onRequestPermissionsResult`.
     *
     * **Não é mais necessário** (ver o KDoc do holder): desde 2.154.0 o resultado chega pelo
     * `ActivityResultRegistry`. Mantido porque **9 apps do portfólio** o chamam num override — para
     * eles, esta função não encontra nada pendente e não faz nada, que é o comportamento correto.
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
        // COARSE, e não FINE: quem ordena por distância não precisa da precisa, e o Android mostra
        // ao usuário qual das duas foi pedida. Ver o KDoc de `AppPermission.LOCATION`.
        AppPermission.LOCATION -> Manifest.permission.ACCESS_COARSE_LOCATION
        AppPermission.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                Manifest.permission.POST_NOTIFICATIONS
            else null // < API 33: não há permissão de runtime
    }

    /**
     * **O Android não sabe dizer "nunca pedida" — e essa é a diferença que decide uma tela.**
     *
     * `checkSelfPermission` só responde concedida ou não; `shouldShowRequestPermissionRationale` é
     * `false` nos DOIS extremos (antes do primeiro pedido e depois da negação definitiva). Sem
     * distinguir os dois, o app cai num de dois defeitos: ou nunca mostra a tela de contexto que as
     * lojas exigem antes do diálogo, ou oferece para sempre um botão "Permitir" que não abre nada.
     *
     * Por isso a lib **lembra** que já pediu, num arquivo próprio de preferências. É a mesma solução
     * que os apps vinham escrevendo à mão — e que, no Android, não tem alternativa: o sistema não
     * expõe esse bit. No iOS não é preciso: `AVAudioSession` já devolve `Undetermined`.
     */
    override fun checkPermission(permission: AppPermission): PermissionStatus {
        val ctx = context ?: return PermissionStatus.NOT_REQUESTED
        val manifest = manifestPermission(permission)
            ?: return PermissionStatus.GRANTED // sem permissão de runtime nesta versão

        if (ContextCompat.checkSelfPermission(ctx, manifest) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            return PermissionStatus.GRANTED
        }

        if (!PermissionMemory.jaPediu(ctx, permission)) return PermissionStatus.NOT_REQUESTED

        val activity = PermissionHostHolder.getActivity()
        val definitiva = activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, manifest)
        return if (definitiva) PermissionStatus.PERMANENTLY_DENIED else PermissionStatus.DENIED
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

        // A marca é gravada ANTES de abrir o diálogo: se o processo morrer com ele na tela (o
        // sistema pode matar o app enquanto o diálogo é do sistema), na volta a permissão continua
        // "já pedida" — e o app mostra o estado de negada, não a tela de contexto de novo.
        activity.applicationContext?.let { PermissionMemory.marcarPedida(it, permission) }

        return callbackFlow {
            // `ActivityResultRegistry` é a via recomendada pelo AndroidX — `requestPermissions` +
            // `onRequestPermissionsResult` está depreciado na `Activity`, e exigia que **cada app**
            // sobrescrevesse o método para repassar o resultado. Era metade do furo desta API: quem
            // não sabia do repasse via o diálogo abrir e a resposta nunca chegar.
            val chave = "kmplib_permission_${'$'}{permission.name}_${'$'}{PermissionHostHolder.nextRequestCode()}"
            val launcher = activity.activityResultRegistry.register(
                chave,
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                val status = when {
                    granted -> PermissionStatus.GRANTED
                    // Negou e o sistema não deixa mais explicar => negação definitiva ("não
                    // perguntar de novo", ou a segunda negação no Android 11+).
                    !ActivityCompat.shouldShowRequestPermissionRationale(activity, manifest) ->
                        PermissionStatus.PERMANENTLY_DENIED
                    else -> PermissionStatus.DENIED
                }
                trySend(status)
                close()
            }
            launcher.launch(manifest)
            // `register` sem `LifecycleOwner` não se desfaz sozinho: sem isto, cada pedido deixaria
            // um registro vivo no registry pelo resto da vida da Activity.
            awaitClose { launcher.unregister() }
        }
    }
}

/**
 * Memória de "esta permissão já foi pedida ao menos uma vez", por aplicação.
 *
 * Arquivo próprio da lib, separado do `AppPreferences` do app: é estado de plataforma, não
 * preferência de usuário — não deve aparecer num backup restaurado noutro aparelho, onde a
 * permissão volta a nunca ter sido pedida.
 */
private object PermissionMemory {
    private const val ARQUIVO = "kmplib_permission_memory"

    fun jaPediu(context: Context, permission: AppPermission): Boolean =
        prefs(context).getBoolean(permission.name, false)

    fun marcarPedida(context: Context, permission: AppPermission) {
        prefs(context).edit().putBoolean(permission.name, true).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(ARQUIVO, Context.MODE_PRIVATE)
}

actual fun createPermissionManager(): PermissionManager = AndroidPermissionManager()

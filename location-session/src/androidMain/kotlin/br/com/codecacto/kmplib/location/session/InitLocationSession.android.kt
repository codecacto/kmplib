package br.com.codecacto.kmplib.location.session

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import br.com.codecacto.kmplib.core.util.AppLogger

private const val TAG = "PatrolLocation"

/**
 * Textos e ícone da notificação fixa do serviço de ronda (Android). `null` = o default da lib, no
 * idioma do aparelho ("Ronda em andamento").
 *
 * @property smallIconRes ícone monocromático da barra de status (`R.drawable.ic_notificacao` do app).
 *   Sem ele, o ícone de localização do sistema — use o do app para a pessoa reconhecer de quem é.
 */
data class PatrolNotificationConfig(
    val title: String? = null,
    val text: String? = null,
    val channelName: String? = null,
    @DrawableRes val smallIconRes: Int? = null,
)

internal object LocationSessionHolder {
    @Volatile
    var context: Context? = null
        private set

    @Volatile
    var notification: PatrolNotificationConfig = PatrolNotificationConfig()
        private set

    fun init(context: Context, notification: PatrolNotificationConfig) {
        this.context = context.applicationContext
        this.notification = notification
    }
}

/**
 * Inicializa o `kmplib-location-session`. Chame no `Application.onCreate()`.
 *
 * Não retoma nada sozinho: no Android quem retoma depois da morte do processo é o próprio serviço
 * (`START_STICKY`), e — se ele não puder — [PatrolLocationSession.resumePatrolIfNeeded] no `ON_RESUME`.
 */
fun initKmpLibLocationSession(
    context: Context,
    notification: PatrolNotificationConfig = PatrolNotificationConfig(),
) {
    LocationSessionHolder.init(context, notification)
}

internal actual fun platformPatrolPort(): PatrolPlatformPort = AndroidPatrolPort

internal object AndroidPatrolPort : PatrolPlatformPort {

    private const val PREFS = "kmplib_location_session"
    private const val KEY_SESSION = "session_id"
    private const val KEY_ACCURACY = "accuracy"

    override fun start(sessionId: String, accuracy: PatrolAccuracy): PatrolStartResult {
        val context = LocationSessionHolder.context ?: return PatrolStartResult.NotInitialized
        if (!hasFineLocation(context)) return PatrolStartResult.PermissionRequired
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (manager == null || !LocationManagerCompat.isLocationEnabled(manager)) {
            return PatrolStartResult.LocationDisabled
        }
        return try {
            ContextCompat.startForegroundService(context, PatrolLocationService.startIntent(context, sessionId, accuracy))
            PatrolStartResult.Started
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+) é IllegalStateException:
            // serviço de localização só se inicia com o app na frente.
            AppLogger.w(TAG, "Serviço de ronda recusado: ${e::class.simpleName}")
            PatrolStartResult.Failed("foreground-service-not-allowed")
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "Serviço de ronda sem permissão: ${e::class.simpleName}")
            PatrolStartResult.PermissionRequired
        }
    }

    override fun stop() {
        val context = LocationSessionHolder.context ?: return
        context.stopService(Intent(context, PatrolLocationService::class.java))
    }

    override fun applyAccuracy(accuracy: PatrolAccuracy) {
        val context = LocationSessionHolder.context ?: return
        try {
            context.startService(PatrolLocationService.accuracyIntent(context, accuracy))
        } catch (e: IllegalStateException) {
            // Serviço já em primeiro plano aceita startService; se o processo estiver em 2º plano sem
            // o serviço, não há sessão a ajustar.
            AppLogger.w(TAG, "Ajuste de precisão ignorado: ${e::class.simpleName}")
        }
    }

    override fun loadPersisted(): PersistedPatrol? {
        val context = LocationSessionHolder.context ?: return null
        return loadPersisted(context)
    }

    fun loadPersisted(context: Context): PersistedPatrol? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_SESSION, null)?.takeIf { it.isNotBlank() } ?: return null
        val acc = prefs.getString(KEY_ACCURACY, null)
            ?.let { name -> PatrolAccuracy.entries.firstOrNull { it.name == name } }
            ?: PatrolAccuracy.BALANCED
        return PersistedPatrol(id, acc)
    }

    override fun persist(session: PersistedPatrol?) {
        val context = LocationSessionHolder.context ?: return
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (session == null) {
            editor.clear()
        } else {
            editor.putString(KEY_SESSION, session.sessionId).putString(KEY_ACCURACY, session.accuracy.name)
        }
        // commit, não apply: a sessão salva é o que permite retomar depois de o sistema matar o
        // processo — e é justamente logo depois de iniciar que o processo costuma ir para o 2º plano.
        editor.commit()
    }

    fun hasFineLocation(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Retomar sem o app na frente exige a localização em 2º plano (Android 10+). */
    fun canResumeInBackground(context: Context): Boolean =
        hasFineLocation(context) && (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            )
}

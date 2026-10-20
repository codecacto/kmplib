package br.com.codecacto.kmplib.location.session

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import br.com.codecacto.kmplib.core.util.AppLogger
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * O Foreground Service da ronda (`foregroundServiceType="location"`, declarado no manifesto do
 * módulo). Mantém o Fused Location Provider ligado com a tela apagada e entrega cada leitura ao
 * [PatrolLocationSession].
 *
 * `START_STICKY`: se o sistema matar o processo, recria o serviço com intent nula — e ele retoma a
 * sessão salva, desde que a localização em 2º plano esteja concedida (o Android 14 não dá acesso à
 * localização a um serviço iniciado sem o app na frente sem ela). Sem ela, a sessão fica salva e
 * [PatrolInterruption.STOPPED_BY_SYSTEM] avisa o app para chamar `resumePatrolIfNeeded()`.
 */
class PatrolLocationService : Service() {

    private var client: FusedLocationProviderClient? = null
    private var requesting: PatrolAccuracy? = null

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val controller = PatrolLocationSession.internalController
            result.locations.forEach { controller.onFix(it.toFix()) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val accuracy = intent.accuracyExtra()
                if (!enterForeground()) return START_NOT_STICKY
                requestUpdates(accuracy)
            }
            ACTION_ACCURACY -> {
                if (requesting != null) requestUpdates(intent.accuracyExtra())
            }
            else -> {
                // Recriado pelo sistema (START_STICKY): retoma a sessão salva, se puder.
                val saved = AndroidPatrolPort.loadPersisted(this)
                if (saved == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                val controller = PatrolLocationSession.internalController
                if (!AndroidPatrolPort.canResumeInBackground(this) || !enterForeground()) {
                    controller.onInterrupted(PatrolInterruption.STOPPED_BY_SYSTEM, keepForResume = true)
                    stopSelf()
                    return START_NOT_STICKY
                }
                controller.onRestoredBySystem(saved)
                requestUpdates(saved.accuracy)
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        client?.removeLocationUpdates(callback)
        client = null
        requesting = null
        super.onDestroy()
    }

    /** `startForeground` com o tipo `location`. `false` se a plataforma recusou. */
    private fun enterForeground(): Boolean = try {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        true
    } catch (e: RuntimeException) {
        // SecurityException (sem permissão de localização) ou ForegroundServiceStartNotAllowedException.
        AppLogger.w(TAG, "startForeground recusado: ${e::class.simpleName}")
        stopSelf()
        false
    }

    @SuppressLint("MissingPermission") // Conferida antes de iniciar o serviço (AndroidPatrolPort).
    private fun requestUpdates(accuracy: PatrolAccuracy) {
        val fused = client ?: LocationServices.getFusedLocationProviderClient(this).also { client = it }
        if (requesting != null) fused.removeLocationUpdates(callback)
        val request = LocationRequest.Builder(accuracy.priority(), accuracy.intervalMillis)
            .setMinUpdateIntervalMillis(accuracy.minSpacingMillis)
            .setWaitForAccurateLocation(accuracy == PatrolAccuracy.HIGH)
            .build()
        try {
            fused.requestLocationUpdates(request, callback, Looper.getMainLooper())
            requesting = accuracy
        } catch (e: SecurityException) {
            AppLogger.w(TAG, "Permissão de localização revogada")
            PatrolLocationSession.internalController.onInterrupted(
                PatrolInterruption.PERMISSION_REVOKED,
                keepForResume = false,
            )
            stopSelf()
        }
    }

    private fun buildNotification(): Notification {
        val config = LocationSessionHolder.notification
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                config.channelName ?: getString(R.string.kmplib_patrol_channel_name),
                // LOW: fixa e silenciosa — avisa que a ronda está ativa sem tocar a cada atualização.
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(
                this,
                0,
                it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(config.smallIconRes ?: android.R.drawable.ic_menu_mylocation)
            .setContentTitle(config.title ?: getString(R.string.kmplib_patrol_notification_title))
            .setContentText(config.text ?: getString(R.string.kmplib_patrol_notification_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(launch)
            .build()
    }

    internal companion object {
        private const val TAG = "PatrolLocationService"
        private const val CHANNEL_ID = "kmplib_patrol_location"
        private const val NOTIFICATION_ID = 0x5A7201
        private const val ACTION_START = "br.com.codecacto.kmplib.location.session.START"
        private const val ACTION_ACCURACY = "br.com.codecacto.kmplib.location.session.ACCURACY"
        private const val EXTRA_SESSION = "session_id"
        private const val EXTRA_ACCURACY = "accuracy"

        fun startIntent(context: Context, sessionId: String, accuracy: PatrolAccuracy): Intent =
            Intent(context, PatrolLocationService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_SESSION, sessionId)
                .putExtra(EXTRA_ACCURACY, accuracy.name)

        fun accuracyIntent(context: Context, accuracy: PatrolAccuracy): Intent =
            Intent(context, PatrolLocationService::class.java)
                .setAction(ACTION_ACCURACY)
                .putExtra(EXTRA_ACCURACY, accuracy.name)

        private fun Intent.accuracyExtra(): PatrolAccuracy =
            getStringExtra(EXTRA_ACCURACY)
                ?.let { name -> PatrolAccuracy.entries.firstOrNull { it.name == name } }
                ?: PatrolAccuracy.BALANCED
    }
}

private fun PatrolAccuracy.priority(): Int = when (this) {
    PatrolAccuracy.HIGH -> Priority.PRIORITY_HIGH_ACCURACY
    PatrolAccuracy.BALANCED -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
}

internal fun Location.toFix(): LocationFix = LocationFix(
    latitude = latitude,
    longitude = longitude,
    accuracy = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
    timestampMillis = time,
    isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        isMock
    } else {
        @Suppress("DEPRECATION")
        isFromMockProvider
    },
    speed = if (hasSpeed()) speed else null,
)

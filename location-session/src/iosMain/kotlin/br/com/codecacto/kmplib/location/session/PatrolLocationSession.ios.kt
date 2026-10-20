package br.com.codecacto.kmplib.location.session

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLBackgroundActivitySession
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.CLServiceSession
import platform.CoreLocation.CLServiceSessionAuthorizationRequirementWhenInUse
import platform.CoreLocation.CLActivityTypeOther
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreLocation.kCLLocationAccuracyNearestTenMeters
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSOperatingSystemVersion
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSThread
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject

private const val TAG = "PatrolLocation"

/**
 * Inicializa o `kmplib-location-session` no iOS. Chame na inicialização do app (main thread).
 *
 * Se havia uma ronda ativa quando o sistema encerrou o app, a sessão é RETOMADA aqui: é o que a
 * Apple pede para o `CLBackgroundActivitySession` — recriá-lo logo no relançamento para as
 * atualizações continuarem.
 */
fun initKmpLibLocationSession() {
    PatrolLocationSession.resumePatrolIfNeeded()
}

internal actual fun platformPatrolPort(): PatrolPlatformPort = IosPatrolPort

@OptIn(ExperimentalForeignApi::class)
private fun isAtLeast(major: Long): Boolean =
    NSProcessInfo.processInfo.isOperatingSystemAtLeastVersion(
        cValue<NSOperatingSystemVersion> {
            majorVersion = major
            minorVersion = 0
            patchVersion = 0
        },
    )

/**
 * Core Location da ronda. Tudo na main thread: o `CLLocationManager` entrega no run loop da thread
 * em que nasceu.
 */
@OptIn(ExperimentalForeignApi::class)
internal object IosPatrolPort : PatrolPlatformPort {

    private const val KEY_SESSION = "kmplib.locationSession.sessionId"
    private const val KEY_ACCURACY = "kmplib.locationSession.accuracy"

    private val supportsSourceInformation: Boolean by lazy { isAtLeast(15) }
    private val supportsBackgroundActivitySession: Boolean by lazy { isAtLeast(17) }
    private val supportsServiceSession: Boolean by lazy { isAtLeast(18) }

    private var manager: CLLocationManager? = null

    /** Segura o delegate: `CLLocationManager.delegate` é weak. */
    private var delegate: NSObject? = null
    private var backgroundSession: CLBackgroundActivitySession? = null
    private var serviceSession: CLServiceSession? = null

    override fun start(sessionId: String, accuracy: PatrolAccuracy): PatrolStartResult {
        if (!NSThread.isMainThread) {
            AppLogger.w(TAG, "startPatrol fora da main thread")
            return PatrolStartResult.Failed("not-main-thread")
        }
        if (!hasLocationBackgroundMode()) {
            // allowsBackgroundLocationUpdates = true sem o modo no Info.plist lança
            // NSInternalInconsistencyException — o app cairia. Recusar é o certo.
            AppLogger.e(TAG, "Info.plist sem UIBackgroundModes 'location'")
            return PatrolStartResult.Failed("missing-UIBackgroundModes-location")
        }
        if (!CLLocationManager.locationServicesEnabled()) return PatrolStartResult.LocationDisabled

        val lm = manager ?: CLLocationManager().also { manager = it }
        when (lm.authorizationStatus()) {
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> return PatrolStartResult.PermissionRequired
            else -> Unit
        }

        val d = delegate ?: PatrolDelegate().also { delegate = it }
        lm.delegate = d as CLLocationManagerDelegateProtocol
        lm.activityType = CLActivityTypeOther
        lm.pausesLocationUpdatesAutomatically = false
        lm.allowsBackgroundLocationUpdates = true
        lm.showsBackgroundLocationIndicator = true
        configure(lm, accuracy)

        // iOS 18+: declara que a sessão exige "ao usar o app" — o próprio sistema mostra o pedido
        // quando ainda não decidido, e o diagnóstico vem no delegate.
        if (supportsServiceSession && serviceSession == null) {
            serviceSession = CLServiceSession.sessionRequiringAuthorization(CLServiceSessionAuthorizationRequirementWhenInUse)
        } else if (!supportsServiceSession && lm.authorizationStatus() == kCLAuthorizationStatusNotDetermined) {
            lm.requestWhenInUseAuthorization()
        }
        // iOS 17+: a sessão de atividade em 2º plano — com "ao usar o app", é ela que mantém as
        // atualizações vivas com a tela apagada (e o indicador azul na barra).
        if (supportsBackgroundActivitySession && backgroundSession == null) {
            backgroundSession = CLBackgroundActivitySession.backgroundActivitySession()
        }
        lm.startUpdatingLocation()
        return PatrolStartResult.Started
    }

    override fun stop() {
        manager?.let {
            it.stopUpdatingLocation()
            it.allowsBackgroundLocationUpdates = false
        }
        backgroundSession?.invalidate()
        backgroundSession = null
        serviceSession?.invalidate()
        serviceSession = null
    }

    override fun applyAccuracy(accuracy: PatrolAccuracy) {
        manager?.let { configure(it, accuracy) }
    }

    private fun configure(lm: CLLocationManager, accuracy: PatrolAccuracy) {
        when (accuracy) {
            PatrolAccuracy.HIGH -> {
                lm.desiredAccuracy = kCLLocationAccuracyBest
                lm.distanceFilter = 5.0
            }
            PatrolAccuracy.BALANCED -> {
                lm.desiredAccuracy = kCLLocationAccuracyNearestTenMeters
                lm.distanceFilter = 10.0
            }
        }
    }

    override fun loadPersisted(): PersistedPatrol? {
        val defaults = NSUserDefaults.standardUserDefaults
        val id = defaults.stringForKey(KEY_SESSION)?.takeIf { it.isNotBlank() } ?: return null
        val acc = defaults.stringForKey(KEY_ACCURACY)
            ?.let { name -> PatrolAccuracy.entries.firstOrNull { it.name == name } }
            ?: PatrolAccuracy.BALANCED
        return PersistedPatrol(id, acc)
    }

    override fun persist(session: PersistedPatrol?) {
        val defaults = NSUserDefaults.standardUserDefaults
        if (session == null) {
            defaults.removeObjectForKey(KEY_SESSION)
            defaults.removeObjectForKey(KEY_ACCURACY)
        } else {
            defaults.setObject(session.sessionId, KEY_SESSION)
            defaults.setObject(session.accuracy.name, KEY_ACCURACY)
        }
    }

    private fun hasLocationBackgroundMode(): Boolean {
        val modes = NSBundle.mainBundle.objectForInfoDictionaryKey("UIBackgroundModes") as? List<*>
        return modes?.any { it == "location" } == true
    }

    fun toFix(location: CLLocation): LocationFix {
        val (lat, lng) = location.coordinate.useContents { latitude to longitude }
        val mock = supportsSourceInformation && location.sourceInformation?.isSimulatedBySoftware == true
        return LocationFix(
            latitude = lat,
            longitude = lng,
            accuracy = location.horizontalAccuracy.toFloat(),
            timestampMillis = (location.timestamp.timeIntervalSince1970 * 1000.0).toLong(),
            isMock = mock,
            // speed < 0 = inválida (Core Location).
            speed = location.speed.takeIf { it >= 0.0 }?.toFloat(),
        )
    }

    fun onAuthorizationChanged(status: Int) {
        when (status) {
            kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways ->
                manager?.startUpdatingLocation()
            kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted -> {
                if (PatrolLocationSession.isPatrolActive.value) {
                    PatrolLocationSession.internalController.onInterrupted(
                        PatrolInterruption.PERMISSION_REVOKED,
                        keepForResume = false,
                    )
                }
            }
            else -> Unit
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PatrolDelegate : NSObject(), CLLocationManagerDelegateProtocol {

    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        val controller = PatrolLocationSession.internalController
        didUpdateLocations.forEach { item ->
            (item as? CLLocation)?.let { controller.onFix(IosPatrolPort.toFix(it)) }
        }
    }

    override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
        IosPatrolPort.onAuthorizationChanged(manager.authorizationStatus())
    }

    override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
        // kCLErrorLocationUnknown é transitório (o GPS ainda procura); negação chega pela autorização.
        AppLogger.w(TAG, "Core Location: código ${didFailWithError.code}")
    }
}

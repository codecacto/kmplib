package br.com.codecacto.kmplib.platform.permission

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionRecordPermissionDenied
import platform.AVFAudio.AVAudioSessionRecordPermissionGranted
import platform.AVFAudio.AVAudioSessionRecordPermissionUndetermined
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreLocation.CLAuthorizationStatus
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.darwin.NSObject
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusNotDetermined
import platform.UserNotifications.UNUserNotificationCenter

/**
 * Implementação iOS do [PermissionManager].
 *
 * Mapeamentos:
 * - [AppPermission.MICROPHONE] → `AVAudioSession.recordPermission` (Info.plist `NSMicrophoneUsageDescription`).
 * - [AppPermission.CAMERA] → `AVCaptureDevice` (Info.plist `NSCameraUsageDescription`).
 * - [AppPermission.NOTIFICATIONS] → `UNUserNotificationCenter`.
 * - [AppPermission.LOCATION] → `CLLocationManager` "when in use" (Info.plist
 *   `NSLocationWhenInUseUsageDescription`).
 * - [AppPermission.PHONE_STATE] / [AppPermission.CALL_LOG] → sem equivalente no iOS → [PermissionStatus.GRANTED].
 */
@OptIn(ExperimentalForeignApi::class)
class IosPermissionManager : PermissionManager {

    override fun checkPermission(permission: AppPermission): PermissionStatus = when (permission) {
        AppPermission.MICROPHONE -> when (AVAudioSession.sharedInstance().recordPermission()) {
            AVAudioSessionRecordPermissionGranted -> PermissionStatus.GRANTED
            AVAudioSessionRecordPermissionDenied -> PermissionStatus.PERMANENTLY_DENIED
            AVAudioSessionRecordPermissionUndetermined -> PermissionStatus.NOT_REQUESTED
            else -> PermissionStatus.NOT_REQUESTED
        }

        AppPermission.CAMERA -> mapAvStatus(
            AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
        )

        // Notifications checa de forma assíncrona; aqui devolvemos NOT_REQUESTED como
        // fallback síncrono. Use requestPermission() para o status real.
        AppPermission.NOTIFICATIONS -> PermissionStatus.NOT_REQUESTED

        AppPermission.LOCATION -> mapLocationStatus(CLLocationManager.authorizationStatus())

        AppPermission.PHONE_STATE, AppPermission.CALL_LOG -> PermissionStatus.GRANTED
    }

    override fun requestPermission(permission: AppPermission): Flow<PermissionStatus> = when (permission) {
        AppPermission.PHONE_STATE, AppPermission.CALL_LOG -> flowOf(PermissionStatus.GRANTED)

        AppPermission.MICROPHONE -> callbackFlow {
            AVAudioSession.sharedInstance().requestRecordPermission { granted ->
                trySend(if (granted) PermissionStatus.GRANTED else PermissionStatus.PERMANENTLY_DENIED)
                close()
            }
            awaitClose { }
        }

        AppPermission.CAMERA -> callbackFlow {
            val status = AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)
            if (status == AVAuthorizationStatusNotDetermined) {
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    trySend(if (granted) PermissionStatus.GRANTED else PermissionStatus.PERMANENTLY_DENIED)
                    close()
                }
            } else {
                trySend(mapAvStatus(status))
                close()
            }
            awaitClose { }
        }

        // O `CLLocationManager` responde pelo DELEGATE, e o delegate é uma referência fraca: sem
        // manter o manager vivo dentro do `callbackFlow`, o ARC o coleta antes de o usuário decidir
        // e o diálogo some sem resposta nenhuma — o botão parece não fazer nada.
        AppPermission.LOCATION -> callbackFlow {
            val atual = mapLocationStatus(CLLocationManager.authorizationStatus())
            if (atual != PermissionStatus.NOT_REQUESTED) {
                trySend(atual)
                close()
                awaitClose { }
                return@callbackFlow
            }
            val manager = CLLocationManager()
            val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
                override fun locationManagerDidChangeAuthorization(manager: CLLocationManager) {
                    val status = mapLocationStatus(manager.authorizationStatus)
                    // `notDetermined` ainda chega uma vez, ao instalar o delegate: só o status
                    // terminal encerra o fluxo.
                    if (status == PermissionStatus.NOT_REQUESTED) return
                    trySend(status)
                    close()
                }
            }
            manager.delegate = delegate
            manager.requestWhenInUseAuthorization()
            awaitClose { manager.delegate = null }
        }

        AppPermission.NOTIFICATIONS -> callbackFlow {
            val center = UNUserNotificationCenter.currentNotificationCenter()
            center.getNotificationSettingsWithCompletionHandler { settings ->
                when (settings?.authorizationStatus) {
                    UNAuthorizationStatusAuthorized -> {
                        trySend(PermissionStatus.GRANTED); close()
                    }
                    UNAuthorizationStatusDenied -> {
                        trySend(PermissionStatus.PERMANENTLY_DENIED); close()
                    }
                    UNAuthorizationStatusNotDetermined -> {
                        val options = UNAuthorizationOptionAlert or
                            UNAuthorizationOptionSound or
                            UNAuthorizationOptionBadge
                        center.requestAuthorizationWithOptions(options) { granted, _ ->
                            trySend(if (granted) PermissionStatus.GRANTED else PermissionStatus.PERMANENTLY_DENIED)
                            close()
                        }
                    }
                    else -> {
                        trySend(PermissionStatus.NOT_REQUESTED); close()
                    }
                }
            }
            awaitClose { }
        }
    }

    private fun mapLocationStatus(status: CLAuthorizationStatus): PermissionStatus = when (status) {
        kCLAuthorizationStatusAuthorizedWhenInUse, kCLAuthorizationStatusAuthorizedAlways ->
            PermissionStatus.GRANTED
        // No iOS, negado é definitivo: o único caminho de volta são os Ajustes do sistema.
        kCLAuthorizationStatusDenied, kCLAuthorizationStatusRestricted ->
            PermissionStatus.PERMANENTLY_DENIED
        kCLAuthorizationStatusNotDetermined -> PermissionStatus.NOT_REQUESTED
        else -> PermissionStatus.NOT_REQUESTED
    }

    private fun mapAvStatus(status: platform.AVFoundation.AVAuthorizationStatus): PermissionStatus =
        when (status) {
            AVAuthorizationStatusAuthorized -> PermissionStatus.GRANTED
            AVAuthorizationStatusDenied -> PermissionStatus.PERMANENTLY_DENIED
            AVAuthorizationStatusRestricted -> PermissionStatus.PERMANENTLY_DENIED
            AVAuthorizationStatusNotDetermined -> PermissionStatus.NOT_REQUESTED
            else -> PermissionStatus.NOT_REQUESTED
        }
}

actual fun createPermissionManager(): PermissionManager = IosPermissionManager()

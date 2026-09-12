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
 * O manager e o delegate de **um** pedido de permissão de localização, presos juntos.
 *
 * ⚠️ `CLLocationManager.delegate` é **weak**: quem tem de segurar o delegate é quem o criou. Reter
 * só o manager (o que a closure do `awaitClose` fazia até a 2.197.0) não segura o delegate — sem
 * dono, o ARC pode liberá-lo antes de a pessoa responder ao diálogo do sistema, o `callbackFlow`
 * nunca emite e a tela fica esperando uma permissão **que já foi concedida**.
 *
 * ### Por que um objeto por pedido, e não uma referência de módulo (2.199.0)
 * A 2.198.0 fechou o buraco com um `var` de módulo, limpo no `awaitClose`. Isso só está certo
 * enquanto houver **um pedido por vez** — e nada impõe isso: duas telas compostas ao mesmo tempo,
 * ou um recompose que recoleta o fluxo, bastam. Aí o primeiro a fechar anula a referência do
 * **outro**, que ainda está esperando, e volta exatamente o defeito acima.
 *
 * A referência agora vive na closure do `awaitClose` deste fluxo: ela existe enquanto o fluxo
 * existir, morre quando ele fecha, e um pedido não enxerga o outro. Sem contador de referências e
 * sem estado compartilhado — portanto sem disputa entre threads, que num `var` global de
 * Kotlin/Native seria o próximo problema.
 */
@OptIn(ExperimentalForeignApi::class)
private class PedidoDeLocalizacao(
    val manager: CLLocationManager,
    /** Não é lido: existe para manter o delegate vivo enquanto o pedido durar. */
    val delegate: NSObject,
)

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
        // manter o par manager+delegate vivo enquanto o fluxo existir, o ARC os coleta antes de o
        // usuário decidir e o diálogo some sem resposta nenhuma — o botão parece não fazer nada.
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
            // A referência forte DESTE pedido — é a closure do `awaitClose` que a segura, e ela
            // vive tanto quanto o fluxo. Ver [PedidoDeLocalizacao]: guardar o delegate num `var`
            // de módulo quebraria dois pedidos simultâneos, um anulando a referência do outro.
            val pedido = PedidoDeLocalizacao(manager, delegate)
            manager.delegate = delegate
            manager.requestWhenInUseAuthorization()
            awaitClose {
                pedido.manager.delegate = null
            }
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

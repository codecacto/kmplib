package br.com.codecacto.kmplib.platform.permission

import kotlinx.coroutines.flow.Flow

/**
 * Status de uma permissão de runtime.
 *
 * - [GRANTED]: concedida.
 * - [DENIED]: negada (ainda é possível pedir de novo).
 * - [PERMANENTLY_DENIED]: negada com "não perguntar novamente" / negada no iOS após a 1ª vez
 *   — o app deve direcionar o usuário para os Ajustes do sistema.
 * - [NOT_REQUESTED]: ainda não solicitada (estado inicial; iOS = `notDetermined`).
 */
enum class PermissionStatus {
    GRANTED,
    DENIED,
    PERMANENTLY_DENIED,
    NOT_REQUESTED
}

/**
 * Permissões de runtime suportadas, de forma multiplataforma. Extensível conforme novos apps.
 *
 * Mapeamento por plataforma:
 * - [MICROPHONE]: Android `RECORD_AUDIO`; iOS `AVAudioSession` record permission
 *   (Info.plist `NSMicrophoneUsageDescription`).
 * - [PHONE_STATE]: Android `READ_PHONE_STATE`; **iOS não tem equivalente** → reportado como
 *   [PermissionStatus.GRANTED] (no-op) para não bloquear fluxos comuns.
 * - [CALL_LOG]: Android `READ_CALL_LOG`; **iOS não tem equivalente** → [PermissionStatus.GRANTED].
 * - [NOTIFICATIONS]: Android `POST_NOTIFICATIONS` (API 33+); iOS `UNUserNotificationCenter`.
 * - [CAMERA]: Android `CAMERA`; iOS `AVCaptureDevice` (Info.plist `NSCameraUsageDescription`).
 * - [LOCATION]: Android `ACCESS_COARSE_LOCATION`; iOS `CLLocationManager` "when in use"
 *   (Info.plist `NSLocationWhenInUseUsageDescription`).
 */
enum class AppPermission {
    MICROPHONE,
    PHONE_STATE,
    CALL_LOG,
    NOTIFICATIONS,
    CAMERA,

    /**
     * Localização aproximada, para "perto de mim" / ordenar por distância.
     *
     * **COARSE, e não FINE**, de propósito: ordenar uma lista por distância não guia ninguém até a
     * porta, e o Android mostra ao usuário qual das duas o app pediu — pedir a precisa "por
     * precaução" é pedir mais do que o produto usa, e é motivo de recusa.
     *
     * O app que a usa precisa declarar `ACCESS_COARSE_LOCATION` no manifesto (Android) e
     * `NSLocationWhenInUseUsageDescription` no `Info.plist` (iOS). Sem a declaração, o sistema
     * **nega sem mostrar diálogo** e o botão vira um controle mudo.
     */
    LOCATION
}

/**
 * Gerenciador de permissões de runtime multiplataforma.
 *
 * Implementações:
 * - Android: `ActivityCompat.requestPermissions` + `shouldShowRequestPermissionRationale`
 *   ([br.com.codecacto.kmplib.platform.permission.AndroidPermissionManager]). Requer
 *   [PermissionHostHolder.setActivity] no `onResume()` e o repasse de
 *   `onRequestPermissionsResult` → [PermissionHostHolder.handlePermissionResult].
 * - iOS: APIs nativas por permissão ([br.com.codecacto.kmplib.platform.permission.IosPermissionManager]).
 *
 * Obtenha via [createPermissionManager] (ou o helper Compose [rememberPermissionManager]).
 *
 * Exemplo (em um ViewModel):
 * ```kotlin
 * private val permissions = createPermissionManager()
 *
 * fun pedirMicrofone() {
 *     permissions.requestPermission(AppPermission.MICROPHONE)
 *         .onEach { status ->
 *             setState { copy(micStatus = status) }
 *             if (status == PermissionStatus.PERMANENTLY_DENIED) sendEffect(Effect.AbrirAjustes)
 *         }
 *         .launchIn(viewModelScope)
 * }
 * ```
 */
interface PermissionManager {

    /**
     * Verifica (sincronamente) o status atual de [permission], sem solicitar nada ao usuário.
     */
    fun checkPermission(permission: AppPermission): PermissionStatus

    /**
     * Solicita [permission] e emite o(s) status resultante(s).
     *
     * Emite ao menos um valor: o status final após a interação do usuário (ou imediatamente,
     * se já concedida / não houver equivalente na plataforma). O [Flow] completa após emitir
     * o status terminal.
     */
    fun requestPermission(permission: AppPermission): Flow<PermissionStatus>
}

/**
 * Cria a implementação de [PermissionManager] para a plataforma atual.
 *
 * No Android, o `requestPermission` exige que a Activity esteja registrada via
 * [PermissionHostHolder.setActivity] (chame em `onResume()`); caso contrário, devolve o status
 * atual sem abrir o diálogo.
 */
expect fun createPermissionManager(): PermissionManager

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
 * - [PRECISE_LOCATION] (2.312.0): Android `ACCESS_FINE_LOCATION` + `ACCESS_COARSE_LOCATION` no MESMO
 *   pedido (o diálogo do Android 12+ com "Precisa"/"Aproximada"); iOS "when in use" com precisão
 *   total — se a pessoa deixou "Localização precisa" desligada, `requestTemporaryFullAccuracyAuthorization`.
 *
 * **Bluetooth NÃO entra aqui, de propósito:** no iOS pedir a permissão é criar um `CBCentralManager`, e
 * a App Store recusa (ITMS-90683) todo app cujo binário referencie o CoreBluetooth sem
 * `NSBluetoothAlwaysUsageDescription` — seriam todos os apps com `kmplib-platform`. O pedido mora em
 * `kmplib-health`: `HeartRateMonitor.permissionStatus()`/`requestPermission()` (2.282.0).
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
    LOCATION,

    /**
     * Localização **precisa durante o uso** (2.312.0) — para quem guia ou confere presença no lugar
     * (ronda: "chegou ao ponto, a ≤ 30 m"). A aproximada do Android é de ~3 km: não serve para isso.
     *
     * - **Android:** pede `ACCESS_FINE_LOCATION` **junto** com `ACCESS_COARSE_LOCATION`, como manda a
     *   documentação do Android 12+ (pedir só a FINE é ignorado pelo sistema). O diálogo mostra
     *   "Precisa" e "Aproximada"; [PermissionStatus.GRANTED] só com a precisa. Quem escolheu a
     *   aproximada fica [PermissionStatus.DENIED] (pedir de novo mostra o diálogo de troca para a
     *   precisa) ou [PermissionStatus.PERMANENTLY_DENIED] (só as Configurações). Declare as DUAS no
     *   manifesto do app.
     * - **iOS:** autorização "when in use" **com** `accuracyAuthorization == fullAccuracy`. Se a pessoa
     *   desligou "Localização precisa" (iOS 14+), o pedido chama
     *   `requestTemporaryFullAccuracyAuthorization(withPurposeKey:)` com
     *   [LocationAccuracySettings.temporaryFullAccuracyPurposeKey] — o `Info.plist` precisa de
     *   `NSLocationTemporaryUsageDescriptionDictionary` com essa chave (sem ela, o sistema nega calado).
     *   Recusada a temporária = [PermissionStatus.DENIED] (pode pedir de novo em outra sessão).
     *
     * O nível atual (nenhuma/aproximada/precisa) sai de [PermissionManager.locationAccuracy].
     */
    PRECISE_LOCATION,
}

/** Precisão da localização que o app pode ler agora (2.312.0). */
enum class LocationAccuracy {
    /** Sem permissão de localização. */
    NONE,

    /** Só a aproximada (Android COARSE, ~3 km; iOS "Localização precisa" desligada, ~1–3 km). */
    APPROXIMATE,

    /** A precisa (Android FINE; iOS `fullAccuracy`, inclusive a temporária desta sessão). */
    PRECISE,
}

/**
 * Configuração do pedido de precisão no iOS (2.312.0). Defina **uma vez**, na inicialização, se a
 * chave do `Info.plist` não for a default.
 */
object LocationAccuracySettings {
    /**
     * Chave de `NSLocationTemporaryUsageDescriptionDictionary` usada por
     * `requestTemporaryFullAccuracyAuthorization(withPurposeKey:)` no [AppPermission.PRECISE_LOCATION].
     */
    var temporaryFullAccuracyPurposeKey: String = DEFAULT_TEMPORARY_FULL_ACCURACY_PURPOSE_KEY

    /** Default da chave de propósito: `"PreciseLocation"`. */
    const val DEFAULT_TEMPORARY_FULL_ACCURACY_PURPOSE_KEY: String = "PreciseLocation"
}

/**
 * Precisão a partir do status das duas permissões — pura, usada pelo default de
 * [PermissionManager.locationAccuracy].
 */
fun locationAccuracyOf(location: PermissionStatus, preciseLocation: PermissionStatus): LocationAccuracy = when {
    preciseLocation == PermissionStatus.GRANTED -> LocationAccuracy.PRECISE
    location == PermissionStatus.GRANTED -> LocationAccuracy.APPROXIMATE
    else -> LocationAccuracy.NONE
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

    /**
     * Status atual de [permission] consultado da forma que a plataforma recomenda — **sem** pedir
     * nada ao usuário.
     *
     * Existe porque há permissão cujo status só se lê de forma **assíncrona**: no iOS, notificação
     * é `UNUserNotificationCenter.getNotificationSettings(completionHandler:)`, e o
     * [checkPermission] síncrono não tem como responder (devolve [PermissionStatus.NOT_REQUESTED]).
     * Tela que precisa saber "o usuário desligou as notificações?" — o `PermissionBanner` do
     * `kmplib-ui`, por exemplo — usa esta, nunca o [checkPermission].
     *
     * O default delega ao [checkPermission], para não quebrar implementações mantidas por apps
     * (fakes de teste). As implementações da lib sobrescrevem onde a plataforma exige.
     */
    suspend fun currentStatus(permission: AppPermission): PermissionStatus = checkPermission(permission)

    /**
     * Precisão da localização que o app tem agora (2.312.0), sem pedir nada: [LocationAccuracy.PRECISE]
     * só com [AppPermission.PRECISE_LOCATION] concedida. Para mostrar "Ative a localização precisa"
     * antes de iniciar uma ronda.
     */
    fun locationAccuracy(): LocationAccuracy = locationAccuracyOf(
        location = checkPermission(AppPermission.LOCATION),
        preciseLocation = checkPermission(AppPermission.PRECISE_LOCATION),
    )
}

/**
 * Status de notificação combinando a permissão de runtime com o **interruptor do sistema**.
 *
 * No Android, notificação tem duas travas: a permissão `POST_NOTIFICATIONS` (API 33+) e o
 * interruptor "Mostrar notificações" nas Configurações do app (`areNotificationsEnabled`). Abaixo da
 * API 33 não há permissão de runtime — ela responde "concedida" — e o único jeito de o usuário
 * recusar é o interruptor. Sem esta combinação, o app diz "notificação ativa" para quem as desligou,
 * e o aviso de "ative as notificações" nunca aparece.
 *
 * Com a permissão concedida e o interruptor desligado, a resposta é
 * [PermissionStatus.PERMANENTLY_DENIED]: pedir de novo não abre diálogo, só as Configurações
 * resolvem. Nos demais casos vale o status da permissão. Pura, testável.
 */
fun combineNotificationStatus(
    runtimeStatus: PermissionStatus,
    notificationsEnabledInSystem: Boolean,
): PermissionStatus =
    if (runtimeStatus == PermissionStatus.GRANTED && !notificationsEnabledInSystem) {
        PermissionStatus.PERMANENTLY_DENIED
    } else {
        runtimeStatus
    }

/**
 * Cria a implementação de [PermissionManager] para a plataforma atual.
 *
 * No Android, o `requestPermission` exige que a Activity esteja registrada via
 * [PermissionHostHolder.setActivity] (chame em `onResume()`); caso contrário, devolve o status
 * atual sem abrir o diálogo.
 */
expect fun createPermissionManager(): PermissionManager

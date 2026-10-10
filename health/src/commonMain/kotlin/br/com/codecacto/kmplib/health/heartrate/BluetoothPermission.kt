package br.com.codecacto.kmplib.health.heartrate

/**
 * Permissão de usar o Bluetooth para a FC ao vivo ([HeartRateMonitor.permissionStatus] /
 * [HeartRateMonitor.requestPermission]). Mesmo vocabulário do `PermissionStatus` do `kmplib-platform`,
 * de propósito — a tela trata os quatro estados do mesmo jeito:
 *
 * - [GRANTED]: pode procurar e conectar.
 * - [DENIED]: negada, e o sistema ainda deixa pedir de novo (mostrar o porquê e oferecer "Permitir").
 * - [PERMANENTLY_DENIED]: negada de vez ("não perguntar novamente" / 2ª negação no Android 11+;
 *   no iOS, qualquer negação ou restrição) — só os Ajustes do sistema resolvem.
 * - [NOT_REQUESTED]: nunca pedida (mostrar a tela de contexto antes do diálogo do sistema).
 *
 * **Por que mora aqui e não no `AppPermission` do `kmplib-platform`:** no iOS, pedir a permissão é
 * criar um `CBCentralManager` — e a App Store recusa o upload (ITMS-90683) de QUALQUER app cujo
 * binário referencie o CoreBluetooth sem `NSBluetoothAlwaysUsageDescription` no `Info.plist`. Quase
 * todo app da fábrica leva o `kmplib-platform`; só quem usa FC por Bluetooth leva este módulo, e esse
 * já declara a frase.
 */
enum class BluetoothPermissionStatus { GRANTED, DENIED, PERMANENTLY_DENIED, NOT_REQUESTED }

/**
 * Status atual pelo que o Android expõe (pura — a regra de [BluetoothPermissionStatus] num lugar só).
 *
 * O Android não diz "nunca pedida": `shouldShowRequestPermissionRationale` é `false` antes do 1º pedido
 * E depois da negação definitiva. Por isso a lib lembra que já pediu ([askedBefore]).
 *
 * @param allGranted todas as permissões necessárias concedidas.
 * @param askedBefore a lib já abriu o diálogo ao menos uma vez neste aparelho.
 * @param canExplain `shouldShowRequestPermissionRationale` de alguma permissão negada; `null` = sem
 *   Activity para perguntar (aí a resposta conservadora é [BluetoothPermissionStatus.DENIED]).
 */
internal fun bluetoothPermissionStatusOf(
    allGranted: Boolean,
    askedBefore: Boolean,
    canExplain: Boolean?,
): BluetoothPermissionStatus = when {
    allGranted -> BluetoothPermissionStatus.GRANTED
    !askedBefore -> BluetoothPermissionStatus.NOT_REQUESTED
    canExplain == false -> BluetoothPermissionStatus.PERMANENTLY_DENIED
    else -> BluetoothPermissionStatus.DENIED
}

/**
 * Resultado do diálogo do Android (pura): concedeu tudo → [BluetoothPermissionStatus.GRANTED]; negou e o
 * sistema ainda deixa explicar → [BluetoothPermissionStatus.DENIED]; negou e não deixa →
 * [BluetoothPermissionStatus.PERMANENTLY_DENIED].
 */
internal fun bluetoothPermissionRequestResult(allGranted: Boolean, canExplain: Boolean): BluetoothPermissionStatus =
    when {
        allGranted -> BluetoothPermissionStatus.GRANTED
        canExplain -> BluetoothPermissionStatus.DENIED
        else -> BluetoothPermissionStatus.PERMANENTLY_DENIED
    }

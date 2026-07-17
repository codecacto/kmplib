package br.com.codecacto.kmplib.push

/**
 * Fábrica multiplataforma do [PushNotificationService], o **ponto de extensão comum** de push.
 *
 * Resolve a implementação certa por plataforma sem o app saber qual é:
 *  - **Android** → [KmpPushNotificationService] (KMPNotifier sobre FCM). O app deve, antes de
 *    consumir o token, inicializar o `FirebaseApp` manualmente (`initFirebaseForPush`, sem
 *    `google-services.json`/plugin) e chamar `NotifierManager.initialize(...)` no entry point.
 *  - **iOS** → `ApplePushNotificationService`, um bridge **APNs-direto** (sem FCM/`plist`) que
 *    alimenta o mesmo [PushNotificationListener] via `ApplePushBridge`. `getToken()` devolve o
 *    device token APNs (hex) em cache.
 *
 * Aditivo: apps que já constroem [KmpPushNotificationService] diretamente seguem funcionando.
 */
expect fun createPushNotificationService(
    listener: PushNotificationListener = NoOpPushNotificationListener,
): PushNotificationService

/**
 * Fábrica multiplataforma do [LocalPushNotifier] (notificação local de foreground).
 *
 *  - **Android** → delega ao `LocalNotifier` do KMPNotifier.
 *  - **iOS** → `UNUserNotificationCenter` (entrega imediata).
 */
expect fun createLocalPushNotifier(): LocalPushNotifier

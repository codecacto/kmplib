package br.com.codecacto.kmplib.push.central

/**
 * De onde o registro central lê o token e o provedor do aparelho (2.360.0). A implementação da
 * plataforma é [platformPushDeviceSource]; troque por um dublê em teste.
 */
interface PushDeviceSource {
    /** `"android"` ou `"ios"` — o `plataforma` do cadastro. */
    val platform: String

    /** `"fcm"` (Android) ou `"apns"` (iOS) — o `provedor` do cadastro. */
    val provider: String

    /**
     * O token atual do push, ou `null` se ainda não existe (FCM não inicializado, APNs ainda sem
     * `didRegisterForRemoteNotifications`). Nunca lança.
     */
    suspend fun token(): String?

    /**
     * O project id do Firebase dono do token FCM — o `projetoFcm` do cadastro, com o qual o servidor
     * escolhe a credencial de envio. `null` no iOS (APNs direto) ou se não há `FirebaseApp` default.
     */
    fun fcmProjectId(): String?
}

/**
 * A fonte da plataforma:
 * - **Android** — token do FCM pelo KMPNotifier (`NotifierManager.getPushNotifier().getToken()`),
 *   provedor `fcm`, e `projetoFcm` = `FirebaseApp` **default** `.options.projectId` — a mesma app que
 *   o FCM usa: a do `initFirebaseForPush` ou a criada pelo `google-services.json`.
 * - **iOS** — token APNs em hexadecimal do `ApplePushBridge`, provedor `apns`, sem `projetoFcm`.
 */
expect fun platformPushDeviceSource(): PushDeviceSource

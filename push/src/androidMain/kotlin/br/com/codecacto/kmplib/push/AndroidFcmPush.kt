package br.com.codecacto.kmplib.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

/**
 * Estratégia de **App ID do FCM** para inicialização MANUAL do `FirebaseApp` no Android — SEM
 * `google-services.json` e SEM o plugin `google-services` (elimina a cerimônia Firebase por app e o
 * erro `processDebugGoogleServices`), mantendo o projeto central `code-cacto`.
 *
 * O default é [PerApp] (padrão-ouro por-app: cada app registra o **próprio** App ID uma vez no
 * console do projeto `code-cacto`, sem baixar json). [Shared] é um **atalho opt-in** que reusa um
 * App ID já existente do `code-cacto` — útil só para o fundador testar push em device rapidamente,
 * nunca o caminho de produção.
 */
sealed interface AndroidFcmAppId {
    /** `mobilesdk_app_id` do Firebase (formato `1:<sender>:android:<hash>`). */
    val applicationId: String

    /** `current_key` / API key Android do app no console. */
    val apiKey: String

    /** `project_id` (o projeto Firebase — aqui, `code-cacto`). */
    val projectId: String

    /** `project_number` / Sender ID do FCM. */
    val gcmSenderId: String

    /**
     * App ID **próprio** do app, registrado uma vez no console do `code-cacto` (gold-standard).
     * Sem `google-services.json`: os 4 campos vêm de constantes do app (ex.: `BuildConfig`).
     */
    data class PerApp(
        override val applicationId: String,
        override val apiKey: String,
        override val projectId: String,
        override val gcmSenderId: String,
    ) : AndroidFcmAppId

    /**
     * App ID **compartilhado** do `code-cacto` (atalho opt-in). `projectId`/`gcmSenderId` já vêm
     * pré-preenchidos com o projeto central; informe `applicationId`/`apiKey` do App ID a reutilizar.
     */
    data class Shared(
        override val applicationId: String,
        override val apiKey: String,
        override val projectId: String = "code-cacto",
        override val gcmSenderId: String = "234743070333",
    ) : AndroidFcmAppId
}

/**
 * Inicializa o `FirebaseApp` **default** manualmente a partir de [appId], sem plugin
 * google-services. Deve ser chamado **antes** de `NotifierManager.initialize(...)` (KMPNotifier
 * reusa o app default para o FCM) e antes de qualquer uso de `FirebaseMessaging`/`AuthRepository`
 * que dependa do app default.
 *
 * **Idempotente:** se um `FirebaseApp` default já existir (por exemplo, plugin google-services ainda
 * presente em algum consumidor, ou dupla chamada), reusa o existente em vez de lançar
 * `IllegalStateException`. Isso torna a mudança aditiva e reversível — não quebra apps que ainda
 * usam o fluxo antigo.
 *
 * @return o `FirebaseApp` default (recém-criado ou preexistente).
 */
fun initFirebaseForPush(context: Context, appId: AndroidFcmAppId): FirebaseApp {
    FirebaseApp.getApps(context)
        .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
        ?.let { return it }

    val options = FirebaseOptions.Builder()
        .setApplicationId(appId.applicationId)
        .setApiKey(appId.apiKey)
        .setProjectId(appId.projectId)
        .setGcmSenderId(appId.gcmSenderId)
        .build()

    return FirebaseApp.initializeApp(context, options)
}

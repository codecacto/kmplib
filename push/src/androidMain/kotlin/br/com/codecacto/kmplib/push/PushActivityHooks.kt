package br.com.codecacto.kmplib.push

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.mmk.kmpnotifier.extensions.onCreateOrOnNewIntent
import com.mmk.kmpnotifier.notification.NotifierManager

/**
 * Os dois ganchos da Activity que fazem o **toque na notificação** chegar ao app no Android (2.363.0).
 *
 * O KMPNotifier só dispara o clique (`onNotificationClicked` → [PushEvents] → `PushTapHost`/ouvinte do
 * app) por `NotifierManager.onCreateOrOnNewIntent(intent)`. Sem essa chamada a notificação aparece e o
 * toque só abre o app — o defeito que a revisão de 12/nov/2026 achou em 7 apps e na casca. Chamar à mão
 * tem duas armadilhas, e este helper cobre as duas:
 *
 * - **Rotação / recriação**: no `onCreate` o `intent` é o mesmo que abriu a Activity; reentregá-lo a cada
 *   rotação abre o Premium/comunicado de novo. Só entrega com `savedInstanceState == null` e quando a
 *   Activity não foi relançada pelo histórico (Recentes devolve o intent antigo).
 * - **App já aberto**: o toque chega em `onNewIntent`; sem `setIntent` a próxima recriação lê o intent
 *   velho. A Activity deve ser `launchMode="singleTop"` (ou `singleTask`): o `PendingIntent` do
 *   KMPNotifier usa `FLAG_ACTIVITY_CLEAR_TOP`, e com `standard` a Activity é recriada a cada toque.
 *
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         PushActivityHooks.onCreate(this, savedInstanceState)
 *         setContent { App() }
 *     }
 *
 *     override fun onNewIntent(intent: Intent) {
 *         super.onNewIntent(intent)
 *         PushActivityHooks.onNewIntent(this, intent)
 *     }
 * }
 * ```
 *
 * Também instala o repasse do KMPNotifier ao hub ([attachKmpNotifier], idempotente) antes de entregar:
 * o toque a frio fica pendente em [PushEvents] mesmo que o serviço de push só nasça depois do login.
 * Requer `NotifierManager.initialize(...)` já feito (no `Application.onCreate`). Nunca lança.
 */
object PushActivityHooks {

    /** Chame no `Activity.onCreate`, depois do `super.onCreate`. */
    fun onCreate(activity: Activity, savedInstanceState: Bundle?) {
        if (savedInstanceState != null) return
        val intent = activity.intent ?: return
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        deliver(intent)
    }

    /** Chame no `Activity.onNewIntent`, depois do `super.onNewIntent`. Faz o `setIntent` também. */
    fun onNewIntent(activity: Activity, intent: Intent) {
        activity.intent = intent
        deliver(intent)
    }

    private fun deliver(intent: Intent) {
        runCatching {
            attachKmpNotifier()
            NotifierManager.onCreateOrOnNewIntent(intent)
        }
    }
}

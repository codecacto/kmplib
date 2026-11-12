package br.com.codecacto.kmplib.platform

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.OnNewIntentProvider
import androidx.core.util.Consumer
import br.com.codecacto.kmplib.core.util.AppLogger
import java.util.Collections
import java.util.WeakHashMap

/**
 * Converte o toque no corpo de uma notificação local da lib (o `Intent` que abre a `Activity`) em
 * [NotificationTaps] — 2.357.0.
 *
 * O `initKmpLibPlatform` registra um `ActivityLifecycleCallbacks` que lê o `Intent` no `onCreate` de
 * toda `Activity` e, nas que são `OnNewIntentProvider` (toda `ComponentActivity`), no `onNewIntent` —
 * o toque com o app já aberto, que a leitura manual no `onCreate` perdia. É a forma oficial de
 * observar o `onNewIntent` sem herança (androidx.core `OnNewIntentProvider`).
 */
internal object NotificationTapIntents {

    private const val TAG = "NotificationTap"

    /** Marca do toque (o extra de sempre, `notification_id`, também aparece no `PendingIntent` de ação). */
    const val EXTRA_TAP_ID = "kmplib.notification.tap"

    /** O `data` inteiro, num `Bundle` — os extras soltos se misturam com os do próprio app. */
    const val EXTRA_TAP_DATA = "kmplib.notification.tap.data"

    @Volatile
    private var registered = false

    private val observed: MutableSet<Activity> = Collections.newSetFromMap(WeakHashMap())

    fun mark(intent: Intent, id: Int, data: Map<String, String>) {
        intent.putExtra(EXTRA_TAP_ID, id)
        intent.putExtra(EXTRA_TAP_DATA, Bundle().apply { data.forEach { (k, v) -> putString(k, v) } })
    }

    /**
     * Lê e CONSOME a marca de [intent] (o extra sai do `Intent`, então recriar a `Activity` não entrega
     * de novo). `Intent` aberto pelos recentes (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) é o toque antigo
     * reaparecendo: descarta a marca sem entregar.
     */
    fun handle(intent: Intent?): Boolean {
        if (intent == null || !intent.hasExtra(EXTRA_TAP_ID)) return false
        val id = intent.getIntExtra(EXTRA_TAP_ID, Int.MIN_VALUE)
        val bundle = intent.getBundleExtra(EXTRA_TAP_DATA)
        intent.removeExtra(EXTRA_TAP_ID)
        intent.removeExtra(EXTRA_TAP_DATA)
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return false
        if (id == Int.MIN_VALUE) return false
        val data = buildMap {
            bundle?.keySet()?.forEach { k -> bundle.getString(k)?.let { put(k, it) } }
        }
        NotificationTaps.deliver(id, data)
        AppLogger.d(TAG, "toque na notificação id=$id")
        return true
    }

    fun register(context: Context) {
        if (registered) return
        val app = context.applicationContext as? Application ?: return
        registered = true
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Recriada (giro, processo restaurado): o toque já foi entregue na 1ª criação.
                if (savedInstanceState == null) handle(activity.intent)
                if (activity is OnNewIntentProvider && observed.add(activity)) {
                    activity.addOnNewIntentListener(Consumer { intent -> handle(intent) })
                }
            }
            override fun onActivityStarted(activity: Activity) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivityStopped(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) {
                observed.remove(activity)
            }
        })
    }
}

/**
 * Entrega a [NotificationTaps] o toque no corpo de notificação local da lib que veio em [intent] —
 * para quem abre OUTRA `Activity` pelo toque ou inicializa a lib depois da `Activity` de entrada
 * (2.357.0). O `initKmpLibPlatform` já faz isso sozinho no `onCreate`/`onNewIntent`; chamar de novo é
 * inofensivo (a marca sai do `Intent` na primeira leitura).
 *
 * @return `true` se [intent] trazia um toque da lib.
 */
fun handleNotificationTapIntent(intent: Intent?): Boolean = NotificationTapIntents.handle(intent)

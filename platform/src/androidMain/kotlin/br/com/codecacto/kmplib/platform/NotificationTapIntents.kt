package br.com.codecacto.kmplib.platform

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.OnNewIntentProvider
import androidx.core.util.Consumer
import br.com.codecacto.kmplib.core.context.AndroidAppContext
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
 *
 * Desde a 2.357.1 só entrega toque com prova de origem ([NotificationTapProof]): a `Activity` de
 * abertura é exportada, então os extras sozinhos podiam ser forjados por outro app.
 */
internal object NotificationTapIntents {

    private const val TAG = "NotificationTap"

    /** Marca do toque (o extra de sempre, `notification_id`, também aparece no `PendingIntent` de ação). */
    const val EXTRA_TAP_ID = "kmplib.notification.tap"

    /** O `data` inteiro, num `Bundle` — os extras soltos se misturam com os do próprio app. */
    const val EXTRA_TAP_DATA = "kmplib.notification.tap.data"

    /** A prova de origem ([NotificationTapProof]) — HMAC de `id` + `data` (2.357.1). */
    const val EXTRA_TAP_PROOF = "kmplib.notification.tap.proof"

    @Volatile
    private var registered = false

    @Volatile
    private var appContext: Context? = null

    private val observed: MutableSet<Activity> = Collections.newSetFromMap(WeakHashMap())

    /**
     * Marca [intent] como toque da lib, com a prova de origem. Sem chave (falha rara de disco) a
     * marca não vai — o toque abre o app com os extras antigos, mas não vira [NotificationTap].
     */
    fun mark(context: Context, intent: Intent, id: Int, data: Map<String, String>) {
        val key = NotificationTapProof.key(context) ?: return
        intent.putExtra(EXTRA_TAP_ID, id)
        intent.putExtra(EXTRA_TAP_DATA, Bundle().apply { data.forEach { (k, v) -> putString(k, v) } })
        intent.putExtra(EXTRA_TAP_PROOF, NotificationTapProof.sign(key, id, data))
    }

    /** O que veio no `Intent`, já lido (o `Bundle` desserializado). */
    internal class RawTap(
        val id: Int,
        val data: Map<String, String>,
        val proof: ByteArray?,
        val fromHistory: Boolean,
    )

    /**
     * Lê e CONSOME a marca de [intent] (o extra sai do `Intent`, então recriar a `Activity` não entrega
     * de novo). `Intent` aberto pelos recentes (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`) é o toque antigo
     * reaparecendo: descarta a marca sem entregar. **Sem prova válida, descarta** (2.357.1): a
     * `Activity` de abertura é exportada, e outro app pode mandar os mesmos extras.
     */
    fun handle(intent: Intent?): Boolean {
        if (intent == null) return false
        val raw = readSafely { read(intent) }
        if (raw == null) {
            runCatching { strip(intent) } // ilegível: não tenta de novo na próxima leitura
            return false
        }
        val context = appContext ?: AndroidAppContext.get()
        return accept(raw, context?.let(NotificationTapProof::key))
    }

    /** Lê e tira a marca. `null` = não é toque da lib. Pode lançar (Bundle externo) — ver [readSafely]. */
    private fun read(intent: Intent): RawTap? {
        if (!intent.hasExtra(EXTRA_TAP_ID)) return null
        val id = intent.getIntExtra(EXTRA_TAP_ID, Int.MIN_VALUE)
        val proof = intent.getByteArrayExtra(EXTRA_TAP_PROOF)
        val bundle = intent.getBundleExtra(EXTRA_TAP_DATA)
        val data = buildMap {
            bundle?.keySet()?.forEach { k -> bundle.getString(k)?.let { put(k, it) } }
        }
        strip(intent)
        return RawTap(id, data, proof, intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0)
    }

    private fun strip(intent: Intent) {
        intent.removeExtra(EXTRA_TAP_ID)
        intent.removeExtra(EXTRA_TAP_DATA)
        intent.removeExtra(EXTRA_TAP_PROOF)
    }

    /**
     * Roda [read] sem deixar escapar exceção: o `Bundle` de um `Intent` externo pode trazer
     * `Parcelable` desconhecido (`BadParcelableException`, `ClassNotFoundException`), e isso não
     * pode derrubar o `onCreate` da `Activity`. O log leva só o tipo do erro, nunca o conteúdo.
     */
    internal fun readSafely(read: () -> RawTap?): RawTap? =
        runCatching(read)
            .onFailure { AppLogger.w(TAG, "toque descartado: extras ilegíveis (${it.javaClass.simpleName})") }
            .getOrNull()

    /** Decide e entrega. Testável sem `Intent`. */
    internal fun accept(raw: RawTap, key: ByteArray?): Boolean {
        if (raw.fromHistory || raw.id == Int.MIN_VALUE) return false
        if (key == null || !NotificationTapProof.verify(key, raw.id, raw.data, raw.proof)) {
            AppLogger.w(TAG, "toque descartado: sem prova de origem válida")
            return false
        }
        NotificationTaps.deliver(raw.id, raw.data)
        AppLogger.d(TAG, "toque na notificação id=${raw.id}")
        return true
    }

    fun register(context: Context) {
        if (registered) return
        val app = context.applicationContext as? Application ?: return
        appContext = app
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
 * inofensivo (a marca sai do `Intent` na primeira leitura). Desde a 2.357.1 só entrega toque com a
 * prova de origem que a lib grava no `PendingIntent` (`Intent` forjado por outro app é descartado) e
 * nunca lança, nem com `Bundle` ilegível.
 *
 * @return `true` se [intent] trazia um toque da lib com prova válida (e ele foi entregue).
 */
fun handleNotificationTapIntent(intent: Intent?): Boolean = NotificationTapIntents.handle(intent)

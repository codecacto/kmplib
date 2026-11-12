package br.com.codecacto.kmplib.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.update
import kotlin.concurrent.Volatile

/**
 * Um toque no **corpo** de uma notificação local agendada pela kmplib ([NotificationScheduler]) — 2.357.0.
 *
 * @property notificationId o `id` usado no agendamento (no lembrete rolante/mensal: `idBase + i`).
 * @property data o `data` do agendamento — inclusive `"kmplib.date"` (AAAA-MM-DD) do
 *   `scheduleRollingDaily`/`scheduleMonthly`. As chaves internas da lib não vêm.
 *   **Trate como entrada NÃO confiável** (2.357.1): valide antes de navegar — formato da data, id
 *   que existe no banco do app, rota de uma lista fechada (allowlist); **nunca** use um valor como
 *   URL, caminho de arquivo ou rota livre. A lib prova a origem no Android, mas o `data` é o que o
 *   app gravou no agendamento, e o app pode ter gravado algo que mudou (registro apagado, versão
 *   nova com outro formato).
 * @property sequence número crescente no processo: dois toques na mesma notificação são dois eventos.
 */
data class NotificationTap(
    val notificationId: Int,
    val data: Map<String, String>,
    val sequence: Long,
) {
    /** A data do disparo de `scheduleRollingDaily`/`scheduleMonthly` (`"kmplib.date"`), se houver. */
    val scheduledDate: String? get() = data[NotificationTaps.DATE_KEY]

    // Sem o conteúdo de `data` (pode ter id de registro do app): só as chaves.
    override fun toString(): String = "NotificationTap(id=$notificationId, chaves=${data.keys}, seq=$sequence)"
}

/**
 * **Toque no corpo da notificação local — a porta única** (2.357.0), app frio e app quente, Android e iOS.
 *
 * Até a 2.356.0 não havia API: no Android o app lia à mão o extra do `Intent` da `Activity` (e perdia o
 * toque que chegava pelo `onNewIntent`, com o app aberto); no iOS o toque virava um evento
 * `NotificationActions` com `actionId` padrão, só para notificação ainda no registro da lib (o
 * `showNotificationNow` e o disparo único já vencido se perdiam).
 *
 * Mesmo desenho do `IncomingLinks`: a plataforma **entrega** ([deliver]) e o toque fica em [pending]
 * até o app **consumir** ([consume]) — é o que cobre o app frio, em que o toque chega antes de existir
 * tela para navegar. Toque novo antes de consumir o anterior substitui o anterior (vale o último).
 *
 * ```kotlin
 * // Na tela raiz, DEPOIS de a navegação existir. O `data` é entrada: valide antes de navegar.
 * NotificationTapEffect { tap ->
 *     val dia = tap.scheduledDate
 *         ?.let { runCatching { LocalDate.parse(it) }.getOrNull() } // só AAAA-MM-DD válido
 *         ?: return@NotificationTapEffect
 *     navController.navigate(Rota.Dia(dia.toString()))              // rota fechada, nunca URL do data
 * }
 * ```
 *
 * **Android:** nada no app — o `initKmpLibPlatform` passa a observar a `Activity` de abertura
 * (`onCreate` e `onNewIntent`, via `ActivityLifecycleCallbacks` + `OnNewIntentProvider`) e entrega o toque;
 * reabrir pelos recentes e girar a tela não entregam de novo. Desde a 2.357.1 o toque só é entregue
 * com a **prova de origem** (HMAC com chave local por instalação) que a lib grava no `PendingIntent`:
 * a `Activity` de abertura é exportada, e um `Intent` com os mesmos extras vindo de outro app é
 * descartado. Notificação que já estava na bandeja antes do update não tem a prova (o toque abre o
 * app, mas não vira [NotificationTap]). O app que abre outra `Activity` pelo
 * toque pode chamar `handleNotificationTapIntent(intent)` (androidMain). Os extras antigos
 * (`notification_id` e cada chave do `data`) continuam no `Intent`: quem lia à mão segue funcionando —
 * mas esses extras soltos **não têm prova** e qualquer app pode forjá-los; migre para esta API.
 *
 * **iOS:** o delegate da lib ([installNotificationActionDelegate]) entrega sozinho. App com delegate
 * próprio (push) passa o `userInfo` ao bridge — `NotificationActionBridge.onNotificationResponse(
 * requestIdentifier:actionIdentifier:userInfo:)`. O delegate tem de estar instalado antes de o
 * `didFinishLaunching` terminar, senão o toque que abriu o app frio não chega (regra da Apple).
 *
 * O evento `NotificationActions` com `actionId` padrão continua sendo enviado no iOS, como antes.
 */
object NotificationTaps {

    /** Chave da data do disparo nos lembretes rolante e mensal. */
    const val DATE_KEY: String = "kmplib.date"

    /** Marca interna das notificações da lib (iOS `userInfo`, Android extra). Nunca chega ao app. */
    internal const val MARKER_KEY: String = "kmplib.notification"

    private val _pending = MutableStateFlow<NotificationTap?>(null)

    /** O último toque ainda não consumido; `null` = nenhum. */
    val pending: StateFlow<NotificationTap?> = _pending.asStateFlow()

    @Volatile
    private var counter: Long = 0

    /**
     * Entrega um toque — chamado pela plataforma da lib (e, no iOS, pelo bridge que o delegate do app
     * chama). Tira as chaves internas da lib de [data].
     */
    fun deliver(notificationId: Int, data: Map<String, String>): NotificationTap {
        val tap = NotificationTap(notificationId, NotificationTapRules.publicData(data), nextSequence())
        _pending.value = tap
        return tap
    }

    /** Marca [tap] como tratado. Toque mais novo que chegou nesse meio-tempo continua pendente. */
    fun consume(tap: NotificationTap) {
        _pending.update { current -> if (current?.sequence == tap.sequence) null else current }
    }

    /** Esquece o toque pendente (logout, teste). */
    fun clear() {
        _pending.value = null
    }

    private fun nextSequence(): Long {
        counter += 1
        return counter
    }
}

/**
 * Trata cada toque no corpo de notificação local da lib — no app frio (o toque que abriu o app) e no
 * quente — e o consome (2.357.0). Ponha na tela raiz, onde a navegação já existe.
 */
@Composable
fun NotificationTapEffect(onTap: (NotificationTap) -> Unit) {
    val current by rememberUpdatedState(onTap)
    LaunchedEffect(Unit) {
        NotificationTaps.pending.filterNotNull().collect { tap ->
            NotificationTaps.consume(tap)
            current(tap)
        }
    }
}

/** Regras puras do toque (testadas em `commonTest`). */
internal object NotificationTapRules {

    /** O `data` que o app vê: sem as chaves internas da lib. */
    fun publicData(data: Map<String, String>): Map<String, String> = data.filterKeys { it != NotificationTaps.MARKER_KEY }

    /**
     * `userInfo` do iOS → `data`: só pares String/String (o que a lib grava). `null` quando não é
     * notificação da kmplib (sem a marca) — aí o toque é do app (push, outra lib).
     */
    fun dataFromUserInfo(userInfo: Map<Any?, *>): Map<String, String>? {
        if (userInfo[NotificationTaps.MARKER_KEY] == null) return null
        return buildMap {
            userInfo.forEach { (k, v) -> if (k is String && v is String && k != NotificationTaps.MARKER_KEY) put(k, v) }
        }
    }
}

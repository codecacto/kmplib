package br.com.codecacto.kmplib.push

import com.mmk.kmpnotifier.notification.NotifierManager
import com.mmk.kmpnotifier.notification.PayloadData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * KMPNotifier-backed push service.
 *
 * Apps remain responsible for calling NotifierManager.initialize(...) in each
 * platform entry point and for handling token sync, topics and deep links.
 *
 * Desde a 2.314.0 o [listener] é registrado no hub do processo ([PushEvents]) — que recebe do
 * KMPNotifier por um único repasse ([attachKmpNotifier]) — em vez de um `NotifierManager.Listener`
 * por instância (o KMPNotifier não tem como remover listener: cada instância vazava um). Vários
 * serviços coexistem; o MESMO ouvinte em dois serviços recebe cada evento uma vez; [release] tira o
 * ouvinte; e o toque que chegou antes do serviço existir (app aberto pelo push) é entregue a ele.
 */
class KmpPushNotificationService(
    private val listener: PushNotificationListener = NoOpPushNotificationListener,
    emitCurrentTokenOnStart: Boolean = false,
) : PushNotificationService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val registration: PushListenerRegistration

    init {
        attachKmpNotifier()
        registration = PushEvents.addListener(listener)

        if (emitCurrentTokenOnStart) {
            scope.launch {
                getToken()?.let(listener::onNewToken)
            }
        }
    }

    override suspend fun getToken(): String? = runCatching {
        NotifierManager.getPushNotifier().getToken()
    }.getOrNull()

    override suspend fun deleteToken(): Result<Unit> = runCatching {
        NotifierManager.getPushNotifier().deleteMyToken()
    }

    override suspend fun subscribeToTopic(topic: String): Result<Unit> = runCatching {
        NotifierManager.getPushNotifier().subscribeToTopic(topic)
    }

    override suspend fun unsubscribeFromTopic(topic: String): Result<Unit> = runCatching {
        NotifierManager.getPushNotifier().unSubscribeFromTopic(topic)
    }

    override fun release() = registration.remove()
}

@OptIn(ExperimentalAtomicApi::class)
private val kmpNotifierAttached = AtomicBoolean(false)

/**
 * Liga o KMPNotifier ao hub [PushEvents] — UM repasse por processo, idempotente (2.314.0).
 *
 * O `KmpPushNotificationService` chama sozinho. Chame você mesmo **logo depois de
 * `NotifierManager.initialize(...)`** (no `Application.onCreate`) quando o serviço de push só nasce
 * mais tarde (depois do login, da Splash): sem o repasse instalado, o toque que abriu o app a frio
 * (entregue pelo `NotifierManager.onCreateOrOnNewIntent` da Activity) não chega a ninguém; com ele,
 * fica pendente em [PushEvents] até um ouvinte nascer.
 */
@OptIn(ExperimentalAtomicApi::class)
fun attachKmpNotifier() {
    if (!kmpNotifierAttached.compareAndSet(false, true)) return
    NotifierManager.addListener(KmpNotifierForwarder)
}

private object KmpNotifierForwarder : NotifierManager.Listener {
    override fun onNewToken(token: String) {
        PushEvents.hub.onNewToken(token)
    }

    override fun onPushNotification(title: String?, body: String?) {
        PushEvents.hub.onPushNotification(title, body)
    }

    override fun onPayloadData(data: PayloadData) {
        PushEvents.hub.onPayloadData(data.toStringMap())
    }

    override fun onPushNotificationWithPayloadData(title: String?, body: String?, data: PayloadData) {
        PushEvents.hub.onPushNotificationWithPayloadData(title, body, data.toStringMap())
    }

    override fun onNotificationClicked(data: PayloadData) {
        PushEvents.hub.onNotificationClicked(data.toStringMap())
    }
}

private fun PayloadData.toStringMap(): Map<String, String> =
    entries.associate { (key, value) -> key to value?.toString().orEmpty() }

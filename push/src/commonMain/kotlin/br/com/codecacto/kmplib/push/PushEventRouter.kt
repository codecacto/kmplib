package br.com.codecacto.kmplib.push

/**
 * Roteamento puro de um evento de push para o [PushNotificationListener] do app.
 *
 * É o **denominador comum** entre o Android (KMPNotifier chama os callbacks do listener) e o
 * iOS APNs-direto ([ApplePushBridge], que delega aqui): mantém a mesma semântica de "toque abre
 * deep link" × "chegou em foreground" nas duas plataformas.
 *
 * - `wasTapped = true`  → **toque** na notificação (background-tap ou cold-start-tap): dispara
 *   [PushNotificationListener.onNotificationClicked] com a carga completa (o app resolve o deep link).
 * - `wasTapped = false` → notificação **recebida em foreground**: dispara
 *   [PushNotificationListener.onPushNotificationWithPayloadData] com título/corpo derivados
 *   ([PushPayload]) + a carga completa.
 *
 * commonMain puro — testável em `commonTest`.
 */
object PushEventRouter {
    fun dispatch(
        listener: PushNotificationListener,
        data: Map<String, String>,
        wasTapped: Boolean,
    ) {
        if (wasTapped) {
            listener.onNotificationClicked(data)
        } else {
            listener.onPushNotificationWithPayloadData(
                title = PushPayload.title(data),
                body = PushPayload.body(data),
                data = data,
            )
        }
    }
}

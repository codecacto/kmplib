package br.com.codecacto.kmplib.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.codecacto.kmplib.core.util.AppLogger

/**
 * Restaura os lembretes locais depois de **reiniciar o aparelho** ou **atualizar o app**.
 *
 * ### O problema que isto resolve
 * O `AlarmManager` do Android **perde todos os alarmes no boot** — é comportamento documentado da
 * plataforma, não bug. Sem um receiver de `BOOT_COMPLETED`, todo lembrete agendado por qualquer app
 * do ecossistema morria em silêncio ao reiniciar o celular e só voltava se o usuário abrisse o app.
 * Num app de dose de 12/12h, isso é a promessa central do produto falhando sem nenhum aviso.
 *
 * ### Como funciona
 * O receiver está **declarado no manifesto da própria kmplib** (junto da permissão
 * `RECEIVE_BOOT_COMPLETED`), então todo app consumidor herda a correção pelo manifest merger, sem
 * editar nada. Ao receber o broadcast, chama [NotificationScheduler.refreshScheduledNotifications],
 * que reagenda tudo o que estiver no registro persistente da lib.
 *
 * ### Ações escutadas
 * - `BOOT_COMPLETED` — o caso principal.
 * - `MY_PACKAGE_REPLACED` — atualização do app **também** cancela os alarmes pendentes.
 * - `QUICKBOOT_POWERON` / `com.htc.intent.action.QUICKBOOT_POWERON` — variantes de fabricante
 *   (HTC e derivados de MIUI/EMUI usam o "quick boot" em vez do broadcast padrão). Escutar as duas
 *   é a mitigação recomendada, e é inofensivo onde não existem.
 *
 * Não escutamos `LOCKED_BOOT_COMPLETED` de propósito: ele chega **antes** do desbloqueio, quando o
 * armazenamento de credencial do usuário (onde vive o `SharedPreferences` do registro) ainda não
 * está montado — ler ali devolveria vazio e reagendaria nada.
 *
 * ### Limite conhecido (fabricantes)
 * Camadas agressivas de economia de bateria (Xiaomi, Samsung, Huawei…) podem impedir o broadcast ou
 * matar o alarme depois. Isso não se resolve em código; a lib expõe
 * [NotificationScheduler.openBatteryOptimizationSettings] para o app orientar o usuário.
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in HANDLED_ACTIONS) {
            AppLogger.d(TAG, "Ação ignorada: $action")
            return
        }

        // O Application já foi criado antes de qualquer receiver do app, então o holder normalmente
        // está inicializado. Garantir aqui torna a restauração independente de o app ter chamado
        // `NotificationSchedulerHolder.init` (um esquecimento de setup não pode custar o lembrete).
        if (NotificationSchedulerHolder.getContext() == null) {
            NotificationSchedulerHolder.init(context.applicationContext)
        }

        // Um receiver não pode lançar: uma exceção aqui derruba o processo do app logo no boot.
        runCatching {
            AndroidNotificationScheduler().refreshScheduledNotifications()
            AppLogger.i(TAG, "Lembretes locais restaurados após $action")
        }.onFailure {
            AppLogger.e(TAG, "Falha ao restaurar lembretes após $action", it as? Exception)
        }
    }

    private companion object {
        const val TAG = "BootCompletedReceiver"

        val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
        )
    }
}

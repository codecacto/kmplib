package br.com.codecacto.kmplib.platform

import br.com.codecacto.kmplib.core.util.AppLogger
import kotlinx.datetime.Instant

/**
 * Agendador de notificações locais multiplataforma.
 *
 * Uso:
 * ```kotlin
 * val scheduler = getNotificationScheduler()
 *
 * // Verificar permissão
 * if (!scheduler.hasPermission()) {
 *     scheduler.requestPermission { granted ->
 *         if (granted) {
 *             // Agendar notificação
 *         }
 *     }
 * }
 *
 * // Agendar notificação
 * scheduler.scheduleNotification(
 *     id = 1,
 *     title = "Lembrete",
 *     body = "Hora de tomar seu medicamento",
 *     scheduledTime = Clock.System.now() + 1.hours,
 *     data = mapOf("medicationId" to "123")
 * )
 *
 * // Cancelar notificação
 * scheduler.cancelNotification(1)
 * ```
 */
interface NotificationScheduler {
    /**
     * Verifica se tem permissão para enviar notificações.
     */
    fun hasPermission(): Boolean

    /**
     * Solicita permissão para notificações.
     * @param onResult Callback com resultado (true = permitido)
     */
    fun requestPermission(onResult: (Boolean) -> Unit)

    /**
     * Agenda uma notificação.
     *
     * @param id ID único da notificação
     * @param title Título da notificação
     * @param body Corpo/descrição da notificação
     * @param scheduledTime Momento em que a notificação deve ser exibida
     * @param data Dados extras para a notificação
     * @param channelId ID do canal (Android) - usa padrão se não informado
     * @param isCritical Se true, tenta bypassar modo não perturbe
     * @param actions Botões da notificação (2.100.0). Ver [NotificationAction]; lista vazia = a
     *   notificação de sempre, só com o toque no corpo.
     */
    fun scheduleNotification(
        id: Int,
        title: String,
        body: String,
        scheduledTime: Instant,
        data: Map<String, String> = emptyMap(),
        channelId: String? = null,
        isCritical: Boolean = false,
        actions: List<NotificationAction> = emptyList()
    )

    /**
     * Agenda uma notificação local que se repete TODO dia no horário (hour:minute) local.
     *
     * Use para lembretes recorrentes (ex.: versículo diário, dose diária). O disparo ocorre
     * todos os dias no mesmo horário local até ser cancelado com [cancelNotification] (mesmo [id]).
     *
     * @param id ID único da notificação/lembrete (use o mesmo para reagendar/cancelar)
     * @param title Título da notificação
     * @param body Corpo/descrição da notificação
     * @param hour Hora do disparo (0..23, horário local)
     * @param minute Minuto do disparo (0..59, horário local)
     * @param data Dados extras para a notificação
     * @param channelId ID do canal (Android) - usa padrão se não informado
     * @param isCritical Se true, tenta bypassar modo não perturbe
     * @param actions Botões da notificação (2.100.0). Um botão de
     *   [NotificationActionKind.SNOOZE] adia **só o disparo de hoje**: a recorrência diária continua
     *   valendo para os próximos dias.
     *
     * Android: o disparo usa `setExactAndAllowWhileIdle` com reagendamento do próximo dia dentro do
     * receiver, e o agendamento é **persistido** pela lib — o `BootCompletedReceiver` restaura tudo
     * depois de reiniciar o aparelho ou atualizar o app (desde a 2.99.0). Ver
     * [refreshScheduledNotifications].
     */
    fun scheduleDailyNotification(
        id: Int,
        title: String,
        body: String,
        hour: Int,
        minute: Int,
        data: Map<String, String> = emptyMap(),
        channelId: String? = null,
        isCritical: Boolean = false,
        actions: List<NotificationAction> = emptyList()
    )

    /**
     * Agenda uma notificação local que se repete TODA semana em [weekday], no horário `hour:minute`
     * (2.125.0).
     *
     * Use para lembrete preso a um **compromisso semanal** — culto, aula, treino, feira, coleta do
     * lixo reciclável. É diferente de [scheduleDailyNotification] em mais do que a frequência:
     * "todo domingo às 18:30" agendado como diário avisaria a pessoa **seis vezes por semana** fora
     * de hora, e agendado como disparo único ([scheduleNotification]) valeria **uma vez só** — o
     * lembrete morreria em silêncio na semana seguinte se o app não fosse aberto.
     *
     * @param id ID único do lembrete (use o mesmo para reagendar/cancelar). Um mesmo compromisso com
     *   vários horários na semana ocupa **um id por horário**.
     * @param weekday Dia da semana, **1 = segunda … 7 = domingo** (ISO-8601 — o mesmo número de
     *   `kotlinx.datetime.DayOfWeek.isoDayNumber`). Fora da faixa é ajustado para dentro dela.
     * @param hour Hora do disparo (0..23)
     * @param minute Minuto do disparo (0..59)
     * @param timeZoneId Fuso IANA (ex.: `"America/Cuiaba"`) em que `hour:minute` deve ser lido;
     *   `null` = o do aparelho. Informe quando o compromisso for de um **lugar** e não da pessoa: o
     *   culto de domingo às 19:00 acontece às 19:00 na cidade da igreja, e quem viajou continua
     *   querendo o aviso a tempo. Fuso desconhecido cai no do aparelho, com log — nunca deixa de
     *   agendar.
     * @param data Dados extras para a notificação
     * @param channelId ID do canal (Android) — usa o padrão se não informado
     * @param isCritical Se true, tenta bypassar modo não perturbe
     * @param actions Botões da notificação. Um [NotificationActionKind.SNOOZE] adia **só o disparo
     *   desta semana**; a recorrência semanal continua valendo.
     *
     * **Android:** `AlarmManager` com reagendamento da semana seguinte dentro do receiver, e o
     * agendamento é **persistido** — o `BootCompletedReceiver` o restaura depois do reboot ou da
     * atualização do app. **iOS:** `UNCalendarNotificationTrigger` com `weekday/hour/minute` e
     * `repeats = true` (o sistema repete sozinho, com o app fechado).
     *
     * Cancela com [cancelNotification] (mesmo [id]).
     *
     * O corpo default existe só para não quebrar `NotificationScheduler` escrito à mão (fake de
     * teste, decorator): as implementações reais de Android e iOS sobrescrevem.
     */
    fun scheduleWeeklyNotification(
        id: Int,
        title: String,
        body: String,
        weekday: Int,
        hour: Int,
        minute: Int,
        timeZoneId: String? = null,
        data: Map<String, String> = emptyMap(),
        channelId: String? = null,
        isCritical: Boolean = false,
        actions: List<NotificationAction> = emptyList()
    ) {
        AppLogger.w(
            "NotificationScheduler",
            "scheduleWeeklyNotification não implementado nesta instância (id=$id) — nada agendado",
        )
    }

    /**
     * Cancela uma notificação agendada (única ou recorrente — diária ou semanal).
     * @param id ID da notificação a cancelar
     */
    fun cancelNotification(id: Int)

    /**
     * Cancela todas as notificações agendadas.
     */
    fun cancelAllNotifications()

    /**
     * Exibe uma notificação imediatamente.
     *
     * @param actions Botões da notificação (2.100.0) — é o que faz um disparo **perdido**, exibido
     *   na volta do reboot, chegar com os mesmos botões do disparo normal.
     */
    fun showNotificationNow(
        id: Int,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
        channelId: String? = null,
        actions: List<NotificationAction> = emptyList()
    )

    /**
     * Uma notificação **FIXA** — a que não se dispensa deslizando (2.144.0).
     *
     * É a faixa que os apps de entrega mantêm na bandeja enquanto o pedido está a caminho: ela
     * substitui a si mesma a cada atualização (mesmo [id]) e só sai quando o app chama
     * [cancelNotification]. Foi pedida assim, nominalmente: *"eu queria que tivesse a notificação
     * fixa, igual tenho no iFood"* (Cidade Conectada, 25/ago/2026).
     *
     * ## Por que não é o [showNotificationNow] com um parâmetro a mais
     * As duas têm ciclos de vida opostos. A comum é um AVISO: aparece, a pessoa toca ou desliza, e
     * acabou (`autoCancel`). Esta é um ESTADO: enquanto durar, ela tem de estar lá — inclusive
     * depois de a pessoa ter tocado nela e voltado. Um booleano numa função só faria o chamador
     * escolher entre dois comportamentos que não se parecem, e o `autoCancel` errado é o tipo de
     * default que só se descobre com o pedido do cliente já na rua.
     *
     * ## O que ela NÃO faz sozinha
     * Não sobrevive ao app ser encerrado pelo sistema com a informação **atualizada**: quem a
     * mantém em dia é o app (a cada leitura do estado) ou um push. Sem nenhum dos dois, ela congela
     * no último texto — que continua sendo melhor que sumir, mas não é acompanhamento em tempo real.
     *
     * No iOS não existe equivalente direto: a bandeja não tem notificação persistente, e o que se
     * aproxima disso é a Live Activity (ActivityKit), que é outra API e outra entrega. Lá esta
     * função exibe uma notificação comum — melhor que silêncio, e declarado.
     *
     * @param id o MESMO em toda atualização: é o que faz a faixa se substituir em vez de empilhar.
     */
    fun showOngoingNotification(
        id: Int,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
        channelId: String? = null,
        actions: List<NotificationAction> = emptyList(),
    ) {
        // Default conservador: quem não implementou cai na notificação comum, que ao menos avisa.
        showNotificationNow(id, title, body, data, channelId, actions)
    }

    /**
     * Adia um agendamento **existente** em [minutes] minutos, a partir de agora (2.100.0).
     *
     * É o mesmo caminho que o botão de [NotificationActionKind.SNOOZE] executa — exposto aqui para o
     * app oferecer "Adiar" **dentro** da tela (bottom sheet de dose, por exemplo) sem reinventar um
     * agendamento único paralelo, que é como os apps vinham fazendo (e que duplicava lembrete e
     * inventava id novo).
     *
     * - Não duplica: o adiamento desloca o disparo do MESMO [id].
     * - Não mata a recorrência: num lembrete diário, o horário regular continua valendo.
     * - Adiar de novo simplesmente move o mesmo disparo mais para a frente.
     * - [id] desconhecido no registro ⇒ no-op com log de aviso (nada é inventado).
     */
    fun snoozeNotification(id: Int, minutes: Int) {}

    // -----------------------------------------------------------------------------------------
    // Sobrevivência a reboot / atualização (2.99.0)
    //
    // Métodos ADITIVOS, todos com corpo default: um `NotificationScheduler` implementado à mão pelo
    // app (fake de teste, decorator) continua compilando sem tocar em nada.
    // -----------------------------------------------------------------------------------------

    /**
     * Agendamentos que a lib tem registrados (o espelho persistente, não o estado do SO).
     *
     * Útil para diagnóstico ("o que está agendado?") e para o app conferir depois de um boot.
     */
    fun scheduledNotifications(): List<ScheduledNotification> = emptyList()

    /**
     * Reconcilia o registro persistente da lib com o sistema operacional.
     *
     * **Android:** reagenda no `AlarmManager` tudo o que estiver registrado (o `AlarmManager` é
     * zerado no boot), exibe os disparos perdidos que ainda estejam dentro da janela de graça
     * ([NotificationRescheduling.DEFAULT_MISSED_GRACE_MILLIS]) e descarta os únicos já vencidos.
     * Chamado automaticamente pelo `BootCompletedReceiver` no boot e na atualização do app.
     *
     * **iOS:** o `UNUserNotificationCenter` persiste as notificações agendadas sozinho, então aqui a
     * função serve ao **teto de 64 pendentes por app**: reabastece a fila com os próximos disparos
     * que ainda não estavam registrados.
     *
     * É **idempotente** — chamar de novo não duplica nada. Chamar na abertura do app é barato e
     * cobre o caso do usuário que desligou o aparelho por dias.
     */
    fun refreshScheduledNotifications() {}

    /**
     * Android 12+ (API 31): o app pode agendar **alarmes exatos**?
     *
     * Quando `false`, a lib continua agendando (com `setAndAllowWhileIdle`), mas o disparo pode
     * atrasar minutos — o SO agrupa alarmes inexatos para poupar bateria. Para lembrete de dose isso
     * costuma ser aceitável; para alarme de despertar, não. Nas demais plataformas/versões devolve
     * `true`.
     */
    fun canScheduleExactAlarms(): Boolean = true

    /**
     * Abre a tela do sistema onde o usuário concede "alarmes e lembretes" (Android 12+,
     * `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`). No-op nas demais plataformas.
     *
     * Chamar **só depois** de explicar o porquê na UI: é uma tela de Configurações, e mandar o
     * usuário para lá sem contexto é o caminho mais curto para ele voltar sem conceder.
     */
    fun requestExactAlarmPermission() {}

    /**
     * Abre a tela de **otimização de bateria** do sistema (Android).
     *
     * Existe por causa das camadas agressivas de fabricante (Xiaomi, Samsung, Huawei…), que matam
     * alarmes de apps "não usados recentemente" — a causa nº 1 de "meu lembrete não tocou" em
     * Android, independente do código estar certo. Abre a **lista geral** de otimização
     * (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`), que não exige permissão restrita e não põe em
     * risco a revisão na Play, ao contrário de pedir a isenção direto.
     */
    fun openBatteryOptimizationSettings() {}
}

/**
 * Obtém a implementação do NotificationScheduler para a plataforma atual.
 */
expect fun getNotificationScheduler(): NotificationScheduler

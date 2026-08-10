package br.com.codecacto.kmplib.platform

import kotlinx.serialization.Serializable

/**
 * O que a lib faz quando o usuário toca no botão de uma notificação.
 *
 * Só existem dois comportamentos porque só existem duas responsabilidades: **adiar** é mecânica de
 * agendamento (a lib sabe fazer sozinha, em qualquer app) e **regra de domínio** é do app (só ele
 * sabe o que "marcar como tomada" significa no banco dele).
 */
@Serializable
enum class NotificationActionKind {
    /**
     * **Adiar (snooze) — executado pela própria lib.** Reagenda o MESMO conteúdo daqui a
     * [NotificationAction.snoozeMinutes] minutos, sem uma linha de código de plataforma no app.
     *
     * O adiamento **não cria** um agendamento novo: ele desloca o disparo do mesmo `id` (ver
     * [ScheduledNotification.snoozedUntilMillis]), então adiar duas vezes não gera duas notificações,
     * e adiar um lembrete **diário** não mata a recorrência — depois do disparo adiado, o lembrete
     * volta ao horário normal.
     */
    SNOOZE,

    /**
     * **Ação do app.** A lib dispensa a notificação e entrega o par
     * (`notificationId`, `actionId`, `data`) ao [NotificationActionHandler] registrado, para o app
     * aplicar a sua regra (gravar a dose no SQLDelight, marcar tarefa concluída…).
     *
     * Funciona com o app em **background ou morto**: no Android quem recebe é um `BroadcastReceiver`
     * da lib (o processo sobe, `Application.onCreate` roda e só então o evento é entregue), e no iOS
     * o sistema acorda o app em background para chamar o delegate. Por isso o handler é registrado no
     * **`Application`/bootstrap**, nunca numa `Activity`/tela.
     */
    APP,
}

/**
 * Um botão de ação de notificação local — multiplataforma.
 *
 * ```kotlin
 * scheduler.scheduleDailyNotification(
 *     id = 42, title = "Nitazoxanida", body = "1 comprimido — 08:00", hour = 8, minute = 0,
 *     data = mapOf("doseId" to "d-17"),
 *     actions = listOf(
 *         NotificationAction.app(id = "MARK_TAKEN", title = "Marcar como tomada"),
 *         NotificationAction.snooze(minutes = 30, title = "Adiar 30 min"),
 *     ),
 * )
 * ```
 *
 * ### Quantos botões cabem
 * O Android exibe **até 3** ações (`NotificationCompat` aceita mais, o sistema mostra 3); o iOS
 * exibe **até 4** na notificação expandida. A lib **não corta** a sua lista — se você declarar 6
 * intervalos de adiamento, os últimos simplesmente não aparecem. Declare no máximo
 * [MAX_PORTABLE_ACTIONS] se quiser o mesmo resultado nas duas plataformas.
 *
 * ### Ícone
 * Nenhuma das duas plataformas mostra ícone de ação hoje (o Android parou no 7.0, o iOS nunca
 * mostrou), então a API não pede um — o rótulo é o que o usuário lê.
 *
 * @property id identificador **estável** da ação, único dentro da notificação. É o que chega ao
 *   [NotificationActionHandler] — trate como chave de contrato, não como texto: mudar o `id` entre
 *   versões do app quebra a ação de uma notificação já agendada no aparelho.
 * @property title rótulo do botão, **já traduzido pelo app** (a lib não faz i18n de texto de app).
 * @property kind ver [NotificationActionKind].
 * @property snoozeMinutes minutos de adiamento — só vale em [NotificationActionKind.SNOOZE].
 * @property opensApp abre o app ao tocar (Android: `Intent` de launcher; iOS:
 *   `UNNotificationActionOptionForeground`). Deixe `false` quando a graça da ação é justamente
 *   **não** abrir o app — é o caso de "marcar como tomada".
 * @property destructive pinta a ação como destrutiva no iOS
 *   (`UNNotificationActionOptionDestructive`, texto vermelho). Sem efeito no Android, que não tem o
 *   conceito.
 */
@Serializable
data class NotificationAction(
    val id: String,
    val title: String,
    val kind: NotificationActionKind = NotificationActionKind.APP,
    val snoozeMinutes: Int = 0,
    val opensApp: Boolean = false,
    val destructive: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "NotificationAction.id não pode ser vazio" }
        require(title.isNotBlank()) { "NotificationAction.title não pode ser vazio" }
        require(kind != NotificationActionKind.SNOOZE || snoozeMinutes > 0) {
            "Ação de adiar exige snoozeMinutes > 0 (recebido: $snoozeMinutes)"
        }
    }

    /** `true` quando o adiamento é executado pela lib. */
    val isSnooze: Boolean get() = kind == NotificationActionKind.SNOOZE

    companion object {
        /** Prefixo dos ids gerados por [snooze] — reservado à lib. */
        const val SNOOZE_ID_PREFIX: String = "kmplib.snooze."

        /** Teto seguro nas DUAS plataformas (Android mostra 3, iOS mostra 4). */
        const val MAX_PORTABLE_ACTIONS: Int = 3

        /** Maior adiamento aceito: 7 dias. Acima disso não é "adiar", é reagendar. */
        const val MAX_SNOOZE_MINUTES: Int = 7 * 24 * 60

        /**
         * Botão de **adiar**, executado pela lib.
         *
         * @param minutes intervalo do adiamento (1..[MAX_SNOOZE_MINUTES]).
         * @param title rótulo já traduzido (ex.: `"Adiar 30 min"`).
         */
        fun snooze(
            minutes: Int,
            title: String,
            id: String = "$SNOOZE_ID_PREFIX$minutes",
        ): NotificationAction = NotificationAction(
            id = id,
            title = title,
            kind = NotificationActionKind.SNOOZE,
            snoozeMinutes = minutes.coerceIn(1, MAX_SNOOZE_MINUTES),
        )

        /**
         * Vários botões de adiar de uma vez — o formato em que o app declara os intervalos.
         *
         * ```kotlin
         * NotificationAction.snoozeOptions(listOf(15, 30, 60)) { "Adiar $it min" }
         * ```
         */
        fun snoozeOptions(
            minutes: List<Int>,
            title: (Int) -> String,
        ): List<NotificationAction> = minutes.map { snooze(minutes = it, title = title(it)) }

        /**
         * Botão de **ação do app**: o evento chega ao [NotificationActionHandler] e o app decide o
         * que fazer. A notificação é dispensada pela lib.
         */
        fun app(
            id: String,
            title: String,
            opensApp: Boolean = false,
            destructive: Boolean = false,
        ): NotificationAction = NotificationAction(
            id = id,
            title = title,
            kind = NotificationActionKind.APP,
            opensApp = opensApp,
            destructive = destructive,
        )
    }
}

/**
 * O que o app recebe quando o usuário toca num botão de
 * [NotificationActionKind.APP] — ou no **corpo** da notificação, se ele quiser tratar isso também
 * (`actionId` == [NotificationActionEvent.DEFAULT_ACTION_ID]).
 *
 * @property notificationId o mesmo `id` usado ao agendar.
 * @property actionId o [NotificationAction.id] tocado.
 * @property data o `data` que o app passou no agendamento — é por onde trafega o id de domínio
 *   (`"doseId"`, `"medicamentoId"`…). A lib nunca inventa nem interpreta esse conteúdo.
 */
data class NotificationActionEvent(
    val notificationId: Int,
    val actionId: String,
    val data: Map<String, String> = emptyMap(),
) {
    companion object {
        /**
         * `actionId` sintético do toque no **corpo** da notificação (não num botão).
         *
         * No iOS corresponde ao `UNNotificationDefaultActionIdentifier`. No Android o toque no corpo
         * abre o app pelo `contentIntent` de sempre e **não** gera evento — o comportamento
         * histórico não mudou.
         */
        const val DEFAULT_ACTION_ID: String = "kmplib.default"
    }
}

/**
 * Executa a regra de domínio de uma ação de notificação.
 *
 * É `suspend` de propósito: o trabalho típico é escrever no banco local (SQLDelight/DataStore), e no
 * Android o receiver da lib segura o broadcast com `goAsync()` enquanto o handler roda — o app não
 * precisa de `runBlocking` nem de um `Service` só para gravar uma linha.
 *
 * **Seja rápido.** O Android concede alguns segundos ao receiver; a lib corta em
 * [NotificationActions.HANDLER_TIMEOUT_MILLIS]. Grave o fato e devolva; sincronização com o servidor
 * é trabalho do sync da próxima abertura.
 */
fun interface NotificationActionHandler {
    suspend fun onNotificationAction(event: NotificationActionEvent)
}

/**
 * Regras **puras** das ações — sem `Context`, sem `UNUserNotificationCenter`, sem relógio implícito.
 * É o que Android e iOS compartilham (e o que a suíte testa).
 */
object NotificationActionRules {

    /** Acha a ação [actionId] declarada em [item], ou `null` se ela não existir mais. */
    fun actionOf(item: ScheduledNotification, actionId: String): NotificationAction? =
        item.actions.firstOrNull { it.id == actionId }

    /**
     * Aplica um adiamento de [minutes] a partir de [nowMillis].
     *
     * O agendamento **não é duplicado**: só o campo [ScheduledNotification.snoozedUntilMillis] muda,
     * então o `id` continua sendo um só, o registro persistente continua sendo um só e o horário
     * original (o `hour`/`minute` do lembrete diário) fica preservado para depois.
     */
    fun applySnooze(
        item: ScheduledNotification,
        minutes: Int,
        nowMillis: Long,
    ): ScheduledNotification = item.copy(
        snoozedUntilMillis = nowMillis +
            minutes.coerceIn(1, NotificationAction.MAX_SNOOZE_MINUTES) * 60_000L,
    )

    /** Desfaz o adiamento (o disparo adiado aconteceu, ou o lembrete foi reagendado). */
    fun clearSnooze(item: ScheduledNotification): ScheduledNotification =
        if (item.snoozedUntilMillis == 0L) item else item.copy(snoozedUntilMillis = 0L)

    /**
     * Identificador de **categoria** (iOS) derivado do conjunto de ações.
     *
     * O `UNUserNotificationCenter` não aceita ações por notificação: elas vivem numa
     * `UNNotificationCategory` registrada no centro, e a notificação só aponta para a categoria pelo
     * identificador. Derivando o identificador do **conteúdo** das ações, dois agendamentos com o
     * mesmo conjunto de botões compartilham uma categoria (não estouram o registro) e um conjunto
     * diferente ganha categoria própria automaticamente — sem o app inventar nomes.
     *
     * Determinístico entre execuções (hash FNV-1a de 32 bits sobre os campos, não `hashCode()` de
     * objeto). Lista vazia ⇒ `""` (notificação sem categoria).
     */
    fun categoryIdentifier(actions: List<NotificationAction>): String {
        if (actions.isEmpty()) return ""
        var hash = FNV_OFFSET_BASIS
        actions.forEach { action ->
            val seed = listOf(
                action.id,
                action.title,
                action.kind.name,
                action.snoozeMinutes.toString(),
                action.opensApp.toString(),
                action.destructive.toString(),
            ).joinToString(separator = FIELD_SEPARATOR, postfix = FIELD_SEPARATOR)
            seed.forEach { char ->
                hash = (hash xor (char.code.toLong() and 0xFFFF)) * FNV_PRIME and 0xFFFFFFFFL
            }
        }
        return CATEGORY_PREFIX + hash.toString(16).padStart(8, '0')
    }

    /**
     * Remove ações de `id` repetido, preservando a primeira ocorrência.
     *
     * Duas ações com o mesmo `id` são ambíguas por construção: a lib não teria como saber qual delas
     * o usuário tocou (o `id` é a única informação que volta do sistema).
     */
    fun distinctActions(actions: List<NotificationAction>): List<NotificationAction> =
        if (actions.size < 2) actions else actions.distinctBy { it.id }

    private const val CATEGORY_PREFIX = "kmplib.act."

    /** Separador improvavel em id/rotulo — evita colisao de hash por concatenacao. */
    private const val FIELD_SEPARATOR = "\u0001"
    private const val FNV_OFFSET_BASIS = 2166136261L
    private const val FNV_PRIME = 16777619L
}

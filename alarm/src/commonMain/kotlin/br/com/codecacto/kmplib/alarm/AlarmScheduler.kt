package br.com.codecacto.kmplib.alarm

/**
 * # Alarme que acorda o aparelho — 2.308.0
 *
 * Para o horário que **não pode passar**: o início da ronda do vigia, o lembrete de ponto.
 *
 * | | Android | iOS |
 * |---|---|---|
 * | [schedulePatrolStart] | `AlarmManager.setAlarmClock` (acorda em Doze, mostra o ícone de despertador) | AlarmKit (iOS 26+, ponte Swift — [AlarmKitBridge]) ou notificação **Time Sensitive** |
 * | [scheduleReminder] | `setExactAndAllowWhileIdle` | notificação **Time Sensitive** |
 * | sem alarme exato | `setWindow` (janela ≥ 10 min) → [AlarmResult.FallbackWindow] | — |
 *
 * O disparo vira **notificação** com o `label` como título; tocar abre o app (Android: extra
 * [ALARM_ID_EXTRA] no intent de abertura; iOS: `userInfo["alarmId"]`). Os alarmes agendados
 * sobrevivem a reboot e a atualização do app (Android reagenda; iOS o sistema guarda).
 *
 * ### O que fica fora, de propósito
 * - **Doze limita `setExactAndAllowWhileIdle` a um disparo a cada ~9 min por app** (em Doze profundo).
 *   Lembretes mais próximos que [MIN_REMINDER_SPACING_MILLIS] podem atrasar até a próxima janela —
 *   espace-os. O início de ronda (`setAlarmClock`) não tem esse limite.
 * - **Plano B por push (FCM de alta prioridade)** para quando o aparelho estiver com o app parado à
 *   força: não é desta lib — é o backend que manda o push no horário. Recomendado para ronda crítica.
 * - Tela cheia por cima do bloqueio (`USE_FULL_SCREEN_INTENT`): restrita na Play a despertador e
 *   chamada; não é declarada.
 *
 * Android: chame `initKmpLibAlarm(context)` no `Application.onCreate()` (ou ao menos
 * `initKmpLibCore`). iOS: entitlement `com.apple.developer.usernotifications.time-sensitive` no app
 * (sem ele a notificação sai como ativa comum e o Foco a silencia) e, para AlarmKit,
 * `NSAlarmKitUsageDescription` no Info.plist + a ponte registrada.
 */
interface AlarmScheduler {

    /** Agenda o alarme de início de ronda [alarmId] para [triggerAtMillis] (epoch, ms). Reagendar o mesmo id substitui. */
    fun schedulePatrolStart(alarmId: Int, triggerAtMillis: Long, label: String): AlarmResult

    /** Agenda um lembrete (espaçamento ≥ 9 min entre lembretes para sobreviver ao Doze). Reagendar o mesmo id substitui. */
    fun scheduleReminder(alarmId: Int, triggerAtMillis: Long, label: String): AlarmResult

    /** Cancela o alarme [alarmId] (agendado ou já exibido). */
    fun cancel(alarmId: Int)

    /** Cancela todos os alarmes agendados por esta lib. */
    fun cancelAll()

    /**
     * Alarme exato permitido? Android 12+: `AlarmManager.canScheduleExactAlarms()` (nasce `false` em
     * instalação nova no Android 14+, salvo com `USE_EXACT_ALARM`); abaixo do 12, `true`. iOS: a
     * notificação não foi negada (última leitura das configurações).
     */
    fun hasExactAlarmPermission(): Boolean

    /**
     * Abre a tela do sistema para a pessoa conceder — Android 12+: "Alarmes e lembretes" do app
     * (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`); iOS: a página do app nos Ajustes.
     * @return `true` se alguma tela abriu.
     */
    fun openPermissionSettings(): Boolean
}

/** O resultado de um agendamento (2.308.0). */
sealed class AlarmResult {
    /** Agendado com o mecanismo exato (Android `setAlarmClock`/`setExactAndAllowWhileIdle`; iOS AlarmKit ou Time Sensitive). */
    data object Scheduled : AlarmResult()

    /**
     * Sem a permissão que o alarme exige: Android sem `SCHEDULE_EXACT_ALARM` no **início de ronda**
     * (o alarme foi agendado mesmo assim, em janela inexata — mas o despertador não está garantido) ou
     * com as notificações do app desligadas; iOS com notificações negadas (nada é agendado — não
     * apareceria). Leve a pessoa a [AlarmScheduler.openPermissionSettings].
     */
    data object PermissionDenied : AlarmResult()

    /** Lembrete sem alarme exato: agendado com `setWindow` (pode atrasar até a janela). */
    data object FallbackWindow : AlarmResult()
}

/** Tipo do alarme. */
enum class AlarmKind { PATROL_START, REMINDER }

/** Como a plataforma Android vai agendar — decidido pela regra pura [planAndroidAlarm]. */
enum class AndroidAlarmStrategy { ALARM_CLOCK, EXACT_ALLOW_WHILE_IDLE, WINDOW }

/** O plano de um agendamento Android: a estratégia e o resultado devolvido ao app. */
data class AndroidAlarmPlan(val strategy: AndroidAlarmStrategy, val result: AlarmResult)

/** Espaçamento mínimo entre lembretes para o Doze não adiá-los (limite do `setExactAndAllowWhileIdle`). */
const val MIN_REMINDER_SPACING_MILLIS: Long = 9 * 60_000L

/** Janela do fallback inexato. O Android 14+ estende qualquer janela menor para 10 min. */
const val FALLBACK_WINDOW_MILLIS: Long = 10 * 60_000L

/** Extra do intent de abertura (Android) e chave do `userInfo` (iOS) com o id do alarme tocado. */
const val ALARM_ID_EXTRA: String = "br.com.codecacto.kmplib.alarm.ALARM_ID"

/**
 * A regra do Android, pura (para teste): alarme exato quando permitido; sem ele, janela inexata —
 * e o início de ronda avisa [AlarmResult.PermissionDenied] (o despertador não está garantido);
 * notificações desligadas também viram [AlarmResult.PermissionDenied].
 */
fun planAndroidAlarm(kind: AlarmKind, canScheduleExact: Boolean, notificationsEnabled: Boolean): AndroidAlarmPlan {
    val strategy = when {
        !canScheduleExact -> AndroidAlarmStrategy.WINDOW
        kind == AlarmKind.PATROL_START -> AndroidAlarmStrategy.ALARM_CLOCK
        else -> AndroidAlarmStrategy.EXACT_ALLOW_WHILE_IDLE
    }
    val result = when {
        !notificationsEnabled -> AlarmResult.PermissionDenied
        strategy != AndroidAlarmStrategy.WINDOW -> AlarmResult.Scheduled
        kind == AlarmKind.PATROL_START -> AlarmResult.PermissionDenied
        else -> AlarmResult.FallbackWindow
    }
    return AndroidAlarmPlan(strategy, result)
}

/**
 * Os lembretes [reminderTimes] (epoch, ms) estão espaçados o bastante para o Doze? Devolve os pares
 * que ficaram a menos de [MIN_REMINDER_SPACING_MILLIS] um do outro (vazio = tudo certo). Para o app
 * conferir a escala antes de agendar.
 */
fun remindersTooClose(reminderTimes: List<Long>): List<Pair<Long, Long>> =
    reminderTimes.sorted().zipWithNext().filter { (a, b) -> b - a < MIN_REMINDER_SPACING_MILLIS }

/** O agendador da plataforma. */
expect fun createAlarmScheduler(): AlarmScheduler

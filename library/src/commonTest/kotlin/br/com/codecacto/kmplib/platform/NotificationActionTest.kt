package br.com.codecacto.kmplib.platform

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Botões de ação da notificação local: o adiamento executado pela lib, a entrega da ação de domínio
 * ao app e a **persistência** disso tudo.
 *
 * Tudo aqui é puro (o "agora" entra por parâmetro), então a suíte não depende de `AlarmManager`, de
 * `UNUserNotificationCenter` nem de relógio — e as decisões testadas são exatamente as que rodam
 * dentro do `NotificationActionReceiver` no Android e do `NotificationActionBridge` no iOS.
 */
class NotificationActionTest {

    private fun millis(iso: String): Long = Instant.parse(iso).toEpochMilliseconds()

    private val marcarTomada = NotificationAction.app(id = "MARK_TAKEN", title = "Marcar como tomada")
    private val adiar30 = NotificationAction.snooze(minutes = 30, title = "Adiar 30 min")

    private fun dose(
        id: Int = 1,
        kind: NotificationScheduleKind = NotificationScheduleKind.DAILY,
        triggerAt: String = "2026-08-10T08:00:00Z",
        hour: Int = 8,
        minute: Int = 0,
        snoozedUntil: Long = 0L,
        actions: List<NotificationAction> = listOf(marcarTomada, adiar30),
    ) = ScheduledNotification(
        id = id,
        title = "Nitazoxanida",
        body = "1 comprimido",
        kind = kind,
        triggerAtMillis = millis(triggerAt),
        hour = if (kind == NotificationScheduleKind.DAILY) hour else -1,
        minute = if (kind == NotificationScheduleKind.DAILY) minute else -1,
        data = mapOf("doseId" to "d-17"),
        actions = actions,
        snoozedUntilMillis = snoozedUntil,
    )

    // -----------------------------------------------------------------------------------
    // Modelo da ação
    // -----------------------------------------------------------------------------------

    @Test
    fun `acao de adiar carrega o intervalo e a de app nao`() {
        assertTrue(adiar30.isSnooze)
        assertEquals(30, adiar30.snoozeMinutes)
        assertEquals("kmplib.snooze.30", adiar30.id)
        assertFalse(marcarTomada.isSnooze)
        assertEquals(0, marcarTomada.snoozeMinutes)
        assertFalse(marcarTomada.opensApp, "a graça da ação é justamente não abrir o app")
    }

    @Test
    fun `o app declara so os intervalos e a lib monta os botoes`() {
        val acoes = NotificationAction.snoozeOptions(listOf(15, 30, 60)) { "Adiar $it min" }
        assertEquals(listOf(15, 30, 60), acoes.map { it.snoozeMinutes })
        assertEquals(listOf("Adiar 15 min", "Adiar 30 min", "Adiar 60 min"), acoes.map { it.title })
        assertTrue(acoes.all { it.isSnooze })
        assertEquals(3, acoes.map { it.id }.toSet().size, "ids derivados do intervalo não colidem")
    }

    @Test
    fun `acao invalida falha na hora de construir e nao em producao`() {
        assertFailsWith<IllegalArgumentException> { NotificationAction.app(id = " ", title = "X") }
        assertFailsWith<IllegalArgumentException> { NotificationAction.app(id = "X", title = "") }
        assertFailsWith<IllegalArgumentException> {
            NotificationAction(id = "X", title = "X", kind = NotificationActionKind.SNOOZE)
        }
    }

    @Test
    fun `adiamento absurdo e limitado em vez de virar agendamento eterno`() {
        val enorme = NotificationAction.snooze(minutes = 999_999, title = "Adiar")
        assertEquals(NotificationAction.MAX_SNOOZE_MINUTES, enorme.snoozeMinutes)
        assertEquals(1, NotificationAction.snooze(minutes = 0, title = "Adiar").snoozeMinutes)
    }

    @Test
    fun `acoes de id repetido sao descartadas porque seriam ambiguas`() {
        val duplicada = NotificationAction.app(id = "MARK_TAKEN", title = "Já tomei")
        val resultado = NotificationActionRules.distinctActions(listOf(marcarTomada, duplicada, adiar30))
        assertEquals(listOf("MARK_TAKEN", adiar30.id), resultado.map { it.id })
        assertEquals("Marcar como tomada", resultado.first().title, "vence a primeira declarada")
    }

    @Test
    fun `a acao e encontrada pelo id que volta do sistema`() {
        val item = dose()
        assertEquals(marcarTomada, NotificationActionRules.actionOf(item, "MARK_TAKEN"))
        assertNull(
            NotificationActionRules.actionOf(item, "ACAO_QUE_O_APP_RENOMEOU"),
            "ação desconhecida não pode virar adiamento por engano",
        )
    }

    // -----------------------------------------------------------------------------------
    // Categoria (iOS)
    // -----------------------------------------------------------------------------------

    @Test
    fun `categoria e deterministica para o mesmo conjunto de acoes`() {
        val a = NotificationActionRules.categoryIdentifier(listOf(marcarTomada, adiar30))
        val b = NotificationActionRules.categoryIdentifier(
            listOf(
                NotificationAction.app(id = "MARK_TAKEN", title = "Marcar como tomada"),
                NotificationAction.snooze(minutes = 30, title = "Adiar 30 min"),
            ),
        )
        assertEquals(a, b, "dois lembretes com os mesmos botões compartilham uma categoria")
        assertTrue(a.startsWith("kmplib.act."))
    }

    @Test
    fun `conjunto diferente de acoes ganha categoria propria`() {
        val comAdiar = NotificationActionRules.categoryIdentifier(listOf(marcarTomada, adiar30))
        val soMarcar = NotificationActionRules.categoryIdentifier(listOf(marcarTomada))
        val outroTexto = NotificationActionRules.categoryIdentifier(
            listOf(NotificationAction.app(id = "MARK_TAKEN", title = "Tomei")),
        )
        assertNotEquals(comAdiar, soMarcar)
        assertNotEquals(soMarcar, outroTexto, "o rótulo faz parte da categoria — ele é exibido")
        assertEquals("", NotificationActionRules.categoryIdentifier(emptyList()))
    }

    // -----------------------------------------------------------------------------------
    // Adiamento — a mecânica que a lib executa sozinha
    // -----------------------------------------------------------------------------------

    @Test
    fun `adiar desloca o disparo sem tocar no horario regular`() {
        val agora = millis("2026-08-10T08:02:00Z")
        val item = dose()
        val adiado = NotificationActionRules.applySnooze(item, minutes = 30, nowMillis = agora)

        assertEquals(millis("2026-08-10T08:32:00Z"), adiado.snoozedUntilMillis)
        assertEquals(millis("2026-08-10T08:32:00Z"), adiado.nextTriggerMillis)
        assertEquals(item.triggerAtMillis, adiado.triggerAtMillis, "o horário regular fica intacto")
        assertEquals(8, adiado.hour)
        assertEquals(0, adiado.minute)
        assertEquals(item.id, adiado.id, "adiar NÃO cria um agendamento novo")
        assertEquals(item.actions, adiado.actions, "o disparo adiado volta com os mesmos botões")
    }

    @Test
    fun `adiar duas vezes continua sendo UM agendamento`() {
        val store = InMemoryNotificationScheduleStore()
        store.put(dose())

        val primeiro = NotificationActionRules.applySnooze(
            store.get(1)!!, minutes = 30, nowMillis = millis("2026-08-10T08:02:00Z"),
        )
        store.put(primeiro)
        val segundo = NotificationActionRules.applySnooze(
            store.get(1)!!, minutes = 30, nowMillis = millis("2026-08-10T08:33:00Z"),
        )
        store.put(segundo)

        assertEquals(1, store.all().size, "o registro continua com um item só")
        assertEquals(millis("2026-08-10T09:03:00Z"), store.get(1)!!.nextTriggerMillis)
    }

    @Test
    fun `sem adiamento o proximo disparo e o horario regular`() {
        val item = dose()
        assertFalse(item.isSnoozed)
        assertEquals(item.triggerAtMillis, item.nextTriggerMillis)
        assertFalse(NotificationActionRules.clearSnooze(item).isSnoozed)
    }

    // -----------------------------------------------------------------------------------
    // Adiamento × restauração pós-boot
    // -----------------------------------------------------------------------------------

    @Test
    fun `reiniciar o aparelho no meio do adiamento NAO perde o disparo adiado`() {
        // Dose das 08:00, adiada às 08:02 para 08:32. O celular reinicia às 08:10.
        val adiado = NotificationActionRules.applySnooze(
            dose(), minutes = 30, nowMillis = millis("2026-08-10T08:02:00Z"),
        )
        val plan = NotificationRescheduling.plan(
            stored = listOf(adiado),
            nowMillis = millis("2026-08-10T08:10:00Z"),
            timeZone = TimeZone.UTC,
        )

        val reagendado = plan.toSchedule.single()
        assertEquals(millis("2026-08-10T08:32:00Z"), reagendado.nextTriggerMillis)
        assertTrue(reagendado.isSnoozed)
        assertTrue(plan.toShowNow.isEmpty())
    }

    @Test
    fun `depois que o disparo adiado passa o lembrete diario volta ao horario normal`() {
        val adiado = NotificationActionRules.applySnooze(
            dose(), minutes = 30, nowMillis = millis("2026-08-10T08:02:00Z"),
        )
        // Só reabriu o app no dia seguinte, bem depois do disparo adiado.
        val plan = NotificationRescheduling.plan(
            stored = listOf(adiado),
            nowMillis = millis("2026-08-11T10:00:00Z"),
            timeZone = TimeZone.UTC,
        )

        val reagendado = plan.toSchedule.single()
        assertFalse(reagendado.isSnoozed, "adiamento vencido não fica no registro")
        val local = Instant.fromEpochMilliseconds(reagendado.nextTriggerMillis).toLocalDateTime(TimeZone.UTC)
        assertEquals(12, local.dayOfMonth)
        assertEquals(8, local.hour)
        assertTrue(plan.toShowNow.isEmpty(), "adiamento de ontem não se recupera")
    }

    @Test
    fun `disparo adiado perdido dentro da graca ainda e exibido`() {
        // Adiou para 08:32, o celular estava desligado, ligou às 08:50.
        val adiado = NotificationActionRules.applySnooze(
            dose(), minutes = 30, nowMillis = millis("2026-08-10T08:02:00Z"),
        )
        val plan = NotificationRescheduling.plan(
            stored = listOf(adiado),
            nowMillis = millis("2026-08-10T08:50:00Z"),
            timeZone = TimeZone.UTC,
        )
        assertEquals(listOf(1), plan.toShowNow.map { it.id })
        assertEquals(
            listOf(marcarTomada, adiar30),
            plan.toShowNow.single().actions,
            "o disparo perdido chega com os mesmos botões",
        )
    }

    @Test
    fun `disparo unico adiado e reagendado e nao expirado`() {
        val unico = dose(id = 7, kind = NotificationScheduleKind.ONE_SHOT, triggerAt = "2026-08-10T08:00:00Z")
        val adiado = NotificationActionRules.applySnooze(
            unico, minutes = 60, nowMillis = millis("2026-08-10T08:05:00Z"),
        )
        val plan = NotificationRescheduling.plan(
            stored = listOf(adiado),
            nowMillis = millis("2026-08-10T08:30:00Z"),
            timeZone = TimeZone.UTC,
        )
        assertTrue(plan.expiredIds.isEmpty(), "o disparo original passou, mas o adiado ainda vem")
        assertEquals(millis("2026-08-10T09:05:00Z"), plan.toSchedule.single().nextTriggerMillis)
    }

    @Test
    fun `a janela do iOS ordena pelo disparo efetivo e nao pelo horario regular`() {
        val adiadoParaDepois = NotificationActionRules.applySnooze(
            dose(id = 1, kind = NotificationScheduleKind.ONE_SHOT, triggerAt = "2026-08-10T08:00:00Z"),
            minutes = 60,
            nowMillis = millis("2026-08-10T08:05:00Z"),
        )
        val logoMais = dose(id = 2, kind = NotificationScheduleKind.ONE_SHOT, triggerAt = "2026-08-10T08:40:00Z")

        val window = NotificationRescheduling.selectWindow(
            items = listOf(adiadoParaDepois, logoMais),
            nowMillis = millis("2026-08-10T08:30:00Z"),
        )
        assertEquals(listOf(2, 1), window.register.map { it.id })
    }

    // -----------------------------------------------------------------------------------
    // Persistência — as ações têm de sobreviver ao reboot e à atualização do app
    // -----------------------------------------------------------------------------------

    @Test
    fun `agendamento com acoes faz round-trip de serializacao sem perder nada`() {
        val original = NotificationActionRules.applySnooze(
            dose(actions = listOf(marcarTomada, adiar30, NotificationAction.app("SKIP", "Pular", destructive = true))),
            minutes = 15,
            nowMillis = millis("2026-08-10T08:02:00Z"),
        )
        val raw = notificationScheduleJson.encodeToString(original)
        val voltou = notificationScheduleJson.decodeFromString<ScheduledNotification>(raw)

        assertEquals(original, voltou)
        assertEquals(3, voltou.actions.size)
        assertTrue(voltou.actions[2].destructive)
        assertEquals(original.snoozedUntilMillis, voltou.snoozedUntilMillis)
    }

    @Test
    fun `registro gravado por versao ANTERIOR continua legivel`() {
        // Exatamente o formato que a 2.99.0 gravava: sem `actions`, sem `snoozedUntilMillis`.
        val antigo = """
            {"id":1,"title":"Dose","body":"Hora do remédio","kind":"DAILY",
             "triggerAtMillis":1786000000000,"hour":8,"minute":0,
             "data":{"medicamentoId":"m-1"},"channelId":"dose_channel","isCritical":false}
        """.trimIndent()

        val item = notificationScheduleJson.decodeFromString<ScheduledNotification>(antigo)

        assertEquals(1, item.id)
        assertEquals(emptyList(), item.actions, "campo novo assume o default, não explode")
        assertEquals(0L, item.snoozedUntilMillis)
        assertEquals(item.triggerAtMillis, item.nextTriggerMillis)
        assertEquals("m-1", item.data["medicamentoId"])
    }

    @Test
    fun `registro gravado por versao POSTERIOR nao derruba a leitura`() {
        val futuro = """
            {"id":1,"title":"Dose","body":"x","kind":"ONE_SHOT","triggerAtMillis":1786000000000,
             "campoQueAindaNaoExiste":"algo","actions":[{"id":"A","title":"Ok","campoNovo":1}]}
        """.trimIndent()

        val item = notificationScheduleJson.decodeFromString<ScheduledNotification>(futuro)

        assertEquals(1, item.id)
        assertEquals(listOf("A"), item.actions.map { it.id })
        assertEquals(NotificationActionKind.APP, item.actions.single().kind)
    }

    // -----------------------------------------------------------------------------------
    // Entrega ao app — o handler registrado no Application
    // -----------------------------------------------------------------------------------

    @Test
    fun `o handler recebe id da notificacao, id da acao e o data do dominio`() = runTest {
        NotificationActions.reset()
        val recebido = CompletableDeferred<NotificationActionEvent>()
        NotificationActions.setHandler { recebido.complete(it) }

        val entregue = NotificationActions.dispatch(
            NotificationActionEvent(notificationId = 42, actionId = "MARK_TAKEN", data = mapOf("doseId" to "d-17")),
        )

        assertTrue(entregue)
        val evento = withTimeout(5_000) { recebido.await() }
        assertEquals(42, evento.notificationId)
        assertEquals("MARK_TAKEN", evento.actionId)
        assertEquals("d-17", evento.data["doseId"])
        NotificationActions.reset()
    }

    @Test
    fun `acao que chega antes do handler nao se perde`() = runTest {
        NotificationActions.reset()
        val entregueSemHandler = NotificationActions.dispatch(
            NotificationActionEvent(notificationId = 7, actionId = "MARK_TAKEN"),
        )
        assertFalse(entregueSemHandler)
        assertEquals(listOf(7), NotificationActions.pendingEvents().map { it.notificationId })

        val recebido = CompletableDeferred<NotificationActionEvent>()
        NotificationActions.setHandler { recebido.complete(it) }

        assertEquals(7, withTimeout(5_000) { recebido.await() }.notificationId)
        NotificationActions.reset()
    }

    @Test
    fun `a fila de eventos nao cresce sem limite`() = runTest {
        NotificationActions.reset()
        repeat(NotificationActions.MAX_PENDING_EVENTS + 5) { i ->
            NotificationActions.dispatch(NotificationActionEvent(notificationId = i, actionId = "A"))
        }
        val fila = NotificationActions.pendingEvents()
        assertEquals(NotificationActions.MAX_PENDING_EVENTS, fila.size)
        assertEquals(5, fila.first().notificationId, "os mais antigos saem primeiro")
        NotificationActions.reset()
    }

    @Test
    fun `handler que lanca nao derruba o processo`() = runTest {
        NotificationActions.reset()
        NotificationActions.setHandler { error("banco indisponível") }

        val entregue = NotificationActions.dispatch(
            NotificationActionEvent(notificationId = 1, actionId = "MARK_TAKEN"),
        )

        assertFalse(entregue, "a falha é reportada, não propagada — um receiver não pode lançar")
        NotificationActions.reset()
    }
}

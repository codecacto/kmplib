package br.com.codecacto.kmplib.platform

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regras de restauração dos lembretes locais (o miolo do conserto do `BOOT_COMPLETED`).
 *
 * Tudo aqui é puro: o "agora" entra por parâmetro, então a suíte não depende de relógio, de
 * `Context` nem de `UNUserNotificationCenter` — e a decisão testada é exatamente a que roda dentro
 * do receiver de boot no Android e do reabastecimento de fila no iOS.
 */
class NotificationReschedulingTest {

    private val saoPaulo = TimeZone.of("America/Sao_Paulo")

    private fun millis(iso: String): Long = Instant.parse(iso).toEpochMilliseconds()

    private fun daily(
        id: Int,
        hour: Int,
        minute: Int,
        triggerAt: String,
    ) = ScheduledNotification(
        id = id,
        title = "Dose",
        body = "Hora do remédio",
        kind = NotificationScheduleKind.DAILY,
        triggerAtMillis = millis(triggerAt),
        hour = hour,
        minute = minute,
    )

    private fun oneShot(id: Int, triggerAt: String) = ScheduledNotification(
        id = id,
        title = "Início do ciclo",
        body = "Seu protocolo começa hoje",
        kind = NotificationScheduleKind.ONE_SHOT,
        triggerAtMillis = millis(triggerAt),
    )

    // -----------------------------------------------------------------------------------
    // Próximo disparo diário
    // -----------------------------------------------------------------------------------

    @Test
    fun `disparo diario de hoje ainda no futuro fica hoje mesmo`() {
        val now = millis("2026-08-10T09:00:00Z")
        val next = NotificationRescheduling.nextDailyTriggerMillis(20, 0, now, TimeZone.UTC)
        val local = Instant.fromEpochMilliseconds(next).toLocalDateTime(TimeZone.UTC)
        assertEquals(10, local.dayOfMonth)
        assertEquals(20, local.hour)
        assertEquals(0, local.minute)
    }

    @Test
    fun `disparo diario ja passado vai para amanha`() {
        val now = millis("2026-08-10T21:00:00Z")
        val next = NotificationRescheduling.nextDailyTriggerMillis(20, 0, now, TimeZone.UTC)
        val local = Instant.fromEpochMilliseconds(next).toLocalDateTime(TimeZone.UTC)
        assertEquals(11, local.dayOfMonth)
        assertEquals(20, local.hour)
    }

    @Test
    fun `disparo diario respeita o fuso local e nao o UTC`() {
        // 23:00Z de 10/08 = 20:00 de 10/08 em São Paulo. Pedindo 21:00 LOCAL, é hoje ainda.
        val now = millis("2026-08-10T23:00:00Z")
        val next = NotificationRescheduling.nextDailyTriggerMillis(21, 0, now, saoPaulo)
        val local = Instant.fromEpochMilliseconds(next).toLocalDateTime(saoPaulo)
        assertEquals(10, local.dayOfMonth)
        assertEquals(21, local.hour)
    }

    @Test
    fun `virada de mes no disparo diario`() {
        val now = millis("2026-01-31T23:30:00Z")
        val next = NotificationRescheduling.nextDailyTriggerMillis(8, 0, now, TimeZone.UTC)
        val local = Instant.fromEpochMilliseconds(next).toLocalDateTime(TimeZone.UTC)
        assertEquals(2, local.monthNumber)
        assertEquals(1, local.dayOfMonth)
        assertEquals(8, local.hour)
    }

    @Test
    fun `hora e minuto invalidos sao limitados em vez de quebrar`() {
        val now = millis("2026-08-10T09:00:00Z")
        val next = NotificationRescheduling.nextDailyTriggerMillis(99, 99, now, TimeZone.UTC)
        val local = Instant.fromEpochMilliseconds(next).toLocalDateTime(TimeZone.UTC)
        assertEquals(23, local.hour)
        assertEquals(59, local.minute)
    }

    // -----------------------------------------------------------------------------------
    // Plano de restauração pós-boot
    // -----------------------------------------------------------------------------------

    @Test
    fun `apos o boot todo lembrete diario volta para a fila com o proximo horario`() {
        val now = millis("2026-08-10T10:00:00Z")
        val plan = NotificationRescheduling.plan(
            stored = listOf(
                daily(1, 8, 0, "2026-08-10T08:00:00Z"),
                daily(2, 20, 0, "2026-08-10T20:00:00Z"),
            ),
            nowMillis = now,
            timeZone = TimeZone.UTC,
        )
        assertEquals(2, plan.toSchedule.size, "os dois lembretes diários voltam")
        assertTrue(plan.expiredIds.isEmpty(), "lembrete diário nunca expira")
        // O das 8h já passou hoje ⇒ próximo é amanhã; o das 20h ainda é hoje.
        val agendados = plan.toSchedule.associateBy { it.id }
        assertEquals(millis("2026-08-11T08:00:00Z"), agendados.getValue(1).triggerAtMillis)
        assertEquals(millis("2026-08-10T20:00:00Z"), agendados.getValue(2).triggerAtMillis)
    }

    @Test
    fun `disparo perdido dentro da janela de graca e exibido ao ligar o aparelho`() {
        // Celular desligado às 19:50, dose às 20:00, ligou às 20:20.
        val now = millis("2026-08-10T20:20:00Z")
        val plan = NotificationRescheduling.plan(
            stored = listOf(daily(1, 20, 0, "2026-08-10T20:00:00Z")),
            nowMillis = now,
            timeZone = TimeZone.UTC,
        )
        assertEquals(listOf(1), plan.toShowNow.map { it.id }, "o usuário precisa saber que a dose passou")
        assertEquals(millis("2026-08-11T20:00:00Z"), plan.toSchedule.single().triggerAtMillis)
    }

    @Test
    fun `disparo perdido ha muito tempo NAO e exibido`() {
        // Ligou o celular no dia seguinte de manhã: avisar da dose de ontem à noite seria pior que
        // não avisar (sugere tomar fora de hora).
        val now = millis("2026-08-11T09:00:00Z")
        val plan = NotificationRescheduling.plan(
            stored = listOf(daily(1, 20, 0, "2026-08-10T20:00:00Z")),
            nowMillis = now,
            timeZone = TimeZone.UTC,
        )
        assertTrue(plan.toShowNow.isEmpty())
        assertEquals(millis("2026-08-11T20:00:00Z"), plan.toSchedule.single().triggerAtMillis)
    }

    @Test
    fun `disparo unico futuro e reagendado sem alteracao`() {
        val now = millis("2026-08-10T10:00:00Z")
        val item = oneShot(7, "2026-08-12T17:36:00Z")
        val plan = NotificationRescheduling.plan(listOf(item), now, TimeZone.UTC)
        assertEquals(listOf(item), plan.toSchedule)
        assertTrue(plan.expiredIds.isEmpty())
        assertTrue(plan.toShowNow.isEmpty())
    }

    @Test
    fun `disparo unico vencido sai do registro`() {
        val now = millis("2026-08-15T10:00:00Z")
        val plan = NotificationRescheduling.plan(
            stored = listOf(oneShot(7, "2026-08-12T17:36:00Z")),
            nowMillis = now,
            timeZone = TimeZone.UTC,
        )
        assertEquals(listOf(7), plan.expiredIds)
        assertTrue(plan.toSchedule.isEmpty(), "não faz sentido reagendar o passado")
        assertTrue(plan.toShowNow.isEmpty())
    }

    @Test
    fun `disparo unico perdido dentro da graca e exibido e depois descartado`() {
        val now = millis("2026-08-12T18:00:00Z")
        val plan = NotificationRescheduling.plan(
            stored = listOf(oneShot(7, "2026-08-12T17:36:00Z")),
            nowMillis = now,
            timeZone = TimeZone.UTC,
        )
        assertEquals(listOf(7), plan.toShowNow.map { it.id })
        assertEquals(listOf(7), plan.expiredIds, "exibido uma vez, não volta a ser agendado")
        assertTrue(plan.toSchedule.isEmpty())
    }

    @Test
    fun `janela de graca e configuravel`() {
        val now = millis("2026-08-12T19:00:00Z")
        val stored = listOf(oneShot(7, "2026-08-12T17:36:00Z"))
        val curta = NotificationRescheduling.plan(stored, now, TimeZone.UTC, graceMillis = 30 * 60_000L)
        val longa = NotificationRescheduling.plan(stored, now, TimeZone.UTC, graceMillis = 3 * 60 * 60_000L)
        assertTrue(curta.toShowNow.isEmpty())
        assertEquals(1, longa.toShowNow.size)
    }

    @Test
    fun `plano de registro vazio nao produz nada`() {
        val plan = NotificationRescheduling.plan(emptyList(), millis("2026-08-10T10:00:00Z"), TimeZone.UTC)
        assertTrue(plan.toSchedule.isEmpty() && plan.toShowNow.isEmpty() && plan.expiredIds.isEmpty())
    }

    @Test
    fun `o plano sai ordenado pelo proximo disparo`() {
        val now = millis("2026-08-10T10:00:00Z")
        val plan = NotificationRescheduling.plan(
            stored = listOf(
                oneShot(3, "2026-08-20T10:00:00Z"),
                daily(1, 20, 0, "2026-08-10T20:00:00Z"),
                oneShot(2, "2026-08-12T10:00:00Z"),
            ),
            nowMillis = now,
            timeZone = TimeZone.UTC,
        )
        assertEquals(listOf(1, 2, 3), plan.toSchedule.map { it.id })
    }

    @Test
    fun `um protocolo de 26 dias com dose de 12 em 12h sobrevive ao boot inteiro`() {
        // O caso do Desparasite-se: 52 disparos únicos + 3 avisos de aproximação.
        val inicio = Instant.parse("2026-08-12T00:00:00Z")
        val stored = (0 until 26).flatMap { dia ->
            listOf(
                oneShot(1000 + dia * 2, (inicio + (dia * 24 + 8).horasComoDuration()).toString()),
                oneShot(1001 + dia * 2, (inicio + (dia * 24 + 20).horasComoDuration()).toString()),
            )
        }
        val now = millis("2026-08-12T09:00:00Z") // reiniciou o celular no 1º dia, depois da 1ª dose
        val plan = NotificationRescheduling.plan(stored, now, TimeZone.UTC)

        assertEquals(51, plan.toSchedule.size, "todas as doses futuras voltam para a fila")
        assertEquals(listOf(1000), plan.expiredIds, "só a dose já tomada sai")
        assertTrue(plan.toSchedule.all { it.triggerAtMillis > now })
    }

    // -----------------------------------------------------------------------------------
    // Janela do teto de 64 do iOS
    // -----------------------------------------------------------------------------------

    @Test
    fun `dentro do teto tudo e registrado no sistema`() {
        val now = millis("2026-08-10T10:00:00Z")
        val stored = (1..10).map { oneShot(it, "2026-08-${10 + it}T10:00:00Z") }
        val window = NotificationRescheduling.selectWindow(stored, now)
        assertEquals(10, window.register.size)
        assertTrue(window.deferred.isEmpty())
    }

    @Test
    fun `acima do teto do iOS os disparos mais distantes esperam na fila da lib`() {
        val now = millis("2026-08-12T00:00:00Z")
        val base = Instant.parse("2026-08-12T08:00:00Z")
        val stored = (0 until 80).map { i -> oneShot(i, (base + (i * 12).horasComoDuration()).toString()) }

        val window = NotificationRescheduling.selectWindow(stored, now)

        assertEquals(NotificationRescheduling.IOS_PENDING_LIMIT, window.register.size)
        assertEquals(80 - NotificationRescheduling.IOS_PENDING_LIMIT, window.deferred.size)
        // Os registrados são os MAIS PRÓXIMOS — é o que o usuário vai receber primeiro.
        assertEquals((0 until 60).toList(), window.register.map { it.id })
        assertTrue(window.register.all { r -> window.deferred.all { d -> r.triggerAtMillis < d.triggerAtMillis } })
    }

    @Test
    fun `lembrete diario tem prioridade na janela porque cobre disparos infinitos`() {
        val now = millis("2026-08-12T00:00:00Z")
        val base = Instant.parse("2026-08-12T08:00:00Z")
        val unicos = (0 until 80).map { i -> oneShot(i, (base + (i * 12).horasComoDuration()).toString()) }
        val diario = daily(999, 7, 0, "2027-01-01T07:00:00Z") // disparo distante de propósito

        val window = NotificationRescheduling.selectWindow(unicos + diario, now)

        assertEquals(999, window.register.first().id, "o diário entra primeiro, mesmo com data distante")
        assertFalse(window.deferred.any { it.id == 999 })
    }

    @Test
    fun `itens ja vencidos nao ocupam vaga na janela`() {
        val now = millis("2026-08-12T12:00:00Z")
        val window = NotificationRescheduling.selectWindow(
            items = listOf(
                oneShot(1, "2026-08-11T08:00:00Z"),
                oneShot(2, "2026-08-13T08:00:00Z"),
            ),
            nowMillis = now,
        )
        assertEquals(listOf(2), window.register.map { it.id })
        assertTrue(window.deferred.isEmpty())
    }

    @Test
    fun `limite zero ou negativo nao registra nada`() {
        val now = millis("2026-08-12T00:00:00Z")
        val stored = listOf(oneShot(1, "2026-08-13T08:00:00Z"))
        assertTrue(NotificationRescheduling.selectWindow(stored, now, limit = 0).register.isEmpty())
        assertEquals(1, NotificationRescheduling.selectWindow(stored, now, limit = 0).deferred.size)
    }

    // -----------------------------------------------------------------------------------
    // Store
    // -----------------------------------------------------------------------------------

    @Test
    fun `o registro guarda por id e reagendar o mesmo id sobrescreve`() {
        val store = InMemoryNotificationScheduleStore()
        store.put(daily(1, 8, 0, "2026-08-10T08:00:00Z"))
        store.put(daily(1, 9, 0, "2026-08-10T09:00:00Z"))
        assertEquals(1, store.all().size, "mesmo id não duplica")
        assertEquals(9, store.get(1)?.hour)

        store.put(oneShot(2, "2026-08-12T10:00:00Z"))
        assertEquals(2, store.all().size)
        store.remove(1)
        assertEquals(listOf(2), store.all().map { it.id })
        store.clear()
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `remover id inexistente e no-op`() {
        val store = InMemoryNotificationScheduleStore(listOf(oneShot(1, "2026-08-12T10:00:00Z")))
        store.remove(42)
        assertEquals(1, store.all().size)
    }
}

private fun Int.horasComoDuration() = kotlin.time.Duration.parse("${this}h")

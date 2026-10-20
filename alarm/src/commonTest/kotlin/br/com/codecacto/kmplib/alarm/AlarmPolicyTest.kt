package br.com.codecacto.kmplib.alarm

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2.308.0 — estratégia Android, espaçamento de lembretes e registro para o reboot. */
class AlarmPolicyTest {

    @Test
    fun inicioDeRondaComAlarmeExatoEhDespertador() {
        val plan = planAndroidAlarm(AlarmKind.PATROL_START, canScheduleExact = true, notificationsEnabled = true)
        assertEquals(AndroidAlarmStrategy.ALARM_CLOCK, plan.strategy)
        assertEquals(AlarmResult.Scheduled, plan.result)
    }

    @Test
    fun lembreteComAlarmeExatoEhExactAllowWhileIdle() {
        val plan = planAndroidAlarm(AlarmKind.REMINDER, canScheduleExact = true, notificationsEnabled = true)
        assertEquals(AndroidAlarmStrategy.EXACT_ALLOW_WHILE_IDLE, plan.strategy)
        assertEquals(AlarmResult.Scheduled, plan.result)
    }

    @Test
    fun semAlarmeExatoCaiNaJanela() {
        val reminder = planAndroidAlarm(AlarmKind.REMINDER, canScheduleExact = false, notificationsEnabled = true)
        assertEquals(AndroidAlarmStrategy.WINDOW, reminder.strategy)
        assertEquals(AlarmResult.FallbackWindow, reminder.result)

        val patrol = planAndroidAlarm(AlarmKind.PATROL_START, canScheduleExact = false, notificationsEnabled = true)
        assertEquals(AndroidAlarmStrategy.WINDOW, patrol.strategy)
        assertEquals(AlarmResult.PermissionDenied, patrol.result)
    }

    @Test
    fun notificacaoDesligadaEhPermissaoNegada() {
        for (kind in AlarmKind.entries) {
            for (exact in listOf(true, false)) {
                assertEquals(AlarmResult.PermissionDenied, planAndroidAlarm(kind, exact, notificationsEnabled = false).result)
            }
        }
    }

    @Test
    fun espacamentoDeLembretesParaODoze() {
        val min = 60_000L
        assertTrue(remindersTooClose(listOf(0L, 9 * min, 18 * min)).isEmpty())
        assertEquals(listOf(9 * min to 12 * min), remindersTooClose(listOf(12 * min, 0L, 9 * min)))
        assertTrue(remindersTooClose(emptyList()).isEmpty())
        assertEquals(9 * 60_000L, MIN_REMINDER_SPACING_MILLIS)
    }

    @Test
    fun registroIdaEVoltaComBarraNoRotulo() {
        val r = AlarmRecord(7, AlarmKind.PATROL_START, 1_700_000_000_000L, "Ronda | Bloco B")
        assertEquals(r, AlarmRecord.decode(7, r.encode()))
        assertNull(AlarmRecord.decode(1, null))
        assertNull(AlarmRecord.decode(1, "XYZ|1|a"))
        assertNull(AlarmRecord.decode(1, "REMINDER|abc|a"))
        assertNull(AlarmRecord.decode(1, "REMINDER"))
    }

    @Test
    fun aposOBootReagendaFuturosERecentes() {
        val now = 10_000_000L
        val futuro = AlarmRecord(1, AlarmKind.REMINDER, now + 1, "a")
        val recente = AlarmRecord(2, AlarmKind.PATROL_START, now - 5 * 60_000L, "b")
        val velho = AlarmRecord(3, AlarmKind.REMINDER, now - 60 * 60_000L, "c")
        val (keep, drop) = recordsToRestore(listOf(futuro, recente, velho), now)
        assertEquals(listOf(futuro, recente), keep)
        assertEquals(listOf(velho), drop)
    }
}

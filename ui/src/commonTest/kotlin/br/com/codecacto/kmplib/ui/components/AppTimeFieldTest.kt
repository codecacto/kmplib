package br.com.codecacto.kmplib.ui.components

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A lógica pura do campo de hora do dia (2.215.0): limites inclusivos, "não depois de agora"
 * combinado com a data, hora de abertura do seletor e a frase do limite.
 */
class AppTimeFieldTest {

    private val texts = AppTimeFieldTexts(
        confirm = "OK",
        dismiss = "Cancelar",
        placeholder = "HH:MM",
        notAfter = { fillTimeTemplate("Escolha um horário até %1\$s", it) },
        notBefore = { fillTimeTemplate("Escolha um horário a partir de %1\$s", it) },
    )

    @Test
    fun `sem limites nada viola`() {
        assertNull(timeLimitViolation(LocalTime(23, 59)))
    }

    @Test
    fun `limites sao inclusivos`() {
        assertNull(timeLimitViolation(LocalTime(14, 32), maxTime = LocalTime(14, 32)))
        assertNull(timeLimitViolation(LocalTime(8, 0), minTime = LocalTime(8, 0)))
    }

    @Test
    fun `um minuto depois do maximo viola`() {
        assertEquals(
            TimeLimitViolation.AfterMax,
            timeLimitViolation(LocalTime(14, 33), maxTime = LocalTime(14, 32)),
        )
    }

    @Test
    fun `antes do minimo viola`() {
        assertEquals(
            TimeLimitViolation.BeforeMin,
            timeLimitViolation(LocalTime(7, 59), minTime = LocalTime(8, 0)),
        )
    }

    @Test
    fun `hoje limita na hora de agora truncada no minuto`() {
        val agora = LocalDateTime(2026, 9, 26, 14, 32, 45)
        assertEquals(LocalTime(14, 32), maxTimeNotAfterNow(LocalDate(2026, 9, 26), agora))
    }

    @Test
    fun `sem data trata como hoje`() {
        val agora = LocalDateTime(2026, 9, 26, 9, 5, 0)
        assertEquals(LocalTime(9, 5), maxTimeNotAfterNow(null, agora))
    }

    @Test
    fun `dia anterior nao tem limite`() {
        val agora = LocalDateTime(2026, 9, 26, 0, 1, 0)
        assertNull(maxTimeNotAfterNow(LocalDate(2026, 9, 25), agora))
    }

    @Test
    fun `data futura fecha no mais restritivo`() {
        val agora = LocalDateTime(2026, 9, 26, 10, 0, 0)
        assertEquals(LocalTime(0, 0), maxTimeNotAfterNow(LocalDate(2026, 9, 27), agora))
    }

    @Test
    fun `formata em 24h com zero a esquerda e sem segundos`() {
        assertEquals("09:05", formatTimeHm(LocalTime(9, 5, 30)))
        assertEquals("00:00", formatTimeHm(LocalTime(0, 0)))
        assertEquals("23:59", formatTimeHm(LocalTime(23, 59)))
    }

    @Test
    fun `seletor abre na hora escolhida`() {
        assertEquals(
            LocalTime(10, 15),
            initialPickerTime(LocalTime(10, 15), null, null, fallback = LocalTime(8, 0)),
        )
    }

    @Test
    fun `sem escolha abre no fallback sem segundos`() {
        assertEquals(
            LocalTime(8, 0),
            initialPickerTime(null, null, null, fallback = LocalTime(8, 0, 59)),
        )
    }

    @Test
    fun `seletor abre dentro dos limites`() {
        assertEquals(
            LocalTime(14, 32),
            initialPickerTime(LocalTime(18, 0), null, LocalTime(14, 32), fallback = LocalTime(8, 0)),
        )
        assertEquals(
            LocalTime(9, 0),
            initialPickerTime(null, LocalTime(9, 0), null, fallback = LocalTime(8, 0)),
        )
    }

    @Test
    fun `frase do limite traz a hora formatada`() {
        assertEquals(
            "Escolha um horário até 14:32",
            timeLimitMessage(LocalTime(15, 0), null, LocalTime(14, 32), texts),
        )
        assertEquals(
            "Escolha um horário a partir de 08:00",
            timeLimitMessage(LocalTime(7, 0), LocalTime(8, 0), null, texts),
        )
        assertNull(timeLimitMessage(LocalTime(12, 0), LocalTime(8, 0), LocalTime(14, 32), texts))
    }
}

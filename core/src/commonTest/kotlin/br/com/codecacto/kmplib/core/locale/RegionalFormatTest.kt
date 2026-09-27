package br.com.codecacto.kmplib.core.locale

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RegionalFormatTest {

    @Test
    fun `placeholder segue a ordem do padrao da regiao`() {
        assertEquals("dd/mm/aaaa", placeholderFromDatePattern("dd/MM/y", 'a'))
        assertEquals("mm/dd/yyyy", placeholderFromDatePattern("MM/dd/y", 'y'))
        assertEquals("mm/dd/yyyy", placeholderFromDatePattern("M/d/yy", 'y'))
        assertEquals("dd.mm.yyyy", placeholderFromDatePattern("dd.MM.y", 'y'))
        assertEquals("yyyy-mm-dd", placeholderFromDatePattern("y-MM-dd", 'y'))
        assertEquals("dd/mm/aaaa", placeholderFromDatePattern("dd/LL/uuuu", 'a'))
    }

    @Test
    fun `texto entre aspas do padrao fica, sem as aspas`() {
        assertEquals("dd de mm de aaaa", placeholderFromDatePattern("dd 'de' MM 'de' y", 'a'))
    }

    @Test
    fun `numero e data saem com os digitos certos em qualquer regiao`() {
        val numero = RegionalFormat.formatNumber(1234.5, minFractionDigits = 2)
        assertEquals("123450", numero.filter { it.isDigit() })
        assertTrue(RegionalFormat.decimalSeparator() in numero)
        assertEquals("12345", RegionalFormat.formatInteger(12345).filter { it.isDigit() })
        assertEquals("1234", RegionalFormat.formatInteger(1234, grouping = false).filter { it.isDigit() })
        // HALF_UP nas duas plataformas.
        assertEquals("3", RegionalFormat.formatNumber(2.5, maxFractionDigits = 0).filter { it.isDigit() })

        val data = RegionalFormat.formatDate(LocalDate(2026, 9, 27))
        assertTrue("27" in data && "09" in data && "2026" in data, data)
        // Meio-dia UTC: a data não escorrega para o dia vizinho em fuso nenhum.
        val instante = LocalDate(2026, 1, 2).toEpochDays() * 86_400_000L
        assertTrue("02" in RegionalFormat.formatDateTime(instante, DateSkeletons.SHORT, TimeZone.UTC))
    }

    @Test
    fun `casas decimais invalidas sao recusadas`() {
        assertFailsWith<IllegalArgumentException> { RegionalFormat.formatNumber(1.0, 3, 2) }
    }

    @Test
    fun `placeholder da regiao tem dia, mes e ano`() {
        val ph = RegionalFormat.datePlaceholder('a')
        assertTrue("dd" in ph && "mm" in ph && "aaaa" in ph, ph)
    }
}

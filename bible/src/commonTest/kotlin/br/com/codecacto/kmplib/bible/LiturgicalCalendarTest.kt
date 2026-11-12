package br.com.codecacto.kmplib.bible

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class LiturgicalCalendarTest {

    private val cal = LiturgicalCalendar()

    @Test
    fun `pascoa em anos conhecidos`() {
        mapOf(
            1818 to "1818-03-22", 1943 to "1943-04-25", 2000 to "2000-04-23", 2008 to "2008-03-23",
            2019 to "2019-04-21", 2024 to "2024-03-31", 2025 to "2025-04-20", 2026 to "2026-04-05",
            2027 to "2027-03-28", 2038 to "2038-04-25", 2285 to "2285-03-22",
        ).forEach { (y, d) -> assertEquals(LocalDate.parse(d), cal.easter(y), "Páscoa $y") }
    }

    @Test
    fun `datas moveis de 2026`() {
        assertEquals(LocalDate(2026, 2, 18), cal.ashWednesday(2026))
        assertEquals(LocalDate(2026, 3, 29), cal.palmSunday(2026))
        assertEquals(LocalDate(2026, 5, 24), cal.pentecost(2026))
        assertEquals(LocalDate(2026, 5, 17), cal.ascension(2026))
        assertEquals(LocalDate(2026, 6, 4), cal.corpusChristi(2026))
        assertEquals(LocalDate(2026, 11, 29), cal.firstSundayOfAdvent(2026))
        assertEquals(LocalDate(2022, 11, 27), cal.firstSundayOfAdvent(2022))
        assertEquals(LocalDate(2026, 1, 4), cal.epiphany(2026))
        assertEquals(LocalDate(2026, 1, 11), cal.baptismOfTheLord(2026))
        // Epifania no domingo 8/1 → Batismo na segunda 9/1 (2023).
        assertEquals(LocalDate(2023, 1, 9), cal.baptismOfTheLord(2023))
        assertEquals(LocalDate(2026, 5, 14), LiturgicalCalendar(ascensionOnSunday = false).ascension(2026))
    }

    @Test
    fun `tempos do ano`() {
        assertEquals(LiturgicalSeason.CHRISTMAS, cal.seasonOf(LocalDate(2026, 1, 5)))
        assertEquals(LiturgicalSeason.CHRISTMAS, cal.seasonOf(LocalDate(2026, 1, 11)))
        assertEquals(LiturgicalSeason.ORDINARY_TIME, cal.seasonOf(LocalDate(2026, 1, 12)))
        assertEquals(LiturgicalSeason.LENT, cal.seasonOf(LocalDate(2026, 2, 18)))
        assertEquals(LiturgicalSeason.LENT, cal.seasonOf(LocalDate(2026, 4, 1)))
        assertEquals(LiturgicalSeason.PASCHAL_TRIDUUM, cal.seasonOf(LocalDate(2026, 4, 3)))
        assertEquals(LiturgicalSeason.PASCHAL_TRIDUUM, cal.seasonOf(LocalDate(2026, 4, 5)))
        assertEquals(LiturgicalSeason.EASTER, cal.seasonOf(LocalDate(2026, 4, 6)))
        assertEquals(LiturgicalSeason.EASTER, cal.seasonOf(LocalDate(2026, 5, 24)))
        assertEquals(LiturgicalSeason.ORDINARY_TIME, cal.seasonOf(LocalDate(2026, 11, 11)))
        assertEquals(LiturgicalSeason.ADVENT, cal.seasonOf(LocalDate(2026, 12, 1)))
        assertEquals(LiturgicalSeason.CHRISTMAS, cal.seasonOf(LocalDate(2026, 12, 25)))
        assertEquals(LiturgicalColor.RED, cal.colorOf(LocalDate(2026, 5, 24)))
        assertEquals(LiturgicalColor.PURPLE, cal.colorOf(LocalDate(2026, 3, 1)))
        assertEquals(LiturgicalColor.GREEN, cal.colorOf(LocalDate(2026, 7, 1)))
        assertEquals(LiturgicalColor.WHITE, cal.colorOf(LocalDate(2026, 4, 12)))
    }
}

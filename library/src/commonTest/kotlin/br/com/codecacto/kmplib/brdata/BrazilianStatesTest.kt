package br.com.codecacto.kmplib.brdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrazilianStatesTest {

    // ========================
    // all Tests
    // ========================

    @Test
    fun `all returns exactly 27 states`() {
        assertEquals(27, BrazilianStates.all.size)
    }

    @Test
    fun `all states have valid data`() {
        BrazilianStates.all.forEach { state ->
            assertTrue(state.code.isNotBlank(), "State code should not be blank")
            assertEquals(2, state.abbreviation.length, "State abbreviation should be 2 characters")
            assertTrue(state.name.isNotBlank(), "State name should not be blank")
        }
    }

    @Test
    fun `all contains expected states`() {
        val abbreviations = BrazilianStates.all.map { it.abbreviation }
        val expected = listOf(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
            "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
            "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"
        )
        expected.forEach { abbr ->
            assertTrue(abbreviations.contains(abbr), "Should contain state $abbr")
        }
    }

    // ========================
    // findByCode Tests
    // ========================

    @Test
    fun `findByCode returns state for valid code`() {
        val sp = BrazilianStates.findByCode("35")
        assertNotNull(sp)
        assertEquals("SP", sp.abbreviation)
        assertEquals("São Paulo", sp.name)
        assertEquals(Region.SUDESTE, sp.region)
    }

    @Test
    fun `findByCode returns null for invalid code`() {
        assertNull(BrazilianStates.findByCode("99"))
        assertNull(BrazilianStates.findByCode("00"))
        assertNull(BrazilianStates.findByCode("invalid"))
    }

    @Test
    fun `findByCode returns null for empty code`() {
        assertNull(BrazilianStates.findByCode(""))
    }

    @Test
    fun `findByCode finds all states by IBGE code`() {
        val codes = listOf("12", "27", "16", "13", "29", "23", "53", "32", "52",
            "21", "51", "50", "31", "15", "25", "41", "26", "22",
            "33", "24", "43", "11", "14", "42", "35", "28", "17")

        codes.forEach { code ->
            assertNotNull(BrazilianStates.findByCode(code), "Should find state with code $code")
        }
    }

    // ========================
    // findByAbbreviation Tests
    // ========================

    @Test
    fun `findByAbbreviation returns state for valid uppercase abbreviation`() {
        val rj = BrazilianStates.findByAbbreviation("RJ")
        assertNotNull(rj)
        assertEquals("33", rj.code)
        assertEquals("Rio de Janeiro", rj.name)
        assertEquals(Region.SUDESTE, rj.region)
    }

    @Test
    fun `findByAbbreviation returns state for valid lowercase abbreviation`() {
        val mg = BrazilianStates.findByAbbreviation("mg")
        assertNotNull(mg)
        assertEquals("31", mg.code)
        assertEquals("Minas Gerais", mg.name)
    }

    @Test
    fun `findByAbbreviation returns state for mixed case abbreviation`() {
        val pr = BrazilianStates.findByAbbreviation("Pr")
        assertNotNull(pr)
        assertEquals("Paraná", pr.name)
    }

    @Test
    fun `findByAbbreviation returns null for invalid abbreviation`() {
        assertNull(BrazilianStates.findByAbbreviation("XX"))
        assertNull(BrazilianStates.findByAbbreviation("ZZ"))
    }

    @Test
    fun `findByAbbreviation returns null for empty abbreviation`() {
        assertNull(BrazilianStates.findByAbbreviation(""))
    }

    @Test
    fun `findByAbbreviation finds all 27 states`() {
        val abbreviations = listOf(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
            "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
            "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"
        )

        abbreviations.forEach { abbr ->
            assertNotNull(BrazilianStates.findByAbbreviation(abbr), "Should find state $abbr")
        }
    }

    // ========================
    // findByName Tests
    // ========================

    @Test
    fun `findByName returns state for exact name`() {
        val ba = BrazilianStates.findByName("Bahia")
        assertNotNull(ba)
        assertEquals("BA", ba.abbreviation)
        assertEquals("29", ba.code)
    }

    @Test
    fun `findByName is case insensitive`() {
        val ce = BrazilianStates.findByName("CEARÁ")
        assertNotNull(ce)
        assertEquals("CE", ce.abbreviation)

        val ce2 = BrazilianStates.findByName("ceará")
        assertNotNull(ce2)
        assertEquals("CE", ce2.abbreviation)

        val ce3 = BrazilianStates.findByName("Ceará")
        assertNotNull(ce3)
        assertEquals("CE", ce3.abbreviation)
    }

    @Test
    fun `findByName ignores accents`() {
        val go = BrazilianStates.findByName("Goias") // sem acento
        assertNotNull(go)
        assertEquals("GO", go.abbreviation)

        val go2 = BrazilianStates.findByName("Goiás") // com acento
        assertNotNull(go2)
        assertEquals("GO", go2.abbreviation)
    }

    @Test
    fun `findByName handles names with multiple words`() {
        val rj = BrazilianStates.findByName("Rio de Janeiro")
        assertNotNull(rj)
        assertEquals("RJ", rj.abbreviation)

        val ms = BrazilianStates.findByName("Mato Grosso do Sul")
        assertNotNull(ms)
        assertEquals("MS", ms.abbreviation)
    }

    @Test
    fun `findByName returns null for invalid name`() {
        assertNull(BrazilianStates.findByName("Estado Inexistente"))
        assertNull(BrazilianStates.findByName("Invalid"))
    }

    @Test
    fun `findByName returns null for empty name`() {
        assertNull(BrazilianStates.findByName(""))
    }

    @Test
    fun `findByName returns null for partial name`() {
        assertNull(BrazilianStates.findByName("São")) // partial match should not work
        assertNull(BrazilianStates.findByName("Rio")) // partial match should not work
    }

    // ========================
    // filter Tests
    // ========================

    @Test
    fun `filter returns all states for empty query`() {
        val result = BrazilianStates.filter("")
        assertEquals(27, result.size)
    }

    @Test
    fun `filter returns all states for blank query`() {
        val result = BrazilianStates.filter("   ")
        assertEquals(27, result.size)
    }

    @Test
    fun `filter by partial name`() {
        val result = BrazilianStates.filter("São")
        assertTrue(result.any { it.name == "São Paulo" })
    }

    @Test
    fun `filter by abbreviation`() {
        val result = BrazilianStates.filter("SP")
        assertTrue(result.any { it.abbreviation == "SP" })
    }

    @Test
    fun `filter is case insensitive`() {
        val result1 = BrazilianStates.filter("são paulo")
        val result2 = BrazilianStates.filter("SÃO PAULO")
        val result3 = BrazilianStates.filter("São Paulo")

        assertTrue(result1.isNotEmpty())
        assertTrue(result2.isNotEmpty())
        assertTrue(result3.isNotEmpty())
    }

    @Test
    fun `filter ignores accents`() {
        val result = BrazilianStates.filter("Goias") // sem acento
        assertTrue(result.any { it.name == "Goiás" })
    }

    @Test
    fun `filter returns empty for no matches`() {
        val result = BrazilianStates.filter("XXXYYYZZZ")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `filter by rio returns multiple results`() {
        val result = BrazilianStates.filter("Rio")
        assertTrue(result.size >= 3) // Rio de Janeiro, Rio Grande do Norte, Rio Grande do Sul
        assertTrue(result.any { it.name == "Rio de Janeiro" })
        assertTrue(result.any { it.name == "Rio Grande do Norte" })
        assertTrue(result.any { it.name == "Rio Grande do Sul" })
    }

    // ========================
    // byRegion Tests
    // ========================

    @Test
    fun `byRegion returns correct states for NORTE`() {
        val result = BrazilianStates.byRegion(Region.NORTE)
        assertEquals(7, result.size) // AC, AM, AP, PA, RO, RR, TO
        val abbreviations = result.map { it.abbreviation }
        assertTrue(abbreviations.containsAll(listOf("AC", "AM", "AP", "PA", "RO", "RR", "TO")))
    }

    @Test
    fun `byRegion returns correct states for NORDESTE`() {
        val result = BrazilianStates.byRegion(Region.NORDESTE)
        assertEquals(9, result.size) // AL, BA, CE, MA, PB, PE, PI, RN, SE
        val abbreviations = result.map { it.abbreviation }
        assertTrue(abbreviations.containsAll(listOf("AL", "BA", "CE", "MA", "PB", "PE", "PI", "RN", "SE")))
    }

    @Test
    fun `byRegion returns correct states for CENTRO_OESTE`() {
        val result = BrazilianStates.byRegion(Region.CENTRO_OESTE)
        assertEquals(4, result.size) // DF, GO, MT, MS
        val abbreviations = result.map { it.abbreviation }
        assertTrue(abbreviations.containsAll(listOf("DF", "GO", "MT", "MS")))
    }

    @Test
    fun `byRegion returns correct states for SUDESTE`() {
        val result = BrazilianStates.byRegion(Region.SUDESTE)
        assertEquals(4, result.size) // ES, MG, RJ, SP
        val abbreviations = result.map { it.abbreviation }
        assertTrue(abbreviations.containsAll(listOf("ES", "MG", "RJ", "SP")))
    }

    @Test
    fun `byRegion returns correct states for SUL`() {
        val result = BrazilianStates.byRegion(Region.SUL)
        assertEquals(3, result.size) // PR, RS, SC
        val abbreviations = result.map { it.abbreviation }
        assertTrue(abbreviations.containsAll(listOf("PR", "RS", "SC")))
    }

    @Test
    fun `all regions sum to 27 states`() {
        val norte = BrazilianStates.byRegion(Region.NORTE).size
        val nordeste = BrazilianStates.byRegion(Region.NORDESTE).size
        val centroOeste = BrazilianStates.byRegion(Region.CENTRO_OESTE).size
        val sudeste = BrazilianStates.byRegion(Region.SUDESTE).size
        val sul = BrazilianStates.byRegion(Region.SUL).size

        assertEquals(27, norte + nordeste + centroOeste + sudeste + sul)
    }

    // ========================
    // abbreviations Tests
    // ========================

    @Test
    fun `abbreviations returns list of 27 items`() {
        assertEquals(27, BrazilianStates.abbreviations.size)
    }

    @Test
    fun `abbreviations contains all expected values`() {
        val expected = listOf(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
            "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
            "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"
        )
        assertTrue(BrazilianStates.abbreviations.containsAll(expected))
    }

    @Test
    fun `abbreviations are all uppercase and 2 characters`() {
        BrazilianStates.abbreviations.forEach { abbr ->
            assertEquals(2, abbr.length)
            assertTrue(abbr.all { it.isUpperCase() })
        }
    }

    // ========================
    // names Tests
    // ========================

    @Test
    fun `names returns list of 27 items`() {
        assertEquals(27, BrazilianStates.names.size)
    }

    @Test
    fun `names contains all expected state names`() {
        assertTrue(BrazilianStates.names.contains("São Paulo"))
        assertTrue(BrazilianStates.names.contains("Rio de Janeiro"))
        assertTrue(BrazilianStates.names.contains("Minas Gerais"))
        assertTrue(BrazilianStates.names.contains("Bahia"))
    }

    @Test
    fun `names are all non-empty strings`() {
        BrazilianStates.names.forEach { name ->
            assertTrue(name.isNotBlank())
        }
    }

    // ========================
    // Data Integrity Tests
    // ========================

    @Test
    fun `no duplicate codes`() {
        val codes = BrazilianStates.all.map { it.code }
        assertEquals(codes.size, codes.distinct().size)
    }

    @Test
    fun `no duplicate abbreviations`() {
        val abbreviations = BrazilianStates.all.map { it.abbreviation }
        assertEquals(abbreviations.size, abbreviations.distinct().size)
    }

    @Test
    fun `no duplicate names`() {
        val names = BrazilianStates.all.map { it.name }
        assertEquals(names.size, names.distinct().size)
    }

    @Test
    fun `all regions are represented`() {
        val regions = BrazilianStates.all.map { it.region }.distinct()
        assertEquals(5, regions.size)
        assertTrue(regions.containsAll(Region.entries))
    }
}

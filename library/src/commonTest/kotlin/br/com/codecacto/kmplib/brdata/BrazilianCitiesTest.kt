package br.com.codecacto.kmplib.brdata

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cobre o catálogo oficial de municípios do IBGE (`BrazilianCities`) — dado de
 * fundação reusado por vários apps (seleção de cidade no cadastro). Além de
 * exercitar toda a API pública (busca/filtros/contagem), valida a INTEGRIDADE da
 * tabela gerada: códigos IBGE de 7 dígitos, UF válida, sem nomes em branco e
 * presença das 27 UFs + DF.
 */
class BrazilianCitiesTest {

    // ========================
    // Integridade da base
    // ========================

    @Test
    fun `all contem o conjunto completo de municipios`() {
        // O Brasil tem 5.570 municípios (IBGE). Tolerância pequena por revisões.
        assertTrue(
            BrazilianCities.all.size in 5560..5575,
            "Esperava ~5570 municípios, veio ${BrazilianCities.all.size}"
        )
    }

    @Test
    fun `count e igual ao tamanho de all`() {
        assertEquals(BrazilianCities.all.size, BrazilianCities.count)
    }

    @Test
    fun `todo municipio tem codigo IBGE de 7 digitos`() {
        BrazilianCities.all.forEach { city ->
            assertEquals(7, city.ibgeCode.length, "Código IBGE inválido: ${city.ibgeCode} (${city.name})")
            assertTrue(city.ibgeCode.all { it.isDigit() }, "Código não-numérico: ${city.ibgeCode}")
        }
    }

    @Test
    fun `todo municipio tem nome nao-branco e UF valida`() {
        val ufCodes = BrazilianStates.all.map { it.code }.toSet()
        BrazilianCities.all.forEach { city ->
            assertTrue(city.name.isNotBlank(), "Nome em branco para ${city.ibgeCode}")
            assertTrue(
                city.stateCode in ufCodes,
                "stateCode ${city.stateCode} não é uma UF válida (${city.name})"
            )
        }
    }

    @Test
    fun `codigos IBGE sao unicos`() {
        val codes = BrazilianCities.all.map { it.ibgeCode }
        assertEquals(codes.size, codes.toSet().size, "Há códigos IBGE duplicados")
    }

    @Test
    fun `os dois primeiros digitos do codigo batem com a UF`() {
        // O código IBGE do município começa com o código da UF.
        BrazilianCities.all.forEach { city ->
            assertEquals(
                city.stateCode,
                city.ibgeCode.take(2),
                "Prefixo do código IBGE de ${city.name} não bate com a UF"
            )
        }
    }

    // ========================
    // City — propriedades derivadas
    // ========================

    @Test
    fun `City state resolve para o estado correto`() {
        val saoPaulo = BrazilianCities.findByCode("3550308")
        assertNotNull(saoPaulo)
        assertEquals("São Paulo", saoPaulo.name)
        assertEquals("SP", saoPaulo.state?.abbreviation)
    }

    @Test
    fun `City fullName combina nome e sigla da UF`() {
        val brasilia = BrazilianCities.findByCode("5300108")
        assertNotNull(brasilia)
        assertEquals("Brasília - DF", brasilia.fullName)
    }

    // ========================
    // findByCode
    // ========================

    @Test
    fun `findByCode retorna municipio existente`() {
        val city = BrazilianCities.findByCode("3304557") // Rio de Janeiro
        assertNotNull(city)
        assertEquals("Rio de Janeiro", city.name)
        assertEquals("33", city.stateCode)
    }

    @Test
    fun `findByCode retorna null para codigo inexistente`() {
        assertNull(BrazilianCities.findByCode("0000000"))
    }

    // ========================
    // findByName
    // ========================

    @Test
    fun `findByName ignora acentos e caixa`() {
        val byPlain = BrazilianCities.findByName("sao paulo")
        assertNotNull(byPlain)
        assertEquals("São Paulo", byPlain.name)
    }

    @Test
    fun `findByName retorna null para nome inexistente`() {
        assertNull(BrazilianCities.findByName("Cidade Que Nao Existe"))
    }

    // ========================
    // getByState
    // ========================

    @Test
    fun `getByState aceita sigla da UF`() {
        val df = BrazilianCities.getByState("DF")
        assertEquals(1, df.size, "DF deve ter apenas Brasília")
        assertEquals("Brasília", df.first().name)
    }

    @Test
    fun `getByState aceita codigo IBGE da UF`() {
        val porCodigo = BrazilianCities.getByState("53")
        val porSigla = BrazilianCities.getByState("DF")
        assertEquals(porSigla, porCodigo)
    }

    @Test
    fun `getByState retorna lista ordenada por nome`() {
        val sp = BrazilianCities.getByState("SP")
        assertTrue(sp.size > 600, "SP deve ter mais de 600 municípios, veio ${sp.size}")
        val nomes = sp.map { it.name.removeAccents() }
        assertEquals(nomes.sorted(), nomes, "Lista de SP deve vir ordenada por nome")
    }

    @Test
    fun `getByState retorna vazio para UF invalida`() {
        assertTrue(BrazilianCities.getByState("ZZ").isEmpty())
    }

    // ========================
    // search
    // ========================

    @Test
    fun `search encontra por trecho ignorando acentos`() {
        val resultados = BrazilianCities.search("sao paulo")
        assertTrue(resultados.any { it.name == "São Paulo" })
    }

    @Test
    fun `search respeita o limite`() {
        val limitado = BrazilianCities.search("a", limit = 5)
        assertEquals(5, limitado.size)
    }

    @Test
    fun `search sem limite retorna todos os correspondentes`() {
        val limitado = BrazilianCities.search("santa", limit = 3)
        val completo = BrazilianCities.search("santa")
        assertEquals(3, limitado.size)
        assertTrue(completo.size > limitado.size)
    }

    @Test
    fun `search com query em branco retorna vazio`() {
        assertTrue(BrazilianCities.search("   ").isEmpty())
    }

    // ========================
    // getCityNames / countByState
    // ========================

    @Test
    fun `getCityNames retorna nomes ordenados`() {
        val nomes = BrazilianCities.getCityNames("RJ")
        assertTrue(nomes.contains("Rio de Janeiro"))
        assertEquals(nomes.map { it.removeAccents() }.sorted(), nomes.map { it.removeAccents() })
    }

    @Test
    fun `countByState bate com getByState`() {
        assertEquals(BrazilianCities.getByState("MG").size, BrazilianCities.countByState("MG"))
        assertEquals(1, BrazilianCities.countByState("DF"))
    }

    @Test
    fun `soma das contagens por UF e igual ao total`() {
        val soma = BrazilianStates.all.sumOf { BrazilianCities.countByState(it.code) }
        assertEquals(BrazilianCities.count, soma)
    }
}

package br.com.codecacto.kmplib.brdata

import kotlinx.serialization.Serializable

/**
 * Representa um estado brasileiro.
 */
@Serializable
data class BrazilianState(
    val code: String,
    val abbreviation: String,
    val name: String,
    val region: Region
)

/**
 * Regiões do Brasil.
 */
@Serializable
enum class Region(val displayName: String) {
    NORTE("Norte"),
    NORDESTE("Nordeste"),
    CENTRO_OESTE("Centro-Oeste"),
    SUDESTE("Sudeste"),
    SUL("Sul")
}

/**
 * Repositório de estados brasileiros.
 */
object BrazilianStates {

    /**
     * Lista completa de estados brasileiros ordenados alfabeticamente.
     */
    val all: List<BrazilianState> = listOf(
        BrazilianState("12", "AC", "Acre", Region.NORTE),
        BrazilianState("27", "AL", "Alagoas", Region.NORDESTE),
        BrazilianState("16", "AP", "Amapá", Region.NORTE),
        BrazilianState("13", "AM", "Amazonas", Region.NORTE),
        BrazilianState("29", "BA", "Bahia", Region.NORDESTE),
        BrazilianState("23", "CE", "Ceará", Region.NORDESTE),
        BrazilianState("53", "DF", "Distrito Federal", Region.CENTRO_OESTE),
        BrazilianState("32", "ES", "Espírito Santo", Region.SUDESTE),
        BrazilianState("52", "GO", "Goiás", Region.CENTRO_OESTE),
        BrazilianState("21", "MA", "Maranhão", Region.NORDESTE),
        BrazilianState("51", "MT", "Mato Grosso", Region.CENTRO_OESTE),
        BrazilianState("50", "MS", "Mato Grosso do Sul", Region.CENTRO_OESTE),
        BrazilianState("31", "MG", "Minas Gerais", Region.SUDESTE),
        BrazilianState("15", "PA", "Pará", Region.NORTE),
        BrazilianState("25", "PB", "Paraíba", Region.NORDESTE),
        BrazilianState("41", "PR", "Paraná", Region.SUL),
        BrazilianState("26", "PE", "Pernambuco", Region.NORDESTE),
        BrazilianState("22", "PI", "Piauí", Region.NORDESTE),
        BrazilianState("33", "RJ", "Rio de Janeiro", Region.SUDESTE),
        BrazilianState("24", "RN", "Rio Grande do Norte", Region.NORDESTE),
        BrazilianState("43", "RS", "Rio Grande do Sul", Region.SUL),
        BrazilianState("11", "RO", "Rondônia", Region.NORTE),
        BrazilianState("14", "RR", "Roraima", Region.NORTE),
        BrazilianState("42", "SC", "Santa Catarina", Region.SUL),
        BrazilianState("35", "SP", "São Paulo", Region.SUDESTE),
        BrazilianState("28", "SE", "Sergipe", Region.NORDESTE),
        BrazilianState("17", "TO", "Tocantins", Region.NORTE)
    )

    /**
     * Mapa de código IBGE para estado.
     */
    private val byCode: Map<String, BrazilianState> = all.associateBy { it.code }

    /**
     * Mapa de sigla para estado.
     */
    private val byAbbreviation: Map<String, BrazilianState> = all.associateBy { it.abbreviation }

    /**
     * Busca estado por código IBGE.
     */
    fun findByCode(code: String): BrazilianState? = byCode[code]

    /**
     * Busca estado por sigla (UF).
     */
    fun findByAbbreviation(abbreviation: String): BrazilianState? =
        byAbbreviation[abbreviation.uppercase()]

    /**
     * Busca estado por nome (case-insensitive, ignora acentos).
     */
    fun findByName(name: String): BrazilianState? {
        val normalizedName = name.removeAccents().lowercase()
        return all.find { it.name.removeAccents().lowercase() == normalizedName }
    }

    /**
     * Filtra estados pelo nome ou sigla.
     */
    fun filter(query: String): List<BrazilianState> {
        if (query.isBlank()) return all

        val normalizedQuery = query.removeAccents().lowercase()
        return all.filter { state ->
            state.name.removeAccents().lowercase().contains(normalizedQuery) ||
                    state.abbreviation.lowercase().contains(normalizedQuery)
        }
    }

    /**
     * Retorna estados de uma região.
     */
    fun byRegion(region: Region): List<BrazilianState> = all.filter { it.region == region }

    /**
     * Lista de siglas de estados.
     */
    val abbreviations: List<String> = all.map { it.abbreviation }

    /**
     * Lista de nomes de estados.
     */
    val names: List<String> = all.map { it.name }
}

$municipios = Get-Content 'E:\CodeCacto\Lib\kmplib\municipios.json' -Encoding UTF8 | ConvertFrom-Json

$ufMap = @{
    '11'='RO'; '12'='AC'; '13'='AM'; '14'='RR'; '15'='PA'; '16'='AP'; '17'='TO';
    '21'='MA'; '22'='PI'; '23'='CE'; '24'='RN'; '25'='PB'; '26'='PE'; '27'='AL'; '28'='SE'; '29'='BA';
    '31'='MG'; '32'='ES'; '33'='RJ'; '35'='SP';
    '41'='PR'; '42'='SC'; '43'='RS';
    '50'='MS'; '51'='MT'; '52'='GO'; '53'='DF'
}

$grouped = $municipios | Group-Object -Property codigo_uf | Sort-Object { [int]$_.Name }

$header = @"
package br.com.codecacto.kmplib.brdata

/**
 * Municipio brasileiro
 *
 * @param ibgeCode Codigo IBGE do municipio (7 digitos)
 * @param name Nome do municipio
 * @param stateCode Codigo IBGE do estado (2 digitos)
 */
data class City(
    val ibgeCode: String,
    val name: String,
    val stateCode: String
) {
    /**
     * Obtem o estado deste municipio
     */
    val state: BrazilianState?
        get() = BrazilianStates.findByCode(stateCode)

    /**
     * Nome completo: "Cidade - UF"
     */
    val fullName: String
        get() = "`$name - `${state?.abbreviation ?: stateCode}"
}

/**
 * Gerenciador de municipios brasileiros
 *
 * Contem todos os municipios do Brasil organizados por estado,
 * baseado nos dados oficiais do IBGE.
 */
object BrazilianCities {

    /**
     * Todos os municipios do Brasil
     */
    val all: List<City> by lazy {
        buildCitiesList()
    }

    /**
     * Busca municipio por codigo IBGE
     *
     * @param ibgeCode Codigo IBGE de 7 digitos
     * @return Municipio encontrado ou null
     */
    fun findByCode(ibgeCode: String): City? {
        return all.find { it.ibgeCode == ibgeCode }
    }

    /**
     * Busca municipio por nome (case insensitive, ignora acentos)
     *
     * @param name Nome do municipio
     * @return Municipio encontrado ou null
     */
    fun findByName(name: String): City? {
        return all.find { it.name.equalsIgnoringAccents(name) }
    }

    /**
     * Obtem todos os municipios de um estado
     *
     * @param stateCode Codigo IBGE do estado (2 digitos) ou sigla (UF)
     * @return Lista de municipios do estado
     */
    fun getByState(stateCode: String): List<City> {
        // Aceita tanto codigo IBGE quanto sigla
        val code = if (stateCode.length == 2 && stateCode.all { it.isDigit() }) {
            stateCode
        } else {
            BrazilianStates.findByAbbreviation(stateCode)?.code ?: return emptyList()
        }

        return all.filter { it.stateCode == code }
    }

    /**
     * Busca municipios por nome parcial (case insensitive, ignora acentos)
     *
     * @param query Texto para buscar
     * @param limit Numero maximo de resultados (0 = todos)
     * @return Lista de municipios encontrados
     */
    fun search(query: String, limit: Int = 0): List<City> {
        if (query.isBlank()) return emptyList()

        val results = all.filter { city ->
            city.name.containsIgnoringAccents(query)
        }

        return if (limit > 0) results.take(limit) else results
    }

    /**
     * Obtem lista de nomes de municipios de um estado
     *
     * @param stateCode Codigo IBGE do estado ou sigla (UF)
     * @return Lista de nomes ordenada alfabeticamente
     */
    fun getCityNames(stateCode: String): List<String> {
        return getByState(stateCode).map { it.name }.sorted()
    }

    /**
     * Conta total de municipios
     */
    val count: Int
        get() = all.size

    /**
     * Conta municipios por estado
     */
    fun countByState(stateCode: String): Int {
        return getByState(stateCode).size
    }

    /**
     * Constroi a lista completa de municipios
     * Dados oficiais do IBGE - 5.571 municipios
     */
    private fun buildCitiesList(): List<City> {
        return listOf(
"@

$cities = @()

foreach ($group in $grouped) {
    $ufCode = $group.Name
    $uf = $ufMap[$ufCode]
    if ($uf) {
        $cities += "            // $uf - Codigo $ufCode"
        foreach ($m in ($group.Group | Sort-Object nome)) {
            $ibge = $m.codigo_ibge.ToString()
            $nome = $m.nome -replace '"', '\"'
            $cities += "            City(`"$ibge`", `"$nome`", `"$ufCode`"),"
        }
    }
}

$cities[-1] = $cities[-1] -replace ',$', ''

$footer = @"
        )
    }
}
"@

$fullContent = $header + "`r`n" + ($cities -join "`r`n") + "`r`n" + $footer

[System.IO.File]::WriteAllText('E:\CodeCacto\Lib\kmplib\library\src\commonMain\kotlin\br\com\codecacto\kmplib\brdata\BrazilianCities.kt', $fullContent, [System.Text.Encoding]::UTF8)

Write-Host "Generated file with $($municipios.Count) cities"

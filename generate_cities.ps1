$municipios = Get-Content 'E:\CodeCacto\Lib\kmplib\municipios.json' -Encoding UTF8 | ConvertFrom-Json

$ufMap = @{
    '11'='RO'; '12'='AC'; '13'='AM'; '14'='RR'; '15'='PA'; '16'='AP'; '17'='TO';
    '21'='MA'; '22'='PI'; '23'='CE'; '24'='RN'; '25'='PB'; '26'='PE'; '27'='AL'; '28'='SE'; '29'='BA';
    '31'='MG'; '32'='ES'; '33'='RJ'; '35'='SP';
    '41'='PR'; '42'='SC'; '43'='RS';
    '50'='MS'; '51'='MT'; '52'='GO'; '53'='DF'
}

$grouped = $municipios | Group-Object -Property codigo_uf | Sort-Object { [int]$_.Name }

$output = @()

foreach ($group in $grouped) {
    $ufCode = $group.Name
    $uf = $ufMap[$ufCode]
    if ($uf) {
        $output += "            // $uf - Codigo $ufCode"
        foreach ($m in ($group.Group | Sort-Object nome)) {
            $ibge = $m.codigo_ibge.ToString()
            $nome = $m.nome -replace '"', '\"'
            $output += "            City(`"$ibge`", `"$nome`", `"$ufCode`"),"
        }
    }
}

$output[-1] = $output[-1] -replace ',$', ''

$output | Out-File -Encoding UTF8 'E:\CodeCacto\Lib\kmplib\cities_generated.txt'
Write-Host "Generated $($municipios.Count) cities"

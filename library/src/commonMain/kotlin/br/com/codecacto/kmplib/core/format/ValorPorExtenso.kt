package br.com.codecacto.kmplib.core.format

/**
 * Conversão de valor monetário (em **centavos**) para texto por extenso em
 * português brasileiro (pt-BR). Puro Kotlin (commonMain), altamente testável.
 *
 * **Contrato de paridade:** esta função DEVE produzir exatamente a mesma string
 * (byte a byte, mesma acentuação, mesmos espaços, sem maiúscula inicial) que a
 * implementação da weblib (`valorPorExtenso(centavos: number)`), para todos os
 * casos definidos em `ReciboFacil/docs/design/valor-por-extenso-casos.md`. Um caso
 * divergente entre as duas libs é bug de paridade.
 *
 * Regras (resumo — fonte de verdade é o contrato):
 *  - Entrada em centavos, inteiro **não-negativo** (`< 0` lança [IllegalArgumentException]).
 *  - `reais = centavos / 100`, `cent = centavos % 100`.
 *  - Saída minúscula, sem "R$", sem ponto final, sem espaços duplicados/nas pontas.
 *  - "um real" / "N reais"; "um centavo" / "N centavos"; conjunção " e " entre reais e centavos.
 *  - "cem" (100 exato) vs "cento e ..." (101–199).
 *  - Conjunção " e " entre grupos: antes do último grupo não-nulo quando ele é `< 100`
 *    ou múltiplo exato de 100 (R8).
 *  - "mil" invariável (sem "um mil"); "milhão/milhões", "bilhão/bilhões", "trilhão/trilhões".
 *  - "de reais" quando a parte inteira é múltiplo exato de milhão/bilhão/trilhão (R10).
 *  - `0` → "zero reais".
 *
 * Exemplos:
 * ```kotlin
 * valorPorExtenso(12000)        // "cento e vinte reais"
 * valorPorExtenso(123456)       // "mil duzentos e trinta e quatro reais e cinquenta e seis centavos"
 * valorPorExtenso(1)            // "um centavo"
 * valorPorExtenso(100000000)    // "um milhão de reais"
 * ```
 *
 * @param centavos valor em centavos (`>= 0`).
 * @return valor por extenso em pt-BR (minúsculo).
 * @throws IllegalArgumentException se `centavos < 0`.
 */
fun valorPorExtenso(centavos: Long): String {
    require(centavos >= 0) { "centavos deve ser >= 0 (recibo não tem valor negativo): $centavos" }

    val reais = centavos / 100
    val cent = (centavos % 100).toInt()

    val parteReais: String? = when {
        reais == 0L -> null
        else -> "${reaisPorExtenso(reais)} ${unidadeReal(reais)}"
    }
    val parteCentavos: String? = when {
        cent == 0 -> null
        cent == 1 -> "um centavo"
        else -> "${grupoPorExtenso(cent)} centavos"
    }

    return when {
        parteReais != null && parteCentavos != null -> "$parteReais e $parteCentavos"
        parteReais != null -> parteReais
        parteCentavos != null -> parteCentavos
        else -> "zero reais" // centavos == 0 (R11)
    }
}

// --- Internals --------------------------------------------------------------

private val UNIDADES = arrayOf(
    "zero", "um", "dois", "três", "quatro", "cinco",
    "seis", "sete", "oito", "nove",
)

private val DEZ_A_DEZENOVE = arrayOf(
    "dez", "onze", "doze", "treze", "quatorze", "quinze",
    "dezesseis", "dezessete", "dezoito", "dezenove",
)

private val DEZENAS = arrayOf(
    "", "", "vinte", "trinta", "quarenta", "cinquenta",
    "sessenta", "setenta", "oitenta", "noventa",
)

// índice = centena (1..9); 100 exato é tratado como "cem" fora desta tabela.
private val CENTENAS = arrayOf(
    "", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos",
    "seiscentos", "setecentos", "oitocentos", "novecentos",
)

/**
 * Singular/plural da unidade "real".
 *
 * Contrato (tabela §2.2/§2.4/§2.7): singular **"real"** APENAS para `1` ("um real")
 * e `101` ("cento e um real"). Todas as demais terminações em "um" usam plural:
 *  - `21` → "vinte e um **reais**".
 *  - `201` → "duzentos e um **reais**".
 *  - `1.000.001` → "um milhão e um **reais**".
 * (Esta regra é espelhada por `reaisNoSingular` na weblib — manter idêntica.)
 */
private fun unidadeReal(reais: Long): String =
    if (reais == 1L || reais == 101L) "real" else "reais"

/**
 * Converte um grupo de 0..999 por extenso (com conjunção interna " e " — R7).
 * Para 0 retorna "" (o chamador trata grupos nulos).
 */
private fun grupoPorExtenso(n: Int): String {
    if (n == 0) return ""
    if (n == 100) return "cem"

    val centena = n / 100
    val resto = n % 100

    val partes = mutableListOf<String>()
    if (centena > 0) partes += CENTENAS[centena]
    if (resto > 0) partes += dezenaUnidade(resto)

    return partes.joinToString(" e ")
}

/** Converte 1..99 por extenso (dezena + unidade com " e " quando ambos ≠ 0). */
private fun dezenaUnidade(n: Int): String {
    return when {
        n < 10 -> UNIDADES[n]
        n in 10..19 -> DEZ_A_DEZENOVE[n - 10]
        else -> {
            val dez = n / 10
            val uni = n % 10
            if (uni == 0) DEZENAS[dez] else "${DEZENAS[dez]} e ${UNIDADES[uni]}"
        }
    }
}

// Escalas: nome singular/plural por índice de grupo (0 = unidade, 1 = milhar, ...).
private val ESCALA_SINGULAR = arrayOf("", "mil", "milhão", "bilhão", "trilhão")
private val ESCALA_PLURAL = arrayOf("", "mil", "milhões", "bilhões", "trilhões")

/**
 * Converte a parte inteira de reais (>= 1) por extenso, aplicando:
 *  - escalas (mil/milhão/...), R9;
 *  - conjunção entre grupos (R8);
 *  - "de" antes da unidade "reais" quando múltiplo exato de milhão+ (R10).
 *
 * NOTA: a unidade "reais"/"real" é anexada pelo chamador; quando o "de" se aplica,
 * este método já retorna o texto com sufixo " de" embutido (ex.: "um milhão de"),
 * de modo que `"$parteReais ${unidadeReal}"` produza "um milhão de reais".
 */
private fun reaisPorExtenso(reais: Long): String {
    // Decompor em grupos de 3 dígitos: índice 0 = unidade (0..999), 1 = milhar, ...
    val grupos = mutableListOf<Int>()
    var rest = reais
    while (rest > 0) {
        grupos += (rest % 1000).toInt()
        rest /= 1000
    }
    // grupos[0] = unidade, grupos[last] = mais alto.

    // Monta os segmentos não-nulos do mais alto ao mais baixo.
    data class Seg(val groupIndex: Int, val value: Int, val text: String)
    val segmentos = mutableListOf<Seg>()
    for (i in grupos.indices.reversed()) {
        val v = grupos[i]
        if (v == 0) continue
        val text = when (i) {
            0 -> grupoPorExtenso(v)
            1 -> {
                // "mil" invariável (sem "um mil"); 2..999 mil → "<grupo> mil"
                if (v == 1) "mil" else "${grupoPorExtenso(v)} mil"
            }
            else -> {
                val escala = if (v == 1) ESCALA_SINGULAR[i] else ESCALA_PLURAL[i]
                "${grupoPorExtenso(v)} $escala"
            }
        }
        segmentos += Seg(i, v, text)
    }

    // Junta os segmentos: espaço entre todos, exceto antes do ÚLTIMO segmento,
    // onde a conjunção " e " entra se o último grupo for < 100 ou múltiplo de 100 (R8).
    val sb = StringBuilder()
    for ((idx, seg) in segmentos.withIndex()) {
        if (idx == 0) {
            sb.append(seg.text)
        } else {
            val ehUltimo = idx == segmentos.size - 1
            val sep = if (ehUltimo && (seg.value < 100 || seg.value % 100 == 0)) " e " else " "
            sb.append(sep).append(seg.text)
        }
    }

    var resultado = sb.toString()

    // R10 — "de reais": parte inteira é múltiplo exato de milhão/bilhão/trilhão,
    // i.e. todos os grupos abaixo do milhão (índices 0 e 1) são zero, e existe
    // grupo de milhão ou superior.
    val temMilhaoOuMais = grupos.size > 2 && grupos.drop(2).any { it != 0 }
    val abaixoDeMilhaoZerado = (grupos.getOrElse(0) { 0 } == 0) && (grupos.getOrElse(1) { 0 } == 0)
    if (temMilhaoOuMais && abaixoDeMilhaoZerado) {
        resultado += " de"
    }

    return resultado
}

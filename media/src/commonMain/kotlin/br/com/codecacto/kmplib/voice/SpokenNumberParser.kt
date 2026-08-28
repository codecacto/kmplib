package br.com.codecacto.kmplib.voice

/**
 * Extrai um **número** de um texto reconhecido por voz em português do Brasil.
 *
 * Motivação (Arroba Certa, O0-1): capturar um **peso** falado no curral ("quatrocentos e vinte",
 * "420 quilos", "420,5") sem tocar na tela. O reconhecedor nativo devolve texto livre; este parser
 * puro (commonMain, sem plataforma) converte esse texto em um número utilizável.
 *
 * **Erro de reconhecimento tem custo financeiro** — por isso o fluxo de UI SEMPRE confirma o valor
 * antes de aceitar (ver `DictationOverlay`); este parser é best-effort e nunca lança.
 *
 * Estratégia (nesta ordem):
 * 1. **Dígitos** no texto (ex.: `"420"`, `"391,3"`, `"1.234,5"`) — caminho mais confiável, pois o
 *    Android/iOS já costumam transcrever números como algarismos.
 * 2. **Número por extenso** pt-BR (`"quatrocentos e vinte"`, `"cento e vinte e três"`), incluindo
 *    "meia"/"meio" (=0,5) e a conjunção decimal falada ("vírgula"/"ponto").
 *
 * Suporta 0..999.999 (cobre pesos, arrobas, quantidades e valores comuns). Ignora unidades faladas
 * ("quilos", "kg", "arrobas", "reais") e ruído textual ao redor.
 */
object SpokenNumberParser {

    private val unitWords = mapOf(
        "zero" to 0, "um" to 1, "uma" to 1, "dois" to 2, "duas" to 2, "tres" to 3, "três" to 3,
        "quatro" to 4, "cinco" to 5, "seis" to 6, "sete" to 7, "oito" to 8, "nove" to 9,
        "dez" to 10, "onze" to 11, "doze" to 12, "treze" to 13, "catorze" to 14, "quatorze" to 14,
        "quinze" to 15, "dezesseis" to 16, "dezasseis" to 16, "dezessete" to 17, "dezassete" to 17,
        "dezoito" to 18, "dezenove" to 19, "dezanove" to 19,
    )

    private val tensWords = mapOf(
        "vinte" to 20, "trinta" to 30, "quarenta" to 40, "cinquenta" to 50, "cincoenta" to 50,
        "sessenta" to 60, "setenta" to 70, "oitenta" to 80, "noventa" to 90,
    )

    private val hundredsWords = mapOf(
        "cem" to 100, "cento" to 100, "duzentos" to 200, "duzentas" to 200, "trezentos" to 300,
        "trezentas" to 300, "quatrocentos" to 400, "quatrocentas" to 400, "quinhentos" to 500,
        "quinhentas" to 500, "seiscentos" to 600, "seiscentas" to 600, "setecentos" to 700,
        "setecentas" to 700, "oitocentos" to 800, "oitocentas" to 800, "novecentos" to 900,
        "novecentas" to 900,
    )

    private val halfWords = setOf("meia", "meio")

    /**
     * Converte [text] em um [Double], ou `null` se nenhum número for reconhecido.
     * Best-effort — nunca lança. Ex.: `"quatrocentos e vinte quilos"` → `420.0`; `"391,3"` → `391.3`.
     */
    fun parse(text: String): Double? {
        if (text.isBlank()) return null
        parseDigits(text)?.let { return it }
        return parseWords(text)
    }

    /**
     * Converte [text] em uma **string de exibição** com o [decimalSeparator] pedido (default `,`
     * do padrão BR), sem casas decimais espúrias (inteiro sai sem parte decimal). `null` se nada
     * for reconhecido. Ex.: `"420"` → `"420"`, `"391,3"` → `"391,3"`.
     */
    fun parseToDisplay(text: String, decimalSeparator: Char = ','): String? {
        val value = parse(text) ?: return null
        return formatNumber(value, decimalSeparator)
    }

    // --- Caminho 1: dígitos --------------------------------------------------

    /**
     * Procura o **primeiro** número em algarismos no texto, tolerando separador decimal por
     * vírgula/ponto e separador de milhar. Também entende a forma falada "420 vírgula 5".
     */
    private fun parseDigits(text: String): Double? {
        val normalized = text.lowercase()
            // Colapsa espaços ao redor do separador decimal falado ("420 vírgula 5" → "420,5").
            .replace(Regex("\\s*\\bv[ií]rgula\\b\\s*"), ",")
            .replace(Regex("\\s*\\bponto\\b\\s*"), ".")
        // Sequência de dígitos com separadores internos de milhar/decimal.
        val match = Regex("\\d[\\d.,]*\\d|\\d").find(normalized) ?: return null
        val raw = match.value
        return normalizeNumericToken(raw)
    }

    /**
     * Normaliza um token numérico bruto (ex.: `"1.234,5"`, `"1,234.5"`, `"420"`) em [Double].
     * O separador decimal é o ÚLTIMO ponto/vírgula presente; os demais são milhar.
     */
    private fun normalizeNumericToken(raw: String): Double? {
        val lastDot = raw.lastIndexOf('.')
        val lastComma = raw.lastIndexOf(',')
        val decimalSepIndex = maxOf(lastDot, lastComma)
        val normalized = if (decimalSepIndex < 0) {
            raw.filter { it.isDigit() }
        } else {
            val intPart = raw.substring(0, decimalSepIndex).filter { it.isDigit() }
            val decPart = raw.substring(decimalSepIndex + 1).filter { it.isDigit() }
            if (decPart.isEmpty()) intPart else "$intPart.$decPart"
        }
        return normalized.toDoubleOrNull()
    }

    // --- Caminho 2: número por extenso ---------------------------------------

    private fun parseWords(text: String): Double? {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return null

        // Separa parte inteira e parte decimal falada ("vírgula"/"ponto").
        val sepIndex = tokens.indexOfFirst { it == "virgula" || it == "ponto" }
        val intTokens = if (sepIndex >= 0) tokens.subList(0, sepIndex) else tokens
        val decTokens = if (sepIndex >= 0) tokens.subList(sepIndex + 1, tokens.size) else emptyList()

        val intPart = parseWordSequence(intTokens)
        val decPart = if (decTokens.isNotEmpty()) parseDecimalWords(decTokens) else null

        if (intPart == null && decPart == null) return null
        val base = (intPart ?: 0).toDouble()
        return if (decPart != null) base + decPart else base
    }

    /** Tokeniza removendo a conjunção "e", acentos e pontuação. */
    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace('ã', 'a').replace('á', 'a').replace('â', 'a').replace('à', 'a')
            .replace('é', 'e').replace('ê', 'e').replace('í', 'i').replace('ó', 'o')
            .replace('ô', 'o').replace('õ', 'o').replace('ú', 'u').replace('ç', 'c')
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() && it != "e" }
    }

    /**
     * Soma uma sequência de palavras-número pt-BR (0..999.999). Retorna `null` se **nenhuma**
     * palavra numérica for encontrada. Palavras desconhecidas (unidades, ruído) são ignoradas.
     * Trata "mil" (milhares) e a forma "meia"/"meio" isolada como 0,5 apenas na parte decimal —
     * na parte inteira "meia dúzia" não é resolvida (fora do escopo); "meia" isolada = 0.
     */
    private fun parseWordSequence(tokens: List<String>): Int? {
        var total = 0
        var current = 0
        var found = false
        for (tokenRaw in tokens) {
            val token = normalizeAccents(tokenRaw)
            when {
                token == "mil" -> {
                    total += (if (current == 0) 1 else current) * 1000
                    current = 0
                    found = true
                }
                hundredsWords.containsKey(token) -> {
                    current += hundredsWords.getValue(token); found = true
                }
                tensWords.containsKey(token) -> {
                    current += tensWords.getValue(token); found = true
                }
                unitWords.containsKey(token) -> {
                    current += unitWords.getValue(token); found = true
                }
                // unidades faladas / ruído → ignora
            }
        }
        if (!found) return null
        return total + current
    }

    /**
     * Interpreta a parte decimal falada. Ex.: "cinco" → 0,5; "vinte e cinco" → 0,25; "meia"/"meio"
     * → 0,5. Se forem só dígitos falados, concatena ("três" → 0,3). Best-effort.
     */
    private fun parseDecimalWords(tokens: List<String>): Double? {
        if (tokens.size == 1 && halfWords.contains(normalizeAccents(tokens[0]))) return 0.5
        val value = parseWordSequence(tokens) ?: return null
        // "cinco" (1 dígito) → 0,5; "vinte e cinco" (25) → 0,25; "cinco" isolado tratado como décimo.
        val digits = value.toString()
        return "0.$digits".toDoubleOrNull()
    }

    private fun normalizeAccents(token: String): String =
        token.replace('ã', 'a').replace('á', 'a').replace('â', 'a').replace('à', 'a')
            .replace('é', 'e').replace('ê', 'e').replace('í', 'i').replace('ó', 'o')
            .replace('ô', 'o').replace('õ', 'o').replace('ú', 'u').replace('ç', 'c')

    /** Formata sem casas decimais quando inteiro; senão com o separador pedido (até 2 casas). */
    internal fun formatNumber(value: Double, decimalSeparator: Char): String {
        val cents = kotlin.math.round(value * 100.0).toLong()
        if (cents % 100L == 0L) return (cents / 100L).toString()
        val intPart = cents / 100L
        var decPart = (kotlin.math.abs(cents) % 100L).toString().padStart(2, '0')
        if (decPart.endsWith("0")) decPart = decPart.dropLast(1)
        return "$intPart$decimalSeparator$decPart"
    }
}

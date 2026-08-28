package br.com.codecacto.kmplib.brdata

/**
 * Normaliza uma string para NFD (Canonical Decomposition).
 * Implementação específica por plataforma.
 */
expect fun String.normalizeNFD(): String

/**
 * Remove acentos e caracteres especiais de uma string usando Unicode NFD normalization.
 *
 * Usa decomposição canônica (NFD) para separar caracteres base de seus diacríticos,
 * e então remove os diacríticos (combining marks).
 *
 * Uso:
 * ```kotlin
 * "São Paulo".removeAccents() // "Sao Paulo"
 * "Maringá".removeAccents()   // "Maringa"
 * "Cáceres".removeAccents()   // "Caceres"
 * ```
 */
fun String.removeAccents(): String {
    // NFD decompõe caracteres acentuados em base + combining mark
    // Ex: "é" -> "e" + combining acute accent
    val normalized = this.normalizeNFD()
    // Remove combining diacritical marks (Unicode range \u0300-\u036f)
    return normalized.replace(Regex("[\\u0300-\\u036f]"), "")
}

/**
 * Verifica se uma string contém outra, ignorando acentos e case.
 *
 * Uso:
 * ```kotlin
 * "São Paulo".containsIgnoringAccents("sao") // true
 * ```
 */
fun String.containsIgnoringAccents(other: String): Boolean {
    return this.removeAccents().contains(other.removeAccents(), ignoreCase = true)
}

/**
 * Verifica se duas strings são iguais, ignorando acentos e case.
 *
 * Uso:
 * ```kotlin
 * "São Paulo".equalsIgnoringAccents("sao paulo") // true
 * ```
 */
fun String.equalsIgnoringAccents(other: String): Boolean {
    return this.removeAccents().equals(other.removeAccents(), ignoreCase = true)
}

/**
 * Capitaliza a primeira letra de cada palavra.
 *
 * Uso:
 * ```kotlin
 * "são paulo".toTitleCase() // "São Paulo"
 * ```
 */
fun String.toTitleCase(): String {
    return this.split(" ").joinToString(" ") { word ->
        word.lowercase().replaceFirstChar { it.uppercase() }
    }
}

/**
 * Remove espaços extras e trim.
 *
 * Uso:
 * ```kotlin
 * "  São   Paulo  ".normalizeSpaces() // "São Paulo"
 * ```
 */
fun String.normalizeSpaces(): String {
    return this.trim().replace(Regex("\\s+"), " ")
}

/**
 * Extrai apenas dígitos de uma string.
 *
 * Uso:
 * ```kotlin
 * "(11) 98765-4321".onlyDigits() // "11987654321"
 * ```
 */
fun String.onlyDigits(): String = this.filter { it.isDigit() }

/**
 * Extrai apenas letras de uma string.
 *
 * Uso:
 * ```kotlin
 * "ABC123".onlyLetters() // "ABC"
 * ```
 */
fun String.onlyLetters(): String = this.filter { it.isLetter() }

/**
 * Extrai apenas caracteres alfanuméricos de uma string.
 *
 * Uso:
 * ```kotlin
 * "ABC-123!".onlyAlphanumeric() // "ABC123"
 * ```
 */
fun String.onlyAlphanumeric(): String = this.filter { it.isLetterOrDigit() }

/**
 * Verifica se uma string contém apenas dígitos.
 */
fun String.isDigitsOnly(): Boolean = this.isNotEmpty() && this.all { it.isDigit() }

/**
 * Verifica se uma string contém apenas letras.
 */
fun String.isLettersOnly(): Boolean = this.isNotEmpty() && this.all { it.isLetter() }

/**
 * Trunca uma string em um tamanho máximo, adicionando reticências se necessário.
 *
 * Uso:
 * ```kotlin
 * "Um texto muito longo".truncate(10) // "Um texto..."
 * ```
 */
fun String.truncate(maxLength: Int, suffix: String = "..."): String {
    return if (this.length <= maxLength) {
        this
    } else {
        this.take(maxLength - suffix.length) + suffix
    }
}

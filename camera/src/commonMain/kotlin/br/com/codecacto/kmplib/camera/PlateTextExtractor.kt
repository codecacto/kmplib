package br.com.codecacto.kmplib.camera

import br.com.codecacto.kmplib.mask.isValidPlate
import br.com.codecacto.kmplib.mask.normalizePlate

/**
 * Extrai a **primeira placa brasileira válida** de um texto livre reconhecido
 * por um motor de OCR (ML Kit no Android, Apple Vision no iOS).
 *
 * O texto de OCR costuma vir com múltiplas linhas, ruído e separadores
 * (ex.: `"BRASIL\nABC-1D23\nMERCOSUL"`). Esta função varre janelas de 7
 * caracteres alfanuméricos contíguos e retorna a primeira que satisfaz
 * [isValidPlate], já **normalizada** via [normalizePlate].
 *
 * Implementada em `commonMain` (sem dependência de plataforma) para ser
 * testável e reutilizada por todos os `actual` do [PlateOcrAnalyzer].
 *
 * @return placa normalizada (ex.: `"ABC1D23"`) ou `null` se nenhuma for válida.
 *
 * ```kotlin
 * extractPlate("placa: abc-1d23\nbrasil") // "ABC1D23"
 * extractPlate("sem placa aqui")          // null
 * ```
 */
fun extractPlate(ocrText: String): String? {
    if (ocrText.isBlank()) return null

    // Mantém apenas alfanuméricos em maiúsculas, preservando quebras como
    // separadores de "palavra" para não juntar tokens de linhas diferentes.
    val tokens = ocrText
        .uppercase()
        .split(Regex("[^A-Z0-9]+"))
        .filter { it.isNotEmpty() }

    // 1) Tenta cada token isolado (caso comum: a placa vem em uma palavra só).
    for (token in tokens) {
        val candidate = normalizePlate(token)
        if (candidate.length == 7 && isValidPlate(candidate)) return candidate
    }

    // 2) Tenta janelas deslizantes sobre o texto concatenado (caso a placa
    //    tenha sido quebrada em dois tokens, ex.: "ABC" + "1D23").
    val joined = tokens.joinToString("")
    if (joined.length >= 7) {
        for (start in 0..(joined.length - 7)) {
            val window = joined.substring(start, start + 7)
            if (isValidPlate(window)) return window
        }
    }

    return null
}

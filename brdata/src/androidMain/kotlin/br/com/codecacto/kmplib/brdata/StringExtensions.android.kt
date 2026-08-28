package br.com.codecacto.kmplib.brdata

import java.text.Normalizer

/**
 * Normaliza uma string para NFD usando java.text.Normalizer.
 */
actual fun String.normalizeNFD(): String {
    return Normalizer.normalize(this, Normalizer.Form.NFD)
}

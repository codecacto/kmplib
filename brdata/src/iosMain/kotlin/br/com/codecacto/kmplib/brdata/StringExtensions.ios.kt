package br.com.codecacto.kmplib.brdata

import platform.Foundation.NSString
import platform.Foundation.decomposedStringWithCanonicalMapping

/**
 * Normaliza uma string para NFD usando NSString.decomposedStringWithCanonicalMapping.
 */
actual fun String.normalizeNFD(): String {
    @Suppress("CAST_NEVER_SUCCEEDS")
    val nsString = this as NSString
    return nsString.decomposedStringWithCanonicalMapping
}

package br.com.codecacto.kmplib.platform

import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale

/**
 * Região do aparelho no iOS.
 *
 * `countryCode` do `NSLocale.currentLocale` é a **região** escolhida em Ajustes → Geral → Idioma e
 * Região, que no iOS é uma configuração separada do idioma — exatamente a distinção que este módulo
 * existe para respeitar.
 */
actual fun platformRegionCode(): String? = NSLocale.currentLocale.countryCode

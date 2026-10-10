package br.com.codecacto.kmplib.core.text

import platform.Foundation.NSString
import platform.Foundation.create
import platform.Foundation.decomposedStringWithCanonicalMapping

@OptIn(kotlinx.cinterop.BetaInteropApi::class)
internal actual fun decomposeCanonical(text: String): String =
    NSString.create(string = text).decomposedStringWithCanonicalMapping

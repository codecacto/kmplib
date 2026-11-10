package br.com.codecacto.kmplib.core.text

import java.text.Normalizer

internal actual fun decomposeCanonical(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)

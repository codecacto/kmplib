package br.com.codecacto.kmplib.platform

import android.content.ClipDescription
import kotlin.test.Test
import kotlin.test.assertEquals

class ClipboardAndroidTest {

    @Test
    fun sensitiveKey_isTheSdkConstant_onApi33AndUp() {
        assertEquals(ClipDescription.EXTRA_IS_SENSITIVE, sensitiveClipExtraKey(33))
        assertEquals(ClipDescription.EXTRA_IS_SENSITIVE, sensitiveClipExtraKey(36))
    }

    @Test
    fun sensitiveKey_belowApi33_isTheSameLiteralString() {
        assertEquals("android.content.extra.IS_SENSITIVE", sensitiveClipExtraKey(24))
        assertEquals("android.content.extra.IS_SENSITIVE", sensitiveClipExtraKey(32))
        // A doc do Android manda usar a string literal abaixo da 33 — precisa ser a MESMA da constante.
        assertEquals(ClipDescription.EXTRA_IS_SENSITIVE, LEGACY_IS_SENSITIVE_KEY)
    }
}

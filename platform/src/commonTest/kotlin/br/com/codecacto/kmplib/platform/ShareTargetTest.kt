package br.com.codecacto.kmplib.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShareTargetTest {

    @Test
    fun `url de stories exige app id numerico`() {
        assertEquals("instagram-stories://share?source_application=123456", instagramStoriesUrl(" 123456 "))
        assertNull(instagramStoriesUrl(null))
        assertNull(instagramStoriesUrl(""))
        assertNull(instagramStoriesUrl("abc&x=1"))
        assertTrue(isValidMetaAppId("987"))
        assertFalse(isValidMetaAppId("9 8"))
    }

    @Test
    fun `arquivo do whatsapp no ios termina em wai e nao sai do diretorio`() {
        assertEquals("versiculo.wai", whatsAppIosFileName("versiculo.png"))
        assertTrue(whatsAppIosFileName("../../etc/x.png").endsWith(".wai"))
        assertFalse(whatsAppIosFileName("../../etc/x.png").contains("/"))
    }

    @Test
    fun `handler escrito a mao nao oferece destino nenhum`() {
        val h = object : ShareHandler {
            override fun shareText(text: String, title: String) = Unit
            override fun shareImage(imageBytes: ByteArray, fileName: String, title: String) = Unit
            override fun shareFile(fileBytes: ByteArray, fileName: String, mimeType: String, title: String) = Unit
        }
        ShareTarget.entries.forEach { assertFalse(h.isInstalled(it)) }
        assertEquals(ShareTargetResult.NotAvailable, h.shareImageTo(ShareTarget.WHATSAPP, byteArrayOf(1), "a.png"))
    }
}

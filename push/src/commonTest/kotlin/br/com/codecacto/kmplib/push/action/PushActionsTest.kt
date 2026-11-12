package br.com.codecacto.kmplib.push.action

import br.com.codecacto.kmplib.ui.announcement.Announcement
import br.com.codecacto.kmplib.ui.announcement.AnnouncementKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class PushActionsTest {

    @Test
    fun le_as_quatro_acoes() {
        assertEquals(PushAction.OpenApp("c1"), PushActions.parse(mapOf("cc_acao" to "app", "cc_campanha" to "c1")))
        assertEquals(PushAction.OpenPremium(null), PushActions.parse(mapOf("cc_acao" to "PREMIUM")))
        assertEquals(
            PushAction.OpenAnnouncement("a9", "c1"),
            PushActions.parse(mapOf("cc_acao" to "comunicado", "cc_comunicado" to "a9", "cc_campanha" to "c1")),
        )
        assertEquals(
            PushAction.OpenLink("https://codecacto.com.br/x?y=1", null),
            PushActions.parse(mapOf("cc_acao" to "link", "cc_url" to " https://codecacto.com.br/x?y=1 ")),
        )
    }

    @Test
    fun push_que_nao_e_de_campanha_devolve_null() {
        assertNull(PushActions.parse(mapOf("rota" to "/pedido/1")))
        assertFalse(PushActions.isCampaign(mapOf("cc_acao" to " ")))
    }

    @Test
    fun acao_incompleta_ou_desconhecida_so_abre_o_app() {
        assertEquals(PushAction.OpenApp(null), PushActions.parse(mapOf("cc_acao" to "comunicado")))
        assertEquals(PushAction.OpenApp(null), PushActions.parse(mapOf("cc_acao" to "outra")))
        assertEquals(PushAction.OpenApp(null), PushActions.parse(mapOf("cc_acao" to "link", "cc_url" to "javascript:alert(1)")))
        assertEquals(PushAction.OpenApp(null), PushActions.parse(mapOf("cc_acao" to "link", "cc_url" to "intent://x#Intent;end")))
    }

    @Test
    fun url_segura() {
        assertTrue(PushActions.isSafeExternalUrl("http://a.com"))
        assertTrue(PushActions.isSafeExternalUrl("HTTPS://a.com/b"))
        assertFalse(PushActions.isSafeExternalUrl("https://"))
        assertFalse(PushActions.isSafeExternalUrl("https:///caminho"))
        assertFalse(PushActions.isSafeExternalUrl("https://a.com/b c"))
        assertFalse(PushActions.isSafeExternalUrl("file:///etc/passwd"))
        assertFalse(PushActions.isSafeExternalUrl("ftp://a.com"))
    }

    private class Calls {
        var premium = 0
        val urls = mutableListOf<String>()
        val shown = mutableListOf<Announcement>()
        val other = mutableListOf<Map<String, String>>()
    }

    private fun router(calls: Calls, found: Announcement?) = PushTapRouter(
        onOpenPremium = { calls.premium++ },
        openUrl = { calls.urls += it },
        findAnnouncement = { id -> found?.takeIf { it.id == id } },
        showAnnouncement = { calls.shown += it },
        onOtherTap = { calls.other += it },
    )

    @Test
    fun roteador_chama_o_destino_certo() = runTest {
        val aviso = Announcement(id = "a1", kind = AnnouncementKind.NEWS, title = "T", message = "M")
        val calls = Calls()
        val r = router(calls, aviso)
        r.route(mapOf("cc_acao" to "premium"))
        r.route(mapOf("cc_acao" to "link", "cc_url" to "https://x.com"))
        r.route(mapOf("cc_acao" to "comunicado", "cc_comunicado" to "a1"))
        r.route(mapOf("cc_acao" to "comunicado", "cc_comunicado" to "nao-existe"))
        r.route(mapOf("cc_acao" to "app"))
        r.route(mapOf("rota" to "/x"))
        assertEquals(1, calls.premium)
        assertEquals(listOf("https://x.com"), calls.urls)
        assertEquals(listOf(aviso), calls.shown)
        assertEquals(listOf(mapOf("rota" to "/x")), calls.other)
    }
}

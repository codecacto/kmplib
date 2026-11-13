package br.com.codecacto.kmplib.push

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class Gravador : PushNotificationListener {
    val tokens = mutableListOf<String>()
    val toques = mutableListOf<Map<String, String>>()
    val foreground = mutableListOf<Map<String, String>>()

    override fun onNewToken(token: String) {
        tokens += token
    }

    override fun onNotificationClicked(data: Map<String, String>) {
        toques += data
    }

    override fun onPushNotificationWithPayloadData(title: String?, body: String?, data: Map<String, String>) {
        foreground += data
    }
}

class PushListenerHubTest {

    private val toque = mapOf("tipo" to "ALERTA", "alertaId" to "a1")

    @Test
    fun varios_ouvintes_recebem_e_nenhum_sobrescreve_o_outro() {
        val hub = PushListenerHub()
        val a = Gravador()
        val b = Gravador()
        hub.addListener(a)
        hub.addListener(b)

        hub.onNewToken("t1")
        PushEventRouter.dispatch(hub, toque, wasTapped = true)
        PushEventRouter.dispatch(hub, toque, wasTapped = false)

        listOf(a, b).forEach {
            assertEquals(listOf("t1"), it.tokens)
            assertEquals(listOf(toque), it.toques)
            assertEquals(listOf(toque), it.foreground)
        }
        assertEquals(2, hub.listenerCount)
    }

    @Test
    fun remover_pelo_handle_tira_so_aquele_ouvinte_e_e_idempotente() {
        val hub = PushListenerHub()
        val a = Gravador()
        val b = Gravador()
        val ra = hub.addListener(a)
        hub.addListener(b)

        ra.remove()
        ra.remove()
        hub.onNewToken("t")

        assertTrue(a.tokens.isEmpty())
        assertEquals(listOf("t"), b.tokens)
        assertFalse(ra.isActive)
        assertEquals(1, hub.listenerCount)
    }

    @Test
    fun mesmo_ouvinte_duas_vezes_recebe_uma_vez_e_sai_com_o_ultimo_handle() {
        val hub = PushListenerHub()
        val a = Gravador()
        val r1 = hub.addListener(a)
        val r2 = hub.addListener(a)

        hub.onNewToken("t1")
        assertEquals(listOf("t1"), a.tokens)

        r1.remove()
        hub.onNewToken("t2")
        assertEquals(listOf("t1", "t2"), a.tokens)

        r2.close()
        hub.onNewToken("t3")
        assertEquals(listOf("t1", "t2"), a.tokens)
        assertEquals(0, hub.listenerCount)
    }

    @Test
    fun toque_sem_ouvinte_fica_pendente_e_vai_ao_primeiro_que_registrar_uma_vez_so() {
        val hub = PushListenerHub()
        PushEventRouter.dispatch(hub, toque, wasTapped = true)
        assertEquals(toque, hub.pendingTap.value)

        val a = Gravador()
        val b = Gravador()
        hub.addListener(a)
        hub.addListener(b)

        assertEquals(listOf(toque), a.toques)
        assertTrue(b.toques.isEmpty())
        assertNull(hub.pendingTap.value)
        assertNull(hub.consumePendingTap())
    }

    @Test
    fun so_o_ultimo_toque_fica_pendente() {
        val hub = PushListenerHub()
        val outro = mapOf("tipo" to "ALERTA", "alertaId" to "a2")
        hub.onNotificationClicked(toque)
        hub.onNotificationClicked(outro)

        assertEquals(outro, hub.consumePendingTap())
        assertNull(hub.consumePendingTap())
    }

    @Test
    fun ouvinte_que_recusa_o_pendente_nao_o_consome() {
        val hub = PushListenerHub()
        hub.onNotificationClicked(toque)

        val a = Gravador()
        hub.addListener(a, receivePendingTap = false)

        assertTrue(a.toques.isEmpty())
        assertEquals(toque, hub.consumePendingTap())
    }

    @Test
    fun noop_nao_registra_nem_consome_o_pendente() {
        val hub = PushListenerHub()
        hub.onNotificationClicked(toque)

        val r = hub.addListener(NoOpPushNotificationListener)

        assertFalse(r.isActive)
        assertEquals(0, hub.listenerCount)
        assertEquals(toque, hub.pendingTap.value)
    }

    @Test
    fun foreground_sem_ouvinte_nao_fica_guardado() {
        val hub = PushListenerHub()
        PushEventRouter.dispatch(hub, toque, wasTapped = false)
        assertNull(hub.pendingTap.value)
    }

    @Test
    fun toque_com_ouvinte_presente_nao_fica_pendente_e_ouvinte_removido_volta_a_guardar() {
        val hub = PushListenerHub()
        val a = Gravador()
        val r = hub.addListener(a)
        hub.onNotificationClicked(toque)
        assertNull(hub.pendingTap.value)

        r.remove()
        hub.onNotificationClicked(toque)
        assertEquals(toque, hub.pendingTap.value)

        hub.clearPendingTap()
        assertNull(hub.pendingTap.value)
    }

    @Test
    fun ouvinte_sem_toque_recebe_token_mas_nao_rouba_o_toque() {
        val hub = PushListenerHub()
        val token = Gravador()
        hub.addListener(token, receivePendingTap = true, receiveTaps = false)
        hub.onNewToken("t")
        hub.onNotificationClicked(toque)
        assertEquals(toque, hub.pendingTap.value, "sem ouvinte de toque, o toque fica pendente")
        assertTrue(token.toques.isEmpty())
        assertEquals(listOf("t"), token.tokens)

        val tela = Gravador()
        hub.addListener(tela)
        assertEquals(listOf(toque), tela.toques)
        assertNull(hub.pendingTap.value)

        hub.onNotificationClicked(toque)
        assertEquals(2, tela.toques.size)
        assertTrue(token.toques.isEmpty())
    }

    private val campanha = mapOf("cc_acao" to "premium", "cc_campanha" to "c1")

    @Test
    fun ouvinte_do_app_nao_rouba_o_toque_de_campanha_a_frio() {
        val hub = PushListenerHub()
        val operacional = Gravador()
        hub.addListener(operacional) // receiveTaps padrão: recebe só o toque do próprio app
        hub.onNotificationClicked(campanha)
        assertTrue(operacional.toques.isEmpty(), "campanha não vai ao ouvinte operacional")
        assertEquals(campanha, hub.pendingTap.value, "fica pendente para o PushTapHost")

        val host = Gravador()
        hub.addListener(host, receiveTaps = false, receiveCampaignTaps = true)
        assertEquals(listOf(campanha), host.toques)
        assertNull(hub.pendingTap.value)

        hub.onNotificationClicked(toque)
        assertEquals(listOf(toque), operacional.toques)
        assertEquals(listOf(campanha), host.toques, "host só de campanha não recebe o do app")
    }

    @Test
    fun toque_do_app_pendente_nao_vai_ao_host_so_de_campanha() {
        val hub = PushListenerHub()
        hub.onNotificationClicked(toque)
        val host = Gravador()
        hub.addListener(host, receiveTaps = false, receiveCampaignTaps = true)
        assertTrue(host.toques.isEmpty())
        assertEquals(toque, hub.pendingTap.value)

        val operacional = Gravador()
        hub.addListener(operacional)
        assertEquals(listOf(toque), operacional.toques)
        assertNull(hub.pendingTap.value)
    }

    @Test
    fun ouvinte_dos_dois_tipos_recebe_campanha_e_toque_do_app() {
        val hub = PushListenerHub()
        val tudo = Gravador()
        hub.addListener(tudo, receiveCampaignTaps = true)
        hub.onNotificationClicked(campanha)
        hub.onNotificationClicked(toque)
        assertEquals(listOf(campanha, toque), tudo.toques)
    }
}

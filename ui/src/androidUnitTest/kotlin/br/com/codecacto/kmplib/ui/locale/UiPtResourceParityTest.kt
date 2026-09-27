package br.com.codecacto.kmplib.ui.locale

import br.com.codecacto.kmplib.sync.rest.DomainApiTexts
import br.com.codecacto.kmplib.ui.components.ConnectivityTexts
import br.com.codecacto.kmplib.ui.components.ErrorStateTexts
import br.com.codecacto.kmplib.ui.components.MaintenanceTexts
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** O recurso pt-BR diz o que o default da classe dizia (2.219.0 trocou a fonte, não o texto). */
class UiPtResourceParityTest {

    private val pt: Map<String, String> by lazy {
        val xml = File("src/commonMain/composeResources/values/strings.xml").readText()
        Regex("""<string name="([^"]+)">(.*?)</string>""").findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2].replace("&amp;", "&") }
    }

    @Test
    fun `cliente de dominio em pt-BR e o de antes`() {
        val d = DomainApiTexts()
        assertEquals(d.offline, pt["kmplib_error_network"])
        assertEquals(d.rateLimited, pt["kmplib_error_rate_limited"])
        assertEquals(d.sessionExpired, pt["kmplib_error_session_expired"])
        assertEquals(d.quotaReached, pt["kmplib_error_quota_reached"])
        assertEquals(d.serverError(404), pt.getValue("kmplib_error_server").replace("%1\$d", "404"))
    }

    @Test
    fun `estado de erro em pt-BR e o de antes`() {
        val d = ErrorStateTexts()
        assertEquals(d.title, pt["kmplib_error_state_title"])
        assertEquals(d.retryButton, pt["kmplib_retry"])
        assertEquals(d.offlineTitle, pt["kmplib_error_state_offline_title"])
        assertEquals(d.offlineMessage, pt["kmplib_error_state_offline_message"])
    }

    @Test
    fun `conexao e manutencao em pt-BR sao os de antes`() {
        val c = ConnectivityTexts()
        assertEquals(c.modalTitle, pt["kmplib_connectivity_title"])
        assertEquals(c.bannerText, pt["kmplib_connectivity_title"])
        assertEquals(c.modalMessage, pt["kmplib_connectivity_modal_message"])
        assertEquals(c.screenMessage, pt["kmplib_connectivity_screen_message"])
        assertEquals(c.checkingButton, pt["kmplib_connectivity_checking"])
        val m = MaintenanceTexts()
        assertEquals(m.title, pt["kmplib_maintenance_title"])
        assertEquals(m.message, pt["kmplib_maintenance_message"])
        assertEquals(m.checkingButton, pt["kmplib_checking"])
    }
}

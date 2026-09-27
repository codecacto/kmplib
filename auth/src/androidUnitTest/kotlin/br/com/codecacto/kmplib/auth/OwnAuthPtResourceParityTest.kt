package br.com.codecacto.kmplib.auth

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * O recurso pt-BR (o que o aparelho brasileiro lê) diz EXATAMENTE o que o default da classe dizia —
 * a 2.219.0 trocou a fonte das mensagens do own-auth, não o texto. App que compara mensagem, e
 * suíte que a confere, não sentem a troca.
 */
class OwnAuthPtResourceParityTest {

    private val pt: Map<String, String> by lazy {
        val xml = File("../ui/src/commonMain/composeResources/values/strings.xml").readText()
        Regex("""<string name="([^"]+)">(.*?)</string>""").findAll(xml)
            .associate { it.groupValues[1] to it.groupValues[2].replace("&amp;", "&") }
    }

    @Test
    fun `mensagens do own-auth em pt-BR sao as de antes`() {
        val d = OwnAuthTexts()
        assertEquals(d.invalidCredentials, pt["kmplib_auth_invalid_credentials"])
        assertEquals(d.emailAlreadyInUse, pt["kmplib_auth_email_in_use"])
        assertEquals(d.weakPassword, pt["kmplib_auth_weak_password"])
        assertEquals(d.invalidResetToken, pt["kmplib_auth_invalid_reset"])
        assertEquals(d.tooManyRequests, pt["kmplib_error_too_many_attempts"])
        assertEquals(d.network, pt["kmplib_error_network"])
        assertEquals(d.sessionExpired, pt["kmplib_error_session_expired"])
        assertEquals(d.socialRejected, pt["kmplib_auth_social_rejected"])
        assertEquals(d.unsupported, pt["kmplib_auth_unsupported"])
        assertEquals(d.currentPasswordIncorrect, pt["kmplib_auth_current_password_incorrect"])
        assertEquals(d.server(500), pt.getValue("kmplib_error_server").replace("%1\$d", "500"))
    }
}

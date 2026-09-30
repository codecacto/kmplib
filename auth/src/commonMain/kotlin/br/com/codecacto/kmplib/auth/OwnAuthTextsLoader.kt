package br.com.codecacto.kmplib.auth

import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_auth_current_password_incorrect
import br.com.codecacto.kmplib.generated.resources.kmplib_auth_email_in_use
import br.com.codecacto.kmplib.generated.resources.kmplib_auth_invalid_credentials
import br.com.codecacto.kmplib.generated.resources.kmplib_auth_invalid_reset
import br.com.codecacto.kmplib.generated.resources.kmplib_auth_profile_rejected
import br.com.codecacto.kmplib.generated.resources.kmplib_auth_social_rejected
import br.com.codecacto.kmplib.generated.resources.kmplib_auth_unsupported
import br.com.codecacto.kmplib.generated.resources.kmplib_auth_weak_password
import br.com.codecacto.kmplib.generated.resources.kmplib_error_network
import br.com.codecacto.kmplib.generated.resources.kmplib_error_server
import br.com.codecacto.kmplib.generated.resources.kmplib_error_session_expired
import br.com.codecacto.kmplib.generated.resources.kmplib_error_too_many_attempts
import br.com.codecacto.kmplib.ui.locale.formatStatusTemplate
import kotlinx.coroutines.CancellationException
import org.jetbrains.compose.resources.getString

/**
 * [OwnAuthTexts] no **idioma da tela** (pt-BR, en, es, pt-PT), lido dos recursos da lib (2.219.0).
 *
 * É o que o own-auth usa quando o app não passa `texts` — não precisa chamar à mão. Existe público
 * para o app que quer só trocar uma frase mantendo o resto traduzido:
 * `OwnAuthConfig(…, texts = loadOwnAuthTexts().copy(invalidCredentials = …))` — lembrando que aí o
 * objeto fica congelado no idioma do momento da construção.
 *
 * Leitura que falha (teste de JVM sem recursos) devolve os defaults pt-BR: mensagem de erro não pode
 * ser a causa de outro erro. `socialNonceMissing` é erro de PROGRAMAÇÃO do app, não frase de tela, e
 * fica no default.
 */
suspend fun loadOwnAuthTexts(): OwnAuthTexts =
    try {
        val servidor = getString(Res.string.kmplib_error_server)
        OwnAuthTexts(
            invalidCredentials = getString(Res.string.kmplib_auth_invalid_credentials),
            emailAlreadyInUse = getString(Res.string.kmplib_auth_email_in_use),
            weakPassword = getString(Res.string.kmplib_auth_weak_password),
            invalidResetToken = getString(Res.string.kmplib_auth_invalid_reset),
            tooManyRequests = getString(Res.string.kmplib_error_too_many_attempts),
            network = getString(Res.string.kmplib_error_network),
            sessionExpired = getString(Res.string.kmplib_error_session_expired),
            socialRejected = getString(Res.string.kmplib_auth_social_rejected),
            server = { codigo -> formatStatusTemplate(servidor, codigo) },
            unsupported = getString(Res.string.kmplib_auth_unsupported),
            currentPasswordIncorrect = getString(Res.string.kmplib_auth_current_password_incorrect),
            profileRejected = getString(Res.string.kmplib_auth_profile_rejected),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        OwnAuthTexts()
    }

package br.com.codecacto.kmplib.ui.screens.register

import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.ui.components.FormPlaceholders
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_register_title
import br.com.codecacto.kmplib.generated.resources.kmplib_name_label
import br.com.codecacto.kmplib.generated.resources.kmplib_name_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_email_label
import br.com.codecacto.kmplib.generated.resources.kmplib_email_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_phone_label
import br.com.codecacto.kmplib.generated.resources.kmplib_password_label
import br.com.codecacto.kmplib.generated.resources.kmplib_password_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_confirm_password_label
import br.com.codecacto.kmplib.generated.resources.kmplib_confirm_password_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_register_button
import br.com.codecacto.kmplib.generated.resources.kmplib_login_prompt
import br.com.codecacto.kmplib.generated.resources.kmplib_login_link
import br.com.codecacto.kmplib.generated.resources.kmplib_or_continue_with
import br.com.codecacto.kmplib.generated.resources.kmplib_google_register
import br.com.codecacto.kmplib.generated.resources.kmplib_apple_register
import br.com.codecacto.kmplib.generated.resources.kmplib_register_terms_prefix
import br.com.codecacto.kmplib.generated.resources.kmplib_terms_of_use
import br.com.codecacto.kmplib.generated.resources.kmplib_and
import br.com.codecacto.kmplib.generated.resources.kmplib_privacy_policy
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Textos da `RegisterScreen` — lambdas `@Composable`, e cada default é um `stringResource` da lib
 * **no idioma do aparelho** (pt-BR, en, es, pt-PT) desde a 2.219.0. O app passa só o que quer trocar.
 *
 * [phonePlaceholder] continua o FORMATO brasileiro (`(00) 00000-0000`) porque o campo, por default,
 * é o telefone brasileiro (`RegisterFields.phoneMask`). App global que troca a máscara pela
 * internacional (`RegisterFields(phoneMask = PhoneInputFormat.forRegion(deviceRegion()).visualTransformation)`)
 * passa também `phonePlaceholder = { stringResource(Res.string.kmplib_phone_placeholder) }` — a
 * instrução "Digite seu telefone", no idioma do aparelho.
 */
data class RegisterTexts(
    val title: @Composable (() -> String)? = { kmpStringResource(Res.string.kmplib_register_title) },
    val nameLabel: @Composable () -> String = { kmpStringResource(Res.string.kmplib_name_label) },
    val namePlaceholder: @Composable () -> String = { kmpStringResource(Res.string.kmplib_name_placeholder) },
    val emailLabel: @Composable () -> String = { kmpStringResource(Res.string.kmplib_email_label) },
    val emailPlaceholder: @Composable () -> String = { kmpStringResource(Res.string.kmplib_email_placeholder) },
    val phoneLabel: @Composable () -> String = { kmpStringResource(Res.string.kmplib_phone_label) },
    val phonePlaceholder: @Composable () -> String = { FormPlaceholders.PHONE },
    val passwordLabel: @Composable () -> String = { kmpStringResource(Res.string.kmplib_password_label) },
    val passwordPlaceholder: @Composable () -> String = { kmpStringResource(Res.string.kmplib_password_placeholder) },
    val confirmPasswordLabel: @Composable () -> String = { kmpStringResource(Res.string.kmplib_confirm_password_label) },
    val confirmPasswordPlaceholder: @Composable () -> String = { kmpStringResource(Res.string.kmplib_confirm_password_placeholder) },
    val registerButton: @Composable () -> String = { kmpStringResource(Res.string.kmplib_register_button) },
    val loginPrompt: @Composable () -> String = { kmpStringResource(Res.string.kmplib_login_prompt) },
    val loginLink: @Composable () -> String = { kmpStringResource(Res.string.kmplib_login_link) },
    val orContinueWith: @Composable () -> String = { kmpStringResource(Res.string.kmplib_or_continue_with) },
    val googleRegister: @Composable () -> String = { kmpStringResource(Res.string.kmplib_google_register) },
    val appleRegister: @Composable () -> String = { kmpStringResource(Res.string.kmplib_apple_register) },
    // O espaço do fim fica no código: espaço na ponta de um recurso XML é frágil.
    val termsPrefix: @Composable () -> String = { kmpStringResource(Res.string.kmplib_register_terms_prefix) + " " },
    val termsText: @Composable () -> String = { kmpStringResource(Res.string.kmplib_terms_of_use) },
    /** A tela já põe um espaço de cada lado. */
    val andText: @Composable () -> String = { kmpStringResource(Res.string.kmplib_and) },
    val privacyText: @Composable () -> String = { kmpStringResource(Res.string.kmplib_privacy_policy) },
)

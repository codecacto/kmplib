package br.com.codecacto.kmplib.ui.screens.developer

import br.com.codecacto.kmplib.ui.components.FormPlaceholders
import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.mask.PhoneInputFormat
import br.com.codecacto.kmplib.ui.locale.rememberDevicePhoneInputFormat
import br.com.codecacto.kmplib.ui.locale.rememberPhonePlaceholder
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_title
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_subtitle
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_name_label
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_name_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_email_label
import br.com.codecacto.kmplib.generated.resources.kmplib_email_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_whatsapp_label
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_subject_label
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_subject_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_message_label
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_message_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_send
import br.com.codecacto.kmplib.generated.resources.kmplib_cancel
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_name_error
import br.com.codecacto.kmplib.generated.resources.kmplib_email_error
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_message_error
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_phone_error
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_error
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_success_title
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_success_message
import br.com.codecacto.kmplib.generated.resources.kmplib_back
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Textos customizáveis para a [ContactScreen] (formulário "Entrar em contato").
 *
 * Espelha os rótulos do `ContactForm` da weblib (paridade web/app). Sem `texts`, a tela usa
 * [rememberContactTexts] — idioma do aparelho (pt-BR, en, es, pt-PT, 2.219.0). Os defaults literais
 * são pt-BR, para uso fora da composição.
 */
data class ContactTexts(
    val title: String = "Entrar em contato",
    val subtitle: String = "Envie sua mensagem e retornaremos em breve.",
    val nameLabel: String = "Nome",
    val namePlaceholder: String = "Seu nome",
    val emailLabel: String = "E-mail",
    val emailPlaceholder: String = FormPlaceholders.EMAIL,
    val whatsappLabel: String = "WhatsApp (opcional)",
    val whatsappPlaceholder: String = FormPlaceholders.PHONE,
    val subjectLabel: String = "Assunto (opcional)",
    val subjectPlaceholder: String = "Sobre o que você quer falar",
    val messageLabel: String = "Mensagem",
    val messagePlaceholder: String = "Escreva sua mensagem…",
    val sendButton: String = "Enviar mensagem",
    val cancelButton: String = "Cancelar",
    val nameError: String = "Informe seu nome.",
    val emailError: String = "E-mail inválido.",
    val messageError: String = "Escreva sua mensagem.",
    val whatsappError: String = "Telefone inválido.",
    val errorMessage: String = "Não foi possível enviar agora. Tente novamente em instantes.",
    val successTitle: String = "Mensagem enviada!",
    val successMessage: String = "Em breve entraremos em contato.",
    val continueButton: String = "Voltar",
    val backContentDescription: String = "Voltar",
)

/** [ContactTexts] no idioma do aparelho; o placeholder do WhatsApp segue o [phoneFormat] do campo. */
@Composable
fun rememberContactTexts(phoneFormat: PhoneInputFormat = rememberDevicePhoneInputFormat()): ContactTexts =
    ContactTexts(
        title = kmpStringResource(Res.string.kmplib_contact_title),
        subtitle = kmpStringResource(Res.string.kmplib_contact_subtitle),
        nameLabel = kmpStringResource(Res.string.kmplib_contact_name_label),
        namePlaceholder = kmpStringResource(Res.string.kmplib_contact_name_placeholder),
        emailLabel = kmpStringResource(Res.string.kmplib_email_label),
        emailPlaceholder = kmpStringResource(Res.string.kmplib_email_placeholder),
        whatsappLabel = kmpStringResource(Res.string.kmplib_contact_whatsapp_label),
        whatsappPlaceholder = rememberPhonePlaceholder(phoneFormat),
        subjectLabel = kmpStringResource(Res.string.kmplib_contact_subject_label),
        subjectPlaceholder = kmpStringResource(Res.string.kmplib_contact_subject_placeholder),
        messageLabel = kmpStringResource(Res.string.kmplib_contact_message_label),
        messagePlaceholder = kmpStringResource(Res.string.kmplib_contact_message_placeholder),
        sendButton = kmpStringResource(Res.string.kmplib_contact_send),
        cancelButton = kmpStringResource(Res.string.kmplib_cancel),
        nameError = kmpStringResource(Res.string.kmplib_contact_name_error),
        emailError = kmpStringResource(Res.string.kmplib_email_error),
        messageError = kmpStringResource(Res.string.kmplib_contact_message_error),
        whatsappError = kmpStringResource(Res.string.kmplib_contact_phone_error),
        errorMessage = kmpStringResource(Res.string.kmplib_contact_error),
        successTitle = kmpStringResource(Res.string.kmplib_contact_success_title),
        successMessage = kmpStringResource(Res.string.kmplib_contact_success_message),
        continueButton = kmpStringResource(Res.string.kmplib_back),
        backContentDescription = kmpStringResource(Res.string.kmplib_back),
    )

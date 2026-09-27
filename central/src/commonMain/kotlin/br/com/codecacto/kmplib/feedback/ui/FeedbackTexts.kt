package br.com.codecacto.kmplib.ui.screens.feedback

import br.com.codecacto.kmplib.ui.components.FormPlaceholders
import androidx.compose.runtime.Composable
import br.com.codecacto.kmplib.mask.PhoneInputFormat
import br.com.codecacto.kmplib.ui.locale.rememberDevicePhoneInputFormat
import br.com.codecacto.kmplib.ui.locale.rememberPhonePlaceholder
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_title
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_subtitle
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_reason_label
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_reason_suggestion
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_reason_bug
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_reason_complaint
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_reason_question
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_reason_praise
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_reason_other
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_message_label
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_message_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_name_label
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_name_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_email_label
import br.com.codecacto.kmplib.generated.resources.kmplib_email_placeholder
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_whatsapp_label
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_submit
import br.com.codecacto.kmplib.generated.resources.kmplib_cancel
import br.com.codecacto.kmplib.generated.resources.kmplib_thanks
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_success
import br.com.codecacto.kmplib.generated.resources.kmplib_continue
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_error
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_reason_error
import br.com.codecacto.kmplib.generated.resources.kmplib_feedback_message_error
import br.com.codecacto.kmplib.generated.resources.kmplib_email_error
import br.com.codecacto.kmplib.generated.resources.kmplib_whatsapp_error
import br.com.codecacto.kmplib.generated.resources.kmplib_back
import org.jetbrains.compose.resources.stringResource

/**
 * Textos customizáveis para a FeedbackScreen.
 *
 * Sem `texts`, a tela usa [rememberFeedbackTexts] — os recursos da lib no idioma do aparelho
 * (pt-BR, en, es, pt-PT, 2.219.0). Os defaults literais desta classe são pt-BR, para quem monta o
 * objeto fora da composição; para trocar uma frase mantendo o resto traduzido:
 * `rememberFeedbackTexts().copy(title = …)`.
 */
data class FeedbackTexts(
    val title: String = "Deixe seu Feedback",
    val subtitle: String = "Sua opinião é importante para nós",
    val motivoLabel: String = "Qual o motivo do seu feedback?",
    val motivoSugestao: String = "Sugestão",
    val motivoBug: String = "Reportar Bug",
    val motivoReclamacao: String = "Reclamação",
    val motivoDuvida: String = "Dúvida",
    val motivoElogio: String = "Elogio",
    val motivoOutro: String = "Outro",
    val mensagemLabel: String = "Sua mensagem",
    val mensagemPlaceholder: String = "Descreva sua sugestão, problema ou dúvida...",
    val nomeLabel: String = "Seu nome (opcional)",
    val nomePlaceholder: String = "Como podemos te chamar?",
    val emailLabel: String = "Seu e-mail (opcional)",
    val emailPlaceholder: String = FormPlaceholders.EMAIL,
    val whatsappLabel: String = "Seu WhatsApp",
    val whatsappPlaceholder: String = FormPlaceholders.PHONE,
    val submitButton: String = "Enviar Feedback",
    val cancelButton: String = "Cancelar",
    val successTitle: String = "Obrigado!",
    val successMessage: String = "Seu feedback foi enviado com sucesso.",
    val continueButton: String = "Continuar",
    val errorMessage: String = "Erro ao enviar feedback. Tente novamente.",
    val motivoError: String = "Selecione um motivo",
    val mensagemError: String = "Digite sua mensagem",
    val emailError: String = "E-mail inválido",
    val whatsappError: String = "Informe um WhatsApp com 11 dígitos",
    val backContentDescription: String = "Voltar"
)

/**
 * [FeedbackTexts] no idioma do aparelho. O placeholder do WhatsApp segue o [phoneFormat] do campo:
 * formato brasileiro no modo BR, instrução no internacional.
 */
@Composable
fun rememberFeedbackTexts(phoneFormat: PhoneInputFormat = rememberDevicePhoneInputFormat()): FeedbackTexts =
    FeedbackTexts(
        title = stringResource(Res.string.kmplib_feedback_title),
        subtitle = stringResource(Res.string.kmplib_feedback_subtitle),
        motivoLabel = stringResource(Res.string.kmplib_feedback_reason_label),
        motivoSugestao = stringResource(Res.string.kmplib_feedback_reason_suggestion),
        motivoBug = stringResource(Res.string.kmplib_feedback_reason_bug),
        motivoReclamacao = stringResource(Res.string.kmplib_feedback_reason_complaint),
        motivoDuvida = stringResource(Res.string.kmplib_feedback_reason_question),
        motivoElogio = stringResource(Res.string.kmplib_feedback_reason_praise),
        motivoOutro = stringResource(Res.string.kmplib_feedback_reason_other),
        mensagemLabel = stringResource(Res.string.kmplib_feedback_message_label),
        mensagemPlaceholder = stringResource(Res.string.kmplib_feedback_message_placeholder),
        nomeLabel = stringResource(Res.string.kmplib_feedback_name_label),
        nomePlaceholder = stringResource(Res.string.kmplib_feedback_name_placeholder),
        emailLabel = stringResource(Res.string.kmplib_feedback_email_label),
        emailPlaceholder = stringResource(Res.string.kmplib_email_placeholder),
        whatsappLabel = stringResource(Res.string.kmplib_feedback_whatsapp_label),
        whatsappPlaceholder = rememberPhonePlaceholder(phoneFormat),
        submitButton = stringResource(Res.string.kmplib_feedback_submit),
        cancelButton = stringResource(Res.string.kmplib_cancel),
        successTitle = stringResource(Res.string.kmplib_thanks),
        successMessage = stringResource(Res.string.kmplib_feedback_success),
        continueButton = stringResource(Res.string.kmplib_continue),
        errorMessage = stringResource(Res.string.kmplib_feedback_error),
        motivoError = stringResource(Res.string.kmplib_feedback_reason_error),
        mensagemError = stringResource(Res.string.kmplib_feedback_message_error),
        emailError = stringResource(Res.string.kmplib_email_error),
        whatsappError = stringResource(Res.string.kmplib_whatsapp_error),
        backContentDescription = stringResource(Res.string.kmplib_back),
    )

/**
 * WhatsApp "completo" para os formulários da lib: no Brasil, **celular com DDD** (11 dígitos — a
 * regra de sempre, WhatsApp é celular); fora dele, telefone internacional plausível (E.164).
 */
internal fun PhoneInputFormat.isCompleteWhatsapp(value: String): Boolean =
    if (isBrazilian) value.length == 11 else isValid(value)

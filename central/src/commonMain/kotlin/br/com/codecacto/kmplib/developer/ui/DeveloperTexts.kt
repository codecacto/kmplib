package br.com.codecacto.kmplib.ui.screens.developer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import br.com.codecacto.kmplib.core.util.BuildInfo
import br.com.codecacto.kmplib.mask.PhoneInputFormat
import br.com.codecacto.kmplib.ui.locale.rememberDevicePhoneInputFormat
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_title
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_subtitle
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_slogan
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_contact_section
import br.com.codecacto.kmplib.generated.resources.kmplib_contact_title
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_whatsapp
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_email
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_site
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_whatsapp_message
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_whatsapp_message_from_app
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_email_subject
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_email_subject_from_app
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_apps_section
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_apps_empty
import br.com.codecacto.kmplib.generated.resources.kmplib_back
import org.jetbrains.compose.resources.stringResource

/**
 * Textos customizáveis para a [DeveloperScreen].
 *
 * Sem `texts`, a tela usa [rememberDeveloperTexts] — idioma do aparelho (pt-BR, en, es, pt-PT,
 * 2.219.0). Os defaults literais são pt-BR, para uso fora da composição. [brandName] não se traduz.
 */
data class DeveloperTexts(
    val title: String = "Desenvolvido por",
    val subtitle: String = "Conheça quem faz o app",
    val brandName: String = "CodeCacto",
    val brandSlogan: String = "Apps que facilitam o seu dia a dia.",
    val contactSectionTitle: String = "Fale com a gente",
    val contactButton: String = "Entrar em contato",
    val whatsappButton: String = "Conversar no WhatsApp",
    val emailButton: String = "Enviar e-mail",
    val siteButton: String = "Visitar site",
    val whatsappMessage: String = "Olá! Vim pelo app e gostaria de falar com vocês.",
    val emailSubject: String = "Contato via app",
    val appsSectionTitle: String = "Nossos apps",
    val appsEmpty: String = "Em breve novos apps por aqui.",
    val backContentDescription: String = "Voltar",
    /** Textos do formulário "Entrar em contato" ([ContactScreen]). */
    val contact: ContactTexts = ContactTexts(),
)

/**
 * [DeveloperTexts] no idioma do aparelho — inclusive o formulário de contato ([rememberContactTexts]).
 *
 * A mensagem do WhatsApp e o assunto do e-mail levam o nome do app ("Vim pelo app LocAki…", 2.230.0):
 * os dois chegam no MESMO contato da CodeCacto, vindos de todos os apps, e sem o nome não há como
 * saber de onde a pessoa veio. [appName] vem do sistema ([BuildInfo.appName]); sem ele, o texto
 * volta à forma genérica.
 */
@Composable
fun rememberDeveloperTexts(
    phoneFormat: PhoneInputFormat = rememberDevicePhoneInputFormat(),
    appName: String? = remember { BuildInfo.appName },
): DeveloperTexts =
    DeveloperTexts(
        title = stringResource(Res.string.kmplib_dev_title),
        subtitle = stringResource(Res.string.kmplib_dev_subtitle),
        brandSlogan = stringResource(Res.string.kmplib_dev_slogan),
        contactSectionTitle = stringResource(Res.string.kmplib_dev_contact_section),
        contactButton = stringResource(Res.string.kmplib_contact_title),
        whatsappButton = stringResource(Res.string.kmplib_dev_whatsapp),
        emailButton = stringResource(Res.string.kmplib_dev_email),
        siteButton = stringResource(Res.string.kmplib_dev_site),
        whatsappMessage = if (appName.isNullOrBlank()) {
            stringResource(Res.string.kmplib_dev_whatsapp_message)
        } else {
            stringResource(Res.string.kmplib_dev_whatsapp_message_from_app, appName)
        },
        emailSubject = if (appName.isNullOrBlank()) {
            stringResource(Res.string.kmplib_dev_email_subject)
        } else {
            stringResource(Res.string.kmplib_dev_email_subject_from_app, appName)
        },
        appsSectionTitle = stringResource(Res.string.kmplib_dev_apps_section),
        appsEmpty = stringResource(Res.string.kmplib_dev_apps_empty),
        backContentDescription = stringResource(Res.string.kmplib_back),
        contact = rememberContactTexts(phoneFormat),
    )

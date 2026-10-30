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
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_tagline
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
import br.com.codecacto.kmplib.generated.resources.kmplib_dev_apps_open
import br.com.codecacto.kmplib.generated.resources.kmplib_back
import br.com.codecacto.kmplib.ui.locale.kmpStringResource

/**
 * Textos customizáveis para a [DeveloperScreen].
 *
 * Sem `texts`, a tela usa [rememberDeveloperTexts] — idioma do aparelho (pt-BR, en, es, pt-PT,
 * 2.219.0). Os defaults literais são pt-BR, para uso fora da composição. [brandName] não se traduz.
 */
data class DeveloperTexts(
    val title: String = "Desenvolvido por",
    /** Sem uso na tela desde a 2.323.0 (a barra de topo é a padrão, só com o título). */
    val subtitle: String = "Conheça quem faz o app",
    val brandName: String = "CodeCacto",
    /** Slogan da marca, o mesmo do site e do `DevelopedByCredit` da weblib (2.336.0). */
    val brandSlogan: String = "Apps que resolvem.",
    /**
     * O parágrafo de venda logo abaixo do slogan: fazemos app e site sob medida (2.336.0). Em branco,
     * a tela não o mostra.
     */
    val brandTagline: String = "Criamos aplicativos e sites sob medida para a sua empresa. Você conta como " +
        "funciona o seu negócio e a gente entrega tudo pronto e fácil de usar.",
    /** Sem uso na tela desde a 2.323.0. */
    val contactSectionTitle: String = "Fale com a gente",
    /** Sem uso na tela desde a 2.323.0 (o formulário de contato saiu dela). */
    val contactButton: String = "Entrar em contato",
    val whatsappButton: String = "Conversar no WhatsApp",
    /** Sem uso na tela desde a 2.323.0 (o e-mail saiu dela). */
    val emailButton: String = "Enviar e-mail",
    val siteButton: String = "Visitar o site",
    val whatsappMessage: String = "Olá! Vim pelo app e gostaria de falar com vocês.",
    /** Sem uso na tela desde a 2.323.0. */
    val emailSubject: String = "Contato via app",
    val appsSectionTitle: String = "Nossos projetos",
    val appsEmpty: String = "Em breve, novos projetos por aqui.",
    val backContentDescription: String = "Voltar",
    /** Textos do formulário [ContactScreen] — sem uso nesta tela desde a 2.323.0. */
    val contact: ContactTexts = ContactTexts(),
    /** Ação do cartão de projeto para o leitor de tela ("toque duas vezes para abrir na loja"), 2.336.0. */
    val appsOpenLabel: String = "Abrir na loja",
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
        title = kmpStringResource(Res.string.kmplib_dev_title),
        subtitle = kmpStringResource(Res.string.kmplib_dev_subtitle),
        brandSlogan = kmpStringResource(Res.string.kmplib_dev_slogan),
        brandTagline = kmpStringResource(Res.string.kmplib_dev_tagline),
        contactSectionTitle = kmpStringResource(Res.string.kmplib_dev_contact_section),
        contactButton = kmpStringResource(Res.string.kmplib_contact_title),
        whatsappButton = kmpStringResource(Res.string.kmplib_dev_whatsapp),
        emailButton = kmpStringResource(Res.string.kmplib_dev_email),
        siteButton = kmpStringResource(Res.string.kmplib_dev_site),
        whatsappMessage = if (appName.isNullOrBlank()) {
            kmpStringResource(Res.string.kmplib_dev_whatsapp_message)
        } else {
            kmpStringResource(Res.string.kmplib_dev_whatsapp_message_from_app, appName)
        },
        emailSubject = if (appName.isNullOrBlank()) {
            kmpStringResource(Res.string.kmplib_dev_email_subject)
        } else {
            kmpStringResource(Res.string.kmplib_dev_email_subject_from_app, appName)
        },
        appsSectionTitle = kmpStringResource(Res.string.kmplib_dev_apps_section),
        appsEmpty = kmpStringResource(Res.string.kmplib_dev_apps_empty),
        backContentDescription = kmpStringResource(Res.string.kmplib_back),
        contact = rememberContactTexts(phoneFormat),
        appsOpenLabel = kmpStringResource(Res.string.kmplib_dev_apps_open),
    )

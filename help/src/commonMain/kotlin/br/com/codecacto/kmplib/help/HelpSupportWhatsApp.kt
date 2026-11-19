package br.com.codecacto.kmplib.help

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.core.config.CodeCactoContact
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.platform.whatsAppPhoneNumber
import br.com.codecacto.kmplib.ui.components.AppButton
import br.com.codecacto.kmplib.ui.components.WhatsAppColors
import br.com.codecacto.kmplib.ui.components.WhatsAppGlyph

/**
 * Suporte pelo WhatsApp na [HelpScreen] (2.375.0, opt-in). Ligado, o "Falar com o suporte" do rodapé da conversa e o
 * da bolha de sem resposta viram o botão de destaque "Chamar o suporte no WhatsApp", que abre a conversa já com a
 * mensagem pronta dizendo de onde a pessoa veio.
 *
 * ```kotlin
 * HelpScreen(
 *     config = helpConfig,
 *     onBack = { nav.popBackStack() },
 *     onSupport = { nav.navigate(Feedback) },          // continua obrigatório: é o caminho sem WhatsApp
 *     onOpenLibrary = { nav.navigate(Biblioteca) },
 *     supportWhatsApp = HelpSupportWhatsApp(productName = "LocAki"),
 * )
 * ```
 *
 * @property phone número do WhatsApp de suporte. Default: o da CodeCacto ([CodeCactoContact.WHATSAPP_PHONE]). Passa por
 *   [whatsAppPhoneNumber] (sem DDI ganha o 55). Número inválido = a tela volta ao botão antigo, com o `onSupport`.
 * @property message mensagem pronta; vence a default. Em branco = a default.
 * @property productName nome do produto na mensagem default ("Olá! Uso o LocAki e gostaria de falar com o suporte.").
 *   Sem ele: "Olá! Gostaria de falar com o suporte." — no idioma da tela (pt-BR, en, es, pt-PT).
 */
data class HelpSupportWhatsApp(
    val phone: String = CodeCactoContact.WHATSAPP_PHONE,
    val message: String? = null,
    val productName: String? = null,
) {
    /** O número que vai ao WhatsApp (só algarismos, com DDI), ou `null` se o [phone] não é um telefone válido. */
    val normalizedPhone: String? get() = whatsAppPhoneNumber(phone)

    /** A mensagem que já vem escrita na conversa: [message], ou a default de [texts] com o [productName]. */
    fun messageFor(texts: HelpTexts): String {
        message?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val product = productName?.trim().orEmpty()
        return if (product.isEmpty()) {
            texts.supportWhatsAppMessage
        } else {
            texts.supportWhatsAppMessageWithProductTemplate.replace(PRODUCT_PLACEHOLDER, product)
        }
    }

    private companion object {
        const val PRODUCT_PLACEHOLDER = "%1\$s"
    }
}

/**
 * O botão de destaque do suporte: verde-petróleo da marca WhatsApp (`#075E54`) com texto e glifo brancos — 7,67:1, o
 * MESMO desenho do botão de WhatsApp do comunicado (2.371.0, par da weblib 0.328.0). O verde `#25D366` com branco mede
 * 1,98:1 e reprova o AA, por isso não é o fundo. Largura toda, altura mínima 56 dp.
 *
 * Abre pelo `UrlLauncher.openWhatsApp` da lib, resolvido no toque — o portão de pais do app infantil e o dublê de
 * teste valem aqui também.
 */
@Composable
internal fun HelpSupportWhatsAppButton(
    support: HelpSupportWhatsApp,
    phone: String,
    texts: HelpTexts,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val message = remember(support, texts) { support.messageFor(texts) }
    AppButton(
        text = texts.supportWhatsApp,
        onClick = { getUrlLauncher().openWhatsApp(phone, message) },
        height = 56.dp,
        primaryColor = WhatsAppColors.DarkGreen,
        contentColor = Color.White,
        icon = WhatsAppGlyph,
        modifier = modifier.testTag(testTag),
    )
}

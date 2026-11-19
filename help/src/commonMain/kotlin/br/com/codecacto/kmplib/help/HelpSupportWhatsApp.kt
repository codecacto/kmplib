package br.com.codecacto.kmplib.help

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.codecacto.kmplib.core.config.CodeCactoContact
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.platform.whatsAppPhoneNumber
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
 * O botão de destaque do suporte (2.375.1, alinhado ao `SupportWhatsAppButton` da weblib 0.331.0): fundo
 * [WhatsAppColors.ActionGreen] (`#008069`, o verde dos botões do WhatsApp) com texto e glifo brancos — 4,9:1, AA — e
 * [WhatsAppColors.DarkGreen] (`#075E54`) enquanto pressionado. Largura toda, altura mínima 56 dp, cantos de 12 dp e
 * rótulo como o `AppButton` (glifo 20 dp decorativo + texto que quebra centralizado).
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Button(
        onClick = { getUrlLauncher().openWhatsApp(phone, message) },
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = helpSupportWhatsAppContainer(pressed),
            contentColor = Color.White,
        ),
        interactionSource = interactionSource,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Decorativo: quem lê o botão já ouviu o rótulo.
            Icon(imageVector = WhatsAppGlyph, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = texts.supportWhatsApp,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** Fundo do botão de suporte: `#008069` em repouso, `#075E54` pressionado. Pura. */
internal fun helpSupportWhatsAppContainer(pressed: Boolean): Color =
    if (pressed) WhatsAppColors.DarkGreen else WhatsAppColors.ActionGreen

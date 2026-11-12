package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_whatsapp_chat
import br.com.codecacto.kmplib.generated.resources.kmplib_whatsapp_chat_with
import br.com.codecacto.kmplib.platform.getUrlLauncher
import br.com.codecacto.kmplib.platform.whatsAppPhoneNumber
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import br.com.codecacto.kmplib.ui.theme.ColorContrast

/**
 * As cores oficiais da marca WhatsApp (guia de marca da Meta), as mesmas da weblib
 * (`WHATSAPP_GREEN`/`WHATSAPP_GREEN_DARK` em `src/ui/WhatsAppIcon.tsx`).
 */
object WhatsAppColors {
    /** Verde da marca, `#25D366`. Ícone sobre fundo escuro; sobre o branco mede só 1,98:1. */
    val Green: Color = Color(0xFF25D366)

    /** Verde-azulado da marca, `#128C7E` — ícone sobre fundo claro (4,1:1 no branco). */
    val TealGreen: Color = Color(0xFF128C7E)

    /** Verde-petróleo da marca, `#075E54` — fundo para texto branco (7,67:1). */
    val DarkGreen: Color = Color(0xFF075E54)
}

/**
 * Caminho do glifo OFICIAL do WhatsApp (balão com o telefone), viewBox 24×24 — o MESMO
 * `WHATSAPP_ICON_PATH` da weblib, com as flags dos arcos escritas por extenso (`0 0 1` em vez de
 * `01`) para não depender da tolerância do parser.
 */
const val WHATSAPP_ICON_PATH: String =
    "M17.472 14.382c-.297-.149-1.758-.867-2.03-.967-.273-.099-.471-.148-.67.15-.197.297-.767.966-.94 1.164-.173.199-.347.223-.644.075-.297-.15-1.255-.463-2.39-1.475-.883-.788-1.48-1.761-1.653-2.059-.173-.297-.018-.458.13-.606.134-.133.298-.347.446-.52.149-.174.198-.298.298-.497.099-.198.05-.371-.025-.52-.075-.149-.669-1.612-.916-2.207-.242-.579-.487-.5-.669-.51-.173-.008-.371-.01-.57-.01-.198 0-.52.074-.792.372-.272.297-1.04 1.016-1.04 2.479 0 1.462 1.065 2.875 1.213 3.074.149.198 2.096 3.2 5.077 4.487.709.306 1.262.489 1.694.625.712.227 1.36.195 1.871.118.571-.085 1.758-.719 2.006-1.413.248-.694.248-1.289.173-1.413-.074-.124-.272-.198-.57-.347" +
        "m-5.421 7.403h-.004a9.87 9.87 0 0 1 -5.031-1.378l-.361-.214-3.741.982.998-3.648-.235-.374" +
        "a9.86 9.86 0 0 1 -1.51-5.26c.001-5.45 4.436-9.884 9.888-9.884 2.64 0 5.122 1.03 6.988 2.898" +
        "a9.825 9.825 0 0 1 2.893 6.994c-.003 5.45-4.437 9.884-9.885 9.884" +
        "m8.413-18.297A11.815 11.815 0 0 0 12.05 0C5.495 0 .16 5.335.157 11.892c0 2.096.547 4.142 1.588 5.945L.057 24l6.305-1.654" +
        "a11.882 11.882 0 0 0 5.683 1.448h.005c6.554 0 11.89-5.335 11.893-11.893" +
        "a11.821 11.821 0 0 0 -3.48-8.413Z"

/**
 * O glifo oficial do WhatsApp como [ImageVector] (24 dp, preenchimento preto — a cor vem do `tint`
 * do `Icon`). Use ao lado de um rótulo ("WhatsApp") ou dentro do [WhatsAppIconButton].
 */
val WhatsAppGlyph: ImageVector by lazy {
    ImageVector.Builder(
        name = "WhatsApp",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    )
        .addPath(pathData = addPathNodes(WHATSAPP_ICON_PATH), fill = SolidColor(Color.Black))
        .build()
}

/**
 * A cor do ícone do WhatsApp sobre [background]: o verde da marca quando ele tem contraste de
 * objeto gráfico (≥ 3:1, WCAG 1.4.11) — fundo escuro —; senão o verde-azulado da marca (fundo
 * claro); em último caso o verde-petróleo. Sempre uma cor da paleta oficial. Pura.
 */
fun whatsAppIconTint(background: Color): Color = when {
    ColorContrast.contrastRatio(WhatsAppColors.Green, background) >= MIN_GRAPHIC_CONTRAST -> WhatsAppColors.Green
    ColorContrast.contrastRatio(WhatsAppColors.TealGreen, background) >= MIN_GRAPHIC_CONTRAST -> WhatsAppColors.TealGreen
    else -> WhatsAppColors.DarkGreen
}

private const val MIN_GRAPHIC_CONTRAST = 3.0

/** Padrões do [WhatsAppIconButton]. */
object WhatsAppIconButtonDefaults {
    /** `testTag` padrão do botão. Em lista, prefira `"$TEST_TAG-<id do cliente>"`. */
    const val TEST_TAG: String = "whatsapp-btn-conversar"

    /** Tamanho do glifo (o alvo de toque continua 48 dp, do `IconButton`). */
    val IconSize: Dp = 24.dp

    /** Cor do ícone pelo fundo do tema ([whatsAppIconTint] sobre `colorScheme.surface`). */
    @Composable
    fun tint(): Color {
        val surface = MaterialTheme.colorScheme.surface
        return remember(surface) { whatsAppIconTint(surface) }
    }
}

/**
 * Botão com o ícone do WhatsApp que abre a conversa com o cliente — o padrão de toda listagem e
 * todo cadastro/detalhe de cliente (fundador, 11/nov/2026).
 *
 * - O número passa por [whatsAppPhoneNumber]: sem DDI ganha o 55, máscara sai. **Telefone vazio ou
 *   inválido não desenha nada** — o layout não reserva espaço.
 * - Toque → `getUrlLauncher().openWhatsApp(numero, message)` (resolvido no toque, então o portão de
 *   pais do app infantil e o dublê de teste valem). [onOpen] troca o destino (teste, protótipo).
 * - Nome acessível: "Conversar no WhatsApp com {contactName}" (4 idiomas); sem nome, "Conversar no
 *   WhatsApp".
 * - Dentro de uma linha clicável inteira (`Modifier.clickable` no cartão), o `IconButton` CONSOME o
 *   próprio toque: o toque no ícone abre o WhatsApp e NÃO abre o detalhe; o toque no resto da linha
 *   continua abrindo o detalhe.
 *
 * ```kotlin
 * ListItem(
 *     modifier = Modifier.clickable { abrir(cliente.id) },
 *     headlineContent = { Text(cliente.nome) },
 *     trailingContent = {
 *         WhatsAppIconButton(
 *             phone = cliente.telefone,
 *             contactName = cliente.nome,
 *             testTag = "${WhatsAppIconButtonDefaults.TEST_TAG}-${cliente.id}",
 *         )
 *     },
 * )
 * ```
 *
 * @param phone telefone como está no banco ("65999998888", "(65) 99999-8888", "+55…").
 * @param contactName nome do cliente, para o leitor de tela.
 * @param message texto que já vem escrito na conversa (o usuário edita antes de enviar).
 * @param tint cor do glifo. Default: verde da marca com contraste sobre o fundo do tema.
 * @param testTag id para o Maestro; único por linha em lista.
 * @param onOpen o que fazer com o número normalizado. Default: abre o WhatsApp.
 */
@Composable
fun WhatsAppIconButton(
    phone: String?,
    contactName: String? = null,
    message: String = "",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = WhatsAppIconButtonDefaults.tint(),
    iconSize: Dp = WhatsAppIconButtonDefaults.IconSize,
    testTag: String = WhatsAppIconButtonDefaults.TEST_TAG,
    onOpen: (phone: String, message: String) -> Unit = { number, text ->
        getUrlLauncher().openWhatsApp(number, text)
    },
) {
    val number = remember(phone) { whatsAppPhoneNumber(phone) } ?: return
    val name = contactName?.trim().orEmpty()
    val description = if (name.isEmpty()) {
        kmpStringResource(Res.string.kmplib_whatsapp_chat)
    } else {
        kmpStringResource(Res.string.kmplib_whatsapp_chat_with, name)
    }
    IconButton(
        onClick = { onOpen(number, message) },
        modifier = modifier.testTag(testTag),
        enabled = enabled,
    ) {
        Icon(
            imageVector = WhatsAppGlyph,
            contentDescription = description,
            tint = if (enabled) tint else tint.copy(alpha = DISABLED_ALPHA),
            modifier = Modifier.size(iconSize),
        )
    }
}

private const val DISABLED_ALPHA = 0.38f

package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.ColorContrast

/**
 * Ênfase visual do [AppBanner].
 *
 * - [SOLID] — fundo **preenchido** com a cor do tom e texto de contraste. Máxima visibilidade; é o
 *   padrão do ecossistema para **erro** (memória `error-banner-solid-standard`) e para confirmações
 *   que precisam ser lidas em 1 olhar.
 * - [SOFT] — fundo tingido (12%) + borda no tom + texto do tema. Aviso inline que informa sem
 *   dominar a tela.
 */
enum class BannerStyle { SOLID, SOFT }

/** Textos do [AppBanner] (i18n; defaults pt-BR). */
data class AppBannerTexts(
    val dismissLabel: String = "Fechar aviso",
)

/**
 * Estilo padrão por tom, espelhando o `Banner` da weblib (`@codecacto/weblib/ui`):
 * **erro é sólido** (desde a 0.67.0 — o antigo vermelho claro passava despercebido), os demais tons
 * são suaves. Lógica pura — testável.
 *
 * O app pode forçar [BannerStyle.SOLID] em qualquer tom quando o banner É o resultado da tela (ex.:
 * "Tudo certo!" ao fim de uma conferência).
 */
fun defaultBannerStyle(tone: StatusTone): BannerStyle =
    if (tone == StatusTone.DANGER) BannerStyle.SOLID else BannerStyle.SOFT

/**
 * Ícone padrão por tom, com o mesmo pareamento da weblib
 * (`info`→Info, `success`→CheckCircle, `warning`→triângulo de alerta, `error`→círculo de erro).
 * Lógica pura — testável.
 */
fun bannerDefaultIcon(tone: StatusTone): ImageVector = when (tone) {
    StatusTone.SUCCESS -> Icons.Filled.CheckCircle
    StatusTone.WARNING -> Icons.Filled.Warning
    StatusTone.DANGER -> Icons.Filled.Error
    StatusTone.INFO, StatusTone.NEUTRAL -> Icons.Outlined.Info
}

/**
 * Região viva do leitor de tela: **assertiva** para erro (interrompe e anuncia na hora — equivalente
 * ao `role="alert"` da weblib) e **polida** para os demais (`role="status"`). Lógica pura — testável.
 */
fun bannerLiveRegion(tone: StatusTone): LiveRegionMode =
    if (tone == StatusTone.DANGER) LiveRegionMode.Assertive else LiveRegionMode.Polite

/**
 * **Banner full-width com ícone + título + mensagem** — o par mobile do `Banner` da weblib
 * (`@codecacto/weblib/ui`), fechando a paridade visual e semântica web ↔ app do padrão do
 * ecossistema "aviso inline por tom, **erro = sólido**".
 *
 * ### Onde ele entra (fronteiras deliberadas)
 * - [StatusBadge] = pill pequeno que rotula **um item** ("Ativo").
 * - `OfflineBanner`/`ConnectivityGate` = específicos de **conectividade**.
 * - [ErrorState] = falha de **carregamento**, ocupa o corpo da tela, tem "Tentar novamente".
 * - [ErrorModal] = diálogo **bloqueante** para falha de ação.
 * - **[AppBanner]** = faixa de **resultado/aviso** no fluxo da tela, que informa (e opcionalmente
 *   oferece uma ação) sem bloquear: "Tudo certo!", "2 crianças não desceram", "E-mail não
 *   verificado", "Modo offline: alterações serão enviadas depois".
 *
 * ### Paridade de tom com a weblib
 * | weblib `variant` | kmplib [StatusTone] |
 * |---|---|
 * | `info` | [StatusTone.INFO] |
 * | `success` | [StatusTone.SUCCESS] |
 * | `warning` | [StatusTone.WARNING] |
 * | `error` | [StatusTone.DANGER] |
 *
 * O nome `DANGER` é o que o kmplib já usa em [StatusBadge] — manter dois vocabulários dentro do app
 * seria pior que a diferença de rótulo entre as plataformas. [StatusTone.NEUTRAL] é extra do mobile
 * (aviso cinza, sem carga semântica).
 *
 * ### Cor e contraste (padrão-ouro)
 * Tudo sai de tokens ([statusToneColor]) — zero cor hardcoded. No estilo [BannerStyle.SOLID] o texto
 * usa o par oficial do `ColorScheme` quando ele existe (`error`/`onError`) e, para os tons sem par no
 * Material (`success`/`warning`/`info`), é **derivado por contraste WCAG** ([ColorContrast.pickOnColor])
 * — assim um âmbar sólido nunca recebe texto branco ilegível, em nenhuma paleta de app.
 *
 * ```kotlin
 * AppBanner(
 *     tone = if (pendentes == 0) StatusTone.SUCCESS else StatusTone.DANGER,
 *     style = BannerStyle.SOLID,
 *     title = if (pendentes == 0) "Tudo certo!" else "Atenção!",
 *     message = if (pendentes == 0) "Todas as $total crianças desceram do veículo."
 *               else "$pendentes crianças não desceram do veículo.",
 * )
 * ```
 *
 * @param message corpo do aviso (obrigatório — um banner sem mensagem não comunica nada).
 * @param tone tom semântico (default [StatusTone.INFO]).
 * @param title título curto opcional, em negrito, acima da mensagem.
 * @param style ênfase; por padrão [defaultBannerStyle] (erro sólido, demais suaves).
 * @param icon ícone à esquerda; `null` esconde (mesmo contrato do `icon={null}` da weblib).
 * @param onDismiss quando não-nulo, exibe o "×" de fechar.
 * @param containerColor sobrescreve a cor de fundo (use tokens do tema).
 * @param contentColor sobrescreve a cor do conteúdo (use tokens do tema).
 * @param texts textos i18n (rótulo acessível do botão fechar).
 * @param action slot opcional à direita (ex.: `TextButton("Reenviar")`) — mesmo papel do `action` da
 *   weblib. Para uma ação de risco, mantenha a confirmação (`ConfirmationDialog`) na tela.
 */
@Composable
fun AppBanner(
    message: String,
    modifier: Modifier = Modifier,
    tone: StatusTone = StatusTone.INFO,
    title: String? = null,
    style: BannerStyle = defaultBannerStyle(tone),
    icon: ImageVector? = bannerDefaultIcon(tone),
    onDismiss: (() -> Unit)? = null,
    containerColor: Color? = null,
    contentColor: Color? = null,
    texts: AppBannerTexts = AppBannerTexts(),
    action: (@Composable () -> Unit)? = null,
) {
    val toneColor = statusToneColor(tone)
    val scheme = MaterialTheme.colorScheme

    val resolvedContainer: Color = containerColor ?: when (style) {
        BannerStyle.SOLID -> toneColor
        BannerStyle.SOFT -> toneColor.copy(alpha = BANNER_SOFT_CONTAINER_ALPHA)
    }

    // SOLID: par oficial do ColorScheme quando existe (error/onError); senão, contraste WCAG.
    // SOFT: texto do tema sobre a superfície tingida.
    val resolvedContent: Color = contentColor ?: when (style) {
        BannerStyle.SOLID -> if (tone == StatusTone.DANGER) scheme.onError else {
            ColorContrast.pickOnColor(toneColor)
        }
        BannerStyle.SOFT -> scheme.onSurface
    }
    val accent: Color = if (style == BannerStyle.SOLID) resolvedContent else toneColor
    val messageColor: Color =
        if (style == BannerStyle.SOLID) resolvedContent else scheme.onSurfaceVariant

    val liveRegionMode = bannerLiveRegion(tone)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = liveRegionMode },
        shape = MaterialTheme.shapes.medium,
        color = resolvedContainer,
        contentColor = resolvedContent,
        border = if (style == BannerStyle.SOFT) {
            BorderStroke(1.dp, toneColor.copy(alpha = BANNER_SOFT_BORDER_ALPHA))
        } else {
            null
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 16.dp,
                    end = if (onDismiss != null) 4.dp else 16.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null, // decorativo: o texto já comunica
                    tint = accent,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(22.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (!title.isNullOrBlank()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = messageColor,
                )
            }

            action?.invoke()

            if (onDismiss != null) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = texts.dismissLabel,
                        tint = resolvedContent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** Opacidade do fundo tingido no estilo [BannerStyle.SOFT]. */
const val BANNER_SOFT_CONTAINER_ALPHA: Float = 0.12f

/** Opacidade da borda no estilo [BannerStyle.SOFT]. */
const val BANNER_SOFT_BORDER_ALPHA: Float = 0.40f

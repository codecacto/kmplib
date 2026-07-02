package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.LocalHighContrast
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact

/**
 * Tom (semântica) do [CommunicationTile], mapeado a tokens de cor do tema (zero hardcode).
 *
 * Todos os tons são **PREENCHIDOS** (cor sólida/tonal, não superfície neutra) para não virarem uma
 * "parede" branca/preta sem cor:
 * - [Normal]: preenchimento SÓLIDO na cor de destaque — par `colorScheme.primary`/`onPrimary`
 *   (a maioria dos pictogramas de comunicação; era neutro/`surface`, agora é colorido).
 * - [Quick]: tonal mais claro e harmônico — par `colorScheme.secondaryContainer`/`onSecondaryContainer`
 *   ("respostas rápidas": Sim/Não/Obrigado — distinto sem competir com o Normal).
 * - [Alert]: faixa urgente vermelha — par coeso `colorScheme.error`/`onError` (ex.: "Dor", "Ajuda", "Banheiro").
 */
enum class TileTone {
    Normal,
    Quick,
    Alert
}

/**
 * Papéis de token de cor (do [ColorScheme]) que um [TileTone] usa. É a **fonte de verdade testável**
 * do mapeamento tom → token, sem depender de cores concretas.
 */
enum class TileColorRole {
    Primary,
    OnPrimary,
    SecondaryContainer,
    OnSecondaryContainer,
    Error,
    OnError
}

/**
 * Mapeia um [TileTone] ao par (container, conteúdo) de [TileColorRole]. Todos os tons são
 * preenchidos — nenhum usa `surface`/`onSurface`. Função pura (testável).
 */
fun communicationTileRoles(tone: TileTone): Pair<TileColorRole, TileColorRole> = when (tone) {
    TileTone.Normal -> TileColorRole.Primary to TileColorRole.OnPrimary
    TileTone.Quick -> TileColorRole.SecondaryContainer to TileColorRole.OnSecondaryContainer
    TileTone.Alert -> TileColorRole.Error to TileColorRole.OnError
}

/**
 * Largura da borda do tile em função do alto contraste. Em **alto contraste** ([highContrast] `true`)
 * desenha uma **borda grossa** ([HIGH_CONTRAST_TILE_BORDER], cor `outline`) em TODOS os tons — no HC
 * o `outline` é preto puro, dando o "peso" visível de alto contraste. Fora do HC, sem borda (`0.dp` →
 * tile só preenchido). Função pura (testável).
 */
fun communicationTileBorderWidth(highContrast: Boolean): Dp =
    if (highContrast) HIGH_CONTRAST_TILE_BORDER else 0.dp

/** Espessura da borda de alto contraste do [CommunicationTile]. */
val HIGH_CONTRAST_TILE_BORDER: Dp = 3.dp

private fun ColorScheme.tileColor(role: TileColorRole): Color = when (role) {
    TileColorRole.Primary -> primary
    TileColorRole.OnPrimary -> onPrimary
    TileColorRole.SecondaryContainer -> secondaryContainer
    TileColorRole.OnSecondaryContainer -> onSecondaryContainer
    TileColorRole.Error -> error
    TileColorRole.OnError -> onError
}

/**
 * Alvo de comunicação acessível: **pictograma + texto**, tile inteiro clicável, com haptic e
 * rótulo para leitor de tela. É a célula típica do [DensityGrid] numa prancha de CAA, mas serve
 * qualquer app "board/launcher" acessível.
 *
 * Acessibilidade (requisitos de 1ª classe):
 * - **Alvo = o tile inteiro** ([Modifier.clickable] com [Role.Button]); não só o ícone.
 * - **Rótulo unificado**: `contentDescription = label` (o leitor de tela anuncia só o rótulo, não
 *   "ícone + texto" duplicado) via [clearAndSetSemantics].
 * - **Haptic** no toque ([LocalHapticFeedback]).
 * - **Pictograma e texto sempre visíveis** (nunca só o ícone).
 * - **Alvo generoso**: altura mínima confortável (≥ 96dp), cresce no modo 1 coluna (largura cheia)
 *   e ganha um passo extra de altura/tamanho de fonte quando compacto para toque de baixa visão.
 * - **Alto contraste**: quando [LocalHighContrast] é `true`, ganha uma **borda grossa** (`outline`)
 *   em todos os tons — combinado com o `ColorScheme` de alto contraste (Normal vira quase-preto com
 *   texto branco), a diferença fica inconfundível.
 *
 * Tons **preenchidos** (cor sólida/tonal), nunca superfície neutra: Normal = `primary`/`onPrimary`
 * (sólido colorido), Quick = `secondaryContainer`/`onSecondaryContainer` (tonal), Alert =
 * `error`/`onError` (vermelho). Cores 100% por tokens ([MaterialTheme.colorScheme]). Nenhuma cor hardcoded.
 *
 * @param label texto do botão (também o `contentDescription`).
 * @param icon pictograma (ImageVector) exibido acima do texto.
 * @param onClick ação ao tocar.
 * @param modifier modificador do tile.
 * @param tone semântica visual ([TileTone]).
 * @param enabled quando `false`, o tile fica esmaecido e não responde ao toque.
 */
@Composable
fun CommunicationTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: TileTone = TileTone.Normal,
    enabled: Boolean = true,
) {
    val colorScheme = MaterialTheme.colorScheme
    val isCompact = LocalIsCompact.current
    val highContrast = LocalHighContrast.current
    val haptic = LocalHapticFeedback.current

    val (containerRole, contentRole) = communicationTileRoles(tone)
    val container: Color = colorScheme.tileColor(containerRole)
    val content: Color = colorScheme.tileColor(contentRole)
    val borderWidth = communicationTileBorderWidth(highContrast)
    val border: BorderStroke? =
        if (borderWidth > 0.dp) BorderStroke(borderWidth, colorScheme.outline) else null

    val minHeight = if (isCompact) 112.dp else 96.dp
    val iconSize = if (isCompact) 44.dp else 40.dp

    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(20.dp),
        border = border,
        tonalElevation = 0.dp,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .alpha(if (enabled) 1f else 0.45f)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .clearAndSetSemantics { contentDescription = label },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = content,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = content,
            )
        }
    }
}

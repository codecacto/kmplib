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
import androidx.compose.ui.unit.dp
import br.com.codecacto.kmplib.ui.theme.LocalIsCompact

/**
 * Tom (semântica) do [CommunicationTile], mapeado a tokens de cor do tema (zero hardcode).
 *
 * - [Normal]: superfície padrão — a maioria dos pictogramas de comunicação.
 * - [Quick]: destaque secundário — "respostas rápidas" (Sim/Não/Obrigado).
 * - [Alert]: faixa urgente — par coeso `colorScheme.error`/`onError` (ex.: "Dor", "Ajuda", "Banheiro").
 */
enum class TileTone {
    Normal,
    Quick,
    Alert
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
 *
 * Cores 100% por tokens ([MaterialTheme.colorScheme]). Nenhuma cor hardcoded.
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
    val haptic = LocalHapticFeedback.current

    val container: Color
    val content: Color
    val border: BorderStroke?
    when (tone) {
        TileTone.Normal -> {
            container = colorScheme.surface
            content = colorScheme.onSurface
            border = BorderStroke(1.dp, colorScheme.outline)
        }
        TileTone.Quick -> {
            container = colorScheme.secondaryContainer
            content = colorScheme.onSecondaryContainer
            border = null
        }
        TileTone.Alert -> {
            container = colorScheme.error
            content = colorScheme.onError
            border = null
        }
    }

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

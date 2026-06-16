package br.com.codecacto.kmplib.ui.components

import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.com.codecacto.kmplib.ui.theme.AppTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * FloatingActionButton temático padrão da plataforma (GAP-EMP-M-01 / GAP-MOS-M-01).
 *
 * Cores vêm do tema ([MaterialTheme.colorScheme]); por padrão `primary`/`onPrimary`, alinhado ao
 * branding do app via [AppTheme]. Suporta duas variantes:
 *  - **Circular** (padrão): só o ícone.
 *  - **Estendida** (label): ícone + texto, quando [label] é informado e [extended] é `true`.
 *
 * Para [enabled] `false`, o FAB fica esmaecido e ignora cliques (Material 3 não tem estado
 * desabilitado nativo para FAB, então aplicamos `alpha` e suprimimos o `onClick`).
 *
 * ```kotlin
 * // Circular
 * AppFab(onClick = { addItem() }, icon = Icons.Default.Add, contentDescription = "Adicionar")
 *
 * // Estendida (rótulo visível)
 * AppFab(
 *     onClick = { addItem() },
 *     icon = Icons.Default.Add,
 *     contentDescription = "Novo empréstimo",
 *     label = "Novo empréstimo",
 * )
 * ```
 *
 * @param onClick Ação ao tocar no FAB.
 * @param icon Ícone exibido.
 * @param contentDescription Descrição para acessibilidade (obrigatória).
 * @param modifier Modificador externo.
 * @param label Texto opcional; quando não nulo e [extended] `true`, renderiza a variante estendida.
 * @param extended Força/inibe a variante estendida. Padrão: estendida sempre que houver [label].
 * @param enabled Quando `false`, esmaece e desativa o clique. Padrão `true`.
 * @param containerColor Cor de fundo. Padrão: `primary` do tema.
 * @param contentColor Cor do ícone/label. Padrão: `onPrimary` do tema.
 */
@Composable
fun AppFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    extended: Boolean = label != null,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val safeOnClick = if (enabled) onClick else ({})
    val stateModifier = modifier
        .alpha(if (enabled) 1f else 0.38f)
        .semantics { this.contentDescription = contentDescription }

    if (extended && label != null) {
        ExtendedFloatingActionButton(
            onClick = safeOnClick,
            modifier = stateModifier,
            containerColor = containerColor,
            contentColor = contentColor,
            icon = { Icon(imageVector = icon, contentDescription = null) },
            text = { Text(text = label) },
        )
    } else {
        FloatingActionButton(
            onClick = safeOnClick,
            modifier = stateModifier,
            containerColor = containerColor,
            contentColor = contentColor,
        ) {
            Icon(imageVector = icon, contentDescription = null)
        }
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun AppFabPreview() {
    AppTheme {
        AppFab(onClick = {}, icon = Icons.Default.Add, contentDescription = "Adicionar")
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun AppFabExtendedPreview() {
    AppTheme {
        AppFab(
            onClick = {},
            icon = Icons.Default.Add,
            contentDescription = "Novo item",
            label = "Novo item",
        )
    }
}

@Suppress("DEPRECATION")
@Preview
@Composable
private fun AppFabDisabledPreview() {
    AppTheme {
        AppFab(
            onClick = {},
            icon = Icons.Default.Add,
            contentDescription = "Adicionar",
            enabled = false,
        )
    }
}

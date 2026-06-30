package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * Secao "Precisa de ajuda?" reutilizavel para telas de pagamento/assinatura.
 *
 * Card com titulo, descricao e um [OutlinedButton] que leva o usuario ao canal de suporte
 * (tipicamente a tela "Desenvolvido por CodeCacto" / contato). Mora em `ui/components` porque
 * serve qualquer tela de monetizacao (paywall, gerenciamento de assinatura, etc.).
 *
 * Tema 100% via [MaterialTheme] — sem cores hardcoded.
 *
 * @param title Titulo (ex.: "Precisa de ajuda?").
 * @param description Descricao curta.
 * @param buttonText Rotulo do botao.
 * @param onOpenDeveloper Callback do botao (abrir suporte/desenvolvedor).
 * @param modifier Modificador externo.
 */
@Composable
fun NeedHelpSection(
    title: String,
    description: String,
    buttonText: String,
    onOpenDeveloper: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onOpenDeveloper,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(text = buttonText, fontWeight = FontWeight.Medium)
            }
        }
    }
}

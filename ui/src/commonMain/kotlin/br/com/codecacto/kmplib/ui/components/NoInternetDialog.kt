package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.platform.automation.exposeTestTagsAsResourceId
import br.com.codecacto.kmplib.platform.automation.DialogTestTags
import androidx.compose.ui.platform.testTag
import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_offline_short
import br.com.codecacto.kmplib.generated.resources.kmplib_no_internet_message
import br.com.codecacto.kmplib.generated.resources.kmplib_got_it
import br.com.codecacto.kmplib.ui.locale.kmpStringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/**
 * Dialog informando que nao ha conexao com a internet.
 *
 * @param onDismiss Callback ao fechar o dialog
 * @param title Titulo do dialog
 * @param message Mensagem descritiva
 * @param buttonText Texto do botao
 * @param iconTint Cor do icone
 */
@Composable
fun NoInternetDialog(
    onDismiss: () -> Unit,
    title: String = kmpStringResource(Res.string.kmplib_offline_short),
    message: String = kmpStringResource(Res.string.kmplib_no_internet_message),
    buttonText: String = kmpStringResource(Res.string.kmplib_got_it),
    iconTint: Color = MaterialTheme.colorScheme.error,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            // Outra janela: religa o `testTagsAsResourceId` da raiz (ver `DialogTestTags`).
            modifier = Modifier.exposeTestTagsAsResourceId(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .testTag(DialogTestTags.CONTAINER)
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.WifiOff,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(48.dp),
                )

                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag(DialogTestTags.TITULO),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = message,
                    fontSize = 14.sp,
                    modifier = Modifier.testTag(DialogTestTags.MENSAGEM),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().testTag(DialogTestTags.BTN_CONFIRMAR),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text(
                        text = buttonText,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

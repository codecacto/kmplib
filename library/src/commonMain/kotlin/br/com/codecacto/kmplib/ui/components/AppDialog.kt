package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog genérico customizável
 *
 * @param show Se o dialog está visível
 * @param onDismiss Callback ao fechar o dialog
 * @param modifier Modificador customizado
 * @param title Título do dialog (opcional)
 * @param icon Ícone do dialog (opcional)
 * @param dismissOnClickOutside Se permite fechar ao clicar fora
 * @param dismissOnBackPress Se permite fechar com botão voltar
 * @param content Conteúdo principal do dialog
 */
@Composable
fun AppDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = null,
    dismissOnClickOutside: Boolean = true,
    dismissOnBackPress: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    if (show) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnClickOutside = dismissOnClickOutside,
                dismissOnBackPress = dismissOnBackPress
            )
        ) {
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Ícone (opcional)
                    icon?.let {
                        Icon(
                            imageVector = it,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Título (opcional)
                    title?.let {
                        Text(
                            text = it,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Conteúdo customizado
                    content()
                }
            }
        }
    }
}

/**
 * Dialog com botões de ação
 *
 * @param show Se o dialog está visível
 * @param onDismiss Callback ao fechar o dialog
 * @param title Título do dialog
 * @param message Mensagem do dialog
 * @param modifier Modificador customizado
 * @param icon Ícone do dialog (opcional)
 * @param confirmText Texto do botão de confirmar
 * @param dismissText Texto do botão de cancelar (null = sem botão)
 * @param onConfirm Callback ao confirmar
 * @param isLoading Se está em estado de loading
 * @param confirmButtonColor Cor do botão de confirmar
 * @param dismissOnClickOutside Se permite fechar ao clicar fora
 */
@Composable
fun AppAlertDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    confirmText: String = "OK",
    dismissText: String? = "Cancelar",
    onConfirm: () -> Unit = onDismiss,
    isLoading: Boolean = false,
    confirmButtonColor: Color = Color(0xFF6C63FF),
    dismissOnClickOutside: Boolean = true
) {
    AppDialog(
        show = show,
        onDismiss = onDismiss,
        title = title,
        icon = icon,
        dismissOnClickOutside = dismissOnClickOutside && !isLoading,
        dismissOnBackPress = !isLoading,
        modifier = modifier
    ) {
        // Mensagem
        Text(
            text = message,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Botões de ação
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Botão cancelar (opcional)
            dismissText?.let {
                AppOutlinedButton(
                    text = it,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    primaryColor = Color.Gray,
                    height = 48.dp
                )
            }

            // Botão confirmar
            AppButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                isLoading = isLoading,
                primaryColor = confirmButtonColor,
                height = 48.dp
            )
        }
    }
}

/**
 * Dialog com input de texto
 *
 * @param show Se o dialog está visível
 * @param onDismiss Callback ao fechar o dialog
 * @param title Título do dialog
 * @param textFieldValue Valor do campo de texto
 * @param onTextFieldValueChange Callback quando o valor do texto muda
 * @param modifier Modificador customizado
 * @param message Mensagem do dialog (opcional)
 * @param textFieldLabel Label do campo de texto
 * @param textFieldPlaceholder Placeholder do campo de texto
 * @param textFieldError Erro do campo de texto (null = sem erro)
 * @param confirmText Texto do botão de confirmar
 * @param dismissText Texto do botão de cancelar
 * @param onConfirm Callback ao confirmar
 * @param isLoading Se está em estado de loading
 */
@Composable
fun AppInputDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    title: String,
    textFieldValue: String,
    onTextFieldValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    textFieldLabel: String = "",
    textFieldPlaceholder: String = "",
    textFieldError: String? = null,
    confirmText: String = "Confirmar",
    dismissText: String = "Cancelar",
    onConfirm: () -> Unit,
    isLoading: Boolean = false
) {
    AppDialog(
        show = show,
        onDismiss = onDismiss,
        title = title,
        dismissOnClickOutside = !isLoading,
        dismissOnBackPress = !isLoading,
        modifier = modifier
    ) {
        // Mensagem (opcional)
        message?.let {
            Text(
                text = it,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Campo de texto
        AppTextField(
            value = textFieldValue,
            onValueChange = onTextFieldValueChange,
            label = textFieldLabel,
            placeholder = textFieldPlaceholder,
            errorMessage = textFieldError,
            enabled = !isLoading
        )

        // Botões de ação
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppOutlinedButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                enabled = !isLoading,
                primaryColor = Color.Gray,
                height = 48.dp
            )

            AppButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                isLoading = isLoading,
                height = 48.dp
            )
        }
    }
}

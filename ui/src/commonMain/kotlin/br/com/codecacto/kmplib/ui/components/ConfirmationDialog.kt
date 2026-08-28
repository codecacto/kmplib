package br.com.codecacto.kmplib.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Dialog de confirmação genérico e customizável
 *
 * @param show Se o dialog deve ser exibido
 * @param title Título do dialog
 * @param message Mensagem do dialog
 * @param confirmText Texto do botão de confirmação
 * @param cancelText Texto do botão de cancelar (null = sem botão de cancelar)
 * @param onConfirm Callback de confirmação
 * @param onDismiss Callback de dismiss
 * @param modifier Modificador customizado
 * @param isLoading Estado de loading (desabilita botões)
 * @param primaryColor Cor primária dos botões
 * @param icon Ícone opcional no topo do dialog
 * @param dismissOnBackPress Permite fechar com botão voltar
 * @param dismissOnClickOutside Permite fechar clicando fora
 */
@Composable
fun ConfirmationDialog(
    show: Boolean,
    title: String,
    message: String,
    confirmText: String = "Confirmar",
    cancelText: String? = "Cancelar",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    icon: (@Composable () -> Unit)? = null,
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true
) {
    if (show) {
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                dismissOnBackPress = dismissOnBackPress && !isLoading,
                dismissOnClickOutside = dismissOnClickOutside && !isLoading
            )
        ) {
            Card(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Ícone opcional
                    icon?.invoke()

                    // Título
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    // Mensagem
                    Text(
                        text = message,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp
                    )

                    // Botões
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Botão cancelar
                        if (cancelText != null) {
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                enabled = !isLoading,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(cancelText)
                            }
                        }

                        // Botão confirmar
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            enabled = !isLoading,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = Color.White
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(confirmText)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * AlertDialog customizado com TextField
 *
 * @param show Se o dialog deve ser exibido
 * @param title Título do dialog
 * @param message Mensagem/descrição do dialog
 * @param textFieldValue Valor atual do TextField
 * @param onTextFieldValueChange Callback quando o valor do TextField muda
 * @param textFieldLabel Label do TextField
 * @param textFieldPlaceholder Placeholder do TextField
 * @param textFieldError Mensagem de erro do TextField
 * @param confirmText Texto do botão de confirmação
 * @param cancelText Texto do botão de cancelar
 * @param onConfirm Callback de confirmação
 * @param onDismiss Callback de dismiss
 * @param modifier Modificador customizado
 * @param isLoading Estado de loading
 * @param primaryColor Cor primária
 */
@Composable
fun InputDialog(
    show: Boolean,
    title: String,
    message: String? = null,
    textFieldValue: String,
    onTextFieldValueChange: (String) -> Unit,
    textFieldLabel: String? = null,
    textFieldPlaceholder: String? = null,
    textFieldError: String? = null,
    confirmText: String = "OK",
    cancelText: String = "Cancelar",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    isPassword: Boolean = false
) {
    if (show) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (message != null) {
                        Text(
                            text = message,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AppTextField(
                        value = textFieldValue,
                        onValueChange = onTextFieldValueChange,
                        label = textFieldLabel,
                        placeholder = textFieldPlaceholder,
                        errorMessage = textFieldError,
                        isPassword = isPassword,
                        primaryColor = primaryColor,
                        enabled = !isLoading
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onConfirm,
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(confirmText)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isLoading
                ) {
                    Text(cancelText)
                }
            },
            modifier = modifier
        )
    }
}

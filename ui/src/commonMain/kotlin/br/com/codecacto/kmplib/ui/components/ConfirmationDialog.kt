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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
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

                    // ⚠️ **Os botões EMPILHAM quando os rótulos não cabem lado a lado**
                    // (15/set/2026). Dois botões com `weight(1f)` dividem a linha ao meio mesmo
                    // quando o texto não cabe na metade — e o rótulo quebra no meio da palavra:
                    // "Denunciar" virava "Denuncia" numa linha e um "r" solto na outra, num
                    // diálogo de moderação. A largura útil de cada metade num celular de 360dp é
                    // ~136dp, e dela o padding do próprio botão come 48dp: sobram 88dp, que
                    // "Denunciar" só não estoura com a fonte no tamanho padrão do sistema.
                    //
                    // A medida é feita ANTES de desenhar, com o `TextMeasurer`, e não chutada: é o
                    // que faz a decisão valer para qualquer rótulo, em qualquer idioma e em
                    // qualquer escala de fonte do aparelho. É o mesmo comportamento do `AlertDialog`
                    // do Material, que empilha as ações quando elas não cabem numa linha.
                    BotoesDeConfirmacao(
                        confirmText = confirmText,
                        cancelText = cancelText,
                        onConfirm = onConfirm,
                        onDismiss = onDismiss,
                        isLoading = isLoading,
                        primaryColor = primaryColor,
                    )
                }
            }
        }
    }
}

/**
 * As duas ações do [ConfirmationDialog] — lado a lado quando cabem, **empilhadas quando não**.
 *
 * O empilhamento é a saída correta, e não encolher a fonte ou cortar com reticências: um botão de
 * ação irreversível que diz "Denunci…" é pior do que um botão numa segunda linha. Empilhado, o
 * confirmar fica **em cima** — é a ação principal, e é onde o polegar chega primeiro num diálogo
 * que acabou de ser lido de cima para baixo.
 */
@Composable
private fun BotoesDeConfirmacao(
    confirmText: String,
    cancelText: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isLoading: Boolean,
    primaryColor: Color,
) {
    val medidor = rememberTextMeasurer()
    val densidade = LocalDensity.current
    val estilo = MaterialTheme.typography.labelLarge

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val larguraDoRotulo: (String) -> Dp = { texto ->
            with(densidade) { medidor.measure(texto, estilo).size.width.toDp() }
        }
        val maiorRotulo = maxOf(
            larguraDoRotulo(confirmText),
            cancelText?.let(larguraDoRotulo) ?: 0.dp,
        )
        // `* 2` porque as duas metades têm a mesma largura: quem não cabe é o MAIOR dos dois.
        val larguraNecessaria = (maiorRotulo + PADDING_INTERNO_DO_BOTAO) * 2 + ESPACO_ENTRE_BOTOES
        val empilhar = cancelText != null && larguraNecessaria > maxWidth

        val confirmar: @Composable (Modifier) -> Unit = { m ->
            Button(
                onClick = onConfirm,
                modifier = m,
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = primaryColor,
                    contentColor = Color.White,
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(confirmText, maxLines = 1)
                }
            }
        }
        val cancelar: @Composable (Modifier) -> Unit = { m ->
            cancelText?.let {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = m,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(it, maxLines = 1)
                }
            }
        }

        if (empilhar) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                confirmar(Modifier.fillMaxWidth())
                cancelar(Modifier.fillMaxWidth())
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ESPACO_ENTRE_BOTOES),
            ) {
                cancelar(Modifier.weight(1f))
                confirmar(Modifier.weight(1f))
            }
        }
    }
}

/** O padding horizontal que o `Button` do Material reserva de cada lado (24dp + 24dp). */
private val PADDING_INTERNO_DO_BOTAO: Dp = 48.dp
private val ESPACO_ENTRE_BOTOES: Dp = 8.dp

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

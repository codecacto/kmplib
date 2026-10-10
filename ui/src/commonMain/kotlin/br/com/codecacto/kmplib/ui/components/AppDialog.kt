package br.com.codecacto.kmplib.ui.components

import br.com.codecacto.kmplib.generated.resources.Res
import br.com.codecacto.kmplib.generated.resources.kmplib_confirm
import br.com.codecacto.kmplib.generated.resources.kmplib_cancel
import br.com.codecacto.kmplib.generated.resources.kmplib_ok
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import br.com.codecacto.kmplib.platform.automation.DialogTestTags
import br.com.codecacto.kmplib.platform.automation.exposeTestTagsAsResourceId
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
                // O diálogo é outra janela: nem o `dismissKeyboardOnTapOutside` nem o
                // `testTagsAsResourceId` da raiz do app o alcançam (este último deixava todo nó do
                // diálogo com `resource-id=""` para o Maestro até a 2.233.0). Os dois se religam aqui.
                modifier = modifier
                    .fillMaxWidth()
                    .dismissKeyboardOnTapOutside()
                    .exposeTestTagsAsResourceId(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    // O id do contêiner fica num nó NOSSO, não no `modifier` do app — um `testTag`
                    // que o app passe lá continua valendo.
                    modifier = Modifier.testTag(DialogTestTags.CONTAINER).padding(24.dp),
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
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag(DialogTestTags.TITULO)
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
    confirmText: String = stringResource(Res.string.kmplib_ok),
    dismissText: String? = stringResource(Res.string.kmplib_cancel),
    onConfirm: () -> Unit = onDismiss,
    isLoading: Boolean = false,
    confirmButtonColor: Color = MaterialTheme.colorScheme.primary,
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(DialogTestTags.MENSAGEM)
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
                    modifier = Modifier.weight(1f).testTag(DialogTestTags.BTN_CANCELAR),
                    enabled = !isLoading,
                    primaryColor = Color.Gray,
                    height = 48.dp
                )
            }

            // Botão confirmar
            AppButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f).testTag(DialogTestTags.BTN_CONFIRMAR),
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
 * @param confirmEnabled Se o botão de confirmar aceita toque (2.281.0). Default `true` = o
 *   comportamento de sempre. Com `false` o botão fica no estado desabilitado do tema e é anunciado
 *   como desabilitado pelo TalkBack/VoiceOver (semântica `disabled` do `Button` do Material3), e
 *   [onConfirm] não é chamado. É o "digite EXCLUIR" da exclusão de conta: passe
 *   `confirmEnabled = typedConfirmationMatches(digitado, "EXCLUIR")` — o confirmar só acende quando
 *   a palavra confere. O cancelar não é afetado.
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
    confirmText: String = stringResource(Res.string.kmplib_confirm),
    dismissText: String = stringResource(Res.string.kmplib_cancel),
    onConfirm: () -> Unit,
    isLoading: Boolean = false,
    confirmEnabled: Boolean = true,
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(DialogTestTags.MENSAGEM)
            )
        }

        // Campo de texto
        AppTextField(
            value = textFieldValue,
            onValueChange = onTextFieldValueChange,
            label = textFieldLabel,
            placeholder = textFieldPlaceholder,
            errorMessage = textFieldError,
            enabled = !isLoading,
            modifier = Modifier.testTag(DialogTestTags.INPUT)
        )

        // Botões de ação
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AppOutlinedButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f).testTag(DialogTestTags.BTN_CANCELAR),
                enabled = !isLoading,
                primaryColor = Color.Gray,
                height = 48.dp
            )

            AppButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f).testTag(DialogTestTags.BTN_CONFIRMAR),
                isLoading = isLoading,
                // `enabled = false` no `Button` do Material3 = estado visual desabilitado do tema +
                // `SemanticsProperties.Disabled` (o leitor de tela anuncia "desativado") + clique
                // ignorado. O `isLoading` continua desligando o botão por conta própria.
                enabled = confirmEnabled,
                height = 48.dp
            )
        }
    }
}

/**
 * `true` se [typed] é a palavra de confirmação [expected], **sem diferenciar caixa e ignorando
 * todo espaço** (2.281.0) — "excluir", " EXCLUIR " e "EX CLUIR" conferem. É a mesma régua do
 * `AccountDeletionConfirmation.matches` da backlib (o servidor que confere o `{"confirmacao": …}`):
 * a intenção é provar atenção, não testar digitação. [expected] em branco nunca confere.
 *
 * Uso típico, com o [AppInputDialog]:
 * ```kotlin
 * val confere = typedConfirmationMatches(digitado, "EXCLUIR")
 * AppInputDialog(…, textFieldValue = digitado, confirmEnabled = confere, onConfirm = { if (confere) excluir() })
 * ```
 */
fun typedConfirmationMatches(typed: String, expected: String): Boolean {
    val alvo = expected.filterNot(Char::isWhitespace)
    if (alvo.isEmpty()) return false
    return typed.filterNot(Char::isWhitespace).equals(alvo, ignoreCase = true)
}
